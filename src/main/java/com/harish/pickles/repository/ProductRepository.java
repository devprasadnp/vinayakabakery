package com.harish.pickles.repository;

import com.harish.pickles.domain.PickleType;
import com.harish.pickles.domain.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = "images")
    Optional<Product> findBySlug(String slug);

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "images")
    List<Product> findByArchivedFalseOrderByNameAsc();

    List<Product> findByArchivedTrueOrderByArchivedAtDesc();

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.active = true AND p.archived = false ORDER BY p.category")
    List<String> findDistinctCategories();

    long countByPickleTypeAndArchivedFalse(PickleType type);

    long countByArchivedFalse();

    long countByArchivedTrue();

    long countByArchivedFalseAndStockQtyLessThan(int threshold);

    long countByInventoryCategoryId(Long categoryId);

    long countByInventoryCategoryIdAndArchivedFalse(Long categoryId);
}
