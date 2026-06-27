package com.swachhdrishti.swachh_drishti.controller;

import com.swachhdrishti.swachh_drishti.dto.AuthRequest;
import com.swachhdrishti.swachh_drishti.dto.AuthResponse;
import com.swachhdrishti.swachh_drishti.dto.SignupRequest;
import com.swachhdrishti.swachh_drishti.entity.User;
import com.swachhdrishti.swachh_drishti.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// WHY @RestController:
// Combines @Controller + @ResponseBody
// Every method return value is automatically serialized to JSON
// Flutter receives JSON, not HTML

// WHY @RequestMapping("/api/auth"):
// All endpoints in this class start with /api/auth
// /signup becomes /api/auth/signup
// /login becomes /api/auth/login
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/signup
    // Flutter sends: {"name":"Awaneet","email":"a@b.com","password":"123456"}
    // @Valid triggers the @NotBlank and @Email checks in SignupRequest
    // If validation fails, Spring returns 400 Bad Request automatically
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(201).body(response);
    }

    // POST /api/auth/login
    // Flutter sends: {"email":"a@b.com","password":"123456"}
    // Returns JWT token if credentials are correct
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // GET /api/auth/me
    // Protected — needs JWT in header
    // @AuthenticationPrincipal gives us the logged-in user directly
    // JwtAuthFilter already validated the token and set the user in SecurityContext
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(authService.getProfile(currentUser));
    }
}