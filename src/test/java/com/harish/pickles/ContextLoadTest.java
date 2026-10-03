package com.harish.pickles;

import static org.assertj.core.api.Assertions.assertThat;

import com.harish.pickles.domain.PickleType;
import com.harish.pickles.domain.Role;
import com.harish.pickles.repository.ProductRepository;
import com.harish.pickles.repository.UserRepository;
import com.harish.pickles.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ContextLoadTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void contextLoadsAndCatalogueIsSeeded() {
        assertThat(productRepository.countByPickleTypeAndArchivedFalse(PickleType.VEG)).isPositive();
        assertThat(productRepository.countByPickleTypeAndArchivedFalse(PickleType.NON_VEG)).isPositive();
    }

    @Test
    void staffAccountsAreSeeded() {
        assertThat(userRepository.countByRole(Role.SUPER_ADMIN)).isEqualTo(1);
        assertThat(userRepository.countByRole(Role.ADMIN)).isEqualTo(1);
        assertThat(userRepository.countByRole(Role.USER)).isPositive();
    }

    @Test
    void superAdminAlsoHoldsTheAdminAndUserAuthorities() {
        assertThat(AppUserDetails.authoritiesFor(Role.SUPER_ADMIN))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
        assertThat(AppUserDetails.authoritiesFor(Role.ADMIN))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        assertThat(AppUserDetails.authoritiesFor(Role.USER))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void deliveryPartnerHoldsNoAdministrativeAuthority() {
        assertThat(AppUserDetails.authoritiesFor(Role.DELIVERY_PARTNER))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_DELIVERY_PARTNER");
        assertThat(Role.DELIVERY_PARTNER.isStaff()).isFalse();
    }
}
