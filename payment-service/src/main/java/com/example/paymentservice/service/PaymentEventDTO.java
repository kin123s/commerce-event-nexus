package com.example.paymentservice.service;

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
public class PaymentEventDTO {
    private Long paymentId;
    private Long orderId;
    private String orderNumber;
    private String paymentNumber;
    private BigDecimal amount;
    private String status;
    private String eventType;
    private String failureReason;
    private LocalDateTime eventTime;
}
