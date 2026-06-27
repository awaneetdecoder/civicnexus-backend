package com.swachhdrishti.swachh_drishti.security;

import com.swachhdrishti.swachh_drishti.repository.UserRepository;
import com.swachhdrishti.swachh_drishti.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

// WHY OncePerRequestFilter:
// This filter runs exactly ONCE per HTTP request — before any controller
// It reads the JWT from the Authorization header, validates it,
// and tells Spring Security who the user is
// After this runs, @AuthenticationPrincipal in controllers works automatically
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Read the Authorization header
        // Flutter sends: "Authorization: Bearer eyJhbGci..."
        final String authHeader = request.getHeader("Authorization");

        // If no auth header or doesn't start with "Bearer ", skip this filter
        // The request will fail at the controller level if the endpoint is protected
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract the token (remove "Bearer " prefix — 7 characters)
        final String jwt = authHeader.substring(7);

        try {
            // Extract email from token
            final String email = jwtService.extractEmail(jwt);

            // Only proceed if we got an email AND no authentication is set yet
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Find the user in DB by email
                UserDetails userDetails = userRepository.findByEmail(email)
                        .orElse(null);

                if (userDetails != null && jwtService.isTokenValid(jwt,
                        (com.swachhdrishti.swachh_drishti.entity.User) userDetails)) {

                    // Create authentication token and set in SecurityContext
                    // WHY SecurityContext: Spring reads this in every controller
                    // to know who the current user is — this is what makes
                    // @AuthenticationPrincipal User currentUser work
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Invalid token — just continue without setting authentication
            // The controller will handle the unauthorized response
        }

        filterChain.doFilter(request, response);
    }
}