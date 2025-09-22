package com.pharmacy.backend.service.impl;

import com.pharmacy.backend.dto.request.BlogRequest;
import com.pharmacy.backend.dto.response.ApiResponse;
import com.pharmacy.backend.dto.response.BlogResponse;
import com.pharmacy.backend.dto.response.PageResponse;
import com.pharmacy.backend.entity.Blog;
import com.pharmacy.backend.entity.Category;
import com.pharmacy.backend.entity.FileMetadata;
import com.pharmacy.backend.exception.AppException;
import com.pharmacy.backend.mapper.BlogMapper;
import com.pharmacy.backend.mapper.CategoryMapper;
import com.pharmacy.backend.repository.BlogRepository;
import com.pharmacy.backend.repository.CategoryRepository;
import com.pharmacy.backend.repository.FileMetadataRepository;
import com.pharmacy.backend.service.BlogService;
import com.pharmacy.backend.service.FileMetadataService;
import com.pharmacy.backend.specification.BlogSpecification;
import com.pharmacy.backend.utils.SlugUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {
    private final BlogRepository blogRepository;
    private final BlogMapper blogMapper;
    private final FileMetadataService fileMetadataService;
    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final FileMetadataRepository fileMetadataRepository;

    @Override
    public ApiResponse<PageResponse<List<BlogResponse>>> getAllBlogs(int pageIndex,
                                                                     int pageSize, String title, String category) {
        if(pageIndex <= 0) {
            pageIndex = 1;
        }

        if(pageSize <= 0) {
            pageSize = 10;
        }

        Specification<Blog> blogSpecification = BlogSpecification.hasTitle(title)
                .and(BlogSpecification.hasCategorySlug(category));

        Pageable pageable = PageRequest.of(pageIndex - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Blog> blogPage;

        blogPage = blogRepository.findAll(blogSpecification, pageable);

        List<BlogResponse> blogResponses = blogPage.getContent().stream().map(
                blog -> {
                    BlogResponse response = blogMapper.toBlogResponse(blog);
                    FileMetadata fileMetadata = fileMetadataRepository.findByUuid(UUID.fromString(blog.getThumbnail()))
                            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                                    "Không tìm thấy hình ảnh với ID: " + blog.getThumbnail(), "File not found"));

                    response.setThumbnail(fileMetadata.getUrl());
                    return response;
                }
        ).toList();
        PageResponse<List<BlogResponse>> pageResponse = PageResponse.<List<BlogResponse>>builder()
                .content(blogResponses)
                .currentPage(pageIndex)
                .totalPages(blogPage.getTotalPages())
                .totalElements(blogPage.getTotalElements())
                .hasNext(blogPage.hasNext())
                .hasPrevious(blogPage.hasPrevious())
                .build();

        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Lấy danh sách bài viết thành công",
                pageResponse
        );
    }

    @Transactional
    @Override
    public ApiResponse<BlogResponse> getBlogBySlug(String slug) {
        Blog blog = blogRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết với slug: " + slug, "Blog not found"));
        BlogResponse blogResponse = blogMapper.toBlogResponse(blog);
        blogResponse.setCategory(categoryMapper.toCategoryResponse(blog.getCategory()));
        FileMetadata fileMetadata = fileMetadataRepository.findByUuid(UUID.fromString(blog.getThumbnail()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hình ảnh với ID: " + blog.getThumbnail(), "File not found"));
        blogResponse.setThumbnail(fileMetadata.getUrl());
        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Lấy bài viết thành công",
                blogResponse
        );
    }

    @Transactional
    @Override
    public ApiResponse<BlogResponse> getBlogById(Long id) {
        Blog blog = blogRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết với ID: " + id, "Blog not found"));
        BlogResponse blogResponse = blogMapper.toBlogResponse(blog);
        blogResponse.setCategory(categoryMapper.toCategoryResponse(blog.getCategory()));
        FileMetadata fileMetadata = fileMetadataRepository.findByUuid(UUID.fromString(blog.getThumbnail()))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy hình ảnh với ID: " + blog.getThumbnail(), "File not found"));
        blogResponse.setThumbnail(fileMetadata.getUrl());
        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Lấy bài viết thành công",
                blogResponse
        );
    }

    @Transactional
    @Override
    public ApiResponse<BlogResponse> createBlog(BlogRequest request, MultipartFile thumbnail) {
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy danh mục với ID: " + request.getCategoryId(), "Category not found"));

        Blog blog = blogMapper.toBlog(request);
        blog.setSlug(createSlug(blog.getTitle()));

        var fileMetadata = fileMetadataService.storeFile(thumbnail, "BLOG");
        blog.setThumbnail(fileMetadata.getData().getId().toString());
        blog.setCategory(category);
        Blog savedBlog = blogRepository.save(blog);

        BlogResponse blogResponse = blogMapper.toBlogResponse(savedBlog);
        return ApiResponse.buildResponse(
                HttpStatus.CREATED.value(),
                "Tạo bài viết thành công",
                blogResponse
        );
    }

    @Transactional
    @Override
    public ApiResponse<BlogResponse> updateBlog(Long id, BlogRequest request, MultipartFile thumbnail) {
        Blog existingBlog = blogRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết với ID: " + id, "Blog not found"));

        Blog blogUpdateFromRequest = blogMapper.toBlogUpdateFromRequest(request, existingBlog);

        if (thumbnail != null && !thumbnail.isEmpty()) {
            fileMetadataService.deleteFile(existingBlog.getThumbnail());
            var fileMetadata = fileMetadataService.storeFile(thumbnail, "BLOG");
            blogUpdateFromRequest.setThumbnail(fileMetadata.getData().getId().toString());
        } else {
            blogUpdateFromRequest.setThumbnail(existingBlog.getThumbnail());
        }

        blogUpdateFromRequest.setSlug(createSlug(blogUpdateFromRequest.getTitle()));
        blogUpdateFromRequest.setUpdatedAt(LocalDateTime.now());

        Blog updatedBlog = blogRepository.save(blogUpdateFromRequest);
        BlogResponse blogResponse = blogMapper.toBlogResponse(updatedBlog);

        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Cập nhật bài viết thành công",
                blogResponse
        );
    }

    @Transactional
    @Override
    public ApiResponse<Void> deleteBlog(Long id) {
        Blog blog = blogRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy bài viết với ID: " + id, "Blog not found"));

        blogRepository.delete(blog);
        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Xóa bài viết thành công",
                null
        );
    }

    private String createSlug(String name) {
        String baseSlug = SlugUtils.generateSlug(name);
        int cnt = 1;
        String slug = baseSlug;
        while(blogRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + cnt++;
        }
        return slug;
    }
}
