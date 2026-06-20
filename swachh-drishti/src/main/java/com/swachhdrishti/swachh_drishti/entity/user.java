package com.swachhdrishti.swachh_drishti.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

// @Entity = "this class is a database table"
// @Table(name="users") = name the table "users" (avoids the reserved word "user")
@Entity
@Table(name = "users")
@Data            // auto-creates getters, setters, toString, equals
@Builder          // lets you build objects like User.builder().name("x").build()
@NoArgsConstructor // empty constructor — JPA needs this to rebuild objects from DB rows
@AllArgsConstructor // constructor with all fields — needed internally by @Builder
public class User implements UserDetails {
    // implements UserDetails = Spring Security can treat this class
    // as "the logged-in user" directly, no separate wrapper class needed

    @Id // primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL auto-increments this
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true) // no two users can share an email
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt HASH stored here, never plain text

    // @Builder.Default = without this, @Builder would ignore "=0" and set null
    @Builder.Default
    @Column(nullable = false)
    private Integer coins = 0;

    @Builder.Default
    @Column(name = "total_reports", nullable = false)
    private Integer totalReports = 0;

    @Builder.Default
    @Column(name = "resolved_reports", nullable = false)
    private Integer resolvedReports = 0;

    // Hibernate sets this ONCE when the row is created, never again
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Hibernate auto-refreshes this every time the row is updated
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Required because we implement UserDetails ──────────────────────
    // Single-role app — every user gets the same authority, hardcoded.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getUsername() {
        return email; // Spring Security calls login identity "username" — ours is email
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}