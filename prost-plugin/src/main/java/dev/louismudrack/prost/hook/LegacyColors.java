package dev.louismudrack.prost.hook;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** § and & colour codes (what most placeholders return) -> MiniMessage tags, so they mix with Prost's MiniMessage texts. */
public final class LegacyColors {
    private static final Pattern HEX = Pattern.compile("[§&]x((?:[§&][0-9a-fA-F]){6})");
    private static final Pattern CODE = Pattern.compile("[§&]([0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"), Map.entry('r', "reset"));

    private LegacyColors() {}

    public static String toMiniMessage(String text) {
        if (text.indexOf('§') < 0 && text.indexOf('&') < 0) return text;
        String hex = HEX.matcher(text).replaceAll(m -> Matcher.quoteReplacement("<#" + m.group(1).replaceAll("[§&]", "") + ">"));
        return CODE.matcher(hex).replaceAll(m -> Matcher.quoteReplacement("<" + TAGS.get(Character.toLowerCase(m.group(1).charAt(0))) + ">"));
    }
}
