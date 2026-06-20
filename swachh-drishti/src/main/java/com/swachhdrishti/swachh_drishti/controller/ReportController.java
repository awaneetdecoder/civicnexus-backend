package com.swachhdrishti.swachh_drishti.controller;

import com.swachhdrishti.swachh_drishti.dto.ReportResponse;
import com.swachhdrishti.swachh_drishti.dto.StatsResponse;
import com.swachhdrishti.swachh_drishti.entity.User;
import com.swachhdrishti.swachh_drishti.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// @RestController + @RequestMapping — same pattern as AuthController
// All report endpoints live under /api/reports
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // @PostMapping — POST /api/reports
    // This is a MULTIPART request — it carries both form fields AND a file
    // Flutter sends this using MultipartRequest, not plain JSON

    // @RequestPart("image") MultipartFile image —
    // "image" must match the field name Flutter sends in the multipart form
    // MultipartFile is Spring's abstraction over an uploaded file

    // @RequestParam — reads individual form fields from the multipart request
    // WHY not @RequestBody here: when sending a file, Flutter cannot send
    // JSON body at the same time. Form fields + file = multipart. Text fields
    // in a multipart request are @RequestParam, not @RequestBody.

    // required = false — address and description are optional fields
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ReportResponse> submitReport(
            @RequestPart("image") MultipartFile image,
            @RequestParam("latitude") Double latitude,
            @RequestParam("longitude") Double longitude,
            @RequestParam(value = "address", required = false) String address,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal User currentUser) {

        // Pass everything to service — controller does NOTHING else
        ReportResponse response = reportService.submitReport(
                image, latitude, longitude, address, description, currentUser
        );
        return ResponseEntity.status(201).body(response);
    }

    // @GetMapping("/myreports") — GET /api/reports/myreports
    // Returns all reports submitted by the currently logged-in user
    // @AuthenticationPrincipal gives us the user from JWT — no need to
    // accept a userId in the URL (that would let a user view someone else's reports)
    @GetMapping("/myreports")
    public ResponseEntity<List<ReportResponse>> getMyReports(
            @AuthenticationPrincipal User currentUser) {

        List<ReportResponse> reports = reportService.getReportsByUser(currentUser);
        return ResponseEntity.ok(reports);
    }

    // @GetMapping("/hotspots") — GET /api/reports/hotspots
    // Returns GPS-clustered zones with 2+ reports
    // City-wide data, shown inside the Flutter app's map tab
    @GetMapping("/hotspots")
    public ResponseEntity<List<?>> getHotspots() {
        return ResponseEntity.ok(reportService.getHotspots());
    }

    // @GetMapping("/stats") — GET /api/reports/stats
    // Returns city-wide aggregate numbers (total reports, top garbage type, etc.)
    @GetMapping("/stats")
    public ResponseEntity<StatsResponse> getStats() {
        return ResponseEntity.ok(reportService.getStats());
    }

    // NOTE: PUT /{id}/status (officer status update) intentionally removed.
    // Not in scope — single-role citizen app, no municipal officer role.
}