package com.pharmacy.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemRequest {
    @NotNull(message = "ID sản phẩm không được để trống")
    private Long productId;
    @NotNull(message = "Số lượng không được để trống")
    private Long quantity;
}
