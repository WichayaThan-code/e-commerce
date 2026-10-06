package com.thaishopfun.merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;import java.util.UUID;
public interface MerchantApplicationRepository extends JpaRepository<MerchantApplication,UUID>{Optional<MerchantApplication> findByUser_Id(UUID userId);}

