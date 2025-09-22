package com.pharmacy.backend.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pharmacy.backend.config.FileStorageProperties;
import com.pharmacy.backend.dto.response.ApiResponse;
import com.pharmacy.backend.dto.response.FileMetadataResponse;
import com.pharmacy.backend.entity.FileMetadata;
import com.pharmacy.backend.enums.FileCategoryEnum;
import com.pharmacy.backend.exception.AppException;
import com.pharmacy.backend.repository.FileMetadataRepository;
import com.pharmacy.backend.repository.UserRepository;
import com.pharmacy.backend.service.FileMetadataService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
public class FileMetadataServiceImpl implements FileMetadataService {

    private final FileMetadataRepository fileMetadataRepository;
    private final UserRepository userRepository;
    private final Cloudinary cloudinary;

    @Transactional
    @Override
    public ApiResponse<FileMetadataResponse> storeFile(MultipartFile file, String category) {
        FileCategoryEnum fileCategoryEnum = FileCategoryEnum.valueOf(category.toUpperCase());
        String originalFileName = file.getOriginalFilename();
        String extension = Optional.ofNullable(originalFileName)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(originalFileName.lastIndexOf('.') + 1))
                .orElse("Unknown");

        String publicId = UUID.randomUUID().toString();

        try {
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", fileCategoryEnum.getSubDirectory(),
                    "public_id", publicId,
                    "overwrite", true,
                    "resource_type", "auto"
            );

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
            String url = (String) uploadResult.get("secure_url");

            FileMetadata fileMetadata = FileMetadata.builder()
                    .originalFileName(originalFileName)
                    .storedFileName(publicId)
                    .fileExtension(extension)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .fileType(fileCategoryEnum.getSubDirectory())
                    .url(url)
                    .build();

            fileMetadata = fileMetadataRepository.save(fileMetadata);

            FileMetadataResponse response = FileMetadataResponse.builder()
                    .id(fileMetadata.getUuid())
                    .storedFileName(publicId)
                    .fileUrl(url)
                    .build();

            return ApiResponse.<FileMetadataResponse>builder()
                    .status(HttpStatus.CREATED.value())
                    .message("Upload file thành công")
                    .data(response)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể upload file", e.getMessage());
        }
    }

    @Override
    public FileSystemResource downloadFile(String uuidStr) {
        throw new UnsupportedOperationException("Download vật lý không hỗ trợ trong Cloudinary. Sử dụng URL.");
    }

    @Override
    public FileSystemResource loadFile(String uuidStr) {
        throw new UnsupportedOperationException("Không hỗ trợ đọc file vật lý từ Cloudinary.");
    }

    @Transactional
    @Override
    public void deleteFile(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) return;

        FileMetadata fileMetadata = fileMetadataRepository.findByUuid(UUID.fromString(uuidStr))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp tin", "File not found"));

        String publicIdWithFolder = fileMetadata.getFileType() + "/" + fileMetadata.getStoredFileName();

        try {
            Map result = cloudinary.uploader().destroy(publicIdWithFolder, ObjectUtils.asMap(
                    "resource_type", "image"
            ));

            String destroyResult = (String) result.get("result");

            if (!"ok".equals(destroyResult) && !"not found".equals(destroyResult)) {
                throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Xóa thất bại", "Cloudinary trả về: " + destroyResult);
            }

            fileMetadataRepository.delete(fileMetadata);
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xóa file", e.getMessage());
        }
    }
}

