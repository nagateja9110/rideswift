package com.rideswift.service;

import com.rideswift.dto.request.InitiatePaymentRequest;
import com.rideswift.dto.request.RefundRequest;
import com.rideswift.dto.response.PaymentResponse;
import com.rideswift.exception.ConflictException;
import com.rideswift.exception.ForbiddenException;
import com.rideswift.exception.PaymentException;
import com.rideswift.exception.ResourceNotFoundException;
import com.rideswift.model.Payment;
import com.rideswift.model.PaymentStatus;
import com.rideswift.model.Ride;
import com.rideswift.model.RideStatus;
import com.rideswift.payment.ChargeRequest;
import com.rideswift.payment.ChargeResult;
import com.rideswift.payment.PaymentGateway;
import com.rideswift.payment.PaymentGatewayFactory;
import com.rideswift.payment.RefundResult;
import com.rideswift.payment.ResilientGatewayExecutor;
import com.rideswift.repository.PaymentRepository;
import com.rideswift.repository.RideRepository;
import com.rideswift.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RideRepository rideRepository;
    private final PaymentGatewayFactory gatewayFactory;
    private final ResilientGatewayExecutor gatewayExecutor;

    public PaymentService(PaymentRepository paymentRepository,
                          RideRepository rideRepository,
                          PaymentGatewayFactory gatewayFactory,
                          ResilientGatewayExecutor gatewayExecutor) {
        this.paymentRepository = paymentRepository;
        this.rideRepository = rideRepository;
        this.gatewayFactory = gatewayFactory;
        this.gatewayExecutor = gatewayExecutor;
    }

    /**
     * Charges the passenger for a completed ride. Runs SERIALIZABLE with a
     * pessimistic lock on the ride: lock ride → create PENDING payment → charge
     * gateway → mark SUCCESS. Any failure rolls the whole transaction back.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public PaymentResponse initiate(UUID passengerUserId, InitiatePaymentRequest request) {
        Ride ride = rideRepository.findByIdForUpdate(request.rideId())
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + request.rideId()));

        if (!ride.getPassenger().getId().equals(passengerUserId)) {
            throw new ForbiddenException("Only the ride's passenger can pay for it");
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new ConflictException("Ride must be COMPLETED before payment");
        }
        if (paymentRepository.existsByRideId(ride.getId())) {
            throw new ConflictException("Payment already exists for this ride");
        }

        BigDecimal fare = ride.getActualFare();
        if (fare == null || fare.signum() <= 0) {
            throw new PaymentException("Ride has no payable fare");
        }
        BigDecimal tip = request.tipAmount() != null ? request.tipAmount() : BigDecimal.ZERO;
        BigDecimal amount = fare.add(tip);
        String currency = request.currency() != null ? request.currency() : "USD";

        Payment payment = Payment.builder()
                .ride(ride)
                .amount(amount)
                .tipAmount(tip)
                .currency(currency)
                .gateway(request.gateway())
                .status(PaymentStatus.PENDING)
                .build();
        payment = paymentRepository.save(payment);

        PaymentGateway handler = gatewayFactory.forGateway(request.gateway());
        ChargeResult result = gatewayExecutor.charge(
                handler, new ChargeRequest(amount, currency, ride.getId().toString()));
        if (!result.success()) {
            // Rolls back the PENDING row and any ride changes (spec: rollback on any failure).
            throw new PaymentException("Charge declined: " + result.message());
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayTransactionId(result.transactionId());
        payment.setProcessedAt(Instant.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    /** Admin refunds a successful payment. */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public PaymentResponse refund(RefundRequest request) {
        Payment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + request.paymentId()));
        // Serialize against any concurrent charge on the same ride.
        rideRepository.findByIdForUpdate(payment.getRide().getId());

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new ConflictException("Only a SUCCESS payment can be refunded");
        }

        RefundResult result = gatewayExecutor.refund(
                gatewayFactory.forGateway(payment.getGateway()),
                payment.getGatewayTransactionId(), payment.getAmount());
        if (!result.success()) {
            throw new PaymentException("Refund failed: " + result.message());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setProcessedAt(Instant.now());
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID userId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        Ride ride = payment.getRide();
        boolean isPassenger = ride.getPassenger().getId().equals(userId);
        boolean isAssignedDriver = ride.getDriver() != null
                && ride.getDriver().getUser().getId().equals(userId);
        if (!isPassenger && !isAssignedDriver && !SecurityUtils.isCurrentUserAdmin()) {
            throw new ForbiddenException("You are not a participant of this payment");
        }
        return PaymentResponse.from(payment);
    }
}
