import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class App {

    private static final String APP = "java-app";

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(env("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/healthz", exchange -> respond(exchange, "text/plain", "ok\n"));
        server.createContext("/", exchange -> {
            String body = """
                    {"app":"%s","version":"%s","greeting":"%s","pod":"%s"}
                    """.formatted(APP, env("APP_VERSION", "unset"), env("GREETING", "unset"), hostname());
            respond(exchange, "application/json", body);
        });

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.printf("%s listening on :%d%n", APP, port);

        // Same reason as the Node/Python apps: exit promptly on SIGTERM so rolling
        // updates aren't padded by terminationGracePeriodSeconds.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    private static void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            // Happens in containers whose hostname isn't resolvable via DNS.
            String fromEnv = System.getenv("HOSTNAME");
            return fromEnv == null ? "unknown" : fromEnv;
        }
    }
}
