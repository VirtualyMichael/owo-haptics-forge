package com.owohaptics.owo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Minimal OWO client: UDP/ASCII protocol to the OWO desktop app on port 54020.
 * Handshake: broadcast "ping" -> "okay" -> "{id}*AUTH*" -> "pong".
 * Sensations: "{id}*SENSATION*{wire}", stop: "{id}*STOP".
 * Note: the app stops answering pings while a sensation plays, so there is deliberately no keepalive.
 */
public final class OwoClient {
    private static final int PORT = 54020;

    private final String gameId;
    private final String extraHost;
    private final Consumer<String> log;
    private final Set<String> connected = ConcurrentHashMap.newKeySet();
    private volatile DatagramSocket socket;
    private volatile boolean running;
    private Thread thread;
    private long lastEndMs;

    public OwoClient(String gameId, String extraHost, Consumer<String> log) {
        this.gameId = gameId;
        this.extraHost = extraHost == null ? "" : extraHost.trim();
        this.log = log;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::loop, "OWO-Haptics-Network");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        for (String ip : connected) send(gameId + "*GAMEUNAVAILABLE", ip);
        connected.clear();
        DatagramSocket s = socket;
        if (s != null) s.close();
    }

    public boolean isConnected() {
        return !connected.isEmpty();
    }

    /** Plays a sensation unless it would cut off a longer one still playing (unless force). */
    public void play(Sensation s, boolean force) {
        if (!isConnected()) return;
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (!force && now < lastEndMs) return;
            lastEndMs = now + Math.round(s.duration * 1000);
        }
        String msg = gameId + "*SENSATION*" + s.toWire();
        for (String ip : connected) send(msg, ip);
    }

    public void stopSensation() {
        synchronized (this) {
            lastEndMs = 0;
        }
        for (String ip : connected) send(gameId + "*STOP", ip);
    }

    private void loop() {
        long lastPing = 0;
        while (running) {
            try {
                if (socket == null || socket.isClosed()) {
                    DatagramSocket s = new DatagramSocket();
                    s.setBroadcast(true);
                    s.setSoTimeout(250);
                    socket = s;
                }
                long now = System.currentTimeMillis();
                if (connected.isEmpty() && now - lastPing >= 500) {
                    lastPing = now;
                    send("ping", "255.255.255.255");
                    send("ping", "127.0.0.1");
                    if (!extraHost.isEmpty()) send("ping", extraHost);
                }
                byte[] buf = new byte[1024];
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(p);
                } catch (SocketTimeoutException e) {
                    continue;
                } catch (IOException e) {
                    // Windows raises "connection reset" after ICMP port-unreachable; harmless.
                    if (!running) return;
                    continue;
                }
                String text = new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII).trim();
                String from = p.getAddress().getHostAddress();
                if (text.equals("okay")) {
                    if (!connected.contains(from)) send(gameId + "*AUTH*", from);
                } else if (text.equals("pong")) {
                    if (connected.add(from)) log.accept("Connected to OWO app at " + from);
                } else if (text.equals("OWO_Close")) {
                    if (connected.remove(from)) log.accept("OWO app at " + from + " closed; searching again");
                }
            } catch (IOException e) {
                if (!running) return;
                log.accept("OWO network error: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    private void send(String msg, String host) {
        DatagramSocket s = socket;
        if (s == null || s.isClosed()) return;
        try {
            byte[] b = msg.getBytes(StandardCharsets.US_ASCII);
            s.send(new DatagramPacket(b, b.length, InetAddress.getByName(host), PORT));
        } catch (IOException ignored) {
            // transient; discovery retries
        }
    }
}
