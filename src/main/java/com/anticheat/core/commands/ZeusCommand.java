package com.anticheat.core.commands;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.MessageService;
import com.anticheat.core.violation.ViolationManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.ChatColor;

import java.util.List;

public class ZeusCommand implements CommandExecutor {

    private final AntiCheatPlugin plugin;

    public ZeusCommand(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageService ms = plugin.getMessages();
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[Zeus] "));

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if (!sender.hasPermission("zeus.help")) {
                sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
                return true;
            }
            {
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                sender.sendMessage(ms.format("zeus.help_header", ph));
            }
            sender.sendMessage(ms.format("zeus.help_reload", java.util.Collections.emptyMap()));
            sender.sendMessage(ms.format("zeus.help_logs", java.util.Collections.emptyMap()));
            sender.sendMessage(ms.format("zeus.help_last", java.util.Collections.emptyMap()));
            sender.sendMessage(ms.format("zeus.help_alerts", java.util.Collections.emptyMap()));
            sender.sendMessage(ms.format("zeus.help_mute", java.util.Collections.emptyMap()));
            sender.sendMessage(ms.format("zeus.help_unmute", java.util.Collections.emptyMap()));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("zeus.reload")) {
                    sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getMessages().reload();
                sender.sendMessage(ms.format("command.reload_success", java.util.Collections.emptyMap()));
                return true;

            case "logs":
                if (!sender.hasPermission("zeus.logs")) {
                    sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
                    return true;
                }
                String targetName = null;
                int count = 10;
                int page = 1;
                if (args.length >= 2) {
                    // If the second argument is a number, it's a count; otherwise it's a player name
                    try {
                        count = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException e) {
                        targetName = args[1];
                    }
                }
                if (args.length >= 3) {
                    // If the second argument is a player, the third can be count or page
                    try {
                        int val = Integer.parseInt(args[2]);
                        if (targetName == null) {
                            count = Math.max(1, val);
                        } else {
                            page = Math.max(1, val);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (args.length >= 4) {
                    try { page = Math.max(1, Integer.parseInt(args[3])); } catch (NumberFormatException ignored) {}
                }
                ViolationManager vm = plugin.getViolationManager();
                List<String> all = vm.getLogsFiltered(targetName);
                if (all.isEmpty()) {
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    sender.sendMessage(ms.format("zeus.logs_empty", ph));
                } else {
                    int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) count));
                    page = Math.min(page, totalPages);
                    int end = all.size();
                    int start = Math.max(0, end - (page * count));
                    int from = Math.max(0, end - ((page - 1) * count));
                    List<String> slice = all.subList(start, from);
                    {
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("prefix", prefix);
                        ph.put("page", String.valueOf(page));
                        ph.put("pages", String.valueOf(totalPages));
                        sender.sendMessage(ms.format("zeus.logs_page", ph));
                    }
                    for (String line : slice) sender.sendMessage(line);
                }
                return true;

            case "last":
                if (!sender.hasPermission("zeus.logs")) {
                    sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
                    return true;
                }
                String who = null;
                if (args.length >= 2) {
                    who = args[1];
                } else if (sender instanceof Player) {
                    who = ((Player) sender).getName();
                }
                if (who == null) {
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    sender.sendMessage(ms.format("zeus.last_usage", ph));
                    return true;
                }
                {
                    ViolationManager vm2 = plugin.getViolationManager();
                    List<String> all2 = vm2.getLogsFiltered(who);
                    String lastDetail = null;
                    for (int i = all2.size() - 1; i >= 0; i--) {
                        String l = all2.get(i);
                        if (l.contains("DETAIL")) { lastDetail = l; break; }
                    }
                    if (lastDetail == null) {
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("prefix", prefix);
                        ph.put("player", who);
                        sender.sendMessage(ms.format("zeus.last_none", ph));
                    } else {
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("prefix", prefix);
                        ph.put("player", who);
                        sender.sendMessage(ms.format("zeus.last_header", ph));
                        sender.sendMessage(lastDetail);
                    }
                }
                return true;

            case "alerts":
                if (!sender.hasPermission("zeus.alerts")) {
                    sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    sender.sendMessage(ms.format("zeus.player_only", ph));
                    return true;
                }
                Player p = (Player) sender;
                boolean enabled = plugin.toggleAlerts(p.getUniqueId());
                if (enabled) {
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    sender.sendMessage(ms.format("zeus.alerts_enabled", ph));
                } else {
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    sender.sendMessage(ms.format("zeus.alerts_disabled", ph));
                }
                return true;

            case "mute":
                if (!sender.hasPermission("zeus.mute")) {
                    sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
                    return true;
                }
                if (args.length < 2) {
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    sender.sendMessage(ms.format("zeus.mute_usage", ph));
                    return true;
                }
                String muteTarget = args[1];
                int minutes = 0;
                if (args.length >= 3) {
                    try { minutes = Math.max(0, Integer.parseInt(args[2])); } catch (NumberFormatException ignored) {}
                }
                OfflinePlayer op = null;
                Player onlineCandidate = Bukkit.getPlayerExact(muteTarget);
                if (onlineCandidate != null) {
                    op = onlineCandidate;
                }
                if (op == null || op.getUniqueId() == null) {
                    for (OfflinePlayer candidate : Bukkit.getOfflinePlayers()) {
                        if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(muteTarget)) {
                            op = candidate;
                            break;
                        }
                    }
                }
                if (op == null || op.getUniqueId() == null) {
                    op = Bukkit.getOfflinePlayer(java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + muteTarget).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
                if (op == null || op.getUniqueId() == null) {
                    sender.sendMessage(ms.format("command.player_not_found", java.util.Collections.emptyMap()));
                    return true;
                }
                plugin.getViolationManager().mute(op.getUniqueId(), minutes);
                String durationStr;
                if (minutes > 0) {
                    java.util.Map<String,String> phDur = new java.util.HashMap<String, String>();
                    phDur.put("minutes", String.valueOf(minutes));
                    durationStr = ms.format("sanction.duration_minutes", phDur);
                } else {
                    durationStr = ms.format("sanction.duration_permanent", java.util.Collections.emptyMap());
                }
                {
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    ph.put("player", muteTarget);
                    ph.put("duration", durationStr);
                    sender.sendMessage(ms.format("zeus.mute_success", ph));
                }
                // Optional broadcast is handled by ViolationManager when automatic; here we only confirm to staff
                return true;

            case "unmute":
                if (!sender.hasPermission("zeus.unmute")) {
                    sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
                    return true;
                }
                if (args.length < 2) {
                    java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                    ph.put("prefix", prefix);
                    sender.sendMessage(ms.format("zeus.unmute_usage", ph));
                    return true;
                }
                String unmuteTarget = args[1];
                OfflinePlayer op2 = null;
                Player onlineCandidate2 = Bukkit.getPlayerExact(unmuteTarget);
                if (onlineCandidate2 != null) {
                    op2 = onlineCandidate2;
                }
                if (op2 == null || op2.getUniqueId() == null) {
                    for (OfflinePlayer candidate : Bukkit.getOfflinePlayers()) {
                        if (candidate.getName() != null && candidate.getName().equalsIgnoreCase(unmuteTarget)) {
                            op2 = candidate;
                            break;
                        }
                    }
                }
                if (op2 == null || op2.getUniqueId() == null) {
                    op2 = Bukkit.getOfflinePlayer(java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + unmuteTarget).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                }
                if (op2 == null || op2.getUniqueId() == null) {
                    sender.sendMessage(ms.format("command.player_not_found", java.util.Collections.emptyMap()));
                    return true;
                }
                plugin.getViolationManager().unmute(op2.getUniqueId());
                {
                    java.util.Map<String,String> ph2 = new java.util.HashMap<String, String>();
                    ph2.put("prefix", prefix);
                    ph2.put("player", unmuteTarget);
                    sender.sendMessage(ms.format("zeus.unmute_success", ph2));
                }
                return true;

            default:
                // Fallback to help
                if (sender.hasPermission("zeus.help")) {
                    {
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("prefix", prefix);
                        sender.sendMessage(ms.format("zeus.help_header", ph));
                    }
                    sender.sendMessage(ms.format("zeus.help_reload", java.util.Collections.emptyMap()));
                    sender.sendMessage(ms.format("zeus.help_logs", java.util.Collections.emptyMap()));
                    sender.sendMessage(ms.format("zeus.help_alerts", java.util.Collections.emptyMap()));
                    sender.sendMessage(ms.format("zeus.help_mute", java.util.Collections.emptyMap()));
                    sender.sendMessage(ms.format("zeus.help_unmute", java.util.Collections.emptyMap()));
                } else {
                    sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
                }
                return true;
        }
    }
}