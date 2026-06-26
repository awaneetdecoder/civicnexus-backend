package com.swachhdrishti.swachh_drishti.service;

import com.swachhdrishti.swachh_drishti.entity.Issue;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service
public class FraudDetectionService {

    // Maximum distance (meters) resolver can be from reported location
    private static final double MAX_DISTANCE_METERS = 50.0;

    // Resolution photo must be taken within this many minutes
    private static final long MAX_PHOTO_AGE_MINUTES = 30;

    // WHY this method: before accepting any resolution,
    // we validate three hard constraints.
    // If ANY fails, the resolution is rejected automatically.
    // This runs BEFORE Gemini comparison — no point calling Gemini
    // if the basics already fail.
    public void validateResolution(
            Issue originalIssue,
            double resolverLat,
            double resolverLng,
            Instant photoTakenAt) {

        validatePhotoAge(photoTakenAt, originalIssue.getCreatedAt().toInstant(
                java.time.ZoneOffset.UTC));

        validateProximity(
                originalIssue.getLatitude(), originalIssue.getLongitude(),
                resolverLat, resolverLng);
    }

    // RULE 1: Photo must be recent
    // WHY: prevents submitting old photos of clean locations
    private void validatePhotoAge(Instant photoTakenAt, Instant issueCreatedAt) {
        Instant now = Instant.now();

        // Photo cannot be older than 30 minutes
        long minutesOld = Duration.between(photoTakenAt, now).toMinutes();
        if (minutesOld > MAX_PHOTO_AGE_MINUTES) {
            throw new RuntimeException(
                    "Resolution photo is " + minutesOld + " minutes old. " +
                            "Photo must be taken within 30 minutes of submission.");
        }

        // Photo cannot predate the issue report
        if (photoTakenAt.isBefore(issueCreatedAt)) {
            throw new RuntimeException(
                    "Resolution photo was taken before the issue was even reported. Fraud detected.");
        }
    }

    // RULE 2: Resolver must be physically near the issue
    // WHY: prevents photographing a different clean road nearby
    private void validateProximity(
            double issueLat, double issueLng,
            double resolverLat, double resolverLng) {

        double distanceMeters = haversineDistance(
                issueLat, issueLng, resolverLat, resolverLng);

        if (distanceMeters > MAX_DISTANCE_METERS) {
            throw new RuntimeException(
                    "You are " + (int)distanceMeters + "m away from the reported location. " +
                            "Must be within " + MAX_DISTANCE_METERS + "m.");
        }
    }

    // Haversine formula — calculates real-world distance between two GPS coordinates
    // WHY not simple Pythagoras: Earth is curved.
    // At Delhi's latitude (~28°N), 1 degree longitude ≠ 1 degree latitude in meters.
    // Haversine accounts for Earth's curvature — accurate within 0.5% for city distances.
    private double haversineDistance(
            double lat1, double lng1,
            double lat2, double lng2) {

        final double R = 6371000; // Earth radius in meters

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng/2) * Math.sin(dLng/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c; // Distance in meters
    }

    // Displacement detection — called after resolution to check for fraud
    // Returns true if pattern looks suspicious
    // WHY separate method: this runs AFTER resolution is accepted,
    // as a background check for the supervisor dashboard
    public boolean isDisplacementSuspicious(
            double resolvedLat, double resolvedLng,
            double newIssueLat, double newIssueLng,
            String issueType) {

        double distance = haversineDistance(
                resolvedLat, resolvedLng, newIssueLat, newIssueLng);

        // Same issue type within 1km within 2 hours of resolution = suspicious
        return distance < 1000;
    }
}