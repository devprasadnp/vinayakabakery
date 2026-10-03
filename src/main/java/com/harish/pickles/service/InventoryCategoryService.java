package com.harish.pickles.service;

import com.harish.pickles.domain.InventoryCategory;
import com.harish.pickles.dto.CategoryDtos.CategoryRequest;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.repository.InventoryCategoryRepository;
import com.harish.pickles.repository.ProductRepository;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The top-level "inventory type" a food, podi tin, snack or cake belongs to - shown as
 * accordion sections on the storefront and as a filter in both the customer and admin views.
 */
@Service
public class InventoryCategoryService {

    private final InventoryCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public InventoryCategoryService(InventoryCategoryRepository categoryRepository,
                                    ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<InventoryCategory> all() {
        return categoryRepository.findByArchivedFalseOrderByDisplayOrderAscNameAsc();
    }

    /** What the storefront accordion offers to browse: live categories only. */
    @Transactional(readOnly = true)
    public List<InventoryCategory> browsable() {
        return categoryRepository.findByArchivedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public InventoryCategory getById(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> NotFoundException.of("Category", id));
    }

    @Transactional(readOnly = true)
    public List<InventoryCategory> archived() {
        return categoryRepository.findByArchivedTrueOrderByArchivedAtDesc();
    }

    /** Live, non-archived foods counted against this category (for accordion/list badges). */
    @Transactional(readOnly = true)
    public long productCount(Long categoryId) {
        return productRepository.countByInventoryCategoryIdAndArchivedFalse(categoryId);
    }

    @Transactional
    public InventoryCategory create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException("A category named " + request.name() + " already exists");
        }
        InventoryCategory category = new InventoryCategory();
        category.setSlug(uniqueSlug(request.name()));
        apply(category, request);
        return categoryRepository.save(category);
    }

    @Transactional
    public InventoryCategory update(Long id, CategoryRequest request) {
        InventoryCategory category = getById(id);
        if (!category.getName().equalsIgnoreCase(request.name())
                && categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException("A category named " + request.name() + " already exists");
        }
        apply(category, request);
        return categoryRepository.save(category);
    }

    @Transactional
    public InventoryCategory setActive(Long id, boolean active) {
        InventoryCategory category = getById(id);
        if (category.isArchived() && active) {
            throw new BusinessException("Restore " + category.getName() + " from the archive first");
        }
        category.setActive(active);
        return categoryRepository.save(category);
    }

    @Transactional
    public InventoryCategory archive(Long id) {
        if (categoryRepository.findByArchivedFalseOrderByDisplayOrderAscNameAsc().size() <= 1) {
            throw new BusinessException("At least one category must stay so every food has a home");
        }
        InventoryCategory category = getById(id);
        category.setArchived(true);
        category.setActive(false);
        category.setArchivedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    @Transactional
    public InventoryCategory restore(Long id) {
        InventoryCategory category = getById(id);
        category.setArchived(false);
        category.setArchivedAt(null);
        return categoryRepository.save(category);
    }

    /**
     * Super admin: remove the row for good. Refused while any product - archived or not -
     * still lives in this category, so no food is ever left pointing at nothing.
     */
    @Transactional
    public String deletePermanently(Long id) {
        InventoryCategory category = getById(id);
        if (!category.isArchived()) {
            throw new BusinessException("Archive " + category.getName() + " before deleting it permanently");
        }
        long references = productRepository.countByInventoryCategoryId(id);
        if (references > 0) {
            throw new BusinessException(category.getName() + " still has " + references
                    + " food(s) in it and cannot be deleted. Move them to another category first.");
        }
        String name = category.getName();
        categoryRepository.delete(category);
        return name;
    }

    private void apply(InventoryCategory category, CategoryRequest r) {
        category.setName(r.name().trim());
        category.setIcon((r.icon() == null || r.icon().isBlank()) ? null : r.icon().trim());
        category.setVeg(r.veg());
        category.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
        category.setActive(r.active() == null || r.active());
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (categoryRepository.existsBySlugIgnoreCase(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "category" : normalized;
    }
}
