package com.anticheat.core.listeners;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Map;
import java.util.HashMap;
import org.bukkit.ChatColor;

public class ModerationHookListener implements Listener {
    private final AntiCheatPlugin plugin;

    public ModerationHookListener(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean broadcastEnabled() {
        return plugin.getConfig().getBoolean("hooks.essentials.broadcast", true);
    }

    private void handle(String senderName, String cmdLine) {
        if (!broadcastEnabled()) return;
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[Zeus] "));
        MessageService ms = plugin.getMessages();

        String line = cmdLine.trim();
        if (line.startsWith("/")) line = line.substring(1);
        String[] parts = line.split("\\s+");
        if (parts.length == 0) return;

        String cmd = parts[0].toLowerCase();
        if (cmd.equals("kick") && parts.length >= 2) {
            String target = parts[1];
            String reason = parts.length >= 3 ? line.substring(line.indexOf(target) + target.length()).trim() : ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
            if (reason.isEmpty()) reason = ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
            Map<String,String> ph = new HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("player", target);
            ph.put("staff", senderName);
            ph.put("reason", reason);
            String msg = ms.format("public_mod.kick", ph);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        } else if (cmd.equals("ban") && parts.length >= 2) {
            String target = parts[1];
            String reason = parts.length >= 3 ? line.substring(line.indexOf(target) + target.length()).trim() : ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
            if (reason.isEmpty()) reason = ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
            String duration = ms.format("sanction.duration_permanent", java.util.Collections.<String,String>emptyMap());
            Map<String,String> ph = new HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("player", target);
            ph.put("staff", senderName);
            ph.put("duration", duration);
            ph.put("reason", reason);
            String msg = ms.format("public_mod.ban", ph);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        } else if ((cmd.equals("tempban") || cmd.equals("banip") || cmd.equals("tempbanip")) && parts.length >= 3) {
            String target = parts[1];
            String rawTime = parts[2];
            String reason = parts.length >= 4 ? line.substring(line.indexOf(rawTime) + rawTime.length()).trim() : ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
            if (reason.isEmpty()) reason = ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
            String duration = formatDuration(rawTime, ms);
            Map<String,String> ph = new HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("player", target);
            ph.put("staff", senderName);
            ph.put("duration", duration);
            ph.put("reason", reason);
            String msg = ms.format("public_mod.tempban", ph);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        } else if (cmd.equals("mute") && parts.length >= 2) {
            String target = parts[1];
            String duration;
            String reason;
            if (parts.length >= 3) {
                String token = parts[2];
                if (token.matches(".*\\d.*")) {
                    duration = formatDuration(token, ms);
                    reason = parts.length >= 4 ? line.substring(line.indexOf(token) + token.length()).trim() : ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
                } else {
                    duration = ms.format("sanction.duration_permanent", java.util.Collections.<String,String>emptyMap());
                    reason = line.substring(line.indexOf(token)).trim();
                }
            } else {
                duration = ms.format("sanction.duration_permanent", java.util.Collections.<String,String>emptyMap());
                reason = ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
            }
            if (reason.isEmpty()) reason = ms.format("sanction.no_reason", java.util.Collections.<String,String>emptyMap());
            Map<String,String> ph = new HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("player", target);
            ph.put("staff", senderName);
            ph.put("duration", duration);
            ph.put("reason", reason);
            String msg = ms.format("public_mod.mute", ph);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        } else if (cmd.equals("unmute") && parts.length >= 2) {
            String target = parts[1];
            Map<String,String> ph = new HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("player", target);
            ph.put("staff", senderName);
            String msg = ms.format("public_mod.unmute", ph);
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        }
    }

    private String formatDuration(String raw, MessageService ms) {
        try {
            long minutes = parseToMinutes(raw);
            if (minutes > 0) {
                Map<String,String> ph = new HashMap<String, String>();
                ph.put("minutes", String.valueOf(minutes));
                return ms.format("sanction.duration_minutes", ph);
            }
        } catch (Exception ignored) {}
        Map<String,String> ph = new HashMap<String, String>();
        ph.put("text", raw);
        return ms.format("sanction.duration_text", ph);
    }

    private long parseToMinutes(String raw) {
        raw = raw.trim().toLowerCase();
        if (raw.matches("^\\d+$")) {
            return Long.parseLong(raw);
        }
        long total = 0;
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                long n = num.length() > 0 ? Long.parseLong(num.toString()) : 0;
                num.setLength(0);
                switch (c) {
                    case 's': total += n / 60; break;
                    case 'm': total += n; break;
                    case 'h': total += n * 60; break;
                    case 'd': total += n * 60 * 24; break;
                    case 'w': total += n * 60 * 24 * 7; break;
                    default: break;
                }
            }
        }
        if (num.length() > 0) total += Long.parseLong(num.toString());
        return total;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Solo si Essentials está presente y hook habilitado
        if (Bukkit.getPluginManager().getPlugin("Essentials") == null) return;
        if (!plugin.getConfig().getBoolean("hooks.essentials.enabled", true)) return;
        String name = event.getPlayer().getName();
        handle(name, event.getMessage());
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        if (Bukkit.getPluginManager().getPlugin("Essentials") == null) return;
        if (!plugin.getConfig().getBoolean("hooks.essentials.enabled", true)) return;
        String name = "CONSOLE";
        handle(name, event.getCommand());
    }
}