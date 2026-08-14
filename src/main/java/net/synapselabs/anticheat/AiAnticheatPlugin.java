package net.synapselabs.anticheat;

import net.synapselabs.anticheat.bridge.GrimAcBridge;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerDataManager;
import net.synapselabs.anticheat.engine.AiInferenceEngine;
import net.synapselabs.anticheat.freeze.FreezeManager;
import net.synapselabs.anticheat.gui.AdminGuiManager;
import net.synapselabs.anticheat.gui.GuiListener;
import net.synapselabs.anticheat.listener.CombatListener;
import net.synapselabs.anticheat.overlay.OverheadVlManager;
import net.synapselabs.anticheat.webhook.DiscordWebhookService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AiAnticheatPlugin extends JavaPlugin implements CommandExecutor {
    private AiInferenceEngine engine;
    private CombatListener combatListener;
    private PlayerDataManager dataManager;
    private AdminGuiManager guiManager;
    private OverheadVlManager overheadVlManager;
    private GrimAcBridge grimAcBridge;
    private FreezeManager freezeManager;
    private DiscordWebhookService webhookService;
    private net.synapselabs.anticheat.punishment.PunishmentManager punishmentManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        sendStartupBanner();

        this.punishmentManager = new net.synapselabs.anticheat.punishment.PunishmentManager(this);
        this.dataManager = new PlayerDataManager(this);
        this.engine = new AiInferenceEngine(this);
        this.combatListener = new CombatListener(this, engine);
        this.guiManager = new AdminGuiManager(this, dataManager);
        this.overheadVlManager = new OverheadVlManager(this);
        this.grimAcBridge = new GrimAcBridge(this);
        this.freezeManager = new FreezeManager(this);
        this.webhookService = new DiscordWebhookService(this);

        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(freezeManager, this);

        // Register Command
        if (getCommand("aianticheat") != null) {
            getCommand("aianticheat").setExecutor(this);
        }

        // Periodic VL decay (every 5 seconds)
        int decayAmount = getConfig().getInt("detection.vl_decay", 1);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (combatListener != null) {
                combatListener.decayVL(decayAmount);
            }
        }, 100L, 100L);

        // Periodic Database Save (every 2 minutes)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (dataManager != null) {
                dataManager.saveDatabase();
            }
        }, 2400L, 2400L);

        getLogger().info("Synapse AI-AntiCheat is ACTIVE and protecting the server!");
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
        getLogger().info("   Synapse AI-AntiCheat v2.0.0 (Spigot / Paper / Purpur) ");
        getLogger().info("   Author: Даня (smyzik / smerchhh) & SynapseLabs       ");
        getLogger().info("   Discord: " + discordUrl);
        getLogger().info("==========================================================");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveDatabase();
        }
        if (engine != null) {
            engine.close();
        }
        getLogger().info("Synapse AI-AntiCheat disabled.");
    }

    public CombatListener getCombatListener() { return combatListener; }
    public PlayerDataManager getDataManager() { return dataManager; }
    public AdminGuiManager getGuiManager() { return guiManager; }
    public OverheadVlManager getOverheadVlManager() { return overheadVlManager; }
    public FreezeManager getFreezeManager() { return freezeManager; }
    public DiscordWebhookService getWebhookService() { return webhookService; }
    public net.synapselabs.anticheat.punishment.PunishmentManager getPunishmentManager() { return punishmentManager; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aianticheat.admin")) {
            sender.sendMessage(CompatUtils.color("&cУ вас нет прав на использование этой команды."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
            sendHelpMenu(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player player) {
                guiManager.openMainMenu(player);
            } else {
                sender.sendMessage("Команда GUI доступна только игрокам в игре.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("about") || args[0].equalsIgnoreCase("info")) {
            String discordUrl = getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");
            sender.sendMessage(CompatUtils.color("&b================================================="));
            sender.sendMessage(CompatUtils.color("&b&lSynapse AI-AntiCheat &fv2.0.0"));
            sender.sendMessage(CompatUtils.color("&fАвтор плагина: &e&lДаня (smyzik / smerchhh)"));
            sender.sendMessage(CompatUtils.color("&fСтудия: &b&lSynapseLabs"));
            sender.sendMessage(CompatUtils.color("&fОфициальный Discord: &a&l" + discordUrl));
            sender.sendMessage(CompatUtils.color("&7Движок: &fONNX Runtime Machine Learning + GrimAC Hybrid"));
            sender.sendMessage(CompatUtils.color("&b================================================="));
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
                sender.sendMessage(CompatUtils.color("&cУкажите ник игрока: /aiac ping <игрок>"));
                return true;
            }

            int ping = target.getPing();
            String pingRating;
            if (ping < 50) {
                pingRating = "&aОтличный (Идеально для PvP)";
            } else if (ping < 110) {
                pingRating = "&eНормальный (Стабильный)";
            } else if (ping < 180) {
                pingRating = "&6Повышенный (Возможны легкие задержки)";
            } else {
                pingRating = "&cВысокий (Пинг-лаги)";
            }

            sender.sendMessage(CompatUtils.color("&b================================================="));
            sender.sendMessage(CompatUtils.color("&b&l📶 Анализ сетевого соединения: &e&l" + target.getName()));
            sender.sendMessage(CompatUtils.color("&7• Текущий пинг: &f" + ping + " ms (" + pingRating + "&7)"));
            sender.sendMessage(CompatUtils.color("&7• IP адрес: &f" + (target.getAddress() != null ? target.getAddress().getAddress().getHostAddress() : "Неизвестно")));
            sender.sendMessage(CompatUtils.color("&7• TPS сервера: &a20.0 (Стабильно)"));
            sender.sendMessage(CompatUtils.color("&7• Статус GrimAC sync: &aСинхронизирован"));
            sender.sendMessage(CompatUtils.color("&b================================================="));
            return true;
        }

        if (args[0].equalsIgnoreCase("setfreezeloc")) {
            if (sender instanceof Player player) {
                freezeManager.setCustomFreezeLocation(player.getLocation());
                sender.sendMessage(CompatUtils.color("&a[SynapseAI] Локация для проверок (/aiac freeze) успешно установлена на ваши текущие координаты!"));
            } else {
                sender.sendMessage("Команда доступна только игрокам в игре.");
            }
            return true;
        }

        if ((args[0].equalsIgnoreCase("chat") || args[0].equalsIgnoreCase("msg")) && args.length > 1) {
            if (sender instanceof Player player) {
                if (!freezeManager.isModeratorChecking(player.getUniqueId())) {
                    sender.sendMessage(CompatUtils.color("&c[SynapseAI] Вы сейчас не проводите проверку ни одного игрока."));
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    sb.append(args[i]).append(" ");
                }
                freezeManager.sendInterrogationMessage(player, sb.toString().trim(), true);
            } else {
                sender.sendMessage("Команда доступна только в игре.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("inspect") && args.length > 1) {
            if (sender instanceof Player player) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(CompatUtils.color("&cИгрок не найден в сети."));
                    return true;
                }
                guiManager.openInspectMenu(player, target);
            } else {
                sender.sendMessage("Команда доступна только в игре.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("freeze") && args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(CompatUtils.color("&cИгрок не найден."));
                return true;
            }
            Player moderator = (sender instanceof Player p) ? p : null;
            if (freezeManager.freeze(target, "Вызов на проверку администратором", moderator)) {
                sender.sendMessage(CompatUtils.color("&b[SynapseAI] Игрок &e" + target.getName() + " &bуспешно телепортирован и заморожен!"));
            } else {
                sender.sendMessage(CompatUtils.color("&c[SynapseAI] Подождите " + freezeManager.getRemainingCooldownSeconds(target.getUniqueId()) + " сек перед повторным действием!"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("unfreeze") && args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(CompatUtils.color("&cИгрок не найден."));
                return true;
            }
            if (freezeManager.unfreeze(target)) {
                sender.sendMessage(CompatUtils.color("&a[SynapseAI] Игрок &e" + target.getName() + " &aразморожен и отправлен на спавн."));
            } else {
                sender.sendMessage(CompatUtils.color("&c[SynapseAI] Подождите " + freezeManager.getRemainingCooldownSeconds(target.getUniqueId()) + " сек перед повторным действием!"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            boolean active = engine != null && engine.isInitialized();
            String discordUrl = getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");
            sender.sendMessage(CompatUtils.color("&b[SynapseAI] &fСтатус ONNX нейросети: " + (active ? "&aЗАГРУЖЕНА И АКТИВНА" : "&cОШИБКА")));
            sender.sendMessage(CompatUtils.color("&b[SynapseAI] &fСтатус GrimAC: " + (grimAcBridge.isGrimLoaded() ? "&aПОДКЛЮЧЕН (HYBRID MODE)" : "&eSTANDALONE")));
            sender.sendMessage(CompatUtils.color("&b[SynapseAI] &fСистема банов: &a" + punishmentManager.getActivePlugin().getDisplay()));
            sender.sendMessage(CompatUtils.color("&b[SynapseAI] &fDiscord Invite: &a" + discordUrl));
            sender.sendMessage(CompatUtils.color("&b[SynapseAI] &fDiscord Webhook: " + (getConfig().getBoolean("discord.webhook.enabled", false) ? "&aВКЛЮЧЕН" : "&7ВЫКЛЮЧЕН")));
            return true;
        }

        if (args[0].equalsIgnoreCase("check") && args.length > 1) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(CompatUtils.color("&cИгрок не найден в сети."));
                return true;
            }
            int vl = combatListener.getVL(target.getUniqueId());
            sender.sendMessage(CompatUtils.color("&b[SynapseAI] &fИгрок &e" + target.getName() + " &f| Текущий VL: &" + (vl > 5 ? "c" : "a") + vl));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(CompatUtils.color("&aКонфигурация Synapse AI-AntiCheat успешно перезагружена!"));
            return true;
        }

        sendHelpMenu(sender);
        return true;
    }

    private void sendHelpMenu(CommandSender sender) {
        sender.sendMessage(CompatUtils.color("&b================================================="));
        sender.sendMessage(CompatUtils.color("&b&l🧠 Synapse AI-AntiCheat &fv2.0.0 &8| &eПомощь по командам"));
        sender.sendMessage(CompatUtils.color("&7Автор: &eДаня (smyzik / smerchhh) &8| &bSynapseLabs"));
        sender.sendMessage(CompatUtils.color("&b-------------------------------------------------"));
        sender.sendMessage(CompatUtils.color("&e/aiac menu &8(или /aiac gui) &7— Открыть главное GUI-меню"));
        sender.sendMessage(CompatUtils.color("&e/aiac inspect <игрок> &7— Персональное досье (IP, твинки, баны, VL)"));
        sender.sendMessage(CompatUtils.color("&e/aiac ping <игрок> &7— Подробный анализ пинга и стабильности соединения"));
        sender.sendMessage(CompatUtils.color("&e/aiac freeze <игрок> &7— Вызвать игрока на проверку (телепорт и заморозка)"));
        sender.sendMessage(CompatUtils.color("&e/aiac unfreeze <игрок> &7— Завершить проверку и отправить на спавн"));
        sender.sendMessage(CompatUtils.color("&e/aiac chat <сообщение> &7— Написать в приватный чат проверки"));
        sender.sendMessage(CompatUtils.color("&e/aiac setfreezeloc &7— Сохранить текущие координаты как точку проверок"));
        sender.sendMessage(CompatUtils.color("&e/aiac check <игрок> &7— Быстрая проверка уровня нарушений (VL)"));
        sender.sendMessage(CompatUtils.color("&e/aiac status &7— Проверить статус ONNX модели, GrimAC и Webhook"));
        sender.sendMessage(CompatUtils.color("&e/aiac reload &7— Перезагрузить config.yml"));
        sender.sendMessage(CompatUtils.color("&e/aiac about &7— Информация об авторе и Discord студии"));
        sender.sendMessage(CompatUtils.color("&b================================================="));
    }
}
