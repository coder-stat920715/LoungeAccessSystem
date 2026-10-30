package com.lounge.access.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EligibilityRequest(
        @Schema(description = "Full card number, or a 6-8 digit BIN (BIN is resolved within the customer's cards)",
                example = "4147200000000011")
        @NotBlank(message = "cardNumber is required")
        @Pattern(regexp = "^[0-9 -]{6,25}$", message = "cardNumber must be 6-25 digits (spaces/hyphens allowed)")
        String cardNumber,

        @Schema(example = "1")
        @NotNull(message = "customerId is required")
        @Positive(message = "customerId must be positive")
        Long customerId,

        @Schema(example = "HYD-T1-ENCORE")
        @NotBlank(message = "loungeId is required")
        @Size(max = 32, message = "loungeId must be at most 32 characters")
        String loungeId,

        @Schema(description = "12-digit digital voucher (mandatory for HDFC debit cards)", example = "482915037261")
        @Size(max = 32, message = "voucherCode must be at most 32 characters")
        String voucherCode) {
}
