package net.synapselabs.anticheat.punishment;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.engine.DetectionSnapshot;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentManager {
    private final AiAnticheatPlugin plugin;
    private DetectedBanPlugin activePlugin = DetectedBanPlugin.BUKKIT;

    private final Map<UUID, Long> hardCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> aiCooldowns = new ConcurrentHashMap<>();

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
        try {
            if (Bukkit.getBanList(BanList.Type.NAME).isBanned(playerName)) return true;
        } catch (Throwable ignored) {}

        try {
            if (ip != null && Bukkit.getBanList(BanList.Type.IP).isBanned(ip)) return true;
        } catch (Throwable ignored) {}

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

    public boolean isPunishmentEnabled() {
        return plugin.getConfig().getBoolean("detection.punishments.enabled", true)
            && plugin.getConfig().getBoolean("punishment.enabled", true);
    }

    public boolean isAutoKickEnabled() {
        if (!isPunishmentEnabled()) return false;
        if (!plugin.getConfig().getBoolean("punishment.auto_kick", true)) return false;
        if (!plugin.getConfig().getBoolean("detection.punishments.auto_kick", true)) return false;
        if (!plugin.getConfig().getBoolean("auto_kick", true)) return false;
        return true;
    }

    public void evaluateSnapshot(DetectionSnapshot snapshot) {
        if (!isPunishmentEnabled()) {
            return; // Shadow mode: alerts & telemetry recorded, automated kicks/bans disabled
        }

        Player player = Bukkit.getPlayer(snapshot.playerId());
        if (player == null || !player.isOnline()) return;

        PlayerProfile profile = plugin.getDataManager().getOrCreate(player);
        long now = System.currentTimeMillis();

        // 1. Hard Check Punishments
        if (snapshot.hasHardViolations()) {
            boolean hardAutoKick = isAutoKickEnabled()
                && plugin.getConfig().getBoolean("detection.punishments.hard.auto_kick", true);
            int hardKickThreshold = plugin.getConfig().getInt("detection.punishments.hard.flags_to_kick", 2);
            int hardCooldownSec = plugin.getConfig().getInt("detection.punishments.hard.cooldown_seconds", 10);

            Long lastHard = hardCooldowns.get(player.getUniqueId());
            if (lastHard == null || (now - lastHard) > (hardCooldownSec * 1000L)) {
                if (hardAutoKick && hardKickThreshold > 0 && profile.getHardFlags() >= hardKickThreshold) {
                    hardCooldowns.put(player.getUniqueId(), now);
                    kickPlayer(player, snapshot.getPrimaryViolation(), true);
                    return;
                }
            }
        }

        // 2. AI Check Punishments
        if (snapshot.hasAiViolations()) {
            boolean aiAutoKick = isAutoKickEnabled()
                && plugin.getConfig().getBoolean("detection.punishments.ai.auto_kick", true);
            int aiKickThreshold = plugin.getConfig().getInt("detection.punishments.ai.flags_to_kick", 4);
            int aiCooldownSec = plugin.getConfig().getInt("detection.punishments.ai.cooldown_seconds", 30);

            Long lastAi = aiCooldowns.get(player.getUniqueId());
            if (lastAi == null || (now - lastAi) > (aiCooldownSec * 1000L)) {
                if (aiAutoKick && aiKickThreshold > 0 && profile.getAiFlags() >= aiKickThreshold) {
                    aiCooldowns.put(player.getUniqueId(), now);
                    kickPlayer(player, snapshot.getPrimaryViolation() + " (" + snapshot.getConfidencePercent() + "% conf)", false);
                }
            }
        }
    }

    public void kickPlayer(Player target, String reason, boolean isHardCheck) {
        String mode = plugin.getConfig().getString("punishment.mode", "AUTO").toUpperCase();
        String discordUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        if (mode.equals("CUSTOM_COMMAND")) {
            // Use the configured custom kick command instead of the built-in kick
            String template = plugin.getConfig().getString("punishment.commands.kick", "kick {player} {reason}");
            String commandToRun = template
                    .replace("{player}", target.getName())
                    .replace("{reason}", reason)
                    .replace("{discord_url}", discordUrl);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target.isOnline()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
                    plugin.getLogger().info("Executed custom kick command for " + target.getName() + ": " + commandToRun);
                }
            });
        } else {
            String kickMsg = plugin.getLanguageManager().getMessage("punishment.kick_message", target,
                "player", target.getName(), "reason", reason, "discord_url", discordUrl);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (target.isOnline()) {
                    target.kickPlayer(kickMsg);
                    plugin.getLogger().info("Kicked player " + target.getName() + " for: " + reason + " (Hard: " + isHardCheck + ")");
                }
            });
        }
    }

    public void banPlayer(Player target, String reason, String adminName, boolean isAutoBan) {
        String mode = plugin.getConfig().getString("punishment.mode", "AUTO").toUpperCase();
        String discordUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        String template;
        if (isAutoBan) {
            template = plugin.getConfig().getString("punishment.commands.auto_ban", "tempban {player} 14d Auto-Ban: Cheating [SynapseAI] -s");
        } else {
            template = plugin.getConfig().getString("punishment.commands.manual_ban", "ban {player} 30d Unfair Advantage [SynapseAI] -s");
        }

        String commandToRun = template
                .replace("{player}", target.getName())
                .replace("{reason}", reason)
                .replace("{admin}", adminName)
                .replace("{discord_url}", discordUrl);

        if (mode.equals("CUSTOM_COMMAND") || (!mode.equals("BUKKIT") && activePlugin != DetectedBanPlugin.BUKKIT)) {
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String fullReason = plugin.getLanguageManager().getMessage("punishment.ban_reason", target, "reason", reason, "discord_url", discordUrl);
                Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), fullReason, null, adminName);
                target.kickPlayer(fullReason);
            });
        }
    }

    public void banForRefusingCheck(String playerName, String ip) {
        String mode = plugin.getConfig().getString("punishment.mode", "AUTO").toUpperCase();
        String template = plugin.getConfig().getString("punishment.commands.leave_on_freeze_ban", "tempban {player} 30d Refusal of Cheat Inspection [SynapseAI] -s");
        String discordUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        String commandToRun = template
                .replace("{player}", playerName)
                .replace("{discord_url}", discordUrl);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mode.equals("CUSTOM_COMMAND") || activePlugin != DetectedBanPlugin.BUKKIT) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
            } else {
                String fullReason = plugin.getLanguageManager().getMessage("punishment.ban_reason_refusal", Bukkit.getConsoleSender(), "discord_url", discordUrl);
                Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, fullReason, new Date(System.currentTimeMillis() + 30L * 86400000L), "SynapseAI");
            }
        });
    }
}
