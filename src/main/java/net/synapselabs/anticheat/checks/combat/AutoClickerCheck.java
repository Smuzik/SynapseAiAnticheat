package net.synapselabs.anticheat.checks.combat;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.data.PlayerProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoClickerCheck implements Listener {
    private final AiAnticheatPlugin plugin;
    private final Map<UUID, Deque<Long>> swingTimestamps = new ConcurrentHashMap<>();

    public AutoClickerCheck(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        if (!plugin.getConfig().getBoolean("detection.extra_checks.autoclicker.enabled", true)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Deque<Long> queue = swingTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        queue.addFirst(now);

        while (!queue.isEmpty() && now - queue.peekLast() > 1000L) {
            queue.removeLast();
        }

        int maxCps = plugin.getConfig().getInt("detection.extra_checks.autoclicker.max_cps", 22);
        int cps = queue.size();

        if (cps > maxCps) {
            flag(player, String.format("AutoClicker (High CPS: %d > %d)", cps, maxCps));
            return;
        }

        // Consistency / Zero-variance macro detection (on sample >= 15 clicks)
        if (queue.size() >= 15) {
            Long[] arr = queue.toArray(new Long[0]);
            double sum = 0;
            int count = arr.length - 1;
            for (int i = 0; i < count; i++) {
                sum += (arr[i] - arr[i + 1]);
            }
            double mean = sum / count;

            double sumSq = 0;
            for (int i = 0; i < count; i++) {
                double diff = (arr[i] - arr[i + 1]) - mean;
                sumSq += diff * diff;
            }
            double stdDevTicks = Math.sqrt(sumSq / count) / 50.0;

            // Bug #3 fix: lowered min_stddev from 0.12 to 0.08 to tolerate butterfly/jitter clicking,
            // and raised CPS floor from 12 to 16 — normal click speeds no longer trigger macro check.
            double minStdDev = plugin.getConfig().getDouble("detection.extra_checks.autoclicker.min_stddev", 0.08);
            if (stdDevTicks < minStdDev && cps >= 16) {
                flag(player, String.format("AutoClicker (Macro Consistency: %.3f stddev)", stdDevTicks));
            }
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
