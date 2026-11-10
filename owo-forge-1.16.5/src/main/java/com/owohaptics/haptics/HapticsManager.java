package com.owohaptics.haptics;

import com.owohaptics.HapticsConfig;
import com.owohaptics.OwoHaptics;
import com.owohaptics.owo.Muscle;
import com.owohaptics.owo.OwoClient;
import com.owohaptics.owo.Sensation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.UseAction;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns client-side player state changes into OWO sensations (Forge 1.16.5). */
public final class HapticsManager {
    private float lastHealth = -1;
    private boolean lastSwinging;
    private boolean wasDrawing;
    private int drawTicks;
    private int tick;

    private static final Map<String, Muscle[]> NONE = new HashMap<String, Muscle[]>();
    /** For /owo play: dynamic zones fall back to sensible defaults. */
    private static final Map<String, Muscle[]> NONE_ALL = new HashMap<String, Muscle[]>();
    static {
        NONE_ALL.put("source", Muscle.TORSO_FRONT);
        NONE_ALL.put("hand", new Muscle[]{Muscle.ARM_R});
        NONE_ALL.put("other_hand", new Muscle[]{Muscle.ARM_L});
        NONE_ALL.put("main_arm", new Muscle[]{Muscle.ARM_R});
        NONE_ALL.put("off_arm", new Muscle[]{Muscle.ARM_L});
    }

    private static OwoClient owo() {
        return OwoHaptics.client;
    }

    private static void playRaw(Sensation s, boolean force) {
        OwoClient c = owo();
        if (c == null || s == null || !HapticsConfig.ENABLED.get()) return;
        int pct = OwoHaptics.effects.settings.masterIntensity;
        c.play(pct == 100 ? s : s.scaled(pct), force);
    }

    private static void play(String id, double amount, Map<String, Muscle[]> dyn, boolean force) {
        playRaw(OwoHaptics.effects.build(id, amount, dyn), force);
    }

    private static Map<String, Muscle[]> dyn(String k, Muscle... v) {
        Map<String, Muscle[]> m = new HashMap<String, Muscle[]>();
        m.put(k, v);
        return m;
    }

    // ------------------------------------------------------------------ main tick

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.isPaused() || owo() == null) {
            lastHealth = -1;
            return;
        }
        tick++;
        if (!HapticsConfig.ENABLED.get() || !owo().isConnected()) {
            lastHealth = p.getHealth() + p.getAbsorptionAmount();
            return;
        }
        if (p.isDeadOrDying()) {
            lastHealth = -1;
            return;
        }

        if (OwoHaptics.effects.settings.damage) handleDamage(p);
        else lastHealth = p.getHealth() + p.getAbsorptionAmount();
        if (OwoHaptics.effects.settings.environment) handleEnvironment(p);
        if (OwoHaptics.effects.settings.heartbeat) handleHeartbeat(p);
        if (OwoHaptics.effects.settings.arms) handleArms(mc, p);
        if (OwoHaptics.effects.settings.eating) handleEating(p);
    }

    // ------------------------------------------------------------------ damage

    private void handleDamage(ClientPlayerEntity p) {
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (lastHealth < 0) {
            lastHealth = hp;
            return;
        }
        float delta = lastHealth - hp;
        lastHealth = hp;
        if (delta < 0.5f) return;

        DamageSource src = p.getLastDamageSource();
        String type = src == null ? "generic" : src.getMsgId();
        String id = OwoHaptics.effects.effectForDamage(type);
        play(id, delta, dyn("source", sideFromSource(p, src)), true);
    }

    /** Picks muscle zones facing the damage source, falling back to the nearest hostile mob, then front torso. */
    private Muscle[] sideFromSource(final ClientPlayerEntity p, DamageSource src) {
        Vector3d from = null;
        if (src != null) {
            from = src.getSourcePosition();
            if (from == null && src.getEntity() != null) from = src.getEntity().position();
        }
        if (from == null) {
            List<LivingEntity> list = p.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6),
                    e -> e != p && e instanceof IMob);
            LivingEntity near = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : list) {
                double d = e.distanceToSqr(p);
                if (d < best) {
                    best = d;
                    near = e;
                }
            }
            if (near != null) from = near.position();
        }
        if (from == null) return Muscle.TORSO_FRONT;

        double dx = from.x - p.getX();
        double dz = from.z - p.getZ();
        float srcYaw = (float) (MathHelper.atan2(-dx, dz) * (180.0 / Math.PI));
        float rel = MathHelper.wrapDegrees(srcYaw - p.yRot); // 0 = ahead, + = to the right
        float abs = Math.abs(rel);
        if (abs <= 45) return Muscle.TORSO_FRONT;
        if (abs >= 135) return Muscle.BACK;
        return rel > 0
                ? new Muscle[]{Muscle.PECTORAL_R, Muscle.ABDOMINAL_R, Muscle.ARM_R, Muscle.DORSAL_R}
                : new Muscle[]{Muscle.PECTORAL_L, Muscle.ABDOMINAL_L, Muscle.ARM_L, Muscle.DORSAL_L};
    }

    // ------------------------------------------------------------------ environment

    private void handleEnvironment(ClientPlayerEntity p) {
        if (p.isOnFire() && !p.fireImmune() && tick % 10 == 0)
            play("env.fire", 0, NONE, false);
        if (p.getAirSupply() <= 0 && !p.canBreatheUnderwater() && tick % 20 == 0)
            play("env.drowning", 0, NONE, false);
        // no powder-snow freezing in 1.16.5
    }

    private void handleHeartbeat(ClientPlayerEntity p) {
        if (p.getHealth() > 6f) return;
        int phase = tick % 20;
        if (phase == 0) play("heartbeat.lub", 0, NONE, false);
        else if (phase == 5) play("heartbeat.dub", 0, NONE, false);
    }

    // ------------------------------------------------------------------ arms (VR friendly)

    private static Map<String, Muscle[]> mainArm(boolean mainRight) {
        Map<String, Muscle[]> m = new HashMap<String, Muscle[]>();
        m.put("main_arm", new Muscle[]{arm(mainRight)});
        m.put("off_arm", new Muscle[]{arm(!mainRight)});
        return m;
    }

    private static Muscle arm(boolean right) {
        return right ? Muscle.ARM_R : Muscle.ARM_L;
    }

    private void handleArms(Minecraft mc, ClientPlayerEntity p) {
        boolean mainRight = mc.options.mainHand == HandSide.RIGHT;

        boolean swinging = p.swinging;
        if (swinging && !lastSwinging) {
            boolean right = p.swingingArm == Hand.MAIN_HAND ? mainRight : !mainRight;
            play("arm.swing", 0, dyn("hand", arm(right)), false);
        }
        lastSwinging = swinging;

        if (mc.gameMode != null && mc.gameMode.isDestroying() && tick % 4 == 0)
            play("arm.mine", 0, mainArm(mainRight), false);

        boolean drawing = p.isUsingItem() && isRanged(p.getUseItem());
        if (drawing) {
            drawTicks++;
            if (tick % 6 == 0) {
                boolean drawRight = p.getUsedItemHand() == Hand.MAIN_HAND ? mainRight : !mainRight;
                play("arm.draw", Math.min(drawTicks, 25), dyn("hand", arm(drawRight)), false);
            }
        } else if (wasDrawing && drawTicks > 4) {
            play("arm.release", 0, mainArm(mainRight), true);
            drawTicks = 0;
        } else {
            drawTicks = 0;
        }
        wasDrawing = drawing;
    }

    private static boolean isRanged(ItemStack s) {
        return s.getItem() == Items.BOW || s.getItem() == Items.CROSSBOW
                || s.getUseAnimation() == UseAction.BOW || s.getUseAnimation() == UseAction.CROSSBOW;
    }

    private void handleEating(ClientPlayerEntity p) {
        if (!p.isUsingItem() || tick % 6 != 0) return;
        UseAction anim = p.getUseItem().getUseAnimation();
        if (anim == UseAction.EAT || anim == UseAction.DRINK) play("eat", 0, NONE, false);
    }

    // ------------------------------------------------------------------ /owo command
    // 1.16.5 has no client-command API, so chat messages starting with "/owo" are intercepted.

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(new StringTextComponent("[OWO] " + msg), false);
    }

    @SubscribeEvent
    public void onChat(ClientChatEvent event) {
        String m = event.getMessage().trim();
        if (!m.equals("/owo") && !m.startsWith("/owo ")) return;
        event.setCanceled(true);
        Minecraft.getInstance().gui.getChat().addRecentChat(event.getOriginalMessage());
        String[] a = m.split("\\s+");
        String sub = a.length > 1 ? a[1] : "help";
        if (sub.equals("status")) {
            say(owo() != null && owo().isConnected() ? "OWO app connected."
                    : "OWO app NOT connected (open the OWO app / visualizer).");
        } else if (sub.equals("stop")) {
            if (owo() != null) owo().stopSensation();
        } else if (sub.equals("ui")) {
            say(OwoHaptics.webUrl() == null ? "Settings page is disabled or could not start." : "Settings page: " + OwoHaptics.webUrl());
        } else if (sub.equals("reload")) {
            OwoHaptics.reloadEffects();
            say("Effects reloaded.");
        } else if (sub.equals("play") && a.length > 2) {
            Sensation s = OwoHaptics.effects.build(a[2], 5, NONE_ALL);
            if (s == null) say("Unknown or disabled effect: " + a[2]);
            else playRaw(s, true);
        } else if (sub.equals("test") && a.length > 2) {
            Muscle[] z;
            String zone = a[2];
            if (zone.equals("front")) z = Muscle.FRONT;
            else if (zone.equals("back")) z = Muscle.BACK;
            else if (zone.equals("left")) z = Muscle.LEFT;
            else if (zone.equals("right")) z = Muscle.RIGHT;
            else if (zone.equals("heart")) z = new Muscle[]{Muscle.PECTORAL_L};
            else if (zone.equals("arms")) z = new Muscle[]{Muscle.ARM_R, Muscle.ARM_L};
            else z = Muscle.ALL;
            if (owo() == null || !owo().isConnected()) say("OWO app not connected.");
            else {
                playRaw(Sensation.of(60, 1.0, 70, z), true);
                say("Sent test to: " + zone);
            }
        } else {
            say("/owo status | ui | stop | reload | play <effect> | test <front|back|left|right|all|heart|arms>");
        }
    }
}
