package com.thaishopfun.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {
    Optional<OtpVerification> findByIdAndStatus(UUID id, OtpStatus status);

    @Query("""
        SELECT o FROM OtpVerification o
        WHERE o.destinationHash = :destinationHash AND o.purpose = :purpose AND o.status = :status
        ORDER BY o.createdDateTime DESC
        """)
    List<OtpVerification> findRecent(@Param("destinationHash") String destinationHash,
                                     @Param("purpose") OtpPurpose purpose,
                                     @Param("status") OtpStatus status);

    long countByDestinationHashAndPurposeAndCreatedDateTimeAfter(String destinationHash, OtpPurpose purpose, Instant after);
}