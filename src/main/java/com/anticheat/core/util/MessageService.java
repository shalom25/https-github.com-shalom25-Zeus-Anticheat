package com.anticheat.core.util;

import com.anticheat.core.AntiCheatPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

public class MessageService {
    private final AntiCheatPlugin plugin;
    private FileConfiguration messages;
    private String language;

    public MessageService(AntiCheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.language = plugin.getConfig().getString("language", "en");
        String fileName = "lang/messages_" + language + ".yml";
        // Ensure the file exists in the data folder
        File dataFile = new File(plugin.getDataFolder(), fileName);
        if (!dataFile.exists()) {
            plugin.saveResource(fileName, false);
        }
        this.messages = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void reload() {
        load();
    }

    public String get(String key) {
        String raw = messages.getString(key);
        if (raw == null) {
            raw = defaultMessageForKey(key);
        }
        if (raw == null) return key;
        return translateColors(raw);
    }

    public String format(String key, Map<String, String> placeholders) {
        String msg = get(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return msg;
    }

    private String translateColors(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String defaultMessageForKey(String key) {
        // Fallbacks to ensure player gets notified even if message files lack new keys
        String lang = this.language == null ? "en" : this.language.toLowerCase();
        switch (key) {
            case "player.baritone":
                return lang.equals("es")
                        ? "{prefix}&cPatrón de pathing automático (Baritone).&7 VL={vl}"
                        : "{prefix}&cAutomated pathing pattern (Baritone).&7 VL={vl}";
            case "player.inventory":
                return lang.equals("es")
                        ? "{prefix}&cMovimiento con inventario abierto.&7 VL={vl}"
                        : "{prefix}&cMovement while inventory open.&7 VL={vl}";
            case "player.crash":
                return lang.equals("es")
                        ? "{prefix}&cContenido potencialmente dañino (Crash: libro/cartel).&7 VL={vl}"
                        : "{prefix}&cPotentially harmful content (Crash: book/sign).&7 VL={vl}";
            default:
                return null;
        }
    }
}