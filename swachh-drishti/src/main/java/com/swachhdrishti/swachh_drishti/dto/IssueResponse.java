package com.swachhdrishti.swachh_drishti.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// WHY separate IssueResponse from ReportResponse:
// Issue is the new entity with Gemini AI fields
// ReportResponse is for the old Report entity — keep both working
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueResponse {
    private Long id;
    private String address;
    private Double latitude;
    private Double longitude;
    private String imageUrl;
    private String issueType;
    private String severity;
    private Integer urgencyScore;
    private String responsibleDepartment;
    private String citizenAdvisory;
    private String description;
    private String status;
    private Integer upvoteCount;
    private Integer coinsAwarded;
    private String reporterName;
    private LocalDateTime createdAt;
}