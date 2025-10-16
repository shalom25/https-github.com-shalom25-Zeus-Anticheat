package com.anticheat.core.checks;

import com.anticheat.core.AntiCheatPlugin;
import com.anticheat.core.util.BlockUtil;
import com.anticheat.core.util.MessageService;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.ChatColor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VehicleCheck implements Listener {

    private final AntiCheatPlugin plugin;
    // Consecutive ticks a boat rider is out of liquid and without a solid block below
    private final Map<UUID, Integer> boatAirTicks = new HashMap<>();

    public VehicleCheck(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        Entity vehicle = event.getVehicle();
        if (!(vehicle instanceof Boat)) return;
        // Find player passenger (compatible with 1.8 and modern versions)
        Player rider = null;
        try {
            java.lang.reflect.Method m = vehicle.getClass().getMethod("getPassengers");
            Object r = m.invoke(vehicle);
            if (r instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) r;
                for (Object o : list) {
                    if (o instanceof Player) { rider = (Player) o; break; }
                }
            }
        } catch (Exception ignored) {
            Entity p = vehicle.getPassenger();
            if (p instanceof Player) rider = (Player) p;
        }
        if (rider == null) return;
        // Bypass: OPs or configured permissions
        if (plugin.isExempt(rider)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // Consider liquid in the current block and the block immediately below
        boolean inLiquid = BlockUtil.isInLiquid(to) || BlockUtil.isInLiquid(to.clone().add(0, -1, 0));
        boolean hasSolidBelow = BlockUtil.hasSolidBelow(to, 2);

        double maxBoatHz = plugin.getConfig().getDouble("checks.vehicle.boat.speed.max_horizontal_per_tick", 0.9);
        double maxBoatUp = plugin.getConfig().getDouble("checks.vehicle.boat.fly.max_vertical_per_tick", 0.4);
        int airTicksThreshold = plugin.getConfig().getInt("checks.vehicle.boat.fly.air_ticks_threshold", 5);
        boolean speedEnabled = plugin.getConfig().getBoolean("checks.vehicle.boat.speed.enabled", true);
        boolean flyEnabled = plugin.getConfig().getBoolean("checks.vehicle.boat.fly.enabled", true);

        // Accumulate air ticks (outside liquid and without block below)
        if (flyEnabled) {
            UUID id = rider.getUniqueId();
            if (!inLiquid && !hasSolidBelow) {
                int t = boatAirTicks.getOrDefault(id, 0) + 1;
                boatAirTicks.put(id, t);
            } else {
                boatAirTicks.put(id, 0);
            }
        }

        if (speedEnabled && horizontal > maxBoatHz) {
            int vl = plugin.getViolationManager().addViolation(rider.getUniqueId(), "BoatSpeed", 1);
            vehicle.teleport(from);
            MessageService ms = plugin.getMessages();
            String prefix = color("messages.prefix");
            java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("vl", String.valueOf(vl));
            rider.sendMessage(ms.format("player.boatspeed", ph));
        }

        // Detectar BoatFly por ascenso vertical o por flote sostenido fuera del agua
        if (flyEnabled && ((!inLiquid && dy > maxBoatUp) || boatAirTicks.getOrDefault(rider.getUniqueId(), 0) >= airTicksThreshold)) {
            int vl = plugin.getViolationManager().addViolation(rider.getUniqueId(), "BoatFly", 1);
            vehicle.teleport(from);
            MessageService ms = plugin.getMessages();
            String prefix = color("messages.prefix");
            java.util.Map<String,String> ph = new java.util.HashMap<String, String>();
            ph.put("prefix", prefix);
            ph.put("vl", String.valueOf(vl));
            rider.sendMessage(ms.format("player.boatfly", ph));
        }
    }

    private String color(String path) {
        String prefix = plugin.getConfig().getString(path, "[AntiCheat] ");
        return ChatColor.translateAlternateColorCodes('&', prefix);
    }
}