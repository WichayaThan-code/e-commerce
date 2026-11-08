package com.thaishopfun.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "shop_invoice_sequences")
@IdClass(ShopInvoiceSequenceEntity.Pk.class)
class ShopInvoiceSequenceEntity {
    @Id
    @Column(name = "shop_id")
    private UUID shopId;

    @Id
    @Column(name = "doc_type", length = 20)
    private String docType;

    @Id
    @Column(name = "year_no")
    private int yearNo;

    @Column(name = "next_seq", nullable = false)
    private int nextSeq = 1;

    public static class Pk implements Serializable {
        private UUID shopId;
        private String docType;
        private int yearNo;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return yearNo == pk.yearNo && Objects.equals(shopId, pk.shopId) && Objects.equals(docType, pk.docType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(shopId, docType, yearNo);
        }
    }
}
