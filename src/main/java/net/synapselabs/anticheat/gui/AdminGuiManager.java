package net.synapselabs.anticheat.gui;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerDataManager;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.data.ThreatState;
import net.synapselabs.anticheat.lang.LanguageManager;
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
        LanguageManager langMgr = plugin.getLanguageManager();
        String lang = langMgr != null ? langMgr.getPlayerLanguage(admin) : "en";

        String title = langMgr != null ? langMgr.getMessage("gui.main_title", lang) : "Admin Dashboard | Synapse AI";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack cyanBorder = createItem(Material.CYAN_STAINED_GLASS_PANE, " ");
        ItemStack blueBorder = createItem(Material.BLUE_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, cyanBorder);
            inv.setItem(45 + i, blueBorder);
        }
        for (int i = 0; i < 54; i += 9) {
            inv.setItem(i, border);
            inv.setItem(i + 8, border);
        }

        String discordUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");
        String grimStatus = plugin.getGrimAcBridge().isGrimLoaded()
            ? (langMgr != null ? langMgr.getRaw("gui.grim_connected", lang) : "CONNECTED (HYBRID)")
            : (langMgr != null ? langMgr.getRaw("gui.grim_standalone", lang) : "STANDALONE");

        // Top Info: AI Engine Shield Status
        String topTitle = langMgr != null ? langMgr.getRaw("gui.top_title", lang) : "&#00d2ff&l🧠 Synapse AI-AntiCheat v3.0";
        String engineStr = langMgr != null ? langMgr.getRaw("gui.engine", lang) : "&fEngine: &#00d2ffONNX Runtime Machine Learning";
        String speedStr = langMgr != null ? langMgr.getRaw("gui.inference_speed", lang) : "&fInference latency: &#00ff88< 0.3 ms (RAM ThreadPool)";
        String grimStr = langMgr != null ? langMgr.getMessage("gui.grim_status", lang, "status", grimStatus) : "&fGrimAC: " + grimStatus;
        String discStr = langMgr != null ? langMgr.getMessage("gui.discord", lang, "url", discordUrl) : "&fDiscord: " + discordUrl;
        String activeStr = langMgr != null ? langMgr.getRaw("gui.system_active", lang) : "&#00ff88✔ SYSTEM ACTIVE & PROTECTING SERVER";

        inv.setItem(4, createItem(Material.NETHER_STAR, topTitle,
            "&7&m----------------------------------------",
            engineStr,
            speedStr,
            grimStr,
            discStr,
            "&7&m----------------------------------------",
            activeStr
        ));

        // Bottom Info: AI Architecture & Threat States
        String threatTitle = langMgr != null ? langMgr.getRaw("gui.threat_info_title", lang) : "&#ffa500&l⚡ AI Architecture & Threat Levels";
        String cleanDesc = langMgr != null ? langMgr.getRaw("gui.threat_clean", lang) : "&#00ff88✔ CLEAN (0-24%)";
        String suspDesc = langMgr != null ? langMgr.getRaw("gui.threat_suspicious", lang) : "&#ffaa00⚠ SUSPICIOUS (25-59%)";
        String cheatDesc = langMgr != null ? langMgr.getRaw("gui.threat_cheat", lang) : "&#ff2244⚡ CHEAT (60-100%)";

        inv.setItem(49, createItem(Material.COMPASS, threatTitle,
            "&7&m----------------------------------------",
            cleanDesc,
            suspDesc,
            cheatDesc,
            "&7&m----------------------------------------"
        ));

        // Online Players Heads
        int slot = 10;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2;

            PlayerProfile profile = dataManager.getOrCreate(p);
            int kScore = Math.round(profile.getKillauraConfidence() * 100.0f);
            int aScore = Math.round(profile.getAimConfidence() * 100.0f);
            ThreatState state = profile.getThreatState();

            int alts = dataManager.getAltCount(profile.getLastIp());
            boolean hasBanned = dataManager.hasBannedAccountsOnIp(profile.getLastIp());
            int ping = p.getPing();
            String pingColor = ping < 60 ? "&#00ff88" : (ping < 130 ? "&#ffaa00" : "&#ff2244");
            String bannedStatus = hasBanned
                ? (langMgr != null ? langMgr.getRaw("gui.banned_yes", lang) : "&#ff2244&lYES")
                : (langMgr != null ? langMgr.getRaw("gui.banned_no", lang) : "&#00ff88NO");
            String threatTag = langMgr != null ? langMgr.getRaw(state.messageKey(), lang) : state.getDisplayTag();

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.setDisplayName(CompatUtils.color("&#ffa500&l" + p.getName()));
                List<String> lore = new ArrayList<>();
                lore.add(CompatUtils.color("&7&m----------------------------------------"));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_threat", lang, "state", threatTag)
                    : "&7• Status: " + threatTag));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_killaura", lang, "bar", renderBar(kScore, 10, "&#ff2244", "&#ffaa00", "&#00ff88"), "score", kScore)
                    : "&7• Killaura: " + kScore + "%"));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_aim", lang, "bar", renderBar(aScore, 10, "&#ff2244", "&#ffaa00", "&#00ff88"), "score", aScore)
                    : "&7• Aim Assist: " + aScore + "%"));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_ping", lang, "color", pingColor, "ping", ping)
                    : "&7• Ping: " + ping + "ms"));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_ip", lang, "ip", profile.getLastIp())
                    : "&7• IP: " + profile.getLastIp()));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_alts", lang, "color", (alts > 1 ? "&#ffaa00" : "&#00ff88"), "alts", alts)
                    : "&7• Alts: " + alts));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_banned_on_ip", lang, "status", bannedStatus)
                    : "&7• Banned on IP: " + bannedStatus));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_flags", lang, "total", profile.getTotalFlags(), "hard", profile.getHardFlags(), "ai", profile.getAiFlags())
                    : "&7• Total flags: " + profile.getTotalFlags()));
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getMessage("gui.player_last_flag", lang, "reason", profile.getLastFlagReason())
                    : "&7• Last flag: " + profile.getLastFlagReason()));
                lore.add(" ");
                lore.add(CompatUtils.color(langMgr != null
                    ? langMgr.getRaw("gui.player_click_inspect", lang)
                    : "&#00ff88▶ Click to open player dossier"));
                lore.add(CompatUtils.color("&7&m----------------------------------------"));
                meta.setLore(lore);
                skull.setItemMeta(meta);
            }
            inv.setItem(slot++, skull);
        }

        admin.openInventory(inv);
    }

    public void openInspectMenu(Player admin, Player target) {
        LanguageManager langMgr = plugin.getLanguageManager();
        String lang = langMgr != null ? langMgr.getPlayerLanguage(admin) : "en";

        PlayerProfile profile = dataManager.getOrCreate(target);
        int ping = target.getPing();
        String pingColor = ping < 60 ? "&#00ff88" : (ping < 130 ? "&#ffaa00" : "&#ff2244");
        boolean isFrozen = plugin.getFreezeManager().isFrozen(target.getUniqueId());

        int kScore = Math.round(profile.getKillauraConfidence() * 100.0f);
        int aScore = Math.round(profile.getAimConfidence() * 100.0f);
        int suspScore = Math.round(profile.getSuspicion() * 100.0f);
        ThreatState state = profile.getThreatState();
        String threatTag = langMgr != null ? langMgr.getRaw(state.messageKey(), lang) : state.getDisplayTag();
        String freezeStatus = isFrozen
            ? (langMgr != null ? langMgr.getRaw("gui.freeze_frozen", lang) : "&#ff2244&lFROZEN")
            : (langMgr != null ? langMgr.getRaw("gui.freeze_free", lang) : "&#00ff88FREE");

        String title = langMgr != null
            ? langMgr.getMessage("gui.inspect_title", lang, "player", target.getName())
            : "&#00d2ff[Dossier] &#ffa500" + target.getName();
        Inventory inv = Bukkit.createInventory(null, 36, title);

        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, border);

        // Player Skull (Slot 4)
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(CompatUtils.color("&#ffa500&l" + target.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(CompatUtils.color(langMgr != null
                ? langMgr.getMessage("gui.inspect_first_join", lang, "time", profile.getFormattedFirstJoin())
                : "&7• First login: " + profile.getFormattedFirstJoin()));
            lore.add(CompatUtils.color(langMgr != null
                ? langMgr.getMessage("gui.inspect_ip", lang, "ip", profile.getLastIp())
                : "&7• IP: " + profile.getLastIp()));
            lore.add(CompatUtils.color(langMgr != null
                ? langMgr.getMessage("gui.inspect_status", lang, "state", threatTag)
                : "&7• Status: " + threatTag));
            lore.add(CompatUtils.color(langMgr != null
                ? langMgr.getMessage("gui.inspect_freeze_state", lang, "state", freezeStatus)
                : "&7• Freeze: " + freezeStatus));
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        inv.setItem(4, skull);

        // Known Accounts on IP (Slot 10)
        List<PlayerProfile> alts = dataManager.getAccountsOnIp(profile.getLastIp());
        List<String> altLore = new ArrayList<>();
        altLore.add(langMgr != null
            ? langMgr.getMessage("gui.inspect_alts_header", lang, "ip", profile.getLastIp())
            : "&7All accounts on IP " + profile.getLastIp() + ":");
        for (PlayerProfile alt : alts) {
            String bStatus = alt.isBanned()
                ? (lang.equals("ru") ? "&c[ЗАБАНЕН]" : "&c[BANNED]")
                : (lang.equals("ru") ? "&a[ЧИСТЫЙ]" : "&a[CLEAN]");
            altLore.add("&7• &f" + alt.getName() + " " + bStatus + " &8(" + alt.getFormattedFirstJoin() + ")");
        }
        String altsTitle = langMgr != null
            ? langMgr.getMessage("gui.inspect_alts_title", lang, "count", alts.size())
            : "&#ffa500&l🌐 Known Accounts (" + alts.size() + ")";
        inv.setItem(10, createItem(Material.BOOKSHELF, altsTitle, altLore.toArray(new String[0])));

        // AI Combat Diagnostics (Slot 12)
        String diagTitle = langMgr != null ? langMgr.getRaw("gui.inspect_combat_diag", lang) : "&#00d2ff&l🎯 AI Combat Diagnostics";
        inv.setItem(12, createItem(Material.TARGET, diagTitle,
            langMgr != null ? langMgr.getMessage("gui.player_killaura", lang, "bar", renderBar(kScore, 10, "&#ff2244", "&#ffaa00", "&#00ff88"), "score", kScore) : "&7• Killaura: " + kScore + "%",
            langMgr != null ? langMgr.getMessage("gui.player_aim", lang, "bar", renderBar(aScore, 10, "&#ff2244", "&#ffaa00", "&#00ff88"), "score", aScore) : "&7• Aim Assist: " + aScore + "%",
            langMgr != null ? langMgr.getMessage("gui.player_risk", lang, "bar", renderBar(suspScore, 10, "&#ff2244", "&#ffaa00", "&#00ff88"), "score", suspScore) : "&7• Risk: " + suspScore + "%",
            "&7• Hard: &#00d2ff" + profile.getHardFlags(),
            "&7• AI: &#ff7700" + profile.getAiFlags(),
            "&7• GrimAC: &#38bdf8" + profile.getGrimFlagsCount(),
            langMgr != null ? langMgr.getMessage("gui.player_last_flag", lang, "reason", profile.getLastFlagReason()) : "&7• Last Flag: " + profile.getLastFlagReason()
        ));

        // Network & Ping Inspector (Slot 14)
        String quality = ping < 100
            ? (langMgr != null ? langMgr.getRaw("gui.network_stable", lang) : "&#00ff88Stable")
            : (langMgr != null ? langMgr.getRaw("gui.network_lag", lang) : "&#ffaa00High Jitter / Lag");
        String netTitle = langMgr != null ? langMgr.getRaw("gui.inspect_network_diag", lang) : "&#00d2ff&l📶 Network Diagnostics";
        inv.setItem(14, createItem(Material.CLOCK, netTitle,
            langMgr != null ? langMgr.getMessage("gui.player_ping", lang, "color", pingColor, "ping", ping) : "&7• Ping: " + ping + "ms",
            langMgr != null ? langMgr.getMessage("gui.inspect_network_quality", lang, "quality", quality) : "&7• Quality: " + quality,
            "&7• TPS: &#00ff8820.0",
            "&7• GrimAC Sync: &#00ff88" + (plugin.getGrimAcBridge().isGrimLoaded() ? "OK" : "N/A")
        ));

        // Freeze / Unfreeze Toggle (Slot 16)
        if (isFrozen) {
            String unfreezeBtn = langMgr != null ? langMgr.getRaw("gui.inspect_btn_unfreeze", lang) : "&#00ff88&l🔓 Unfreeze Player";
            String unfreezeLore = langMgr != null ? langMgr.getRaw("gui.inspect_btn_unfreeze_lore", lang) : "&7Finish inspection and send to spawn.";
            inv.setItem(16, createItem(Material.PACKED_ICE, unfreezeBtn, unfreezeLore));
        } else {
            String freezeBtn = langMgr != null ? langMgr.getRaw("gui.inspect_btn_freeze", lang) : "&#00d2ff&l❄️ Call for Inspection";
            String freezeLore = langMgr != null ? langMgr.getRaw("gui.inspect_btn_freeze_lore", lang) : "&7Teleport to inspection room and freeze movements.";
            inv.setItem(16, createItem(Material.ICE, freezeBtn, freezeLore));
        }

        // Bottom Action Row
        String resetBtn = langMgr != null ? langMgr.getRaw("gui.inspect_btn_reset", lang) : "&#00ff88&l⚡ Reset Flags";
        String resetLore = langMgr != null ? langMgr.getRaw("gui.inspect_btn_reset_lore", lang) : "&7Clear flags and suspicion.";
        inv.setItem(28, createItem(Material.GOLDEN_APPLE, resetBtn, resetLore));

        String spectateBtn = langMgr != null ? langMgr.getRaw("gui.inspect_btn_spectate", lang) : "&#a855f7&l👁️ Spectate";
        String spectateLore = langMgr != null ? langMgr.getRaw("gui.inspect_btn_spectate_lore", lang) : "&7Watch player in first person mode.";
        inv.setItem(30, createItem(Material.ENDER_EYE, spectateBtn, spectateLore));

        String kickBtn = langMgr != null ? langMgr.getRaw("gui.inspect_btn_kick", lang) : "&#ffaa00&l🚫 Kick Player";
        String kickLore = langMgr != null ? langMgr.getRaw("gui.inspect_btn_kick_lore", lang) : "&7Disconnect suspect from server.";
        inv.setItem(32, createItem(Material.BARRIER, kickBtn, kickLore));

        String banBtn = langMgr != null ? langMgr.getRaw("gui.inspect_btn_ban", lang) : "&#ff2244&l🔨 Ban Account";
        String banLore = langMgr != null ? langMgr.getRaw("gui.inspect_btn_ban_lore", lang) : "&7Issue ban via punishment system.";
        inv.setItem(34, createItem(Material.ANVIL, banBtn, banLore));

        String backBtn = langMgr != null ? langMgr.getRaw("gui.inspect_btn_back", lang) : "&#ffa500&l⬅️ Back to Menu";
        inv.setItem(35, createItem(Material.ARROW, backBtn));

        admin.openInventory(inv);
    }

    public static String renderBar(int percent, int totalBars, String dangerColor, String warningColor, String safeColor) {
        int filled = (int) Math.round((percent / 100.0) * totalBars);
        String color = percent >= 70 ? dangerColor : (percent >= 40 ? warningColor : safeColor);
        StringBuilder sb = new StringBuilder("&8[");
        sb.append(color);
        for (int i = 0; i < totalBars; i++) {
            if (i < filled) {
                sb.append("█");
            } else {
                sb.append("&7░");
            }
        }
        sb.append("&8]");
        return sb.toString();
    }

    public static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(CompatUtils.color(name));
            if (lore != null && lore.length > 0) {
                List<String> list = new ArrayList<>();
                for (String s : lore) {
                    if (s != null) list.add(CompatUtils.color(s));
                }
                meta.setLore(list);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
