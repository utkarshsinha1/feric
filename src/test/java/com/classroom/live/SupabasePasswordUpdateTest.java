package com.classroom.live;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupabasePasswordUpdateTest {
    @Test
    void updatesPasswordUsingTheRecoveryTokenWithSupabaseAuth() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer mockSupabase = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        mockSupabase.createContext("/auth/v1/user", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            apiKey.set(exchange.getRequestHeaders().getFirst("apikey"));
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
            client.updatePassword("recovery-access-token", "new-pass".toCharArray());

            assertEquals("PUT", requestMethod.get());
            assertEquals("/auth/v1/user", requestPath.get());
            assertEquals("Bearer recovery-access-token", authorization.get());
            assertEquals("test-publishable-key", apiKey.get());
            assertEquals("{\"password\":\"new-pass\"}", requestBody.get());
        } finally {
            mockSupabase.stop(0);
        }
    }
}
