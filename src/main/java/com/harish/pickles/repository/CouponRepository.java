package com.harish.pickles.repository;

import com.harish.pickles.domain.Coupon;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<Coupon> findByArchivedFalseOrderByIdDesc(Pageable pageable);

    Page<Coupon> findByArchivedFalseAndCodeContainingIgnoreCaseOrderByIdDesc(String code, Pageable pageable);

    List<Coupon> findByArchivedTrueOrderByArchivedAtDesc();
}
