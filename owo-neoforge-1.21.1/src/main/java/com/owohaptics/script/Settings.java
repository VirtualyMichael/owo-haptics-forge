package com.owohaptics.script;

/** Global behaviour switches. Live-editable from the web UI; persisted inside effects.json. */
public final class Settings {
    /** Global intensity percent, 0..150. */
    public volatile int masterIntensity = 100;
    public volatile boolean damage = true;
    public volatile boolean environment = true;
    public volatile boolean heartbeat = true;
    public volatile boolean arms = true;
    public volatile boolean eating = true;

    public void reset() {
        masterIntensity = 100;
        damage = environment = heartbeat = arms = eating = true;
    }
}
