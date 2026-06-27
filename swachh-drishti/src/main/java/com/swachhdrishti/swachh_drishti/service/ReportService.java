package com.swachhdrishti.swachh_drishti.service;

import com.swachhdrishti.swachh_drishti.dto.ReportResponse;
import com.swachhdrishti.swachh_drishti.dto.StatsResponse;
import com.swachhdrishti.swachh_drishti.entity.Report;
import com.swachhdrishti.swachh_drishti.entity.User;
import com.swachhdrishti.swachh_drishti.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportResponse submitReport(
            MultipartFile image,
            Double latitude,
            Double longitude,
            String address,
            String description,
            User currentUser) {

        // Save image locally
        String imageUrl = saveImage(image);

        Report report = Report.builder()
                .user(currentUser)
                .latitude(latitude)
                .longitude(longitude)
                .address(address)
                .Description(description)
                .imageUrl(imageUrl)
                .severityScore(3)
                .garbageType(Report.GarbageType.MIXED)
                .aiConfidence(0.8)
                .aiLabels("civic issue")
                .coinsAwarded(10)
                .build();

        Report saved = reportRepository.save(report);
        return mapToResponse(saved);
    }

    public List<ReportResponse> getReportsByUser(User user) {
        return reportRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<?> getHotspots() {
        return reportRepository.findAll();
    }

    public StatsResponse getStats() {
        long total = reportRepository.count();
        long pending = reportRepository.countByStatus(Report.Status.PENDING);
        long resolved = reportRepository.countByStatus(Report.Status.RESOLVED);

        return StatsResponse.builder()
                .totalReports(total)
                .pendingReports(pending)
                .resolvedReports(resolved)
                .topGarbageType("MIXED")
                .build();
    }

    private String saveImage(MultipartFile image) {
        try {
            String uploadDir = "uploads/";
            Files.createDirectories(Paths.get(uploadDir));
            String filename = UUID.randomUUID() + "_" + image.getOriginalFilename();
            Path path = Paths.get(uploadDir + filename);
            Files.write(path, image.getBytes());
            return "/uploads/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save image: " + e.getMessage());
        }
    }

    private ReportResponse mapToResponse(Report r) {
        return ReportResponse.builder()
                .id(r.getId())
                .address(r.getAddress())
                .description(r.getDescription())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .imageUrl(r.getImageUrl())
                .status(r.getStatus().name())
                .severityScore(r.getSeverityScore())
                .garbageType(r.getGarbageType() != null ? r.getGarbageType().name() : null)
                .coinsAwarded(r.getCoinsAwarded())
                .createdAt(r.getCreatedAt())
                .build();
    }
}