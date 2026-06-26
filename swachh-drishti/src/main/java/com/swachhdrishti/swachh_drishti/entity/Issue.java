package com.swachhdrishti.swachh_drishti.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "issues")  // NEW table, don't touch "reports" table
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // WHO reported this — always required
    // FetchType.LAZY: WHY — don't load the full User object from DB
    // unless someone actually accesses issue.getUser()
    // Without LAZY, every issue query also runs a user query = 2x DB calls
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // LOCATION — all three required for map pins and fraud detection
    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 500)
    private String address;

    // THE PHOTO — stored as a URL in cloud storage, not file bytes
    // WHY URL not bytes: storing image bytes in MySQL is a terrible idea
    // - Massively slows down every query
    // - MySQL is not designed for binary blobs
    // - Cloud storage is free, fast, and CDN-delivered
    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    // AI ANALYSIS FIELDS — all set by Gemini, never by the user
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false)
    private IssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(name = "urgency_score")
    private Integer urgencyScore;  // 1-10 from Gemini

    @Column(name = "responsible_department", length = 100)
    private String responsibleDepartment;  // "PWD", "WATER_BOARD" etc

    @Column(name = "citizen_advisory", length = 500)
    private String citizenAdvisory;  // Gemini's advice to citizen

    @Column(name = "ai_description", length = 500)
    private String aiDescription;  // Gemini's one-line description

    @Column(name = "estimated_resolution_days")
    private Integer estimatedResolutionDays;

    // LIFECYCLE — status changes over time
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private Status status = Status.REPORTED;

    // COMMUNITY VERIFICATION
    // WHY store count not a list: for the hackathon, a simple counter
    // is enough. A full upvote list (separate table) is Phase 2.
    @Builder.Default
    @Column(name = "upvote_count", nullable = false)
    private Integer upvoteCount = 0;

    // GAMIFICATION
    @Builder.Default
    @Column(name = "coins_awarded", nullable = false)
    private Integer coinsAwarded = 0;

    // RESOLUTION PROOF — set when worker marks resolved
    @Column(name = "resolution_image_url")
    private String resolutionImageUrl;  // The "after" photo URL

    @Column(name = "resolution_confidence")
    private Integer resolutionConfidence;  // 0-100, set by Gemini comparison

    @Column(name = "resolution_verified")
    private Boolean resolutionVerified;  // Did Gemini confirm it's fixed?

    @Column(name = "resolver_latitude")
    private Double resolverLatitude;  // GPS of resolver — for fraud check

    @Column(name = "resolver_longitude")
    private Double resolverLongitude;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    // TIMESTAMPS — auto-managed by Hibernate, never set manually
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ENUMS — defined here because they belong to Issue
    public enum IssueType {
        POTHOLE, GARBAGE, BROKEN_LIGHT, WATER_LEAKAGE,
        ENCROACHMENT, ROAD_DAMAGE, OTHER
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum Status {
        REPORTED,       // Just submitted
        VERIFIED,       // 3+ upvotes from community
        ASSIGNED,       // Municipal worker assigned
        IN_PROGRESS,    // Worker says they're working on it
        RESOLVED,       // Worker uploaded resolution photo
        REJECTED        // Spam or not a real issue
    }
}