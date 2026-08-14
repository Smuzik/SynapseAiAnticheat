package net.synapselabs.anticheat.bridge;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.data.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class GrimAcBridge implements Listener {
    private final AiAnticheatPlugin plugin;
    private boolean grimLoaded = false;

    public GrimAcBridge(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
        hookGrim();
    }

    private void hookGrim() {
        Plugin grim = Bukkit.getPluginManager().getPlugin("GrimAC");
        if (grim != null && grim.isEnabled()) {
            this.grimLoaded = true;
            plugin.getLogger().info("GrimAC engine successfully connected to Synapse AI Pipeline!");
            registerGrimEvents();
        } else {
            plugin.getLogger().info("GrimAC not found (standalone Synapse AI mode active).");
        }
    }

    private void registerGrimEvents() {
        try {
            Class<?> eventClass = Class.forName("ac.grim.grimac.api.events.GrimFlagEvent");
            Bukkit.getPluginManager().registerEvent(
                (Class<? extends Event>) eventClass,
                this,
                EventPriority.MONITOR,
                (listener, event) -> {
                    try {
                        Object userObj = event.getClass().getMethod("getUser").invoke(event);
                        if (userObj != null) {
                            Player player = (Player) userObj.getClass().getMethod("getPlayer").invoke(userObj);
                            if (player != null) {
                                PlayerProfile profile = plugin.getDataManager().getOrCreate(player);
                                profile.incrementGrimFlags();
                            }
                        }
                    } catch (Exception ignored) {}
                },
                plugin,
                true
            );
            plugin.getLogger().info("GrimAC Flag Listener registered successfully!");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("GrimAC API event class not directly found, running via packet layer.");
        }
    }

    public boolean isGrimLoaded() {
        return grimLoaded;
    }
}
