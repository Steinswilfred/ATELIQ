package com.clothingplatform.order;

import com.clothingplatform.cart.CartService.PricedItem;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.order.CheckoutPricingService.Computed;
import com.clothingplatform.order.OrderService.CheckoutRequest;
import com.clothingplatform.order.OrderService.PlacedOrder;
import com.clothingplatform.order.PricingCalculator.Totals;
import com.clothingplatform.payment.PaymentService;
import com.clothingplatform.payment.PaymentService.RazorpayCheckout;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Orchestration only: not transactional itself, so the order is committed before we call the payment provider. */
@Service
public class CheckoutService {
    public record Preview(List<PricedItem> items, int garments, Totals totals, String tailorCategory, String couponCode, List<String> errors, boolean canCheckout, boolean codEnabled, boolean onlineEnabled) {}
    public record Result(long orderId, String orderNumber, String paymentMethod, BigDecimal total, RazorpayCheckout razorpay) {}

    private final CheckoutPricingService pricing;
    private final OrderService orders;
    private final PaymentService payments;
    public CheckoutService(CheckoutPricingService pricing, OrderService orders, PaymentService payments) { this.pricing = pricing; this.orders = orders; this.payments = payments; }

    public Preview preview(long userId, Long tailorCategoryId, String couponCode) {
        Computed c = pricing.compute(userId, tailorCategoryId, couponCode, false);
        boolean ok = c.errors().isEmpty() && c.tailor() != null;
        return new Preview(c.items(), c.garments(), c.totals(), c.tailor() == null ? null : c.tailor().name(), c.coupon() == null ? null : c.coupon().code(), c.errors(), ok, c.settings().codEnabled(), payments.onlinePaymentsEnabled());
    }

    public Result checkout(long userId, CheckoutRequest r) {
        if ("RAZORPAY".equals(r.paymentMethod()) && !payments.onlinePaymentsEnabled())
            throw new BusinessException("PAYMENTS_UNAVAILABLE", "Online payment is not available right now. Choose Cash on Delivery or try later.", HttpStatus.SERVICE_UNAVAILABLE);
        PlacedOrder po = orders.place(userId, r);
        if ("COD".equals(po.paymentMethod())) return new Result(po.orderId(), po.orderNumber(), "COD", po.total(), null);
        try {
            return new Result(po.orderId(), po.orderNumber(), "RAZORPAY", po.total(), payments.createGatewayOrder(po.orderId(), userId));
        } catch (RuntimeException e) {
            orders.cancelUnpaid(po.orderId(), "The payment could not be started");   // nothing was charged; the customer can check out again
            throw e;
        }
    }

    /** Retry for an order whose online payment failed or was abandoned. */
    public Result retryPayment(long userId, long orderId) {
        var d = orders.detail(userId, orderId);
        return new Result(d.id(), d.orderNumber(), d.paymentMethod(), d.total(), payments.createGatewayOrder(orderId, userId));
    }
}
