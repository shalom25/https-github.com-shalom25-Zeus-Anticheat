package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.Location;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.anticheat.core.util.MessageService;
import org.bukkit.util.Vector;

public class PlacementCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Deque<Long>> placeTimes = new HashMap<>();
    private final Map<UUID, Integer> scaffoldStreak = new HashMap<>();
    private final Map<UUID, Integer> trapStreak = new HashMap<>();

    public PlacementCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // Bypass: OPs o permisos configurados
        if (plugin.isExempt(player)) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> deque = placeTimes.computeIfAbsent(id, k -> new ArrayDeque<>());
        deque.addLast(now);
        while (!deque.isEmpty() && now - deque.peekFirst() > 1000) deque.pollFirst();

        boolean fastPlaceEnabled = plugin.getConfig().getBoolean("checks.place.fastplace.enabled", true);
        int maxPerSec = plugin.getConfig().getInt("checks.place.fastplace.max_per_sec", 10);
        if (fastPlaceEnabled && deque.size() > maxPerSec) {
            int vl = plugin.getViolationManager().addViolation(id, "FastPlace", 1);
            event.setCancelled(true);
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.fastplace", ph));
            }
            return;
        }

        Location pLoc = player.getLocation();
        Location bLoc = event.getBlockPlaced().getLocation();
        // Usa el centro del bloque para el cálculo horizontal, reduce falsos positivos en towering
        Location bCenterXZ = bLoc.clone().add(0.5, 0.0, 0.5);
        boolean belowFeet = bLoc.getY() <= pLoc.getY() - 0.9 && Math.abs(bCenterXZ.getX() - pLoc.getX()) < 1.2 && Math.abs(bCenterXZ.getZ() - pLoc.getZ()) < 1.2;
        double dx = bCenterXZ.getX() - pLoc.getX();
        double dz = bCenterXZ.getZ() - pLoc.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        boolean scaffoldEnabled = plugin.getConfig().getBoolean("checks.place.scaffold.enabled", true);
        double maxHzWhileScaffold = plugin.getConfig().getDouble("checks.place.scaffold.max_player_horizontal", 0.35);
        int scaffoldThreshold = plugin.getConfig().getInt("checks.place.scaffold.threshold", 4);
        boolean sneakingExempt = plugin.getConfig().getBoolean("checks.place.scaffold.sneaking_exempt", true);
        boolean toweringExempt = plugin.getConfig().getBoolean("checks.place.scaffold.towering_exempt", true);
        double towerHzEps = plugin.getConfig().getDouble("checks.place.scaffold.towering_horizontal_epsilon", 0.22);
        // Exención adicional: colocar al saltar mientras se torre (subida vertical legítima)
        boolean jumpingExemptEnabled = plugin.getConfig().getBoolean("checks.place.scaffold.jumping_exempt", true);
        double jumpVertMin = plugin.getConfig().getDouble("checks.place.scaffold.jump_vertical_min", 0.23);
        double jumpHzEps = plugin.getConfig().getDouble("checks.place.scaffold.jump_horizontal_epsilon", 0.38);
        boolean midAir = !player.getLocation().getBlock().getRelative(0, -1, 0).getType().isSolid();
        double velY = player.getVelocity().getY();
        boolean jumping = midAir && velY > jumpVertMin;
        boolean towering = belowFeet && horizontal <= towerHzEps;
        boolean jumpExempt = jumpingExemptEnabled && jumping && belowFeet && horizontal <= jumpHzEps;
        if (scaffoldEnabled && !(sneakingExempt && player.isSneaking()) && !((toweringExempt) && towering) && !jumpExempt && belowFeet && horizontal > maxHzWhileScaffold) {
            int s = scaffoldStreak.getOrDefault(id, 0) + 1;
            scaffoldStreak.put(id, s);
            if (s >= scaffoldThreshold) {
                int vl = plugin.getViolationManager().addViolation(id, "Scaffold", 1);
                event.setCancelled(true);
                scaffoldStreak.put(id, 0);
                if (vl % 2 == 1) {
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.scaffold", ph));
                }
                return;
            }
        } else {
            scaffoldStreak.put(id, 0);
        }

        boolean freecamEnabled = plugin.getConfig().getBoolean("checks.place.freecam.enabled", true);
        double reachMax = plugin.getConfig().getDouble("checks.place.freecam.max_reach", 5.5);
        Location targetCenter = bLoc.clone().add(0.5, 0.5, 0.5);
        double dist = player.getEyeLocation().distance(targetCenter);
        if (freecamEnabled) {
            // Alcance excesivo
            if (dist > reachMax) {
                int vl = plugin.getViolationManager().addViolation(id, "Freecam", 1);
                event.setCancelled(true);
                // Activar congelamiento tras detectar Freecam
                if (plugin.getConfig().getBoolean("checks.place.freecam.freeze_on_detect", true)) {
                    int freezeMs = plugin.getConfig().getInt("checks.place.freecam.freeze_duration_ms", 2500);
                    plugin.freezePlayerForFreecam(id, now + freezeMs);
                }
                if (vl % 2 == 1) {
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.freecam", ph));
                }
                return;
            }
            // Verificación de línea de visión para evitar colocación a través de paredes (Freecam)
            boolean losEnabled = plugin.getConfig().getBoolean("checks.place.freecam.los_enabled", true);
            if (losEnabled) {
                org.bukkit.block.Block support = event.getBlockAgainst();
                if (isOccludedLOS(player, targetCenter, support)) {
                    int vl = plugin.getViolationManager().addViolation(id, "Freecam", 1);
                    event.setCancelled(true);
                    // Activar congelamiento tras detectar Freecam
                    if (plugin.getConfig().getBoolean("checks.place.freecam.freeze_on_detect", true)) {
                        int freezeMs = plugin.getConfig().getInt("checks.place.freecam.freeze_duration_ms", 2500);
                        plugin.freezePlayerForFreecam(id, now + freezeMs);
                    }
                    if (vl % 2 == 1) {
                        MessageService ms = plugin.getMessages();
                        String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("prefix", prefix);
                        ph.put("vl", String.valueOf(vl));
                        player.sendMessage(ms.format("player.freecam", ph));
                    }
                    return;
                }
            }
        }

        // resto de checks

        boolean autocrystalEnabled = plugin.getConfig().getBoolean("checks.place.autocrystal.enabled", true);
        if (autocrystalEnabled && event.getBlockPlaced().getType() != null && "END_CRYSTAL".equalsIgnoreCase(event.getBlockPlaced().getType().name())) {
            int crystalsPerSec = plugin.getConfig().getInt("checks.place.autocrystal.max_per_sec", 6);
            if (deque.size() > crystalsPerSec) {
                int vl = plugin.getViolationManager().addViolation(id, "AutoCrystal", 1);
                event.setCancelled(true);
                if (vl % 2 == 1) {
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.autocrystal", ph));
                }
                return;
            }
        }

        boolean autotrapEnabled = plugin.getConfig().getBoolean("checks.place.autotrap.enabled", true);
        int trapRadius = plugin.getConfig().getInt("checks.place.autotrap.radius", 1);
        int trapThreshold = plugin.getConfig().getInt("checks.place.autotrap.threshold", 5);
        boolean nearPlayer = false;
        for (org.bukkit.entity.Player other : player.getWorld().getPlayers()) {
            if (!other.getUniqueId().equals(id)) {
                if (other.getLocation().distance(bLoc) <= trapRadius) {
                    nearPlayer = true;
                    break;
                }
            }
        }
        if (autotrapEnabled && nearPlayer) {
            int t = trapStreak.getOrDefault(id, 0) + 1;
            trapStreak.put(id, t);
            if (t >= trapThreshold) {
                int vl = plugin.getViolationManager().addViolation(id, "AutoTrap", 1);
                event.setCancelled(true);
                trapStreak.put(id, 0);
                if (vl % 2 == 1) {
                    MessageService ms = plugin.getMessages();
                    String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("vl", String.valueOf(vl));
                    player.sendMessage(ms.format("player.autotrap", ph));
                }
            }
        } else {
            trapStreak.put(id, 0);
        }
    }

    private boolean isOccludedLOS(Player player, Location targetCenter, org.bukkit.block.Block supportBlock) {
        Location eye = player.getEyeLocation();
        Vector dir = targetCenter.clone().subtract(eye).toVector();
        double dist = dir.length();
        if (dist <= 0.0001) return false;
        dir.normalize();
        double step = plugin.getConfig().getDouble("checks.place.freecam.los_step", 0.3);
        double minDist = plugin.getConfig().getDouble("checks.place.freecam.los_min_distance", 3.0);
        if (dist < minDist) return false; // Evitar falsos positivos a corta distancia
        double buffer = plugin.getConfig().getDouble("checks.place.freecam.los_target_buffer", 0.6);
        for (double t = 0.0; t < dist - buffer; t += step) {
            Location sample = eye.clone().add(dir.clone().multiply(t));
            org.bukkit.block.Block b = sample.getBlock();
            if (supportBlock != null && b.getLocation().equals(supportBlock.getLocation())) {
                continue;
            }
            if (b.getType().isSolid()) {
                boolean ignorePassable = plugin.getConfig().getBoolean("checks.place.freecam.los_ignore_passable", true);
                if (ignorePassable) {
                    String name = b.getType().name();
                    if (name.contains("TRAPDOOR") || name.contains("FENCE") || name.contains("FENCE_GATE") || name.contains("DOOR")
                            || name.contains("SIGN") || name.contains("LADDER") || name.contains("VINE") || name.contains("CARPET")
                            || name.contains("GLASS_PANE")) {
                        continue;
                    }
                }
                return true;
            }
        }
        return false;
    }

}