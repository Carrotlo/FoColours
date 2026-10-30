package me.foesio.foColours.util;

import net.md_5.bungee.api.ChatColor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[A-Fa-f0-9]{6}");
    private static final String ESCAPED_HASH_TOKEN = "__FOCOLOURS_ESCAPED_HASH__";
    private static final String ESCAPED_AMPERSAND_TOKEN = "__FOCOLOURS_ESCAPED_AMPERSAND__";
    private static final java.util.Map<Character, Character> SMALL_CAPS = java.util.Map.ofEntries(
            java.util.Map.entry('a', 'ᴀ'),
            java.util.Map.entry('b', 'ʙ'),
            java.util.Map.entry('c', 'ᴄ'),
            java.util.Map.entry('d', 'ᴅ'),
            java.util.Map.entry('e', 'ᴇ'),
            java.util.Map.entry('f', 'ꜰ'),
            java.util.Map.entry('g', 'ɢ'),
            java.util.Map.entry('h', 'ʜ'),
            java.util.Map.entry('i', 'ɪ'),
            java.util.Map.entry('j', 'ᴊ'),
            java.util.Map.entry('k', 'ᴋ'),
            java.util.Map.entry('l', 'ʟ'),
            java.util.Map.entry('m', 'ᴍ'),
            java.util.Map.entry('n', 'ɴ'),
            java.util.Map.entry('o', 'ᴏ'),
            java.util.Map.entry('p', 'ᴘ'),
            java.util.Map.entry('q', 'ǫ'),
            java.util.Map.entry('r', 'ʀ'),
            java.util.Map.entry('s', 'ꜱ'),
            java.util.Map.entry('t', 'ᴛ'),
            java.util.Map.entry('u', 'ᴜ'),
            java.util.Map.entry('v', 'ᴠ'),
            java.util.Map.entry('w', 'ᴡ'),
            java.util.Map.entry('x', 'x'),
            java.util.Map.entry('y', 'ʏ'),
            java.util.Map.entry('z', 'ᴢ')
    );

    private TextUtil() {
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String protectedInput = input
                .replace("\\#", ESCAPED_HASH_TOKEN)
                .replace("\\&", ESCAPED_AMPERSAND_TOKEN);
        Matcher matcher = HEX_PATTERN.matcher(protectedInput);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, ChatColor.of(matcher.group()).toString());
        }
        matcher.appendTail(builder);
        String translated = org.bukkit.ChatColor.translateAlternateColorCodes('&', builder.toString());
        return translated
                .replace(ESCAPED_HASH_TOKEN, "#")
                .replace(ESCAPED_AMPERSAND_TOKEN, "&");
    }

    public static String hexToLegacy(String hex) {
        if (hex == null) {
            return org.bukkit.ChatColor.WHITE.toString();
        }

        try {
            return ChatColor.of(hex).toString();
        } catch (IllegalArgumentException exception) {
            return org.bukkit.ChatColor.WHITE.toString();
        }
    }

    public static String stripColour(String input) {
        return org.bukkit.ChatColor.stripColor(colorize(input));
    }

    public static String applyRainbow(String input, List<String> palette, boolean advanceOnSpaces) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        if (palette.isEmpty()) {
            return input;
        }

        StringBuilder builder = new StringBuilder();
        int colorIndex = 0;
        for (char character : input.toCharArray()) {
            if (character == ' ' && !advanceOnSpaces) {
                builder.append(character);
                continue;
            }

            String hex = palette.get(colorIndex % palette.size());
            builder.append(hexToLegacy(hex)).append(character);
            colorIndex++;
        }
        builder.append(org.bukkit.ChatColor.RESET);
        return builder.toString();
    }

    public static String toSmallCapsPreservingColors(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        boolean colorEscape = false;
        for (char character : input.toCharArray()) {
            if (colorEscape) {
                builder.append(character);
                colorEscape = false;
                continue;
            }

            if (character == org.bukkit.ChatColor.COLOR_CHAR) {
                builder.append(character);
                colorEscape = true;
                continue;
            }

            char lowered = Character.toLowerCase(character);
            builder.append(SMALL_CAPS.getOrDefault(lowered, character));
        }
        return builder.toString();
    }
}
