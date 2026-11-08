package com.thaishopfun.merchant.delivery;

import com.thaishopfun.merchant.Shop;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes shop lifecycle mail events inside the business transaction.
 * Delivery happens AFTER_COMMIT via {@link ShopLifecycleEmailListener}.
 */
@Component
public class ShopLifecycleEmailPublisher {
    private final ApplicationEventPublisher events;

    public ShopLifecycleEmailPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void applicationSubmitted(Shop shop) {
        if (shop == null || shop.getId() == null) return;
        var contact = ownerContact(shop);
        events.publishEvent(new ShopApplicationSubmittedMailEvent(
            shop.getId(),
            blankTo(shop.getName(), "ร้านค้า"),
            contact.name(),
            contact.email(),
            contact.userLabel()
        ));
    }

    public void statusChanged(Shop shop, ShopLifecycleAction action) {
        statusChanged(shop, action, null);
    }

    public void statusChanged(Shop shop, ShopLifecycleAction action, String reason) {
        if (shop == null || shop.getId() == null || action == null) return;
        var contact = ownerContact(shop);
        if (contact.email() == null || contact.email().isBlank()) return;
        events.publishEvent(new ShopStatusChangedMailEvent(
            action,
            shop.getId(),
            blankTo(shop.getName(), "ร้านค้า"),
            contact.name(),
            contact.email(),
            reason == null || reason.isBlank() ? null : reason.trim()
        ));
    }

    private static Contact ownerContact(Shop shop) {
        String email = null;
        String first = null;
        String last = null;
        String accountEmail = null;
        String accountFirst = null;

        var owner = shop.getOwner();
        if (owner != null) {
            email = trim(owner.getEmail());
            first = trim(owner.getFirstName());
            last = trim(owner.getLastName());
        }
        var application = shop.getApplication();
        if (application != null) {
            if (email == null) email = trim(application.getOwnerEmail());
            if (first == null) first = trim(application.getOwnerFirstName());
            if (last == null) last = trim(application.getOwnerLastName());
        }
        var user = shop.getUser();
        if (user != null) {
            accountEmail = trim(user.getEmail());
            accountFirst = trim(user.getFirstName());
            if (email == null) email = accountEmail;
            if (first == null) first = accountFirst;
        }

        var name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        if (name.isBlank()) name = accountFirst;
        var userLabel = name != null && !name.isBlank()
            ? name
            : (accountEmail != null ? accountEmail : (email != null ? email : "ผู้ใช้"));
        return new Contact(name == null || name.isBlank() ? null : name, email, userLabel);
    }

    private record Contact(String name, String email, String userLabel) {}

    private static String trim(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
