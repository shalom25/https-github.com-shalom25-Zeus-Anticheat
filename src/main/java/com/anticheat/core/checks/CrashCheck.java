package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detecciones de intentos de crash por contenido excesivo:
 * - Carteles con demasiados caracteres por línea o total.
 * La detección de libros está en InventoryCheck para compatibilidad 1.8.
 */
public class CrashCheck implements Listener {

    private final AntiCheatPlugin plugin;

    public CrashCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        if (!plugin.getConfig().getBoolean("checks.crash.sign.enabled", true)) return;
        Player player = event.getPlayer();
        // Bypass granular: Crash NO debe ser eximido si así lo indica config
        if (plugin.isExemptFor(player, "crash")) return;
        UUID id = player.getUniqueId();

        String[] lines = event.getLines();
        int maxPerLine = plugin.getConfig().getInt("checks.crash.sign.max_chars_per_line", 60);
        int maxTotal = plugin.getConfig().getInt("checks.crash.sign.max_total_chars", 120);
        boolean cancel = plugin.getConfig().getBoolean("checks.crash.sign.cancel_on_detect", true);

        int total = 0;
        boolean tooLongLine = false;
        for (String l : lines) {
            if (l != null) {
                total += l.length();
                if (l.length() > maxPerLine) {
                    tooLongLine = true;
                }
            }
        }

        if (tooLongLine || total > maxTotal) {
            int vl = plugin.getViolationManager().addViolation(id, "Crash", 1);
            if (cancel) event.setCancelled(true);
            // Log detallado del contenido del cartel
            try {
                org.bukkit.Location loc = player.getLocation();
                plugin.getViolationManager().addDetectionDetail(
                        player,
                        "Crash",
                        vl,
                        String.format(
                                "sign total=%d lineTooLong=%s cancel=%s pos=(%.2f,%.2f,%.2f)",
                                total,
                                String.valueOf(tooLongLine),
                                String.valueOf(cancel),
                                loc.getX(), loc.getY(), loc.getZ()
                        )
                );
            } catch (Throwable ignored) {}
            MessageService ms = plugin.getMessages();
            String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
            Map<String,String> ph = new HashMap<>();
            ph.put("prefix", prefix);
            ph.put("vl", String.valueOf(vl));
            player.sendMessage(ms.format("player.crash", ph));
        }
    }
}