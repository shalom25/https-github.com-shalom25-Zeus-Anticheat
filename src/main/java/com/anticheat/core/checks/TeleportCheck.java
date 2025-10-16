package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import com.anticheat.core.util.MessageService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;

public class TeleportCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Deque<Long>> chorusTeleports = new HashMap<>();

    public TeleportCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // Evitar referencia directa a TeleportCause.CHORUS_FRUIT (no existe en 1.8)
        if (!"CHORUS_FRUIT".equals(event.getCause().name())) return;
        boolean chorusEnabled = plugin.getConfig().getBoolean("checks.teleport.chorus.enabled", true);
        if (!chorusEnabled) return;
        Player player = event.getPlayer();
        // Bypass: OPs o permisos configurados
        if (plugin.isExempt(player)) return;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> deque = chorusTeleports.computeIfAbsent(id, k -> new ArrayDeque<>());
        deque.addLast(now);
        while (!deque.isEmpty() && now - deque.peekFirst() > 10000) deque.pollFirst();

        int maxPer10s = plugin.getConfig().getInt("checks.teleport.chorus.max_teleports_per_10s", 3);
        if (deque.size() > maxPer10s) {
            int vl = plugin.getViolationManager().addViolation(id, "ChorusControl", 1);
            event.setCancelled(true);
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.choruscontrol", ph));
            }
        }
    }

}