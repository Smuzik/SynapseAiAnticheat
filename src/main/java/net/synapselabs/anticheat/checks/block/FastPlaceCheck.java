package net.synapselabs.anticheat.checks.block;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.data.PlayerProfile;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastPlaceCheck implements Listener {
    private final AiAnticheatPlugin plugin;
    private final Map<UUID, Deque<Long>> placeTimestamps = new ConcurrentHashMap<>();

    public FastPlaceCheck(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("detection.extra_checks.fastplace.enabled", true)) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Deque<Long> queue = placeTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        queue.addFirst(now);

        // Remove entries older than 1 second
        while (!queue.isEmpty() && now - queue.peekLast() > 1000L) {
            queue.removeLast();
        }

        int maxPerSec = plugin.getConfig().getInt("detection.extra_checks.fastplace.max_blocks_per_second", 20);
        if (queue.size() > maxPerSec) {
            event.setCancelled(true);
            flag(player, String.format("FastPlace (%d blocks/sec > %d)", queue.size(), maxPerSec));
        }
    }

    private void flag(Player player, String reason) {
        PlayerProfile profile = plugin.getDataManager().getOrCreate(player);
        profile.incrementHardFlags(reason);

        if (plugin.getConfig().getBoolean("logging.console.enabled", true)) {
            plugin.getLogger().info(String.format(
                "[Extra-Check] Player '%s' flagged for '%s' | Hard Flags: %d",
                player.getName(), reason, profile.getHardFlags()
            ));
        }
    }
}
