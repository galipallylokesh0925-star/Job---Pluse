package com.aijobagent.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Metadata recorded during resume upload and parsing.
 */
@Schema(description = "Upload and parsing metadata for a candidate resume")
public class ResumeMetadata {

    @Schema(description = "Original uploaded filename", example = "Jane_Doe_Resume_2026.pdf")
    private String fileName;

    @Schema(description = "MIME content type", example = "application/pdf")
    private String contentType;

    @Schema(description = "File size in bytes", example = "1048576")
    private long sizeBytes;

    @Schema(description = "Total characters extracted from the document", example = "4250")
    private int extractedCharacterCount;

    @Schema(description = "Parser engine utilized", example = "Apache PDFBox 3.0.3")
    private String parserEngine;

    @Schema(description = "Timestamp when the file was processed")
    private Instant parsedAt;

    public ResumeMetadata() {}

    public ResumeMetadata(String fileName, String contentType, long sizeBytes,
                          int extractedCharacterCount, String parserEngine, Instant parsedAt) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.extractedCharacterCount = extractedCharacterCount;
        this.parserEngine = parserEngine;
        this.parsedAt = parsedAt != null ? parsedAt : Instant.now();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getExtractedCharacterCount() {
        return extractedCharacterCount;
    }

    public void setExtractedCharacterCount(int extractedCharacterCount) {
        this.extractedCharacterCount = extractedCharacterCount;
    }

    public String getParserEngine() {
        return parserEngine;
    }

    public void setParserEngine(String parserEngine) {
        this.parserEngine = parserEngine;
    }

    public Instant getParsedAt() {
        return parsedAt;
    }

    public void setParsedAt(Instant parsedAt) {
        this.parsedAt = parsedAt;
    }
}
