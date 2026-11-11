package com.thaishopfun.merchant.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShopPlanExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(ShopPlanExpiryScheduler.class);
    private final ShopPlanPurchaseService purchases;

    public ShopPlanExpiryScheduler(ShopPlanPurchaseService purchases) {
        this.purchases = purchases;
    }

    /** Expire paid plans past plan_expires_at, then send 7-day / 1-day reminders. */
    @Scheduled(cron = "0 15 1 * * *", zone = "Asia/Bangkok")
    public void runDaily() {
        int expired = purchases.expireDuePlans();
        int reminded = purchases.notifyExpiringSoon();
        if (expired > 0 || reminded > 0) {
            log.info("Plan expiry job: expired={} reminders={}", expired, reminded);
        }
    }
}
