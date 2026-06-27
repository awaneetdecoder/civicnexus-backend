package com.swachhdrishti.swachh_drishti.controller;

import com.swachhdrishti.swachh_drishti.dto.IssueResponse;
import com.swachhdrishti.swachh_drishti.entity.User;
import com.swachhdrishti.swachh_drishti.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;

    // POST /api/issues
    // Flutter sends: multipart form with image + lat + lng + address
    // Returns: IssueResponse with AI analysis results
    // PROTECTED: needs JWT
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<IssueResponse> submitIssue(
            @RequestPart("image") MultipartFile image,
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam(value = "address", required = false) String address,
            @AuthenticationPrincipal User currentUser) throws Exception {

        IssueResponse response = issueService.submitIssue(
                image, latitude, longitude, address, currentUser);
        return ResponseEntity.status(201).body(response);
    }

    // GET /api/issues/all
    // Returns all issues as map pins — PUBLIC, no JWT needed
    // WHY public: anyone can see the map without logging in
    // This increases visibility and encourages more reporting
    @GetMapping("/all")
    public ResponseEntity<List<IssueResponse>> getAllIssues() {
        return ResponseEntity.ok(issueService.getAllIssues());
    }

    // GET /api/issues/mine
    // Returns only the logged-in user's issues
    // PROTECTED: needs JWT
    @GetMapping("/mine")
    public ResponseEntity<List<IssueResponse>> getMyIssues(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(issueService.getMyIssues(currentUser));
    }

    // POST /api/issues/{id}/upvote
    // Community verification — adds 1 upvote
    // Auto-verifies issue when 3+ upvotes reached
    // PROTECTED: needs JWT
    @PostMapping("/{id}/upvote")
    public ResponseEntity<IssueResponse> upvoteIssue(@PathVariable Long id) {
        return ResponseEntity.ok(issueService.upvoteIssue(id));
    }

    // POST /api/issues/{id}/resolve
    // Worker submits resolution proof photo
    // Runs fraud detection + Gemini before/after comparison
    // PROTECTED: needs JWT
    @PostMapping(value = "/{id}/resolve", consumes = "multipart/form-data")
    public ResponseEntity<IssueResponse> resolveIssue(
            @PathVariable Long id,
            @RequestPart("image") MultipartFile resolutionImage,
            @RequestParam("resolverLatitude") Double resolverLat,
            @RequestParam("resolverLongitude") Double resolverLng,
            @AuthenticationPrincipal User currentUser) throws Exception {

        IssueResponse response = issueService.resolveIssue(
                id, resolutionImage, resolverLat, resolverLng, currentUser);
        return ResponseEntity.ok(response);
    }

    // GET /api/issues/{id}/displacement-check
    // Supervisor tool — checks if resolved issue caused displacement
    // PROTECTED: needs JWT
    @GetMapping("/{id}/displacement-check")
    public ResponseEntity<Boolean> checkDisplacement(@PathVariable Long id) {
        return ResponseEntity.ok(issueService.checkDisplacement(id));
    }
}