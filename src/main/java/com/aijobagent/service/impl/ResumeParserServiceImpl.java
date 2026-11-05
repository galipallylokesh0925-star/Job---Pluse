package com.aijobagent.service.impl;

import com.aijobagent.dto.profile.ExtractedResumeData;
import com.aijobagent.exception.ResumeParsingException;
import com.aijobagent.model.Education;
import com.aijobagent.model.ProjectDetail;
import com.aijobagent.model.WorkExperience;
import com.aijobagent.service.PdfOcrService;
import com.aijobagent.service.ResumeParserService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production implementation of ResumeParserService.
 *
 * Utilizes Apache PDFBox 3.0 for PDF files, Apache POI 5.3 for DOCX files,
 * and deterministic regex & lexical pattern matching for entity extraction.
 */
@Service
public class ResumeParserServiceImpl implements ResumeParserService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParserServiceImpl.class);

    private final PdfOcrService pdfOcrService;

    public ResumeParserServiceImpl(PdfOcrService pdfOcrService) {
        this.pdfOcrService = pdfOcrService;
    }

    // Precompiled Regex Patterns for Entity Extraction
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}|(?:\\+?91[-.\\s]?)?[6-9]\\d{9}");

    private static final Pattern LINKEDIN_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?linkedin\\.com/in/[a-zA-Z0-9_%-]+/?", Pattern.CASE_INSENSITIVE);

    private static final Pattern GITHUB_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?github\\.com/[a-zA-Z0-9_%-]+/?", Pattern.CASE_INSENSITIVE);

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?", Pattern.CASE_INSENSITIVE);

    // Curated catalog of tech skills for deterministic keyword extraction
    private static final List<String> KNOWN_SKILLS = List.of(
            // Languages
            "Java", "Python", "JavaScript", "TypeScript", "C++", "C#", "Go", "Rust", "Kotlin", "SQL", "HTML", "CSS", "Bash",
            // Backend & Frameworks
            "Spring Boot", "Spring Data", "Spring Security", "Spring MVC", "Spring AI", "Hibernate", "JPA",
            "Node.js", "Express", "Django", "FastAPI", "Flask", "ASP.NET", "GraphQL", "REST APIs", "Microservices",
            // Frontend
            "React", "Next.js", "Angular", "Vue", "Redux", "Tailwind CSS", "Bootstrap",
            // Databases & Caching
            "MongoDB", "PostgreSQL", "MySQL", "Redis", "Oracle", "Cassandra", "Elasticsearch", "DynamoDB", "SQLite",
            // Cloud & DevOps
            "AWS", "Docker", "Kubernetes", "Azure", "GCP", "Linux", "Git", "GitHub", "GitLab", "CI/CD", "Jenkins", "Maven", "Gradle", "Terraform",
            // Messaging & Architecture
            "Kafka", "RabbitMQ", "ActiveMQ", "WebSockets", "OOP", "Data Structures", "Algorithms", "System Design",
            // AI & Data
            "Machine Learning", "Deep Learning", "NLP", "PyTorch", "TensorFlow", "Pandas", "NumPy"
    );

    @Override
    public ExtractedResumeData parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResumeParsingException("Uploaded resume file is empty or missing.");
        }

        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "uploaded_resume");
        String contentType = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase(Locale.ROOT);
        String lowerName = originalFilename.toLowerCase(Locale.ROOT);

        String rawText;
        String parserEngine;

        try {
            if (lowerName.endsWith(".pdf") || contentType.contains("pdf")) {
                parserEngine = "Apache PDFBox 3.0.3";
                byte[] pdfBytes = file.getBytes();
                rawText = extractTextFromPdf(pdfBytes);
                if (rawText == null || rawText.isBlank()) {
                    rawText = pdfOcrService.extractText(pdfBytes);
                    parserEngine += " + Tesseract OCR";
                }
            } else if (lowerName.endsWith(".docx") || contentType.contains("wordprocessingml")) {
                parserEngine = "Apache POI 5.3.0";
                try (InputStream inputStream = file.getInputStream()) {
                    rawText = extractTextFromDocx(inputStream);
                }
            } else if (lowerName.endsWith(".txt") || contentType.contains("text/plain")) {
                parserEngine = "UTF-8 PlainText Reader";
                rawText = new String(file.getBytes(), StandardCharsets.UTF_8);
            } else {
                throw new ResumeParsingException("Unsupported file format for '" + originalFilename +
                        "'. Supported formats are PDF (.pdf) and Word (.docx).");
            }
        } catch (ResumeParsingException rpe) {
            throw rpe;
        } catch (Exception ex) {
            log.error("Failed to parse resume document '{}': {}", originalFilename, ex.getMessage(), ex);
            throw new ResumeParsingException("Failed to decode resume: " + ex.getMessage(), ex);
        }

        if (rawText == null || rawText.isBlank()) {
            String message = parserEngine.endsWith("Tesseract OCR")
                    ? "No readable text was found, even after OCR. Upload a clearer, upright scan or a searchable PDF/DOCX."
                    : "No extractable text was found. Image-only PDFs are supported through OCR; otherwise upload a searchable PDF, DOCX, or TXT file.";
            throw new ResumeParsingException(message);
        }

        return parseText(rawText, parserEngine);
    }

    @Override
    public ExtractedResumeData parseText(String rawText, String engine) {
        String cleanText = normalizeText(rawText);

        // 1. Extract Email
        String email = extractFirstMatch(cleanText, EMAIL_PATTERN);

        // 2. Extract Phone Number
        String phone = extractFirstMatch(cleanText, PHONE_PATTERN);

        // 3. Extract Links
        String linkedInUrl = extractFirstMatch(cleanText, LINKEDIN_PATTERN);
        String githubUrl = extractFirstMatch(cleanText, GITHUB_PATTERN);
        String portfolioUrl = extractPortfolioUrl(cleanText, linkedInUrl, githubUrl);

        // 4. Extract Candidate Full Name
        String candidateName = extractCandidateName(cleanText);

        // 5. Extract Skills
        List<String> skills = extractSkills(cleanText);

        // 6. Extract Education Entries
        List<Education> educations = extractEducation(cleanText);

        // 7. Extract Work Experience
        List<WorkExperience> experiences = extractExperience(cleanText);

        // 8. Extract Projects
        List<ProjectDetail> projects = extractProjects(cleanText);

        // 9. Extract Certifications
        List<String> certifications = extractCertifications(cleanText);

        // 10. Extract Summary / Objective
        String summary = extractSummary(cleanText);

        return new ExtractedResumeData(
                cleanText,
                candidateName,
                email,
                phone,
                extractLocation(cleanText),
                linkedInUrl,
                githubUrl,
                portfolioUrl,
                summary,
                skills,
                educations,
                experiences,
                projects,
                certifications,
                cleanText.length(),
                engine
        );
    }

    private String extractTextFromPdf(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted()) {
                throw new ResumeParsingException("The PDF resume is password-protected or encrypted.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractTextFromDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String extractFirstMatch(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null;
    }

    private String extractPortfolioUrl(String text, String linkedInUrl, String githubUrl) {
        Matcher matcher = URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String url = matcher.group().trim();
            if (linkedInUrl != null && url.contains(linkedInUrl)) continue;
            if (githubUrl != null && url.contains(githubUrl)) continue;
            if (url.contains("w3.org") || url.contains("schema.org") || url.contains("apache.org")) continue;
            return url;
        }
        return null;
    }

    private String extractCandidateName(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;

            // Reject if contains keywords
            String lower = trimmed.toLowerCase();
            if (lower.contains("resume") || lower.contains("curriculum") || lower.contains("cv")
                    || lower.contains("email") || lower.contains("phone") || lower.contains("contact")
                    || lower.contains("page") || lower.contains("profile") || lower.contains("github")
                    || lower.contains("linkedin") || lower.contains("http") || lower.contains("@")) {
                continue;
            }

            // Name is typically 2 to 4 alphabetic words
            String[] words = trimmed.split("\\s+");
            if (words.length >= 2 && words.length <= 4 && trimmed.matches("^[a-zA-Z\\s.'-]+$")) {
                return trimmed;
            }
        }
        return null;
    }

    private String extractLocation(String text) {
        List<String> knownCities = List.of(
                "Bengaluru", "Bangalore", "Pune", "Hyderabad", "Mumbai", "Delhi", "Noida", "Gurugram", "Gurgaon",
                "Chennai", "Kolkata", "Ahmedabad", "Jaipur", "Chandigarh", "Kochi", "Indore", "Remote"
        );
        for (String city : knownCities) {
            Pattern cityPattern = Pattern.compile("\\b" + Pattern.quote(city) + "\\b", Pattern.CASE_INSENSITIVE);
            if (cityPattern.matcher(text).find()) {
                return city.equalsIgnoreCase("Bangalore") ? "Bengaluru, India" : city + ", India";
            }
        }
        return null;
    }

    private List<String> extractSkills(String text) {
        Set<String> matchedSkills = new LinkedHashSet<>();
        String lowerText = text.toLowerCase();

        for (String skill : KNOWN_SKILLS) {
            // Case-insensitive regex with word boundary
            Pattern pattern = Pattern.compile("(?<![a-zA-Z0-9#+])" + Pattern.quote(skill.toLowerCase()) + "(?![a-zA-Z0-9#+])");
            if (pattern.matcher(lowerText).find()) {
                matchedSkills.add(skill);
            }
        }

        return new ArrayList<>(matchedSkills);
    }

    private String extractSummary(String text) {
        Pattern summaryHeader = Pattern.compile("(?i)(?:summary|professional summary|objective|career objective|about me)\\s*[:\\-]?\\s*\n?(.*?)(\n\n[A-Z\\s]{3,}|\n\n(?:experience|education|skills|projects)|$)", Pattern.DOTALL);
        Matcher matcher = summaryHeader.matcher(text);
        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            if (extracted.length() > 20) {
                return extracted.length() > 500 ? extracted.substring(0, 500) + "..." : extracted;
            }
        }
        return null;
    }

    private List<Education> extractEducation(String text) {
        List<Education> educations = new ArrayList<>();
        Pattern degreePattern = Pattern.compile("(?i)\\b(B\\.?Tech|B\\.?E\\.?|M\\.?Tech|BCA|MCA|B\\.Sc|M\\.Sc|Bachelor|Master)(?:\\s+in\\s+([A-Za-z\\s]+))?");
        Matcher matcher = degreePattern.matcher(text);

        while (matcher.find()) {
            String degree = matcher.group(1);
            String field = matcher.group(2) != null ? matcher.group(2).trim() : "Computer Science";
            Education edu = new Education();
            edu.setDegree(degree);
            edu.setFieldOfStudy(field);
            edu.setInstitution("Engineering / University Institute");
            educations.add(edu);
            if (educations.size() >= 2) break; // Keep top 2 degrees
        }

        return educations;
    }

    private List<WorkExperience> extractExperience(String text) {
        List<WorkExperience> experiences = new ArrayList<>();
        // Search for lines indicating experience or intern roles
        Pattern rolePattern = Pattern.compile("(?i)(Software Engineer|Developer|Frontend|Backend|Full Stack|Intern|SDE|Data Engineer)[\\s\\w]*?(?:at|@|,)?\\s+([A-Za-z0-9\\s]{3,30})");
        Matcher matcher = rolePattern.matcher(text);

        while (matcher.find()) {
            String title = matcher.group(1).trim();
            String company = matcher.group(2).trim();
            WorkExperience exp = new WorkExperience();
            exp.setTitle(title);
            exp.setCompany(company);
            experiences.add(exp);
            if (experiences.size() >= 3) break;
        }

        return experiences;
    }

    private List<ProjectDetail> extractProjects(String text) {
        List<ProjectDetail> projects = new ArrayList<>();
        Pattern projectSection = Pattern.compile("(?i)(?:projects|key projects|academic projects)\\s*[:\\-]?\n(.*?)(?=\n\n[A-Z\\s]{3,}|$)", Pattern.DOTALL);
        Matcher matcher = projectSection.matcher(text);

        if (matcher.find()) {
            String section = matcher.group(1);
            String[] lines = section.split("\n");
            for (String line : lines) {
                String trimmed = line.trim().replaceAll("^[-*•]\\s*", "");
                if (trimmed.length() > 10 && trimmed.contains("-")) {
                    String[] parts = trimmed.split("-", 2);
                    ProjectDetail pd = new ProjectDetail();
                    pd.setTitle(parts[0].trim());
                    pd.setDescription(parts[1].trim());
                    projects.add(pd);
                    if (projects.size() >= 3) break;
                }
            }
        }

        return projects;
    }

    private List<String> extractCertifications(String text) {
        List<String> certs = new ArrayList<>();
        List<String> knownCerts = List.of(
                "AWS Certified Solutions Architect", "AWS Certified Cloud Practitioner",
                "AWS Certified Developer", "Oracle Certified Professional", "Oracle Certified Associate",
                "Google Cloud Certified", "Microsoft Certified: Azure Fundamentals",
                "Certified Kubernetes Administrator", "CKA", "CKAD"
        );

        for (String cert : knownCerts) {
            if (text.toLowerCase().contains(cert.toLowerCase())) {
                certs.add(cert);
            }
        }

        return certs;
    }
}
