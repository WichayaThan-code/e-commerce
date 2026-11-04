package com.thaishopfun.help;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HelpFeedbackRepository extends JpaRepository<HelpFeedback, UUID> {
    boolean existsByFaqIdAndUserId(UUID faqId, UUID userId);

    /** Anonymous vote of a browser session (rows with a user id never carry a session key). */
    boolean existsByFaqIdAndSessionKeyAndUserIdIsNull(UUID faqId, String sessionKey);

    long countByFaqId(UUID faqId);
}
