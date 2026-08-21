package net.synapselabs.anticheat.checks.item;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.data.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FastInteractCheck implements Listener {
    private final AiAnticheatPlugin plugin;
    private final Map<UUID, Long> startEatTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> startBowTime = new ConcurrentHashMap<>();

    public FastInteractCheck(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.hasItem()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Material type = event.getItem().getType();

        if (type.isEdible() || type == Material.POTION || type == Material.MILK_BUCKET) {
            startEatTime.put(uuid, System.currentTimeMillis());
        } else if (type == Material.BOW || type == Material.CROSSBOW) {
            startBowTime.put(uuid, System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!plugin.getConfig().getBoolean("detection.extra_checks.fastinteract.enabled", true)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Long startTime = startEatTime.remove(uuid);

        if (startTime != null) {
            long elapsedTicks = (System.currentTimeMillis() - startTime) / 50L;
            int minEatTicks = plugin.getConfig().getInt("detection.extra_checks.fastinteract.min_eat_ticks", 25);

            if (elapsedTicks < minEatTicks) {
                event.setCancelled(true);
                flag(player, String.format("FastEat (%d ticks < %d ticks)", elapsedTicks, minEatTicks));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getConfig().getBoolean("detection.extra_checks.fastinteract.enabled", true)) return;

        UUID uuid = player.getUniqueId();
        Long startTime = startBowTime.remove(uuid);

        if (startTime != null) {
            long elapsedTicks = (System.currentTimeMillis() - startTime) / 50L;
            int minBowTicks = plugin.getConfig().getInt("detection.extra_checks.fastinteract.min_bow_ticks", 15);

            if (elapsedTicks < minBowTicks && event.getForce() >= 0.8f) {
                event.setCancelled(true);
                flag(player, String.format("FastBow (%d ticks < %d ticks)", elapsedTicks, minBowTicks));
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
