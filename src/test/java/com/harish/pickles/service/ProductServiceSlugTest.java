package com.harish.pickles.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductServiceSlugTest {

    @Test
    void buildsUrlSafeSlugs() {
        assertThat(ProductService.slugify("Avakaya Mango Pickle")).isEqualTo("avakaya-mango-pickle");
        assertThat(ProductService.slugify("Royyala (Prawn) Pickle - 300g")).isEqualTo("royyala-prawn-pickle-300g");
        assertThat(ProductService.slugify("  Gongura   Pickle  ")).isEqualTo("gongura-pickle");
    }

    @Test
    void neverReturnsAnEmptySlug() {
        assertThat(ProductService.slugify("!!!")).isEqualTo("pickle");
    }
}
