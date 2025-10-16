package com.anticheat.core.gui;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.MessageService;
import com.anticheat.core.violation.ViolationManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MenuPlayerActionsGUI implements Listener {
    private final AntiCheatPlugin plugin;
    private final Map<UUID, UUID> openTargets = new java.util.concurrent.ConcurrentHashMap<>();
    private MenuAlertsGUI alertsGUI;

    public MenuPlayerActionsGUI(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void setAlertsGUI(MenuAlertsGUI alertsGUI) {
        this.alertsGUI = alertsGUI;
    }

    public void openFor(Player viewer, Player target) {
        MessageService ms = plugin.getMessages();
        String title = ms.format("menu.actions.title", new java.util.HashMap<String, String>() {{ put("player", target.getName()); }});
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Action items
        ItemStack tp = new ItemStack(safeMaterial("ENDER_PEARL"));
        ItemMeta tpMeta = tp.getItemMeta();
        if (tpMeta != null) {
            tpMeta.setDisplayName(ms.get("menu.actions.teleport_name"));
            tp.setItemMeta(tpMeta);
        }

        ItemStack reset = new ItemStack(safeMaterial("BARRIER"));
        ItemMeta resetMeta = reset.getItemMeta();
        if (resetMeta != null) {
            resetMeta.setDisplayName(ms.get("menu.actions.reset_name"));
            reset.setItemMeta(resetMeta);
        }

        ItemStack logs = new ItemStack(safeMaterial("BOOK"));
        ItemMeta logsMeta = logs.getItemMeta();
        if (logsMeta != null) {
            logsMeta.setDisplayName(ms.get("menu.actions.logs_name"));
            logs.setItemMeta(logsMeta);
        }

        inv.setItem(10, tp);
        inv.setItem(13, reset);
        inv.setItem(16, logs);

        // Back button
        ItemStack back = new ItemStack(safeMaterial("ARROW"));
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ms.get("menu.actions.back_name"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(22, back);

        openTargets.put(viewer.getUniqueId(), target.getUniqueId());
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player viewer = (Player) event.getWhoClicked();
        UUID tgtId = openTargets.get(viewer.getUniqueId());
        if (tgtId == null) return;
        Player target = Bukkit.getPlayer(tgtId);
        if (target == null) return;

        String expected = plugin.getMessages().format("menu.actions.title", new java.util.HashMap<String, String>() {{ put("player", target.getName()); }});
        if (!expected.equals(event.getView().getTitle())) return;

        event.setCancelled(true);
        ItemStack current = event.getCurrentItem();
        if (current == null) return;
        ItemMeta meta = current.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String name = meta.getDisplayName();
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
        MessageService ms = plugin.getMessages();
        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
        ph.put("prefix", prefix);
        ph.put("player", target.getName());

        // Teleport
        if (name.equals(ms.get("menu.actions.teleport_name"))) {
            if (!viewer.hasPermission("zeus.menu.teleport")) {
                viewer.sendMessage(ms.format("command.no_permission", ph));
                return;
            }
            try {
                viewer.teleport(target.getLocation());
                viewer.sendMessage(ms.format("menu.actions.tp_done", ph));
            } catch (Throwable t) {
                viewer.sendMessage(prefix + ChatColor.RED + " Teleport failed.");
            }
            return;
        }

        // Reset VL
        if (name.equals(ms.get("menu.actions.reset_name"))) {
            if (!viewer.hasPermission("zeus.menu.resetvl")) {
                viewer.sendMessage(ms.format("command.no_permission", ph));
                return;
            }
            ViolationManager vm = plugin.getViolationManager();
            vm.resetAll(target.getUniqueId());
            viewer.sendMessage(ms.format("command.reset_all_success", ph));
            return;
        }

        // Ver últimos logs
        if (name.equals(ms.get("menu.actions.logs_name"))) {
            if (!viewer.hasPermission("zeus.menu.logs")) {
                viewer.sendMessage(ms.format("command.no_permission", ph));
                return;
            }
            ViolationManager vm = plugin.getViolationManager();
            java.util.List<String> all = vm.getLogsFiltered(target.getName());
            if (all.isEmpty()) {
                viewer.sendMessage(ms.format("zeus.logs_empty", ph));
            } else {
                viewer.sendMessage(ms.format("zeus.logs_header", ph));
                int count = 10;
                int end = all.size();
                int start = Math.max(0, end - count);
                java.util.List<String> slice = all.subList(start, end);
                for (String line : slice) viewer.sendMessage(line);
            }
        }

        // Back
        if (name.equals(ms.get("menu.actions.back_name"))) {
            if (alertsGUI != null) {
                alertsGUI.openFor(viewer);
            } else {
                viewer.closeInventory();
            }
            return;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player viewer = (Player) event.getPlayer();
        UUID tgtId = openTargets.get(viewer.getUniqueId());
        if (tgtId == null) return;
        Player target = Bukkit.getPlayer(tgtId);
        if (target == null) {
            openTargets.remove(viewer.getUniqueId());
            return;
        }
        String expected = plugin.getMessages().format("menu.actions.title", new java.util.HashMap<String, String>() {{ put("player", target.getName()); }});
        if (expected.equals(event.getView().getTitle())) {
            openTargets.remove(viewer.getUniqueId());
        }
    }

    private Material safeMaterial(String name) {
        try { return Material.valueOf(name); } catch (IllegalArgumentException e) { return Material.STONE; }
    }
}