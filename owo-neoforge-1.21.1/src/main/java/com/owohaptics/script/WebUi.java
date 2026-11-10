package com.owohaptics.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.owohaptics.owo.OwoClient;
import com.owohaptics.owo.Sensation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tiny dependency-free HTTP server for the settings page (no jdk.httpserver, which the Minecraft
 * launcher's bundled Java may not include). Binds to localhost unless LAN access is enabled.
 *
 * Writes require the header "X-OWO: 1" (browsers cannot send it cross-site without a CORS preflight,
 * which this server never approves) and, in localhost mode, a localhost Host header.
 */
public final class WebUi {
    private static final int MAX_BODY = 64 * 1024;

    private final Effects effects;
    private final OwoClient client;
    private final boolean lan;
    private final Consumer<String> log;
    private ServerSocket server;
    private volatile boolean running;
    private int port;
    private byte[] page;

    private WebUi(Effects effects, OwoClient client, boolean lan, Consumer<String> log) {
        this.effects = effects;
        this.client = client;
        this.lan = lan;
        this.log = log;
    }

    /** Starts the server, trying up to 10 ports from basePort. Returns null if none could be bound. */
    public static WebUi start(Effects effects, OwoClient client, int basePort, boolean lan, Consumer<String> log) {
        WebUi ui = new WebUi(effects, client, lan, log);
        for (int p = basePort; p < basePort + 10; p++) {
            try {
                InetAddress bind = lan ? null : InetAddress.getByName("127.0.0.1");
                ui.server = new ServerSocket(p, 20, bind);
                ui.port = p;
                break;
            } catch (IOException e) {
                // try next port
            }
        }
        if (ui.server == null) {
            log.accept("OWO web UI: could not bind any port near " + basePort);
            return null;
        }
        ui.page = ui.loadPage();
        ui.running = true;
        Thread t = new Thread(ui::acceptLoop, "OWO-Haptics-WebUI");
        t.setDaemon(true);
        t.start();
        log.accept("OWO settings UI: " + ui.url() + (lan ? " (reachable from your network)" : " (this PC only)"));
        return ui;
    }

    public String url() {
        return "http://localhost:" + port + "/";
    }

    public void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
            // closing
        }
    }

    private byte[] loadPage() {
        try (InputStream in = WebUi.class.getResourceAsStream("/owohaptics/ui.html")) {
            if (in != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toByteArray();
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "<h1>OWO Haptics</h1><p>ui.html missing from the jar.</p>".getBytes(StandardCharsets.UTF_8);
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket s = server.accept();
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        handle(s);
                    }
                }, "OWO-Haptics-WebUI-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!running) return;
            }
        }
    }

    // ------------------------------------------------------------------ HTTP handling

    private void handle(Socket s) {
        try {
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            String head = readHead(in);
            if (head == null) return;
            String[] lines = head.split("\r\n");
            String[] req = lines[0].split(" ");
            if (req.length < 2) return;
            String method = req[0];
            String path = req[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            Map<String, String> headers = new HashMap<String, String>();
            for (int i = 1; i < lines.length; i++) {
                int c = lines[i].indexOf(':');
                if (c > 0) headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
            }
            if (!lan && !hostOk(headers.get("host"))) {
                respond(s, 403, "text/plain", "Forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String body = "";
            if (method.equals("POST")) {
                if (!"1".equals(headers.get("x-owo"))) {
                    respond(s, 403, "text/plain", "Missing X-OWO header".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                int len = 0;
                try {
                    len = Integer.parseInt(headers.containsKey("content-length") ? headers.get("content-length") : "0");
                } catch (NumberFormatException ignored) {
                    // treated as empty
                }
                if (len < 0 || len > MAX_BODY) {
                    respond(s, 413, "text/plain", "Too large".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                byte[] b = new byte[len];
                int off = 0;
                while (off < len) {
                    int n = in.read(b, off, len - off);
                    if (n < 0) break;
                    off += n;
                }
                body = new String(b, 0, off, StandardCharsets.UTF_8);
            }
            route(s, method, path, body);
        } catch (IOException | RuntimeException e) {
            try {
                respond(s, 400, "text/plain", "Bad request".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // client gone
            }
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
                // done
            }
        }
    }

    private static boolean hostOk(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        int c = h.lastIndexOf(':');
        if (c > 0) h = h.substring(0, c);
        return h.equals("localhost") || h.equals("127.0.0.1");
    }

    private static String readHead(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int state = 0;
        int b;
        while ((b = in.read()) >= 0) {
            out.write(b);
            if (out.size() > 16 * 1024) return null;
            if ((state == 0 || state == 2) && b == '\r') state++;
            else if ((state == 1 || state == 3) && b == '\n') state++;
            else state = 0;
            if (state == 4) return new String(out.toByteArray(), StandardCharsets.ISO_8859_1).trim();
        }
        return null;
    }

    private void route(Socket s, String method, String path, String body) throws IOException {
        if (method.equals("GET") && (path.equals("/") || path.equals("/index.html"))) {
            respond(s, 200, "text/html; charset=utf-8", page);
        } else if (method.equals("GET") && path.equals("/api/state")) {
            JsonObject o = effects.snapshot();
            o.addProperty("connected", client != null && client.isConnected());
            json(s, 200, o);
        } else if (method.equals("POST") && path.startsWith("/api/effect/")) {
            String id = path.substring("/api/effect/".length());
            JsonObject o = new JsonParser().parse(body).getAsJsonObject();
            json(s, effects.updateEffect(id, o) ? 200 : 400, ok());
        } else if (method.equals("POST") && path.equals("/api/settings")) {
            effects.updateSettings(new JsonParser().parse(body).getAsJsonObject());
            json(s, 200, ok());
        } else if (method.equals("POST") && path.equals("/api/preview")) {
            JsonObject o = new JsonParser().parse(body).getAsJsonObject();
            double amount = o.has("amount") ? o.get("amount").getAsDouble() : 5;
            Sensation sen = effects.buildPreview(o, amount);
            boolean connected = client != null && client.isConnected();
            if (sen != null && connected) {
                int pct = effects.settings.masterIntensity;
                client.play(pct == 100 ? sen : sen.scaled(pct), true);
            }
            JsonObject r = ok();
            r.addProperty("connected", connected);
            r.addProperty("played", sen != null && connected);
            json(s, 200, r);
        } else if (method.equals("POST") && path.equals("/api/stop")) {
            if (client != null) client.stopSensation();
            json(s, 200, ok());
        } else if (method.equals("POST") && path.equals("/api/reset")) {
            effects.resetToDefaults();
            json(s, 200, ok());
        } else {
            respond(s, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private void json(Socket s, int code, JsonObject o) throws IOException {
        respond(s, code, "application/json; charset=utf-8", o.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void respond(Socket s, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 400 ? "Bad Request" : code == 403 ? "Forbidden"
                : code == 404 ? "Not Found" : code == 413 ? "Payload Too Large" : "Error";
        String head = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = s.getOutputStream();
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }
}
