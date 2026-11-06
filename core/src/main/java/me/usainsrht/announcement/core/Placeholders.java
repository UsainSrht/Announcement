package me.usainsrht.announcement.core;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Built-in placeholders that work everywhere, no placeholder plugin needed.
 * PlaceholderAPI / MiniPlaceholders are layered on top of this by the Bukkit/Paper code.
 */
public final class Placeholders {
    private Placeholders() {
    }

    /**
     * @param playerName literal player name, or null when it is not known/needed
     * @param selector   when true %player_name% becomes the selector tag, which the game resolves per player
     */
    public static String builtin(String text, String playerName, boolean selector) {
        if (text.indexOf('%') < 0) return text;
        String name = null;
        if (selector) name = "<selector:@s>";
        else if (playerName != null) name = escape(playerName);
        if (name != null) {
            text = text.replace("%player_name%", name)
                    .replace("%player_displayname%", name)
                    .replace("%player%", name);
        }
        return text.replace("%server_time%", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
                .replace("%server_date%", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    /** Player names are plain text, never MiniMessage markup. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("<", "\\<");
    }

    public static boolean needsPlayerSelector(String text) {
        return text.contains("%player_name%") || text.contains("%player_displayname%") || text.contains("%player%");
    }
}
