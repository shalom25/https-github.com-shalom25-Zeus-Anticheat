package com.anticheat.core.violation;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ViolationManager {
    private final AntiCheatPlugin plugin;
    private final Map<UUID, Map<String, Integer>> violations = new ConcurrentHashMap<>();
    private final Deque<String> logs = new ArrayDeque<>();
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();

    public ViolationManager(AntiCheatPlugin plugin) {
        this.plugin = plugin;
        // Load persistent logs if enabled
        boolean persist = plugin.getConfig().getBoolean("logs.persist_to_file", true);
        int maxInMem = plugin.getConfig().getInt("logs.max_in_memory", 500);
        if (persist) {
            try {
                File dataDir = plugin.getDataFolder();
                if (!dataDir.exists()) dataDir.mkdirs();
                String fileName = plugin.getConfig().getString("logs.file_name", "logs.txt");
                Path logPath = new File(dataDir, fileName).toPath();
                if (Files.exists(logPath)) {
                    List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
                    int start = Math.max(0, lines.size() - maxInMem);
                    for (int i = start; i < lines.size(); i++) {
                        logs.addLast(lines.get(i));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public int addViolation(UUID uuid, String check, int amount) {
        Map<String, Integer> map = violations.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        int current = map.getOrDefault(check, 0) + amount;
        map.put(check, current);

        // Notificación inmediata a staff si está habilitada
        if (plugin.getConfig().getBoolean("messages.notify_ops_on_detect", true)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                MessageService ms = plugin.getMessages();
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("player", player.getName());
                ph.put("check", check);
                ph.put("vl", String.valueOf(current));
                String msg = ms.format("staff.detect", ph);
                broadcastStaff(msg);
                addLog("DETECT", player.getName(), check, current);
            }
        }

        handlePunishments(uuid, check, current);
        return current;
    }

    public int getViolation(UUID uuid, String check) {
        Map<String, Integer> map = violations.get(uuid);
        if (map == null) return 0;
        return map.getOrDefault(check, 0);
    }

    public void reset(UUID uuid, String check) {
        Map<String, Integer> map = violations.get(uuid);
        if (map != null) map.remove(check);
    }

    public void resetAll(UUID uuid) {
        Map<String, Integer> map = violations.get(uuid);
        if (map != null) map.clear();
    }

    public Map<String, Integer> getAll(UUID uuid) {
        Map<String, Integer> map = violations.get(uuid);
        if (map == null) return new ConcurrentHashMap<>();
        return new ConcurrentHashMap<>(map);
    }

    private void handlePunishments(UUID uuid, String check, int vl) {
        int warn = plugin.getConfig().getInt("punishments." + check + ".warn", Integer.MAX_VALUE);
        int kick = plugin.getConfig().getInt("punishments." + check + ".kick", Integer.MAX_VALUE);
        int ban = plugin.getConfig().getInt("punishments." + check + ".ban", Integer.MAX_VALUE);
        int banMinutes = plugin.getConfig().getInt("punishments." + check + ".ban_minutes", 0);
        int mute = plugin.getConfig().getInt("punishments." + check + ".mute", Integer.MAX_VALUE);
        int muteMinutes = plugin.getConfig().getInt("punishments." + check + ".mute_minutes", 0);

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
        MessageService ms = plugin.getMessages();

        if (vl == warn) {
            java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("player", player.getName());
            ph.put("check", check);
            ph.put("vl", String.valueOf(vl));
            String warnMsg = ms.format("staff.warn", ph);
            broadcastStaff(warnMsg);
            addLog("WARN", player.getName(), check, vl);
        }

        // Ban primero si aplica
        if (vl >= ban && ban != Integer.MAX_VALUE) {
            String durationStr = banMinutes > 0
                    ? ms.format("sanction.duration_minutes", new java.util.HashMap<String, String>() {{ put("minutes", String.valueOf(banMinutes)); }})
                    : ms.format("sanction.duration_permanent", java.util.Collections.emptyMap());

            // Ejecutar ban
            try {
                Date expires = banMinutes > 0 ? new Date(System.currentTimeMillis() + banMinutes * 60L * 1000L) : null;
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), "Cheating: " + check, expires, "Zeus");
                player.kickPlayer("Cheating: " + check);
            } catch (Exception ignored) {}

            addLog("BAN", player.getName(), check, vl);

            if (plugin.getConfig().getBoolean("messages.broadcast_on_ban", true)) {
                java.util.Map<String,String> ph2 = new java.util.HashMap<String, String>();
                ph2.put("prefix", prefix);
                ph2.put("player", player.getName());
                ph2.put("staff", "Zeus");
                ph2.put("duration", durationStr);
                ph2.put("reason", "Cheating: " + check);
                String pubBan = ms.format("public_mod.ban", ph2);
                if (plugin.getConfig().getBoolean("messages.include_vl_in_broadcasts", false)) {
                    java.util.Map<String,String> addPh = new java.util.HashMap<String, String>();
                    addPh.put("vl", String.valueOf(vl));
                    pubBan += ms.format("sanction.vl_fragment", addPh);
                }
                broadcastAll(pubBan);
            }

            reset(uuid, check);
            return; // Si baneamos, no continuamos con otras sanciones
        }

        // Mute si aplica
        if (vl >= mute && mute != Integer.MAX_VALUE) {
            long until = muteMinutes > 0 ? System.currentTimeMillis() + muteMinutes * 60L * 1000L : Long.MAX_VALUE;
            mutedUntil.put(uuid, until);

            String durationStr = muteMinutes > 0
                    ? ms.format("sanction.duration_minutes", new java.util.HashMap<String, String>() {{ put("minutes", String.valueOf(muteMinutes)); }})
                    : ms.format("sanction.duration_permanent", java.util.Collections.emptyMap());

            addLog("MUTE", player.getName(), check, vl);

            if (plugin.getConfig().getBoolean("messages.broadcast_on_mute", true)) {
                java.util.Map<String,String> ph3 = new java.util.HashMap<String, String>();
                ph3.put("prefix", prefix);
                ph3.put("player", player.getName());
                ph3.put("staff", "Zeus");
                ph3.put("duration", durationStr);
                ph3.put("reason", "Cheating: " + check);
                String pubMute = ms.format("public_mod.mute", ph3);
                if (plugin.getConfig().getBoolean("messages.include_vl_in_broadcasts", false)) {
                    java.util.Map<String,String> addPh = new java.util.HashMap<String, String>();
                    addPh.put("vl", String.valueOf(vl));
                    pubMute += ms.format("sanction.vl_fragment", addPh);
                }
                broadcastAll(pubMute);
            }

            reset(uuid, check);
            // No return; puede seguir acumulando para kick/ban en el futuro
        }

        // Kick si aplica
        if (vl >= kick && kick != Integer.MAX_VALUE) {
            java.util.Map<String,String> ph4 = new java.util.HashMap<String, String>();
            ph4.put("prefix", prefix);
            ph4.put("player", player.getName());
            ph4.put("check", check);
            ph4.put("vl", String.valueOf(vl));
            String kickMsg = ms.format("staff.kick", ph4);
            broadcastStaff(kickMsg);
            player.kickPlayer("Cheating: " + check);
            addLog("KICK", player.getName(), check, vl);

            if (plugin.getConfig().getBoolean("messages.broadcast_on_kick", true)) {
                java.util.Map<String,String> ph5 = new java.util.HashMap<String, String>();
                ph5.put("prefix", prefix);
                ph5.put("player", player.getName());
                ph5.put("staff", "Zeus");
                ph5.put("reason", "Cheating: " + check);
                String pubKick = ms.format("public_mod.kick", ph5);
                if (plugin.getConfig().getBoolean("messages.include_vl_in_broadcasts", false)) {
                    java.util.Map<String,String> addPh = new java.util.HashMap<String, String>();
                    addPh.put("vl", String.valueOf(vl));
                    pubKick += ms.format("sanction.vl_fragment", addPh);
                }
                broadcastAll(pubKick);
            }

            reset(uuid, check);
        }
    }

    private void broadcastStaff(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                if (p.hasPermission("zeus.alerts") && plugin.areAlertsEnabled(p.getUniqueId())) {
                    p.sendMessage(message);
                }
            } catch (Throwable ignored) {}
        }
    }

    private void broadcastAll(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.sendMessage(message); } catch (Throwable ignored) {}
        }
    }

    private void addLog(String type, String player, String check, int vl) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String line = ChatColor.GRAY + "[" + time + "] " + ChatColor.YELLOW + type
                + ChatColor.GRAY + " " + player
                + ChatColor.DARK_GRAY + " - "
                + ChatColor.AQUA + check
                + ChatColor.GRAY + " VL=" + vl;
        logs.addLast(line);
        int maxInMem = plugin.getConfig().getInt("logs.max_in_memory", 500);
        while (logs.size() > maxInMem) logs.pollFirst();

        // Persist to file if enabled
        if (plugin.getConfig().getBoolean("logs.persist_to_file", true)) {
            try {
                File dataDir = plugin.getDataFolder();
                if (!dataDir.exists()) dataDir.mkdirs();
                String fileName = plugin.getConfig().getString("logs.file_name", "logs.txt");
                Path logPath = new File(dataDir, fileName).toPath();
                byte[] data = (stripColors(line) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                if (Files.exists(logPath)) {
                    Files.write(logPath, data, StandardOpenOption.APPEND);
                } else {
                    Files.write(logPath, data, StandardOpenOption.CREATE);
                }
            } catch (Exception ignored) {}
        }
    }

    // Additional detailed logging with metadata (coordinates, cancelled action, etc.)
    public void addDetectionDetail(Player player, String check, int vl, String details) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String base = ChatColor.GRAY + "[" + time + "] " + ChatColor.YELLOW + "DETAIL"
                + ChatColor.GRAY + " " + (player != null ? player.getName() : "?")
                + ChatColor.DARK_GRAY + " - "
                + ChatColor.AQUA + check
                + ChatColor.GRAY + " VL=" + vl
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + (details == null ? "" : details);
        logs.addLast(base);
        int maxInMem = plugin.getConfig().getInt("logs.max_in_memory", 500);
        while (logs.size() > maxInMem) logs.pollFirst();

        if (plugin.getConfig().getBoolean("logs.persist_to_file", true)) {
            try {
                File dataDir = plugin.getDataFolder();
                if (!dataDir.exists()) dataDir.mkdirs();
                String fileName = plugin.getConfig().getString("logs.file_name", "logs.txt");
                Path logPath = new File(dataDir, fileName).toPath();
                byte[] data = (stripColors(base) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                if (Files.exists(logPath)) {
                    Files.write(logPath, data, StandardOpenOption.APPEND);
                } else {
                    Files.write(logPath, data, StandardOpenOption.CREATE);
                }
            } catch (Exception ignored) {}
        }
    }

    public List<String> getLogs(String playerFilter, int max) {
        List<String> out = new ArrayList<>();
        for (String l : logs) {
            if (playerFilter == null || l.contains(" " + playerFilter + " ")) {
                out.add(l);
            }
        }
        int start = Math.max(0, out.size() - max);
        return out.subList(start, out.size());
    }

    // Returns all filtered logs (for pagination)
    public List<String> getLogsFiltered(String playerFilter) {
        List<String> out = new ArrayList<>();
        for (String l : logs) {
            if (playerFilter == null || l.contains(" " + playerFilter + " ")) {
                out.add(l);
            }
        }
        return out;
    }

    private String stripColors(String s) {
        return s.replaceAll("§[0-9A-FK-ORa-fk-or]", "");
    }

    public boolean isMuted(UUID uuid) {
        Long until = mutedUntil.get(uuid);
        if (until == null) return false;
        if (until == Long.MAX_VALUE) return true;
        if (System.currentTimeMillis() <= until) return true;
        // expired
        mutedUntil.remove(uuid);
        return false;
    }

    public Long getMuteUntil(UUID uuid) {
        return mutedUntil.get(uuid);
    }

    public void mute(UUID uuid, int minutes) {
        long until = minutes > 0 ? System.currentTimeMillis() + minutes * 60L * 1000L : Long.MAX_VALUE;
        mutedUntil.put(uuid, until);
    }

    public void unmute(UUID uuid) {
        mutedUntil.remove(uuid);
    }
}