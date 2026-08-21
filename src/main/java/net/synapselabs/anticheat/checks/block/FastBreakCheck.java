package net.synapselabs.anticheat.checks.block;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.data.PlayerProfile;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastBreakCheck implements Listener {
    private final AiAnticheatPlugin plugin;
    private final Map<UUID, Long> startDamageTime = new ConcurrentHashMap<>();

    public FastBreakCheck(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        startDamageTime.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("detection.extra_checks.fastbreak.enabled", true)) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Block block = event.getBlock();
        Material type = block.getType();
        float hardness = type.getHardness();

        // Skip instant-break blocks (flowers, torch, slime, glass panes with eff, etc.)
        if (hardness <= 0.05f) return;

        UUID uuid = player.getUniqueId();
        Long startTime = startDamageTime.remove(uuid);
        long now = System.currentTimeMillis();

        if (startTime == null) {
            // Block broken with zero damage packet initiation
            if (hardness > 0.8f && !player.hasPotionEffect(PotionEffectType.FAST_DIGGING)) {
                event.setCancelled(true);
                flag(player, "FastBreak (Instant: No Damage Packet)");
            }
            return;
        }

        long elapsedMs = now - startTime;
        // Basic minimum time estimation for solid blocks (e.g. obsidian = 6.5s, stone = 250ms, etc.)
        long minExpectedMs = Math.round(hardness * 200.0);
        if (player.hasPotionEffect(PotionEffectType.FAST_DIGGING)) {
            minExpectedMs /= 2;
        }

        if (elapsedMs < (minExpectedMs * 0.45) && minExpectedMs > 150) {
            event.setCancelled(true);
            flag(player, String.format("FastBreak (%dms < %dms)", elapsedMs, minExpectedMs));
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
