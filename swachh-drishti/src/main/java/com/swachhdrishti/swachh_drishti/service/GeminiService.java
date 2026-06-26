package com.swachhdrishti.swachh_drishti.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY no @RequiredArgsConstructor here:
// @RequiredArgsConstructor generates a constructor for ALL final fields.
// But OkHttpClient and ObjectMapper are initialized inline (= new ...()).
// Lombok and inline initialization conflict — Lombok wins and breaks it.
// @Value fields also cannot be final because Spring injects AFTER construction.
// Solution: no Lombok constructor annotation, no final on these fields.
@Service
public class GeminiService {

    // WHY @Value:
    // Reads gemini.api.key from application.properties at startup.
    // That file is in .gitignore — key never touches git.
    // CANNOT be final — Spring injects this after the object is constructed.
    @Value("${gemini.api.key}")
    private String apiKey;

    // WHY not final:
    // These are instantiated inline. If you mark them final AND use
    // @RequiredArgsConstructor, Lombok puts them in the constructor
    // signature — but they already have values. Conflict → errors.
    // No annotation needed. Spring never touches these — we manage them.
    private OkHttpClient httpClient = new OkHttpClient();
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";

    // ─────────────────────────────────────────────
    // ANALYZE ISSUE — called when citizen submits report
    // ─────────────────────────────────────────────

    // WHY MultipartFile parameter:
    // Spring receives the uploaded photo as MultipartFile.
    // Gemini API does not accept file uploads directly.
    // So we read the bytes and encode to Base64 string.
    // Base64 is a way to represent binary data as text — safe to put in JSON.
    public Map<String, Object> analyzeIssue(MultipartFile image) throws Exception {
        String base64Image = Base64.getEncoder().encodeToString(image.getBytes());
        String mimeType = image.getContentType() != null ? image.getContentType() : "image/jpeg";

        // WHY HashMap and not Map.of():
        // Map.of() only accepts up to 10 key-value pairs — Java limitation.
        // HashMap has no limit.
        // Also Map.of() throws NullPointerException if any value is null.
        // HashMap is safer for dynamic data.
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mime_type", mimeType);
        inlineData.put("data", base64Image);

        Map<String, Object> imagePart = new HashMap<>();
        imagePart.put("inline_data", inlineData);

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", buildAnalysisPrompt());

        // Gemini expects: contents[0].parts = [textPart, imagePart]
        // WHY array not List: Gemini's API schema uses JSON arrays here.
        // Object[] serializes to JSON array correctly via ObjectMapper.
        Map<String, Object> content = new HashMap<>();
        content.put("parts", new Object[]{textPart, imagePart});

        Map<String, Object> generationConfig = new HashMap<>();
        // WHY temperature 0.1:
        // Temperature controls randomness. 0 = fully deterministic.
        // We want consistent JSON structure every time, not creative variation.
        // 0.1 is near-deterministic but avoids edge case bugs at exactly 0.
        generationConfig.put("temperature", 0.1);
        generationConfig.put("maxOutputTokens", 500);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", new Object[]{content});
        requestBody.put("generationConfig", generationConfig);

        return callGemini(requestBody);
    }

    // ─────────────────────────────────────────────
    // COMPARE BEFORE/AFTER — called when worker submits resolution
    // ─────────────────────────────────────────────

    // WHY two images:
    // originalImageUrl = the photo citizen took when reporting (stored in DB)
    // resolutionImage  = the photo worker just took claiming it's fixed
    // Gemini compares them and tells us if the issue is actually resolved.
    public Map<String, Object> compareBeforeAfter(
            MultipartFile resolutionImage,
            String originalImageUrl) throws Exception {

        String base64Resolution = Base64.getEncoder()
                .encodeToString(resolutionImage.getBytes());

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", buildComparisonPrompt());

        // Image 1: original issue photo, referenced by URL
        // WHY url reference and not base64:
        // The original image is already in cloud storage (or your server).
        // Gemini can fetch it by URL — no need to re-download and re-encode.
        Map<String, Object> originalUrlMap = new HashMap<>();
        originalUrlMap.put("url", originalImageUrl);
        Map<String, Object> originalImagePart = new HashMap<>();
        originalImagePart.put("image_url", originalUrlMap);

        // Image 2: resolution photo, sent as base64 (just uploaded, no URL yet)
        Map<String, Object> resolutionInlineData = new HashMap<>();
        resolutionInlineData.put("mime_type", "image/jpeg");
        resolutionInlineData.put("data", base64Resolution);
        Map<String, Object> resolutionImagePart = new HashMap<>();
        resolutionImagePart.put("inline_data", resolutionInlineData);

        Map<String, Object> content = new HashMap<>();
        // Order matters: text prompt first, then image 1, then image 2
        content.put("parts", new Object[]{textPart, originalImagePart, resolutionImagePart});

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.1);
        generationConfig.put("maxOutputTokens", 300);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", new Object[]{content});
        requestBody.put("generationConfig", generationConfig);

        return callGemini(requestBody);
    }

    // ─────────────────────────────────────────────
    // CORE HTTP CALL — shared by both methods above
    // ─────────────────────────────────────────────

    private Map<String, Object> callGemini(Map<String, Object> requestBody) throws Exception {
        // ObjectMapper converts Java Map → JSON string
        // WHY not manual string building:
        // Your prompt contains quotes and special characters.
        // Manual string building breaks JSON. ObjectMapper handles all escaping.
        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // OkHttp request construction
        // WHY OkHttp and not RestTemplate:
        // RestTemplate is being deprecated in Spring 6+.
        // OkHttp is simpler for one-off external API calls with raw JSON.
        Request request = new Request.Builder()
                .url(GEMINI_URL + "?key=" + apiKey)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        // WHY try-with-resources (try (Response response = ...)):
        // OkHttp Response holds a network connection open.
        // try-with-resources guarantees response.close() is called even if
        // an exception occurs — prevents connection leaks.
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // Include response body in error so you can debug what Gemini returned
                String errorBody = response.body() != null ? response.body().string() : "no body";
                throw new RuntimeException(
                        "Gemini API error: HTTP " + response.code() + " — " + errorBody);
            }

            // WHY null check on body():
            // OkHttp's body() CAN return null for certain response types (204 No Content).
            // Without null check, you get NullPointerException at runtime.
            ResponseBody responseBodyObj = response.body();
            if (responseBodyObj == null) {
                throw new RuntimeException("Gemini returned empty response body");
            }
            String responseBodyString = responseBodyObj.string();

            // Parse outer Gemini response wrapper
            // WHY TypeReference instead of Map.class:
            // objectMapper.readValue(str, Map.class) gives unchecked cast warning.
            // TypeReference tells Jackson the exact generic type — type-safe, no warning.
            Map<String, Object> geminiResponse = objectMapper.readValue(
                    responseBodyString,
                    new TypeReference<Map<String, Object>>() {}
            );

            // Navigate the nested Gemini response structure:
            // geminiResponse
            //   └── candidates (List)
            //         └── [0] (Map)
            //               └── content (Map)
            //                     └── parts (List)
            //                           └── [0] (Map)
            //                                 └── text (String) ← this is our JSON
            List<?> candidates = (List<?>) geminiResponse.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new RuntimeException("Gemini returned no candidates");
            }

            Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
            List<?> parts = (List<?>) content.get("parts");
            String text = (String) ((Map<?, ?>) parts.get(0)).get("text");

            if (text == null || text.isBlank()) {
                throw new RuntimeException("Gemini returned empty text in response");
            }

            // WHY trim():
            // Gemini sometimes adds leading/trailing whitespace or newlines.
            // jsonDecode fails on ANY character outside the JSON braces.
            String cleanText = text.trim();

            // WHY strip markdown fences:
            // Even with "Return ONLY valid JSON" in prompt,
            // Gemini occasionally wraps in ```json ... ```.
            // This strips it if present.
            if (cleanText.startsWith("```")) {
                cleanText = cleanText
                        .replaceAll("^```json\\s*", "")
                        .replaceAll("^```\\s*", "")
                        .replaceAll("```$", "")
                        .trim();
            }

            // Parse Gemini's inner JSON text into a Map we can use
            return objectMapper.readValue(
                    cleanText,
                    new TypeReference<Map<String, Object>>() {}
            );
        }
    }

    // ─────────────────────────────────────────────
    // PROMPTS
    // ─────────────────────────────────────────────

    private String buildAnalysisPrompt() {
        // WHY text block ("""):
        // Java 15+ feature. Preserves formatting without \n escapes.
        // Cleaner to read and edit than single-line strings.
        return """
                You are a municipal AI officer analyzing a civic issue photo from India.
                Analyze the image carefully and return ONLY valid JSON.
                No markdown, no explanation, no ```json wrapper. Just the raw JSON object.
                
                {
                  "issueType": "POTHOLE|GARBAGE|BROKEN_LIGHT|WATER_LEAKAGE|ENCROACHMENT|ROAD_DAMAGE|OTHER",
                  "severity": "LOW|MEDIUM|HIGH|CRITICAL",
                  "urgencyScore": <integer 1-10>,
                  "responsibleDepartment": "PWD|MUNICIPAL_CORPORATION|ELECTRICITY_BOARD|WATER_BOARD|TRAFFIC_POLICE",
                  "isActuallyCivicIssue": <true or false>,
                  "citizenAdvisory": "<one sentence advice for the citizen>",
                  "estimatedResolutionDays": <integer>,
                  "description": "<one sentence describing exactly what you see in the image>"
                }
                
                Severity guide:
                LOW = minor inconvenience, no safety risk
                MEDIUM = affects daily life but not immediately dangerous
                HIGH = safety risk, needs attention within 3 days
                CRITICAL = immediate danger, risk of injury or serious damage
                
                If the image does not show a civic issue, set isActuallyCivicIssue to false
                and set issueType to OTHER.""";
    }

    private String buildComparisonPrompt() {
        return """
                You are verifying whether a civic issue has been resolved.
                You are given two images of the same location.
                Image 1 is the original reported problem.
                Image 2 is the claimed resolution.
                
                Return ONLY valid JSON. No markdown, no explanation:
                
                {
                  "isResolved": <true or false>,
                  "confidence": <integer 0-100>,
                  "isPartialFix": <true or false>,
                  "reason": "<one sentence explaining your conclusion based on visual evidence>",
                  "shouldEscalate": <true or false>
                }
                
                isResolved: true only if the specific issue from Image 1 is clearly gone in Image 2.
                confidence: how certain you are (100 = absolutely certain, 0 = cannot tell).
                isPartialFix: true if the problem is reduced but not fully resolved.
                shouldEscalate: true if confidence is below 60 or if the fix looks temporary.""";
    }
}