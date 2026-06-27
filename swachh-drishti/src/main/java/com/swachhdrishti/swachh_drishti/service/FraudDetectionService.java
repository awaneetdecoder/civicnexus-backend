package com.swachhdrishti.swachh_drishti.service;

import com.swachhdrishti.swachh_drishti.entity.Issue;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

@Service
public class FraudDetectionService {

    private static final double MAX_DISTANCE_METERS = 50.0;
    private static final long MAX_PHOTO_AGE_MINUTES = 30;

    // Main validation method — called before accepting any resolution
    // Throws RuntimeException if any check fails
    // WHY throw instead of return boolean:
    // Caller (IssueService) can catch the specific message and return it to Flutter
    // Boolean would lose the reason for failure
    public void validateResolution(
            double issueLatitude,
            double issueLongitude,
            java.time.LocalDateTime issueCreatedAt,
            double resolverLat,
            double resolverLng,
            Instant photoTakenAt) {

        validatePhotoAge(photoTakenAt, issueCreatedAt.toInstant(ZoneOffset.UTC));
        validateProximity(issueLatitude, issueLongitude, resolverLat, resolverLng);
    }

    // RULE 1: Photo must be taken recently
    // Prevents: submitting old gallery photos of clean roads
    private void validatePhotoAge(Instant photoTakenAt, Instant issueCreatedAt) {
        Instant now = Instant.now();

        long minutesOld = Duration.between(photoTakenAt, now).toMinutes();
        if (minutesOld > MAX_PHOTO_AGE_MINUTES) {
            throw new RuntimeException(
                    "Resolution photo is " + minutesOld + " minutes old. " +
                            "Must be taken within 30 minutes.");
        }

        if (photoTakenAt.isBefore(issueCreatedAt)) {
            throw new RuntimeException(
                    "Resolution photo was taken before the issue was reported.");
        }
    }

    // RULE 2: Resolver must be physically at the location
    // Prevents: photographing a different clean road 500m away
    private void validateProximity(
            double issueLat, double issueLng,
            double resolverLat, double resolverLng) {

        double distance = haversineDistance(
                issueLat, issueLng, resolverLat, resolverLng);

        if (distance > MAX_DISTANCE_METERS) {
            throw new RuntimeException(
                    "You are " + (int) distance + "m away from the issue location. " +
                            "Must be within 50m.");
        }
    }

    // Haversine formula — real-world GPS distance calculation
    // WHY not simple subtraction:
    // Earth is curved. At Delhi's latitude, 1 degree of longitude
    // is about 97km, but 1 degree of latitude is 111km.
    // Haversine accounts for Earth's curvature — accurate to 0.5%
    public double haversineDistance(
            double lat1, double lng1,
            double lat2, double lng2) {

        final double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // Displacement detection — checks if garbage was just moved nearby
    // Called AFTER resolution is accepted, as background supervisor check
    public boolean isDisplacementSuspicious(
            double resolvedLat, double resolvedLng,
            double newIssueLat, double newIssueLng) {

        double distance = haversineDistance(
                resolvedLat, resolvedLng, newIssueLat, newIssueLng);

        // Same type of issue within 1km = suspicious pattern
        return distance < 1000;
    }
}