package org.jume.loyalitybot.controller;

import lombok.RequiredArgsConstructor;
import org.jume.loyalitybot.service.PosterApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final PosterApiService posterApiService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "loyaltybot");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> response = new HashMap<>();

        boolean posterHealthy = posterApiService.healthCheck();

        response.put("status", posterHealthy ? "UP" : "DEGRADED");
        response.put("service", "loyaltybot");

        Map<String, String> dependencies = new HashMap<>();
        dependencies.put("poster-api", posterHealthy ? "UP" : "DOWN");
        response.put("dependencies", dependencies);

        if (posterHealthy) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(503).body(response);
        }
    }
}
