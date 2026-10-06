package com.thaishopfun.merchant;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="merchant_documents",uniqueConstraints=@UniqueConstraint(name="uq_merchant_documents_application_id_document_type",columnNames={"application_id","document_type"}))
public class MerchantDocument {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="application_id") private MerchantApplication application;
    @Enumerated(EnumType.STRING) @Column(name="document_type",nullable=false) private DocumentType type;
    @Column(name="original_filename",nullable=false) private String originalFilename;
    @Column(name="stored_filename",nullable=false) private String storedFilename;
    @Column(name="storage_path",nullable=false,length=700) private String storagePath;
    @Column(name="content_type",nullable=false,length=100) private String contentType;
    @Column(name="file_size",nullable=false) private long fileSize;
    @Column(nullable=false,length=64) private String sha256;
    @Column(name="uploaded_at",nullable=false) private Instant uploadedAt;
    protected MerchantDocument(){}
    public MerchantDocument(MerchantApplication app,DocumentType type,String original,String stored,String path,String contentType,long size,String sha256){id=UUID.randomUUID();application=app;this.type=type;originalFilename=original;storedFilename=stored;storagePath=path;this.contentType=contentType;fileSize=size;this.sha256=sha256;uploadedAt=Instant.now();}
    public UUID getId(){return id;} public DocumentType getType(){return type;} public String getOriginalFilename(){return originalFilename;} public String getStoredFilename(){return storedFilename;} public String getStoragePath(){return storagePath;} public String getContentType(){return contentType;} public long getFileSize(){return fileSize;} public String getSha256(){return sha256;} public Instant getUploadedAt(){return uploadedAt;}
}
