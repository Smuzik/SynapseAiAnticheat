package net.synapselabs.anticheat.compat;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompatUtils {
    private static final Pattern HEX_AMP_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_HASH_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    public static String color(String msg) {
        if (msg == null) return "";

        // Replace &#RRGGBB
        Matcher matcherAmp = HEX_AMP_PATTERN.matcher(msg);
        StringBuffer buffer = new StringBuffer();
        while (matcherAmp.find()) {
            String hex = matcherAmp.group(1);
            matcherAmp.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        matcherAmp.appendTail(buffer);
        String step1 = buffer.toString();

        // Translate standard & codes
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', step1);
    }

    public static String rgbGradient(String text, String hexFrom, String hexTo) {
        if (text == null || text.isEmpty()) return "";
        try {
            Color color1 = Color.decode(hexFrom.startsWith("#") ? hexFrom : "#" + hexFrom);
            Color color2 = Color.decode(hexTo.startsWith("#") ? hexTo : "#" + hexTo);

            StringBuilder builder = new StringBuilder();
            int length = text.length();
            for (int i = 0; i < length; i++) {
                float ratio = (float) i / (float) Math.max(1, length - 1);
                int red = (int) (color1.getRed() * (1 - ratio) + color2.getRed() * ratio);
                int green = (int) (color1.getGreen() * (1 - ratio) + color2.getGreen() * ratio);
                int blue = (int) (color1.getBlue() * (1 - ratio) + color2.getBlue() * ratio);

                String hex = String.format("#%02x%02x%02x", red, green, blue);
                builder.append(ChatColor.of(hex)).append(text.charAt(i));
            }
            return builder.toString();
        } catch (Exception e) {
            return color(text);
        }
    }

    public static TextComponent createGrimButton(String label, String hexColor, String hoverText, String command) {
        TextComponent btn = new TextComponent(color("&8[" + hexColor + label + "&8]"));
        btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(color(hoverText)).create()));
        btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        return btn;
    }

    public static void sendMessage(Player player, String message) {
        if (player == null || !player.isOnline()) return;
        player.sendMessage(color(message));
    }

    public static void sendComponent(Player player, TextComponent component) {
        if (player == null || !player.isOnline()) return;
        player.spigot().sendMessage(component);
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
