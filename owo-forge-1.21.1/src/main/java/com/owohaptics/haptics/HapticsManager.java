package com.owohaptics.haptics;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.owohaptics.HapticsConfig;
import com.owohaptics.OwoHaptics;
import com.owohaptics.owo.Muscle;
import com.owohaptics.owo.OwoClient;
import com.owohaptics.owo.Sensation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Turns client-side player state changes into OWO sensations. */
public final class HapticsManager {
    private float lastHealth = -1;
    private boolean lastSwinging;
    private boolean wasDrawing;
    private int drawTicks;
    private int tick;

    private static OwoClient owo() {
        return OwoHaptics.client;
    }

    private static void playRaw(Sensation s, boolean force) {
        OwoClient c = owo();
        if (c == null || s == null || !HapticsConfig.ENABLED.get()) return;
        int pct = OwoHaptics.effects.settings.masterIntensity;
        c.play(pct == 100 ? s : s.scaled(pct), force);
    }

    private static final Map<String, Muscle[]> NONE = new HashMap<>();
    /** For /owo play: dynamic zones fall back to sensible defaults. */
    private static final Map<String, Muscle[]> NONE_ALL = new HashMap<>();
    static {
        Muscle[] front = Muscle.TORSO_FRONT;
        NONE_ALL.put("source", front);
        NONE_ALL.put("hand", new Muscle[]{Muscle.ARM_R});
        NONE_ALL.put("other_hand", new Muscle[]{Muscle.ARM_L});
        NONE_ALL.put("main_arm", new Muscle[]{Muscle.ARM_R});
        NONE_ALL.put("off_arm", new Muscle[]{Muscle.ARM_L});
    }

    private static void play(String id, double amount, Map<String, Muscle[]> dyn, boolean force) {
        playRaw(OwoHaptics.effects.build(id, amount, dyn), force);
    }

    private static Map<String, Muscle[]> dyn(String k, Muscle... v) {
        Map<String, Muscle[]> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    // ------------------------------------------------------------------ main tick

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
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

    private void handleDamage(LocalPlayer p) {
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (lastHealth < 0) {
            lastHealth = hp;
            return;
        }
        float delta = lastHealth - hp;
        lastHealth = hp;
        if (delta < 0.5f) return;

        DamageSource src = p.getLastDamageSource();
        String type = src == null ? "generic" : src.type().msgId();
        String id = OwoHaptics.effects.effectForDamage(type);
        play(id, delta, dyn("source", sideFromSource(p, src)), true);
    }

    /** Picks muscle zones facing the damage source, falling back to the nearest hostile mob, then front torso. */
    private Muscle[] sideFromSource(LocalPlayer p, DamageSource src) {
        Vec3 from = null;
        if (src != null) {
            from = src.getSourcePosition();
            if (from == null && src.getEntity() != null) from = src.getEntity().position();
        }
        if (from == null) {
            LivingEntity near = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6),
                            e -> e != p && e instanceof Enemy)
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
            if (near != null) from = near.position();
        }
        if (from == null) return Muscle.TORSO_FRONT;

        double dx = from.x - p.getX();
        double dz = from.z - p.getZ();
        float srcYaw = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
        float rel = Mth.wrapDegrees(srcYaw - p.getYRot()); // 0 = ahead, + = to the right
        float abs = Math.abs(rel);
        if (abs <= 45) return Muscle.TORSO_FRONT;
        if (abs >= 135) return Muscle.BACK;
        return rel > 0
                ? new Muscle[]{Muscle.PECTORAL_R, Muscle.ABDOMINAL_R, Muscle.ARM_R, Muscle.DORSAL_R}
                : new Muscle[]{Muscle.PECTORAL_L, Muscle.ABDOMINAL_L, Muscle.ARM_L, Muscle.DORSAL_L};
    }

    // ------------------------------------------------------------------ environment

    private void handleEnvironment(LocalPlayer p) {
        if (p.isOnFire() && !p.fireImmune() && tick % 10 == 0)
            play("env.fire", 0, NONE, false);
        if (p.getAirSupply() <= 0 && !p.canBreatheUnderwater() && tick % 20 == 0)
            play("env.drowning", 0, NONE, false);
        if (p.getTicksFrozen() > 100 && tick % 15 == 0)
            play("env.freezing", p.getTicksFrozen() / 10.0, NONE, false);
    }

    private void handleHeartbeat(LocalPlayer p) {
        if (p.getHealth() > 6f) {
            return;
        }
        // lub-dub: one beat at the start of each second, a softer one 5 ticks later
        int phase = tick % 20;
        if (phase == 0) play("heartbeat.lub", 0, NONE, false);
        else if (phase == 5) play("heartbeat.dub", 0, NONE, false);
    }

    // ------------------------------------------------------------------ arms (VR friendly)

    private static Map<String, Muscle[]> mainArm(boolean mainRight) {
        Map<String, Muscle[]> m = new HashMap<>();
        m.put("main_arm", new Muscle[]{arm(mainRight)});
        m.put("off_arm", new Muscle[]{arm(!mainRight)});
        return m;
    }

    private static Muscle arm(boolean right) {
        return right ? Muscle.ARM_R : Muscle.ARM_L;
    }

    private void handleArms(Minecraft mc, LocalPlayer p) {
        boolean mainRight = mc.options.mainHand().get() == HumanoidArm.RIGHT;

        // swing start
        boolean swinging = p.swinging;
        if (swinging && !lastSwinging) {
            boolean right = p.swingingArm == InteractionHand.MAIN_HAND ? mainRight : !mainRight;
            play("arm.swing", 0, dyn("hand", arm(right)), false);
        }
        lastSwinging = swinging;

        // mining vibration
        if (mc.gameMode != null && mc.gameMode.isDestroying() && tick % 4 == 0)
            play("arm.mine", 0, mainArm(mainRight), false);

        // bow / crossbow draw and release
        boolean drawing = p.isUsingItem() && isRanged(p.getUseItem());
        if (drawing) {
            drawTicks++;
            if (tick % 6 == 0) {
                boolean drawRight = p.getUsedItemHand() == InteractionHand.MAIN_HAND ? mainRight : !mainRight;
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
        return s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.getUseAnimation() == UseAnim.BOW
                || s.getUseAnimation() == UseAnim.CROSSBOW;
    }

    private void handleEating(LocalPlayer p) {
        if (!p.isUsingItem() || tick % 6 != 0) return;
        UseAnim anim = p.getUseItem().getUseAnimation();
        if (anim == UseAnim.EAT || anim == UseAnim.DRINK)
            play("eat", 0, NONE, false);
    }

    // ------------------------------------------------------------------ /owo command

    @SubscribeEvent
    public void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("owo")
                .then(Commands.literal("status").executes(c -> {
                    boolean ok = owo() != null && owo().isConnected();
                    c.getSource().sendSuccess(() -> Component.literal(
                            ok ? "OWO app connected." : "OWO app NOT connected (open the OWO app / visualizer)."), false);
                    return 1;
                }))
                .then(Commands.literal("ui").executes(c -> {
                    String u = OwoHaptics.webUrl();
                    c.getSource().sendSuccess(() -> Component.literal(u == null ? "Settings page is disabled or could not start." : "Settings page: " + u), false);
                    return 1;
                }))
                .then(Commands.literal("reload").executes(c -> {
                    OwoHaptics.reloadEffects();
                    c.getSource().sendSuccess(() -> Component.literal("OWO effects reloaded."), false);
                    return 1;
                }))
                .then(Commands.literal("play").then(Commands.argument("effect", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            for (String id : OwoHaptics.effects.ids()) b.suggest(id);
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            String id = StringArgumentType.getString(c, "effect");
                            Sensation s = OwoHaptics.effects.build(id, 5, NONE_ALL);
                            if (s == null) {
                                c.getSource().sendFailure(Component.literal("Unknown or disabled effect: " + id));
                                return 0;
                            }
                            playRaw(s, true);
                            return 1;
                        })))
                .then(Commands.literal("stop").executes(c -> {
                    if (owo() != null) owo().stopSensation();
                    return 1;
                }))
                .then(Commands.literal("test")
                        .then(Commands.argument("zone", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (String z : new String[]{"front", "back", "left", "right", "all", "heart", "arms"})
                                        b.suggest(z);
                                    return b.buildFuture();
                                })
                                .executes(c -> {
                                    String zone = StringArgumentType.getString(c, "zone");
                                    Muscle[] m = switch (zone) {
                                        case "front" -> Muscle.FRONT;
                                        case "back" -> Muscle.BACK;
                                        case "left" -> Muscle.LEFT;
                                        case "right" -> Muscle.RIGHT;
                                        case "heart" -> new Muscle[]{Muscle.PECTORAL_L};
                                        case "arms" -> new Muscle[]{Muscle.ARM_R, Muscle.ARM_L};
                                        default -> Muscle.ALL;
                                    };
                                    if (owo() == null || !owo().isConnected()) {
                                        c.getSource().sendFailure(Component.literal("OWO app not connected."));
                                        return 0;
                                    }
                                    playRaw(Sensation.of(60, 1.0, 70, m), true);
                                    c.getSource().sendSuccess(() -> Component.literal("Sent test to: " + zone), false);
                                    return 1;
                                }))));
    }
}
