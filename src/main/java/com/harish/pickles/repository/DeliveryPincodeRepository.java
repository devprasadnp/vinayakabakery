package com.harish.pickles.repository;

import com.harish.pickles.domain.DeliveryPincode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryPincodeRepository extends JpaRepository<DeliveryPincode, Long> {

    Optional<DeliveryPincode> findByPincode(String pincode);

    boolean existsByPincodeAndActiveTrue(String pincode);

    List<DeliveryPincode> findByPincodeIn(Collection<String> pincodes);

    long countByActiveTrue();

    Page<DeliveryPincode> findAllByOrderByPincodeAsc(Pageable pageable);

    @Query("""
            SELECT p FROM DeliveryPincode p
            WHERE p.pincode LIKE CONCAT(:q, '%')
               OR LOWER(COALESCE(p.areaName, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY p.pincode ASC
            """)
    Page<DeliveryPincode> search(@Param("q") String q, Pageable pageable);
}
