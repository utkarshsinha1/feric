package com.classroom.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PasswordResetServerTest {
    @Test
    void servesResetPageOnlyFromTheExpectedLocalRoute() throws Exception {
        SupabaseConfig config =
            new SupabaseConfig("https://project.supabase.co", "sb_publishable_test");
        try (PasswordResetServer server = PasswordResetServer.forTest(config)) {
            URI resetUri = server.redirectUri();
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(resetUri).GET().build(),
                HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Return to Feric"));
            assertTrue(response.body().contains("/reset/callback"));
            assertTrue(!response.body().contains("type=\"password\""));
            assertTrue(response.headers().firstValue("Cache-Control").orElse("")
                .contains("no-store"));
            assertTrue(response.headers().firstValue("Content-Security-Policy").orElse("")
                .contains("connect-src 'self'"));
        }
    }

    @Test
    void rejectsCrossOriginRecoveryCallbacks() throws Exception {
        SupabaseConfig config =
            new SupabaseConfig("https://project.supabase.co", "sb_publishable_test");
        try (PasswordResetServer server = PasswordResetServer.forTest(config)) {
            URI callbackUri = URI.create(
                server.redirectUri().toString().replace("/reset", "/reset/callback"));
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(callbackUri)
                    .header("Origin", "https://attacker.invalid")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"type\":\"recovery\",\"access_token\":\"test\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

            assertEquals(403, response.statusCode());
        }
    }

    @Test
    void handsAValidRecoveryTokenToTheNativeResetInterface() throws Exception {
        AtomicReference<String> receivedToken = new AtomicReference<>();
        SupabaseConfig config =
            new SupabaseConfig("https://project.supabase.co", "sb_publishable_test");
        try (PasswordResetServer server =
                PasswordResetServer.forTest(config, receivedToken::set)) {
            URI redirectUri = server.redirectUri();
            URI callbackUri = URI.create(redirectUri + "/callback");
            String origin = redirectUri.getScheme() + "://" + redirectUri.getAuthority();
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(callbackUri)
                    .header("Origin", origin)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"type\":\"recovery\",\"access_token\":\"valid-recovery-token\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("valid-recovery-token", receivedToken.get());
        }
    }
}
