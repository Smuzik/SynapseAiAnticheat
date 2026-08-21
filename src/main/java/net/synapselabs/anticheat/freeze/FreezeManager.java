package net.synapselabs.anticheat.freeze;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FreezeManager implements Listener {
    private final AiAnticheatPlugin plugin;
    private final Map<UUID, Location> frozenLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> previousLocations = new ConcurrentHashMap<>();
    
    // Cooldown tracking (UUID -> expiration timestamp in millis)
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // Moderator <-> Suspect links for dedicated interrogation chat
    private final Map<UUID, UUID> suspectToModerator = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> moderatorToSuspect = new ConcurrentHashMap<>();

    public FreezeManager(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
        startWarningTask();
    }

    public boolean isFrozen(UUID uuid) {
        return frozenLocations.containsKey(uuid);
    }

    public boolean isModeratorChecking(UUID moderatorUuid) {
        return moderatorToSuspect.containsKey(moderatorUuid);
    }

    public UUID getSuspectForModerator(UUID moderatorUuid) {
        return moderatorToSuspect.get(moderatorUuid);
    }

    public String getDiscordUrl() {
        return plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");
    }

    public boolean hasCooldown(UUID targetUuid) {
        Long exp = cooldowns.get(targetUuid);
        return exp != null && System.currentTimeMillis() < exp;
    }

    public long getRemainingCooldownSeconds(UUID targetUuid) {
        Long exp = cooldowns.get(targetUuid);
        if (exp == null) return 0;
        long diff = exp - System.currentTimeMillis();
        return Math.max(0, (diff + 999) / 1000);
    }

    public void applyCooldown(UUID targetUuid) {
        int cdSec = plugin.getConfig().getInt("freeze.cooldown_seconds", 3);
        cooldowns.put(targetUuid, System.currentTimeMillis() + (cdSec * 1000L));
    }

    public void setCustomFreezeLocation(Location loc) {
        plugin.getConfig().set("freeze.teleport.enabled", true);
        plugin.getConfig().set("freeze.teleport.use_custom_location", true);
        plugin.getConfig().set("freeze.teleport.custom_location.world", loc.getWorld().getName());
        plugin.getConfig().set("freeze.teleport.custom_location.x", loc.getX());
        plugin.getConfig().set("freeze.teleport.custom_location.y", loc.getY());
        plugin.getConfig().set("freeze.teleport.custom_location.z", loc.getZ());
        plugin.getConfig().set("freeze.teleport.custom_location.yaw", (double) loc.getYaw());
        plugin.getConfig().set("freeze.teleport.custom_location.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }

    private Location getTargetFreezeLocation(Player player) {
        if (!plugin.getConfig().getBoolean("freeze.teleport.enabled", true)) {
            return player.getLocation();
        }

        boolean useCustom = plugin.getConfig().getBoolean("freeze.teleport.use_custom_location", false);
        if (useCustom) {
            String worldName = plugin.getConfig().getString("freeze.teleport.custom_location.world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = plugin.getConfig().getDouble("freeze.teleport.custom_location.x", 0.5);
                double y = plugin.getConfig().getDouble("freeze.teleport.custom_location.y", 100.0);
                double z = plugin.getConfig().getDouble("freeze.teleport.custom_location.z", 0.5);
                float yaw = (float) plugin.getConfig().getDouble("freeze.teleport.custom_location.yaw", 0.0);
                float pitch = (float) plugin.getConfig().getDouble("freeze.teleport.custom_location.pitch", 0.0);
                return new Location(world, x, y, z, yaw, pitch);
            }
        }

        // Default: World Spawn Location
        return player.getWorld().getSpawnLocation().add(0.5, 0.0, 0.5);
    }

    public boolean freeze(Player player, String reason, Player moderator) {
        UUID suspectId = player.getUniqueId();
        LanguageManager lang = plugin.getLanguageManager();
        if (hasCooldown(suspectId)) {
            if (moderator != null) {
                CompatUtils.sendMessage(moderator, lang.getMessage("freeze.cooldown_wait", moderator, "seconds", getRemainingCooldownSeconds(suspectId)));
            }
            return false;
        }
        applyCooldown(suspectId);

        previousLocations.put(suspectId, player.getLocation());

        Location targetLoc = getTargetFreezeLocation(player);
        player.teleport(targetLoc);
        frozenLocations.put(suspectId, targetLoc);

        if (moderator != null) {
            UUID modId = moderator.getUniqueId();
            suspectToModerator.put(suspectId, modId);
            moderatorToSuspect.put(modId, suspectId);

            CompatUtils.sendMessage(moderator, lang.getMessage("freeze.staff_start", moderator, "player", player.getName()));
        }

        String discordUrl = getDiscordUrl();
        String targetLang = lang.getPlayerLanguage(player);
        String displayReason = (reason != null && !reason.isBlank() && !reason.equals("Inspection by Staff") && !reason.equals("Inspection by Admin"))
            ? reason
            : lang.getMessage("gui.freeze_reason", targetLang);

        CompatUtils.playSound(player, "ENTITY_ELDER_GUARDIAN_CURSE", 1.0f, 1.0f);
        CompatUtils.sendTitle(player, lang.getMessage("freeze.title_frozen", targetLang), lang.getMessage("freeze.subtitle_discord", targetLang, "discord_url", discordUrl), 10, 80, 20);
        CompatUtils.sendMessage(player, lang.getMessage("freeze.frozen_target", targetLang, "reason", displayReason, "discord_url", discordUrl));
        return true;
    }

    public boolean unfreeze(Player player) {
        UUID suspectId = player.getUniqueId();
        LanguageManager lang = plugin.getLanguageManager();
        if (hasCooldown(suspectId)) {
            return false;
        }
        applyCooldown(suspectId);

        frozenLocations.remove(suspectId);

        UUID modId = suspectToModerator.remove(suspectId);
        if (modId != null) {
            moderatorToSuspect.remove(modId);
            Player mod = Bukkit.getPlayer(modId);
            if (mod != null && mod.isOnline()) {
                CompatUtils.sendMessage(mod, lang.getMessage("freeze.staff_passed", mod, "player", player.getName()));
            }
        }

        // Send player to Spawn when they pass check
        boolean toSpawn = plugin.getConfig().getBoolean("freeze.teleport_to_spawn_on_pass", true);
        if (toSpawn) {
            Location spawnLoc = player.getWorld().getSpawnLocation().add(0.5, 0.0, 0.5);
            player.teleport(spawnLoc);
        } else {
            Location prev = previousLocations.remove(suspectId);
            if (prev != null) player.teleport(prev);
        }
        previousLocations.remove(suspectId);

        String targetLang = lang.getPlayerLanguage(player);
        CompatUtils.playSound(player, "ENTITY_PLAYER_LEVELUP", 1.0f, 1.2f);
        CompatUtils.sendTitle(player, lang.getMessage("freeze.title_passed", targetLang), lang.getMessage("freeze.subtitle_passed", targetLang), 10, 60, 15);
        CompatUtils.sendMessage(player, lang.getMessage("freeze.unfrozen_target", targetLang));
        return true;
    }

    public void sendInterrogationMessage(Player sender, String message, boolean isModerator) {
        LanguageManager lang = plugin.getLanguageManager();

        UUID suspectId = isModerator ? moderatorToSuspect.get(sender.getUniqueId()) : sender.getUniqueId();
        UUID moderatorId = isModerator ? sender.getUniqueId() : suspectToModerator.get(sender.getUniqueId());

        Player suspect = (suspectId != null) ? Bukkit.getPlayer(suspectId) : null;
        Player moderator = (moderatorId != null) ? Bukkit.getPlayer(moderatorId) : null;

        if (suspect != null && suspect.isOnline()) {
            String suspectLang = lang.getPlayerLanguage(suspect);
            String roleTag = lang.getRaw(isModerator ? "freeze.role_moderator" : "freeze.role_suspect", suspectLang);
            String formatted = lang.getMessage("freeze.interrogation_format", suspectLang, "role", roleTag, "sender", sender.getName(), "message", message);
            suspect.sendMessage(formatted);
            CompatUtils.playSound(suspect, "ENTITY_EXPERIENCE_ORB_PICKUP", 0.8f, 1.2f);
        }

        if (moderator != null && moderator.isOnline()) {
            String modLang = lang.getPlayerLanguage(moderator);
            String roleTag = lang.getRaw(isModerator ? "freeze.role_moderator" : "freeze.role_suspect", modLang);
            String formatted = lang.getMessage("freeze.interrogation_format", modLang, "role", roleTag, "sender", sender.getName(), "message", message);
            moderator.sendMessage(formatted);
            CompatUtils.playSound(moderator, "ENTITY_EXPERIENCE_ORB_PICKUP", 0.8f, 1.5f);
        }

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("aianticheat.admin") && staff != suspect && staff != moderator) {
                String staffLang = lang.getPlayerLanguage(staff);
                String roleTag = lang.getRaw(isModerator ? "freeze.role_moderator" : "freeze.role_suspect", staffLang);
                String formatted = lang.getMessage("freeze.interrogation_format", staffLang, "role", roleTag, "sender", sender.getName(), "message", message);
                staff.sendMessage(formatted);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (isFrozen(uuid)) {
            event.setCancelled(true);
            sendInterrogationMessage(player, event.getMessage(), false);
            return;
        }

        if (isModeratorChecking(uuid)) {
            String msg = event.getMessage();
            if (msg.startsWith("!") || msg.startsWith("@")) {
                event.setCancelled(true);
                String cleanMsg = msg.substring(1).trim();
                if (!cleanMsg.isEmpty()) {
                    sendInterrogationMessage(player, cleanMsg, true);
                }
            }
        }
    }

    private void startWarningTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LanguageManager lang = plugin.getLanguageManager();
            String discordUrl = getDiscordUrl();
            for (UUID id : frozenLocations.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    CompatUtils.sendTitle(p, lang.getMessage("freeze.title_warning", p), lang.getMessage("freeze.subtitle_discord", p, "discord_url", discordUrl), 0, 40, 10);
                    CompatUtils.sendActionBar(p, lang.getMessage("freeze.actionbar_warning", p));
                }
            }
        }, 30L, 30L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (frozenLocations.containsKey(id)) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
                event.setTo(from.setDirection(to.getDirection()));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p && frozenLocations.containsKey(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (frozenLocations.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (frozenLocations.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (frozenLocations.containsKey(event.getPlayer().getUniqueId())) {
            String msg = event.getMessage().toLowerCase();
            if (!msg.startsWith("/msg") && !msg.startsWith("/r") && !msg.startsWith("/w")) {
                event.setCancelled(true);
                CompatUtils.sendMessage(event.getPlayer(), plugin.getLanguageManager().getMessage("freeze.command_blocked", event.getPlayer()));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (frozenLocations.remove(id) != null) {
            previousLocations.remove(id);
            UUID modId = suspectToModerator.remove(id);
            if (modId != null) {
                moderatorToSuspect.remove(modId);
            }
            String ip = (event.getPlayer().getAddress() != null) ? event.getPlayer().getAddress().getAddress().getHostAddress() : "127.0.0.1";
            plugin.getPunishmentManager().banForRefusingCheck(event.getPlayer().getName(), ip);
            LanguageManager lang = plugin.getLanguageManager();
            String defLang = lang.getPlayerLanguage(Bukkit.getConsoleSender());
            Bukkit.broadcastMessage(lang.getMessage("freeze.quit_ban_broadcast", defLang, "player", event.getPlayer().getName()));
        }
    }
}
