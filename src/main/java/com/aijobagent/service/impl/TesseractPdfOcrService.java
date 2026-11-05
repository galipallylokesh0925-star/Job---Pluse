package com.aijobagent.service.impl;

import com.aijobagent.exception.ResumeParsingException;
import com.aijobagent.service.PdfOcrService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class TesseractPdfOcrService implements PdfOcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractPdfOcrService.class);
    private static final double MAX_RENDERED_PAGE_PIXELS = 40_000_000;
    private static final String ENGINE_SETUP_MESSAGE =
            "This scanned PDF has no embedded text and OCR could not start. Install Tesseract OCR with English language data, add it to PATH, or set TESSERACT_COMMAND to its executable path.";

    private final String tesseractCommand;
    private final String language;
    private final int dpi;
    private final int maximumPages;
    private final long timeoutSeconds;

    public TesseractPdfOcrService(
            @Value("${app.resume.ocr.tesseract-command:tesseract}") String tesseractCommand,
            @Value("${app.resume.ocr.language:eng}") String language,
            @Value("${app.resume.ocr.dpi:300}") int dpi,
            @Value("${app.resume.ocr.maximum-pages:20}") int maximumPages,
            @Value("${app.resume.ocr.timeout-seconds:60}") long timeoutSeconds) {
        if (tesseractCommand.isBlank() || language.isBlank() || dpi < 72 || dpi > 600
                || maximumPages < 1 || timeoutSeconds < 1) {
            throw new IllegalArgumentException("Invalid PDF OCR configuration.");
        }
        this.tesseractCommand = resolveTesseractCommand(tesseractCommand);
        this.language = language;
        this.dpi = dpi;
        this.maximumPages = maximumPages;
        this.timeoutSeconds = timeoutSeconds;
    }

    private String resolveTesseractCommand(String configuredCommand) {
        if (!configuredCommand.equals("tesseract")
                || !System.getProperty("os.name", "").startsWith("Windows")) {
            return configuredCommand;
        }

        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        String localAppData = System.getenv("LOCALAPPDATA");
        List<String> installDirectories = List.of(
                programFiles == null ? "" : programFiles + "\\Tesseract-OCR",
                programFilesX86 == null ? "" : programFilesX86 + "\\Tesseract-OCR",
                localAppData == null ? "" : localAppData + "\\Programs\\Tesseract-OCR");
        for (String directory : installDirectories) {
            if (!directory.isBlank()) {
                Path executable = Path.of(directory, "tesseract.exe");
                if (Files.isExecutable(executable)) {
                    return executable.toString();
                }
            }
        }
        return configuredCommand;
    }

    @Override
    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() > maximumPages) {
                throw new ResumeParsingException("OCR is limited to " + maximumPages
                        + " pages per resume. Upload a shorter resume or a searchable PDF.");
            }

            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder extractedText = new StringBuilder();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                validatePageSize(document.getPage(page).getCropBox());
                BufferedImage image = renderer.renderImageWithDPI(page, dpi, ImageType.GRAY);
                try {
                    extractedText.append(runTesseract(image)).append('\n');
                } finally {
                    image.flush();
                }
            }
            return extractedText.toString();
        } catch (ResumeParsingException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResumeParsingException("OCR was interrupted before the scanned resume could be read.", ex);
        } catch (Exception ex) {
            log.error("Failed to OCR scanned PDF resume: {}", ex.getMessage(), ex);
            throw new ResumeParsingException("OCR failed while reading the scanned PDF. Try a clearer, upright scan.", ex);
        }
    }

    private void validatePageSize(PDRectangle page) {
        double widthPixels = Math.ceil(page.getWidth() * dpi / 72.0);
        double heightPixels = Math.ceil(page.getHeight() * dpi / 72.0);
        double pixelCount = widthPixels * heightPixels;
        if (widthPixels <= 0 || heightPixels <= 0 || !Double.isFinite(pixelCount)
                || pixelCount > MAX_RENDERED_PAGE_PIXELS) {
            throw new ResumeParsingException(
                    "A scanned PDF page exceeds the OCR image-size limit. Upload a standard-size PDF or a searchable document.");
        }
    }

    private String runTesseract(BufferedImage image) throws IOException, InterruptedException {
        Path imageFile = null;
        Path outputFile = null;
        Path errorFile = null;
        try {
            imageFile = Files.createTempFile("resume-ocr-", ".png");
            outputFile = Files.createTempFile("resume-ocr-", ".txt");
            errorFile = Files.createTempFile("resume-ocr-", ".log");
            if (!ImageIO.write(image, "png", imageFile.toFile())) {
                throw new ResumeParsingException("Could not prepare a scanned PDF page for OCR.");
            }

            Process process;
            try {
                process = new ProcessBuilder(
                        tesseractCommand, imageFile.toString(), "stdout", "-l", language, "--psm", "3")
                        .redirectOutput(outputFile.toFile())
                        .redirectError(errorFile.toFile())
                        .start();
            } catch (IOException ex) {
                throw new ResumeParsingException(ENGINE_SETUP_MESSAGE, ex);
            }

            try {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor();
                    throw new ResumeParsingException(
                            "OCR timed out while reading a scanned PDF page. Try a shorter resume.");
                }
            } catch (InterruptedException ex) {
                process.destroyForcibly();
                throw ex;
            }

            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                String error = Files.readString(errorFile, StandardCharsets.UTF_8).trim();
                String detail = error.isBlank() ? "" : " " + error.substring(0, Math.min(error.length(), 300));
                throw new ResumeParsingException("Tesseract OCR failed with exit code "
                        + process.exitValue() + "." + detail);
            }
            return output;
        } finally {
            deleteTemporaryFile(imageFile);
            deleteTemporaryFile(outputFile);
            deleteTemporaryFile(errorFile);
        }
    }

    private void deleteTemporaryFile(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Could not remove temporary OCR file '{}': {}", file, ex.getMessage());
        }
    }
}
