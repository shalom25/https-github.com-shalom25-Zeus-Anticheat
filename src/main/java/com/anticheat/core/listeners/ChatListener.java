package com.anticheat.core.listeners;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.MessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;

import org.bukkit.ChatColor;
import java.util.HashMap;
import java.util.Map;

public class ChatListener implements Listener {
    private final AntiCheatPlugin plugin;

    public ChatListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        com.anticheat.core.violation.ViolationManager vm = plugin.getViolationManager();
        if (!vm.isMuted(player.getUniqueId())) return;

        event.setCancelled(true);

        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
        MessageService ms = plugin.getMessages();

        Long until = vm.getMuteUntil(player.getUniqueId());
        String durationStr;
        if (until == null) {
            durationStr = "";
        } else if (until == Long.MAX_VALUE) {
            durationStr = ms.format("sanction.duration_permanent", java.util.Collections.<String,String>emptyMap());
        } else {
            long now = System.currentTimeMillis();
            long remainingMs = until - now;
            long minutes = Math.max(1, (remainingMs + 59999) / 60000);
            Map<String,String> ph = new HashMap<String, String>();
            ph.put("minutes", String.valueOf(minutes));
            durationStr = ms.format("sanction.duration_minutes", ph);
        }

        Map<String,String> ph2 = new HashMap<String, String>();
        ph2.put("prefix", prefix);
        ph2.put("duration", durationStr);
        String msg = ms.format("player_muted", ph2);
        player.sendMessage(msg);
    }
}