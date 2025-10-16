package com.anticheat.core.gui;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.violation.ViolationManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class MenuAlertsGUI implements Listener {

    private final AntiCheatPlugin plugin;
    private final MenuPlayerActionsGUI actionsGUI;
    private final java.util.Map<java.util.UUID, java.util.List<java.util.UUID>> openLists = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Integer> openPage = new java.util.concurrent.ConcurrentHashMap<>();

    public MenuAlertsGUI(AntiCheatPlugin plugin, MenuPlayerActionsGUI actionsGUI) {
        this.plugin = plugin;
        this.actionsGUI = actionsGUI;
    }

    public void openFor(Player viewer) {
        java.util.List<java.util.UUID> flagged = collectFlagged();
        openLists.put(viewer.getUniqueId(), flagged);
        openPage.put(viewer.getUniqueId(), 0);
        render(viewer);
    }

    private java.util.List<java.util.UUID> collectFlagged() {
        java.util.List<java.util.UUID> flagged = new java.util.ArrayList<>();
        ViolationManager vm = plugin.getViolationManager();
        java.util.List<Player> online = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort((a, b) -> Integer.compare(sum(vm.getAll(b.getUniqueId())), sum(vm.getAll(a.getUniqueId()))));
        for (Player p : online) {
            if (p == null) continue;
            if (plugin.isExempt(p)) continue; // no mostrar exentos totales
            int total = sum(vm.getAll(p.getUniqueId()));
            if (total > 0) flagged.add(p.getUniqueId());
        }
        return flagged;
    }

    private void render(Player viewer) {
        java.util.UUID vid = viewer.getUniqueId();
        java.util.List<java.util.UUID> list = openLists.getOrDefault(vid, java.util.Collections.emptyList());
        int page = openPage.getOrDefault(vid, 0);
        int size = 27;
        String title = plugin.getMessages().get("menu.alerts.title");
        Inventory inv = Bukkit.createInventory(null, size, title);

        int itemsPerPage = 25; // reservar 18 y 26 para navegación
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, list.size());
        int slot = 0;
        ViolationManager vm = plugin.getViolationManager();
        for (int i = start; i < end; i++) {
            java.util.UUID uuid = list.get(i);
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) continue;
            ItemStack skull = makeSkull(target);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                Map<String, Integer> map = vm.getAll(target.getUniqueId());
                int total = sum(map);
                meta.setDisplayName(ChatColor.AQUA + target.getName() + ChatColor.GRAY + " • VL " + total);
                List<String> lore = buildLore(map);
                meta.setLore(lore);
                skull.setItemMeta(meta);
            }
            while (slot == 18 || slot == 26) slot++;
            if (slot >= size) break;
            inv.setItem(slot++, skull);
        }

        int maxPage = (list.size() == 0) ? 0 : (list.size() - 1) / itemsPerPage;
        // Prev button
        if (page > 0) {
            ItemStack prev = new ItemStack(safeMaterial("ARROW") != null ? safeMaterial("ARROW") : Material.STONE);
            ItemMeta pMeta = prev.getItemMeta();
            if (pMeta != null) {
                pMeta.setDisplayName(plugin.getMessages().get("menu.alerts.prev_name"));
                prev.setItemMeta(pMeta);
            }
            inv.setItem(18, prev);
        } else {
            Material m = safeMaterial("GRAY_STAINED_GLASS_PANE");
            if (m == null) m = safeMaterial("STAINED_GLASS_PANE");
            if (m == null) m = (safeMaterial("BARRIER") != null ? safeMaterial("BARRIER") : Material.STONE);
            ItemStack prevDis = new ItemStack(m);
            ItemMeta pdMeta = prevDis.getItemMeta();
            if (pdMeta != null) {
                pdMeta.setDisplayName(plugin.getMessages().get("menu.alerts.prev_disabled"));
                prevDis.setItemMeta(pdMeta);
            }
            inv.setItem(18, prevDis);
        }

        // Next button
        if (page < maxPage) {
            ItemStack next = new ItemStack(safeMaterial("ARROW") != null ? safeMaterial("ARROW") : Material.STONE);
            ItemMeta nMeta = next.getItemMeta();
            if (nMeta != null) {
                nMeta.setDisplayName(plugin.getMessages().get("menu.alerts.next_name"));
                next.setItemMeta(nMeta);
            }
            inv.setItem(26, next);
        } else {
            Material m2 = safeMaterial("GRAY_STAINED_GLASS_PANE");
            if (m2 == null) m2 = safeMaterial("STAINED_GLASS_PANE");
            if (m2 == null) m2 = (safeMaterial("BARRIER") != null ? safeMaterial("BARRIER") : Material.STONE);
            ItemStack nextDis = new ItemStack(m2);
            ItemMeta ndMeta = nextDis.getItemMeta();
            if (ndMeta != null) {
                ndMeta.setDisplayName(plugin.getMessages().get("menu.alerts.next_disabled"));
                nextDis.setItemMeta(ndMeta);
            }
            inv.setItem(26, nextDis);
        }

        viewer.openInventory(inv);
    }

    private ItemStack makeSkull(Player owner) {
        Material mat = safeMaterial("PLAYER_HEAD");
        if (mat == null) mat = safeMaterial("SKULL_ITEM");
        if (mat == null) mat = safeMaterial("SKULL");
        ItemStack skull;
        if (mat != null) {
            // Para versiones legacy, usar data=3 para cabeza de jugador
            if (mat.name().equals("SKULL_ITEM") || mat.name().equals("SKULL")) {
                skull = new ItemStack(mat, 1, (short) 3);
            } else {
                skull = new ItemStack(mat, 1);
            }
        } else {
            skull = new ItemStack(Material.valueOf("STONE"));
        }
        ItemMeta im = skull.getItemMeta();
        if (im instanceof SkullMeta) {
            SkullMeta sm = (SkullMeta) im;
            try {
                java.lang.reflect.Method m = sm.getClass().getMethod("setOwningPlayer", org.bukkit.OfflinePlayer.class);
                m.invoke(sm, owner);
            } catch (Throwable t) {
                try {
                    java.lang.reflect.Method m2 = sm.getClass().getMethod("setOwner", String.class);
                    m2.invoke(sm, owner.getName());
                } catch (Throwable ignored) {}
            }
            skull.setItemMeta(sm);
        }
        return skull;
    }

    private Material safeMaterial(String name) {
        try { return Material.valueOf(name); } catch (IllegalArgumentException e) { return null; }
    }

    private int sum(Map<String, Integer> map) {
        int t = 0;
        for (int v : map.values()) t += v;
        return t;
    }

    private List<String> buildLore(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
        List<String> lore = new ArrayList<>();
        String header = plugin.getMessages().get("menu.alerts.lore_header");
        lore.add(header);
        int limit = Math.min(5, entries.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            String line = ChatColor.DARK_GRAY + "- " + ChatColor.YELLOW + e.getKey() + ChatColor.GRAY + ": " + e.getValue();
            lore.add(line);
        }
        if (entries.size() > limit) {
            lore.add(plugin.getMessages().get("menu.alerts.lore_more"));
        }
        return lore;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView() == null) return;
        String title = event.getView().getTitle();
        String expected = plugin.getMessages().get("menu.alerts.title");
        if (!expected.equals(title)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;
        ItemMeta im = clicked.getItemMeta();
        Player viewer = (Player) event.getWhoClicked();
        if (im == null || !im.hasDisplayName()) return;

        String name = im.getDisplayName();
        if (name.equals(plugin.getMessages().get("menu.alerts.prev_name"))) {
            int page = openPage.getOrDefault(viewer.getUniqueId(), 0);
            if (page > 0) {
                openPage.put(viewer.getUniqueId(), page - 1);
                render(viewer);
            }
            return;
        }
        if (name.equals(plugin.getMessages().get("menu.alerts.next_name"))) {
            java.util.List<java.util.UUID> list = openLists.getOrDefault(viewer.getUniqueId(), java.util.Collections.emptyList());
            int page = openPage.getOrDefault(viewer.getUniqueId(), 0);
            int itemsPerPage = 25;
            int maxPage = (list.size() == 0) ? 0 : (list.size() - 1) / itemsPerPage;
            if (page < maxPage) {
                openPage.put(viewer.getUniqueId(), page + 1);
                render(viewer);
            }
            return;
        }

        if (!(im instanceof SkullMeta)) return;
        SkullMeta sm = (SkullMeta) im;
        Player target = null;
        try {
            java.lang.reflect.Method m = sm.getClass().getMethod("getOwningPlayer");
            Object r = m.invoke(sm);
            if (r instanceof OfflinePlayer) {
                OfflinePlayer op = (OfflinePlayer) r;
                if (op != null) target = op.isOnline() ? op.getPlayer() : Bukkit.getPlayer(op.getName());
            }
        } catch (Throwable ignored) {}
        if (target == null) {
            try {
                java.lang.reflect.Method m2 = sm.getClass().getMethod("getOwner");
                Object n = m2.invoke(sm);
                if (n instanceof String) target = Bukkit.getPlayerExact((String) n);
            } catch (Throwable ignored) {}
        }
        if (target != null) {
            actionsGUI.openFor(viewer, target);
        }
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player viewer = (Player) event.getPlayer();
        String expected = plugin.getMessages().get("menu.alerts.title");
        if (expected.equals(event.getView().getTitle())) {
            openLists.remove(viewer.getUniqueId());
            openPage.remove(viewer.getUniqueId());
        }
    }
}