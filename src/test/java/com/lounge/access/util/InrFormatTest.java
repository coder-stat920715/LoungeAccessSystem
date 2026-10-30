package com.lounge.access.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InrFormatTest {

    @Test
    void usesIndianDigitGrouping() {
        assertThat(InrFormat.format(new BigDecimal("0"))).isEqualTo("\u20B90");
        assertThat(InrFormat.format(new BigDecimal("999"))).isEqualTo("\u20B9999");
        assertThat(InrFormat.format(new BigDecimal("5000"))).isEqualTo("\u20B95,000");
        assertThat(InrFormat.format(new BigDecimal("100000"))).isEqualTo("\u20B91,00,000");
        assertThat(InrFormat.format(new BigDecimal("12345678"))).isEqualTo("\u20B91,23,45,678");
        assertThat(InrFormat.format(new BigDecimal("-19200"))).isEqualTo("-\u20B919,200");
        assertThat(InrFormat.format(new BigDecimal("599.60"))).isEqualTo("\u20B9600");
    }
}
