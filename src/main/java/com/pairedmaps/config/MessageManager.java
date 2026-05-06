package com.pairedmaps.config;
import com.pairedmaps.PairedMapsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
public final class MessageManager {
    private final PairedMapsPlugin plugin;
    private FileConfiguration messagesConfig;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    public MessageManager(PairedMapsPlugin plugin) {
        this.plugin = plugin;
    }
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file);
        var resource = plugin.getResource("messages.yml");
        if (resource != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8)
            );
            messagesConfig.setDefaults(defaults);
            messagesConfig.options().copyDefaults(true);
            if (messagesConfig instanceof YamlConfiguration yaml) {
                try {
                    yaml.save(file);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to save merged messages.yml defaults: " + e.getMessage());
                }
            }
        }
    }
    public Component get(String key) {
        String raw = messagesConfig.getString(key, "<missing:" + key + ">");
        return miniMessage.deserialize(raw);
    }
    public Component get(String key, String... replacements) {
        String raw = messagesConfig.getString(key, "<missing:" + key + ">");
        for (int i = 0; i < replacements.length - 1; i += 2) {
            raw = raw.replace("<" + replacements[i] + ">", replacements[i + 1]);
        }
        return miniMessage.deserialize(raw);
    }
    public String getRaw(String key, String... replacements) {
        String raw = messagesConfig.getString(key, "<missing:" + key + ">");
        for (int i = 0; i < replacements.length - 1; i += 2) {
            raw = raw.replace("<" + replacements[i] + ">", replacements[i + 1]);
        }
        return raw;
    }
}

