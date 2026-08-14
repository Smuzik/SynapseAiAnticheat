package net.synapselabs.anticheat.gui;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerProfile;
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

        if (plainTitle.contains("Панель Администратора")) {
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
        } else if (plainTitle.contains("Досье")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            // Extract target cleanly from title and/or from slot 4 player head
            Player target = null;
            Inventory inv = event.getInventory();
            ItemStack headItem = inv.getItem(4);
            if (headItem != null && headItem.getItemMeta() instanceof SkullMeta meta && meta.getOwningPlayer() != null) {
                target = meta.getOwningPlayer().getPlayer();
            }

            if (target == null) {
                String targetName = plainTitle.replace("[Досье]", "").trim();
                target = Bukkit.getPlayerExact(targetName);
                if (target == null) target = Bukkit.getPlayer(targetName);
            }

            if (target == null && clicked.getType() != Material.ARROW) {
                CompatUtils.sendMessage(admin, "&c[SynapseAI] Целевой игрок вышел из сети.");
                admin.closeInventory();
                return;
            }

            Material mat = clicked.getType();

            switch (mat) {
                case ARROW -> {
                    plugin.getGuiManager().openMainMenu(admin);
                }
                case GOLDEN_APPLE -> {
                    if (target != null) {
                        plugin.getCombatListener().resetVL(target.getUniqueId());
                        CompatUtils.sendMessage(admin, "&a[SynapseAI] Уровень VL для игрока &e" + target.getName() + " &aуспешно сброшен на 0!");
                        plugin.getGuiManager().openInspectMenu(admin, target);
                    }
                }
                case ICE, PACKED_ICE -> {
                    if (target != null) {
                        if (plugin.getFreezeManager().isFrozen(target.getUniqueId())) {
                            if (plugin.getFreezeManager().unfreeze(target)) {
                                CompatUtils.sendMessage(admin, "&a[SynapseAI] Игрок &e" + target.getName() + " &aразморожен и отправлен на спавн.");
                            } else {
                                CompatUtils.sendMessage(admin, "&c[SynapseAI] Подождите " + plugin.getFreezeManager().getRemainingCooldownSeconds(target.getUniqueId()) + " сек перед повторным действием!");
                            }
                        } else {
                            if (plugin.getFreezeManager().freeze(target, "Вызов на проверку администратором", admin)) {
                                CompatUtils.sendMessage(admin, "&b[SynapseAI] Игрок &e" + target.getName() + " &bвызван на проверку и заморожен!");
                            } else {
                                CompatUtils.sendMessage(admin, "&c[SynapseAI] Подождите " + plugin.getFreezeManager().getRemainingCooldownSeconds(target.getUniqueId()) + " сек перед повторным действием!");
                            }
                        }
                        plugin.getGuiManager().openInspectMenu(admin, target);
                    }
                }
                case ENDER_EYE -> {
                    if (target != null) {
                        admin.setGameMode(GameMode.SPECTATOR);
                        admin.teleport(target.getLocation());
                        CompatUtils.sendMessage(admin, "&b[SynapseAI] Вы наблюдаете за игроком &e" + target.getName() + " &bв режиме Наблюдателя.");
                        admin.closeInventory();
                    }
                }
                case BARRIER -> {
                    if (target != null) {
                        target.kickPlayer(CompatUtils.color("&c[SynapseAI] Вы были отключены администратором."));
                        CompatUtils.sendMessage(admin, "&c[SynapseAI] Игрок &e" + target.getName() + " &cбыл кикнут.");
                        plugin.getGuiManager().openMainMenu(admin);
                    }
                }
                case ANVIL -> {
                    if (target != null) {
                        PlayerProfile profile = plugin.getDataManager().getOrCreate(target);
                        profile.setBanned(true);
                        plugin.getDataManager().saveDatabase();
                        plugin.getPunishmentManager().banPlayer(target, "Использование читов", admin.getName(), false);
                        CompatUtils.sendMessage(admin, "&4[SynapseAI] Игрок &e" + target.getName() + " &4был забанен через " + plugin.getPunishmentManager().getActivePlugin().getDisplay() + "!");
                        plugin.getGuiManager().openMainMenu(admin);
                    }
                }
                default -> {}
            }
        }
    }
}
