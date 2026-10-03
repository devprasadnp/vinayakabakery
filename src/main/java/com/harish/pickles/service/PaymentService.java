package com.harish.pickles.service;

import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderStatus;
import com.harish.pickles.domain.Payment;
import com.harish.pickles.domain.PaymentStatus;
import com.harish.pickles.dto.Money;
import com.harish.pickles.dto.OrderDtos.PaymentInitResponse;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;
import com.harish.pickles.payment.GatewayState;
import com.harish.pickles.payment.PaymentGateway;
import com.harish.pickles.payment.dto.PhonePePaymentDetail;
import com.harish.pickles.payment.dto.PhonePeWebhookRequest;
import com.harish.pickles.repository.OrderRepository;
import com.harish.pickles.repository.PaymentRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the money side of an order:
 * <pre>
 * placeOrder -> initiate() -> customer pays on PhonePe -> redirect back  -> reconcile()
 *                                                      \-> webhook      -> handleWebhook()
 * </pre>
 * Both the redirect and the webhook funnel into the same idempotent {@code apply(...)},
 * so a duplicate callback can never double-count an order.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    public PaymentService(PaymentGateway gateway, PaymentRepository paymentRepository,
                          OrderRepository orderRepository, OrderService orderService) {
        this.gateway = gateway;
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }

    public String gatewayName() {
        return gateway.name();
    }

    /** Creates (or reuses) the payment attempt and returns the URL the browser must go to. */
    @Transactional
    public PaymentInitResponse initiate(Order order) {
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BusinessException("Order " + order.getOrderNumber() + " is already paid");
        }
        if (!order.isPayable()) {
            throw new BusinessException("Order " + order.getOrderNumber()
                    + " cannot be paid in state " + order.getStatus());
        }
        // Callers cancel such orders first (OrderService.cancelIfOutsideDeliveryArea); this is the backstop.
        if (!orderService.isDeliverable(order)) {
            throw new BusinessException(OrderService.outsideAreaMessage(order));
        }

        Payment payment = paymentRepository.findByMerchantOrderId(order.getOrderNumber())
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setOrder(order);
                    p.setMerchantOrderId(order.getOrderNumber());
                    p.setAmountPaise(order.getTotalPaise());
                    return p;
                });
        payment.setProvider(gateway.name());
        payment.setAmountPaise(order.getTotalPaise());
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setErrorCode(null);
        payment.setErrorMessage(null);

        InitiateResult result = gateway.initiate(new InitiateRequest(
                order.getOrderNumber(),
                order.getTotalPaise(),
                "Sri Vinayaka Bakery Home order " + order.getOrderNumber(),
                order.getShippingAddress() == null ? null : order.getShippingAddress().getContactName(),
                order.getShippingAddress() == null ? null : order.getShippingAddress().getContactPhone()));

        payment.setProviderOrderId(result.providerOrderId());
        payment.setRedirectUrl(result.redirectUrl());
        payment.setQrPayload(result.qrPayload());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setRawResponse(result.rawResponse());
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PENDING_PAYMENT);
        orderRepository.save(order);

        return new PaymentInitResponse(order.getOrderNumber(), payment.getMerchantOrderId(),
                result.providerOrderId(), Money.toRupees(order.getTotalPaise()),
                result.redirectUrl(), result.state().name());
    }

    /** Asks the gateway what actually happened. Safe to call repeatedly. */
    @Transactional
    public Payment reconcile(String merchantOrderId) {
        Payment payment = paymentRepository.findByMerchantOrderId(merchantOrderId)
                .orElseThrow(() -> NotFoundException.of("Payment", merchantOrderId));
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return payment;
        }
        StatusResult status = gateway.checkStatus(merchantOrderId);
        return apply(payment, status.state(), status.transactionId(), status.paymentMode(),
                status.errorCode(), status.errorMessage(), status.rawResponse(), status.providerOrderId());
    }

    /** Entry point for the PhonePe server-to-server callback. */
    @Transactional
    public Optional<Payment> handleWebhook(PhonePeWebhookRequest request) {
        if (request == null || request.payload() == null) {
            return Optional.empty();
        }
        String merchantOrderId = request.payload().merchantOrderId() != null
                ? request.payload().merchantOrderId()
                : request.payload().originalMerchantOrderId();
        if (merchantOrderId == null) {
            log.warn("PhonePe webhook without a merchantOrderId, event={}", request.event());
            return Optional.empty();
        }

        Optional<Payment> found = paymentRepository.findByMerchantOrderId(merchantOrderId);
        if (found.isEmpty()) {
            log.warn("PhonePe webhook for unknown order {}", merchantOrderId);
            return Optional.empty();
        }

        Payment payment = found.get();
        String event = request.event() == null ? "" : request.event();
        if (event.startsWith("pg.refund.")) {
            if (event.endsWith("completed")) {
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.getOrder().setStatus(OrderStatus.REFUNDED);
                paymentRepository.save(payment);
            }
            return Optional.of(payment);
        }

        PhonePePaymentDetail latest = request.payload().latestAttempt();
        GatewayState state = GatewayState.from(request.payload().state());
        return Optional.of(apply(payment, state,
                latest == null ? null : latest.transactionId(),
                latest == null ? null : latest.paymentMode(),
                request.payload().errorCode(),
                request.payload().detailedErrorCode(),
                null,
                request.payload().orderId()));
    }

    /**
     * Entry point for the Paytm server-to-server callback. {@code params} is the posted form,
     * with {@code CHECKSUMHASH} already verified by the caller
     * ({@link com.harish.pickles.web.rest.PaymentWebhookController}) - this method trusts it.
     */
    @Transactional
    public Optional<Payment> handlePaytmWebhook(Map<String, String> params) {
        String orderId = params.get("ORDERID");
        if (orderId == null || orderId.isBlank()) {
            log.warn("Paytm webhook without an ORDERID");
            return Optional.empty();
        }
        Optional<Payment> found = paymentRepository.findByMerchantOrderId(orderId);
        if (found.isEmpty()) {
            log.warn("Paytm webhook for unknown order {}", orderId);
            return Optional.empty();
        }
        GatewayState state = GatewayState.from(params.get("STATUS"));
        return Optional.of(apply(found.get(), state, params.get("TXNID"), params.get("PAYMENTMODE"),
                params.get("RESPCODE"), params.get("RESPMSG"), params.toString(), orderId));
    }

    /**
     * The single place where a gateway outcome is written to our order.
     * Idempotent: applying COMPLETED twice does not release or re-reserve stock twice.
     */
    private Payment apply(Payment payment, GatewayState state, String transactionId, String paymentMode,
                          String errorCode, String errorMessage, String rawResponse, String providerOrderId) {
        Order order = payment.getOrder();

        if (providerOrderId != null) {
            payment.setProviderOrderId(providerOrderId);
        }
        if (transactionId != null) {
            payment.setTransactionId(transactionId);
        }
        if (paymentMode != null) {
            payment.setPaymentMode(paymentMode);
        }
        if (rawResponse != null) {
            payment.setRawResponse(rawResponse);
        }

        switch (state) {
            case COMPLETED -> {
                if (payment.getStatus() != PaymentStatus.COMPLETED) {
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setErrorCode(null);
                    payment.setErrorMessage(null);
                    order.setStatus(OrderStatus.PAID);
                    log.info("Order {} marked PAID (txn {})", order.getOrderNumber(), payment.getTransactionId());
                }
            }
            case FAILED -> {
                if (payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.COMPLETED) {
                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setErrorCode(errorCode);
                    payment.setErrorMessage(errorMessage);
                    order.setStatus(OrderStatus.PAYMENT_FAILED);
                    orderService.releaseStock(order);
                    log.info("Order {} payment FAILED ({})", order.getOrderNumber(), errorCode);
                }
            }
            default -> {
                if (payment.getStatus() == PaymentStatus.INITIATED) {
                    payment.setStatus(PaymentStatus.PENDING);
                }
            }
        }

        orderRepository.save(order);
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findByMerchantOrderId(String merchantOrderId) {
        return paymentRepository.findByMerchantOrderId(merchantOrderId);
    }

    /** For the admin orders list: one lookup for the whole page, keyed by order number, for audit display. */
    @Transactional(readOnly = true)
    public Map<String, Payment> findByMerchantOrderIds(Collection<String> merchantOrderIds) {
        return paymentRepository.findByMerchantOrderIdIn(merchantOrderIds).stream()
                .collect(Collectors.toMap(Payment::getMerchantOrderId, p -> p));
    }

    /**
     * Admin confirmation for the manual UPI-QR flow: after checking their bank/UPI app, the
     * admin types in the UTR/reference id and the amount received. We verify the amount matches
     * the order total, and that the reference hasn't already been used to pay a different order,
     * before flipping the order to PAID. There is no gateway here to ask, so this human check
     * stands in for {@link #apply}.
     *
     * <p>Also callable when the order is already PAID, so a wrong reference/amount typed in the
     * first time can be corrected — the record stays around afterwards for audit.</p>
     */
    @Transactional
    public Payment confirmManualPayment(String orderNumber, String transactionId, long amountPaise) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> NotFoundException.of("Order", orderNumber));
        if (!order.isPayable() && order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException("Order " + orderNumber
                    + " cannot be paid in state " + order.getStatus());
        }
        // An order we can no longer deliver is not accepted. Correcting an already-PAID order is still allowed.
        if (order.getStatus() != OrderStatus.PAID && !orderService.isDeliverable(order)) {
            throw new BusinessException("Order " + orderNumber + " is for PIN code "
                    + order.getShippingAddress().getPincode() + ", which is outside the delivery area, so it cannot"
                    + " be accepted. Cancel it and refund the customer if they have paid.");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new BusinessException("UPI reference / transaction id is required");
        }
        if (amountPaise != order.getTotalPaise()) {
            throw new BusinessException("Amount " + Money.format(amountPaise)
                    + " does not match the order total " + Money.format(order.getTotalPaise()));
        }
        String txnId = transactionId.trim();
        paymentRepository.findByTransactionIdAndStatus(txnId, PaymentStatus.COMPLETED)
                .filter(existing -> !existing.getMerchantOrderId().equals(orderNumber))
                .ifPresent(existing -> {
                    throw new BusinessException("Reference " + txnId
                            + " is already used for order " + existing.getMerchantOrderId());
                });

        Payment payment = paymentRepository.findByMerchantOrderId(orderNumber)
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setOrder(order);
                    p.setMerchantOrderId(orderNumber);
                    return p;
                });
        payment.setProvider("MANUAL_QR");
        payment.setAmountPaise(amountPaise);
        payment.setTransactionId(txnId);
        payment.setPaymentMode("UPI");
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setErrorCode(null);
        payment.setErrorMessage(null);
        paymentRepository.save(payment);

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);

        log.info("Order {} marked PAID manually (txn {})", orderNumber, txnId);
        return payment;
    }
}
