package com.cachelab.server;

import com.cachelab.core.policy.Policy;
import com.cachelab.server.dto.CompareRequest;
import com.cachelab.server.model.Pattern;
import com.cachelab.server.model.RunConfig;
import com.cachelab.server.model.Sample;
import com.cachelab.server.service.Run;
import com.cachelab.server.service.RunManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

public class EmbeddedHttpServer {

    private final int port;
    private final RunManager runManager;
    private HttpServer server;

    public EmbeddedHttpServer(int port, RunManager runManager) {
        this.port = port;
        this.runManager = runManager;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/api", new ApiHandler());

        server.start();
        System.out.println("=================================================");
        System.out.println("  CacheLab Embedded Server running on port " + port);
        System.out.println("  API Ready: http://localhost:" + port + "/api");
        System.out.println("=================================================");
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();

            // Set CORS headers
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            try {
                if (path.equals("/api/runs") && "POST".equals(method)) {
                    handleStartRun(exchange);
                } else if ((path.equals("/api/compare") || path.equals("/api/runs/compare")) && "POST".equals(method)) {
                    handleCompare(exchange);
                } else if (path.matches("^/api/runs/[^/]+/stream$") && "GET".equals(method)) {
                    handleStream(exchange, extractRunId(path, 3));
                } else if (path.matches("^/api/runs/[^/]+/stop$") && ("POST".equals(method) || "DELETE".equals(method))) {
                    handleStop(exchange, extractRunId(path, 3));
                } else if (path.matches("^/api/runs/[^/]+$") && "DELETE".equals(method)) {
                    handleStop(exchange, extractRunId(path, 3));
                } else if (path.matches("^/api/runs/[^/]+(/latest)?$") && "GET".equals(method)) {
                    handleGetSample(exchange, extractRunId(path, 3));
                } else {
                    sendJson(exchange, 404, "{\"error\":\"Route not found: " + path + "\"}");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private void handleStartRun(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        RunConfig config = parseRunConfig(body);
        String runId = runManager.startRun(config);
        sendJson(exchange, 201, "{\"runId\":\"" + runId + "\"}");
    }

    private void handleCompare(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        CompareRequest req = parseCompareRequest(body);
        Map<String, String> runIds = runManager.startCompare(req);
        sendJson(exchange, 201, "{\"runIds\":{\"LRU\":\"" + runIds.get("LRU") + "\",\"LFU\":\"" + runIds.get("LFU") + "\"}}");
    }

    private void handleStop(HttpExchange exchange, String runId) throws IOException {
        runManager.stopRun(runId);
        sendJson(exchange, 200, "{\"status\":\"STOPPED\"}");
    }

    private void handleGetSample(HttpExchange exchange, String runId) throws IOException {
        Run run = runManager.getRun(runId);
        if (run == null) {
            sendJson(exchange, 404, "{\"error\":\"Run not found: " + runId + "\"}");
            return;
        }
        Sample sample = run.getLatestSample();
        sendJson(exchange, 200, sample != null ? sample.toJson() : "{}");
    }

    private void handleStream(HttpExchange exchange, String runId) throws IOException {
        Run run = runManager.getRun(runId);
        if (run == null) {
            sendJson(exchange, 404, "{\"error\":\"Run not found: " + runId + "\"}");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0); // Chunked streaming

        OutputStream os = exchange.getResponseBody();
        SseEmitter emitter = new SseEmitter(0L);

        emitter.setHandler(builder -> {
            try {
                Object data = builder.getData();
                String payload = (data instanceof Sample s) ? s.toJson() : String.valueOf(data);
                String eventStr = "event: " + (builder.getName() != null ? builder.getName() : "sample") + "\n"
                        + "data: " + payload + "\n\n";
                os.write(eventStr.getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> {
            try {
                os.close();
            } catch (Exception ignored) {}
            exchange.close();
        });

        run.addEmitter(emitter);
    }

    private String extractRunId(String path, int segmentIndex) {
        String[] parts = path.split("/");
        return parts.length > segmentIndex ? parts[segmentIndex] : "";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static RunConfig parseRunConfig(String json) {
        Policy policy = Policy.valueOf(getJsonString(json, "policy", "LRU"));
        Pattern pattern = Pattern.valueOf(getJsonString(json, "pattern", "ZIPFIAN"));
        int capacity = getJsonInt(json, "capacity", 100);
        int keySpace = getJsonInt(json, "keySpace", 1000);
        double zipfSkew = getJsonDouble(json, "zipfSkew", 1.0);
        int totalOps = getJsonInt(json, "totalOps", 200_000);
        int threads = getJsonInt(json, "threads", 8);
        double readRatio = getJsonDouble(json, "readRatio", 0.9);
        long ttlMs = getJsonLong(json, "ttlMs", 0L);
        long loaderLatencyMs = getJsonLong(json, "loaderLatencyMs", 1L);
        long seed = getJsonLong(json, "seed", 42L);

        return new RunConfig(
                policy, capacity, keySpace, pattern, zipfSkew,
                totalOps, threads, readRatio, ttlMs, loaderLatencyMs, seed
        );
    }

    private static CompareRequest parseCompareRequest(String json) {
        CompareRequest req = new CompareRequest();
        req.setPattern(Pattern.valueOf(getJsonString(json, "pattern", "ZIPFIAN")));
        req.setCapacity(getJsonInt(json, "capacity", 100));
        req.setKeySpace(getJsonInt(json, "keySpace", 1000));
        req.setZipfSkew(getJsonDouble(json, "zipfSkew", 1.0));
        req.setTotalOps(getJsonInt(json, "totalOps", 200_000));
        req.setThreads(getJsonInt(json, "threads", 8));
        req.setReadRatio(getJsonDouble(json, "readRatio", 0.9));
        req.setTtlMs(getJsonLong(json, "ttlMs", 0L));
        req.setLoaderLatencyMs(getJsonLong(json, "loaderLatencyMs", 1L));
        req.setSeed(getJsonLong(json, "seed", 42L));
        return req;
    }

    private static String getJsonString(String json, String key, String defaultVal) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : defaultVal;
    }

    private static int getJsonInt(String json, String key, int defaultVal) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultVal;
    }

    private static long getJsonLong(String json, String key, long defaultVal) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Long.parseLong(m.group(1)) : defaultVal;
    }

    private static double getJsonDouble(String json, String key, double defaultVal) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(\\.\\d+)?)");
        Matcher m = p.matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : defaultVal;
    }
}
