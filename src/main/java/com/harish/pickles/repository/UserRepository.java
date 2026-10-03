package com.harish.pickles.repository;

import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    /** Accounts an order can be handed to: partners who are still allowed to sign in. */
    List<User> findByRoleAndEnabledTrueOrderByFullNameAsc(Role role);

    Page<User> findByRoleInOrderByIdDesc(Collection<Role> roles, Pageable pageable);

    @Query("""
            SELECT u FROM User u
            WHERE u.role IN :roles
              AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :q, '%'))
                OR COALESCE(u.phone, '') LIKE CONCAT('%', :q, '%'))
            ORDER BY u.id DESC
            """)
    Page<User> search(@Param("roles") Collection<Role> roles, @Param("q") String q, Pageable pageable);
}
