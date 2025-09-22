package com.pharmacy.backend.service.impl;

import com.pharmacy.backend.dto.request.CartItemRequest;
import com.pharmacy.backend.dto.response.ApiResponse;
import com.pharmacy.backend.dto.response.CartItemResponse;
import com.pharmacy.backend.dto.response.CartResponse;
import com.pharmacy.backend.dto.response.ProductResponse;
import com.pharmacy.backend.entity.*;
import com.pharmacy.backend.exception.AppException;
import com.pharmacy.backend.mapper.ProductMapper;
import com.pharmacy.backend.repository.*;
import com.pharmacy.backend.security.SecurityUtils;
import com.pharmacy.backend.service.CartService;
import com.pharmacy.backend.utils.NumberUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartRepository cartRepository;
    private final ProductMapper productMapper;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final FileMetadataRepository fileMetadataRepository;

    @Transactional
    @Override
    public void createCart(User user) {
        if(cartRepository.existsByUser(user)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Giỏ hàng đã tồn tại", "Cart already exists for this user");
        }

        Cart cart = new Cart(user);
        cartRepository.save(cart);
    }

    @Transactional
    @Override
    public ApiResponse<CartResponse> getCart() {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        List<CartItem> items = cartItemRepository.findByCart(cart,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        List<CartItemResponse> itemResponses = items.stream()
                .map(cartItem -> {
                    CartItemResponse cartItemResponse = CartItemResponse.builder()
                            .id(cartItem.getId())
                            .quantity(cartItem.getQuantity())
                            .priceAtAddition(cartItem.getPriceAtAddition())
                            .priceDifferent(Math.abs(cartItem.getPriceAtAddition() - cartItem.getProduct().getPriceNew()))
                            .priceChangeType(NumberUtils.toPriceChangeType(cartItem.getProduct().getPriceNew() - cartItem.getPriceAtAddition()))
                            .isOutOfStock(cartItem.isOutOfStock())
                            .selected(cartItem.getSelected())
                            .build();
                    ProductResponse productResponse = productMapper.toProductResponse(cartItem.getProduct());
                    FileMetadata fileMetadata = fileMetadataRepository.findByUuid(UUID.fromString(
                            cartItem.getProduct().getThumbnail()))
                            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                                    "Ảnh sản phẩm không tồn tại", "Product image not found"));
                    productResponse.setThumbnailUrl(fileMetadata.getUrl());
                    cartItemResponse.setProduct(productResponse);
                    return cartItemResponse;
                })
                .toList();

        CartResponse response = new CartResponse(cart.getId(), cart.getTotalPrice(), itemResponses);
        return ApiResponse.<CartResponse>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách sản phẩm trong giỏ hàng thành công")
                .data(response)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional
    public ApiResponse<CartItemResponse> addItemToCart(CartItemRequest request) {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        if(request.getQuantity() <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Số lượng sản phẩm phải lớn hơn 0", "Invalid product quantity");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Sản phẩm không tồn tại", "Product not found"));

        if(product.getActive() == null || !product.getActive()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Sản phẩm không khả dụng", "Product is not available");
        }
        if (product.getQuantity() < request.getQuantity()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Số lượng sản phẩm không đủ", "Insufficient product quantity");
        }

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        CartItem existingItem = cartItemRepository.findByCartAndProduct(cart, product)
                .orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(
                    (existingItem.getQuantity() + request.getQuantity() > product.getQuantity())
                            ? product.getQuantity()
                            : existingItem.getQuantity() + request.getQuantity()
            );
            existingItem.setPriceAtAddition(product.getPriceNew());
            existingItem = cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = new CartItem();
            newItem.setProduct(product);
            newItem.setQuantity(request.getQuantity());
            newItem.setPriceAtAddition(product.getPriceNew());
            newItem.setCart(cart);
            cart.getCartItems().add(newItem);
            existingItem = cartItemRepository.save(newItem);
        }

        CartItemResponse response = CartItemResponse.builder()
                .id(existingItem.getId())
                .product(productMapper.toProductResponse(product))
                .quantity(existingItem.getQuantity())
                .priceAtAddition(product.getPriceNew())
                .build();

        return ApiResponse.buildResponse(
                HttpStatus.CREATED.value(),
                "Thêm sản phẩm vào giỏ hàng thành công",
                response
        );
    }

    @Override
    @Transactional
    public ApiResponse<CartItemResponse> updateItemQuantity(Long itemId, Long quantity) {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        CartItem item = cartItemRepository.findByCartAndId(cart, itemId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Sản phẩm trong giỏ hàng không tồn tại", "Cart item not found"));

        if (item.getProduct().getQuantity() < quantity) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Số lượng sản phẩm không đủ", "Insufficient product quantity");
        }

        if(item.isSelected()) {
            cart.setTotalPrice(
                    item.getQuantity() < quantity ?
                            cart.getTotalPrice() + (item.getProduct().getPriceNew() * (quantity - item.getQuantity())) :
                            cart.getTotalPrice() - item.getProduct().getPriceNew() * (item.getQuantity() - quantity)
            );
        }
        item.setQuantity(quantity);

        cartItemRepository.save(item);
        cartRepository.save(cart);

        CartItemResponse response = CartItemResponse.builder()
                .id(item.getId())
                .product(productMapper.toProductResponse(item.getProduct()))
                .quantity(item.getQuantity())
                .priceAtAddition(item.getPriceAtAddition())
                .build();

        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Cập nhật số lượng sản phẩm trong giỏ hàng thành công",
                response
        );
    }

    @Transactional
    @Override
    public ApiResponse<Void> removeItemFromCart(Long itemId) {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        CartItem item = cartItemRepository.findByCartAndId(cart, itemId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Sản phẩm trong giỏ hàng không tồn tại", "Cart item not found"));

        cartItemRepository.delete(item);
        cart.setTotalPrice(cart.getTotalPrice() - item.getProduct().getPriceNew() * item.getQuantity());
        cartRepository.save(cart);

        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Đã xóa sản phẩm khỏi giỏ hàng",
                null
        );
    }

    @Transactional
    @Override
    public ApiResponse<Void> clearCart() {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        cart.getCartItems().forEach(item -> {
            if (item.getSelected()) {
                cart.setTotalPrice(cart.getTotalPrice() - item.getProduct().getPriceNew() * item.getQuantity());
            }
        });

        cartItemRepository.deleteAll(cart.getCartItems());
        cart.getCartItems().clear();
        cartRepository.save(cart);

        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Đã xóa tất cả sản phẩm khỏi giỏ hàng",
                null
        );
    }

    @Transactional
    @Override
    public ApiResponse<CartItemResponse> changeItemSelection(Long itemId, Boolean status) {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        CartItem item = cartItemRepository.findByCartAndId(cart, itemId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Sản phẩm trong giỏ hàng không tồn tại", "Cart item not found"));

        if(status && item.isOutOfStock()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Sản phẩm đã hết hàng", "Product is out of stock");
        }

        if(!item.getSelected().equals(status)) {
            item.setSelected(status);
            cart.setTotalPrice(cart.getTotalPrice() + (item.isSelected() ? item.getProduct().getPriceNew() * item.getQuantity()
                    : -item.getProduct().getPriceNew() * item.getQuantity()));
            cartItemRepository.save(item);
            cartRepository.save(cart);
        }

        CartItemResponse response = CartItemResponse.builder()
                .id(item.getId())
                .product(productMapper.toProductResponse(item.getProduct()))
                .quantity(item.getQuantity())
                .priceAtAddition(item.getPriceAtAddition())
                .selected(item.getSelected())
                .isOutOfStock(item.isOutOfStock())
                .priceDifferent(Math.abs(item.getPriceAtAddition() - item.getProduct().getPriceNew()))
                .build();

        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Cập nhật trạng thái sản phẩm trong giỏ hàng thành công",
                response
        );
    }

    @Transactional
    @Override
    public ApiResponse<List<CartItemResponse>> selectAllItems(Boolean status) {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        List<CartItem> items = cartItemRepository.findAllByCartAndSelected(cart, !status);

        items.forEach(item -> {
            boolean wasSelected = item.isSelected();

            if (wasSelected != status) {
                item.setSelected(status);
                long change = item.getProduct().getPriceNew() * item.getQuantity();
                if (status) {
                    cart.setTotalPrice(cart.getTotalPrice() + change);
                } else {
                    cart.setTotalPrice(cart.getTotalPrice() - change);
                }
                cartItemRepository.save(item);
            }
        });

        cartRepository.save(cart);

        List<CartItemResponse> responses = items.stream()
                .map(item -> CartItemResponse.builder()
                        .id(item.getId())
                        .product(productMapper.toProductResponse(item.getProduct()))
                        .quantity(item.getQuantity())
                        .priceAtAddition(item.getPriceAtAddition())
                        .selected(item.getSelected())
                        .isOutOfStock(item.isOutOfStock())
                        .priceDifferent(Math.abs(item.getPriceAtAddition() - item.getProduct().getPriceNew()))
                        .build())
                .toList();

        return ApiResponse.buildResponse(
                HttpStatus.OK.value(),
                "Cập nhật trạng thái tất cả sản phẩm trong giỏ hàng thành công",
                responses
        );
    }

    @Transactional
    @Override
    public ApiResponse<List<CartResponse>> getCartItemsForCheckout() {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        List<CartItem> items = cartItemRepository.findAllByCartAndSelected(cart, true);

        List<CartItemResponse> itemResponses = items.stream()
                .map(cartItem -> {
                    CartItemResponse cartItemResponse = CartItemResponse.builder()
                            .id(cartItem.getId())
                            .quantity(cartItem.getQuantity())
                            .priceAtAddition(cartItem.getPriceAtAddition())
                            .priceDifferent(Math.abs(cartItem.getPriceAtAddition() - cartItem.getProduct().getPriceNew()))
                            .priceChangeType(NumberUtils.toPriceChangeType(cartItem.getProduct().getPriceNew() - cartItem.getPriceAtAddition()))
                            .isOutOfStock(cartItem.isOutOfStock())
                            .selected(cartItem.getSelected())
                            .build();
                    ProductResponse productResponse = productMapper.toProductResponse(cartItem.getProduct());
                    FileMetadata fileMetadata = fileMetadataRepository.findByUuid(UUID.fromString(
                                    cartItem.getProduct().getThumbnail()))
                            .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                                    "Ảnh sản phẩm không tồn tại", "Product image not found"));
                    productResponse.setThumbnailUrl(fileMetadata.getUrl());
                    cartItemResponse.setProduct(productResponse);
                    return cartItemResponse;
                })
                .toList();

        CartResponse response = new CartResponse(cart.getId(), cart.getTotalPrice(), itemResponses);
        return ApiResponse.<List<CartResponse>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách sản phẩm trong giỏ hàng thành công")
                .data(List.of(response))
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public ApiResponse<Long> getTotalItemsInCart() {
        User user = userRepository.findById(Objects.requireNonNull(SecurityUtils.getCurrentUserId()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        "Người dùng không hợp lệ", "Invalid user"));
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,
                        "Giỏ hàng không tồn tại", "Cart not found for this user"));

        List<CartItem> items = cartItemRepository.findAllByCart(cart);

        Long totalItems = items.stream()
                .mapToLong(CartItem::getQuantity)
                .sum();

        return ApiResponse.<Long>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy tổng số sản phẩm trong giỏ hàng thành công")
                .data(totalItems)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
