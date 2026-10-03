package com.harish.pickles.config;

import com.harish.pickles.domain.InventoryCategory;
import com.harish.pickles.domain.PickleType;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.Role;
import com.harish.pickles.domain.SpiceLevel;
import com.harish.pickles.domain.User;
import com.harish.pickles.repository.InventoryCategoryRepository;
import com.harish.pickles.repository.ProductRepository;
import com.harish.pickles.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-run data: the staff accounts and the opening catalogue of veg and non-veg pickles.
 * Each block runs only when its table is empty, so it never overwrites real data.
 * The super admin block also back-fills on an existing database that predates the role.
 */
@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // Set once at the top of seedCatalogue() so the product() helper below can assign
    // the right inventory category without every one of its ~20 call sites changing.
    private InventoryCategory vegPicklesCategory;
    private InventoryCategory nonVegPicklesCategory;

    @Bean
    @Transactional
    public ApplicationRunner seedData(UserRepository userRepository, ProductRepository productRepository,
                                      InventoryCategoryRepository categoryRepository,
                                      PasswordEncoder encoder, AppProperties props) {
        return args -> {
            seedStaff(userRepository, encoder, props);
            seedCatalogue(productRepository, categoryRepository);
        };
    }

    private void seedStaff(UserRepository userRepository, PasswordEncoder encoder, AppProperties props) {
        // Super admin: created on a fresh database, and back-filled on one upgraded
        // from an earlier version that had no SUPER_ADMIN role.
        if (userRepository.countByRole(Role.SUPER_ADMIN) == 0) {
            String email = props.getSuperAdmin().getEmail().toLowerCase();
            if (!userRepository.existsByEmailIgnoreCase(email)) {
                userRepository.save(new User(props.getSuperAdmin().getName(), email,
                        encoder.encode(props.getSuperAdmin().getPassword()), Role.SUPER_ADMIN));
                log.info("Seeded super admin {} - change this password before going live", email);
            }
        }

        if (userRepository.countByRole(Role.ADMIN) == 0) {
            String email = props.getAdmin().getEmail().toLowerCase();
            if (!userRepository.existsByEmailIgnoreCase(email)) {
                userRepository.save(new User(props.getAdmin().getName(), email,
                        encoder.encode(props.getAdmin().getPassword()), Role.ADMIN));
                log.info("Seeded admin {}", email);
            }
        }

        if (userRepository.countByRole(Role.USER) == 0
                && !userRepository.existsByEmailIgnoreCase("customer@srivinayakabakeryhome.com")) {
            User demo = new User("Demo Customer", "customer@srivinayakabakeryhome.com",
                    encoder.encode("Customer@123"), Role.USER);
            demo.setPhone("9876543210");
            userRepository.save(demo);
        }
    }

    private void seedCatalogue(ProductRepository productRepository, InventoryCategoryRepository categoryRepository) {
        if (productRepository.count() > 0) {
            return;
        }
        // V4's Flyway migration seeds these rows before this runner ever executes, on both a
        // fresh database and one upgraded from an earlier version - so both are always found.
        this.vegPicklesCategory = categoryRepository.findBySlugIgnoreCase("veg-pickles")
                .orElseThrow(() -> new IllegalStateException("veg-pickles category is missing - check V4 migration"));
        this.nonVegPicklesCategory = categoryRepository.findBySlugIgnoreCase("non-veg-pickles")
                .orElseThrow(() -> new IllegalStateException("non-veg-pickles category is missing - check V4 migration"));

        List<Product> catalogue = new ArrayList<>();

        // ---------------- Veg pickles ----------------
        catalogue.add(product("HP-VEG-AVK-500", "Avakaya Mango Pickle", PickleType.VEG, "Mango",
                SpiceLevel.HOT, 349, 449, 500,
                "The Andhra classic. Raw Banginapalli mango cut with the stone, mustard powder, "
                        + "Guntur chilli and cold-pressed sesame oil, matured for three weeks.",
                "Raw mango, mustard powder, red chilli powder, salt, sesame oil, fenugreek, turmeric"));
        catalogue.add(product("HP-VEG-MAG-500", "Magaya Sweet Mango Pickle", PickleType.VEG, "Mango",
                SpiceLevel.MILD, 329, 399, 500,
                "Sun-dried mango shreds in jaggery and mild chilli - the gentle one children reach for.",
                "Raw mango, jaggery, red chilli powder, salt, sesame oil, mustard"));
        catalogue.add(product("HP-VEG-LMN-400", "Nimmakaya Lemon Pickle", PickleType.VEG, "Citrus",
                SpiceLevel.MEDIUM, 279, 329, 400,
                "Thin-skinned lemons cured in salt for ten days, then tempered with chilli and mustard.",
                "Lemon, salt, red chilli powder, mustard, fenugreek, turmeric, sesame oil"));
        catalogue.add(product("HP-VEG-GON-400", "Gongura Pickle", PickleType.VEG, "Greens",
                SpiceLevel.HOT, 299, 359, 400,
                "Sorrel leaves pounded on stone with roasted garlic and chilli. Tart, fiery, unmistakably Telugu.",
                "Gongura leaves, garlic, red chilli, salt, sesame oil, mustard, cumin"));
        catalogue.add(product("HP-VEG-TOM-400", "Tomato Thokku", PickleType.VEG, "Vegetable",
                SpiceLevel.MEDIUM, 259, 299, 400,
                "Slow-cooked country tomatoes reduced till the oil floats. Pairs with idli, dosa and curd rice.",
                "Tomato, red chilli powder, garlic, salt, sesame oil, mustard, curry leaves"));
        catalogue.add(product("HP-VEG-AML-400", "Usirikaya Amla Pickle", PickleType.VEG, "Vegetable",
                SpiceLevel.MEDIUM, 289, 349, 400,
                "Whole gooseberries steamed and steeped in mustard oil - astringent, bracing, good for the gut.",
                "Amla, mustard powder, red chilli powder, salt, fenugreek, turmeric, sesame oil"));
        catalogue.add(product("HP-VEG-CHL-400", "Pandu Mirapakaya Red Chilli Pickle", PickleType.VEG, "Chilli",
                SpiceLevel.EXTRA_HOT, 269, 319, 400,
                "Ripe red chillies stuffed with mustard and tamarind. Handle with respect.",
                "Red chilli, mustard, tamarind, salt, sesame oil, fenugreek"));
        catalogue.add(product("HP-VEG-GAR-300", "Garlic Pickle", PickleType.VEG, "Vegetable",
                SpiceLevel.HOT, 299, 349, 300,
                "Whole peeled cloves softened in oil with chilli and tamarind.",
                "Garlic, red chilli powder, tamarind, salt, sesame oil, mustard"));
        catalogue.add(product("HP-VEG-MIX-500", "Mixed Vegetable Pickle", PickleType.VEG, "Vegetable",
                SpiceLevel.MEDIUM, 279, 339, 500,
                "Carrot, cauliflower, green chilli and lime in a mustard-forward masala.",
                "Carrot, cauliflower, green chilli, lime, mustard, salt, sesame oil, turmeric"));
        catalogue.add(product("HP-VEG-DRM-400", "Drumstick Pickle", PickleType.VEG, "Vegetable",
                SpiceLevel.MEDIUM, 289, 339, 400,
                "Tender drumstick batons in a tangy tamarind and chilli base.",
                "Drumstick, tamarind, red chilli powder, salt, sesame oil, mustard"));

        // ---------------- Non-veg pickles ----------------
        catalogue.add(product("HP-NV-CHK-400", "Boneless Chicken Pickle", PickleType.NON_VEG, "Chicken",
                SpiceLevel.HOT, 649, 749, 400,
                "Country chicken cubes fried till dry, then simmered in chilli, ginger and garlic. No preservatives.",
                "Chicken, red chilli powder, ginger, garlic, salt, sesame oil, mustard, vinegar"));
        catalogue.add(product("HP-NV-CHB-400", "Chicken Pickle with Bone", PickleType.NON_VEG, "Chicken",
                SpiceLevel.HOT, 599, 699, 400,
                "Bone-in pieces for anyone who believes the marrow carries the flavour.",
                "Chicken with bone, red chilli powder, ginger, garlic, salt, sesame oil, spices"));
        catalogue.add(product("HP-NV-GCK-400", "Gongura Chicken Pickle", PickleType.NON_VEG, "Chicken",
                SpiceLevel.EXTRA_HOT, 699, 799, 400,
                "Boneless chicken folded into a sorrel-leaf masala. Sour and fiery in equal measure.",
                "Chicken, gongura leaves, red chilli powder, garlic, salt, sesame oil"));
        catalogue.add(product("HP-NV-MTN-400", "Mutton Pickle", PickleType.NON_VEG, "Mutton",
                SpiceLevel.HOT, 899, 1049, 400,
                "Boneless goat meat cooked down slowly until the spices cling to every piece.",
                "Mutton, red chilli powder, ginger, garlic, salt, sesame oil, spices, vinegar"));
        catalogue.add(product("HP-NV-GMT-400", "Gongura Mutton Pickle", PickleType.NON_VEG, "Mutton",
                SpiceLevel.EXTRA_HOT, 949, 1099, 400,
                "Our most requested festival jar - mutton and gongura, cooked the Rayalaseema way.",
                "Mutton, gongura leaves, red chilli powder, garlic, salt, sesame oil"));
        catalogue.add(product("HP-NV-PRN-300", "Royyala Prawn Pickle", PickleType.NON_VEG, "Seafood",
                SpiceLevel.HOT, 799, 899, 300,
                "Small coastal prawns from Kakinada, deep fried and pickled the same day.",
                "Prawns, red chilli powder, garlic, tamarind, salt, sesame oil, mustard"));
        catalogue.add(product("HP-NV-FSH-300", "Chepa Fish Pickle", PickleType.NON_VEG, "Seafood",
                SpiceLevel.HOT, 749, 849, 300,
                "Boneless seer fish cubes, fried crisp and finished with tamarind and chilli.",
                "Fish, red chilli powder, tamarind, garlic, salt, sesame oil, fenugreek"));
        catalogue.add(product("HP-NV-CRB-300", "Crab Pickle", PickleType.NON_VEG, "Seafood",
                SpiceLevel.EXTRA_HOT, 899, 999, 300,
                "A coastal delicacy made in small batches when the catch is right.",
                "Crab meat, red chilli powder, garlic, tamarind, salt, sesame oil"));
        catalogue.add(product("HP-NV-EGG-300", "Egg Pickle", PickleType.NON_VEG, "Egg",
                SpiceLevel.MEDIUM, 449, 519, 300,
                "Halved boiled eggs steeped in a mustard and chilli masala.",
                "Egg, red chilli powder, mustard, garlic, salt, sesame oil, vinegar"));
        catalogue.add(product("HP-NV-CCK-400", "Natu Kodi Country Chicken Pickle", PickleType.NON_VEG, "Chicken",
                SpiceLevel.EXTRA_HOT, 849, 949, 400,
                "Free-range country chicken - chewier, darker, far more flavour than broiler.",
                "Country chicken, red chilli powder, ginger, garlic, salt, sesame oil, spices"));

        productRepository.saveAll(catalogue);
        log.info("Seeded {} pickles ({} veg, {} non-veg)", catalogue.size(),
                productRepository.countByPickleTypeAndArchivedFalse(PickleType.VEG),
                productRepository.countByPickleTypeAndArchivedFalse(PickleType.NON_VEG));
    }

    private Product product(String sku, String name, PickleType type, String category, SpiceLevel spice,
                            long priceRupees, long mrpRupees, int grams,
                            String description, String ingredients) {
        Product p = new Product();
        p.setSku(sku);
        p.setSlug(slug(name));
        p.setName(name);
        p.setPickleType(type);
        p.setCategory(category);
        p.setInventoryCategory(type == PickleType.VEG ? vegPicklesCategory : nonVegPicklesCategory);
        p.setSpiceLevel(spice);
        p.setPricePaise(priceRupees * 100);
        p.setMrpPaise(mrpRupees * 100);
        p.setWeightGrams(grams);
        p.setShelfLifeDays(type == PickleType.VEG ? 365 : 180);
        p.setDescription(description);
        p.setIngredients(ingredients);
        p.setStockQty(40);
        p.setActive(true);
        return p;
    }

    private static String slug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}
