package com.owohaptics.script;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.owohaptics.owo.Muscle;
import com.owohaptics.owo.Sensation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Data-driven effect table. Defaults are built in; a user JSON file (effects.json) can override any
 * field of any effect, add new effects, and remap damage types (including modded ones) to effects.
 *
 * Zone tokens: front, back, left, right, all, torso, arms, heart, a muscle name (e.g. ARM_R), or a
 * dynamic token supplied by the game code: source, hand, other_hand, main_arm, off_arm.
 */
public final class Effects {

    public static final class Def {
        public boolean enabled = true;
        public int frequency = 60;      // 1..100 Hz
        public double duration = 0.3;   // seconds
        public int intensity = 50;      // base percent
        public double perDamage = 0;    // extra percent per unit of the effect's "amount" (damage, ticks...)
        public double rampUp = 0;
        public double rampDown = 0;
        public List<String> zones = new ArrayList<String>(Arrays.asList("all"));

        Def() { }

        Def(int frequency, double duration, int intensity, double perDamage, String... zones) {
            this.frequency = frequency;
            this.duration = duration;
            this.intensity = intensity;
            this.perDamage = perDamage;
            this.zones = new ArrayList<String>(Arrays.asList(zones));
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Def> defs = new LinkedHashMap<String, Def>();
    private final Map<String, String> damageTypes = new LinkedHashMap<String, String>();
    public final Settings settings = new Settings();
    private Path file;

    /** Dynamic zone tokens used when an effect is previewed/tested outside of gameplay. */
    private static final Map<String, Muscle[]> PREVIEW_DYNAMIC = new HashMap<String, Muscle[]>();
    static {
        PREVIEW_DYNAMIC.put("source", Muscle.TORSO_FRONT);
        PREVIEW_DYNAMIC.put("hand", new Muscle[]{Muscle.ARM_R});
        PREVIEW_DYNAMIC.put("other_hand", new Muscle[]{Muscle.ARM_L});
        PREVIEW_DYNAMIC.put("main_arm", new Muscle[]{Muscle.ARM_R});
        PREVIEW_DYNAMIC.put("off_arm", new Muscle[]{Muscle.ARM_L});
    }

    public Effects() {
        loadDefaults();
    }

    private void loadDefaults() {
        defs.clear();
        damageTypes.clear();
        // damage
        defs.put("damage.fall", new Def(20, 0.5, 35, 8, "all"));
        defs.put("damage.explosion", new Def(100, 0.6, 70, 8, "all"));
        defs.put("damage.drown", new Def(30, 0.4, 35, 8, "torso"));
        defs.put("damage.fire", new Def(70, 0.3, 35, 8, "torso", "arms"));
        defs.put("damage.freeze", new Def(90, 0.3, 35, 8, "all"));
        defs.put("damage.magic", new Def(40, 0.5, 35, 8, "ABDOMINAL_R", "ABDOMINAL_L", "LUMBAR_R", "LUMBAR_L"));
        defs.put("damage.thorns", new Def(100, 0.2, 35, 8, "source"));
        Def proj = new Def(35, 0.2, 60, 8, "source");
        proj.rampDown = 0.1;
        defs.put("damage.projectile", proj);
        defs.put("damage.generic", new Def(60, 0.25, 35, 8, "source"));
        // environment
        defs.put("env.fire", new Def(60, 0.3, 35, 0, "torso", "arms"));
        defs.put("env.drowning", new Def(25, 0.5, 55, 0, "PECTORAL_R", "PECTORAL_L"));
        defs.put("env.freezing", new Def(90, 0.2, 20, 1, "all"));   // amount = frozen ticks / 10
        defs.put("heartbeat.lub", new Def(45, 0.2, 55, 0, "PECTORAL_L"));
        defs.put("heartbeat.dub", new Def(45, 0.2, 35, 0, "PECTORAL_L"));
        // arms / VR
        defs.put("arm.swing", new Def(70, 0.1, 40, 0, "hand"));
        defs.put("arm.mine", new Def(80, 0.2, 25, 0, "main_arm"));
        defs.put("arm.draw", new Def(30, 0.2, 20, 2, "ARM_R", "ARM_L"));  // amount = ticks drawn
        defs.put("arm.release", new Def(90, 0.25, 70, 0, "main_arm"));
        defs.put("eat", new Def(20, 0.1, 18, 0, "ABDOMINAL_R", "ABDOMINAL_L"));

        String[][] map = {
                {"fall", "damage.fall"}, {"flyIntoWall", "damage.fall"},
                {"explosion", "damage.explosion"}, {"explosion.player", "damage.explosion"},
                {"fireball", "damage.explosion"}, {"lightningBolt", "damage.explosion"},
                {"drown", "damage.drown"},
                {"onFire", "damage.fire"}, {"inFire", "damage.fire"}, {"lava", "damage.fire"},
                {"hotFloor", "damage.fire"}, {"onFire.player", "damage.fire"},
                {"freeze", "damage.freeze"},
                {"starve", "damage.magic"}, {"wither", "damage.magic"}, {"magic", "damage.magic"},
                {"indirectMagic", "damage.magic"},
                {"cactus", "damage.thorns"}, {"sweetBerryBush", "damage.thorns"}, {"thorns", "damage.thorns"},
                {"arrow", "damage.projectile"}, {"trident", "damage.projectile"},
                {"thrown", "damage.projectile"}, {"mob_projectile", "damage.projectile"},
        };
        for (String[] m : map) damageTypes.put(m[0], m[1]);
    }

    /** Effect id for a vanilla/modded damage message id; falls back to damage.generic. */
    public synchronized String effectForDamage(String msgId) {
        String id = damageTypes.get(msgId);
        return id != null ? id : "damage.generic";
    }

    public synchronized Def get(String id) {
        return defs.get(id);
    }

    public synchronized List<String> ids() {
        return new ArrayList<String>(defs.keySet());
    }

    /**
     * Builds a sensation, or null if the effect is unknown or disabled.
     *
     * @param amount  effect-specific magnitude (damage points, ticks, ...), multiplied by perDamage
     * @param dynamic muscles for dynamic zone tokens (source, hand, ...); may be empty
     */
    public synchronized Sensation build(String id, double amount, Map<String, Muscle[]> dynamic) {
        Def d = defs.get(id);
        if (d == null || !d.enabled) return null;
        List<Muscle> zones = resolve(d.zones, dynamic);
        if (zones.isEmpty()) return null;
        int intensity = (int) Math.round(d.intensity + d.perDamage * amount);
        return new Sensation(d.frequency, d.duration, intensity, d.rampUp, d.rampDown, 100,
                zones.toArray(new Muscle[0]));
    }

    private static List<Muscle> resolve(List<String> tokens, Map<String, Muscle[]> dynamic) {
        List<Muscle> out = new ArrayList<Muscle>();
        for (String raw : tokens) {
            String t = raw.trim().toLowerCase(Locale.ROOT);
            Muscle[] ms = dynamic == null ? null : dynamic.get(t);
            if (ms == null) ms = group(t);
            if (ms == null) {
                for (Muscle m : Muscle.values()) {
                    if (m.name().equalsIgnoreCase(t)) ms = new Muscle[]{m};
                }
            }
            if (ms == null) continue;
            for (Muscle m : ms) if (!out.contains(m)) out.add(m);
        }
        return out;
    }

    private static Muscle[] group(String t) {
        switch (t) {
            case "front": return Muscle.FRONT;
            case "back": return Muscle.BACK;
            case "left": return Muscle.LEFT;
            case "right": return Muscle.RIGHT;
            case "all": return Muscle.ALL;
            case "torso": return Muscle.TORSO_FRONT;
            case "arms": return new Muscle[]{Muscle.ARM_R, Muscle.ARM_L};
            case "heart": return new Muscle[]{Muscle.PECTORAL_L};
            default: return null;
        }
    }

    // ------------------------------------------------------------------ file handling

    /** Loads (and, if missing, writes out) the user file. Bad files are reported and defaults are kept. */
    public synchronized void load(Path file, Consumer<String> log) {
        this.file = file;
        loadDefaults();
        settings.reset();
        try {
            if (!Files.exists(file)) {
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                Files.write(file, toJson().getBytes(StandardCharsets.UTF_8));
                log.accept("Wrote default effects file: " + file);
                return;
            }
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(text).getAsJsonObject();
            if (root.has("effects")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("effects").entrySet()) {
                    Def d = defs.get(e.getKey());
                    if (d == null) {
                        d = new Def();
                        defs.put(e.getKey(), d);
                    }
                    merge(d, e.getValue().getAsJsonObject());
                }
            }
            if (root.has("settings")) mergeSettings(root.getAsJsonObject("settings"));
            if (root.has("damageTypes")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("damageTypes").entrySet()) {
                    damageTypes.put(e.getKey(), e.getValue().getAsString());
                }
            }
            log.accept("Loaded effects from " + file);
        } catch (IOException | RuntimeException ex) {
            loadDefaults();
            settings.reset();
            log.accept("Could not read " + file + " (" + ex + "); using built-in defaults");
        }
    }

    private static void merge(Def d, JsonObject o) {
        if (o.has("enabled")) d.enabled = o.get("enabled").getAsBoolean();
        if (o.has("frequency")) d.frequency = o.get("frequency").getAsInt();
        if (o.has("duration")) d.duration = o.get("duration").getAsDouble();
        if (o.has("intensity")) d.intensity = o.get("intensity").getAsInt();
        if (o.has("perDamage")) d.perDamage = o.get("perDamage").getAsDouble();
        if (o.has("rampUp")) d.rampUp = o.get("rampUp").getAsDouble();
        if (o.has("rampDown")) d.rampDown = o.get("rampDown").getAsDouble();
        if (o.has("zones")) {
            List<String> z = new ArrayList<String>();
            JsonElement el = o.get("zones");
            if (el.isJsonArray()) {
                for (JsonElement x : el.getAsJsonArray()) z.add(x.getAsString());
            } else {
                z.add(el.getAsString());
            }
            d.zones = z;
        }
    }

    private String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("_help", "Edit values, then run /owo reload. zones: front back left right all torso arms heart, "
                + "a muscle name like ARM_R, or source/hand/other_hand/main_arm/off_arm. "
                + "Final intensity = intensity + perDamage * amount. Add your own effect ids, and map "
                + "modded damage types under damageTypes (type message id -> effect id).");
        root.add("settings", GSON.toJsonTree(settings));
        root.add("effects", GSON.toJsonTree(defs));
        root.add("damageTypes", GSON.toJsonTree(damageTypes));
        return GSON.toJson(root);
    }

    // ------------------------------------------------------------------ live editing (web UI)

    private void mergeSettings(JsonObject o) {
        if (o.has("masterIntensity")) settings.masterIntensity = Math.max(0, Math.min(150, o.get("masterIntensity").getAsInt()));
        if (o.has("damage")) settings.damage = o.get("damage").getAsBoolean();
        if (o.has("environment")) settings.environment = o.get("environment").getAsBoolean();
        if (o.has("heartbeat")) settings.heartbeat = o.get("heartbeat").getAsBoolean();
        if (o.has("arms")) settings.arms = o.get("arms").getAsBoolean();
        if (o.has("eating")) settings.eating = o.get("eating").getAsBoolean();
    }

    /** Settings, effects and damage-type map as JSON for the UI. */
    public synchronized JsonObject snapshot() {
        JsonObject root = new JsonObject();
        root.add("settings", GSON.toJsonTree(settings));
        root.add("effects", GSON.toJsonTree(defs));
        root.add("damageTypes", GSON.toJsonTree(damageTypes));
        return root;
    }

    public synchronized void updateSettings(JsonObject o) {
        mergeSettings(o);
        save();
    }

    /** Merges the given fields into an effect (creating it if new) and saves. */
    public synchronized boolean updateEffect(String id, JsonObject o) {
        if (!id.matches("[A-Za-z0-9._-]{1,48}")) return false;
        Def d = defs.get(id);
        if (d == null) {
            d = new Def();
            defs.put(id, d);
        }
        merge(d, o);
        save();
        return true;
    }

    public synchronized void resetToDefaults() {
        loadDefaults();
        settings.reset();
        save();
    }

    /** Builds a sensation from unsaved UI values for the Test button. Null if no valid zone. */
    public synchronized Sensation buildPreview(JsonObject def, double amount) {
        Def d = new Def();
        merge(d, def);
        List<Muscle> zones = resolve(d.zones, PREVIEW_DYNAMIC);
        if (zones.isEmpty()) return null;
        int intensity = (int) Math.round(d.intensity + d.perDamage * amount);
        return new Sensation(d.frequency, d.duration, intensity, d.rampUp, d.rampDown, 100,
                zones.toArray(new Muscle[0]));
    }

    public synchronized void save() {
        if (file == null) return;
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.write(file, toJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // surfaced next time the user reloads; UI keeps working in memory
        }
    }
}
