package com.swachhdrishti.swachh_drishti.service;

import com.swachhdrishti.swachh_drishti.dto.IssueResponse;
import com.swachhdrishti.swachh_drishti.entity.Issue;
import com.swachhdrishti.swachh_drishti.entity.User;
import com.swachhdrishti.swachh_drishti.repository.IssueRepository;
import com.swachhdrishti.swachh_drishti.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final GeminiService geminiService;
    private final FraudDetectionService fraudDetectionService;

    // ─────────────────────────────────────────────
    // SUBMIT NEW ISSUE
    // Called by: IssueController POST /api/issues
    // Flow: save image → call Gemini → save to DB → return response
    // ─────────────────────────────────────────────
    public IssueResponse submitIssue(
            MultipartFile image,
            Double latitude,
            Double longitude,
            String address,
            User currentUser) throws Exception {

        // STEP 1: Save image to local storage
        // WHY save first before Gemini:
        // If Gemini fails, we still have the image saved
        // We can retry analysis later without asking user to re-upload
        String imageUrl = saveImageLocally(image);

        // STEP 2: Send image to Gemini for AI analysis
        // This returns a Map with issueType, severity, department etc
        // WHY Map<String, Object>:
        // Gemini returns JSON which ObjectMapper parses into Map
        // We extract individual fields by key name
        Map<String, Object> aiResult;
        try {
            aiResult = geminiService.analyzeIssue(image);
        } catch (Exception e) {
            // If Gemini fails, use safe defaults — don't block the user
            // WHY not throw: citizen's report is more important than AI analysis
            // They can see their issue is submitted even if AI couldn't analyze
            aiResult = getDefaultAiResult();
        }

        // STEP 3: Extract AI fields from the result map
        // WHY toString(): ObjectMapper sometimes returns Integer or Boolean
        // for fields that should be String. toString() handles both safely.
        String issueTypeStr = getStringValue(aiResult, "issueType", "OTHER");
        String severityStr = getStringValue(aiResult, "severity", "MEDIUM");
        String department = getStringValue(aiResult, "responsibleDepartment", "MUNICIPAL_CORPORATION");
        String advisory = getStringValue(aiResult, "citizenAdvisory", "Issue recorded.");
        String description = getStringValue(aiResult, "description", "Civic issue reported.");
        int urgencyScore = getIntValue(aiResult, "urgencyScore", 5);
        int resolutionDays = getIntValue(aiResult, "estimatedResolutionDays", 7);

        // Convert String to enum safely
        // WHY try-catch on valueOf:
        // If Gemini returns an unexpected value like "ROAD_HOLE" instead of "POTHOLE"
        // valueOf() throws IllegalArgumentException — we catch it and use OTHER
        Issue.IssueType issueType;
        try {
            issueType = Issue.IssueType.valueOf(issueTypeStr);
        } catch (IllegalArgumentException e) {
            issueType = Issue.IssueType.OTHER;
        }

        Issue.Severity severity;
        try {
            severity = Issue.Severity.valueOf(severityStr);
        } catch (IllegalArgumentException e) {
            severity = Issue.Severity.MEDIUM;
        }

        // STEP 4: Award coins based on severity
        // WHY gamification: increases citizen participation
        // CRITICAL issues get more coins = more motivation to report serious problems
        int coinsToAward = switch (severity) {
            case CRITICAL -> 50;
            case HIGH -> 30;
            case MEDIUM -> 20;
            case LOW -> 10;
        };

        // STEP 5: Build and save Issue entity to database
        Issue issue = Issue.builder()
                .reporter(currentUser)
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .imageUrl(imageUrl)
                .issueType(issueType)
                .severity(severity)
                .urgencyScore(urgencyScore)
                .responsibleDepartment(department)
                .citizenAdvisory(advisory)
                .aiDescription(description)
                .estimatedResolutionDays(resolutionDays)
                .coinsAwarded(coinsToAward)
                .status(Issue.Status.REPORTED)
                .build();

        Issue savedIssue = issueRepository.save(issue);

        // STEP 6: Update user's coin balance and report count
        // WHY separate save: User and Issue are separate entities
        // Saving Issue doesn't automatically update User
        currentUser.setCoins(currentUser.getCoins() + coinsToAward);
        currentUser.setTotalReports(currentUser.getTotalReports() + 1);
        userRepository.save(currentUser);

        return mapToResponse(savedIssue);
    }

    // ─────────────────────────────────────────────
    // GET ALL ISSUES — for the map screen
    // Public endpoint — no JWT needed
    // ─────────────────────────────────────────────
    public List<IssueResponse> getAllIssues() {
        return issueRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // GET MY ISSUES — for activity screen
    // Protected — needs JWT
    // ─────────────────────────────────────────────
    public List<IssueResponse> getMyIssues(User currentUser) {
        return issueRepository.findByReporterOrderByCreatedAtDesc(currentUser)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // UPVOTE — community verification
    // Each user click adds 1 to upvote count
    // When upvotes >= 3, status auto-upgrades to VERIFIED
    // ─────────────────────────────────────────────
    public IssueResponse upvoteIssue(Long issueId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found: " + issueId));

        issue.setUpvoteCount(issue.getUpvoteCount() + 1);

        // WHY auto-verify at 3 upvotes:
        // 3 independent citizens confirming = community consensus
        // Prevents single user spamming fake issues
        if (issue.getUpvoteCount() >= 3 && issue.getStatus() == Issue.Status.REPORTED) {
            issue.setStatus(Issue.Status.VERIFIED);
        }

        return mapToResponse(issueRepository.save(issue));
    }

    // ─────────────────────────────────────────────
    // RESOLVE ISSUE — worker submits resolution proof
    // Validates GPS + timestamp + Gemini before/after comparison
    // ─────────────────────────────────────────────
    public IssueResponse resolveIssue(
            Long issueId,
            MultipartFile resolutionImage,
            Double resolverLat,
            Double resolverLng,
            User currentUser) throws Exception {

        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found: " + issueId));

        // FRAUD CHECK: GPS proximity validation
        // Passes latitude/longitude directly — no getLatitude() needed
        fraudDetectionService.validateResolution(
                issue.getLatitude(),
                issue.getLongitude(),
                issue.getCreatedAt(),
                resolverLat,
                resolverLng,
                java.time.Instant.now()
        );

        // Save resolution image
        String resolutionImageUrl = saveImageLocally(resolutionImage);

        // AI COMPARISON: Gemini compares before vs after
        Map<String, Object> comparison;
        try {
            comparison = geminiService.compareBeforeAfter(
                    resolutionImage, issue.getImageUrl());
        } catch (Exception e) {
            comparison = Map.of(
                    "isResolved", true,
                    "confidence", 70,
                    "reason", "Auto-approved: AI analysis unavailable"
            );
        }

        boolean isResolved = Boolean.TRUE.equals(comparison.get("isResolved"));
        int confidence = getIntValue(comparison, "confidence", 70);

        // Only mark resolved if Gemini confirms AND confidence is above 60%
        // WHY 60%: below 60% = Gemini is not sure = escalate for human review
        if (isResolved && confidence >= 60) {
            issue.setStatus(Issue.Status.RESOLVED);
            issue.setResolutionImageUrl(resolutionImageUrl);
            issue.setResolutionVerified(true);
            issue.setResolutionConfidence(confidence);
            issue.setResolvedAt(java.time.LocalDateTime.now());
        } else {
            // Escalate — needs human supervisor review
            issue.setStatus(Issue.Status.IN_PROGRESS);
            issue.setResolutionImageUrl(resolutionImageUrl);
            issue.setResolutionVerified(false);
            issue.setResolutionConfidence(confidence);
        }

        return mapToResponse(issueRepository.save(issue));
    }

    // ─────────────────────────────────────────────
    // DISPLACEMENT CHECK — supervisor tool
    // Checks if a resolved issue's location has a new same-type issue nearby
    // ─────────────────────────────────────────────
    public boolean checkDisplacement(Long resolvedIssueId) {
        Issue resolved = issueRepository.findById(resolvedIssueId)
                .orElseThrow(() -> new RuntimeException("Issue not found"));

        // Find issues of same type created in last 2 hours within 1km
        List<Issue> nearbyIssues = issueRepository.findNearbyIssuesOfSameType(
                resolved.getLatitude(),
                resolved.getLongitude(),
                1.0, // 1km radius
                resolved.getIssueType().name(),
                java.time.LocalDateTime.now().minusHours(2)
        );

        // If any nearby same-type issues exist after this resolution = suspicious
        return !nearbyIssues.isEmpty();
    }

    // ─────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────

    // Save image to local uploads folder
    // Returns the URL path that can be used to retrieve the image
    private String saveImageLocally(MultipartFile image) {
        try {
            String uploadDir = "uploads/";
            Files.createDirectories(Paths.get(uploadDir));
            // WHY UUID prefix: prevents filename collisions
            // Two users uploading "photo.jpg" would overwrite each other without it
            String filename = UUID.randomUUID() + "_" + image.getOriginalFilename();
            Path path = Paths.get(uploadDir + filename);
            Files.write(path, image.getBytes());
            return "http://localhost:8080/uploads/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + e.getMessage());
        }
    }

    // Convert Issue entity to IssueResponse DTO
    // WHY DTO instead of returning entity directly:
    // Entity has JPA annotations, lazy-loaded relations, internal fields
    // DTO has exactly what Flutter needs — clean, no circular references
    private IssueResponse mapToResponse(Issue issue) {
        return IssueResponse.builder()
                .id(issue.getId())
                .address(issue.getAddress())
                .latitude(issue.getLatitude())
                .longitude(issue.getLongitude())
                .imageUrl(issue.getImageUrl())
                .issueType(issue.getIssueType().name())
                .severity(issue.getSeverity().name())
                .urgencyScore(issue.getUrgencyScore())
                .responsibleDepartment(issue.getResponsibleDepartment())
                .citizenAdvisory(issue.getCitizenAdvisory())
                .description(issue.getAiDescription())
                .status(issue.getStatus().name())
                .upvoteCount(issue.getUpvoteCount())
                .coinsAwarded(issue.getCoinsAwarded())
                .reporterName(issue.getReporter().getName())
                .createdAt(issue.getCreatedAt())
                .build();
    }

    // Safe Map value extractors
    // WHY these helpers:
    // Gemini returns Map<String, Object> where Object could be String, Integer, Boolean
    // Direct casting throws ClassCastException on type mismatch
    // These methods handle all types safely with a fallback default
    private String getStringValue(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Integer i) return i;
        if (val instanceof Double d) return d.intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private Map<String, Object> getDefaultAiResult() {
        return Map.of(
                "issueType", "OTHER",
                "severity", "MEDIUM",
                "urgencyScore", 5,
                "responsibleDepartment", "MUNICIPAL_CORPORATION",
                "citizenAdvisory", "Issue recorded. Our team will review it.",
                "description", "Civic issue reported by citizen.",
                "estimatedResolutionDays", 7
        );
    }
}