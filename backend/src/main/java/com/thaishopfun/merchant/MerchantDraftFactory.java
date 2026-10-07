package com.thaishopfun.merchant;

import com.thaishopfun.auth.AuthException;
import com.thaishopfun.auth.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Creates a new DRAFT application in its own transaction. If a concurrent request already created the
 * user's draft, the partial unique index (one DRAFT per user) rejects the insert; because this runs in a
 * separate transaction the caller's transaction stays usable and can simply re-read the existing draft.
 */
@Component
class MerchantDraftFactory {
    private final MerchantApplicationRepository applications;
    private final UserAccountRepository users;

    MerchantDraftFactory(MerchantApplicationRepository applications, UserAccountRepository users) {
        this.applications = applications;
        this.users = users;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createDraft(UUID userId) {
        var user = users.findById(userId)
            .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "ไม่พบบัญชีผู้ใช้"));
        applications.saveAndFlush(new MerchantApplication(user));
    }
}
