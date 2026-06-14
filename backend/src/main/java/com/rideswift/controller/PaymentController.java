package com.rideswift.controller;

import com.rideswift.dto.request.InitiatePaymentRequest;
import com.rideswift.dto.request.RazorpayOrderRequest;
import com.rideswift.dto.request.RazorpayVerifyRequest;
import com.rideswift.dto.request.RefundRequest;
import com.rideswift.dto.response.PaymentResponse;
import com.rideswift.dto.response.RazorpayOrderResponse;
import com.rideswift.security.SecurityUtils;
import com.rideswift.service.PaymentService;
import com.rideswift.service.RazorpayService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RazorpayService razorpayService;

    public PaymentController(PaymentService paymentService, RazorpayService razorpayService) {
        this.paymentService = paymentService;
        this.razorpayService = razorpayService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<PaymentResponse> initiate(@Valid @RequestBody InitiatePaymentRequest request) {
        PaymentResponse response = paymentService.initiate(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Step 1 of the real Razorpay flow: open an order for the frontend Checkout. */
    @PostMapping("/razorpay/order")
    public ResponseEntity<RazorpayOrderResponse> razorpayOrder(@Valid @RequestBody RazorpayOrderRequest request) {
        return ResponseEntity.ok(razorpayService.createOrder(SecurityUtils.currentUserId(), request));
    }

    /** Step 2: verify the checkout callback signature and mark the payment paid. */
    @PostMapping("/razorpay/verify")
    public ResponseEntity<PaymentResponse> razorpayVerify(@Valid @RequestBody RazorpayVerifyRequest request) {
        return ResponseEntity.ok(razorpayService.verify(SecurityUtils.currentUserId(), request));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> get(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.get(SecurityUtils.currentUserId(), paymentId));
    }

    @PostMapping("/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponse> refund(@Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(request));
    }
}
