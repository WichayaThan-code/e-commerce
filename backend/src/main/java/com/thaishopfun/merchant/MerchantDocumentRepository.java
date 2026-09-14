package com.thaishopfun.merchant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;import java.util.Optional;import java.util.UUID;
public interface MerchantDocumentRepository extends JpaRepository<MerchantDocument,UUID>{List<MerchantDocument> findByApplication_Id(UUID applicationId);Optional<MerchantDocument> findByApplication_IdAndType(UUID applicationId,DocumentType type);}
