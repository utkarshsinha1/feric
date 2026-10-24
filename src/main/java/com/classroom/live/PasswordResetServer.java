package com.classroom.live;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import java.util.function.Consumer;

public final class PasswordResetServer implements AutoCloseable {
    private static final int PORT = 43129;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ScheduledExecutorService EXPIRY =
        Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "feric-password-reset-expiry");
            thread.setDaemon(true);
            return thread;
        });
    private static PasswordResetServer active;

    private final SupabaseConfig config;
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService requests;
    private final Consumer<String> recoveryListener;
    private ScheduledFuture<?> expiry;

    private PasswordResetServer(
            SupabaseConfig config, int port, Consumer<String> recoveryListener)
            throws IOException {
        this.config = config;
        this.recoveryListener = recoveryListener;
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        server = HttpServer.create(new InetSocketAddress(loopback, port), 0);
        requests = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "feric-password-reset-page");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(requests);
        server.createContext("/reset", this::serveResetPage);
        server.createContext("/reset/callback", this::receiveRecoveryToken);
        server.start();
        refreshExpiry();
    }

    public static synchronized PasswordResetServer start(SupabaseConfig config)
            throws IOException {
        if (active != null && active.config.equals(config)) {
            active.refreshExpiry();
            return active;
        }
        if (active != null) {
            active.close();
        }
        try {
            active = new PasswordResetServer(config, PORT, token ->
                SwingUtilities.invokeLater(() -> PasswordResetDialog.open(config, token)));
        } catch (IOException exception) {
            throw new IOException(
                "Could not start Feric's local password reset page on "
                    + "127.0.0.1:" + PORT + ". Close another Feric instance and try again.",
                exception);
        }
        return active;
    }

    static PasswordResetServer forTest(SupabaseConfig config) throws IOException {
        return new PasswordResetServer(config, 0, token -> {});
    }

    static PasswordResetServer forTest(
            SupabaseConfig config, Consumer<String> recoveryListener) throws IOException {
        return new PasswordResetServer(config, 0, recoveryListener);
    }

    public URI redirectUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/reset");
    }

    private void serveResetPage(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())
                || !"/reset".equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        String body = page();
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        headers.set(
            "Content-Security-Policy",
            "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; "
                + "connect-src 'self'"
                + "; form-action 'none'; base-uri 'none'; frame-ancestors 'none'");
        headers.set("Cache-Control", "no-store");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("X-Content-Type-Options", "nosniff");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void receiveRecoveryToken(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())
                || !"/reset/callback".equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, "text/plain; charset=utf-8", "Not found");
            return;
        }
        if (!isSameOrigin(exchange.getRequestHeaders().getFirst("Origin"))) {
            respond(exchange, 403, "text/plain; charset=utf-8", "Forbidden");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(8193);
        if (body.length > 8192) {
            respond(exchange, 413, "text/plain; charset=utf-8", "Request too large");
            return;
        }

        JsonNode request;
        try {
            request = JSON.readTree(body);
        } catch (JsonProcessingException exception) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Invalid recovery request");
            return;
        }
        String token = request == null ? null : request.path("access_token").asText(null);
        String type = request == null ? null : request.path("type").asText(null);
        if (!"recovery".equals(type) || token == null || token.isBlank()
                || token.length() > 8192) {
            respond(exchange, 400, "text/plain; charset=utf-8", "Invalid recovery link");
            return;
        }

        respond(exchange, 200, "text/plain; charset=utf-8", "Return to Feric to continue.");
        recoveryListener.accept(token);
    }

    private boolean isSameOrigin(String originValue) {
        if (originValue == null) {
            return false;
        }
        try {
            URI origin = URI.create(originValue);
            URI expected = redirectUri();
            return expected.getScheme().equals(origin.getScheme())
                && expected.getHost().equals(origin.getHost())
                && expected.getPort() == origin.getPort()
                && (origin.getRawPath() == null || origin.getRawPath().isEmpty());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String page() {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Continue password reset in Feric</title>
              <style>
                body{font:16px system-ui,sans-serif;background:#1c1c1d;color:#d7d7d8;
                  margin:0;min-height:100vh;display:grid;place-items:center}
                main{box-sizing:border-box;width:min(420px,calc(100%% - 32px));padding:28px;
                  background:#262627;border:1px solid #343436;border-radius:10px}
                h1{font-size:22px;margin:0 0 12px}
                p{color:#aaa;margin:0 0 20px;line-height:1.5}
                label{display:block;margin:14px 0 6px}
                input{box-sizing:border-box;width:100%%;padding:11px;background:#1f1f20;
                  color:#fff;border:1px solid #555;border-radius:4px;font:inherit}
                button{margin-top:20px;padding:11px 16px;border:0;border-radius:4px;
                  background:#7ea3c1;color:#1c1c1d;font:inherit;font-weight:600;cursor:pointer}
                button:disabled{opacity:.6;cursor:wait}
                #status{margin-top:16px;overflow-wrap:anywhere}
              </style>
            </head>
            <body>
              <main>
                <h1>Password reset</h1>
                <p id="status" role="status">Checking your recovery link…</p>
              </main>
              <script>
                const status = document.getElementById('status');
                const tokenParams = new URLSearchParams(location.hash.slice(1));
                if (tokenParams.has('error')) {
                  status.textContent = tokenParams.get('error_description')
                    || 'This recovery link is invalid or expired. Request a new one in Feric.';
                } else if (tokenParams.get('type') !== 'recovery'
                    || !tokenParams.get('access_token')) {
                  status.textContent =
                    'This recovery link is invalid or expired. Request a new one in Feric.';
                } else {
                  const accessToken = tokenParams.get('access_token');
                  const recoveryType = tokenParams.get('type');
                  history.replaceState(null, '', location.pathname);
                  status.textContent = 'Opening the password reset form in Feric…';
                  fetch('/reset/callback', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({
                      access_token: accessToken,
                      type: recoveryType
                    })
                  }).then(response => {
                    if (!response.ok) {
                      throw new Error('Feric could not receive the recovery link.');
                    }
                    status.textContent =
                      'Return to Feric. The password reset form should now be open.';
                  }).catch(error => {
                    status.textContent = error.message
                      || 'Could not connect to Feric. Make sure the app is still open.';
                  });
                  }
              </script>
            </body>
            </html>
            """;
    }

    private static void respond(
            HttpExchange exchange, int status, String contentType, String text)
            throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void refreshExpiry() {
        if (expiry != null) {
            expiry.cancel(false);
        }
        expiry = EXPIRY.schedule(this::close, 30, TimeUnit.MINUTES);
    }

    @Override
    public void close() {
        synchronized (PasswordResetServer.class) {
            closeWhileLocked();
        }
    }

    private void closeWhileLocked() {
        server.stop(0);
        requests.shutdownNow();
        if (expiry != null) {
            expiry.cancel(false);
            expiry = null;
        }
        if (active == this) {
            active = null;
        }
    }
}
