package com.lounge.access.service.impl;

import com.lounge.access.dto.Bank;
import com.lounge.access.dto.CardType;
import com.lounge.access.dto.StatementAnalysisResponse;
import com.lounge.access.service.statement.AnalysisRequest;
import com.lounge.access.service.statement.CsvStatementParser;
import com.lounge.access.service.statement.ParsedStatement;
import com.lounge.access.service.statement.PdfTextExtractor;
import com.lounge.access.service.statement.StatementAnalyzer;
import com.lounge.access.service.statement.StatementParseException;
import com.lounge.access.service.statement.TextStatementParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Reads uploaded statements (CSV, TXT, PDF), parses them in memory and returns the eligibility forecast.
 * Statement contents and passwords are never persisted or logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementAnalysisService {

    static final int MAX_FILES = 6;
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final StatementAnalyzer analyzer;
    private final CsvStatementParser csvParser;
    private final TextStatementParser textParser;
    private final PdfTextExtractor pdfExtractor;
    private final Clock clock;

    public StatementAnalysisResponse analyze(List<MultipartFile> files, Bank bank, CardType cardType,
                                             String productCode, LocalDate cardIssueDate, YearMonth targetMonth,
                                             int visitsUsedInTargetQuarter, String pdfPassword) {
        List<MultipartFile> uploads = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (uploads.isEmpty()) {
            throw new StatementParseException(400, "Attach at least one non-empty statement file in the 'statements' field.");
        }
        if (uploads.size() > MAX_FILES) {
            throw new StatementParseException(400, "At most " + MAX_FILES + " statement files can be analysed per request.");
        }
        YearMonth target = targetMonth != null ? targetMonth : YearMonth.now(clock).plusMonths(1);

        List<ParsedStatement> parsed = new ArrayList<>();
        for (MultipartFile file : uploads) {
            parsed.add(parse(file, pdfPassword));
        }
        log.info("Statement analysis bank={} cardType={} product={} files={} target={}", bank, cardType, productCode,
                parsed.size(), target);

        return analyzer.analyze(new AnalysisRequest(bank, cardType, productCode.trim().toUpperCase(Locale.ROOT),
                cardIssueDate, target, visitsUsedInTargetQuarter), parsed);
    }

    private ParsedStatement parse(MultipartFile file, String pdfPassword) {
        String name = safeName(file.getOriginalFilename());
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new StatementParseException(400, name + " exceeds the 5 MB limit.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new StatementParseException(400, "Unable to read uploaded file " + name);
        }
        ParsedStatement parsed = switch (ext) {
            case "csv" -> csvParser.parse(name, new String(bytes, StandardCharsets.UTF_8));
            case "txt" -> {
                String text = new String(bytes, StandardCharsets.UTF_8);
                yield looksTabular(text) ? csvParser.parse(name, text) : textParser.parse(name, "TEXT", text);
            }
            case "pdf" -> textParser.parse(name, "PDF", pdfExtractor.extract(name, bytes, pdfPassword));
            default -> throw new StatementParseException(400,
                    "Unsupported file type for " + name + ". Upload CSV, TXT or PDF statements.");
        };
        if (parsed.transactions().isEmpty()) {
            throw new StatementParseException(422, "No transactions could be recognised in " + name
                    + ". Expected rows with a date, a description and an amount.");
        }
        return parsed;
    }

    /** A .txt export with a delimited header row is parsed as CSV; free text is parsed line by line. */
    private static boolean looksTabular(String text) {
        return text.lines().limit(60).anyMatch(l -> {
            String s = l.toLowerCase(Locale.ROOT);
            return s.contains("date") && s.contains("amount") && (s.contains(",") || s.contains(";") || s.contains("\t") || s.contains("|"));
        });
    }

    private static String safeName(String original) {
        String base = original == null ? "statement" : original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.isBlank() ? "statement" : base;
    }
}
