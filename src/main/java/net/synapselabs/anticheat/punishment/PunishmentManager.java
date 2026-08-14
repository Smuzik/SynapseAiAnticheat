package net.synapselabs.anticheat.punishment;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Date;

public class PunishmentManager {
    private final AiAnticheatPlugin plugin;
    private DetectedBanPlugin activePlugin = DetectedBanPlugin.BUKKIT;

    public enum DetectedBanPlugin {
        LITEBANS("LiteBans"),
        ADVANCED_BAN("AdvancedBan"),
        LIBERTY_BANS("LibertyBans"),
        ESSENTIALS("Essentials"),
        MAX_BANS("MaxBans"),
        BUKKIT("Bukkit/Vanilla");

        private final String display;
        DetectedBanPlugin(String display) { this.display = display; }
        public String getDisplay() { return display; }
    }

    public PunishmentManager(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
        detectBanPlugin();
    }

    public void detectBanPlugin() {
        if (Bukkit.getPluginManager().isPluginEnabled("LiteBans")) {
            this.activePlugin = DetectedBanPlugin.LITEBANS;
        } else if (Bukkit.getPluginManager().isPluginEnabled("AdvancedBan")) {
            this.activePlugin = DetectedBanPlugin.ADVANCED_BAN;
        } else if (Bukkit.getPluginManager().isPluginEnabled("LibertyBans")) {
            this.activePlugin = DetectedBanPlugin.LIBERTY_BANS;
        } else if (Bukkit.getPluginManager().isPluginEnabled("Essentials")) {
            this.activePlugin = DetectedBanPlugin.ESSENTIALS;
        } else if (Bukkit.getPluginManager().isPluginEnabled("MaxBans")) {
            this.activePlugin = DetectedBanPlugin.MAX_BANS;
        } else {
            this.activePlugin = DetectedBanPlugin.BUKKIT;
        }
        plugin.getLogger().info("Detected Ban System: " + activePlugin.getDisplay());
    }

    public DetectedBanPlugin getActivePlugin() {
        return activePlugin;
    }

    public boolean isPlayerBanned(String playerName, String ip) {
        // 1. Check Bukkit Vanilla BanList (Name)
        try {
            if (Bukkit.getBanList(BanList.Type.NAME).isBanned(playerName)) {
                return true;
            }
        } catch (Throwable ignored) {}

        // 2. Check Bukkit Vanilla BanList (IP)
        try {
            if (ip != null && Bukkit.getBanList(BanList.Type.IP).isBanned(ip)) {
                return true;
            }
        } catch (Throwable ignored) {}

        // 3. Check LiteBans API via Reflection
        if (activePlugin == DetectedBanPlugin.LITEBANS) {
            try {
                Class<?> dbClass = Class.forName("litebans.api.Database");
                Object dbInstance = dbClass.getMethod("get").invoke(null);
                if (dbInstance != null) {
                    Player p = Bukkit.getPlayer(playerName);
                    if (p != null) {
                        Boolean banned = (Boolean) dbClass.getMethod("isPlayerBanned", java.util.UUID.class, String.class)
                                .invoke(dbInstance, p.getUniqueId(), ip);
                        if (banned != null && banned) return true;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 4. Check AdvancedBan API via Reflection
        if (activePlugin == DetectedBanPlugin.ADVANCED_BAN) {
            try {
                Class<?> pmClass = Class.forName("me.leoko.advancedban.manager.PunishmentManager");
                Object pmInstance = pmClass.getMethod("get").invoke(null);
                if (pmInstance != null) {
                    Boolean banned = (Boolean) pmClass.getMethod("isBanned", String.class).invoke(pmInstance, playerName);
                    if (banned != null && banned) return true;
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }

    public void banPlayer(Player target, String reason, String adminName, boolean isAutoBan) {
        String mode = plugin.getConfig().getString("punishment.mode", "AUTO").toUpperCase();
        String discordUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        String template;
        if (isAutoBan) {
            template = plugin.getConfig().getString("punishment.commands.auto_ban", "tempban {player} 14d Читы [SynapseAI] -s");
        } else {
            template = plugin.getConfig().getString("punishment.commands.manual_ban", "ban {player} 30d Использование читов [SynapseAI] -s");
        }

        String commandToRun = template
                .replace("{player}", target.getName())
                .replace("{reason}", reason)
                .replace("{admin}", adminName)
                .replace("{discord_url}", discordUrl);

        if (mode.equals("CUSTOM_COMMAND") || !mode.equals("BUKKIT") && activePlugin != DetectedBanPlugin.BUKKIT) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
            });
        } else {
            // Native Bukkit / Vanilla ban
            Bukkit.getScheduler().runTask(plugin, () -> {
                String fullReason = CompatUtils.color("&4[SynapseAI] " + reason + "\n&7Апелляция: &b" + discordUrl);
                Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), fullReason, null, adminName);
                target.kickPlayer(fullReason);
            });
        }
    }

    public void banForRefusingCheck(String playerName, String ip) {
        String template = plugin.getConfig().getString("punishment.commands.leave_on_freeze_ban", "tempban {player} 30d Отказ от проверки на читы [SynapseAI] -s");
        String discordUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        String commandToRun = template
                .replace("{player}", playerName)
                .replace("{discord_url}", discordUrl);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (activePlugin != DetectedBanPlugin.BUKKIT) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
            } else {
                String fullReason = CompatUtils.color("&4[SynapseAI] Отказ от проверки на читы\n&7Апелляция: &b" + discordUrl);
                Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, fullReason, new Date(System.currentTimeMillis() + 30L * 86400000L), "SynapseAI");
            }
        });
    }
}
