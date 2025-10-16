package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import com.anticheat.core.util.MessageService;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class ElytraCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Long> boostUntilMs = new HashMap<>();

    public ElytraCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("checks.elytra.enabled", true)) return;
        Player player = event.getPlayer();
        if (!isGliding(player)) return;

        long now = System.currentTimeMillis();
        long until = boostUntilMs.getOrDefault(player.getUniqueId(), 0L);
        boolean inBoost = now < until;
        if (inBoost) {
            // Do not flag during rocket boost to avoid false positives
            return;
        }

        double dy = event.getTo().getY() - event.getFrom().getY();
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double maxUp = plugin.getConfig().getDouble("checks.elytra.max_vertical_up_per_tick", 0.9);
        double maxHz = plugin.getConfig().getDouble("checks.elytra.max_horizontal_per_tick", 1.8);

        if (dy > maxUp || horizontal > maxHz) {
            int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "ElytraFly", 1);
            event.setTo(event.getFrom());
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.elytrafly", ph));
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("checks.elytra.enabled", true)) return;
        Player player = event.getPlayer();
        // Bypass: OPs or configured permissions
        if (plugin.isExempt(player)) return;
        if (!isGliding(player)) return;
        // Detect rocket from the event item or player's hands (main/offhand)
        boolean hasFirework = false;
        if (event.getItem() != null) {
            String itemName = event.getItem().getType().name();
            hasFirework = itemName.contains("FIREWORK");
        }
        if (!hasFirework) {
            hasFirework = hasFireworkInHands(player);
        }
        if (hasFirework) {
            int window = plugin.getConfig().getInt("checks.elytra.boost_exempt_window_ms", 1200);
            boostUntilMs.put(player.getUniqueId(), System.currentTimeMillis() + window);
        }
    }

    private boolean hasFireworkInHands(Player player) {
        try {
            Object inv = player.getInventory();
            java.lang.reflect.Method mMain = null;
            java.lang.reflect.Method mOff = null;
            java.lang.reflect.Method mOld = null;
            try { mMain = inv.getClass().getMethod("getItemInMainHand"); } catch (Throwable ignored) {}
            try { mOff = inv.getClass().getMethod("getItemInOffHand"); } catch (Throwable ignored) {}
            try { mOld = inv.getClass().getMethod("getItemInHand"); } catch (Throwable ignored) {}
            if (mMain != null) {
                Object main = mMain.invoke(inv);
                if (main instanceof ItemStack && ((ItemStack) main).getType().name().contains("FIREWORK")) return true;
            }
            if (mOff != null) {
                Object off = mOff.invoke(inv);
                if (off instanceof ItemStack && ((ItemStack) off).getType().name().contains("FIREWORK")) return true;
            }
            if (mOld != null) {
                Object old = mOld.invoke(inv);
                if (old instanceof ItemStack && ((ItemStack) old).getType().name().contains("FIREWORK")) return true;
            }
        } catch (Throwable ignored) {}
        return false;
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
        // Grant exemption to players gliding near the rocket spawn
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (!isGliding(p)) continue;
                if (p.getWorld() != entity.getWorld()) continue;
                if (p.getLocation().distanceSquared(entity.getLocation()) <= radiusSq) { // radio configurable
                    boostUntilMs.put(p.getUniqueId(), now + window);
                }
            } catch (Throwable ignored) {}
        }
    }

    private boolean isGliding(Player player) {
        try {
            java.lang.reflect.Method m = player.getClass().getMethod("isGliding");
            Object r = m.invoke(player);
            if (r instanceof Boolean) return ((Boolean) r).booleanValue();
        } catch (Exception ignored) {}
        return false;
    }
    
}