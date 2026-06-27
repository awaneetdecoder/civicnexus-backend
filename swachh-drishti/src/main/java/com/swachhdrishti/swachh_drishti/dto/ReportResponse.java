package com.swachhdrishti.swachh_drishti.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private Long id;
    private String address;
    private String description;
    private Double latitude;
    private Double longitude;
    private String imageUrl;
    private String status;
    private Integer severityScore;
    private String garbageType;
    private Integer coinsAwarded;
    private LocalDateTime createdAt;
}