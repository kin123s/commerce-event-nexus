package com.example.orderservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment 서비스에서 발행하는 이벤트 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEventDTO {
    private Long paymentId;
    private Long orderId;
    private String orderNumber;
    private String paymentNumber;
    private BigDecimal amount;
    private String status;
    private String eventType; // PAYMENT_COMPLETED, PAYMENT_FAILED
    private String failureReason;
    private LocalDateTime eventTime;
}
