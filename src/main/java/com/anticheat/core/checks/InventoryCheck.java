package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
// PlayerSwapHandItemsEvent does not exist in 1.8, omitted for compatibility
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InventoryCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Deque<Long>> switches = new HashMap<>();
    private final Map<UUID, Long> lastDamage = new HashMap<>();
    private final Map<UUID, Integer> invMoveTicks = new HashMap<>();

    // Compatibility with versions without offhand: detect via inventory clicks

    public InventoryCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player p = (Player) event.getEntity();
        // Bypass: OPs or configured permissions
        if (plugin.isExempt(p)) return;
        UUID id = p.getUniqueId();
        lastDamage.put(id, System.currentTimeMillis());
    }

    // onSwap method removed for 1.8 compatibility (no SwapHand event)

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        // Bypass: OPs or configured permissions
        if (plugin.isExempt(player)) return;
        boolean autototemEnabled = plugin.getConfig().getBoolean("checks.inventory.autototem.enabled", true);
        ItemStack clicked = event.getCurrentItem();
        if (autototemEnabled && isTotem(clicked)) {
            long now = System.currentTimeMillis();
            long last = lastDamage.getOrDefault(player.getUniqueId(), 0L);
            int window = plugin.getConfig().getInt("checks.inventory.autototem.switch_window_ms", 300);
            if (now - last <= window) {
                int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "AutoTotem", 1);
                boolean cancel = plugin.getConfig().getBoolean("checks.inventory.autototem.cancel_on_detect", false);
                if (cancel) event.setCancelled(true);
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.autototem", ph));
            }
        }

        // Crash detection for books with excessive content
        boolean crashBookEnabled = plugin.getConfig().getBoolean("checks.crash.book.enabled", true);
        if (crashBookEnabled && clicked != null && clicked.getType() != null) {
            String type = clicked.getType().name();
            if ("WRITTEN_BOOK".equalsIgnoreCase(type) || "BOOK_AND_QUILL".equalsIgnoreCase(type)) {
                if (clicked.hasItemMeta() && clicked.getItemMeta() instanceof BookMeta) {
                    BookMeta meta = (BookMeta) clicked.getItemMeta();
                    java.util.List<String> pages = meta.getPages();
                    int maxPages = plugin.getConfig().getInt("checks.crash.book.max_pages", 50);
                    int maxChars = plugin.getConfig().getInt("checks.crash.book.max_chars_per_page", 3500);
                    boolean remove = plugin.getConfig().getBoolean("checks.crash.book.remove_on_detect", true);
                    boolean cancelCrash = plugin.getConfig().getBoolean("checks.crash.book.cancel_on_detect", true);
                    boolean tooManyPages = pages != null && pages.size() > maxPages;
                    boolean tooLargePage = false;
                    if (pages != null) {
                        for (String p : pages) {
                            if (p != null && p.length() > maxChars) { tooLargePage = true; break; }
                        }
                    }
                    if (tooManyPages || tooLargePage) {
                        int vl = plugin.getViolationManager().addViolation(player.getUniqueId(), "Crash", 1);
                        if (cancelCrash) event.setCancelled(true);
                        if (remove) {
                            event.setCurrentItem(null);
                        }
                        // Detailed logging of crash attempt via book
                        try {
                            int pagesCount = pages != null ? pages.size() : 0;
                            int maxPageLen = 0;
                            if (pages != null) {
                                for (String p : pages) {
                                    if (p != null && p.length() > maxPageLen) maxPageLen = p.length();
                                }
                            }
                            org.bukkit.Location loc = player.getLocation();
                            String invType = event.getInventory() != null && event.getInventory().getType() != null ? event.getInventory().getType().name() : "UNKNOWN";
                            plugin.getViolationManager().addDetectionDetail(
                                    player, "Crash", vl,
                                    String.format(
                                            "book pages=%d maxPageLen=%d tooMany=%s tooLargePage=%s inv=%s cancel=%s pos=(%.2f,%.2f,%.2f)",
                                            pagesCount, maxPageLen,
                                            String.valueOf(tooManyPages), String.valueOf(tooLargePage),
                                            invType, String.valueOf(cancelCrash),
                                            loc.getX(), loc.getY(), loc.getZ()
                                    )
                            );
                        } catch (Throwable ignored) {}
                        MessageService ms = plugin.getMessages();
                        String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                        java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                        ph.put("prefix", prefix);
                        ph.put("vl", String.valueOf(vl));
                        player.sendMessage(ms.format("player.crash", ph));
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // Bypass: OPs or configured permissions
        if (plugin.isExempt(player)) return;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> deque = switches.computeIfAbsent(id, k -> new ArrayDeque<>());
        deque.addLast(now);
        while (!deque.isEmpty() && now - deque.peekFirst() > 1000) deque.pollFirst();
        boolean autotoolEnabled = plugin.getConfig().getBoolean("checks.inventory.autotool.enabled", true);
        int maxSwitch = plugin.getConfig().getInt("checks.inventory.autotool.max_switch_per_sec", 10);
        if (autotoolEnabled && deque.size() > maxSwitch) {
            int vl = plugin.getViolationManager().addViolation(id, "AutoTool", 1);
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.autotool", ph));
            }
        }
    }

    private boolean isTotem(ItemStack item) {
        return item != null && item.getType() != null && "TOTEM_OF_UNDYING".equalsIgnoreCase(item.getType().name());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // InventoryMove detection: movement while inventory is open
        if (!plugin.getConfig().getBoolean("checks.inventory.move.enabled", true)) return;
        if (event.getFrom().toVector().equals(event.getTo().toVector())) return;
        Player player = event.getPlayer();
        // Bypass: OPs or configured permissions
        if (plugin.isExempt(player)) return;
        org.bukkit.inventory.InventoryView view = player.getOpenInventory();
        if (view == null) return;
        InventoryType type = view.getType();
        // Allow personal inventory and creative
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) return;
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double maxHz = plugin.getConfig().getDouble("checks.inventory.move.max_horizontal_when_open", 0.12);
        int threshold = plugin.getConfig().getInt("checks.inventory.move.threshold_ticks", 6);
        boolean cancel = plugin.getConfig().getBoolean("checks.inventory.move.cancel_on_detect", true);
        if (horizontal > maxHz) {
            UUID id = player.getUniqueId();
            int t = invMoveTicks.getOrDefault(id, 0) + 1;
            invMoveTicks.put(id, t);
            if (t >= threshold) {
                int vl = plugin.getViolationManager().addViolation(id, "Inventory", 1);
                if (cancel) event.setTo(event.getFrom());
                // Detailed logging of movement with inventory open
                try {
                    org.bukkit.Location loc = event.getTo();
                    String invTypeName = type != null ? type.name() : "UNKNOWN";
                    plugin.getViolationManager().addDetectionDetail(
                            player, "Inventory", vl,
                            String.format(
                                    "inv=%s hz=%.3f ticks=%d cancel=%s pos=(%.2f,%.2f,%.2f)",
                                    invTypeName, horizontal, t, String.valueOf(cancel),
                                    loc.getX(), loc.getY(), loc.getZ()
                            )
                    );
                } catch (Throwable ignored) {}
                MessageService ms = plugin.getMessages();
                String prefix = org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "[AntiCheat] "));
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.inventory", ph));
                invMoveTicks.put(id, 0);
            }
        } else {
            invMoveTicks.put(player.getUniqueId(), 0);
        }
    }
}