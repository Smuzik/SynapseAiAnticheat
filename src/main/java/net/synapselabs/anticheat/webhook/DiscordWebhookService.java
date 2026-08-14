package net.synapselabs.anticheat.webhook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhookService {
    private final JavaPlugin plugin;

    public DiscordWebhookService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendFlagAlert(String playerName, String cheatType, int confidencePercent, int vl, int ping, double distance, float angleOffset) {
        boolean enabled = plugin.getConfig().getBoolean("discord.webhook.enabled", false);
        if (!enabled) return;

        String webhookUrl = plugin.getConfig().getString("discord.webhook.url", "");
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("YOUR_WEBHOOK_HERE")) return;

        int minVl = plugin.getConfig().getInt("discord.webhook.min_vl_to_send", 4);
        if (vl < minVl) return;

        String inviteUrl = plugin.getConfig().getString("discord.invite_url", "https://dsc.gg/synapselabs");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String avatarUrl = "https://mc-heads.net/avatar/" + playerName + "/64";
                String jsonPayload = String.format("""
                    {
                      "username": "Synapse AI-AntiCheat",
                      "avatar_url": "https://i.imgur.com/r62KqGZ.png",
                      "embeds": [
                        {
                          "title": "🚨 Зафиксировано нарушение на сервере",
                          "color": 65535,
                          "thumbnail": { "url": "%s" },
                          "fields": [
                            { "name": "👤 Игрок", "value": "`%s`", "inline": true },
                            { "name": "⚔️ Тип чита", "value": "`%s`", "inline": true },
                            { "name": "📊 Уверенность ИИ", "value": "`%d%%`", "inline": true },
                            { "name": "⚠️ Уровень VL", "value": "`%d`", "inline": true },
                            { "name": "📶 Пинг", "value": "`%d ms`", "inline": true },
                            { "name": "📐 Метрики удара", "value": "Дистанция: `%.2fm` | Угол: `%.1f°`", "inline": false }
                          ],
                          "footer": { "text": "SynapseLabs • %s" },
                          "timestamp": "%s"
                        }
                      ]
                    }
                    """,
                    avatarUrl,
                    playerName,
                    cheatType,
                    confidencePercent,
                    vl,
                    ping,
                    distance,
                    angleOffset,
                    inviteUrl,
                    java.time.Instant.now().toString()
                );

                URL url = new URL(webhookUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "SynapseLabs-AntiCheat");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }
}
