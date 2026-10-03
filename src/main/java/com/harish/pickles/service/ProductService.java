package com.harish.pickles.service;

import com.harish.pickles.domain.InventoryCategory;
import com.harish.pickles.domain.PickleType;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.ProductImage;
import com.harish.pickles.dto.Money;
import com.harish.pickles.dto.ProductDtos.ProductRequest;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.config.AppProperties;
import com.harish.pickles.repository.InventoryCategoryRepository;
import com.harish.pickles.repository.OrderItemRepository;
import com.harish.pickles.repository.ProductImageRepository;
import com.harish.pickles.repository.ProductRepository;
import com.harish.pickles.repository.ProductSpecifications;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryCategoryRepository categoryRepository;
    private final StorageService storageService;
    private final AppProperties appProperties;

    public ProductService(ProductRepository productRepository, ProductImageRepository imageRepository,
                          OrderItemRepository orderItemRepository, InventoryCategoryRepository categoryRepository,
                          StorageService storageService, AppProperties appProperties) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.orderItemRepository = orderItemRepository;
        this.categoryRepository = categoryRepository;
        this.storageService = storageService;
        this.appProperties = appProperties;
    }

    /**
     * Translates a "sort" query param used by both the storefront and admin product
     * screens into a real {@link Sort}. Falls back to name order for anything unknown.
     */
    public static Sort sortFor(String key) {
        if (key == null) {
            return Sort.by("name").ascending();
        }
        return switch (key) {
            case "price_asc" -> Sort.by("pricePaise").ascending();
            case "price_desc" -> Sort.by("pricePaise").descending();
            case "newest" -> Sort.by("createdAt").descending();
            case "stock_asc" -> Sort.by("stockQty").ascending();
            case "stock_desc" -> Sort.by("stockQty").descending();
            case "name_desc" -> Sort.by("name").descending();
            default -> Sort.by("name").ascending();
        };
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Page<Product> browse(PickleType type, Long categoryId, String query, Pageable pageable) {
        Page<Product> page = productRepository.findAll(
                ProductSpecifications.storefront(type, categoryId, query), pageable);
        page.getContent().forEach(p -> { p.getImages().size(); p.getInventoryCategory().getName(); });
        return page;
    }

    /** A handful of live foods of one type, for the home page rails. */
    @Transactional(readOnly = true)
    public List<Product> featured(PickleType type, int limit) {
        return browse(type, null, null, PageRequest.of(0, limit, Sort.by("name"))).getContent();
    }

    @Transactional(readOnly = true)
    public Page<Product> catalogue(PickleType type, Long categoryId, String query, Boolean activeOnly,
                                   Pageable pageable) {
        Page<Product> page = productRepository.findAll(
                ProductSpecifications.catalogue(type, categoryId, query, activeOnly), pageable);
        page.getContent().forEach(p -> { p.getImages().size(); p.getInventoryCategory().getName(); });
        return page;
    }

    @Transactional(readOnly = true)
    public Product getBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> NotFoundException.of("Product", slug));
        product.getImages().size();
        product.getInventoryCategory().getName();
        return product;
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Product", id));
        product.getImages().size();
        product.getInventoryCategory().getName();
        return product;
    }

    @Transactional(readOnly = true)
    public List<Product> allForAdmin() {
        return productRepository.findByArchivedFalseOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Product> archived() {
        List<Product> list = productRepository.findByArchivedTrueOrderByArchivedAtDesc();
        list.forEach(p -> { p.getImages().size(); p.getInventoryCategory().getName(); });
        return list;
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return productRepository.findDistinctCategories();
    }

    /** How many past order lines reference this food. Zero means it can be deleted for good. */
    @Transactional(readOnly = true)
    public long orderReferences(Long productId) {
        return orderItemRepository.countByProductId(productId);
    }

    // ----------------------------------------------------------------- write

    @Transactional
    public Product create(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new BusinessException("SKU " + request.sku() + " is already used");
        }
        Product product = new Product();
        product.setSku(request.sku().trim());
        product.setSlug(uniqueSlug(request.name()));
        apply(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = getById(id);
        if (!product.getSku().equalsIgnoreCase(request.sku())
                && productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new BusinessException("SKU " + request.sku() + " is already used");
        }
        product.setSku(request.sku().trim());
        if (!product.getName().equalsIgnoreCase(request.name())) {
            product.setSlug(uniqueSlug(request.name()));
        }
        apply(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Product product = getById(id);
        if (product.isArchived() && active) {
            throw new BusinessException("Restore " + product.getName() + " from the archive first");
        }
        product.setActive(active);
        productRepository.save(product);
    }

    // --------------------------------------------------------------- archive

    /** Super admin: take a food out of every listing without destroying it. */
    @Transactional
    public Product archive(Long id) {
        Product product = getById(id);
        product.setArchived(true);
        product.setActive(false);
        product.setArchivedAt(LocalDateTime.now());
        log.info("Product {} ({}) archived", product.getId(), product.getName());
        return productRepository.save(product);
    }

    @Transactional
    public Product restore(Long id) {
        Product product = getById(id);
        product.setArchived(false);
        product.setArchivedAt(null);
        return productRepository.save(product);
    }

    /**
     * Super admin: remove the row and its photos for good. Refused while any past
     * order still references the food, because that would rewrite order history.
     */
    @Transactional
    public String deletePermanently(Long id) {
        Product product = getById(id);
        if (!product.isArchived()) {
            throw new BusinessException("Archive " + product.getName() + " before deleting it permanently");
        }
        long references = orderItemRepository.countByProductId(id);
        if (references > 0) {
            throw new BusinessException(product.getName() + " appears in " + references
                    + " past order line(s) and cannot be deleted. Keep it archived instead.");
        }
        List<String> files = product.getImages().stream().map(ProductImage::getFilename).toList();
        String name = product.getName();
        productRepository.delete(product);
        files.forEach(storageService::delete);
        log.warn("Product {} ({}) permanently deleted with {} photo(s)", id, name, files.size());
        return name;
    }

    // ---------------------------------------------------------------- photos

    /** Stores each uploaded file and attaches it to the product. */
    @Transactional
    public int addImages(Long productId, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return 0;
        }
        Product product = getById(productId);
        int max = appProperties.getStorage().getMaxImagesPerProduct();
        int existing = product.getImages().size();
        int added = 0;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (existing + added >= max) {
                throw new BusinessException("A food can carry at most " + max
                        + " photos. Remove one before adding another.");
            }
            String filename = storageService.store(file);
            ProductImage image = new ProductImage();
            image.setFilename(filename);
            image.setOriginalName(file.getOriginalFilename());
            image.setContentType(file.getContentType());
            image.setSizeBytes(file.getSize());
            image.setSortOrder(existing + added);
            image.setPrimary(existing == 0 && added == 0);
            product.addImage(image);
            added++;
        }
        if (added > 0) {
            productRepository.save(product);
            log.info("Added {} photo(s) to product {}", added, productId);
        }
        return added;
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        Product product = getById(productId);
        ProductImage target = product.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("Photo", imageId));

        boolean wasPrimary = target.isPrimary();
        String filename = target.getFilename();
        product.getImages().remove(target);

        if (wasPrimary && !product.getImages().isEmpty()) {
            product.getImages().get(0).setPrimary(true);
        }
        productRepository.save(product);
        storageService.delete(filename);
    }

    @Transactional
    public void setPrimaryImage(Long productId, Long imageId) {
        Product product = getById(productId);
        boolean found = false;
        for (ProductImage image : product.getImages()) {
            boolean isTarget = image.getId().equals(imageId);
            image.setPrimary(isTarget);
            found = found || isTarget;
        }
        if (!found) {
            throw NotFoundException.of("Photo", imageId);
        }
        productRepository.save(product);
    }

    // --------------------------------------------------------------- helpers

    private void apply(Product product, ProductRequest r) {
        product.setName(r.name().trim());
        product.setDescription(r.description());
        product.setPickleType(r.pickleType());
        product.setCategory(r.category().trim());
        InventoryCategory inventoryCategory = categoryRepository.findById(r.inventoryCategoryId())
                .orElseThrow(() -> NotFoundException.of("Category", r.inventoryCategoryId()));
        product.setInventoryCategory(inventoryCategory);
        product.setSpiceLevel(r.spiceLevel());
        product.setPricePaise(Money.toPaise(r.price()));
        product.setMrpPaise(r.mrp() == null ? null : Money.toPaise(r.mrp()));
        product.setWeightGrams(r.weightGrams());
        product.setShelfLifeDays(r.shelfLifeDays() == null ? 365 : r.shelfLifeDays());
        product.setIngredients(r.ingredients());
        product.setImageUrl(r.imageUrl());
        product.setStockQty(r.stockQty());
        product.setActive(r.active() == null || r.active());
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (productRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "pickle" : normalized;
    }
}
