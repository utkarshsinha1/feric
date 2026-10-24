package com.classroom.live;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupabasePasswordResetRequestTest {
    @Test
    void sendsTheDesktopRedirectWithTheRecoveryRequest() throws Exception {
        AtomicReference<String> requestUri = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer mockSupabase = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        mockSupabase.createContext("/auth/v1/recover", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            requestBody.set(new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        mockSupabase.start();
        try {
            int port = mockSupabase.getAddress().getPort();
            SupabaseClient client = new SupabaseClient(
                new SupabaseConfig("http://127.0.0.1:" + port, "test-publishable-key"));
            URI redirect = URI.create("http://127.0.0.1:43129/reset");
            client.requestPasswordReset("person@example.com", redirect);

            assertEquals(
                "/auth/v1/recover?redirect_to=http%3A%2F%2F127.0.0.1%3A43129%2Freset",
                requestUri.get());
            assertEquals("{\"email\":\"person@example.com\"}", requestBody.get());
        } finally {
            mockSupabase.stop(0);
        }
    }
}
