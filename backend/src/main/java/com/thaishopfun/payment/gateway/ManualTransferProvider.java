package com.thaishopfun.payment.gateway;

import com.thaishopfun.order.Order;
import com.thaishopfun.order.Payment;
import com.thaishopfun.payment.PaymentAccountService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Direct transfer to the shop's own bank account / PromptPay. No money passes through the platform and there is no
 * webhook: the buyer uploads a slip and the shop verifies it (OrderPaymentService). Registered for BANK_TRANSFER;
 * PROMPTPAY uses the same flow through the same service.
 */
@Component
public class ManualTransferProvider implements PaymentGateway {
    private final PaymentAccountService accounts;

    public ManualTransferProvider(PaymentAccountService accounts) {
        this.accounts = accounts;
    }

    @Override public String provider() { return "BANK_TRANSFER"; }

    @Override public boolean webhookEnabled() { return false; }

    @Override
    public PaymentIntent createPaymentIntent(Order order) {
        var instructions = new LinkedHashMap<String, Object>();
        instructions.put("type", "MANUAL_TRANSFER");
        instructions.put("accounts", accounts.activeForBuyer(order.getShopId()));
        instructions.put("next", "upload slip: POST /api/orders/" + order.getId() + "/payments");
        return new PaymentIntent(provider(), null, order.getGrandTotal(), order.getCurrency(), instructions);
    }

    @Override
    public WebhookEvent verifyWebhook(Map<String, String> headers, byte[] body) {
        throw new WebhookSignatureException("manual transfer has no webhook");
    }

    @Override
    public PaymentStatusResult queryStatus(Payment payment) {
        return new PaymentStatusResult(payment.getStatus(), payment.getProviderPaymentId(), payment.getAmount());
    }
}
