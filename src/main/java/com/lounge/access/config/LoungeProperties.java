package com.lounge.access.config;

import com.lounge.access.dto.Bank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Externalised business rules (prefix {@code lounge}). Defaults mirror the documented bank policies. */
@Getter
@Setter
@ConfigurationProperties(prefix = "lounge")
public class LoungeProperties {

    private Axis axis = new Axis();
    private HdfcDebit hdfcDebit = new HdfcDebit();
    private Generic generic = new Generic();
    private Pass pass = new Pass();

    @Getter
    @Setter
    public static class Axis {
        private BigDecimal minimumSpend = new BigDecimal("50000");
        private int lookbackMonths = 3;
        private int newCardWaiverMonths = 3;
        private int visitsPerQuarter = 2;
    }

    @Getter
    @Setter
    public static class HdfcDebit {
        private BigDecimal minimumSpend = new BigDecimal("10000");
        private int visitsPerQuarter = 2;
    }

    @Getter
    @Setter
    public static class Generic {
        private BigDecimal minimumSpend = new BigDecimal("25000");
        private int visitsPerQuarter = 2;
        private List<Bank> supportedBanks = new ArrayList<>(List.of(Bank.ICICI, Bank.SBI));
    }

    @Getter
    @Setter
    public static class Pass {
        private int validityMinutes = 120;
    }
}
