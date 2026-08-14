package net.synapselabs.anticheat.overlay;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class OverheadVlManager {
    private final AiAnticheatPlugin plugin;
    private final Set<UUID> enabledStaff = ConcurrentHashMap.newKeySet();

    public OverheadVlManager(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
        startHudTask();
    }

    public boolean toggleHud(UUID staffUuid) {
        if (enabledStaff.contains(staffUuid)) {
            enabledStaff.remove(staffUuid);
            return false;
        } else {
            enabledStaff.add(staffUuid);
            return true;
        }
    }

    private void startHudTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (!staff.hasPermission("aianticheat.admin")) continue;

                RayTraceResult trace = staff.getWorld().rayTraceEntities(
                    staff.getEyeLocation(),
                    staff.getEyeLocation().getDirection(),
                    30.0,
                    0.6,
                    entity -> entity instanceof Player && entity != staff
                );

                if (trace != null && trace.getHitEntity() instanceof Player target) {
                    int vl = plugin.getCombatListener().getVL(target.getUniqueId());
                    PlayerProfile profile = plugin.getDataManager().getOrCreate(target);
                    int alts = plugin.getDataManager().getAltCount(profile.getLastIp());
                    boolean banned = plugin.getDataManager().hasBannedAccountsOnIp(profile.getLastIp());

                    String vlColor = vl >= 8 ? "&c&l" : (vl >= 3 ? "&e&l" : "&a");
                    String altColor = banned ? "&c&l" : (alts > 1 ? "&e" : "&a");

                    String actionMsg = String.format(
                        "&8[&b&lSynapse&3AI&8] &fЦель: &e&l%s &8| &fVL: %s%d &8| &fТвинки: %s%d &8| &fФлаги: &6%d &8| &fПинг: &a%dms",
                        target.getName(),
                        vlColor, vl,
                        altColor, alts,
                        profile.getAiFlagsCount(),
                        target.getPing()
                    );

                    CompatUtils.sendActionBar(staff, actionMsg);
                }
            }
        }, 5L, 5L);
    }
}
