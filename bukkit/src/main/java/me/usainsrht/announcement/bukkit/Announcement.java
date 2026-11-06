package me.usainsrht.announcement.bukkit;

import me.clip.placeholderapi.PlaceholderAPI;
import me.usainsrht.announcement.core.AnnConfig;
import me.usainsrht.announcement.core.Entry;
import me.usainsrht.announcement.core.MiniText;
import me.usainsrht.announcement.core.NativeRenderer;
import me.usainsrht.announcement.core.Placeholders;
import me.usainsrht.announcement.core.Selector;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import space.arim.morepaperlib.MorePaperLib;
import space.arim.morepaperlib.scheduling.ScheduledTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Spigot / Paper / Purpur / Folia entry point.
 * <ul>
 *   <li>Paper (1.18.2+): rendered with the server's own MiniMessage, MiniPlaceholders supported.</li>
 *   <li>Spigot and older Paper: rendered with the bundled MiniMessage subset and sent through the bungee chat API.</li>
 *   <li>PlaceholderAPI is used when installed, on every one of them.</li>
 * </ul>
 */
public final class Announcement extends JavaPlugin {
    /** Set while a copy of this jar is announcing on this JVM (guards against hybrid servers loading it twice). */
    public static final String RUNNING_PROPERTY = "announcement.running";

    private static Announcement instance;

    private final AnnConfig config = new AnnConfig();
    private final Selector selector = new Selector();
    private MorePaperLib morePaperLib;
    private ScheduledTask timer;
    private NativeRenderer nativeRenderer;
    private boolean papi;
    private boolean active;

    public String commandPermission() {
        return config.getString("command.permission", "announcement.use");
    }

    public static Announcement getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        if (System.getProperty(RUNNING_PROPERTY) != null) {
            getLogger().warning("Announcement is already running as a mod on this server (" + System.getProperty(RUNNING_PROPERTY)
                    + "); disabling the plugin copy.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        System.setProperty(RUNNING_PROPERTY, "bukkit");
        active = true;

        morePaperLib = new MorePaperLib(this);
        papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        nativeRenderer = loadNativeRenderer();

        if (!loadConfig()) return;
        registerCommand();
        schedule();
        Telemetry.start(this, () -> config, () -> nativeRenderer != null ? "Paper MiniMessage" : "built-in",
                () -> papi, () -> nativeRenderer != null && nativeRenderer.usesMiniPlaceholders());

        getLogger().info("Rendering with " + (nativeRenderer != null ? "Paper MiniMessage" : "built-in MiniMessage subset")
                + (papi ? ", PlaceholderAPI" : "")
                + (nativeRenderer != null && nativeRenderer.usesMiniPlaceholders() ? ", MiniPlaceholders" : ""));
    }

    @Override
    public void onDisable() {
        if (!active) return;
        if (timer != null) timer.cancel();
        System.clearProperty(RUNNING_PROPERTY);
    }

    // ---------------------------------------------------------------- setup

    private NativeRenderer loadNativeRenderer() {
        try {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            if (!net.kyori.adventure.audience.Audience.class.isAssignableFrom(Player.class)) return null;
        } catch (Throwable t) {
            return null; // Spigot, or a Paper too old to ship MiniMessage
        }
        try {
            return (NativeRenderer) Class.forName("me.usainsrht.announcement.paper.PaperRenderer")
                    .getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            getLogger().warning("Paper renderer unavailable (" + t + "), using built-in renderer.");
            return null;
        }
    }

    private boolean loadConfig() {
        try {
            Path dir = getDataFolder().toPath();
            AnnConfig.saveDefault(dir, Announcement.class);
            config.load(dir.resolve("config.yml"));
            return true;
        } catch (Exception e) {
            getLogger().severe("Could not load config.yml: " + e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void registerCommand() {
        try {
            Object server = Bukkit.getServer();
            CommandMap map = (CommandMap) server.getClass().getMethod("getCommandMap").invoke(server);
            map.register("announcement", new AnnouncementCommand(this,
                    config.getString("command.name", "announcement"),
                    config.getString("command.description", "command to announce custom messages"),
                    config.getString("command.usage", "/announcement <message>")));
        } catch (Exception e) {
            getLogger().severe("Could not register the command: " + e);
        }
    }

    private void schedule() {
        if (timer != null) timer.cancel();
        long period = Math.max(1, config.delaySeconds()) * 20L;
        timer = morePaperLib.scheduling().globalRegionalScheduler().runAtFixedRate((Runnable) this::announce, 1L, period);
    }

    /** Re-reads config.yml and restarts the timer. */
    public boolean reload() {
        try {
            config.load(getDataFolder().toPath().resolve("config.yml"));
        } catch (Exception e) {
            getLogger().severe("Could not reload config.yml: " + e);
            return false;
        }
        schedule();
        return true;
    }

    // ---------------------------------------------------------------- announcing

    /** Announces the next configured entry. */
    public void announce() {
        Entry next = selector.next(config);
        if (next != null) announce(next);
    }

    /** Announces a one-off message (used by /announcement &lt;message&gt;). */
    public void announce(String message) {
        announce(new Entry(message, null));
    }

    private void announce(Entry entry) {
        Telemetry.sent();
        List<String> template = config.template();
        boolean providers = papi || (nativeRenderer != null && nativeRenderer.usesMiniPlaceholders());
        boolean selfRef = Placeholders.needsPlayerSelector(entry.message) || anyContainsPlayer(template);
        boolean perPlayer = (entry.perPlayer != null ? entry.perPlayer : config.perPlayer()) && (providers || selfRef);

        if (perPlayer) {
            for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
                Object rendered = render(player, template, entry.message);
                deliver(player, rendered);
            }
        } else {
            // parsed once with global placeholders, identical for everyone
            Object rendered = render(null, template, entry.message);
            for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) deliver(player, rendered);
        }

        if (config.broadcast()) {
            Object rendered = render(null, template, entry.message);
            if (rendered instanceof List) {
                @SuppressWarnings("unchecked")
                List<MiniText.Seg> segs = (List<MiniText.Seg>) rendered;
                Bukkit.getConsoleSender().sendMessage(MiniText.toLegacy(segs));
            } else {
                nativeRenderer.send(Bukkit.getConsoleSender(), rendered);
            }
        }
    }

    private static boolean anyContainsPlayer(List<String> lines) {
        for (String l : lines) if (Placeholders.needsPlayerSelector(l)) return true;
        return false;
    }

    /** @return an Adventure Component (native) or a List of segments (built-in renderer) */
    private Object render(Player player, List<String> template, String message) {
        Function<String, String> pre = text -> {
            text = Placeholders.builtin(text, player == null ? null : player.getName(), false);
            if (papi) {
                try {
                    text = PlaceholderAPI.setPlaceholders(player, text);
                } catch (Throwable t) {
                    getLogger().warning("PlaceholderAPI failed: " + t);
                }
            }
            return text;
        };
        boolean custom = config.customMiniMessage();
        if (nativeRenderer != null) {
            List<String> lines = new ArrayList<>(template.size());
            for (String line : template) lines.add(pre.apply(line));
            return nativeRenderer.render(player, lines, pre.apply(message), custom);
        }
        return MiniText.renderTemplate(template, message, pre, custom);
    }

    private void deliver(Player player, Object rendered) {
        // Folia: talk to a player from the region/thread that owns it. Elsewhere this simply runs now.
        morePaperLib.scheduling().entitySpecificScheduler(player).run(() -> send(player, rendered), null);
    }

    @SuppressWarnings("unchecked")
    private void send(Player player, Object rendered) {
        if (!player.isOnline()) return;
        if (rendered instanceof List) {
            BaseComponent[] components = ComponentSerializer.parse(MiniText.toJson((List<MiniText.Seg>) rendered));
            player.spigot().sendMessage(components);
        } else {
            nativeRenderer.send(player, rendered);
        }
    }
}
