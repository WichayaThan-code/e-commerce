package com.thaishopfun.merchant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_categories")
public class ProductCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;
    @Column(name = "name_th", nullable = false, unique = true, length = 100)
    private String nameTh;
    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;
    @Column(name = "name_zh", nullable = false, length = 100)
    private String nameZh;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean active = true;

    protected ProductCategory() {}

    public ProductCategory(String nameTh, String nameEn, String nameZh, int sortOrder) {
        this.nameTh = nameTh;
        this.nameEn = nameEn;
        this.nameZh = nameZh;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getNameTh() { return nameTh; }
    public String getNameEn() { return nameEn; }
    public String getNameZh() { return nameZh; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}
