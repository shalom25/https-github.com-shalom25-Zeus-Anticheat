package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.MessageService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BaritoneCheck implements Listener {

    private final AntiCheatPlugin plugin;

    private final Map<UUID, Integer> stableTicks = new HashMap<>();
    private final Map<UUID, Double> lastHz = new HashMap<>();
    private final Map<UUID, Float> lastYaw = new HashMap<>();
    private final Map<UUID, Float> lastPitch = new HashMap<>();
    private final Map<UUID, Long> lastPlaceMs = new HashMap<>();
    private final Map<UUID, Long> boostUntilMs = new HashMap<>();

    public BaritoneCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        lastPlaceMs.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onFireworkSpawn(EntitySpawnEvent event) {
        if (!plugin.getConfig().getBoolean("checks.elytra.enabled", true)) return;
        Entity entity = event.getEntity();
        String typeName = entity.getType().name();
        if (!typeName.contains("FIREWORK")) return;
        long now = System.currentTimeMillis();
        int window = plugin.getConfig().getInt("checks.elytra.boost_exempt_window_ms", 1200);
        double radius = plugin.getConfig().getDouble("checks.elytra.boost_detect_radius", 3.5);
        double radiusSq = radius * radius;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                java.lang.reflect.Method m = p.getClass().getMethod("isGliding");
                Object r = m.invoke(p);
                boolean gliding = (r instanceof Boolean) && ((Boolean) r).booleanValue();
                if (!gliding) continue;
                if (p.getWorld() != entity.getWorld()) continue;
                if (p.getLocation().distanceSquared(entity.getLocation()) <= radiusSq) {
                    boostUntilMs.put(p.getUniqueId(), now + window);
                }
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("checks.movement.baritone.enabled", true)) return;
        if (event.getFrom().toVector().equals(event.getTo().toVector())) return; // sin movimiento
        Player player = event.getPlayer();
        // Bypass: OPs o permisos configurados
        if (plugin.isExempt(player)) return;
        if (player.isInsideVehicle()) return;

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float fromYaw = event.getFrom().getYaw();
        float toYaw = event.getTo().getYaw();
        float fromPitch = event.getFrom().getPitch();
        float toPitch = event.getTo().getPitch();

        double hzEps = plugin.getConfig().getDouble("checks.movement.baritone.horizontal_epsilon", 0.02);
        double yawEps = plugin.getConfig().getDouble("checks.movement.baritone.yaw_epsilon_deg", 1.0);
        double pitchEps = plugin.getConfig().getDouble("checks.movement.baritone.pitch_epsilon_deg", 1.0);
        int threshold = plugin.getConfig().getInt("checks.movement.baritone.stable_ticks_threshold", 35);
        boolean cancel = plugin.getConfig().getBoolean("checks.movement.baritone.cancel_on_detect", true);

        // Exención: planeo con Elytra (incluye impulso con cohetes)
        boolean glidingExempt = plugin.getConfig().getBoolean("checks.movement.baritone.gliding_exempt", true);
        boolean isGliding = false;
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("isGliding");
            Object r = m.invoke(player);
            if (r instanceof Boolean) isGliding = ((Boolean) r).booleanValue();
        } catch (Exception ignored) {}
        Long boostUntil = boostUntilMs.get(player.getUniqueId());
        boolean boosted = boostUntil != null && System.currentTimeMillis() <= boostUntil.longValue();
        if ((glidingExempt && isGliding) || boosted) {
            UUID id = player.getUniqueId();
            stableTicks.put(id, 0);
            lastHz.put(id, horizontal);
            lastYaw.put(id, toYaw);
            lastPitch.put(id, toPitch);
            return;
        }

        // Exención: construcción vertical (towering) con colocación reciente
        long now = System.currentTimeMillis();
        Long lastPlace = lastPlaceMs.get(player.getUniqueId());
        int placeExemptMs = plugin.getConfig().getInt("checks.movement.baritone.place_exempt_ms", 800);
        double placeMinDy = plugin.getConfig().getDouble("checks.movement.baritone.place_vertical_min", 0.12);
        double placeHzEps = plugin.getConfig().getDouble("checks.movement.baritone.place_horizontal_epsilon", 0.35);
        double dy = event.getTo().getY() - event.getFrom().getY();
        boolean toweringExempt = lastPlace != null && (now - lastPlace) <= placeExemptMs && dy > placeMinDy && horizontal <= placeHzEps;
        if (toweringExempt) {
            // Resetear estabilidad mientras se construye verticalmente para evitar falsos positivos
            UUID id = player.getUniqueId();
            stableTicks.put(id, 0);
            lastHz.put(id, horizontal);
            lastYaw.put(id, toYaw);
            lastPitch.put(id, toPitch);
            return;
        }

        UUID id = player.getUniqueId();
        Double lastH = lastHz.get(id);
        Float ly = lastYaw.get(id);
        Float lp = lastPitch.get(id);

        boolean stable = false;
        if (lastH != null && ly != null && lp != null) {
            double dh = Math.abs(horizontal - lastH);
            double dyaw = Math.abs(toYaw - ly);
            double dpitch = Math.abs(toPitch - lp);
            stable = dh <= hzEps && dyaw <= yawEps && dpitch <= pitchEps;
        }

        lastHz.put(id, horizontal);
        lastYaw.put(id, toYaw);
        lastPitch.put(id, toPitch);

        int s = stable ? (stableTicks.getOrDefault(id, 0) + 1) : 0;
        stableTicks.put(id, s);

        if (s >= threshold) {
            int vl = plugin.getViolationManager().addViolation(id, "Baritone", 1);
            if (cancel) {
                event.setTo(event.getFrom());
            }
            // Log detallado con metadatos
            try {
                plugin.getViolationManager().addDetectionDetail(
                        player,
                        "Baritone",
                        vl,
                        String.format(
                                "pos=(%.2f,%.2f,%.2f) hz=%.3f yaw=%.1f->%.1f pitch=%.1f->%.1f cancel=%s",
                                event.getTo().getX(), event.getTo().getY(), event.getTo().getZ(),
                                horizontal,
                                fromYaw, toYaw,
                                fromPitch, toPitch,
                                String.valueOf(cancel)
                        )
                );
            } catch (Throwable ignored) {}
            MessageService ms = plugin.getMessages();
            String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
            java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("vl", String.valueOf(vl));
            player.sendMessage(ms.format("player.baritone", ph));
            // Reset para evitar spam y permitir reevaluación
            stableTicks.put(id, 0);
        }
    }
}