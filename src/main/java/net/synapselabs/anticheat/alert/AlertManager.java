package net.synapselabs.anticheat.alert;

import net.md_5.bungee.api.chat.TextComponent;
import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.data.PlayerProfile;
import net.synapselabs.anticheat.engine.DetectionSnapshot;
import net.synapselabs.anticheat.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Dispatches differentiated in-game alerts (Blue for Hard checks, Orange for AI checks)
 * with interactive GrimAC-style action buttons and multi-language support.
 */
public class AlertManager {
    private final AiAnticheatPlugin plugin;

    public AlertManager(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void dispatchAlert(DetectionSnapshot snapshot) {
        if (!snapshot.isFlagged()) return;

        Player player = Bukkit.getPlayer(snapshot.playerId());
        if (player == null || !player.isOnline()) return;

        PlayerProfile profile = plugin.getDataManager().getOrCreate(player);

        broadcastAlert(snapshot, profile);

        // Console Logging
        if (plugin.getConfig().getBoolean("logging.console.enabled", true)) {
            String checkType = snapshot.hasHardViolations() ? "[Hard-Check]" : "[AI-Check]";
            plugin.getLogger().info(String.format(
                "%s Player '%s' flagged for '%s' | Confidence: %d%% | State: %s | Ping: %dms | Flags: %d (Hard: %d, AI: %d, Grim: %d)",
                checkType,
                snapshot.playerName(),
                snapshot.getPrimaryViolation(),
                snapshot.getConfidencePercent(),
                snapshot.threatState().name(),
                player.getPing(),
                profile.getTotalFlags(),
                profile.getHardFlags(),
                profile.getAiFlags(),
                profile.getGrimFlagsCount()
            ));
        }

        // Webhook dispatch
        double reach = snapshot.featureVector() != null ? snapshot.featureVector().namedFeatures().getOrDefault("raycast_distance", 3.0f) : 3.0;
        float angle = snapshot.featureVector() != null ? snapshot.featureVector().namedFeatures().getOrDefault("angle_offset_deg", 0.0f) : 0.0f;
        plugin.getWebhookService().sendFlagAlert(
            snapshot.playerName(),
            snapshot.getPrimaryViolation(),
            snapshot.getConfidencePercent(),
            profile.getTotalFlags(),
            player.getPing(),
            reach,
            angle
        );
    }

    private void broadcastAlert(DetectionSnapshot snapshot, PlayerProfile profile) {
        String soundName = plugin.getConfig().getString("detection.sound_alert.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        boolean soundEnabled = plugin.getConfig().getBoolean("detection.sound_alert.enabled", true);
        boolean isHardCheck = snapshot.hasHardViolations();
        LanguageManager langMgr = plugin.getLanguageManager();

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("aianticheat.alerts") || staff.hasPermission("aianticheat.admin")) {
                String lang = langMgr != null ? langMgr.getPlayerLanguage(staff) : "en";
                TextComponent alertComp;

                if (isHardCheck) {
                    String formatted = langMgr != null ? langMgr.getMessage("alerts.hard", lang,
                        "player", snapshot.playerName(),
                        "check", snapshot.getPrimaryViolation(),
                        "flags", profile.getHardFlags()
                    ) : ("§b[Hard] " + snapshot.playerName() + " failed " + snapshot.getPrimaryViolation());
                    alertComp = new TextComponent(formatted + " ");
                } else {
                    String formatted = langMgr != null ? langMgr.getMessage("alerts.ai", lang,
                        "player", snapshot.playerName(),
                        "check", snapshot.getPrimaryViolation(),
                        "confidence", snapshot.getConfidencePercent(),
                        "state", langMgr.getRaw(snapshot.threatState().messageKey(), lang),
                        "flags", profile.getAiFlags()
                    ) : ("§6[AI] " + snapshot.playerName() + " flagged " + snapshot.getPrimaryViolation());
                    alertComp = new TextComponent(formatted + " ");
                }

                appendButtons(alertComp, snapshot.playerName(), lang);
                CompatUtils.sendComponent(staff, alertComp);

                if (soundEnabled) {
                    float pitch = isHardCheck ? 0.8f : 1.4f;
                    CompatUtils.playSound(staff, soundName, 1.0f, pitch);
                }
            }
        }
    }

    private void appendButtons(TextComponent message, String playerName, String lang) {
        LanguageManager langMgr = plugin.getLanguageManager();
        String txtDossier = langMgr != null ? langMgr.getRaw("alerts.btn_dossier", lang) : "Dossier";
        String txtSpectate = langMgr != null ? langMgr.getRaw("alerts.btn_spectate", lang) : "Spectate";
        String txtFreeze = langMgr != null ? langMgr.getRaw("alerts.btn_freeze", lang) : "Freeze";
        String txtKick = langMgr != null ? langMgr.getRaw("alerts.btn_kick", lang) : "Kick";

        String hoverDossier = langMgr != null ? langMgr.getRaw("alerts.btn_dossier_hover", lang) : "Open dossier";
        String hoverSpectate = langMgr != null ? langMgr.getRaw("alerts.btn_spectate_hover", lang) : "Spectate player";
        String hoverFreeze = langMgr != null ? langMgr.getRaw("alerts.btn_freeze_hover", lang) : "Freeze player";
        String hoverKick = langMgr != null ? langMgr.getRaw("alerts.btn_kick_hover", lang) : "Kick player";

        // Kick reason shown to the removed player; localized (fallback keeps the historical English text).
        String kickReason = langMgr != null ? langMgr.getRaw("alerts.kick_reason", lang) : "Unfair Advantage [SynapseAI]";

        message.addExtra(CompatUtils.createGrimButton(txtDossier, "&#00d2ff", hoverDossier, "/aiac inspect " + playerName));
        message.addExtra(new TextComponent(" "));
        message.addExtra(CompatUtils.createGrimButton(txtSpectate, "&#a855f7", hoverSpectate, "/aiac inspect " + playerName));
        message.addExtra(new TextComponent(" "));
        message.addExtra(CompatUtils.createGrimButton(txtFreeze, "&#06b6d4", hoverFreeze, "/aiac freeze " + playerName));
        message.addExtra(new TextComponent(" "));
        message.addExtra(CompatUtils.createGrimButton(txtKick, "&#ef4444", hoverKick, "/kick " + playerName + " " + kickReason));
    }
}
