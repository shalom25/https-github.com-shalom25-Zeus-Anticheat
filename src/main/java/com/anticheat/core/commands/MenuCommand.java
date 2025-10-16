package com.anticheat.core.commands;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.gui.MenuAlertsGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MenuCommand implements CommandExecutor {
    private final AntiCheatPlugin plugin;
    private final MenuAlertsGUI gui;

    public MenuCommand(AntiCheatPlugin plugin, MenuAlertsGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
        com.anticheat.core.util.MessageService ms = plugin.getMessages();
        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
        ph.put("prefix", prefix);
        if (!(sender instanceof Player)) {
            sender.sendMessage(ms.format("menu.alerts.only_ingame", ph));
            return true;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("zeus.menu.alerts")) {
            p.sendMessage(ms.format("menu.alerts.no_permission", ph));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("alerts")) {
            gui.openFor(p);
            return true;
        }
        p.sendMessage(ms.format("menu.alerts.usage", ph));
        return true;
    }
}