package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.BlockUtil;
import com.anticheat.core.util.MessageService;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.*;

public class MovementCheck implements Listener {

    private final AntiCheatPlugin plugin;

    private final Set<Material> ignoreSurfaces = new HashSet<>();
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Map<UUID, Location> lastMoveLoc = new HashMap<>();
    private final Map<UUID, Integer> jesusTicks = new HashMap<>();
    private final Map<UUID, Integer> bhopTicks = new HashMap<>();
    private final Map<UUID, Integer> spiderTicks = new HashMap<>();
    private final Map<UUID, Double> fallDistanceAcc = new HashMap<>();
    private final Map<UUID, Long> lastTimerWindowStart = new HashMap<>();
    private final Map<UUID, Integer> moveEventsInWindow = new HashMap<>();
    private final Map<UUID, Integer> noSlowTicks = new HashMap<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private final Map<UUID, Long> lastBhopJumpMs = new HashMap<>();
    private final Map<UUID, Long> lastGroundedMs = new HashMap<>();
    private final Map<UUID, Long> lastFallDamageMs = new HashMap<>();
    private final Map<UUID, Long> noFallDeferUntilMs = new HashMap<>();
    private final Map<UUID, Long> noFallDeferStartMs = new HashMap<>();
    private final Map<UUID, Long> lastGlideMs = new HashMap<>();
    private final Map<UUID, Long> lastLiquidMs = new HashMap<>();
    private final Map<UUID, Long> lastFreezeNotifyMs = new HashMap<>();

    public MovementCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        loadIgnoreSurfaces();
    }

    private boolean enabled(String path) {
        return plugin.getConfig().getBoolean(path, true);
    }

    private void loadIgnoreSurfaces() {
        List<String> list = plugin.getConfig().getStringList("checks.movement.speed.ignore_surfaces");
        for (String s : list) {
            try {
                ignoreSurfaces.add(Material.valueOf(s));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().toVector().equals(event.getTo().toVector())) return; // no movimiento

        Player player = event.getPlayer();

        // Bypass: OPs o permisos configurados
        if (plugin.isExempt(player)) return;

        // Congelamiento por Freecam: si está activo, cancelar movimiento
        long now = System.currentTimeMillis();
        long until = plugin.getFreecamFreezeUntil(player.getUniqueId());
        if (until > now) {
            event.setTo(event.getFrom());
            if (plugin.getConfig().getBoolean("checks.place.freecam.freeze_notify", true)) {
                long last = lastFreezeNotifyMs.getOrDefault(player.getUniqueId(), 0L);
                if (now - last >= 1000) {
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    String msg = plugin.getConfig().getString("checks.place.freecam.freeze_message", "&cMovimiento congelado por Freecam.");
                    player.sendMessage(prefix + org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
                    lastFreezeNotifyMs.put(player.getUniqueId(), now);
                }
            }
            return;
        } else if (until != 0L) {
            // Clear state when freeze expires
            plugin.clearFreecamFreeze(player.getUniqueId());
        }

        // Record gliding moment to exempt Elytra landings in NoFall
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("isGliding");
            Object r = m.invoke(player);
            if (r instanceof Boolean && ((Boolean) r).booleanValue()) {
                lastGlideMs.put(player.getUniqueId(), System.currentTimeMillis());
            }
        } catch (Exception ignored) {}

        // Speed check (Speed)
        checkSpeed(player, event);

        // Fly check (illegal flight)
        checkFly(player, event);

        // BHop (bunny hop) and OnGround Speed
        checkBHopAndOnGroundSpeed(player, event);

        // Unnatural step (Step)
        checkStep(player, event);

        // Walk on water (Jesus/WaterWalk)
        checkJesus(player, event);

        // Jetpack (sustained vertical ascent without permission)
        checkJetpack(player, event);

        // Spider/Climb (wall climbing)
        checkSpider(player, event);

        // Blink (packet-based teleport)
        checkBlink(player, event);

        // Heuristic NoFall (long fall without damage)
        trackNoFallHeuristic(player, event);

        // Timer (excessive movement events)
        checkTimer(player);

        checkNoSlow(player, event);
    }

    private void checkSpeed(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.speed.enabled")) return;
        // Skip Speed when the player is gliding with Elytra
        boolean isGliding = false;
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("isGliding");
            Object r = m.invoke(player);
            if (r instanceof Boolean) isGliding = ((Boolean) r).booleanValue();
        } catch (Exception ignored) {}
        if (isGliding) return;

        // Temporarily exempt Speed right after finishing Elytra gliding
        long now = System.currentTimeMillis();
        long lastGlide = lastGlideMs.getOrDefault(player.getUniqueId(), 0L);
        long postGlideExemptMs = plugin.getConfig().getLong("checks.movement.elytra_post_landing_jump_exempt_ms", 1000L);
        if (lastGlide != 0L && now - lastGlide <= postGlideExemptMs) {
            return;
        }

        double dy = event.getTo().getY() - event.getFrom().getY();
        boolean onGround = player.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid();
        if (!onGround || dy > 0) return;

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // Umbrales desde config
        double baseWalk = plugin.getConfig().getDouble("checks.movement.speed.max_base_walk", 0.25);
        double baseSprint = plugin.getConfig().getDouble("checks.movement.speed.max_base_sprint", 0.35);
        double perSpeedBonus = plugin.getConfig().getDouble("checks.movement.speed.per_speed_level_bonus", 0.10);
        double marginMultiplier = plugin.getConfig().getDouble("checks.movement.speed.margin_multiplier", 1.25);
        double jitterBuffer = plugin.getConfig().getDouble("checks.movement.speed.jitter_buffer", 0.04);

        boolean isSprinting = player.isSprinting();
        double allowed = isSprinting ? baseSprint : baseWalk;

        int speedLevel = 0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType().equals(PotionEffectType.SPEED)) {
                speedLevel = Math.max(speedLevel, effect.getAmplifier() + 1);
            }
        }
        allowed += speedLevel * perSpeedBonus;

        Material below = player.getLocation().clone().add(0, -0.5, 0).getBlock().getType();
        boolean slippery = ignoreSurfaces.contains(below);

        // Avoid false positives from vehicles or damage
        if (player.isInsideVehicle()) return;

        double limit = allowed * marginMultiplier + jitterBuffer;
        if (!slippery && horizontal > limit) {
            int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "Speed", 1);
            if (vl % 2 == 1) { // no-spam messages
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                String key = isSprinting ? "player.speed_sprint" : "player.speed_walk";
                player.sendMessage(ms.format(key, ph));
            }
            // Immediate speed block: setback to previous location
            event.setTo(event.getFrom());
        }
    }

    private void checkFly(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.fly.enabled")) return;
        // Ignore if the player can fly (creative mode) or is inside a vehicle
        if (player.getAllowFlight() || player.isInsideVehicle()) {
            airTicks.put(player.getUniqueId(), 0);
            return;
        }

        // Ignore Elytra gliding if available in this version
        boolean isGliding = false;
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("isGliding");
            Object r = m.invoke(player);
            if (r instanceof Boolean) isGliding = ((Boolean) r).booleanValue();
        } catch (Exception ignored) {}
        if (isGliding) {
            airTicks.put(player.getUniqueId(), 0);
            return;
        }

        double dy = event.getTo().getY() - event.getFrom().getY();
        double hoverEpsilon = plugin.getConfig().getDouble("checks.movement.fly.hover_epsilon", 0.015);

        boolean onGround = player.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid();
        boolean inLiquid = BlockUtil.isInLiquid(player.getLocation());
        boolean hasSolidBelow = BlockUtil.hasSolidBelow(player.getLocation(), 2);

        int ticks = airTicks.getOrDefault(player.getUniqueId(), 0);
        // If falling (significant negative dy), don't count as flight and reset tolerance
        if (dy < -hoverEpsilon) {
            airTicks.put(player.getUniqueId(), 0);
            return;
        }

        if (!onGround && !inLiquid && !hasSolidBelow) {
            ticks++;
            airTicks.put(player.getUniqueId(), ticks);
            int maxAirTicks = plugin.getConfig().getInt("checks.movement.fly.max_air_ticks", 12);
            if (ticks > maxAirTicks) {
                int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "Fly", 1);
                if (vl % 2 == 1) {
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.fly", ph));
                }
                // Block illegal flight with setback
                event.setTo(event.getFrom());
            }
        } else {
            airTicks.put(player.getUniqueId(), 0);
        }
    }

    private void checkBHopAndOnGroundSpeed(Player player, PlayerMoveEvent event) {
        boolean allowOnGround = enabled("checks.movement.ongroundspeed.enabled");
        boolean allowBhop = enabled("checks.movement.bhop.enabled");
        double dy = event.getTo().getY() - event.getFrom().getY();
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        boolean solidBelow = player.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid();
        Material below = player.getLocation().clone().add(0, -0.5, 0).getBlock().getType();
        boolean slippery = ignoreSurfaces.contains(below);
        UUID id = player.getUniqueId();
        double absDy = Math.abs(dy);
        boolean isActualGround = solidBelow && absDy < 0.031; // on ground if minimal vertical movement
        boolean wasGround = wasOnGround.getOrDefault(id, true);
        long now = System.currentTimeMillis();
        // Post-glide Elytra exemption to avoid Speed/BHop false positives on landing and jumping
        long lastGlide = lastGlideMs.getOrDefault(id, 0L);
        long jumpExemptMs = plugin.getConfig().getLong("checks.movement.elytra_post_landing_jump_exempt_ms", 800L);
        if (lastGlide != 0L && now - lastGlide <= jumpExemptMs) {
            wasOnGround.put(id, solidBelow);
            bhopTicks.put(id, 0);
            return;
        }
        // Mark landing to allow brief OnGroundSpeed tolerance right after falling
        if (isActualGround && !wasGround) {
            lastGroundedMs.put(id, now);
        }

        // OnGround Speed: moverse demasiado rápido estando en suelo sólido, considerando Speed
        if (allowOnGround && isActualGround && !slippery && !player.isInsideVehicle() && !com.anticheat.core.util.BlockUtil.isInLiquid(player.getLocation())) {
            double baseWalk = plugin.getConfig().getDouble("checks.movement.speed.max_base_walk", 0.25);
            double baseSprint = plugin.getConfig().getDouble("checks.movement.speed.max_base_sprint", 0.35);
            double perSpeedBonus = plugin.getConfig().getDouble("checks.movement.speed.per_speed_level_bonus", 0.10);
            boolean isSprinting = player.isSprinting();
            double allowed = isSprinting ? baseSprint : baseWalk;
            int speedLevel = 0;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.SPEED)) {
                    speedLevel = Math.max(speedLevel, effect.getAmplifier() + 1);
                }
            }
            allowed += speedLevel * perSpeedBonus;
            // Brief tolerance after landing
            long lastLand = lastGroundedMs.getOrDefault(id, 0L);
            if (lastLand != 0L && now - lastLand <= 250) {
                wasOnGround.put(id, isActualGround);
                bhopTicks.put(id, 0);
                return; // skip check right after landing
            }
            if (horizontal > allowed * 1.22) { // larger margin for variation
                int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "OnGroundSpeed", 1);
                event.setTo(event.getFrom());
                if (vl % 2 == 1) {
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.ongroundspeed", ph));
                }
            }
        }

        // BHop: count real jumps (ground->air) with high horizontal speed
        int resetMs = plugin.getConfig().getInt("checks.movement.bhop.reset_ms", 2500);
        long lastJump = lastBhopJumpMs.getOrDefault(id, 0L);
        if (lastJump != 0L && now - lastJump > resetMs) {
            bhopTicks.put(id, 0);
        }

        if (allowBhop && !isActualGround && wasGround && dy > 0.39 && !slippery && !player.isInsideVehicle()) {
            // Calculate allowed speed dynamically (considering Speed)
            double baseWalk = plugin.getConfig().getDouble("checks.movement.speed.max_base_walk", 0.25);
            double baseSprint = plugin.getConfig().getDouble("checks.movement.speed.max_base_sprint", 0.35);
            double perSpeedBonus = plugin.getConfig().getDouble("checks.movement.speed.per_speed_level_bonus", 0.10);
            boolean isSprinting = player.isSprinting();
            double allowed = isSprinting ? baseSprint : baseWalk;
            int speedLevel = 0;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (effect.getType().equals(PotionEffectType.SPEED)) {
                    speedLevel = Math.max(speedLevel, effect.getAmplifier() + 1);
                }
            }
            allowed += speedLevel * perSpeedBonus;

            if (horizontal > allowed * 1.20) {
                int t = bhopTicks.getOrDefault(id, 0) + 1;
                bhopTicks.put(id, t);
                lastBhopJumpMs.put(id, now);
                int threshold = plugin.getConfig().getInt("checks.movement.bhop.threshold_jumps", 3);
                if (t >= threshold) {
                    int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "BHop", 1);
                    event.setTo(event.getFrom());
                    bhopTicks.put(id, 0);
                    if (vl % 2 == 1) {
                        MessageService ms = plugin.getMessages();
                        String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("prefix", prefix);
                        ph.put("vl", String.valueOf(vl));
                        player.sendMessage(ms.format("player.bhop", ph));
                    }
                }
            }
        } else {
            if (isActualGround) {
                bhopTicks.put(id, 0);
            }
        }
        wasOnGround.put(id, isActualGround);
    }

    private void checkStep(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.step.enabled")) return;
        // Exempt when the player is gliding with Elytra to avoid false positives
        if (isGliding(player)) return;
        double dy = event.getTo().getY() - event.getFrom().getY();
        if (dy <= 0) return;

        // Ignore stairs and slabs
        Material feet = player.getLocation().getBlock().getType();
        Material under = player.getLocation().clone().add(0, -1, 0).getBlock().getType();
        if (BlockUtil.isStairsOrSlab(feet) || BlockUtil.isStairsOrSlab(under)) return;

        double maxStep = plugin.getConfig().getDouble("checks.movement.step.max_step_y", 0.7);
        if (dy > maxStep && dy < 1.25) {
            int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "Step", 1);
            event.setTo(event.getFrom());
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.step", ph));
            }
        }
    }

    // Gliding (elytra) detection via reflection for cross-version compatibility
    private boolean isGliding(Player player) {
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("isGliding");
            Object r = m.invoke(player);
            if (r instanceof Boolean) return ((Boolean) r).booleanValue();
        } catch (Exception ignored) {}
        return false;
    }

    private void checkJesus(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.jesus.enabled")) return;
        Location to = event.getTo();
        Location feetLoc = to.clone();
        Material feetType = feetLoc.getBlock().getType();
        Material belowType = feetLoc.clone().add(0, -1, 0).getBlock().getType();

        boolean feetAir = feetType == Material.AIR;
        boolean belowWater = BlockUtil.isWater(belowType);

        double dx = to.getX() - event.getFrom().getX();
        double dz = to.getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (feetAir && belowWater && !player.isInsideVehicle()) {
            int t = jesusTicks.getOrDefault(player.getUniqueId(), 0) + 1;
            jesusTicks.put(player.getUniqueId(), t);
            double maxHz = plugin.getConfig().getDouble("checks.movement.jesus.max_horizontal_per_tick", 0.35);
            if (horizontal > maxHz && t > 3) { // edge tolerance
                int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "WaterWalk", 1);
                event.setTo(event.getFrom());
                if (vl % 2 == 1) {
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.waterwalk", ph));
                }
            }
        } else {
            jesusTicks.put(player.getUniqueId(), 0);
        }
    }

    private void checkJetpack(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.jetpack.enabled")) return;
        boolean isGliding = false;
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("isGliding");
            Object r = m.invoke(player);
            if (r instanceof Boolean) isGliding = ((Boolean) r).booleanValue();
        } catch (Exception ignored) {}
        if (player.getAllowFlight() || isGliding) return;
        double dy = event.getTo().getY() - event.getFrom().getY();
        double maxUp = plugin.getConfig().getDouble("checks.movement.jetpack.max_vertical_per_tick", 0.7);
        if (dy > maxUp) {
            int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "Jetpack", 1);
            event.setTo(event.getFrom());
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.jetpack", ph));
            }
        }
    }

    private void checkSpider(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.spider.enabled")) return;
        if (player.getAllowFlight()) return;
        double dy = event.getTo().getY() - event.getFrom().getY();
        boolean inLiquid = BlockUtil.isInLiquid(event.getTo());
        if (inLiquid) return;
        boolean onGround = player.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid();

        // Detect adjacent solid blocks (walls)
        Location loc = player.getLocation();
        boolean nearWall = loc.clone().add(1, 0, 0).getBlock().getType().isSolid()
                || loc.clone().add(-1, 0, 0).getBlock().getType().isSolid()
                || loc.clone().add(0, 0, 1).getBlock().getType().isSolid()
                || loc.clone().add(0, 0, -1).getBlock().getType().isSolid();

        // Ignore stairs and slabs (extra edge tolerance)
        Material feet = player.getLocation().getBlock().getType();
        Material under = player.getLocation().clone().add(0, -1, 0).getBlock().getType();
        if (BlockUtil.isStairsOrSlab(feet) || BlockUtil.isStairsOrSlab(under)) return;
        boolean nearClimbable = loc.clone().add(1, 0, 0).getBlock().getType() == Material.LADDER
                || loc.clone().add(-1, 0, 0).getBlock().getType() == Material.LADDER
                || loc.clone().add(0, 0, 1).getBlock().getType() == Material.LADDER
                || loc.clone().add(0, 0, -1).getBlock().getType() == Material.LADDER
                || loc.clone().add(1, 0, 0).getBlock().getType() == Material.VINE
                || loc.clone().add(-1, 0, 0).getBlock().getType() == Material.VINE
                || loc.clone().add(0, 0, 1).getBlock().getType() == Material.VINE
                || loc.clone().add(0, 0, -1).getBlock().getType() == Material.VINE
                || feet == Material.LADDER || feet == Material.VINE;
        if (nearClimbable) return;

        double minSpiderUp = plugin.getConfig().getDouble("checks.movement.spider.min_vertical_per_tick", 0.08);
        double maxSpiderUp = plugin.getConfig().getDouble("checks.movement.spider.max_vertical_per_tick", 0.35);
        double margin = plugin.getConfig().getDouble("checks.movement.spider.margin_vertical", 0.03);
        if (!onGround && nearWall && dy >= minSpiderUp && dy <= (maxSpiderUp + margin)) {
            int t = spiderTicks.getOrDefault(player.getUniqueId(), 0) + 1;
            spiderTicks.put(player.getUniqueId(), t);
            int threshold = plugin.getConfig().getInt("checks.movement.spider.threshold_ticks", 4);
            if (t >= threshold) {
                int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "Climb", 1);
                event.setTo(event.getFrom());
                spiderTicks.put(player.getUniqueId(), 0);
                if (vl % 2 == 1) {
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.climb", ph));
                }
            }
        } else {
            spiderTicks.put(player.getUniqueId(), 0);
        }
    }

    private void checkBlink(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.blink.enabled")) return;
        long now = System.currentTimeMillis();
        Location last = lastMoveLoc.get(player.getUniqueId());
        Long lastTime = lastMoveTime.get(player.getUniqueId());

        lastMoveLoc.put(player.getUniqueId(), event.getTo().clone());
        lastMoveTime.put(player.getUniqueId(), now);

        if (last == null || lastTime == null) return;

        double dist = event.getTo().distance(last);
        long dt = Math.max(1, now - lastTime);
        double maxBlinkDist = plugin.getConfig().getDouble("checks.movement.blink.max_distance", 6.0);
        if (dist > maxBlinkDist && dt < 300) { // large jump in a short time
            int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "Blink", 1);
            event.setTo(event.getFrom());
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.blink", ph));
            }
        }
    }

    private void trackNoFallHeuristic(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.nofall.enabled")) return;
        // Accumulate fall distance when descending; if landing after > threshold and not in liquid, mark
        double dy = event.getTo().getY() - event.getFrom().getY();
        UUID id = player.getUniqueId();
        double acc = fallDistanceAcc.getOrDefault(id, 0.0);
        long now = System.currentTimeMillis();

        boolean inLiquid = BlockUtil.isInLiquid(event.getTo());
        if (inLiquid) {
            // Upon entering liquid, clear accumulation to avoid marking NoFall upon exiting water
            lastLiquidMs.put(id, now);
            fallDistanceAcc.put(id, 0.0);
            noFallDeferUntilMs.remove(id);
            noFallDeferStartMs.remove(id);
        }
        boolean onGround = player.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid();
        boolean isGliding = false;
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("isGliding");
            Object r = m.invoke(player);
            if (r instanceof Boolean) isGliding = ((Boolean) r).booleanValue();
        } catch (Exception ignored) {}
        if (isGliding) {
            lastGlideMs.put(id, now);
        }
        if (dy < 0 && !inLiquid) {
            // Do not accumulate fall while gliding with elytra
            if (!isGliding) {
                acc += -dy;
            }
            fallDistanceAcc.put(id, acc);
        }
        if (onGround) {
            // Exempt recent landing from water
            long lastLiq = lastLiquidMs.getOrDefault(id, 0L);
            long waterExemptMs = plugin.getConfig().getLong("checks.movement.nofall.water_landing_exempt_ms", 1500L);
            if (lastLiq != 0L && (now - lastLiq) <= waterExemptMs) {
                fallDistanceAcc.put(id, 0.0);
                noFallDeferUntilMs.remove(id);
                noFallDeferStartMs.remove(id);
                return;
            }
            // Exempt recent landing after elytra gliding
            long lastGlide = lastGlideMs.getOrDefault(id, 0L);
            long elytraExemptMs = plugin.getConfig().getLong("checks.movement.nofall.elytra_landing_exempt_ms", 1200L);
            if (lastGlide != 0L && (now - lastGlide) <= elytraExemptMs) {
                fallDistanceAcc.put(id, 0.0);
                noFallDeferUntilMs.remove(id);
                noFallDeferStartMs.remove(id);
                return;
            }
            double minFall = plugin.getConfig().getDouble("checks.movement.nofall.min_fall_distance", 3.5);
            // Ajustar por encantamientos y efectos (ej. Manzana de Notch -> Resistance)
            int ffLevel = 0;
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && boots.hasItemMeta() && boots.getItemMeta().hasEnchant(Enchantment.PROTECTION_FALL)) {
                ffLevel = boots.getItemMeta().getEnchantLevel(Enchantment.PROTECTION_FALL);
            }
            double ffBonus = plugin.getConfig().getDouble("checks.movement.nofall.feather_falling_bonus_per_level", 1.0);
            minFall += ffLevel * ffBonus;

            boolean hasResistance = false;
            boolean hasAbsorption = false;
            for (PotionEffect pe : player.getActivePotionEffects()) {
                if (pe.getType().equals(PotionEffectType.DAMAGE_RESISTANCE)) {
                    hasResistance = true;
                } else if (pe.getType().equals(PotionEffectType.ABSORPTION)) {
                    hasAbsorption = true;
                }
            }
            if (hasResistance) {
                double resMult = plugin.getConfig().getDouble("checks.movement.nofall.resistance_multiplier", 1.5);
                minFall *= resMult;
            }
            boolean absorptionExempt = plugin.getConfig().getBoolean("checks.movement.nofall.absorption_exempt", false);
            boolean skipByAbsorption = absorptionExempt && hasAbsorption;
            long windowMs = plugin.getConfig().getLong("checks.movement.nofall.damage_exempt_window_ms", 1500L);
            Long lastFallMs = lastFallDamageMs.get(id);
            boolean recentFallDamage = lastFallMs != null && (now - lastFallMs) <= windowMs;
            Long deferStart = noFallDeferStartMs.get(id);
            boolean damageAfterDefer = (deferStart != null && lastFallMs != null && lastFallMs >= deferStart);
            if (acc > minFall) {
                if (skipByAbsorption || recentFallDamage || damageAfterDefer) {
                // Exempt due to absorption or recent fall damage: clear accumulation and deferral
                fallDistanceAcc.put(id, 0.0);
                noFallDeferUntilMs.remove(id);
                noFallDeferStartMs.remove(id);
                } else {
                    long graceMs = plugin.getConfig().getLong("checks.movement.nofall.landing_grace_ms", 500L);
                    Long deferUntil = noFallDeferUntilMs.get(id);
                    long until = (deferUntil == null) ? 0L : deferUntil.longValue();
                    if (until == 0L) {
                        // Start a grace window to wait for the damage event
                        noFallDeferStartMs.put(id, now);
                        noFallDeferUntilMs.put(id, now + graceMs);
                        return;
                    }
                    if (now < until) {
                        // Still within the window: do not mark or clear
                        return;
                    }
                    // Window expired and no damage occurred: mark NoFall
                    int vl = plugin.getViolationManager().addViolation(id, "NoFall", 1);
                    event.setTo(event.getFrom());
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.nofall", ph));
                    // Reset tras sanción
                    fallDistanceAcc.put(id, 0.0);
                    noFallDeferUntilMs.remove(id);
                    noFallDeferStartMs.remove(id);
                }
            } else {
                // Caída por debajo de umbral: limpiar acumulado y deferral
                fallDistanceAcc.put(id, 0.0);
                noFallDeferUntilMs.remove(id);
                noFallDeferStartMs.remove(id);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            lastFallDamageMs.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    private void checkTimer(Player player) {
        if (!enabled("checks.movement.timer.enabled")) return;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long windowStart = lastTimerWindowStart.getOrDefault(id, now);
        int count = moveEventsInWindow.getOrDefault(id, 0) + 1;
        moveEventsInWindow.put(id, count);
        if (now - windowStart >= 1000) {
            int maxPerSec = plugin.getConfig().getInt("checks.movement.timer.max_move_events_per_sec", 35);
            if (count > maxPerSec) {
                int vl = plugin.getViolationManager().addViolation(id, "Timer", 1);
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.timer", ph));
            }
            lastTimerWindowStart.put(id, now);
            moveEventsInWindow.put(id, 0);
        } else {
            lastTimerWindowStart.put(id, windowStart);
        }
    }

    private void checkNoSlow(Player player, PlayerMoveEvent event) {
        if (!enabled("checks.movement.noslow.enabled")) return;
        if (!player.isBlocking()) return;
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double maxBlockingHz = plugin.getConfig().getDouble("checks.movement.noslow.max_blocking_horizontal", 0.20);
        UUID id = player.getUniqueId();
        if (horizontal > maxBlockingHz) {
            int t = noSlowTicks.getOrDefault(id, 0) + 1;
            noSlowTicks.put(id, t);
            int threshold = plugin.getConfig().getInt("checks.movement.noslow.threshold", 4);
            if (t >= threshold) {
                int vl = plugin.getViolationManager().addViolation(id, "NoSlow", 1);
                event.setTo(event.getFrom());
                noSlowTicks.put(id, 0);
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.noslow", ph));
            }
        } else {
            noSlowTicks.put(id, 0);
        }
    }

}