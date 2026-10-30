package com.lounge.access.service.statement;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads the sample statements from src/test/resources/statements. */
final class StatementFixtures {

    private StatementFixtures() {
    }

    static String read(String fileName) {
        try (InputStream in = StatementFixtures.class.getResourceAsStream("/statements/" + fileName)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + fileName);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ParsedStatement csv(String fileName) {
        return new CsvStatementParser(new TransactionCategorizer()).parse(fileName, read(fileName));
    }

    /** scenario = "eligible" or "not_eligible"; months = aug, sep, oct. */
    static List<ParsedStatement> axisStatements(String scenario, String... months) {
        List<ParsedStatement> result = new ArrayList<>();
        for (String m : months) {
            result.add(csv("axis_ace_" + scenario + "_" + m + "_2026.csv"));
        }
        return result;
    }
}
