package com.lounge.access;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** End-to-end test of POST /api/v1/statements/analyze (multipart upload, security, JSON contract). */
@SpringBootTest
@AutoConfigureMockMvc
class StatementControllerIT {

    private static final String URL = "/api/v1/statements/analyze";

    @Autowired
    private MockMvc mockMvc;

    private static MockMultipartFile file(String name) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/statements/" + name));
        return new MockMultipartFile("statements", name, "text/csv", bytes);
    }

    private static MockMultipartHttpServletRequestBuilder axisRequest(String scenario) throws IOException {
        return (MockMultipartHttpServletRequestBuilder) multipart(URL)
                .file(file("axis_ace_" + scenario + "_aug_2026.csv"))
                .file(file("axis_ace_" + scenario + "_sep_2026.csv"))
                .file(file("axis_ace_" + scenario + "_oct_2026.csv"))
                .param("bank", "AXIS").param("cardType", "CREDIT").param("productCode", "ACE")
                .param("targetMonth", "2026-11")
                .with(httpBasic("reception", "lounge@123"));
    }

    @Test
    void eligibleStatementsReturnEligibleVerdictAndPlan() throws Exception {
        mockMvc.perform(axisRequest("eligible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.eligibleForTargetMonth").value(true))
                .andExpect(jsonPath("$.qualifyingSpendInWindow").value(66500.0))
                .andExpect(jsonPath("$.planForUpcomingMonths.planMonth").value("2026-12"))
                .andExpect(jsonPath("$.planForUpcomingMonths.additionalQualifyingSpendNeeded").value(5000.0));
    }

    @Test
    void lowSpendStatementsReturnNotEligibleWithSteps() throws Exception {
        mockMvc.perform(axisRequest("not_eligible"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.surplusOrShortfall").value(-19200.0))
                .andExpect(jsonPath("$.planForUpcomingMonths.additionalQualifyingSpendNeeded").value(29000.0))
                .andExpect(jsonPath("$.recommendations[0]").exists());
    }

    @Test
    void unsupportedFileTypeIsRejected() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("statements", "notes.docx", "application/octet-stream", "x".getBytes()))
                        .param("bank", "AXIS").param("productCode", "ACE")
                        .with(httpBasic("reception", "lounge@123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mockMvc.perform(multipart(URL).file(file("axis_ace_eligible_aug_2026.csv"))
                        .param("bank", "AXIS").param("productCode", "ACE"))
                .andExpect(status().isUnauthorized());
    }
}
