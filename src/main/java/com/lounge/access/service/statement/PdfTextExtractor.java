package com.lounge.access.service.statement;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts text from (optionally password-protected) PDF statements. The password is never logged or stored. */
@Component
public class PdfTextExtractor {

    public String extract(String fileName, byte[] pdf, String password) {
        try (PDDocument document = password == null || password.isBlank()
                ? Loader.loadPDF(pdf) : Loader.loadPDF(pdf, password)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new StatementParseException(400, fileName
                    + " is password protected. Supply the correct 'pdfPassword' form field.");
        } catch (IOException e) {
            throw new StatementParseException(422, "Unable to read PDF " + fileName
                    + ". Export the statement as CSV or text if the problem persists.");
        }
    }
}
