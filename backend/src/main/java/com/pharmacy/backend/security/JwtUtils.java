package com.pharmacy.backend.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.pharmacy.backend.entity.InvalidatedToken;
import com.pharmacy.backend.entity.PasswordResetToken;
import com.pharmacy.backend.entity.Role;
import com.pharmacy.backend.entity.User;
import com.pharmacy.backend.enums.RoleCodeEnum;
import com.pharmacy.backend.exception.AppException;
import com.pharmacy.backend.repository.InvalidatedTokenRepository;
import com.pharmacy.backend.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
@Slf4j
@Component
public class JwtUtils{
    @Value("${jwt.secret}")
    String secret;

    @Value("${jwt.expiration}")
    long expiration;

    @Value("${jwt.refresh.expiration}")
    long refreshExpiration;

    @Value("${jwt.reset-password.expiration}")
    Long resetPasswordExpiration;

    static final String ISUER = "Pharmacy";
    final InvalidatedTokenRepository invalidatedTokenRepository;
    final PasswordResetTokenRepository passwordResetTokenRepository;

    public String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        List<RoleCodeEnum> roles = user.getRoles().stream()
                .map(Role::getCode)
                .toList();

        List<String> authorities = roles.stream()
                .map(Enum::name).toList();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(ISUER)
                .subject(user.getUsername())
                .claim("authorities", authorities)
                .claim("email", user.getEmail())
                .claim("id", user.getId())
                .claim("ver", user.getTokenVersion())
                .issueTime(new Date())
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(new Date(System.currentTimeMillis() + expiration * 1000))
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(secret.getBytes()));
        } catch (JOSEException e) {
            throw new RuntimeException("Error signing JWT token", e);
        }
        return jwsObject.serialize();
    }

    public SignedJWT verifyToken(String token, boolean isRefreshToken) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(secret.getBytes());

        SignedJWT signedJWT = SignedJWT.parse(token);
        Date expirationTime = isRefreshToken
                ? new Date(signedJWT.getJWTClaimsSet().getIssueTime().getTime() + refreshExpiration * 1000)
                : signedJWT.getJWTClaimsSet().getExpirationTime();
        var verified = signedJWT.verify(verifier);

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã bị vô hiệu hóa", "token");
        }

        if(!verified) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ", "token");
        }

        if(expirationTime == null || expirationTime.before(new Date())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn", "token");
        }
        return signedJWT;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanUpExpiredTokens() {
        Date now = new Date();
        List<InvalidatedToken> expiredTokens = invalidatedTokenRepository.findTop10ByExpirationTimeBefore(now);
        if (!expiredTokens.isEmpty()) {
            invalidatedTokenRepository.deleteAll(expiredTokens);
        }
        log.info("Cleaned up {} expired tokens", expiredTokens.size());
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanUpPasswordResetTokens() {
        LocalDateTime now = LocalDateTime.now();
        List<PasswordResetToken> expiredTokens = passwordResetTokenRepository.findTop10ByExpiresAtBefore(now);
        if (!expiredTokens.isEmpty()) {
            passwordResetTokenRepository.deleteAll(expiredTokens);
        }
        log.info("Cleaned up {} expired password reset tokens", expiredTokens.size());
    }


    public LocalDateTime getExpirationTime(String token, boolean isRefreshToken) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        if (isRefreshToken) {
            return LocalDateTime.ofInstant(signedJWT.getJWTClaimsSet().getIssueTime().toInstant(), java.time.ZoneId.systemDefault())
                    .plusSeconds(refreshExpiration);
        } else {
            return LocalDateTime.ofInstant(signedJWT.getJWTClaimsSet().getExpirationTime().toInstant(), java.time.ZoneId.systemDefault());
        }
    }

    public String getUserEmail(String token) throws ParseException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        return signedJWT.getJWTClaimsSet().getStringClaim("email");
    }

    public Boolean isTokenExpired(String token, boolean isRefreshToken) {
        try {
            LocalDateTime expirationTime = getExpirationTime(token, isRefreshToken);
            return expirationTime.isBefore(LocalDateTime.now());
        } catch (ParseException e) {
            log.error("Error parsing token expiration time", e);
            return true;
        }
    }

    public String generatePasswordResetToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        List<RoleCodeEnum> roles = user.getRoles().stream()
                .map(Role::getCode)
                .toList();

        List<String> authorities = roles.stream()
                .map(Enum::name).toList();

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(ISUER)
                .subject(user.getUsername())
                .claim("authorities", authorities)
                .claim("email", user.getEmail())
                .claim("id", user.getId())
                .claim("ver", user.getTokenVersion())
                .issueTime(new Date())
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(new Date(System.currentTimeMillis() + resetPasswordExpiration * 1000))
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(secret.getBytes()));
        } catch (JOSEException e) {
            throw new RuntimeException("Error signing JWT token", e);
        }
        return jwsObject.serialize();
    }

}
