package com.swachhdrishti.swachh_drishti.repository;

import com.swachhdrishti.swachh_drishti.entity.Issue;
import com.swachhdrishti.swachh_drishti.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

// WHY extend JpaRepository<Issue, Long>:
// JpaRepository gives you save(), findById(), findAll(), delete() for FREE
// You only write methods that are custom queries
// Spring generates the SQL from the method NAME automatically
public interface IssueRepository extends JpaRepository<Issue, Long> {

    // Spring sees "findByReporter" → generates:
    // SELECT * FROM issues WHERE reporter_id = ?
    List<Issue> findByReporterOrderByCreatedAtDesc(User reporter);

    // findByStatus → SELECT * FROM issues WHERE status = ?
    List<Issue> findByStatusOrderByUrgencyScoreDesc(Issue.Status status);

    // Custom @Query for displacement detection
    // WHY native SQL and not JPQL:
    // Haversine distance formula is SQL math — no JPQL equivalent
    // :lat, :lng, :km are named parameters — Spring replaces them safely
    // (prevents SQL injection)
    @Query(value = """
        SELECT * FROM issues 
        WHERE issue_type = :issueType
        AND status IN ('REPORTED', 'VERIFIED', 'ASSIGNED', 'IN_PROGRESS')
        AND created_at > :since
        AND (
            6371 * acos(
                cos(radians(:lat)) * cos(radians(latitude)) *
                cos(radians(longitude) - radians(:lng)) +
                sin(radians(:lat)) * sin(radians(latitude))
            )
        ) < :km
        ORDER BY created_at DESC
        """, nativeQuery = true)
    List<Issue> findNearbyIssuesOfSameType(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("km") double km,
            @Param("issueType") String issueType,
            @Param("since") LocalDateTime since
    );

    // For dashboard stats
    @Query("SELECT i.issueType, COUNT(i) FROM Issue i GROUP BY i.issueType")
    List<Object[]> countByIssueType();

    @Query("SELECT i.severity, COUNT(i) FROM Issue i GROUP BY i.severity")
    List<Object[]> countBySeverity();
}