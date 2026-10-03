package com.harish.pickles.repository;

import com.harish.pickles.domain.PickleType;
import com.harish.pickles.domain.Product;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria-based filters. Built this way rather than as JPQL with
 * {@code (:type IS NULL OR ...)} so that PostgreSQL never has to infer the type
 * of a null enum parameter.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** What a shopper may see: live, not archived, optionally one dietary type, category or search term. */
    public static Specification<Product> storefront(PickleType type, Long categoryId, String query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.isFalse(root.get("archived")));
            addType(predicates, root, cb, type);
            addCategory(predicates, root, cb, categoryId);
            addSearch(predicates, root, cb, query);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** What the admin catalogue shows: everything except archived rows. */
    public static Specification<Product> catalogue(PickleType type, Long categoryId, String query,
                                                    Boolean activeOnly) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("archived")));
            if (activeOnly != null) {
                predicates.add(activeOnly ? cb.isTrue(root.get("active")) : cb.isFalse(root.get("active")));
            }
            addType(predicates, root, cb, type);
            addCategory(predicates, root, cb, categoryId);
            addSearch(predicates, root, cb, query);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addType(List<Predicate> predicates, jakarta.persistence.criteria.Root<Product> root,
                                jakarta.persistence.criteria.CriteriaBuilder cb, PickleType type) {
        if (type != null) {
            predicates.add(cb.equal(root.get("pickleType"), type));
        }
    }

    private static void addCategory(List<Predicate> predicates, jakarta.persistence.criteria.Root<Product> root,
                                    jakarta.persistence.criteria.CriteriaBuilder cb, Long categoryId) {
        if (categoryId != null) {
            predicates.add(cb.equal(root.get("inventoryCategory").get("id"), categoryId));
        }
    }

    private static void addSearch(List<Predicate> predicates, jakarta.persistence.criteria.Root<Product> root,
                                  jakarta.persistence.criteria.CriteriaBuilder cb, String query) {
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("category")), like),
                    cb.like(cb.lower(root.get("sku")), like)));
        }
    }
}
