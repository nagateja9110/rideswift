package com.rideswift.service;

import com.rideswift.config.RazorpayProperties;
import com.rideswift.dto.request.RazorpayOrderRequest;
import com.rideswift.dto.request.RazorpayVerifyRequest;
import com.rideswift.dto.response.PaymentResponse;
import com.rideswift.dto.response.RazorpayOrderResponse;
import com.rideswift.exception.ConflictException;
import com.rideswift.exception.ForbiddenException;
import com.rideswift.exception.PaymentException;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.Gateway;
import com.rideswift.model.Payment;
import com.rideswift.model.PaymentStatus;
import com.rideswift.model.Ride;
import com.rideswift.model.RideStatus;
import com.rideswift.repository.PaymentRepository;
import com.rideswift.repository.RideRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real Razorpay payments using the standard order → checkout → verify flow:
 *  1. {@link #createOrder} validates the ride, opens a Razorpay order (amount in
 *     paise) and persists a PENDING payment. The frontend opens Razorpay Checkout
 *     with the returned order id; the customer pays card/UPI in Razorpay's widget,
 *     so card data never touches our server.
 *  2. {@link #verify} checks the HMAC signature Razorpay returns and only then
 *     marks the payment SUCCESS. Never trust the client's word that it paid —
 *     the signature is the proof.
 */
@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);

    private final RazorpayProperties props;
    private final PaymentRepository paymentRepository;
    private final RideRepository rideRepository;
    private volatile RazorpayClient client;

    public RazorpayService(RazorpayProperties props,
                           PaymentRepository paymentRepository,
                           RideRepository rideRepository) {
        this.props = props;
        this.paymentRepository = paymentRepository;
        this.rideRepository = rideRepository;
    }

    /** Lazily build (and cache) the SDK client; fails clearly if keys are missing. */
    private RazorpayClient client() {
        if (!props.configured()) {
            throw new PaymentException("Razorpay is not configured (set RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET)");
        }
        RazorpayClient c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        client = new RazorpayClient(props.keyId(), props.keySecret());
                    } catch (RazorpayException e) {
                        throw new PaymentException("Could not initialise Razorpay client: " + e.getMessage());
                    }
                }
                c = client;
            }
        }
        return c;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public RazorpayOrderResponse createOrder(java.util.UUID passengerUserId, RazorpayOrderRequest request) {
        Ride ride = rideRepository.findByIdForUpdate(request.rideId())
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + request.rideId()));

        if (!ride.getPassenger().getId().equals(passengerUserId)) {
            throw new ForbiddenException("Only the ride's passenger can pay for it");
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new ConflictException("Ride must be COMPLETED before payment");
        }

        BigDecimal fare = ride.getActualFare();
        if (fare == null || fare.signum() <= 0) {
            throw new PaymentException("Ride has no payable fare");
        }
        BigDecimal tip = request.tipAmount() != null ? request.tipAmount() : BigDecimal.ZERO;
        BigDecimal amount = fare.add(tip).setScale(2, RoundingMode.HALF_UP);

        // Reuse an abandoned PENDING row for the same ride; never re-charge a SUCCESS.
        Payment payment = paymentRepository.findByRideId(ride.getId()).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new ConflictException("Payment already completed for this ride");
        }
        if (payment == null) {
            payment = Payment.builder()
                    .ride(ride)
                    .amount(amount)
                    .tipAmount(tip)
                    .currency("INR")
                    .gateway(Gateway.RAZORPAY)
                    .status(PaymentStatus.PENDING)
                    .build();
        } else {
            payment.setAmount(amount);
            payment.setTipAmount(tip);
            payment.setGateway(Gateway.RAZORPAY);
            payment.setStatus(PaymentStatus.PENDING);
        }

        long amountPaise = amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();

        Order order;
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", ride.getId().toString());
            order = client().orders.create(orderRequest);
        } catch (RazorpayException e) {
            log.warn("Razorpay order creation failed for ride {}: {}", ride.getId(), e.getMessage());
            throw new PaymentException("Could not start payment: " + e.getMessage());
        }

        String orderId = order.get("id");
        payment.setGatewayOrderId(orderId);
        payment = paymentRepository.save(payment);

        return new RazorpayOrderResponse(
                payment.getId(),
                props.keyId(),
                orderId,
                amountPaise,
                "INR",
                "RideSwift",
                "Ride " + shortId(ride.getId().toString()),
                ride.getPassenger().getName(),
                ride.getPassenger().getEmail(),
                ride.getPassenger().getPhone());
    }

    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public PaymentResponse verify(java.util.UUID passengerUserId, RazorpayVerifyRequest request) {
        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + request.paymentId()));
        // Serialize against any concurrent attempt on the same ride.
        rideRepository.findByIdForUpdate(payment.getRide().getId());

        if (!payment.getRide().getPassenger().getId().equals(passengerUserId)) {
            throw new ForbiddenException("Only the ride's passenger can confirm this payment");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return PaymentResponse.from(payment);   // idempotent: already verified
        }
        if (payment.getGatewayOrderId() == null
                || !payment.getGatewayOrderId().equals(request.razorpayOrderId())) {
            throw new PaymentException("Order id mismatch");
        }

        boolean valid;
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.razorpayOrderId());
            attributes.put("razorpay_payment_id", request.razorpayPaymentId());
            attributes.put("razorpay_signature", request.razorpaySignature());
            valid = Utils.verifyPaymentSignature(attributes, props.keySecret());
        } catch (RazorpayException e) {
            valid = false;
        }
        if (!valid) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new PaymentException("Payment signature verification failed");
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayTransactionId(request.razorpayPaymentId());
        payment.setProcessedAt(Instant.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    private static String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }
}
