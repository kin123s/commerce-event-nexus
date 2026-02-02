package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // React 대시보드 접근 허용
public class PaymentController {
    
    private final PaymentService paymentService;
    
    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        log.info("GET /api/payments - Fetch all payments");
        List<PaymentResponse> payments = paymentService.getAllPayments();
        return ResponseEntity.ok(payments);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable Long id) {
        log.info("GET /api/payments/{} - Fetch payment by id", id);
        PaymentResponse payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(payment);
    }
    
    @GetMapping("/order/{orderNumber}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderNumber(@PathVariable String orderNumber) {
        log.info("GET /api/payments/order/{} - Fetch payment by order number", orderNumber);
        PaymentResponse payment = paymentService.getPaymentByOrderNumber(orderNumber);
        return ResponseEntity.ok(payment);
    }
}
