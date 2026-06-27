package com.swachhdrishti.swachh_drishti.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// WHY this class exists:
// After login or signup, we send back a JWT token + user info
// Flutter stores the token in SecureStorage and uses it for all future requests
// We never send the password back — only what the frontend needs
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String name;
    private String email;
    private Integer coins;
    private Integer totalReports;
}