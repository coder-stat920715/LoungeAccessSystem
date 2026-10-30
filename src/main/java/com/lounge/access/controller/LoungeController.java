package com.lounge.access.controller;

import com.lounge.access.dto.EligibilityRequest;
import com.lounge.access.dto.EligibilityResponse;
import com.lounge.access.dto.RedeemPassRequest;
import com.lounge.access.dto.RedeemPassResponse;
import com.lounge.access.service.impl.LoungeAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lounge")
@RequiredArgsConstructor
@Tag(name = "Lounge Access", description = "Eligibility verification and pass redemption at the reception desk")
public class LoungeController {

    private final LoungeAccessService loungeAccessService;

    @PostMapping("/check-eligibility")
    @Operation(summary = "Check eligibility and issue a lounge pass",
            description = "Always returns 200 for a processed attempt; inspect 'eligible' and 'denialReason'. "
                    + "visitsRemaining of -1 means unlimited access.")
    public ResponseEntity<EligibilityResponse> checkEligibility(@Valid @RequestBody EligibilityRequest request) {
        return ResponseEntity.ok(loungeAccessService.checkEligibility(request));
    }

    @PostMapping("/redeem-pass")
    @Operation(summary = "Redeem an issued pass at the reception terminal",
            description = "404 unknown token, 409 already redeemed / wrong lounge, 410 expired.")
    public ResponseEntity<RedeemPassResponse> redeemPass(@Valid @RequestBody RedeemPassRequest request) {
        return ResponseEntity.ok(loungeAccessService.redeemPass(request));
    }
}
