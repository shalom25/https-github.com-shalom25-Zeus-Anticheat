package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import com.anticheat.core.util.MessageService;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClickCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Deque<Long>> clickTimes = new HashMap<>();
    private final Map<UUID, Long> lastBlockBreakTime = new HashMap<>();
    

    public ClickCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // Bypass: OPs o permisos configurados
        if (plugin.isExempt(player)) return;
        Action a = event.getAction();
        if (a == Action.PHYSICAL) return;
        // Consider only left-click (LMB); ignore right-click to reduce false positives
        if (a != Action.LEFT_CLICK_AIR && a != Action.LEFT_CLICK_BLOCK) return;

        // Ignore clicks on blocks (breaking/mining) to reduce false positives when breaking grass
        boolean ignoreBlockClicks = plugin.getConfig().getBoolean("checks.click.autoclicker.ignore_block_clicks", true);
        if (ignoreBlockClicks && a == Action.LEFT_CLICK_BLOCK) return;

        // Ignore clicks very close to a block break (when continuously mining)
        ItemStack hand = player.getItemInHand();
        int effLevel = hand != null ? hand.getEnchantmentLevel(Enchantment.DIG_SPEED) : 0;
        int hasteLevel = 0;
        for (PotionEffect pe : player.getActivePotionEffects()) {
            if (pe.getType() == PotionEffectType.FAST_DIGGING) {
                hasteLevel = pe.getAmplifier() + 1;
                break;
            }
        }
        long baseIgnoreMs = plugin.getConfig().getLong("checks.click.autoclicker.ignore_recent_block_break_ms", 220);
        boolean considerEffClicks = plugin.getConfig().getBoolean("checks.click.autoclicker.consider_efficiency", true);
        int bonusEffMs = plugin.getConfig().getInt("checks.click.autoclicker.ignore_recent_bonus_eff_ms", 30);
        int bonusHasteMs = plugin.getConfig().getInt("checks.click.autoclicker.ignore_recent_bonus_haste_ms", 40);
        int toolBonusMs = plugin.getConfig().getInt("checks.click.autoclicker.ignore_recent_tool_bonus_ms", 50);
        boolean isMiningTool = hand != null && (hand.getType().name().contains("SPADE") || hand.getType().name().contains("PICKAXE") || hand.getType().name().contains("AXE"));
        long ignoreRecentMs = baseIgnoreMs + (considerEffClicks ? (effLevel * bonusEffMs + hasteLevel * bonusHasteMs) : 0) + (isMiningTool ? toolBonusMs : 0);
        UUID id = player.getUniqueId();
        Long lastBreak = lastBlockBreakTime.get(id);
        long now = System.currentTimeMillis();
        if (lastBreak != null && (now - lastBreak) <= ignoreRecentMs) {
            return;
        }
        
        Deque<Long> deque = clickTimes.computeIfAbsent(id, k -> new ArrayDeque<>());
        deque.addLast(now);
        while (!deque.isEmpty() && now - deque.peekFirst() > 1000) deque.pollFirst();

        boolean autoclickerEnabled = plugin.getConfig().getBoolean("checks.click.autoclicker.enabled", true);
        int maxCps = plugin.getConfig().getInt("checks.click.autoclicker.max_cps", 20);
        if (autoclickerEnabled && deque.size() > maxCps) {
            int vl = plugin.getViolationManager().addViolation(id, "AutoClicker", 1);
            event.setCancelled(true);
            // Detailed logging for diagnosis
            try {
                Block clicked = event.getClickedBlock();
                ItemStack item = player.getItemInHand();
                Material itemMat = item != null ? item.getType() : Material.AIR;
                plugin.getViolationManager().addDetectionDetail(
                        player,
                        "AutoClicker",
                        vl,
                        String.format(
                                "action=%s block=%s item=%s cps=%d cancel=%s recentBreakDelta=%dms ignoreRecentMs=%d eff=%d haste=%d", 
                                a.name(),
                                clicked != null ? clicked.getType().name() : "NONE",
                                itemMat.name(),
                                deque.size(),
                                String.valueOf(true),
                                lastBreak != null ? (now - lastBreak) : -1,
                                ignoreRecentMs,
                                effLevel,
                                hasteLevel
                        )
                );
            } catch (Throwable ignored) {}
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = color("messages.prefix");
                Map<String, String> ph = new HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.autoclicker", ph));
            }
        }

        
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        lastBlockBreakTime.put(id, System.currentTimeMillis());
    }

    

    private String color(String path) {
        String prefix = plugin.getConfig().getString(path, "[AntiCheat] ");
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    
}