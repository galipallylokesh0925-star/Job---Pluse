package com.aijobagent.service.impl;

import com.aijobagent.dto.applicationprep.ApplicationPreparationDto;
import com.aijobagent.dto.applicationprep.ProfileReviewDto;
import com.aijobagent.dto.applicationprep.ProfileReviewItemDto;
import com.aijobagent.dto.applicationprep.TruthfulnessValidationDto;
import com.aijobagent.exception.ResourceNotFoundException;
import com.aijobagent.model.CandidateProfile;
import com.aijobagent.model.Education;
import com.aijobagent.model.JobPosting;
import com.aijobagent.model.ProjectDetail;
import com.aijobagent.model.WorkExperience;
import com.aijobagent.repository.CandidateProfileRepository;
import com.aijobagent.repository.JobPostingRepository;
import com.aijobagent.service.ApplicationPreparationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ApplicationPreparationServiceImpl implements ApplicationPreparationService {

    private static final String VALIDATION_DISCLAIMER =
            "This deterministic check can flag likely unsupported statements, but it cannot prove a letter is truthful. Review it before submitting.";
    private static final Pattern PERSONAL_CLAIM = Pattern.compile(
            "(?i)\\b(?:i|my|we|our)\\s+(?:have|had|worked|built|led|developed|created|managed|achieved|increased|reduced|hold|earned|possess|completed|studied|graduated|am\\s+(?:certified|proficient|skilled|experienced|an?\\s+expert))\\b");
    private static final Pattern PROFILE_FACT_CLAIM = Pattern.compile(
            "(?i)\\bmy profile\\s+(?:lists|includes|shows|states|records)\\b");
    private static final Pattern QUALIFICATION_CLAIM = Pattern.compile(
            "(?i)\\b(?:certified|proficient|expert|skilled|experienced)\\b");
    private static final Pattern MEASURABLE_CLAIM = Pattern.compile(
            "(?i)\\b\\d+(?:[,.]\\d+)*(?:\\+)?\\s*(?:years?|months?|%|percent|people|persons?|users?|customers?|projects?|team members?|million|billion|k|lakh|crore)\\b");

    private final CandidateProfileRepository profileRepository;
    private final JobPostingRepository jobRepository;

    public ApplicationPreparationServiceImpl(
            CandidateProfileRepository profileRepository,
            JobPostingRepository jobRepository) {
        this.profileRepository = profileRepository;
        this.jobRepository = jobRepository;
    }

    @Override
    public ProfileReviewDto reviewActiveProfile() {
        return reviewProfile(getActiveProfile());
    }

    @Override
    public ApplicationPreparationDto prepareForJob(String jobId) {
        CandidateProfile profile = getActiveProfile();
        JobPosting job = getJob(jobId);
        List<String> matchedSkills = findMatchedSkills(profile, job);
        List<String> evidence = collectEvidence(profile, job, matchedSkills);
        String letter = buildCoverLetter(profile, job, matchedSkills);

        return new ApplicationPreparationDto(
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getApplyUrl(),
                letter,
                "Evidence-grounded deterministic template",
                evidence,
                reviewProfile(profile),
                validate(profile, letter));
    }

    @Override
    public TruthfulnessValidationDto validateCoverLetter(String jobId, String coverLetter) {
        CandidateProfile profile = getActiveProfile();
        getJob(jobId);
        if (coverLetter == null || coverLetter.isBlank()) {
            throw new IllegalArgumentException("Cover letter must not be blank");
        }
        return validate(profile, coverLetter);
    }

    private CandidateProfile getActiveProfile() {
        return profileRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException("No candidate profile is available."));
    }

    private JobPosting getJob(String jobId) {
        String normalizedJobId = java.util.Objects.requireNonNull(jobId, "Job ID is required").trim();
        if (normalizedJobId.isBlank()) {
            throw new IllegalArgumentException("Job ID must not be blank.");
        }
        return jobRepository.findById(normalizedJobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + normalizedJobId));
    }

    private ProfileReviewDto reviewProfile(CandidateProfile profile) {
        boolean resumeAvailable = hasText(profile.getRawResumeText());
        List<ProfileReviewItemDto> items = new ArrayList<>();
        int presentFields = 0;

        List<ProfileField> fields = List.of(
                new ProfileField("Full name", values(profile.getFullName()), true),
                new ProfileField("Email", values(profile.getEmail()), true),
                new ProfileField("Phone", values(profile.getPhone()), false),
                new ProfileField("Location", values(profile.getLocation()), false),
                new ProfileField("Professional summary", values(profile.getSummary()), false),
                new ProfileField("Skills", profile.getSkills(), true),
                new ProfileField("Work experience", experienceValues(profile), false),
                new ProfileField("Education", educationValues(profile), false),
                new ProfileField("Projects", projectValues(profile), false),
                new ProfileField("Certifications", profile.getCertifications(), false));

        for (ProfileField field : fields) {
            boolean present = field.values().stream().anyMatch(ApplicationPreparationServiceImpl::hasText);
            if (present) {
                presentFields++;
            }
            List<String> populatedValues = field.values().stream()
                    .filter(ApplicationPreparationServiceImpl::hasText)
                    .toList();
            long valuesFound = populatedValues.stream()
                    .filter(value -> containsNormalized(profile.getRawResumeText(), value))
                    .count();
            boolean verified = present && resumeAvailable && valuesFound == populatedValues.size();
            String status;
            String message;
            if (!present) {
                status = field.required() ? "MISSING" : "NOT_PROVIDED";
                message = field.required()
                        ? "Add this field before preparing an application."
                        : "No value is currently recorded.";
            } else if (!resumeAvailable) {
                status = "PROFILE_ONLY";
                message = "Present in the profile; upload a resume to check it against source text.";
            } else if (verified) {
                status = "RESUME_MATCH";
                message = "At least one recorded value appears in the uploaded resume text.";
            } else if (valuesFound > 0) {
                status = "PARTIAL_MATCH";
                message = "Some recorded values appear in the uploaded resume text; review the remaining values.";
            } else {
                status = "REVIEW";
                message = "Not found verbatim in the uploaded resume text; review the profile value.";
            }
            items.add(new ProfileReviewItemDto(field.label(), status, message));
        }

        long missingRequired = items.stream()
                .filter(item -> item.status().equals("MISSING"))
                .count();
        long reviewNeeded = items.stream()
                .filter(item -> item.status().equals("REVIEW") || item.status().equals("PROFILE_ONLY"))
                .count();
        String overallStatus = missingRequired > 0
                ? "INCOMPLETE"
                : reviewNeeded > 0 ? "NEEDS_REVIEW" : "READY";

        return new ProfileReviewDto(
                Math.round(presentFields * 100.0f / fields.size()),
                presentFields,
                fields.size(),
                resumeAvailable,
                overallStatus,
                items);
    }

    private String buildCoverLetter(CandidateProfile profile, JobPosting job, List<String> matchedSkills) {
        String title = hasText(job.getTitle()) ? job.getTitle() : "the advertised";
        String company = hasText(job.getCompany()) ? job.getCompany() : "your organization";
        StringBuilder letter = new StringBuilder("Dear Hiring Team,\n\n")
                .append("I am writing to apply for the ")
                .append(title)
                .append(" role at ")
                .append(company)
                .append(".\n\n");

        if (!matchedSkills.isEmpty()) {
            letter.append("My profile lists these skills that also appear in the job requirements: ")
                    .append(String.join(", ", matchedSkills))
                    .append(".\n\n");
        }

        profile.getExperience().stream()
                .filter(experience -> hasText(experience.getTitle()) || hasText(experience.getCompany()))
                .findFirst()
                .ifPresent(experience -> {
                    letter.append("My profile includes ");
                    if (hasText(experience.getTitle())) {
                        letter.append(experience.getTitle());
                    } else {
                        letter.append("experience");
                    }
                    if (hasText(experience.getCompany())) {
                        letter.append(" at ").append(experience.getCompany());
                    }
                    letter.append(".\n\n");
                });

        profile.getProjects().stream()
                .filter(project -> hasText(project.getTitle()))
                .findFirst()
                .ifPresent(project -> {
                    letter.append("A project listed in my profile is ")
                            .append(project.getTitle());
                    List<String> technologies = nonBlank(project.getTechnologies());
                    if (!technologies.isEmpty()) {
                        letter.append(", using ").append(String.join(", ", technologies));
                    }
                    letter.append(".\n\n");
                });

        profile.getEducation().stream()
                .filter(education -> hasText(education.getDegree()) || hasText(education.getInstitution()))
                .findFirst()
                .ifPresent(education -> {
                    letter.append("My profile lists ");
                    if (hasText(education.getDegree())) {
                        letter.append(education.getDegree());
                    } else {
                        letter.append("an education entry");
                    }
                    if (hasText(education.getInstitution())) {
                        letter.append(" from ").append(education.getInstitution());
                    }
                    letter.append(".\n\n");
                });

        letter.append("Thank you for considering my application. I would welcome the opportunity to discuss my profile and its relevance to this role.\n\n")
                .append("Sincerely");
        if (hasText(profile.getFullName())) {
            letter.append(",\n").append(profile.getFullName());
        }
        return letter.toString();
    }

    private TruthfulnessValidationDto validate(CandidateProfile profile, String coverLetter) {
        List<String> evidence = collectProfileFacts(profile);
        String evidenceText = String.join(" ", evidence) + " "
                + valueOr(profile.getRawResumeText(), "");
        List<String> unsupported = new ArrayList<>();
        int checkedClaims = 0;

        for (String sentence : coverLetter.split("(?<=[.!?])\\s+|\\R+")) {
            String statement = sentence.trim();
            if (statement.isEmpty()) {
                continue;
            }
            Matcher personalClaim = PERSONAL_CLAIM.matcher(statement);
            Matcher profileClaim = PROFILE_FACT_CLAIM.matcher(statement);
            Matcher qualificationClaim = QUALIFICATION_CLAIM.matcher(statement);
            Matcher measurableClaim = MEASURABLE_CLAIM.matcher(statement);
            boolean hasPersonalClaim = personalClaim.find();
            boolean hasProfileClaim = profileClaim.find();
            boolean hasQualificationClaim = qualificationClaim.find();
            boolean hasUnsupportedMeasure = false;
            while (measurableClaim.find()) {
                if (!containsNormalized(evidenceText, measurableClaim.group())) {
                    hasUnsupportedMeasure = true;
                }
            }
            if (hasPersonalClaim || hasProfileClaim || hasQualificationClaim || hasUnsupportedMeasure) {
                checkedClaims++;
                boolean unsupportedPersonalClaim = (hasPersonalClaim || hasQualificationClaim)
                        && !containsExactClaim(profile, statement);
                boolean unsupportedProfileClaim = hasProfileClaim
                        && !containsAnyEvidence(statement, evidence, profile.getRawResumeText());
                if (unsupportedPersonalClaim || unsupportedProfileClaim || hasUnsupportedMeasure) {
                    unsupported.add(statement);
                }
            }
        }

        return new TruthfulnessValidationDto(
                unsupported.isEmpty(),
                checkedClaims,
                List.copyOf(new LinkedHashSet<>(unsupported)),
                VALIDATION_DISCLAIMER);
    }

    private List<String> findMatchedSkills(CandidateProfile profile, JobPosting job) {
        List<String> profileSkills = nonBlank(profile.getSkills());
        List<String> matched = new ArrayList<>();
        for (String requiredSkill : nonBlank(job.getRequiredSkills())) {
            boolean listed = profileSkills.stream()
                    .anyMatch(skill -> skill.equalsIgnoreCase(requiredSkill));
            if (listed && matched.stream().noneMatch(skill -> skill.equalsIgnoreCase(requiredSkill))) {
                matched.add(requiredSkill);
            }
        }
        return matched;
    }

    private List<String> collectEvidence(CandidateProfile profile, JobPosting job, List<String> matchedSkills) {
        List<String> evidence = new ArrayList<>();
        addIfText(evidence, "Job: " + valueOr(job.getTitle(), "title not provided")
                + " at " + valueOr(job.getCompany(), "company not provided"));
        if (hasText(profile.getFullName())) {
            evidence.add("Candidate name from profile: " + profile.getFullName());
        }
        if (!matchedSkills.isEmpty()) {
            evidence.add("Skills listed in both profile and job requirements: " + String.join(", ", matchedSkills));
        }
        profile.getExperience().stream()
                .filter(experience -> hasText(experience.getTitle()) || hasText(experience.getCompany()))
                .findFirst()
                .ifPresent(experience -> evidence.add("Work experience listed in profile: "
                        + valueOr(experience.getTitle(), "role not provided") + " at "
                        + valueOr(experience.getCompany(), "organization not provided")));
        profile.getProjects().stream()
                .filter(project -> hasText(project.getTitle()))
                .findFirst()
                .ifPresent(project -> evidence.add("Project listed in profile: " + project.getTitle()));
        profile.getEducation().stream()
                .filter(education -> hasText(education.getDegree()) || hasText(education.getInstitution()))
                .findFirst()
                .ifPresent(education -> evidence.add("Education listed in profile: "
                        + valueOr(education.getDegree(), "qualification not provided") + " at "
                        + valueOr(education.getInstitution(), "institution not provided")));
        return List.copyOf(evidence);
    }

    private List<String> collectProfileFacts(CandidateProfile profile) {
        List<String> facts = new ArrayList<>();
        addIfText(facts, profile.getFullName());
        addIfText(facts, profile.getEmail());
        addIfText(facts, profile.getPhone());
        addIfText(facts, profile.getLocation());
        addIfText(facts, profile.getSummary());
        addAll(facts, profile.getSkills());
        addAll(facts, profile.getCertifications());
        for (Education education : profile.getEducation()) {
            addIfText(facts, education.getDegree());
            addIfText(facts, education.getInstitution());
            addIfText(facts, education.getFieldOfStudy());
            addIfText(facts, education.getStartDate());
            addIfText(facts, education.getEndDate());
            addIfText(facts, education.getGrade());
        }
        for (WorkExperience experience : profile.getExperience()) {
            addIfText(facts, experience.getTitle());
            addIfText(facts, experience.getCompany());
            addIfText(facts, experience.getLocation());
            addIfText(facts, experience.getStartDate());
            addIfText(facts, experience.getEndDate());
            addAll(facts, experience.getBulletPoints());
        }
        for (ProjectDetail project : profile.getProjects()) {
            addIfText(facts, project.getTitle());
            addIfText(facts, project.getDescription());
            addAll(facts, project.getTechnologies());
        }
        return facts;
    }

    private List<String> experienceValues(CandidateProfile profile) {
        List<String> values = new ArrayList<>();
        for (WorkExperience experience : profile.getExperience()) {
            addIfText(values, experience.getTitle());
            addIfText(values, experience.getCompany());
            addIfText(values, experience.getLocation());
            addAll(values, experience.getBulletPoints());
        }
        return values;
    }

    private List<String> educationValues(CandidateProfile profile) {
        List<String> values = new ArrayList<>();
        for (Education education : profile.getEducation()) {
            addIfText(values, education.getDegree());
            addIfText(values, education.getInstitution());
            addIfText(values, education.getFieldOfStudy());
            addIfText(values, education.getStartDate());
            addIfText(values, education.getEndDate());
            addIfText(values, education.getGrade());
        }
        return values;
    }

    private List<String> projectValues(CandidateProfile profile) {
        List<String> values = new ArrayList<>();
        for (ProjectDetail project : profile.getProjects()) {
            addIfText(values, project.getTitle());
            addIfText(values, project.getDescription());
            addAll(values, project.getTechnologies());
        }
        return values;
    }

    private static boolean containsAnyEvidence(String statement, List<String> evidence, String rawResumeText) {
        if (hasText(rawResumeText) && containsNormalized(rawResumeText, statement)) {
            return true;
        }
        return evidence.stream()
                .filter(ApplicationPreparationServiceImpl::hasText)
                .map(ApplicationPreparationServiceImpl::normalize)
                .filter(value -> value.length() >= 3)
                .anyMatch(normalize(statement)::contains);
    }

    private boolean containsExactClaim(CandidateProfile profile, String statement) {
        if (hasText(profile.getRawResumeText()) && containsNormalized(profile.getRawResumeText(), statement)) {
            return true;
        }
        return collectProfileFacts(profile).stream()
                .filter(ApplicationPreparationServiceImpl::hasText)
                .anyMatch(fact -> containsNormalized(fact, statement));
    }

    private static boolean containsNormalized(String source, String value) {
        String normalizedSource = normalize(source);
        String normalizedValue = normalize(value);
        return !normalizedValue.isEmpty() && normalizedSource.contains(normalizedValue);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static List<String> values(String value) {
        return hasText(value) ? List.of(value) : List.of();
    }

    private static List<String> nonBlank(List<String> values) {
        return values == null ? List.of() : values.stream().filter(ApplicationPreparationServiceImpl::hasText).toList();
    }

    private static void addAll(Collection<String> target, List<String> values) {
        if (values != null) {
            values.stream().filter(ApplicationPreparationServiceImpl::hasText).forEach(target::add);
        }
    }

    private static void addIfText(Collection<String> target, String value) {
        if (hasText(value)) {
            target.add(value.trim());
        }
    }

    private static String valueOr(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ProfileField(String label, List<String> values, boolean required) {}
}
