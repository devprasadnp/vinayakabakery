package com.harish.pickles.repository;

import com.harish.pickles.domain.InventoryCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryCategoryRepository extends JpaRepository<InventoryCategory, Long> {

    List<InventoryCategory> findByArchivedFalseOrderByDisplayOrderAscNameAsc();

    List<InventoryCategory> findByArchivedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc();

    List<InventoryCategory> findByArchivedTrueOrderByArchivedAtDesc();

    Optional<InventoryCategory> findBySlugIgnoreCase(String slug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlugIgnoreCase(String slug);
}
