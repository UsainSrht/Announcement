package me.usainsrht.announcement.core;

import java.util.List;

/**
 * Renderer backed by the server's own Adventure/MiniMessage (Paper, Purpur, Folia).
 * Declared with plain Objects so the Java 8 Bukkit module can use it without seeing Adventure types.
 */
public interface NativeRenderer {
    /**
     * @param player        a Bukkit Player, or null to render with global placeholders only
     * @param customCenter use the MiniMessage instance that knows the centered tag
     * @return an opaque Adventure Component
     */
    Object render(Object player, List<String> template, String message, boolean customCenter);

    /** @param audience a Bukkit Player or CommandSender (all are Adventure Audiences on Paper) */
    void send(Object audience, Object component);

    /** true when MiniPlaceholders is installed and will be used for the next render. */
    boolean usesMiniPlaceholders();
}
