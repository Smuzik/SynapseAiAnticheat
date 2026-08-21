package net.synapselabs.anticheat.gui;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class GuiListener implements Listener {
    private final AiAnticheatPlugin plugin;

    public GuiListener(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        String rawTitle = event.getView().getTitle();
        String plainTitle = ChatColor.stripColor(rawTitle);

        boolean isMainMenu = plainTitle.contains("Панель Администратора")
                || plainTitle.contains("Admin Dashboard")
                || plainTitle.contains("Synapse AI");
        boolean isInspectMenu = plainTitle.contains("Досье")
                || plainTitle.contains("Dossier")
                || plainTitle.contains("[Досье]")
                || plainTitle.contains("[Dossier]");

        LanguageManager langMgr = plugin.getLanguageManager();
        String lang = langMgr != null ? langMgr.getPlayerLanguage(admin) : "en";

        if (isMainMenu) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            if (clicked.getType() == Material.PLAYER_HEAD && clicked.getItemMeta() instanceof SkullMeta meta) {
                if (meta.getOwningPlayer() != null && meta.getOwningPlayer().getPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    plugin.getGuiManager().openInspectMenu(admin, target);
                } else if (meta.hasDisplayName()) {
                    String cleanName = ChatColor.stripColor(meta.getDisplayName()).trim();
                    Player target = Bukkit.getPlayer(cleanName);
                    if (target != null) {
                        plugin.getGuiManager().openInspectMenu(admin, target);
                    }
                }
            }
        } else if (isInspectMenu) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            Player target = null;
            Inventory inv = event.getInventory();
            ItemStack headItem = inv.getItem(4);
            if (headItem != null && headItem.getItemMeta() instanceof SkullMeta meta && meta.getOwningPlayer() != null) {
                target = meta.getOwningPlayer().getPlayer();
            }

            if (target == null) {
                String targetName = plainTitle.replace("[Досье]", "").replace("[Dossier]", "")
                        .replace("Досье", "").replace("Dossier", "")
                        .replace("|", "").trim();
                target = Bukkit.getPlayerExact(targetName);
                if (target == null) target = Bukkit.getPlayer(targetName);
            }

            if (target == null && clicked.getType() != Material.ARROW) {
                String offlineMsg = langMgr != null ? langMgr.getMessage("gui.target_offline", lang) : "Target player is offline.";
                CompatUtils.sendMessage(admin, offlineMsg);
                admin.closeInventory();
                return;
            }

            Material mat = clicked.getType();

            switch (mat) {
                case ARROW -> plugin.getGuiManager().openMainMenu(admin);
                case GOLDEN_APPLE -> {
                    if (target != null) {
                        PlayerProfile profile = plugin.getDataManager().getOrCreate(target);
                        profile.resetFlags();
                        var tracker = plugin.getCombatListener().getTracker(target.getUniqueId());
                        if (tracker != null) tracker.reset();
                        plugin.getOverheadDisplayManager().updateDisplay(target.getUniqueId(), 0, 0, net.synapselabs.anticheat.data.ThreatState.CLEAN);
                        String resetMsg = langMgr != null
                            ? langMgr.getMessage("gui.flags_reset", lang, "player", target.getName())
                            : "Flags and suspicion reset for " + target.getName();
                        CompatUtils.sendMessage(admin, resetMsg);
                        plugin.getGuiManager().openInspectMenu(admin, target);
                    }
                }
                case ICE, PACKED_ICE -> {
                    if (target != null) {
                        if (plugin.getFreezeManager().isFrozen(target.getUniqueId())) {
                            if (plugin.getFreezeManager().unfreeze(target)) {
                                String unfreezeStaff = langMgr != null
                                    ? langMgr.getMessage("freeze.unfrozen_staff", lang, "player", target.getName())
                                    : "Player " + target.getName() + " unfrozen.";
                                CompatUtils.sendMessage(admin, unfreezeStaff);
                            } else {
                                String waitMsg = langMgr != null
                                    ? langMgr.getMessage("freeze.cooldown_wait", lang, "seconds", plugin.getFreezeManager().getRemainingCooldownSeconds(target.getUniqueId()))
                                    : "Please wait before repeating this action.";
                                CompatUtils.sendMessage(admin, waitMsg);
                            }
                        } else {
                            String freezeReason = langMgr != null ? langMgr.getMessage("gui.freeze_reason", lang) : "Inspection by Admin";
                            if (plugin.getFreezeManager().freeze(target, freezeReason, admin)) {
                                String frozenStaff = langMgr != null
                                    ? langMgr.getMessage("freeze.frozen_staff", lang, "player", target.getName())
                                    : "Player " + target.getName() + " frozen for inspection.";
                                CompatUtils.sendMessage(admin, frozenStaff);
                            } else {
                                String waitMsg = langMgr != null
                                    ? langMgr.getMessage("freeze.cooldown_wait", lang, "seconds", plugin.getFreezeManager().getRemainingCooldownSeconds(target.getUniqueId()))
                                    : "Please wait before repeating this action.";
                                CompatUtils.sendMessage(admin, waitMsg);
                            }
                        }
                        plugin.getGuiManager().openInspectMenu(admin, target);
                    }
                }
                case ENDER_EYE -> {
                    if (target != null) {
                        admin.setGameMode(GameMode.SPECTATOR);
                        admin.teleport(target.getLocation());
                        String specMsg = langMgr != null
                            ? langMgr.getMessage("gui.spectate_msg", lang, "player", target.getName())
                            : "Spectating " + target.getName();
                        CompatUtils.sendMessage(admin, specMsg);
                        admin.closeInventory();
                    }
                }
                case BARRIER -> {
                    if (target != null) {
                        String kickClient = langMgr != null ? langMgr.getMessage("gui.kick_msg", lang) : "Disconnected by admin.";
                        target.kickPlayer(CompatUtils.color(kickClient));
                        String kickAdmin = langMgr != null
                            ? langMgr.getMessage("gui.kick_admin", lang, "player", target.getName())
                            : "Player " + target.getName() + " kicked.";
                        CompatUtils.sendMessage(admin, kickAdmin);
                        plugin.getGuiManager().openMainMenu(admin);
                    }
                }
                case ANVIL -> {
                    if (target != null) {
                        PlayerProfile profile = plugin.getDataManager().getOrCreate(target);
                        profile.setBanned(true);
                        plugin.getDataManager().saveDatabase();
                        String banReason = langMgr != null ? langMgr.getMessage("gui.ban_reason", lang) : "Cheating";
                        plugin.getPunishmentManager().banPlayer(target, banReason, admin.getName(), false);
                        String banAdmin = langMgr != null
                            ? langMgr.getMessage("gui.ban_admin", lang, "player", target.getName(), "system", plugin.getPunishmentManager().getActivePlugin().getDisplay())
                            : "Player " + target.getName() + " banned.";
                        CompatUtils.sendMessage(admin, banAdmin);
                        plugin.getGuiManager().openMainMenu(admin);
                    }
                }
                default -> {}
            }
        }
    }
}
