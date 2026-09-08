package com.gongguhan.domain.product.service;

import com.gongguhan.domain.product.dto.ProductResponse;
import com.gongguhan.domain.product.dto.ProductUpsertRequest;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.entity.ProductImage;
import com.gongguhan.domain.product.repository.CategoryRepository;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageStorage imageStorage;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductImageStorage imageStorage) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.imageStorage = imageStorage;
    }

    @Transactional
    public ProductResponse create(Long sellerId, ProductUpsertRequest request, List<MultipartFile> images) {
        ensureUsableSeller(sellerId);
        Category category = findCategory(request.categoryId());
        validateImages(images, request.thumbnailIndex(), true);

        List<ProductImageStorage.StoredImage> storedImages = storeAll(images);
        cleanupOnRollback(storedImages);

        Product product = Product.create(
                sellerId,
                category,
                request.name(),
                request.brand(),
                request.modelName(),
                request.color(),
                request.description(),
                request.price());
        addImages(product, storedImages, request.thumbnailIndex());
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse update(
            Long sellerId,
            Long productId,
            ProductUpsertRequest request,
            List<MultipartFile> replacementImages) {
        ensureUsableSeller(sellerId);
        Product product = findOwnedProduct(sellerId, productId);
        ensureNoActiveGroupBuy(productId);
        Category category = findCategory(request.categoryId());

        product.update(
                category,
                request.name(),
                request.brand(),
                request.modelName(),
                request.color(),
                request.description(),
                request.price());

        if (replacementImages != null && !replacementImages.isEmpty()) {
            validateImages(replacementImages, request.thumbnailIndex(), false);
            List<String> previousUrls = product.getImages().stream().map(ProductImage::getImageUrl).toList();
            List<ProductImageStorage.StoredImage> storedImages = storeAll(replacementImages);
            cleanupOnRollback(storedImages);

            List<ProductImage> replacements = new ArrayList<>();
            for (int i = 0; i < storedImages.size(); i++) {
                ProductImageStorage.StoredImage stored = storedImages.get(i);
                replacements.add(ProductImage.create(product, stored.publicUrl(), i, i == request.thumbnailIndex()));
            }
            product.replaceImages(replacements);
            deleteAfterCommit(previousUrls);
        }

        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void delete(Long sellerId, Long productId) {
        ensureUsableSeller(sellerId);
        Product product = findOwnedProduct(sellerId, productId);
        ensureNoActiveGroupBuy(productId);
        product.softDelete();
    }

    public Page<ProductResponse> getSellerProducts(Long sellerId, Pageable pageable) {
        return productRepository.findBySellerIdAndStatus(sellerId, Product.STATUS_ACTIVE, pageable)
                .map(ProductResponse::from);
    }

    private void ensureUsableSeller(Long sellerId) {
        if (sellerId == null || productRepository.countUsableSeller(sellerId) == 0) {
            throw new BusinessException(ErrorCode.SELLER_SUSPENDED);
        }
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "카테고리를 찾을 수 없습니다."));
    }

    private Product findOwnedProduct(Long sellerId, Long productId) {
        return productRepository.findByProductIdAndSellerIdAndStatus(productId, sellerId, Product.STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    private void ensureNoActiveGroupBuy(Long productId) {
        if (productRepository.countActiveGroupBuys(productId) > 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE_TRANSITION,
                    "진행 중인 공동구매에 연결된 상품은 수정하거나 삭제할 수 없습니다.");
        }
    }

    private void validateImages(List<MultipartFile> images, int thumbnailIndex, boolean required) {
        if (images == null || images.isEmpty()) {
            if (required) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "상품 이미지를 한 장 이상 등록해야 합니다.");
            }
            return;
        }
        if (thumbnailIndex < 0 || thumbnailIndex >= images.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "대표 이미지 위치가 올바르지 않습니다.");
        }
    }

    private List<ProductImageStorage.StoredImage> storeAll(List<MultipartFile> images) {
        List<ProductImageStorage.StoredImage> stored = new ArrayList<>();
        try {
            for (MultipartFile image : images) {
                stored.add(imageStorage.store(image));
            }
            return stored;
        } catch (RuntimeException e) {
            stored.forEach(image -> imageStorage.deleteByUrl(image.publicUrl()));
            throw e;
        }
    }

    private void addImages(
            Product product, List<ProductImageStorage.StoredImage> storedImages, int thumbnailIndex) {
        for (int i = 0; i < storedImages.size(); i++) {
            product.addImage(storedImages.get(i).publicUrl(), i, i == thumbnailIndex);
        }
    }

    private void cleanupOnRollback(List<ProductImageStorage.StoredImage> storedImages) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storedImages.forEach(image -> imageStorage.deleteByUrl(image.publicUrl()));
                }
            }
        });
    }

    private void deleteAfterCommit(List<String> imageUrls) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageUrls.forEach(imageStorage::deleteByUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageUrls.forEach(imageStorage::deleteByUrl);
            }
        });
    }
}
