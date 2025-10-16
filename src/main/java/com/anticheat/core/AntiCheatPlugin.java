package com.anticheat.core;

import com.anticheat.core.checks.MovementCheck;
import com.anticheat.core.checks.CombatCheck;
import com.anticheat.core.checks.VehicleCheck;
import com.anticheat.core.checks.ElytraCheck;
import com.anticheat.core.checks.PlacementCheck;
import com.anticheat.core.checks.BreakCheck;
import com.anticheat.core.checks.ClickCheck;
import com.anticheat.core.checks.InventoryCheck;
import com.anticheat.core.checks.TeleportCheck;
import com.anticheat.core.checks.BaritoneCheck;
import com.anticheat.core.checks.CrashCheck;
import com.anticheat.core.commands.AntiCheatCommand;
import com.anticheat.core.violation.ViolationManager;
import com.anticheat.core.util.MessageService;
import com.anticheat.core.commands.ZeusCommand;
import com.anticheat.core.listeners.ChatListener;
import com.anticheat.core.listeners.ModerationHookListener;
import com.anticheat.core.gui.MenuAlertsGUI;
import com.anticheat.core.gui.MenuPlayerActionsGUI;
import com.anticheat.core.commands.MenuCommand;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;
import org.bukkit.entity.Player;

public class AntiCheatPlugin extends JavaPlugin {

    private ViolationManager violationManager;
    private MessageService messageService;
    private final Set<UUID> alertsDisabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> freecamFreezeUntil = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Guardar archivos de mensajes por defecto (si no existen)
        try {
            saveResource("lang/messages_es.yml", false);
        } catch (IllegalArgumentException ignored) {}
        try {
            saveResource("lang/messages_en.yml", false);
        } catch (IllegalArgumentException ignored) {}

        this.messageService = new MessageService(this);
        this.messageService.load();
        this.violationManager = new ViolationManager(this);

        // Registrar listeners (checks)
        getServer().getPluginManager().registerEvents(new MovementCheck(this), this);
        getServer().getPluginManager().registerEvents(new CombatCheck(this), this);
        getServer().getPluginManager().registerEvents(new VehicleCheck(this), this);
        getServer().getPluginManager().registerEvents(new ElytraCheck(this), this);
        getServer().getPluginManager().registerEvents(new PlacementCheck(this), this);
        getServer().getPluginManager().registerEvents(new BreakCheck(this), this);
        getServer().getPluginManager().registerEvents(new ClickCheck(this), this);
        getServer().getPluginManager().registerEvents(new InventoryCheck(this), this);
        getServer().getPluginManager().registerEvents(new BaritoneCheck(this), this);
        getServer().getPluginManager().registerEvents(new CrashCheck(this), this);
        // Menú GUI de alertas y submenú de acciones
        MenuPlayerActionsGUI actionsGUI = new MenuPlayerActionsGUI(this);
        MenuAlertsGUI menuGUI = new MenuAlertsGUI(this, actionsGUI);
        actionsGUI.setAlertsGUI(menuGUI);
        getServer().getPluginManager().registerEvents(menuGUI, this);
        getServer().getPluginManager().registerEvents(actionsGUI, this);

        // Ejecutores de comando
        try { getCommand("zeus").setExecutor(new ZeusCommand(this)); } catch (Throwable ignored) {}
        try { getCommand("anticheat").setExecutor(new AntiCheatCommand(this)); } catch (Throwable ignored) {}
        try { getCommand("menu").setExecutor(new MenuCommand(this, menuGUI)); } catch (Throwable ignored) {}
        getServer().getPluginManager().registerEvents(new TeleportCheck(this), this);
        // Listener de chat para muteos
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        // Hook de moderación para Essentials
        if (getConfig().getBoolean("hooks.essentials.enabled", true) && getServer().getPluginManager().getPlugin("Essentials") != null) {
            getServer().getPluginManager().registerEvents(new ModerationHookListener(this), this);
        }

        // Comando admin
        if (getCommand("anticheat") != null) {
            getCommand("anticheat").setExecutor(new AntiCheatCommand(this));
        }

        // Comando Zeus
        if (getCommand("zeus") != null) {
            getCommand("zeus").setExecutor(new ZeusCommand(this));
        }

        getLogger().info("Zeus Anti-Cheat enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Zeus Anti-Cheat deshabilitado.");
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }

    public MessageService getMessages() {
        return messageService;
    }

    // Detección de jugadores Bedrock vía Floodgate (si está disponible)
    public boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        try {
            org.bukkit.plugin.Plugin fg = getServer().getPluginManager().getPlugin("Floodgate");
            if (fg == null) fg = getServer().getPluginManager().getPlugin("floodgate");
            if (fg == null) return false; // Floodgate no instalado
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            java.lang.reflect.Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            if (api == null) return false;
            java.lang.reflect.Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", java.util.UUID.class);
            Object res = isFloodgatePlayer.invoke(api, player.getUniqueId());
            return (res instanceof Boolean) && ((Boolean) res).booleanValue();
        } catch (Throwable ignored) {}
        return false;
    }

    // Alerts toggle per-player
    public boolean areAlertsEnabled(UUID uuid) {
        return !alertsDisabled.contains(uuid);
    }

    public void setAlertsEnabled(UUID uuid, boolean enabled) {
        if (enabled) alertsDisabled.remove(uuid); else alertsDisabled.add(uuid);
    }

    public boolean toggleAlerts(UUID uuid) {
        if (alertsDisabled.contains(uuid)) {
            alertsDisabled.remove(uuid);
            return true;
        } else {
            alertsDisabled.add(uuid);
            return false;
        }
    }

    // Freecam freeze state
    public void freezePlayerForFreecam(UUID uuid, long untilMs) {
        if (untilMs <= System.currentTimeMillis()) {
            freecamFreezeUntil.remove(uuid);
        } else {
            freecamFreezeUntil.put(uuid, untilMs);
        }
    }

    public long getFreecamFreezeUntil(UUID uuid) {
        Long v = freecamFreezeUntil.get(uuid);
        return v == null ? 0L : v.longValue();
    }

    public void clearFreecamFreeze(UUID uuid) {
        freecamFreezeUntil.remove(uuid);
    }

    // Exención central: jugadores que no deben ser afectados por el anti‑cheat
    public boolean isExempt(Player player) {
        if (player == null) return false;
        try {
            // Ignorar completamente jugadores Bedrock si está habilitado
            if (getConfig().getBoolean("exempt.bedrock", true) && isBedrockPlayer(player)) return true;
            // Eximir OP si está habilitado
            if (getConfig().getBoolean("exempt.ops", true) && player.isOp()) return true;
            // Eximir por permisos configurados
            java.util.List<String> perms = getConfig().getStringList("exempt.permissions");
            if (perms != null) {
                for (String perm : perms) {
                    if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    // Bypass granular: permite controlar si un check específico puede ser eximido
    public boolean isExemptFor(Player player, String checkKey) {
        if (player == null) return false;
        // Bedrock: siempre exento si está habilitado, independientemente del check
        try {
            if (getConfig().getBoolean("exempt.bedrock", true) && isBedrockPlayer(player)) return true;
        } catch (Throwable ignored) {}
        String path = "exempt.checks." + (checkKey == null ? "" : checkKey.toLowerCase());
        boolean allowBypassForCheck = getConfig().getBoolean(path, true);
        if (!allowBypassForCheck) return false;
        return isExempt(player);
    }
}