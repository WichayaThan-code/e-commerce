package com.thaishopfun.cart;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.common.FieldViolation;
import com.thaishopfun.common.ValidationException;
import com.thaishopfun.merchant.Shop;
import com.thaishopfun.merchant.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Merchant shipping rules (V1 shipping_methods + V24 extra_fee_per_kg). */
@Service
public class ShippingMethodService {
    private static final Set<String> FEE_TYPES = Set.of("FLAT", "WEIGHT", "FREE");

    private final ShippingMethodRepository methods;
    private final ShopRepository shops;

    public ShippingMethodService(ShippingMethodRepository methods, ShopRepository shops) {
        this.methods = methods;
        this.shops = shops;
    }

    @Transactional(readOnly = true)
    public List<CartDtos.ShippingMethodResponse> list(Long shopNo) {
        var shop = shop(shopNo);
        return methods.findByShopIdOrderByCreatedDateTimeAsc(shop.getUuid()).stream().map(method -> toResponse(shop, method)).toList();
    }

    @Transactional
    public CartDtos.ShippingMethodResponse create(UUID userId, Long shopNo, CartDtos.ShippingMethodRequest request) {
        var shop = shop(shopNo);
        var method = new ShippingMethod(shop.getUuid());
        apply(method, request);
        method.stamp(userId);
        return toResponse(shop, methods.save(method));
    }

    @Transactional
    public CartDtos.ShippingMethodResponse update(UUID userId, Long shopNo, UUID id, CartDtos.ShippingMethodRequest request) {
        var shop = shop(shopNo);
        var method = owned(shop, id);
        apply(method, request);
        method.stamp(userId);
        return toResponse(shop, methods.save(method));
    }

    @Transactional
    public CartDtos.ShippingMethodResponse setActive(UUID userId, Long shopNo, UUID id, boolean active) {
        var shop = shop(shopNo);
        var method = owned(shop, id);
        method.setActive(active);
        method.stamp(userId);
        return toResponse(shop, methods.save(method));
    }

    private static void apply(ShippingMethod method, CartDtos.ShippingMethodRequest request) {
        var errors = new ArrayList<FieldViolation>();
        if (request == null) throw new ValidationException(List.of(new FieldViolation("name", "REQUIRED", "ต้องกรอกชื่อวิธีจัดส่ง")));
        var name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) errors.add(new FieldViolation("name", "REQUIRED", "ต้องกรอกชื่อวิธีจัดส่ง"));
        else if (name.length() > 100) errors.add(new FieldViolation("name", "MAX_LENGTH", "ชื่อยาวเกิน 100 ตัวอักษร"));
        var type = request.feeType() == null ? "FLAT" : request.feeType().trim().toUpperCase();
        if (!FEE_TYPES.contains(type)) errors.add(new FieldViolation("feeType", "INVALID", "ประเภทค่าส่งต้องเป็น FLAT, WEIGHT หรือ FREE"));
        var fee = request.feeAmount() == null ? BigDecimal.ZERO : request.feeAmount();
        if (fee.signum() < 0) errors.add(new FieldViolation("feeAmount", "MIN", "ค่าส่งต้องไม่ติดลบ"));
        if (!"FREE".equals(type) && request.feeAmount() == null) errors.add(new FieldViolation("feeAmount", "REQUIRED", "ต้องกรอกค่าส่ง"));
        if (request.extraFeePerKg() != null && request.extraFeePerKg().signum() < 0) {
            errors.add(new FieldViolation("extraFeePerKg", "MIN", "ค่าส่งต่อกิโลต้องไม่ติดลบ"));
        }
        if (request.freeShippingMinAmount() != null && request.freeShippingMinAmount().signum() < 0) {
            errors.add(new FieldViolation("freeShippingMinAmount", "MIN", "ยอดส่งฟรีต้องไม่ติดลบ"));
        }
        if (request.estimatedDaysMin() != null && request.estimatedDaysMin() < 0) {
            errors.add(new FieldViolation("estimatedDaysMin", "MIN", "จำนวนวันต้องไม่ติดลบ"));
        }
        if (request.estimatedDaysMin() != null && request.estimatedDaysMax() != null && request.estimatedDaysMax() < request.estimatedDaysMin()) {
            errors.add(new FieldViolation("estimatedDaysMax", "MIN", "วันสูงสุดต้องไม่น้อยกว่าวันต่ำสุด"));
        }
        if (request.carrierCode() != null && request.carrierCode().length() > 50) {
            errors.add(new FieldViolation("carrierCode", "MAX_LENGTH", "รหัสขนส่งยาวเกิน 50 ตัวอักษร"));
        }
        if (!errors.isEmpty()) throw new ValidationException(ValidationException.topCode(errors), errors);
        method.setName(name);
        method.setCarrierCode(request.carrierCode() == null || request.carrierCode().isBlank() ? null : request.carrierCode().trim().toUpperCase());
        method.setFeeType(type);
        method.setFeeAmount("FREE".equals(type) ? CartMoney.zero() : CartMoney.of(fee));
        method.setExtraFeePerKg(request.extraFeePerKg() == null ? null : CartMoney.of(request.extraFeePerKg()));
        method.setMinOrderAmount(request.freeShippingMinAmount() == null ? null : CartMoney.of(request.freeShippingMinAmount()));
        method.setEstimatedDaysMin(request.estimatedDaysMin());
        method.setEstimatedDaysMax(request.estimatedDaysMax());
        if (request.active() != null) method.setActive(request.active());
    }

    private CartDtos.ShippingMethodResponse toResponse(Shop shop, ShippingMethod method) {
        return new CartDtos.ShippingMethodResponse(method.getId(), shop.getId(), method.getName(), method.getCarrierCode(),
            method.getFeeType(), CartMoney.of(method.getFeeAmount()),
            method.getExtraFeePerKg() == null ? null : CartMoney.of(method.getExtraFeePerKg()),
            method.getMinOrderAmount() == null ? null : CartMoney.of(method.getMinOrderAmount()),
            method.getEstimatedDaysMin(), method.getEstimatedDaysMax(), method.isActive());
    }

    private ShippingMethod owned(Shop shop, UUID id) {
        return methods.findByIdAndShopId(id, shop.getUuid())
            .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHIPPING_METHOD_NOT_FOUND", "ไม่พบวิธีจัดส่ง"));
    }

    private Shop shop(Long shopNo) {
        return shops.findByShopId(shopNo).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้านี้"));
    }
}
