package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import com.anticheat.core.util.MessageService;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;

public class BreakCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Deque<Long>> breakTimes = new HashMap<>();

    public BreakCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // Bypass: OPs o permisos configurados
        if (plugin.isExempt(player)) return;

        // Ignorar bloques blandos (plantas, hojas, nieve, etc.) configurables para reducir falsos positivos
        java.util.List<String> softBlocks = plugin.getConfig().getStringList("checks.break.fastbreak.soft_blocks_ignore");
        if (softBlocks != null && !softBlocks.isEmpty()) {
            Material m = event.getBlock().getType();
            if (softBlocks.contains(m.name())) {
                return;
            }
        }

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> deque = breakTimes.computeIfAbsent(id, k -> new ArrayDeque<>());
        deque.addLast(now);
        while (!deque.isEmpty() && now - deque.peekFirst() > 1000) deque.pollFirst();

        boolean fastbreakEnabled = plugin.getConfig().getBoolean("checks.break.fastbreak.enabled", true);
        int baseMaxPerSec = plugin.getConfig().getInt("checks.break.fastbreak.max_per_sec", 12);
        boolean considerEfficiency = plugin.getConfig().getBoolean("checks.break.fastbreak.consider_efficiency", true);
        int effBonusPerLevel = plugin.getConfig().getInt("checks.break.fastbreak.efficiency_bonus_per_level", 1);
        int hasteBonusPerLevel = plugin.getConfig().getInt("checks.break.fastbreak.haste_bonus_per_level", 2);
        double shovelEffMult = plugin.getConfig().getDouble("checks.break.fastbreak.shovel_efficiency_multiplier", 2.0);
        java.util.List<String> softAllow = plugin.getConfig().getStringList("checks.break.fastbreak.soft_blocks_allow");
        int softAllowBonusPerSec = plugin.getConfig().getInt("checks.break.fastbreak.soft_blocks_allow_bonus_per_sec", 6);
        int effLevel = 0;
        int hasteLevel = 0;
        boolean isShovel = false;
        Material toolType = Material.AIR;
        if (considerEfficiency) {
            ItemStack inHand = player.getItemInHand();
            if (inHand != null) {
                effLevel = inHand.getEnchantmentLevel(Enchantment.DIG_SPEED);
                toolType = inHand.getType();
                String n = toolType.name();
                isShovel = n.contains("SPADE") || n.contains("SHOVEL");
            }
            for (PotionEffect pe : player.getActivePotionEffects()) {
                if (pe.getType() == PotionEffectType.FAST_DIGGING) {
                    hasteLevel = pe.getAmplifier() + 1; // niveles desde 1
                    break;
                }
            }
        }
        int effEffective = (int) Math.round(effLevel * (isShovel ? shovelEffMult : 1.0));
        int maxPerSec = baseMaxPerSec + (effEffective * effBonusPerLevel) + (hasteLevel * hasteBonusPerLevel);
        Material brokenType = event.getBlock().getType();
        if (softAllow != null && softAllow.contains(brokenType.name())) {
            maxPerSec += softAllowBonusPerSec;
        }
        if (fastbreakEnabled && deque.size() > maxPerSec) {
            int vl = plugin.getViolationManager().addViolation(id, "FastBreak", 1);
            event.setCancelled(true);
            // Detailed logging for diagnosis
            try {
                plugin.getViolationManager().addDetectionDetail(
                        player,
                        "FastBreak",
                        vl,
                        String.format(
                                "block=%s eff=%d effEffective=%d shovel=%s haste=%d cps=%d baseMax=%d effBonus=%d hasteBonus=%d softAllow=%s softBonus=%d", 
                                brokenType.name(),
                                effLevel,
                                effEffective,
                                String.valueOf(isShovel),
                                hasteLevel,
                                deque.size(),
                                baseMaxPerSec,
                                effBonusPerLevel,
                                hasteBonusPerLevel,
                                String.valueOf(softAllow != null && softAllow.contains(brokenType.name())),
                                softAllowBonusPerSec
                        )
                );
            } catch (Throwable ignored) {}
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = color("messages.prefix");
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                player.sendMessage(ms.format("player.fastbreak", ph));
            }
            return;
        }

    }
    private String color(String path) {
        String prefix = plugin.getConfig().getString(path, "[AntiCheat] ");
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }
}