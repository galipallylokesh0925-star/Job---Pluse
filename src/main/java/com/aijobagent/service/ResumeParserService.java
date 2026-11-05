package com.aijobagent.service;

import com.aijobagent.dto.profile.ExtractedResumeData;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service contract for parsing resume documents (PDF, Word DOCX, TXT)
 * into structured candidate data and raw text.
 */
public interface ResumeParserService {

    /**
     * Parses a MultipartFile resume into extracted structured data and full text.
     *
     * @param file the uploaded file
     * @return ExtractedResumeData containing contact details, skills, experience, education, projects, and raw text
     */
    ExtractedResumeData parse(MultipartFile file);

    /**
     * Extracts structured profile entities from an existing raw text string.
     * Useful for direct text pasting or re-evaluating heuristics.
     */
    ExtractedResumeData parseText(String rawText, String engine);
}
