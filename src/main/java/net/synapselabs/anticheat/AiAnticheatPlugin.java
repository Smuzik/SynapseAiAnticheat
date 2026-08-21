package net.synapselabs.anticheat;

import net.synapselabs.anticheat.alert.AlertManager;
import net.synapselabs.anticheat.bridge.GrimAcBridge;
import net.synapselabs.anticheat.checks.block.FastBreakCheck;
import net.synapselabs.anticheat.checks.block.FastPlaceCheck;
import net.synapselabs.anticheat.checks.combat.AutoClickerCheck;
import net.synapselabs.anticheat.checks.item.FastInteractCheck;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerDataManager;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.engine.AiInferenceEngine;
import net.synapselabs.anticheat.engine.HardCombatChecks;
import net.synapselabs.anticheat.engine.InferenceService;
import net.synapselabs.anticheat.freeze.FreezeManager;
import net.synapselabs.anticheat.gui.AdminGuiManager;
import net.synapselabs.anticheat.gui.GuiListener;
import net.synapselabs.anticheat.lang.LanguageManager;
import net.synapselabs.anticheat.listener.CombatListener;
import net.synapselabs.anticheat.overlay.OverheadDisplayManager;
import net.synapselabs.anticheat.punishment.PunishmentManager;
import net.synapselabs.anticheat.webhook.DiscordWebhookService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AiAnticheatPlugin extends JavaPlugin implements CommandExecutor {
    private LanguageManager languageManager;
    private AiInferenceEngine engine;
    private InferenceService inferenceService;
    private HardCombatChecks hardCombatChecks;
    private AlertManager alertManager;
    private OverheadDisplayManager overheadDisplayManager;
    private CombatListener combatListener;
    private PlayerDataManager dataManager;
    private AdminGuiManager guiManager;
    private GrimAcBridge grimAcBridge;
    private FreezeManager freezeManager;
    private DiscordWebhookService webhookService;
    private PunishmentManager punishmentManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.languageManager = new LanguageManager(this);
        sendStartupBanner();

        this.punishmentManager = new PunishmentManager(this);
        this.dataManager = new PlayerDataManager(this);
        this.engine = new AiInferenceEngine(this);
        this.inferenceService = new InferenceService(this, engine);
        this.hardCombatChecks = new HardCombatChecks(this);
        this.alertManager = new AlertManager(this);
        this.overheadDisplayManager = new OverheadDisplayManager(this);

        this.combatListener = new CombatListener(
            this,
            hardCombatChecks,
            inferenceService,
            alertManager,
            overheadDisplayManager
        );

        this.guiManager = new AdminGuiManager(this, dataManager);
        this.grimAcBridge = new GrimAcBridge(this);
        this.freezeManager = new FreezeManager(this);
        this.webhookService = new DiscordWebhookService(this);

        // Register Core Listeners
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(freezeManager, this);
        getServer().getPluginManager().registerEvents(overheadDisplayManager, this);

        // Register Extra Check Listeners
        getServer().getPluginManager().registerEvents(new FastBreakCheck(this), this);
        getServer().getPluginManager().registerEvents(new FastPlaceCheck(this), this);
        getServer().getPluginManager().registerEvents(new AutoClickerCheck(this), this);
        getServer().getPluginManager().registerEvents(new FastInteractCheck(this), this);

        // Register Command
        if (getCommand("aianticheat") != null) {
            getCommand("aianticheat").setExecutor(this);
        }

        // Periodic confidence & suspicion decay (every 5 seconds)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (combatListener != null) {
                combatListener.decayAll(0.04f);
            }
        }, 100L, 100L);

        // Periodic Database Save (every 2 minutes)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (dataManager != null) {
                dataManager.saveDatabase();
            }
        }, 2400L, 2400L);

        if (!isSetupCompleted()) {
            getLogger().warning("==========================================================");
            getLogger().warning("[Synapse AI] FIRST RUN SETUP REQUIRED!");
            getLogger().warning("Type /aiac in-game to select your language and activate AI protection.");
            getLogger().warning("==========================================================");
        } else {
            getLogger().info("Synapse AI-AntiCheat v3.0 is ACTIVE and protecting the server!");
        }
    }

    private void sendStartupBanner() {
        String discordUrl = getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");
        getLogger().info("==========================================================");
        getLogger().info("   ____                                    _     _       ");
        getLogger().info("  / ___| _   _ _ __   __ _ _ __  ___  ___ | |   | |      ");
        getLogger().info("  \\___ \\| | | | '_ \\ / _` | '_ \\/ __|/ _ \\| |   | |  ");
        getLogger().info("   ___) | |_| | | | | (_| | |_) \\__ \\  __/| |___| |___ ");
        getLogger().info("  |____/ \\__, |_| |_|\\__,_| .__/|___/\\___||_____|_____|");
        getLogger().info("         |___/            |_|                            ");
        getLogger().info("   Synapse AI-AntiCheat v3.0 (Spigot / Paper / Purpur) ");
        getLogger().info("   Author: Synapse Labs Studio                         ");
        getLogger().info("   Discord: " + discordUrl);
        getLogger().info("==========================================================");
    }

    public boolean isSetupCompleted() {
        return getConfig().getBoolean("setup_completed", false);
    }

    public void setSetupCompleted(boolean completed) {
        getConfig().set("setup_completed", completed);
        saveConfig();
    }

    @Override
    public void onDisable() {
        if (overheadDisplayManager != null) {
            overheadDisplayManager.removeAll();
        }
        if (inferenceService != null) {
            inferenceService.close();
        }
        if (languageManager != null) {
            languageManager.savePlayerLanguages();
        }
        if (dataManager != null) {
            dataManager.saveDatabase();
        }
        if (engine != null) {
            engine.close();
        }
        getLogger().info("Synapse AI-AntiCheat disabled.");
    }

    public LanguageManager getLanguageManager() { return languageManager; }
    public CombatListener getCombatListener() { return combatListener; }
    public PlayerDataManager getDataManager() { return dataManager; }
    public AdminGuiManager getGuiManager() { return guiManager; }
    public OverheadDisplayManager getOverheadManager() { return overheadDisplayManager; }
    public OverheadDisplayManager getOverheadDisplayManager() { return overheadDisplayManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public DiscordWebhookService getWebhookService() { return webhookService; }
    public PunishmentManager getPunishmentManager() { return punishmentManager; }
    public GrimAcBridge getGrimAcBridge() { return grimAcBridge; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aianticheat.admin")) {
            sender.sendMessage(languageManager.getMessage("prefix", sender) + languageManager.getMessage("commands.access_denied", sender));
            return true;
        }

        // First-Run Language Gate
        if (!isSetupCompleted()) {
            if (args.length == 0 || (!args[0].equalsIgnoreCase("lang") && !args[0].equalsIgnoreCase("language"))) {
                if (sender instanceof Player player) {
                    languageManager.sendLanguageSelectorPrompt(player);
                } else {
                    sender.sendMessage(CompatUtils.color("&c[SynapseAI] Please configure language first: /aiac lang <en|ru>"));
                }
                return true;
            }
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
            sendHelpMenu(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("lang") || args[0].equalsIgnoreCase("language")) {
            if (args.length > 1) {
                String chosenLang = args[1].toLowerCase();
                if (chosenLang.equals("en") || chosenLang.equals("ru")) {
                    languageManager.setDefaultLanguage(chosenLang);
                    if (sender instanceof Player p) {
                        languageManager.setPlayerLanguage(p.getUniqueId(), chosenLang);
                    }
                    if (!isSetupCompleted()) {
                        setSetupCompleted(true);
                        for (Player staff : Bukkit.getOnlinePlayers()) {
                            if (staff.hasPermission("aianticheat.admin")) {
                                staff.sendMessage(languageManager.getMessage("setup.completed", chosenLang, "lang", chosenLang.toUpperCase()));
                            }
                        }
                    } else {
                        sender.sendMessage(languageManager.getMessage("lang_selector.changed", chosenLang, "lang", chosenLang.toUpperCase()));
                    }
                } else {
                    sender.sendMessage(languageManager.getMessage("commands.lang_usage", sender));
                }
            } else if (sender instanceof Player player) {
                languageManager.sendLanguageSelectorPrompt(player);
            } else {
                sender.sendMessage(languageManager.getMessage("commands.lang_current_default", sender, "lang", getConfig().getString("language", "en")));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) {
                guiManager.openMainMenu(player);
            } else {
                sender.sendMessage(languageManager.getMessage("commands.players_only_gui", sender));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("about") || args[0].equalsIgnoreCase("info")) {
            String discordUrl = getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");
            String lang = languageManager.getPlayerLanguage(sender);
            sender.sendMessage(languageManager.getMessage("about.header", lang));
            sender.sendMessage(languageManager.getMessage("about.title", lang, "version", "3.0"));
            sender.sendMessage(languageManager.getMessage("about.author", lang));
            sender.sendMessage(languageManager.getMessage("about.studio", lang));
            sender.sendMessage(languageManager.getMessage("about.discord", lang, "url", discordUrl));
            sender.sendMessage(languageManager.getMessage("about.engine", lang));
            sender.sendMessage(languageManager.getMessage("about.header", lang));
            return true;
        }

        if (args[0].equalsIgnoreCase("ping") || args[0].equalsIgnoreCase("checkping")) {
            Player target = null;
            if (args.length > 1) {
                target = Bukkit.getPlayer(args[1]);
            } else if (sender instanceof Player p) {
                target = p;
            }

            if (target == null) {
                sender.sendMessage(languageManager.getMessage("commands.ping_specify", sender));
                return true;
            }

            String lang = languageManager.getPlayerLanguage(sender);
            int ping = target.getPing();
            String pingRating;
            if (ping < 50) {
                pingRating = languageManager.getRaw("commands.ping_rating_excellent", lang);
            } else if (ping < 110) {
                pingRating = languageManager.getRaw("commands.ping_rating_normal", lang);
            } else if (ping < 180) {
                pingRating = languageManager.getRaw("commands.ping_rating_elevated", lang);
            } else {
                pingRating = languageManager.getRaw("commands.ping_rating_high", lang);
            }

            String ip = target.getAddress() != null
                ? target.getAddress().getAddress().getHostAddress()
                : languageManager.getRaw("commands.unknown", lang);
            String grimStatus = grimAcBridge.isGrimLoaded()
                ? languageManager.getRaw("commands.grim_synced", lang)
                : languageManager.getRaw("commands.grim_standalone", lang);

            sender.sendMessage(languageManager.getMessage("commands.separator", lang));
            sender.sendMessage(languageManager.getMessage("commands.ping_header", lang, "player", target.getName()));
            sender.sendMessage(languageManager.getMessage("commands.ping_current", lang, "ping", ping, "rating", pingRating));
            sender.sendMessage(languageManager.getMessage("commands.ping_ip", lang, "ip", ip));
            sender.sendMessage(languageManager.getMessage("commands.ping_tps", lang));
            sender.sendMessage(languageManager.getMessage("commands.ping_grim", lang, "status", grimStatus));
            sender.sendMessage(languageManager.getMessage("commands.separator", lang));
            return true;
        }

        if (args[0].equalsIgnoreCase("setfreezeloc")) {
            if (sender instanceof Player player) {
                freezeManager.setCustomFreezeLocation(player.getLocation());
                sender.sendMessage(languageManager.getMessage("commands.freezeloc_set", sender));
            } else {
                sender.sendMessage(languageManager.getMessage("commands.ingame_only", sender));
            }
            return true;
        }

        if ((args[0].equalsIgnoreCase("chat") || args[0].equalsIgnoreCase("msg")) && args.length > 1) {
            if (sender instanceof Player player) {
                if (!freezeManager.isModeratorChecking(player.getUniqueId())) {
                    sender.sendMessage(languageManager.getMessage("commands.chat_not_inspecting", sender));
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    sb.append(args[i]).append(" ");
                }
                freezeManager.sendInterrogationMessage(player, sb.toString().trim(), true);
            } else {
                sender.sendMessage(languageManager.getMessage("commands.ingame_only", sender));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("inspect") && args.length > 1) {
            if (sender instanceof Player player) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(languageManager.getMessage("commands.player_not_found_online", player));
                    return true;
                }
                guiManager.openInspectMenu(player, target);
            } else {
                sender.sendMessage(languageManager.getMessage("commands.ingame_only", sender));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("freeze") && args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(languageManager.getMessage("commands.player_not_found", sender));
                return true;
            }
            Player moderator = (sender instanceof Player p) ? p : null;
            String defaultReason = languageManager.getMessage("gui.freeze_reason", sender);
            if (freezeManager.freeze(target, defaultReason, moderator)) {
                sender.sendMessage(languageManager.getMessage("freeze.frozen_staff", sender, "player", target.getName()));
            } else {
                sender.sendMessage(languageManager.getMessage("freeze.cooldown_wait", sender, "seconds", freezeManager.getRemainingCooldownSeconds(target.getUniqueId())));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("unfreeze") && args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(languageManager.getMessage("commands.player_not_found", sender));
                return true;
            }
            if (freezeManager.unfreeze(target)) {
                sender.sendMessage(languageManager.getMessage("freeze.unfrozen_staff", sender, "player", target.getName()));
            } else {
                sender.sendMessage(languageManager.getMessage("freeze.cooldown_wait", sender, "seconds", freezeManager.getRemainingCooldownSeconds(target.getUniqueId())));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            String lang = languageManager.getPlayerLanguage(sender);
            boolean active = engine != null && engine.isInitialized();
            String discordUrl = getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");
            sender.sendMessage(languageManager.getMessage("commands.status_model", lang, "status",
                languageManager.getRaw(active ? "commands.status_model_active" : "commands.status_model_error", lang)));
            sender.sendMessage(languageManager.getMessage("commands.status_bridge", lang, "status",
                languageManager.getRaw(grimAcBridge.isGrimLoaded() ? "commands.status_bridge_connected" : "commands.status_bridge_standalone", lang)));
            sender.sendMessage(languageManager.getMessage("commands.status_ban", lang, "system", punishmentManager.getActivePlugin().getDisplay()));
            sender.sendMessage(languageManager.getMessage("commands.status_overhead", lang));
            sender.sendMessage(languageManager.getMessage("commands.status_discord", lang, "url", discordUrl));
            sender.sendMessage(languageManager.getMessage("commands.status_webhook", lang, "status",
                languageManager.getRaw(getConfig().getBoolean("discord.webhook.enabled", false) ? "commands.status_enabled" : "commands.status_disabled", lang)));
            return true;
        }

        if (args[0].equalsIgnoreCase("check") && args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(languageManager.getMessage("commands.player_not_found_online", sender));
                return true;
            }
            String lang = languageManager.getPlayerLanguage(sender);
            PlayerProfile profile = dataManager.getOrCreate(target);
            int kScore = Math.round(profile.getKillauraConfidence() * 100.0f);
            int aScore = Math.round(profile.getAimConfidence() * 100.0f);
            int susp = Math.round(profile.getSuspicion() * 100.0f);

            sender.sendMessage(languageManager.getMessage("commands.check_header", lang, "player", target.getName()));
            sender.sendMessage(languageManager.getMessage("commands.check_threat", lang, "state",
                languageManager.getRaw(profile.getThreatState().messageKey(), lang)));
            sender.sendMessage(languageManager.getMessage("commands.check_killaura", lang,
                "bar", AdminGuiManager.renderBar(kScore, 10, "&#ff2244", "&#ffaa00", "&#00ff88"), "score", kScore));
            sender.sendMessage(languageManager.getMessage("commands.check_aim", lang,
                "bar", AdminGuiManager.renderBar(aScore, 10, "&#ff2244", "&#ffaa00", "&#00ff88"), "score", aScore));
            sender.sendMessage(languageManager.getMessage("commands.check_suspicion", lang,
                "bar", AdminGuiManager.renderBar(susp, 10, "&#ff2244", "&#ffaa00", "&#00ff88"), "score", susp));
            sender.sendMessage(languageManager.getMessage("commands.check_flags", lang,
                "total", profile.getTotalFlags(), "hard", profile.getHardFlags(), "ai", profile.getAiFlags(), "grim", profile.getGrimFlagsCount()));
            return true;
        }

        if (args[0].equalsIgnoreCase("testwebhook") || args[0].equalsIgnoreCase("webhook")) {
            webhookService.testWebhook(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            languageManager.reload();
            sender.sendMessage(languageManager.getMessage("punishment.reloaded", sender));
            return true;
        }

        sendHelpMenu(sender);
        return true;
    }

    private void sendHelpMenu(CommandSender sender) {
        String lang = languageManager.getPlayerLanguage(sender);
        String discordUrl = getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        sender.sendMessage(CompatUtils.color("&b================================================="));
        sender.sendMessage(languageManager.getMessage("help.header", lang, "version", "3.0"));
        sender.sendMessage(languageManager.getMessage("help.author", lang));
        sender.sendMessage(CompatUtils.color("&b-------------------------------------------------"));
        sender.sendMessage(languageManager.getMessage("help.menu", lang));
        sender.sendMessage(languageManager.getMessage("help.inspect", lang));
        sender.sendMessage(languageManager.getMessage("help.freeze", lang));
        sender.sendMessage(languageManager.getMessage("help.unfreeze", lang));
        sender.sendMessage(languageManager.getMessage("help.lang", lang));
        sender.sendMessage(languageManager.getMessage("help.reload", lang));
        sender.sendMessage(languageManager.getMessage("help.discord", lang, "url", discordUrl));

        if (sender instanceof Player p && !languageManager.hasExplicitLanguage(p)) {
            languageManager.sendLanguageSelectorPrompt(p);
        } else {
            sender.sendMessage(CompatUtils.color("&b================================================="));
        }
    }
}
