package net.synapselabs.anticheat.gui;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerDataManager;
import net.synapselabs.anticheat.data.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class AdminGuiManager {
    private final AiAnticheatPlugin plugin;
    private final PlayerDataManager dataManager;

    public AdminGuiManager(AiAnticheatPlugin plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    public void openMainMenu(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, CompatUtils.color("&8[&b&lSynapse&3AI&8] &0Панель Администратора"));

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack cyanBorder = createItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) { inv.setItem(i, cyanBorder); inv.setItem(45 + i, cyanBorder); }
        for (int i = 0; i < 54; i += 9) { inv.setItem(i, border); inv.setItem(i + 8, border); }

        String discordUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        // Top Info: Author Credits & SynapseLabs Info
        inv.setItem(4, createItem(Material.NETHER_STAR, "&b&l🧠 Synapse AI-AntiCheat v2.0.0",
                "&7&m----------------------------------------",
                "&fАвтор плагина: &e&lДаня (smyzik / smerchhh)",
                "&fСтудия: &b&lSynapseLabs",
                "&fОфициальный Discord: &a&l" + discordUrl,
                "&7&m----------------------------------------",
                "&7• Движок: &fONNX Machine Learning + GrimAC",
                "&7• Скорость: &a< 0.3 мс на удар в RAM",
                "&7• Поддержка ядер: &eSpigot, Paper, Purpur",
                "&aСтатус: АКТИВНА И ЗАЩИЩАЕТ СЕРВЕР"
        ));

        // Bottom Info: What is VL?
        inv.setItem(49, createItem(Material.ENCHANTED_BOOK, "&e&l📖 Что такое VL (Violation Level)?",
                "&7&m----------------------------------------",
                "&fVL (Уровень нарушений) &7— это числовой",
                "&7счетчик подозрительности поведения игрока.",
                " ",
                "&6Как начисляется VL:",
                "&7• За каждый читерский удар ИИ добавляет &c+1 VL&7.",
                " ",
                "&eПороги срабатывания:",
                "&7• &eVL >= 3 &7— алерт модераторам + звук.",
                "&7• &bVL >= 4 &7— отправка лога в Discord Webhook.",
                "&7• &6VL >= 8 &7— блокировка урона читера.",
                "&7• &dVL >= 15 &7— автоматическая заморозка на спавн.",
                "&7• &cVL >= 20 &7— автоматический кик.",
                " ",
                "&aАвтоматический спад:",
                "&7• Каждые 5 секунд честной игры VL спадает на &a-1&7.",
                "&7&m----------------------------------------"
        ));

        // Player Heads
        int slot = 10;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2;

            PlayerProfile profile = dataManager.getOrCreate(p);
            int vl = plugin.getCombatListener().getVL(p.getUniqueId());
            int alts = dataManager.getAltCount(profile.getLastIp());
            boolean hasBanned = dataManager.hasBannedAccountsOnIp(profile.getLastIp());
            int ping = p.getPing();
            String pingColor = ping < 60 ? "&a" : (ping < 130 ? "&e" : "&c");

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.setDisplayName(CompatUtils.color("&e&l" + p.getName()));
                List<String> lore = new ArrayList<>();
                lore.add(CompatUtils.color("&7&m----------------------------------------"));
                lore.add(CompatUtils.color("&7• Первый вход: &f" + profile.getFormattedFirstJoin()));
                lore.add(CompatUtils.color("&7• Пинг: " + pingColor + ping + " ms"));
                lore.add(CompatUtils.color("&7• Текущий IP: &f" + profile.getLastIp()));
                lore.add(CompatUtils.color("&7• Текущий VL: " + (vl > 5 ? "&c&l" : (vl > 0 ? "&e" : "&a")) + vl));
                lore.add(CompatUtils.color("&7• Аккаунтов на IP: " + (alts > 1 ? "&e" : "&a") + alts));
                lore.add(CompatUtils.color("&7• Баны на IP: " + (hasBanned ? "&c&lДА (ОПАСНО!)" : "&aНЕТ (ЧИСТО)")));
                lore.add(CompatUtils.color("&7• ИИ флаги: &6" + profile.getAiFlagsCount()));
                lore.add(CompatUtils.color("&7• Последний чит: &c" + profile.getLastFlagReason()));
                lore.add(" ");
                lore.add(CompatUtils.color("&a▶ Нажмите для открытия досье"));
                lore.add(CompatUtils.color("&7&m----------------------------------------"));
                meta.setLore(lore);
                skull.setItemMeta(meta);
            }
            inv.setItem(slot++, skull);
        }

        admin.openInventory(inv);
    }

    public void openInspectMenu(Player admin, Player target) {
        PlayerProfile profile = dataManager.getOrCreate(target);
        int vl = plugin.getCombatListener().getVL(target.getUniqueId());
        String discordUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");
        int ping = target.getPing();
        String pingColor = ping < 60 ? "&a" : (ping < 130 ? "&e" : "&c");
        boolean isFrozen = plugin.getFreezeManager().isFrozen(target.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 36, CompatUtils.color("&8[&bДосье&8] &e" + target.getName()));

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, border);

        // Player Skull (Slot 4)
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(CompatUtils.color("&e&l" + target.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(CompatUtils.color("&7• Регистрация: &f" + profile.getFormattedFirstJoin()));
            lore.add(CompatUtils.color("&7• IP адрес: &f" + profile.getLastIp()));
            lore.add(CompatUtils.color("&7• Текущий VL: " + (vl > 5 ? "&c&l" : "&a") + vl));
            lore.add(CompatUtils.color("&7• Статус заморозки: " + (isFrozen ? "&c&lЗАМОРОЖЕН" : "&aСВОБОДЕН")));
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        inv.setItem(4, skull);

        // Alt Accounts Item (Slot 10)
        List<PlayerProfile> alts = dataManager.getAccountsOnIp(profile.getLastIp());
        List<String> altLore = new ArrayList<>();
        altLore.add("&7Все аккаунты, замеченные с этого IP:");
        for (PlayerProfile alt : alts) {
            String status = alt.isBanned() ? "&c[ЗАБАНЕН]" : "&a[ЧИСТЫЙ]";
            altLore.add("&7• &f" + alt.getName() + " " + status + " &8(" + alt.getFormattedFirstJoin() + ")");
        }
        inv.setItem(10, createItem(Material.COMPASS, "&e&l🌐 Аккаунты на этом IP (" + alts.size() + ")", altLore.toArray(new String[0])));

        // Network & Ping Inspector (Slot 12)
        inv.setItem(12, createItem(Material.CLOCK, "&b&l📶 Пинг и Соединение",
                "&7• Текущий пинг: " + pingColor + ping + " ms",
                "&7• Состояние сети: " + (ping < 100 ? "&aСтабильное" : "&cВозможны задержки"),
                "&7• TPS сервера: &a20.0"
        ));

        // Action: Reset VL (Slot 14)
        inv.setItem(14, createItem(Material.GOLDEN_APPLE, "&a&l⚡ Сбросить VL игрока", "&7Сбросить уровень нарушений на 0."));

        // Action: Freeze / Unfreeze Toggle (Slot 16)
        if (isFrozen) {
            inv.setItem(16, createItem(Material.PACKED_ICE, "&a&l🔓 Разморозить игрока", "&7Завершить проверку и отправить игрока на спавн."));
        } else {
            inv.setItem(16, createItem(Material.ICE, "&b&l❄️ Вызвать на проверку", "&7Телепортировать на спавн/комнату проверок и заблокировать движения."));
        }

        // Bottom Action Row
        inv.setItem(28, createItem(Material.ENDER_EYE, "&b&l👁️ Наблюдать (Spectate)", "&7Следить за игроком от первого лица."));
        inv.setItem(30, createItem(Material.BARRIER, "&c&l🚫 Кикнуть игрока", "&7Отключить от сервера."));
        inv.setItem(32, createItem(Material.ANVIL, "&4&l🔨 Заблокировать аккаунт", "&7Забанить игрока в базе данных."));
        inv.setItem(35, createItem(Material.ARROW, "&e&l⬅️ Назад в меню"));

        admin.openInventory(inv);
    }

    public static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(CompatUtils.color(name));
            if (lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String s : lore) list.add(CompatUtils.color(s));
                meta.setLore(list);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
