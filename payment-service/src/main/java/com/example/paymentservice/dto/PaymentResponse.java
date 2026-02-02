package com.example.paymentservice.dto;

import com.example.paymentservice.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    
    private Long id;
    private String paymentNumber;
    private Long orderId;
    private String orderNumber;
    private BigDecimal amount;
    private String customerName;
    private String customerEmail;
    private String status;
    private String paymentMethod;
    private String transactionId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static PaymentResponse fromEntity(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentNumber(payment.getPaymentNumber())
                .orderId(payment.getOrderId())
                .orderNumber(payment.getOrderNumber())
                .amount(payment.getAmount())
                .customerName(payment.getCustomerName())
                .customerEmail(payment.getCustomerEmail())
                .status(payment.getStatus().name())
                .paymentMethod(payment.getPaymentMethod())
                .transactionId(payment.getTransactionId())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
