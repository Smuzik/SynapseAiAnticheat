package net.synapselabs.anticheat.compat;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompatUtils {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String color(String msg) {
        if (msg == null) return "";
        // Hex Color Translation (1.16+)
        Matcher matcher = HEX_PATTERN.matcher(msg);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append("§").append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static void sendMessage(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        player.sendMessage(color(message));
    }

    public static void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(message)));
        } catch (Throwable ignored) {
            player.sendMessage(color(message));
        }
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline()) return;
        try {
            player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
        } catch (Throwable ignored) {}
    }

    public static void playSound(Player player, String soundName, float volume, float pitch) {
        if (player == null || !player.isOnline()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Throwable ignored) {}
    }
}
