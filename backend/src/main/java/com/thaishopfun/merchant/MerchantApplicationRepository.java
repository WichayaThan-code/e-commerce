package com.thaishopfun.merchant;
import org.springframework.data.jpa.repository.JpaRepository;
<<<<<<< HEAD
import java.util.Optional;import java.util.UUID;
public interface MerchantApplicationRepository extends JpaRepository<MerchantApplication,UUID>{Optional<MerchantApplication> findByUser_Id(UUID userId);}
=======
import java.util.Optional;
import java.util.UUID;

public interface MerchantApplicationRepository extends JpaRepository<MerchantApplication, UUID> {
    Optional<MerchantApplication> findFirstByUser_IdAndStatusOrderByUpdatedAtDesc(UUID userId, ApplicationStatus status);
    boolean existsByShopNameIgnoreCaseAndIdNot(String shopName, UUID id);
}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb

