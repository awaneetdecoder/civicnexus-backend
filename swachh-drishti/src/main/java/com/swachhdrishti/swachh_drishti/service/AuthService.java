package com.swachhdrishti.swachh_drishti.service;

import com.swachhdrishti.swachh_drishti.dto.AuthRequest;
import com.swachhdrishti.swachh_drishti.dto.AuthResponse;
import com.swachhdrishti.swachh_drishti.dto.SignupRequest;
import com.swachhdrishti.swachh_drishti.entity.User;
import com.swachhdrishti.swachh_drishti.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    // WHY this method:
    // Creates a new user, hashes their password (never store plain text),
    // saves to DB, generates JWT token, returns it to Flutter
    public AuthResponse signup(SignupRequest request) {
        // Check if email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        // Build User entity — BCrypt hashes the password
        // WHY BCrypt: one-way hash — even if DB is leaked, passwords are safe
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);

        // Generate JWT token for immediate login after signup
        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .name(user.getName())
                .email(user.getEmail())
                .coins(user.getCoins())
                .totalReports(user.getTotalReports())
                .build();
    }

    // WHY AuthenticationManager:
    // Spring Security's built-in class that handles credential verification
    // It calls UserDetailsService (our User entity) + PasswordEncoder automatically
    // We don't manually compare passwords — Spring does it securely
    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // If we reach here, credentials are valid — Spring didn't throw
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .name(user.getName())
                .email(user.getEmail())
                .coins(user.getCoins())
                .totalReports(user.getTotalReports())
                .build();
    }

    public AuthResponse getProfile(User user) {
        return AuthResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .coins(user.getCoins())
                .totalReports(user.getTotalReports())
                .build();
    }
}