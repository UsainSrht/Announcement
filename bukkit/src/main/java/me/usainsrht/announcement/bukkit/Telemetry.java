package me.usainsrht.announcement.bukkit;

import me.usainsrht.announcement.core.AnnConfig;
import me.usainsrht.announcement.core.Entry;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Anonymous usage statistics through bStats (https://bstats.org). Nothing but
 * counts and feature flags is sent,
 * never message text. Server owners opt out globally in
 * {@code plugins/bStats/config.yml}.
 * Mods (Fabric/Forge/NeoForge) never send anything: bStats has no mod-loader
 * platform.
 */
final class Telemetry {
    /**
     * Plugin id from https://bstats.org/plugin/bukkit/Announcement; while 0 nothing
     * is started.
     */
    private static final int BSTATS_PLUGIN_ID = 35185;

    private Telemetry() {
    }

    static void start(JavaPlugin plugin, Supplier<AnnConfig> config, Supplier<String> renderer,
            Supplier<Boolean> papi, Supplier<Boolean> miniPlaceholders) {
        if (BSTATS_PLUGIN_ID <= 0)
            return;
        try {
            Metrics m = new Metrics(plugin, BSTATS_PLUGIN_ID);

            m.addCustomChart(new SimplePie("renderer", renderer::get));

            m.addCustomChart(new SimplePie("placeholder_mode", () -> {
                AnnConfig c = config.get();
                int per = 0, global = 0;
                for (Entry e : c.getAnnouncements()) {
                    if (e.perPlayer != null ? e.perPlayer : c.perPlayer())
                        per++;
                    else
                        global++;
                }
                if (per > 0 && global > 0)
                    return "mixed";
                return global > 0 ? "everyone" : "per player";
            }));

            m.addCustomChart(new AdvancedPie("placeholder_providers", () -> {
                Map<String, Integer> out = new HashMap<>();
                out.put("PlaceholderAPI", papi.get() ? 1 : 0);
                out.put("MiniPlaceholders", miniPlaceholders.get() ? 1 : 0);
                out.put("built-in only", !papi.get() && !miniPlaceholders.get() ? 1 : 0);
                out.values().removeIf(v -> v == 0);
                return out;
            }));

            m.addCustomChart(new SimplePie("announcement_count", () -> bucket(config.get().getAnnouncements().size(),
                    0, 1, 5, 10, 25, 50)));
            m.addCustomChart(new SimplePie("delay", () -> bucket(config.get().delaySeconds(),
                    30, 60, 120, 300, 600, 1800)));
            m.addCustomChart(new SimplePie("order", () -> config.get().ordered() ? "ordered" : "random"));
            m.addCustomChart(new SimplePie("console_broadcast", () -> config.get().broadcast() ? "on" : "off"));
            m.addCustomChart(
                    new SimplePie("custom_minimessage", () -> config.get().customMiniMessage() ? "on" : "off"));
            m.addCustomChart(new SimplePie("folia", () -> isFolia() ? "folia" : "other"));
            m.addCustomChart(new SingleLineChart("announcements_sent", Telemetry::drainSent));
        } catch (Throwable t) {
            plugin.getLogger().fine("bStats unavailable: " + t);
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger SENT = new java.util.concurrent.atomic.AtomicInteger();

    /** Counts one announcement round. */
    static void sent() {
        SENT.incrementAndGet();
    }

    private static int drainSent() {
        return SENT.getAndSet(0);
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** "<= 60" style bucket so the dashboard stays readable. */
    private static String bucket(int value, int... limits) {
        for (int l : limits)
            if (value <= l)
                return "<= " + l;
        return "> " + limits[limits.length - 1];
    }
}
