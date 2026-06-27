package com.swachhdrishti.swachh_drishti.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// WHY this class exists:
// When Flutter sends POST /api/auth/login, it sends JSON like:
// {"email": "test@gmail.com", "password": "123456"}
// Spring maps that JSON into this object automatically via @RequestBody
// @NotBlank = Spring validates the field is not empty BEFORE controller runs
@Data
public class AuthRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}