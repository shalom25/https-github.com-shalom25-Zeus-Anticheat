package com.anticheat.core.commands;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.violation.ViolationManager;
import com.anticheat.core.util.MessageService;
// (import removed – Map is unused)
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
 

public class AntiCheatCommand implements CommandExecutor {

    private final AntiCheatPlugin plugin;

    public AntiCheatCommand(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageService ms = plugin.getMessages();
        // String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
        if (!sender.hasPermission("anticheat.admin")) {
            sender.sendMessage(ms.format("command.no_permission", java.util.Collections.emptyMap()));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ms.format("command.usage_main", java.util.Collections.emptyMap()));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                plugin.getMessages().reload();
                sender.sendMessage(ms.format("command.reload_success", java.util.Collections.emptyMap()));
                return true;
            case "status":
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(ms.format("command.player_not_found", java.util.Collections.emptyMap()));
                        return true;
                    }
                    ViolationManager vm = plugin.getViolationManager();
                    String[] checks = new String[]{
                            "Speed","OnGroundSpeed","Fly","BHop","Blink","Step","WaterWalk","Jetpack","NoFall","Timer",
                            "Climb","NoSlow","ElytraFly","BoatSpeed","BoatFly",
                            "Reach","KillAura",
                            "FastPlace","Scaffold","Freecam","AutoCrystal","AutoTrap",
                            "FastBreak",
                            "AutoClicker","AutoTotem","AutoTool",
                            "Inventory","Crash","Baritone","ChorusControl"
                    };
                    StringBuilder sb = new StringBuilder();
                    int total = 0;
                    for (String c : checks) {
                        int vl = vm.getViolation(target.getUniqueId(), c);
                        if (vl > 0) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(c).append("=").append(vl);
                            total += vl;
                        }
                    }
                    if (sb.length() == 0) {
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("player", target.getName());
                        sender.sendMessage(ms.format("command.status_none", ph));
                    } else {
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("player", target.getName());
                        ph.put("list", sb.toString());
                        ph.put("total", String.valueOf(total));
                        sender.sendMessage(ms.format("command.status_with", ph));
                    }
                } else {
                    sender.sendMessage(ms.format("command.usage_status", java.util.Collections.emptyMap()));
                }
                return true;
            case "reset":
                if (args.length >= 2) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(ms.format("command.player_not_found", java.util.Collections.emptyMap()));
                        return true;
                    }
                    ViolationManager vm = plugin.getViolationManager();
                    if (args.length >= 3) {
                        String which = args[2];
                        if (which.equalsIgnoreCase("all")) {
                            vm.resetAll(target.getUniqueId());
                            java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                            ph.put("player", target.getName());
                            sender.sendMessage(ms.format("command.reset_all_success", ph));
                        } else {
                            vm.reset(target.getUniqueId(), which);
                            java.util.Map<String,String> ph2 = new java.util.HashMap<String, String>();
                            ph2.put("player", target.getName());
                            ph2.put("check", which);
                            sender.sendMessage(ms.format("command.reset_check_success", ph2));
                        }
                    } else {
                        vm.resetAll(target.getUniqueId());
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("player", target.getName());
                        sender.sendMessage(ms.format("command.reset_all_success", ph));
                    }
                } else {
                    sender.sendMessage(ms.format("command.usage_reset", java.util.Collections.emptyMap()));
                }
                return true;
            default:
                sender.sendMessage(ms.format("command.usage_main", java.util.Collections.emptyMap()));
                return true;
        }
    }
}