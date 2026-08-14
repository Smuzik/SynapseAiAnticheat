package net.synapselabs.anticheat.listener;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.engine.AiInferenceEngine;
import net.synapselabs.anticheat.tracker.KinematicHistory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatListener implements Listener {
    private final AiAnticheatPlugin plugin;
    private final AiInferenceEngine engine;

    private final Map<UUID, KinematicHistory> histories = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violationLevels = new ConcurrentHashMap<>();

    public CombatListener(AiAnticheatPlugin plugin, AiInferenceEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = plugin.getDataManager().getOrCreate(player);

        if (plugin.getDataManager().hasBannedAccountsOnIp(profile.getLastIp())) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("aianticheat.admin")) {
                    CompatUtils.sendMessage(staff, "&8[&c&lAI-AC ВНИМАНИЕ&8] &fИгрок &e" + player.getName() + " &fзашел с IP &c" + profile.getLastIp() + "&f, на котором есть &4ЗАБАНЕННЫЕ АККАУНТЫ&f!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        histories.computeIfAbsent(event.getPlayer().getUniqueId(), k -> new KinematicHistory())
                .push(event.getTo());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        UUID attackerId = attacker.getUniqueId();
        KinematicHistory history = histories.computeIfAbsent(attackerId, k -> new KinematicHistory());
        history.push(attacker.getLocation());

        Location eyeLoc = attacker.getEyeLocation();
        Location victimLoc = victim.getEyeLocation();

        double distance = eyeLoc.distance(victimLoc);
        float angleOffset = KinematicHistory.calculateAngleOffset(eyeLoc, victimLoc);

        float yawDelta1t = history.getYawDelta(1);
        float yawDelta2t = history.getYawDelta(2);
        float yawDelta5t = history.getYawDelta(5);
        float yawAccel = history.getYawAcceleration();

        float pitchDelta1t = history.getPitchDelta(1);
        float pitchDelta2t = history.getPitchDelta(2);
        float pitchDelta5t = history.getPitchDelta(5);
        float pitchAccel = history.getPitchAcceleration();

        boolean isFalling = attacker.getFallDistance() > 0.0f && !attacker.isOnGround();
        boolean isSprinting = attacker.isSprinting();
        
        float cooldown = 1.0f;
        try {
            cooldown = attacker.getAttackCooldown();
        } catch (Throwable ignored) {}

        float[] features = new float[]{
            (float) distance,
            angleOffset,
            Math.abs(yawDelta1t),
            Math.abs(yawDelta2t),
            Math.abs(yawDelta5t),
            Math.abs(yawAccel),
            Math.abs(pitchDelta1t),
            Math.abs(pitchDelta2t),
            Math.abs(pitchDelta5t),
            Math.abs(pitchAccel),
            1.0f,
            (float) distance,
            angleOffset,
            isFalling ? 1.0f : 0.0f,
            isSprinting ? 1.0f : 0.0f,
            cooldown
        };

        AiInferenceEngine.PredictionResult result = engine.predict(features);

        double minConf = plugin.getConfig().getDouble("detection.min_confidence", 0.70);
        int vlPerFlag = plugin.getConfig().getInt("detection.vl_per_flag", 1);
        int alertThreshold = plugin.getConfig().getInt("detection.actions.alert_vl_threshold", 3);
        int cancelThreshold = plugin.getConfig().getInt("detection.actions.cancel_damage_vl_threshold", 8);
        int freezeThreshold = plugin.getConfig().getInt("detection.actions.freeze_vl_threshold", 15);
        int kickThreshold = plugin.getConfig().getInt("detection.actions.kick_vl_threshold", 20);

        if (result.cheatProbability() >= minConf || result.predictedClass() == 1) {
            int currentVl = violationLevels.merge(attackerId, vlPerFlag, Integer::sum);
            
            String cheatType = (distance > 3.4) ? "Reach" : (angleOffset > 30.0 ? "SilentAura" : "KillAura");
            PlayerProfile profile = plugin.getDataManager().getOrCreate(attacker);
            profile.incrementAiFlags(cheatType, result.cheatProbability());

            int confPercent = Math.round(result.cheatProbability() * 100.0f);

            // In-Game Staff Alert + Sound
            if (currentVl >= alertThreshold) {
                String alertTemplate = plugin.getConfig().getString("alerts.format", "&8[&b&lSynapse&3AI&8] &fИгрок &e{player} &fподозрение: &c{type} &7(Уверенность: &c{confidence}%&7, VL: &6{vl}&7)");
                String msg = alertTemplate
                    .replace("{player}", attacker.getName())
                    .replace("{type}", cheatType)
                    .replace("{confidence}", String.valueOf(confPercent))
                    .replace("{vl}", String.valueOf(currentVl));

                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("aianticheat.alerts")) {
                        CompatUtils.sendMessage(staff, msg);
                        if (plugin.getConfig().getBoolean("detection.actions.sound_alert.enabled", true)) {
                            String soundName = plugin.getConfig().getString("detection.actions.sound_alert.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
                            CompatUtils.playSound(staff, soundName, 1.0f, 1.2f);
                        }
                    }
                }
            }

            // Discord Webhook Dispatch
            plugin.getWebhookService().sendFlagAlert(attacker.getName(), cheatType, confPercent, currentVl, attacker.getPing(), distance, angleOffset);

            // Cancel Damage
            if (currentVl >= cancelThreshold) {
                event.setCancelled(true);
            }

            // Auto-Freeze Suspect
            if (freezeThreshold > 0 && currentVl >= freezeThreshold && !plugin.getFreezeManager().isFrozen(attackerId)) {
                plugin.getFreezeManager().freeze(attacker, "Превышение уровня нарушений (" + cheatType + " VL: " + currentVl + ")", null);
            }

            // Auto Kick
            if (kickThreshold > 0 && currentVl >= kickThreshold) {
                String kickMsg = plugin.getConfig().getString("detection.actions.kick_message", "&cKicked for cheating.\n&7Appeal: &bdsc.gg/synapselabs");
                Bukkit.getScheduler().runTask(plugin, () -> attacker.kickPlayer(CompatUtils.color(kickMsg)));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        histories.remove(id);
        violationLevels.remove(id);
    }

    public void decayVL(int amount) {
        violationLevels.replaceAll((uuid, vl) -> Math.max(0, vl - amount));
        violationLevels.entrySet().removeIf(e -> e.getValue() <= 0);
    }

    public int getVL(UUID playerUuid) {
        return violationLevels.getOrDefault(playerUuid, 0);
    }

    public void resetVL(UUID playerUuid) {
        violationLevels.remove(playerUuid);
    }
}
