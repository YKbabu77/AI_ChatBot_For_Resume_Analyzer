package com.resumeai.bot.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Service
public class DocumentService {

    public String extractText(
            File file,
            String fileName) throws IOException {

        if (fileName == null) {
            throw new IOException("File name is missing");
        }

        String lowerName =
                fileName.toLowerCase();

        String text;

        if (lowerName.endsWith(".pdf")) {

            text = extractPdfText(file);

        } else if (lowerName.endsWith(".docx")) {

            text = extractDocxText(file);

        } else {

            throw new IOException(
                    "Unsupported file format. Please upload PDF or DOCX."
            );
        }

        if (text == null || text.isBlank()) {

            throw new IOException(
                    "No readable text found in document."
            );
        }

        return text.trim();
    }

    private String extractPdfText(
            File file) throws IOException {

        try (var document =
                     Loader.loadPDF(file)) {

            PDFTextStripper stripper =
                    new PDFTextStripper();

            return stripper.getText(document);
        }
    }

    private String extractDocxText(
            File file) throws IOException {

        StringBuilder text =
                new StringBuilder();

        try (
                FileInputStream inputStream =
                        new FileInputStream(file);

                XWPFDocument document =
                        new XWPFDocument(inputStream)
        ) {

            // Extract normal paragraphs
            document.getParagraphs()
                    .forEach(paragraph -> {

                        String paragraphText =
                                paragraph.getText();

                        if (paragraphText != null
                                && !paragraphText.isBlank()) {

                            text.append(paragraphText)
                                    .append("\n");
                        }
                    });

            // Extract table content
            document.getTables()
                    .forEach(table -> {

                        table.getRows()
                                .forEach(row -> {

                                    row.getTableCells()
                                            .forEach(cell -> {

                                                String cellText =
                                                        cell.getText();

                                                if (cellText != null
                                                        && !cellText.isBlank()) {

                                                    text.append(cellText)
                                                            .append(" ");
                                                }
                                            });

                                    text.append("\n");
                                });
                    });
        }

        return text.toString();
    }
}