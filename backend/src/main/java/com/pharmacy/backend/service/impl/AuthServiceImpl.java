package com.pharmacy.backend.service.impl;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import com.pharmacy.backend.dto.request.*;
import com.pharmacy.backend.dto.response.*;
import com.pharmacy.backend.entity.*;
import com.pharmacy.backend.enums.FileCategoryEnum;
import com.pharmacy.backend.enums.RoleCodeEnum;
import com.pharmacy.backend.exception.AppException;
import com.pharmacy.backend.mapper.UserMapper;
import com.pharmacy.backend.repository.*;
import com.pharmacy.backend.security.JwtUtils;
import com.pharmacy.backend.security.SecurityUtils;
import com.pharmacy.backend.service.AuthService;
import com.pharmacy.backend.service.CartService;
import com.pharmacy.backend.service.EmailService;
import com.pharmacy.backend.service.FileMetadataService;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class AuthServiceImpl implements AuthService {
    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    RoleRepository roleRepository;
    JwtUtils jwtUtils;
    InvalidatedTokenRepository invalidatedTokenRepository;
    FileMetadataService fileMetadataService;
    FileMetadataRepository fileMetadataRepository;
    CartService cartService;
    EmailService emailService;
    PasswordResetTokenRepository passwordResetTokenRepository;

    @Transactional
    @Override
    public ApiResponse<AuthResponse> login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Tài khoản hoặc mật khẩu không chính xác", "email"));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Tài khoản hoặc mật khẩu không chính xác", "password");
        }

        String token = jwtUtils.generateToken(user);
        AuthResponse authResponse = new AuthResponse(token);

        return ApiResponse.<AuthResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Đăng nhập thành công")
                .data(authResponse)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    @Override
    public ApiResponse<UserResponse> register(UserRequest request) {
        if(userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Email đã tồn tại", "email");
        }

        if(!request.getPassword().equals(request.getConfirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp", "confirmPassword");
        }

        request.setPassword(passwordEncoder.encode(request.getPassword()));

        User user = userMapper.toUser(request);
        Role role = roleRepository.findByCode(RoleCodeEnum.USER)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy quyền", "role"));

        user.getRoles().add(role);

        FileMetadata fileMetadata = FileMetadata.builder()
                .originalFileName("default-avatar.jpg")
                .storedFileName("default-avatar.jpg")
                .fileExtension("jpg")
                .fileSize(0L)
                .contentType("image/jpeg")
                .fileType(FileCategoryEnum.AVATAR.name())
                .build();

        fileMetadata = fileMetadataRepository.save(fileMetadata);
        user.setProfilePic(fileMetadata.getUuid().toString());

        User savedUser = userRepository.save(user);
        cartService.createCart(savedUser);

        UserResponse userResponse = userMapper.toUserResponse(savedUser);
        userResponse.setRoles(null);
        return ApiResponse.<UserResponse>builder()
                .status(HttpStatus.CREATED.value())
                .message("Đăng ký thành công")
                .data(userResponse)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    @Override
    public ApiResponse<UserResponse> changeInfo(UserInfoRequest request, MultipartFile profilePic) {
        Long id = SecurityUtils.getCurrentUserId();
        assert id != null;
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại", "email"));
        if(request != null) {
            user.setEmail(request.getEmail());
            user.setUsername(request.getUsername());
        }
        if(profilePic != null) {
            fileMetadataService.deleteFile(user.getProfilePic());
            ApiResponse<FileMetadataResponse> fileResponse = fileMetadataService.storeFile(profilePic, FileCategoryEnum.AVATAR.name());
            if(fileResponse.getStatus() != HttpStatus.OK.value() && fileResponse.getStatus() != HttpStatus.CREATED.value()) {
                throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Lưu ảnh đại diện thất bại", "profilePic");
            }
            user.setProfilePic(fileResponse.getData().getId().toString());
        }
        UserResponse userResponse = userMapper.toUserResponse(userRepository.save(user));
        userResponse.setRoles(null);
        return ApiResponse.<UserResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật thông tin thành công")
                .data(userResponse)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public ApiResponse<String> logout(String bearerToken) throws ParseException {
        if(bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Token không hợp lệ", "token");
        }
        String token = bearerToken.substring(7);

        SignedJWT jwt = SignedJWT.parse(token);
        InvalidatedToken invalidatedToken = new InvalidatedToken(
                jwt.getJWTClaimsSet().getJWTID(),
                jwt.getJWTClaimsSet().getExpirationTime()
        );

        invalidatedTokenRepository.save(invalidatedToken);
        return ApiResponse.<String>builder()
                .status(HttpStatus.OK.value())
                .message("Đăng xuất thành công")
                .data("Token đã được vô hiệu hóa")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    @Override
    public ApiResponse<AuthResponse> refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        SignedJWT signedJWT = jwtUtils.verifyToken(request.getToken(), true);
        long userId = (long) signedJWT.getJWTClaimsSet().getClaim("id");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại", "user"));

        InvalidatedToken invalidatedToken = new InvalidatedToken(signedJWT.getJWTClaimsSet().getJWTID(),
                signedJWT.getJWTClaimsSet().getExpirationTime());
        invalidatedTokenRepository.save(invalidatedToken);

        String newToken = jwtUtils.generateToken(user);
        AuthResponse authResponse = new AuthResponse(newToken);
        return ApiResponse.<AuthResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Làm mới token thành công")
                .data(authResponse)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public ApiResponse<String> changePassword(ChangePasswordRequest request) {
        User user = userRepository.findByEmail(SecurityUtils.getCurrentUserEmail())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại", "email"));
        if(!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Mật khẩu cũ không chính xác", "oldPassword");
        }
        String newPassword = request.getPassword();
        if(!newPassword.equals(request.getConfirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp", "confirmPassword");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        return ApiResponse.<String>builder()
                .status(HttpStatus.OK.value())
                .message("Đổi mật khẩu thành công")
                .data("Mật khẩu đã được cập nhật")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    @Override
    public ApiResponse<Void> forgotPassword(ConfirmationEmailRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại", "email"));
        try {
            String token = jwtUtils.generatePasswordResetToken(user);
            LocalDateTime expiryAt = jwtUtils.getExpirationTime(token, false);
            PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                    .token(token)
                    .expiresAt(expiryAt)
                    .build();
            passwordResetTokenRepository.save(passwordResetToken);

            emailService.sendResetEmail(user.getEmail(), token, expiryAt, request.isUser());
        } catch (ParseException | MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Yêu cầu đặt lại mật khẩu đã được gửi, hãy kiểm tra email")
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public ApiResponse<Void> resetPassword(ResetPasswordRequest request) throws ParseException {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(request.getResetToken())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Mã đặt lại mật khẩu không hợp lệ", "token"));

        if(jwtUtils.isTokenExpired(token.getToken(), false)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Mã đặt lại mật khẩu đã hết hạn", "token");
        }

        User user = userRepository.findByEmail(jwtUtils.getUserEmail(token.getToken()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Người dùng không tồn tại", "email"));

        if(!request.getPassword().equals(request.getConfirmPassword())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp", "confirmPassword");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        passwordResetTokenRepository.delete(token);

        return ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đặt lại mật khẩu thành công")
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

}
