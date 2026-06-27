package com.swachhdrishti.swachh_drishti.repository;

import com.swachhdrishti.swachh_drishti.entity.Report;
import com.swachhdrishti.swachh_drishti.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByUserOrderByCreatedAtDesc(User user);
    Long countByStatus(Report.Status status);
}