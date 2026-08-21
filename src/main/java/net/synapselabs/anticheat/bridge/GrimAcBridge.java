package net.synapselabs.anticheat.bridge;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.tracker.CombatTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public class GrimAcBridge implements Listener {
    private final AiAnticheatPlugin plugin;
    private boolean grimLoaded = false;

    public GrimAcBridge(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
        hookGrim();
    }

    private void hookGrim() {
        if (!plugin.getConfig().getBoolean("detection.grimac_bridge.enabled", true)) {
            return;
        }

        Plugin grim = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (grim != null && grim.isEnabled()) {
            this.grimLoaded = true;
            plugin.getLogger().info("GrimAC engine successfully connected to Synapse AI Pipeline!");
            registerGrimEvents();
        } else {
            plugin.getLogger().info("GrimAC not detected (standalone Synapse AI mode active).");
        }
    }

    @SuppressWarnings("unchecked")
    private void registerGrimEvents() {
        String[] possibleEventNames = {
            "ac.grim.grimac.api.events.FlagEvent",
            "ac.grim.grimac.api.events.GrimFlagEvent"
        };

        for (String className : possibleEventNames) {
            try {
                Class<?> eventClass = Class.forName(className);
                if (Event.class.isAssignableFrom(eventClass)) {
                    Bukkit.getPluginManager().registerEvent(
                        (Class<? extends Event>) eventClass,
                        this,
                        EventPriority.MONITOR,
                        (listener, event) -> handleGrimFlag(event),
                        plugin,
                        true
                    );
                    plugin.getLogger().info("GrimAC Flag Listener registered (" + className + ")!");
                    return;
                }
            } catch (ClassNotFoundException ignored) {}
        }
    }

    private void handleGrimFlag(Event event) {
        try {
            Player player = null;
            String checkName = "Unknown";
            double vl = 1.0;

            // Try getUser().getPlayer()
            try {
                Method getUserMethod = event.getClass().getMethod("getUser");
                Object userObj = getUserMethod.invoke(event);
                if (userObj != null) {
                    Method getPlayerMethod = userObj.getClass().getMethod("getPlayer");
                    player = (Player) getPlayerMethod.invoke(userObj);
                }
            } catch (Throwable ignored) {}

            if (player == null) {
                try {
                    Method getPlayerMethod = event.getClass().getMethod("getPlayer");
                    player = (Player) getPlayerMethod.invoke(event);
                } catch (Throwable ignored) {}
            }

            if (player == null) return;

            // Try getCheck().getCheckName() or getCheckName()
            try {
                Method getCheckMethod = event.getClass().getMethod("getCheck");
                Object checkObj = getCheckMethod.invoke(event);
                if (checkObj != null) {
                    Method getNameMethod = checkObj.getClass().getMethod("getCheckName");
                    checkName = (String) getNameMethod.invoke(checkObj);
                }
            } catch (Throwable ignored) {
                try {
                    Method getNameMethod = event.getClass().getMethod("getCheckName");
                    checkName = (String) getNameMethod.invoke(event);
                } catch (Throwable ignored2) {}
            }

            // Try getViolations()
            try {
                Method getVlMethod = event.getClass().getMethod("getViolations");
                Object vlObj = getVlMethod.invoke(event);
                if (vlObj instanceof Number num) {
                    vl = num.doubleValue();
                }
            } catch (Throwable ignored) {}

            PlayerProfile profile = plugin.getDataManager().getOrCreate(player);
            profile.incrementGrimFlags(checkName, vl);

            CombatTracker tracker = plugin.getCombatListener() != null ? plugin.getCombatListener().getTracker(player.getUniqueId()) : null;
            if (tracker != null) {
                tracker.registerGrimViolation(checkName, vl);
                if (plugin.getOverheadManager() != null) {
                    plugin.getOverheadManager().updateDisplay(
                        player.getUniqueId(),
                        Math.round(tracker.getKillauraConfidence() * 100.0f),
                        Math.round(tracker.getAimConfidence() * 100.0f),
                        tracker.getThreatState()
                    );
                }
            }

            if (plugin.getConfig().getBoolean("logging.console.enabled", true)) {
                plugin.getLogger().info(String.format(
                    "[GrimAC-Bridge] Ingested flag for %s: %s (VL: %.1f) -> Updated SynapseAI State: %s",
                    player.getName(), checkName, vl, (tracker != null ? tracker.getThreatState().name() : "CLEAN")
                ));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Error handling GrimAC flag event: " + t.getMessage());
        }
    }

    public boolean isGrimLoaded() {
        return grimLoaded;
    }
}
