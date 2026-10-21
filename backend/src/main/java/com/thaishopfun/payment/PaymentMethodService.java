package com.thaishopfun.payment;

import com.thaishopfun.order.CodSettingsService;
import com.thaishopfun.order.PaymentMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payment methods a shop offers at checkout: BANK_TRANSFER (always) and COD (per-shop setting, V29). */
@Service
public class PaymentMethodService {
    private final CodSettingsService cod;
    private final PaymentAccountService accounts;

    public PaymentMethodService(CodSettingsService cod, PaymentAccountService accounts) {
        this.cod = cod;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public PaymentDtos.PaymentMethodsView view(Long shopNo, UUID shopId) {
        return view(shopNo, shopId, cod.settings(shopId));
    }

    @Transactional
    public PaymentDtos.PaymentMethodsView updateCod(Long shopNo, UUID shopId, PaymentDtos.CodSettingsRequest body, UUID userId) {
        var req = body == null ? new PaymentDtos.CodSettingsRequest(null, null, null) : body;
        return view(shopNo, shopId, cod.update(shopId, req.enabled(), req.fee(), req.maxOrderAmount(), userId));
    }

    private PaymentDtos.PaymentMethodsView view(Long shopNo, UUID shopId, CodSettingsService.Settings settings) {
        var bank = new PaymentDtos.PaymentMethodView(PaymentMethod.BANK_TRANSFER.name(), true, BigDecimal.ZERO.setScale(2), null,
            !accounts.activeProviders(shopId).isEmpty(), null);
        var codView = new PaymentDtos.PaymentMethodView(PaymentMethod.COD.name(), settings.enabled(), settings.fee(), settings.maxOrderAmount(),
            null, settings.updatedAt());
        return new PaymentDtos.PaymentMethodsView(shopNo, List.of(bank, codView));
    }
}
