package com.gongguhan.domain.product.entity;

import com.gongguhan.global.common.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_seller", columnList = "seller_id,status"),
        @Index(name = "idx_products_category", columnList = "category_id,status"),
        @Index(name = "idx_products_restriction", columnList = "restriction_review_status")
})
public class Product extends BaseTimeEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String brand;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(length = 50)
    private String color;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private long price;

    @Column(name = "ai_extracted", nullable = false)
    private boolean aiExtracted;

    @Column(name = "withdrawal_restricted", nullable = false)
    private boolean withdrawalRestricted;

    @Column(name = "restriction_reason_id")
    private Long restrictionReasonId;

    @Column(name = "restriction_evidence_url", length = 500)
    private String restrictionEvidenceUrl;

    @Column(name = "restriction_review_status", nullable = false, length = 20)
    private String restrictionReviewStatus;

    @Column(name = "restriction_reviewed_by")
    private Long restrictionReviewedBy;

    @Column(name = "restriction_reviewed_at")
    private LocalDateTime restrictionReviewedAt;

    @Column(nullable = false, length = 20)
    private String status;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, imageId ASC")
    private final List<ProductImage> images = new ArrayList<>();

    protected Product() {
    }

    public static Product create(
            Long sellerId,
            Category category,
            String name,
            String brand,
            String modelName,
            String color,
            String description,
            long price) {
        Product product = new Product();
        product.sellerId = sellerId;
        product.category = category;
        product.name = name;
        product.brand = brand;
        product.modelName = modelName;
        product.color = color;
        product.description = description;
        product.price = price;
        product.aiExtracted = false;
        product.withdrawalRestricted = false;
        product.restrictionReviewStatus = "NONE";
        product.status = STATUS_ACTIVE;
        return product;
    }

    public void update(
            Category category,
            String name,
            String brand,
            String modelName,
            String color,
            String description,
            long price) {
        this.category = category;
        this.name = name;
        this.brand = brand;
        this.modelName = modelName;
        this.color = color;
        this.description = description;
        this.price = price;
    }

    public void addImage(String imageUrl, int sortOrder, boolean thumbnail) {
        images.add(ProductImage.create(this, imageUrl, sortOrder, thumbnail));
    }

    public void replaceImages(List<ProductImage> replacement) {
        images.clear();
        images.addAll(replacement);
    }

    public void softDelete() {
        status = STATUS_DELETED;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getModelName() {
        return modelName;
    }

    public String getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public long getPrice() {
        return price;
    }

    public boolean isAiExtracted() {
        return aiExtracted;
    }

    public boolean isWithdrawalRestricted() {
        return withdrawalRestricted;
    }

    public Long getRestrictionReasonId() {
        return restrictionReasonId;
    }

    public String getRestrictionReviewStatus() {
        return restrictionReviewStatus;
    }

    public String getStatus() {
        return status;
    }

    public List<ProductImage> getImages() {
        return Collections.unmodifiableList(images);
    }
}
