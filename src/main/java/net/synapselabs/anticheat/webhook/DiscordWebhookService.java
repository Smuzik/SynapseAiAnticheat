package net.synapselabs.anticheat.webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.synapselabs.anticheat.AiAnticheatPlugin;
import net.synapselabs.anticheat.compat.CompatUtils;
import net.synapselabs.anticheat.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class DiscordWebhookService {
    private final AiAnticheatPlugin plugin;

    public DiscordWebhookService(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
    }

    public void testWebhook(CommandSender sender) {
        boolean enabled = plugin.getConfig().getBoolean("discord.webhook.enabled", false);
        String webhookUrl = plugin.getConfig().getString("discord.webhook.url", "");

        if (!enabled || webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("YOUR_WEBHOOK_HERE")) {
            sender.sendMessage(CompatUtils.color("&#ff2244[SynapseAI] Discord webhook is disabled or URL is not set in config.yml!"));
            return;
        }

        sender.sendMessage(CompatUtils.color("&#00d2ff[SynapseAI] Sending test alert to Discord webhook..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("username", "Synapse AI-AntiCheat");
                payload.addProperty("avatar_url", "https://i.imgur.com/r62KqGZ.png");

                JsonArray embeds = new JsonArray();
                JsonObject embed = new JsonObject();
                embed.addProperty("title", "✅ Synapse AI-AntiCheat Test Webhook");
                embed.addProperty("description", "Discord webhook integration is successfully connected and working!");
                embed.addProperty("color", 0x00E5FF);

                JsonArray fields = new JsonArray();

                JsonObject f1 = new JsonObject();
                f1.addProperty("name", "Status");
                f1.addProperty("value", "`ONLINE & ACTIVE`");
                f1.addProperty("inline", true);
                fields.add(f1);

                JsonObject f2 = new JsonObject();
                f2.addProperty("name", "Server");
                f2.addProperty("value", "`Purpur AI-AntiCheat v3.0`");
                f2.addProperty("inline", true);
                fields.add(f2);

                embed.add("fields", fields);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "Synapse Labs Studio • https://dsc.gg/synapselabs");
                embed.add("footer", footer);
                embed.addProperty("timestamp", java.time.Instant.now().toString());

                embeds.add(embed);
                payload.add("embeds", embeds);

                int code = executePost(webhookUrl, payload.toString());
                if (code >= 200 && code < 300) {
                    sender.sendMessage(CompatUtils.color("&#00ff88[SynapseAI] Test webhook sent successfully! (HTTP " + code + ")"));
                } else {
                    sender.sendMessage(CompatUtils.color("&#ff2244[SynapseAI] Discord returned error HTTP " + code));
                }
            } catch (Exception e) {
                sender.sendMessage(CompatUtils.color("&#ff2244[SynapseAI] Failed to send webhook: " + e.getMessage()));
            }
        });
    }

    public void sendFlagAlert(String playerName, String cheatType, int confidencePercent, int totalFlags, int ping, double distance, float angleOffset) {
        boolean enabled = plugin.getConfig().getBoolean("discord.webhook.enabled", false);
        if (!enabled) return;

        String webhookUrl = plugin.getConfig().getString("discord.webhook.url", "");
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("YOUR_WEBHOOK_HERE")) return;

        int minFlags = plugin.getConfig().getInt("discord.webhook.min_flags_to_send", 1);
        if (totalFlags < minFlags) return;

        String inviteUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        LanguageManager lang = plugin.getLanguageManager();
        String defLang = lang.getPlayerLanguage(Bukkit.getConsoleSender());
        String embedTitle = lang.getRaw("discord.embed_title", defLang);
        String fieldPlayer = lang.getRaw("discord.field_player", defLang);
        String fieldCheat = lang.getRaw("discord.field_cheat", defLang);
        String fieldConfidence = lang.getRaw("discord.field_confidence", defLang);
        String fieldFlags = lang.getRaw("discord.field_flags", defLang);
        String fieldPing = lang.getRaw("discord.field_ping", defLang);
        String fieldMetrics = lang.getRaw("discord.field_metrics", defLang);
        String metricsValue = lang.getRaw("discord.metrics_value", defLang)
                .replace("{distance}", String.format(Locale.US, "%.2f", distance))
                .replace("{angle}", String.format(Locale.US, "%.1f", angleOffset));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("username", "Synapse AI-AntiCheat");
                payload.addProperty("avatar_url", "https://i.imgur.com/r62KqGZ.png");

                JsonArray embeds = new JsonArray();
                JsonObject embed = new JsonObject();
                embed.addProperty("title", embedTitle);
                embed.addProperty("color", 0x00E5FF);

                JsonObject thumbnail = new JsonObject();
                thumbnail.addProperty("url", "https://mc-heads.net/avatar/" + playerName + "/64");
                embed.add("thumbnail", thumbnail);

                JsonArray fields = new JsonArray();

                JsonObject f1 = new JsonObject();
                f1.addProperty("name", fieldPlayer);
                f1.addProperty("value", "`" + playerName + "`");
                f1.addProperty("inline", true);
                fields.add(f1);

                JsonObject f2 = new JsonObject();
                f2.addProperty("name", fieldCheat);
                f2.addProperty("value", "`" + cheatType + "`");
                f2.addProperty("inline", true);
                fields.add(f2);

                JsonObject f3 = new JsonObject();
                f3.addProperty("name", fieldConfidence);
                f3.addProperty("value", "`" + confidencePercent + "%`");
                f3.addProperty("inline", true);
                fields.add(f3);

                JsonObject f4 = new JsonObject();
                f4.addProperty("name", fieldFlags);
                f4.addProperty("value", "`" + totalFlags + "`");
                f4.addProperty("inline", true);
                fields.add(f4);

                JsonObject f5 = new JsonObject();
                f5.addProperty("name", fieldPing);
                f5.addProperty("value", "`" + ping + " ms`");
                f5.addProperty("inline", true);
                fields.add(f5);

                JsonObject f6 = new JsonObject();
                f6.addProperty("name", fieldMetrics);
                f6.addProperty("value", metricsValue);
                f6.addProperty("inline", false);
                fields.add(f6);

                embed.add("fields", fields);

                JsonObject footer = new JsonObject();
                footer.addProperty("text", "Synapse Labs Studio • " + inviteUrl);
                embed.add("footer", footer);
                embed.addProperty("timestamp", java.time.Instant.now().toString());

                embeds.add(embed);
                payload.add("embeds", embeds);

                int code = executePost(webhookUrl, payload.toString());
                if (code >= 400) {
                    plugin.getLogger().warning("Discord webhook returned error HTTP " + code);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to dispatch Discord webhook: " + e.getMessage());
            }
        });
    }

    private int executePost(String webhookUrl, String jsonPayload) throws Exception {
        URL url = new URL(webhookUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", "SynapseLabs-AntiCheat");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        conn.disconnect();
        return responseCode;
    }
}
