package com.aijobagent.service;

import com.aijobagent.dto.profile.ExtractedResumeData;
import com.aijobagent.exception.ResumeParsingException;
import com.aijobagent.service.impl.ResumeParserServiceImpl;
import com.aijobagent.service.impl.TesseractPdfOcrService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeParserServiceTest {

    private ResumeParserService resumeParserService;
    private PdfOcrService pdfOcrService;

    @BeforeEach
    void setUp() {
        pdfOcrService = mock(PdfOcrService.class);
        resumeParserService = new ResumeParserServiceImpl(pdfOcrService);
    }

    @Test
    @DisplayName("Successfully extracts text, contact info, and skills from a PDF document")
    void shouldExtractDataFromPdfResume() throws IOException {
        // Arrange: Generate synthetic PDF document with PDFBox 3.0
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(50, 750);
                stream.showText("Arjun Sharma");
                stream.newLineAtOffset(0, -20);
                stream.showText("Email: arjun.sharma@example.com | Phone: +91 9876543210");
                stream.newLineAtOffset(0, -20);
                stream.showText("LinkedIn: https://linkedin.com/in/arjunsharma GitHub: https://github.com/arjunsharma");
                stream.newLineAtOffset(0, -20);
                stream.showText("Summary: Motivated Software Engineer with hands-on experience in cloud architectures.");
                stream.newLineAtOffset(0, -20);
                stream.showText("Skills: Java, Spring Boot, MongoDB, Docker, React, Microservices");
                stream.newLineAtOffset(0, -20);
                stream.showText("Education: B.Tech in Computer Science from National Institute of Technology");
                stream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            pdfBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Arjun_Sharma_Resume.pdf",
                "application/pdf",
                pdfBytes
        );

        // Act
        ExtractedResumeData extracted = resumeParserService.parse(file);

        // Assert
        assertNotNull(extracted);
        assertEquals("Arjun Sharma", extracted.fullName());
        assertEquals("arjun.sharma@example.com", extracted.email());
        assertEquals("+91 9876543210", extracted.phone());
        assertTrue(extracted.skills().contains("Java"));
        assertTrue(extracted.skills().contains("Spring Boot"));
        assertTrue(extracted.skills().contains("MongoDB"));
        assertTrue(extracted.skills().contains("Docker"));
        assertTrue(extracted.skills().contains("React"));
        assertTrue(extracted.characterCount() > 50);
        assertTrue(extracted.parserEngine().contains("PDFBox"));
        verifyNoInteractions(pdfOcrService);
    }

    @Test
    @DisplayName("Uses OCR when a PDF contains no embedded text")
    void shouldUseOcrForScannedPdf() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        when(pdfOcrService.extractText(pdfBytes)).thenReturn(
                "Arjun Sharma\nEmail: arjun.sharma@example.com\nSkills: Java, Spring Boot");
        MockMultipartFile file = new MockMultipartFile(
                "file", "scanned-resume.pdf", "application/pdf", pdfBytes);

        ExtractedResumeData extracted = resumeParserService.parse(file);

        assertEquals("Arjun Sharma", extracted.fullName());
        assertEquals("arjun.sharma@example.com", extracted.email());
        assertTrue(extracted.skills().contains("Java"));
        assertTrue(extracted.parserEngine().contains("Tesseract OCR"));
        verify(pdfOcrService).extractText(pdfBytes);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_TESSERACT_INTEGRATION_TESTS", matches = "true")
    @DisplayName("Reads text from a real image-only PDF using the installed Tesseract engine")
    void shouldReadScannedPdfWithInstalledTesseract() throws IOException {
        byte[] pdfBytes;
        BufferedImage scan = new BufferedImage(1275, 1650, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scan.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, scan.getWidth(), scan.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 56));
            graphics.drawString("Jordan Taylor", 80, 200);
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 42));
            graphics.drawString("jordan.taylor@example.com", 80, 330);
            graphics.drawString("Skills: Java, Spring Boot", 80, 460);
        } finally {
            graphics.dispose();
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.drawImage(
                        LosslessFactory.createFromImage(document, scan),
                        0, 0, PDRectangle.LETTER.getWidth(), PDRectangle.LETTER.getHeight());
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdfBytes = output.toByteArray();
        } finally {
            scan.flush();
        }

        ResumeParserService parser = new ResumeParserServiceImpl(
                new TesseractPdfOcrService("tesseract", "eng", 200, 1, 60));
        MockMultipartFile file = new MockMultipartFile(
                "file", "scanned-resume.pdf", "application/pdf", pdfBytes);

        ExtractedResumeData extracted = parser.parse(file);

        assertEquals("jordan.taylor@example.com", extracted.email());
        assertTrue(extracted.skills().contains("Java"));
        assertTrue(extracted.parserEngine().contains("Tesseract OCR"));
    }

    @Test
    @DisplayName("Preserves a useful OCR setup error when Tesseract is unavailable")
    void shouldSurfaceOcrSetupError() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        when(pdfOcrService.extractText(pdfBytes)).thenThrow(
                new ResumeParsingException("Install Tesseract OCR and retry."));
        MockMultipartFile file = new MockMultipartFile(
                "file", "scanned-resume.pdf", "application/pdf", pdfBytes);

        ResumeParsingException exception = assertThrows(
                ResumeParsingException.class, () -> resumeParserService.parse(file));

        assertEquals("Install Tesseract OCR and retry.", exception.getMessage());
    }

    @Test
    @DisplayName("Explains when OCR cannot recognize text in a scanned PDF")
    void shouldExplainWhenOcrFindsNoText() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        when(pdfOcrService.extractText(pdfBytes)).thenReturn(" \n ");
        MockMultipartFile file = new MockMultipartFile(
                "file", "scanned-resume.pdf", "application/pdf", pdfBytes);

        ResumeParsingException exception = assertThrows(
                ResumeParsingException.class, () -> resumeParserService.parse(file));

        assertTrue(exception.getMessage().contains("even after OCR"));
    }

    @Test
    @DisplayName("Explains how to enable OCR when Tesseract is unavailable")
    void shouldGiveSetupInstructionsWhenTesseractIsUnavailable() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        PdfOcrService ocrService = new TesseractPdfOcrService(
                "missing-tesseract-executable-for-test", "eng", 150, 20, 1);

        ResumeParsingException exception = assertThrows(
                ResumeParsingException.class, () -> ocrService.extractText(pdfBytes));

        assertTrue(exception.getMessage().contains("Install Tesseract OCR"));
    }

    @Test
    @DisplayName("Rejects scanned PDF pages that exceed the OCR image-size limit")
    void shouldRejectOversizedScannedPdfPage() throws IOException {
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(0, 0, 50_000, 50_000)));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        PdfOcrService ocrService = new TesseractPdfOcrService(
                "missing-tesseract-executable-for-test", "eng", 150, 20, 1);

        ResumeParsingException exception = assertThrows(
                ResumeParsingException.class, () -> ocrService.extractText(pdfBytes));

        assertTrue(exception.getMessage().contains("image-size limit"));
    }

    @Test
    @DisplayName("Successfully extracts text, contact info, and skills from a Word DOCX document")
    void shouldExtractDataFromDocxResume() throws IOException {
        // Arrange: Generate synthetic DOCX with Apache POI
        byte[] docxBytes;
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun r1 = p1.createRun();
            r1.setText("Priya Patel");

            XWPFParagraph p2 = doc.createParagraph();
            XWPFRun r2 = p2.createRun();
            r2.setText("Email: priya.patel@workmail.com | Phone: 9876543210 | Location: Bengaluru, India");

            XWPFParagraph p3 = doc.createParagraph();
            XWPFRun r3 = p3.createRun();
            r3.setText("Technical Skills: Python, Django, PostgreSQL, AWS, Git, Kubernetes, Redis");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            docxBytes = baos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "Priya_Patel_Resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        // Act
        ExtractedResumeData extracted = resumeParserService.parse(file);

        // Assert
        assertNotNull(extracted);
        assertEquals("Priya Patel", extracted.fullName());
        assertEquals("priya.patel@workmail.com", extracted.email());
        assertTrue(extracted.skills().contains("Python"));
        assertTrue(extracted.skills().contains("PostgreSQL"));
        assertTrue(extracted.skills().contains("AWS"));
        assertTrue(extracted.skills().contains("Kubernetes"));
        assertTrue(extracted.parserEngine().contains("Apache POI"));
    }

    @Test
    @DisplayName("Throws ResumeParsingException when file is empty")
    void shouldThrowWhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        assertThrows(ResumeParsingException.class, () -> resumeParserService.parse(emptyFile));
    }

    @Test
    @DisplayName("Throws ResumeParsingException on unsupported file format")
    void shouldThrowOnUnsupportedFileFormat() {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "script.exe", "application/octet-stream", "bad binary".getBytes());
        assertThrows(ResumeParsingException.class, () -> resumeParserService.parse(invalidFile));
    }
}
