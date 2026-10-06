package com.thaishopfun.merchant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnerRepository extends JpaRepository<Owner, UUID> {
    List<Owner> findByUser_IdOrderByCreatedAtAsc(UUID userId);
    Optional<Owner> findByCitizenIdHash(String citizenIdHash);
}
