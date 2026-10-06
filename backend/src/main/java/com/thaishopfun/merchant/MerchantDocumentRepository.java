package com.thaishopfun.merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.Optional;import java.util.UUID;
<<<<<<< HEAD
public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument,UUID>{List<MerchantDocument> findByApplication_Id(UUID applicationId);Optional<MerchantDocument> findByApplication_IdAndType(UUID applicationId,DocumentType type);}
=======
public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument,UUID>{
    List<MerchantDocument> findByApplication_Id(UUID applicationId);
    Optional<MerchantDocument> findByApplication_IdAndType(UUID applicationId,DocumentType type);
    boolean existsByApplication_LinkedOwner_IdAndType(UUID ownerId, DocumentType type);
}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
