package me.usainsrht.announcement.core;

/** One configured announcement. */
public final class Entry {
    public final String message;
    /** null = use the global placeholders.per_player setting. */
    public final Boolean perPlayer;

    public Entry(String message, Boolean perPlayer) {
        this.message = message;
        this.perPlayer = perPlayer;
    }
}
