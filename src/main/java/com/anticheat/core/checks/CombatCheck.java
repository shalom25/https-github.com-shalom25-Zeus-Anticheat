package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import com.anticheat.core.util.MessageService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;

public class CombatCheck implements Listener {

    private final AntiCheatPlugin plugin;
    private final Map<UUID, Deque<Long>> hitTimes = new HashMap<>();
    

    public CombatCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (!(damager instanceof Player) || !(victim instanceof LivingEntity)) return;

        Player attacker = (Player) damager;
        LivingEntity target = (LivingEntity) victim;

        // Bypass: OPs o permisos configurados
        if (plugin.isExempt(attacker)) return;

        // Alcance (Reach)
        boolean reachEnabled = plugin.getConfig().getBoolean("checks.combat.reach.enabled", true);
        double reachMax = plugin.getConfig().getDouble("checks.combat.reach.max_distance", 3.8);
        double distance = attacker.getEyeLocation().distance(target.getEyeLocation());
        if (reachEnabled && distance > reachMax) {
            int vl = plugin.getViolationManager().addViolation(attacker.getUniqueId(), "Reach", 1);
            event.setCancelled(true);
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = color("messages.prefix");
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                attacker.sendMessage(ms.format("player.reach", ph));
            }
            return;
        }

        // KillAura (CPS/intervalos extremadamente bajos)
        long now = System.currentTimeMillis();
        UUID id = attacker.getUniqueId();
        Deque<Long> deque = hitTimes.computeIfAbsent(id, k -> new ArrayDeque<>());
        deque.addLast(now);
        // mantener ventana de ~1.5s
        while (!deque.isEmpty() && now - deque.peekFirst() > 1500) {
            deque.pollFirst();
        }
        int cps = deque.size();
        boolean killauraEnabled = plugin.getConfig().getBoolean("checks.combat.killaura.enabled", true);
        int maxCps = plugin.getConfig().getInt("checks.combat.killaura.max_cps", 15);
        if (killauraEnabled && cps > maxCps) {
            int vl = plugin.getViolationManager().addViolation(id, "KillAura", 1);
            event.setCancelled(true);
            if (vl % 2 == 1) {
                MessageService ms = plugin.getMessages();
                String prefix = color("messages.prefix");
                java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
                ph.put("prefix", prefix);
                ph.put("vl", String.valueOf(vl));
                attacker.sendMessage(ms.format("player.killaura", ph));
            }
        }

        
    }

    private String color(String path) {
        String prefix = plugin.getConfig().getString(path, "[AntiCheat] ");
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }

    
}