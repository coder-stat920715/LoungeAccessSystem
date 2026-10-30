package com.lounge.access.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedeemPassRequest(
        @NotBlank(message = "passToken is required")
        @Size(max = 40, message = "passToken must be at most 40 characters")
        String passToken,

        @Schema(example = "HYD-T1-ENCORE")
        @NotBlank(message = "loungeId is required")
        @Size(max = 32, message = "loungeId must be at most 32 characters")
        String loungeId,

        @Size(max = 32, message = "terminalId must be at most 32 characters")
        String terminalId) {
}
