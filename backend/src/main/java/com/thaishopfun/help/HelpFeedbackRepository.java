package com.thaishopfun.help;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HelpFeedbackRepository extends JpaRepository<HelpFeedback, UUID> {
    Optional<HelpFeedback> findByFaqIdAndUserId(UUID faqId, UUID userId);

    /** Anonymous vote of a browser session (rows with a user id never carry a session key). */
    Optional<HelpFeedback> findByFaqIdAndSessionKeyAndUserIdIsNull(UUID faqId, String sessionKey);

    long countByFaqId(UUID faqId);
}
