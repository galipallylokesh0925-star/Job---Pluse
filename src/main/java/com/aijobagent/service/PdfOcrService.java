package com.aijobagent.service;

/**
 * Extracts text from scanned PDF pages when normal PDF text extraction is empty.
 */
public interface PdfOcrService {

    String extractText(byte[] pdfBytes);
}
