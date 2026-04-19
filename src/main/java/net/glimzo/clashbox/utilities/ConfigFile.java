package net.glimzo.clashbox.utilities;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class ConfigFile {

    private ConfigFile() {}

    public static FileConfiguration load(JavaPlugin plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public static void save(JavaPlugin plugin, String fileName, FileConfiguration cfg) {
        File file = new File(plugin.getDataFolder(), fileName);
        try {
            cfg.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("[ClashBox] Failed to save " + fileName + ": " + e.getMessage());
        }
    }
}
