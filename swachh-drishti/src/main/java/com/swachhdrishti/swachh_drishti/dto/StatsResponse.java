package com.swachhdrishti.swachh_drishti.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    private Long totalReports;
    private Long pendingReports;
    private Long resolvedReports;
    private String topGarbageType;
}