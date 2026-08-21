package net.synapselabs.anticheat.listener;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.alert.AlertManager;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.engine.DetectionSnapshot;
import net.synapselabs.anticheat.engine.FeatureVector;
import net.synapselabs.anticheat.engine.HardCombatChecks;
import net.synapselabs.anticheat.engine.InferenceService;
import net.synapselabs.anticheat.engine.LagCompensator;
import net.synapselabs.anticheat.overlay.OverheadDisplayManager;
import net.synapselabs.anticheat.tracker.CombatTracker;
import net.synapselabs.anticheat.tracker.KinematicHistory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatListener implements Listener {
    private final AiAnticheatPlugin plugin;
    private final HardCombatChecks hardChecks;
    private final InferenceService inferenceService;
    private final AlertManager alertManager;
    private final OverheadDisplayManager overheadManager;

    /** Real per-victim position history — turns a ping-spiked "reach" into a legit hit. */
    private final LagCompensator lagCompensator = new LagCompensator();

    private final Map<UUID, CombatTracker> trackers = new ConcurrentHashMap<>();

    /** Tracks the last teleport/respawn time per player for grace period (Bug #4: God Mode false bans). */
    private final Map<UUID, Long> lastTeleportOrRespawn = new ConcurrentHashMap<>();

    /** Minimum grace period after TP/respawn before combat analysis kicks in (ms). */
    private static final long MIN_GRACE_MS = 300L;

    public CombatListener(
        AiAnticheatPlugin plugin,
        HardCombatChecks hardChecks,
        InferenceService inferenceService,
        AlertManager alertManager,
        OverheadDisplayManager overheadManager
    ) {
        this.plugin = plugin;
        this.hardChecks = hardChecks;
        this.inferenceService = inferenceService;
        this.alertManager = alertManager;
        this.overheadManager = overheadManager;

        // Subscribe to asynchronous detection snapshots
        this.inferenceService.subscribe(this::handleAsyncSnapshot);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = plugin.getDataManager().getOrCreate(player);

        if (!plugin.isSetupCompleted() && player.hasPermission("aianticheat.admin")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    CompatUtils.sendMessage(player, plugin.getLanguageManager().getMessage("setup.required_prompt", player));
                }
            }, 20L);
        }

        if (plugin.getDataManager().hasBannedAccountsOnIp(profile.getLastIp())) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("aianticheat.admin") || staff.hasPermission("aianticheat.alerts")) {
                    String lang = plugin.getLanguageManager().getPlayerLanguage(staff);
                    CompatUtils.sendMessage(staff, plugin.getLanguageManager().getMessage(
                        "alerts.banned_ip_join", lang,
                        "player", player.getName(),
                        "ip", profile.getLastIp()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        CombatTracker tracker = trackers.computeIfAbsent(player.getUniqueId(), k -> new CombatTracker());
        tracker.getHistory().push(event.getTo());
        // Record this player's hitbox so that, when they are the VICTIM of an attack, reach can be
        // rewound to where they actually were in the attacker's lag-compensated view.
        lagCompensator.record(player);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.isSetupCompleted()) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        // TPS Protection Check
        if (plugin.getConfig().getBoolean("detection.tps_protection.enabled", true)) {
            try {
                double[] tps = Bukkit.getTPS();
                double minTps = plugin.getConfig().getDouble("detection.tps_protection.min_tps", 17.5);
                if (tps.length > 0 && tps[0] < minTps) {
                    return;
                }
            } catch (Throwable ignored) {}
        }

        UUID attackerId = attacker.getUniqueId();
        int ping = safePing(attacker);

        // Bug #4 fix: Grace period after teleport/respawn. During the desync window the client and
        // server positions are not yet synchronized, producing false God Mode / reach / aim signals.
        Long lastGrace = lastTeleportOrRespawn.get(attackerId);
        if (lastGrace != null) {
            long gracePeriod = Math.max(MIN_GRACE_MS, ping * 2L);
            if (System.currentTimeMillis() - lastGrace < gracePeriod) {
                return; // still in grace period — skip all combat analysis
            }
        }
        // Also check victim grace period (victim just TP'd/respawned → attacker shouldn't be flagged)
        if (victim instanceof Player victimPlayer) {
            Long victimGrace = lastTeleportOrRespawn.get(victimPlayer.getUniqueId());
            if (victimGrace != null) {
                long gracePeriod = Math.max(MIN_GRACE_MS, safePing(victimPlayer) * 2L);
                if (System.currentTimeMillis() - victimGrace < gracePeriod) {
                    return;
                }
            }
        }

        CombatTracker tracker = trackers.computeIfAbsent(attackerId, k -> new CombatTracker());
        KinematicHistory history = tracker.getHistory();
        history.push(attacker.getLocation());

        Location eyeLoc = attacker.getEyeLocation();
        double distance = eyeLoc.distance(victim.getEyeLocation());
        history.registerAttack(distance);

        // Record the victim's current hitbox, and rewind reach to the attacker's lag-compensated view.
        lagCompensator.record(victim);
        double latencyMargin = latencyMarginFor(ping);

        // ONE raycast, reused for both the hard signals and the model's feature vector.
        KinematicHistory.RaycastHitResult raycast = KinematicHistory.raycastTargetHitbox(eyeLoc, victim, latencyMargin, 6.0);
        double compensatedReach = lagCompensator.compensatedReach(eyeLoc, victim.getUniqueId(), ping, raycast.distance());

        // Bug #2 fix: When the attacker's eyes are inside the victim's bounding box (point-blank melee),
        // every single hit produces a "perfect" aim/reach signature that looks exactly like killaura.
        // This is completely normal gameplay (e.g. two players overlapping). Skip analysis entirely.
        if (compensatedReach < 0.5 && raycast.distance() < 0.3f) {
            return;
        }

        // 1. Hard checks — now produce SIGNALS + CONTEXT (no punishment here). The only synchronous action
        //    is a protective, reversible damage cancel, and only for lag-compensated-CERTAIN reach.
        HardCombatChecks.HardCheckResult hardResult =
                hardChecks.evaluate(attacker, victim, tracker, raycast, compensatedReach);

        if (hardResult.cancelDamage()) {
            event.setCancelled(true);
        }

        // 2. Asynchronous decision: signals + context + AI model → Risk Engine → verdict.
        FeatureVector vector = tracker.createFeatureVector(attacker, eyeLoc, victim.getEyeLocation(), raycast);

        inferenceService.submitAnalysis(attacker, vector, tracker, hardResult.signals(), hardResult.context());
    }

    // --- Bug #4: Track teleport & respawn events for grace period ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        lastTeleportOrRespawn.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        lastTeleportOrRespawn.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        // Death precedes respawn — mark grace start early so the desync window is fully covered
        lastTeleportOrRespawn.put(event.getEntity().getUniqueId(), System.currentTimeMillis());
    }

    private void handleAsyncSnapshot(DetectionSnapshot snapshot) {
        overheadManager.updateDisplay(
            snapshot.playerId(),
            snapshot.getKillauraPercent(),
            snapshot.getAimPercent(),
            snapshot.threatState()
        );

        // SUSPICIOUS and above are shown to staff; ONLY a CHEAT verdict is ever actioned.
        if (snapshot.isFlagged()) {
            alertManager.dispatchAlert(snapshot);
        }
        if (snapshot.isActionable()) {
            plugin.getPunishmentManager().evaluateSnapshot(snapshot);
        }
    }

    /** Bounding-box expansion for the raycast, scaled by ping (0.10 .. 0.40 blocks).
     *  Bug #6 fix: raised upper clamp from 0.25 to 0.40 and coefficient from 0.0005 to 0.001
     *  so high-ping players (200+ ms) get a fairer hitbox expansion. */
    private static double latencyMarginFor(int pingMs) {
        return Math.max(0.10, Math.min(0.40, 0.10 + pingMs * 0.001));
    }

    private static int safePing(Player player) {
        try {
            return player.getPing();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        trackers.remove(uuid);
        lagCompensator.forget(uuid);
        lastTeleportOrRespawn.remove(uuid);
    }

    public void decayAll(float amount) {
        for (Map.Entry<UUID, CombatTracker> entry : trackers.entrySet()) {
            UUID uuid = entry.getKey();
            CombatTracker tracker = entry.getValue();
            tracker.decay(amount);

            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                PlayerProfile profile = plugin.getDataManager().getOrCreate(p);
                profile.setKillauraConfidence(tracker.getKillauraConfidence());
                profile.setAimConfidence(tracker.getAimConfidence());
                profile.setSuspicion(tracker.getSuspicion());
                profile.setThreatState(tracker.getThreatState());

                overheadManager.updateDisplay(
                    uuid,
                    Math.round(tracker.getKillauraConfidence() * 100.0f),
                    Math.round(tracker.getAimConfidence() * 100.0f),
                    tracker.getThreatState()
                );
            }
        }
    }

    public CombatTracker getTracker(UUID uuid) {
        return trackers.get(uuid);
    }
}
