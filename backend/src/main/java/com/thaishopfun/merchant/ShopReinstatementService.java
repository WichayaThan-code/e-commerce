package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ShopReinstatementService {
    private final ShopRepository shops;
    private final ShopReinstatementRequestRepository requests;
    private final UserAccountRepository users;
    private final ShopMemberRepository members;

    public ShopReinstatementService(ShopRepository shops, ShopReinstatementRequestRepository requests, UserAccountRepository users, ShopMemberRepository members) {
        this.shops = shops;
        this.requests = requests;
        this.users = users;
        this.members = members;
    }

    @Transactional(readOnly = true)
    public MerchantDtos.ReinstatementResponse current(UUID userId, Long shopId) {
        var shop = ownedSuspended(userId, shopId);
        return response(shop.getId(), requests.findFirstByShop_ShopIdOrderByCreatedAtDesc(shop.getId()).orElse(null));
    }

    @Transactional
    public MerchantDtos.ReinstatementResponse submit(UUID userId, Long shopId, String message) {
        var shop = ownedSuspended(userId, shopId);
        var text = message == null ? "" : message.trim();
        if (text.length() < 10) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "กรุณาอธิบายเหตุผลอย่างน้อย 10 ตัวอักษร");
        }
        var pending = requests.findFirstByShop_ShopIdAndStatusOrderByCreatedAtDesc(shop.getId(), ReinstatementStatus.PENDING).orElse(null);
        if (pending != null) {
            pending.revise(text);
            return response(shop.getId(), requests.save(pending));
        }
        var user = users.findById(userId).orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
        return response(shop.getId(), requests.save(new ShopReinstatementRequest(shop, user, text)));
    }

    public String pendingMessage(Long shopId) {
        return requests.findFirstByShop_ShopIdAndStatusOrderByCreatedAtDesc(shopId, ReinstatementStatus.PENDING)
            .map(ShopReinstatementRequest::getMessage)
            .orElse(null);
    }

    @Transactional
    public void approvePending(Long shopId) {
        for (var request : requests.findByShop_ShopIdAndStatus(shopId, ReinstatementStatus.PENDING)) {
            request.approve();
        }
    }

    private Shop ownedSuspended(UUID userId, Long shopId) {
        var shop = shops.findByShopId(shopId).orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า"));
        // Permission is enforced by AuthorizationInterceptor; this is defense in depth with the same answer (404).
        if (!members.existsByShopIdAndUserIdAndStatusAndRemovedAtIsNull(shop.getUuid(), userId, "ACTIVE")) {
            throw new AuthException(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND", "ไม่พบร้านค้า");
        }
        if (shop.getStatus() != ShopStatus.SUSPENDED) {
            throw new AuthException(HttpStatus.CONFLICT, "SHOP_NOT_SUSPENDED", "ยื่นคำร้องได้เฉพาะร้านที่ถูกระงับ");
        }
        return shop;
    }

    private static MerchantDtos.ReinstatementResponse response(Long shopId, ShopReinstatementRequest request) {
        if (request == null) return new MerchantDtos.ReinstatementResponse(shopId, null, null, null);
        return new MerchantDtos.ReinstatementResponse(shopId, request.getStatus().name(), request.getMessage(), request.getCreatedAt());
    }
}
