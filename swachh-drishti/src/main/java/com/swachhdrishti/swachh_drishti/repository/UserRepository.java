package com.swachhdrishti.swachh_drishti.repository;

import com.swachhdrishti.swachh_drishti.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// WHY Optional<User>:
// findByEmail might return null if email doesn't exist
// Optional forces the caller to handle the null case explicitly
// Without Optional, forgetting to null-check causes NullPointerException
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}