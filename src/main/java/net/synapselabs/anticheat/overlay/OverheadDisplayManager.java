package net.synapselabs.anticheat.overlay;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.data.ThreatState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, differential Overhead TextDisplay manager.
 * Displays real-time Killaura & Aim confidence scores strictly visible to staff with LuckPerms permissions.
 * Guarantees zero orphan persistence across server restarts, reloads, or world unloads.
 */
public class OverheadDisplayManager implements Listener {
    public static final String OVERHEAD_TAG = "synapse_overhead";
    private final AiAnticheatPlugin plugin;
    private final Map<UUID, OverheadEntry> displays = new ConcurrentHashMap<>();

    private static class OverheadEntry {
        final TextDisplay entity;
        int lastKillauraScore = -1;
        int lastAimScore = -1;
        ThreatState lastState = null;
        long lastUpdate = 0;

        OverheadEntry(TextDisplay entity) {
            this.entity = entity;
        }
    }

    public OverheadDisplayManager(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
        purgeOrphanedDisplays();
        startPositionTask();
        startPermissionSyncTask();
    }

    public void purgeOrphanedDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof TextDisplay td && (td.getScoreboardTags().contains(OVERHEAD_TAG) || !td.isPersistent())) {
                    try {
                        td.remove();
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    public void createDisplay(Player target) {
        if (!plugin.getConfig().getBoolean("detection.overhead_display.enabled", true)) return;
        if (target == null || !target.isOnline()) return;

        UUID uuid = target.getUniqueId();
        removeDisplay(uuid);

        try {
            Location loc = target.getLocation().add(0, 2.25, 0);
            TextDisplay display = (TextDisplay) target.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
            display.setBillboard(Display.Billboard.CENTER);
            display.setViewRange(32.0f);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setPersistent(false); // NEVER save to chunk .mca files
            display.addScoreboardTag(OVERHEAD_TAG);

            // Hide from all regular players by default
            try {
                display.setVisibleByDefault(false);
            } catch (Throwable ignored) {}

            PlayerProfile profile = plugin.getDataManager().getOrCreate(target);
            int kScore = Math.round(profile.getKillauraConfidence() * 100.0f);
            int aScore = Math.round(profile.getAimConfidence() * 100.0f);
            ThreatState state = profile.getThreatState();

            String defaultLang = plugin.getLanguageManager().getPlayerLanguage(target);
            String text = formatDisplayText(kScore, aScore, state, defaultLang);
            display.setText(text);

            OverheadEntry entry = new OverheadEntry(display);
            entry.lastKillauraScore = kScore;
            entry.lastAimScore = aScore;
            entry.lastState = state;
            entry.lastUpdate = System.currentTimeMillis();

            displays.put(uuid, entry);

            // Apply visibility strictly to authorized staff
            applyInitialVisibility(display);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not spawn TextDisplay entity (Server version might not support TextDisplay): " + t.getMessage());
        }
    }

    public void updateDisplay(UUID targetUuid, int killauraScore, int aimScore, ThreatState state) {
        OverheadEntry entry = displays.get(targetUuid);
        if (entry == null || entry.entity == null || !entry.entity.isValid()) {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && target.isOnline()) {
                createDisplay(target);
            }
            return;
        }

        // Differential update: only update text if values changed
        if (entry.lastKillauraScore == killauraScore && entry.lastAimScore == aimScore && entry.lastState == state) {
            return;
        }

        Player target = Bukkit.getPlayer(targetUuid);
        String lang = target != null ? plugin.getLanguageManager().getPlayerLanguage(target) : "en";
        String text = formatDisplayText(killauraScore, aimScore, state, lang);
        entry.entity.setText(text);
        entry.lastKillauraScore = killauraScore;
        entry.lastAimScore = aimScore;
        entry.lastState = state;
        entry.lastUpdate = System.currentTimeMillis();
    }

    public void updateVisibility(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;

        boolean isStaff = hasOverheadPermission(viewer);

        for (OverheadEntry entry : displays.values()) {
            if (entry.entity != null && entry.entity.isValid()) {
                if (isStaff) {
                    viewer.showEntity(plugin, entry.entity);
                } else {
                    viewer.hideEntity(plugin, entry.entity);
                }
            }
        }
    }

    private void applyInitialVisibility(TextDisplay display) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hasOverheadPermission(p)) {
                p.showEntity(plugin, display);
            } else {
                p.hideEntity(plugin, display);
            }
        }
    }

    public boolean hasOverheadPermission(Player player) {
        String perm = plugin.getConfig().getString("detection.overhead_display.required_permission", "aianticheat.overhead");
        return player.hasPermission(perm) || player.hasPermission("aianticheat.admin");
    }

    private String formatDisplayText(int killauraScore, int aimScore, ThreatState state, String lang) {
        String kColor = killauraScore >= 70 ? "&#ff2244" : (killauraScore >= 40 ? "&#ffaa00" : "&#00ff88");
        String aColor = aimScore >= 70 ? "&#ff2244" : (aimScore >= 40 ? "&#ffaa00" : "&#00ff88");

        String stateTag = plugin.getLanguageManager().getMessage(state.messageKey(), lang);

        return CompatUtils.color(String.format(
            "%s &#888888[&#777777Killaura: %s%d%% &#888888| &#777777Aim: %s%d%%&#888888]",
            stateTag,
            kColor, killauraScore,
            aColor, aimScore
        ));
    }

    public void removeDisplay(UUID targetUuid) {
        OverheadEntry entry = displays.remove(targetUuid);
        if (entry != null && entry.entity != null && entry.entity.isValid()) {
            try {
                entry.entity.remove();
            } catch (Throwable ignored) {}
        }
    }

    public void removeAll() {
        for (OverheadEntry entry : displays.values()) {
            if (entry.entity != null && entry.entity.isValid()) {
                try {
                    entry.entity.remove();
                } catch (Throwable ignored) {}
            }
        }
        displays.clear();
        purgeOrphanedDisplays();
    }

    private void startPositionTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            double heightOffset = plugin.getConfig().getDouble("detection.overhead_display.height_offset", 2.25);
            for (Map.Entry<UUID, OverheadEntry> entry : displays.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                TextDisplay display = entry.getValue().entity;

                if (player != null && player.isOnline() && display != null && display.isValid()) {
                    if (player.getWorld().equals(display.getWorld())) {
                        display.teleport(player.getLocation().add(0, heightOffset, 0));
                    }
                }
            }
        }, 20L, 4L);
    }

    private void startPermissionSyncTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateVisibility(player);
            }
        }, 60L, 60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                createDisplay(player);
                updateVisibility(player);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removeDisplay(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                createDisplay(player);
                updateVisibility(player);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        purgeOrphanedDisplays();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        purgeOrphanedDisplays();
    }
}
