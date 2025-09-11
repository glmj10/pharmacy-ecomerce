package com.pharmacy.backend.entity;

import com.pharmacy.backend.enums.OrderStatusEnum;
import com.pharmacy.backend.enums.PaymentMethodEnum;
import com.pharmacy.backend.enums.PaymentStatusEnum;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "orders")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    Long totalPrice;

    @Column(name = "customer_name")
    String customerName;

    @Column(name = "customer_phone_number")
    String customerPhoneNumber;

    @Column(name = "customer_address")
    String customerAddress;

    @Column(columnDefinition = "TEXT")
    String note;

    @Column(name = "payment_method")
    @Enumerated(EnumType.STRING)
    PaymentMethodEnum paymentMethod;

    @Column(name = "payment_status")
    @Enumerated(EnumType.STRING)
    PaymentStatusEnum paymentStatus;

    @Enumerated(EnumType.STRING)
    OrderStatusEnum status;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "order")
    List<OrderDetail> orderDetails = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "cart_id", referencedColumnName = "id")
    Cart cart;

    @Column(name = "created_at")
    @CreationTimestamp
    LocalDateTime createdAt;

    @Column(name = "updated_at")
    @CreationTimestamp
    LocalDateTime updatedAt;
}
