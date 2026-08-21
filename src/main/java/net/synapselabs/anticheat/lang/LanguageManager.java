package net.synapselabs.anticheat.lang;

import net.md_5.bungee.api.chat.TextComponent;
import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageManager {
    /** File under the plugin data folder that persists each player's explicit language choice. */
    private static final String PLAYER_LANG_FILE = "player_languages.yml";

    private final AiAnticheatPlugin plugin;
    private final Map<String, YamlConfiguration> langConfigs = new HashMap<>();
    private final Map<UUID, String> playerLanguages = new ConcurrentHashMap<>();
    private String defaultLanguage = "en";

    public LanguageManager(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        langConfigs.clear();
        this.defaultLanguage = plugin.getConfig().getString("language", "en").toLowerCase();

        saveDefaultLangFile("messages_en.yml");
        saveDefaultLangFile("messages_ru.yml");

        loadLangFile("en", "messages_en.yml");
        loadLangFile("ru", "messages_ru.yml");

        // Restore persisted per-player choices. Safe on /aiac reload because every set() is written
        // through to disk immediately, so the in-memory map never holds unpersisted entries.
        loadPlayerLanguages();
    }


    private void saveDefaultLangFile(String filename) {
        File file = new File(plugin.getDataFolder(), filename);
        if (!file.exists()) {
            plugin.saveResource(filename, false);
        }
    }

    private void loadLangFile(String langCode, String filename) {
        File file = new File(plugin.getDataFolder(), filename);
        YamlConfiguration config;
        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            InputStream stream = plugin.getResource(filename);
            if (stream != null) {
                config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } else {
                config = new YamlConfiguration();
            }
        }
        langConfigs.put(langCode.toLowerCase(), config);
    }

    public String getPlayerLanguage(CommandSender sender) {
        if (sender instanceof Player p) {
            return playerLanguages.getOrDefault(p.getUniqueId(), defaultLanguage);
        }
        return defaultLanguage;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setDefaultLanguage(String lang) {
        this.defaultLanguage = lang.toLowerCase();
        plugin.getConfig().set("language", this.defaultLanguage);
        plugin.saveConfig();
    }

    /**
     * Whether this sender has made an explicit language choice that we've persisted. Used to decide
     * whether to show the one-time language selector: we prompt only until the player picks a language,
     * then never again (the fix for the "selector re-appears on every /aiac and resets on restart" bug).
     */
    public boolean hasExplicitLanguage(CommandSender sender) {
        return sender instanceof Player p && playerLanguages.containsKey(p.getUniqueId());
    }

    public void setPlayerLanguage(UUID uuid, String lang) {
        playerLanguages.put(uuid, lang.toLowerCase());
        savePlayerLanguages();
    }

    /** Loads persisted per-player language choices from {@value #PLAYER_LANG_FILE}. */
    private void loadPlayerLanguages() {
        playerLanguages.clear();
        File file = new File(plugin.getDataFolder(), PLAYER_LANG_FILE);
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String lang = section.getString(key);
                if (lang != null && !lang.isBlank()) {
                    playerLanguages.put(id, lang.toLowerCase());
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed UUID keys rather than fail the whole load
            }
        }
    }

    /** Persists per-player language choices. Called write-through on every set and on plugin disable. */
    public void savePlayerLanguages() {
        File file = new File(plugin.getDataFolder(), PLAYER_LANG_FILE);
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : playerLanguages.entrySet()) {
            cfg.set("players." + entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + PLAYER_LANG_FILE + ": " + e.getMessage());
        }
    }


    public String getRaw(String key, String lang) {
        YamlConfiguration config = langConfigs.get(lang.toLowerCase());
        if (config == null || !config.contains(key)) {
            config = langConfigs.get(defaultLanguage);
        }
        if (config == null || !config.contains(key)) {
            config = langConfigs.get("en");
        }
        return config != null ? config.getString(key, key) : key;
    }

    public String getMessage(String key, CommandSender sender, Object... replacements) {
        String lang = getPlayerLanguage(sender);
        return getMessage(key, lang, replacements);
    }

    public String getMessage(String key, String lang, Object... replacements) {
        String template = getRaw(key, lang);
        if (replacements != null && replacements.length > 0) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                String placeholder = "{" + replacements[i] + "}";
                String val = String.valueOf(replacements[i + 1]);
                template = template.replace(placeholder, val);
            }
        }
        return CompatUtils.color(template);
    }

    public void sendLanguageSelectorPrompt(Player player) {
        String currentLang = getPlayerLanguage(player);
        CompatUtils.sendMessage(player, getMessage("lang_selector.header", currentLang));
        CompatUtils.sendMessage(player, getMessage("lang_selector.prompt", currentLang));
        CompatUtils.sendMessage(player, getMessage("lang_selector.current", currentLang, "current", currentLang.toUpperCase()));

        TextComponent options = new TextComponent("   ");
        options.addExtra(CompatUtils.createGrimButton(
            getRaw("lang_selector.btn_en", currentLang),
            "&#00d2ff",
            getRaw("lang_selector.btn_en_hover", currentLang),
            "/aiac lang en"
        ));
        options.addExtra(new TextComponent("   "));
        options.addExtra(CompatUtils.createGrimButton(
            getRaw("lang_selector.btn_ru", currentLang),
            "&#00ff88",
            getRaw("lang_selector.btn_ru_hover", currentLang),
            "/aiac lang ru"
        ));

        CompatUtils.sendComponent(player, options);
        CompatUtils.sendMessage(player, getMessage("lang_selector.header", currentLang));
    }
}
