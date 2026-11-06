package me.usainsrht.announcement.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import me.usainsrht.announcement.core.AnnConfig;
import me.usainsrht.announcement.core.Entry;
import me.usainsrht.announcement.core.Log;
import me.usainsrht.announcement.core.MiniText;
import me.usainsrht.announcement.core.Placeholders;
import me.usainsrht.announcement.core.Selector;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Loader independent mod logic: one daemon thread watches for a running MinecraftServer (dedicated or integrated)
 * and announces on its main thread by running vanilla commands; with no server it announces into the local chat of
 * a game client instead.
 */
final class ModRuntime implements Runnable {
    static final String RUNNING_PROPERTY = "announcement.running";

    private static ModRuntime instance;

    private final LoaderInfo info;
    private final Log log = new ModLog();
    private final Selector selector = new Selector();
    private final Path configFile;

    private volatile AnnConfig config = new AnnConfig();

    // server side state (touched by the watcher thread only, except where noted)
    private Object lastServer;
    private volatile ServerAccess access;
    private CommandDispatcher<Object> registeredOn;
    private long nextServerAnnounce;

    // client side state
    private Object lastClient;
    private ClientChat client;
    private boolean clientProbed;
    private boolean wasInWorld;
    private long nextClientAnnounce;

    private ModRuntime(LoaderInfo info, Path configFile) {
        this.info = info;
        this.configFile = configFile;
    }

    static synchronized void start(LoaderInfo info) {
        if (instance != null) return;
        String other = System.getProperty(RUNNING_PROPERTY);
        if (other != null) return; // e.g. a hybrid server where the Bukkit plugin copy is already announcing
        Path dir = Paths.get("config", "announcement").toAbsolutePath();
        ModRuntime rt = new ModRuntime(info, dir.resolve("config.yml"));
        try {
            AnnConfig.saveDefault(dir, ModRuntime.class);
            rt.reload();
        } catch (Exception e) {
            rt.log.error("Announcement could not load " + rt.configFile + ", mod disabled", e);
            return;
        }
        instance = rt;
        System.setProperty(RUNNING_PROPERTY, info.name());
        Thread t = new Thread(rt, "Announcement");
        t.setDaemon(true);
        t.start();
        rt.log.info("Announcement started (" + info.name() + ")");
    }

    boolean reload() {
        try {
            AnnConfig c = new AnnConfig();
            c.load(configFile);
            config = c;
            return true;
        } catch (Exception e) {
            log.error("Could not reload " + configFile, e);
            return false;
        }
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    private long initialDelayMs() {
        return Math.min(config.delaySeconds(), 15) * 1000L;
    }

    @Override
    public void run() {
        while (true) {
            try {
                tick();
            } catch (Throwable t) {
                log.error("Announcement tick failed", t);
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void tick() throws Exception {
        Object server = info.findServer();
        if (server != lastServer) {
            lastServer = server;
            registeredOn = null;
            access = null;
            if (server != null) {
                access = ServerAccess.probe(server);
                if (access == null) {
                    log.warn("Announcement: this Minecraft version is not supported, could not map the server ("
                            + server.getClass().getName() + ")");
                }
                nextServerAnnounce = now() + initialDelayMs();
            }
        }
        ServerAccess a = access;
        if (a != null) {
            ensureCommand(a);
            if (now() >= nextServerAnnounce) {
                nextServerAnnounce = now() + config.delaySeconds() * 1000L;
                Entry e = selector.next(config);
                if (e != null) announceOnServer(a, e);
            }
            return;
        }
        if (server == null) clientTick();
    }

    // ------------------------------------------------------------------ server side

    private void announceOnServer(final ServerAccess a, final Entry entry) {
        a.onServerThread(new Runnable() {
            @Override
            public void run() {
                try {
                    send(a, entry);
                } catch (Throwable t) {
                    log.error("Announcement failed", t);
                }
            }
        });
    }

    /** Must run on the server thread. */
    private void send(ServerAccess a, Entry entry) throws Exception {
        AnnConfig c = config;
        Function<String, String> pre = new Function<String, String>() {
            @Override
            public String apply(String text) {
                return Placeholders.builtin(text, null, true);
            }
        };
        List<MiniText.Seg> segs = MiniText.renderTemplate(c.template(), entry.message, pre, c.customMiniMessage());
        String json = MiniText.toJson(segs);
        // Runs once per player: {"selector":"@s"} (the player name placeholder) resolves relative to the command
        // source, and unlike a plain "tellraw @a" an empty server does not log "No player was found".
        a.run("execute as @a run tellraw @s " + json);
        if (c.broadcast()) log.info(MiniText.toPlain(segs).trim());
    }

    private void ensureCommand(final ServerAccess a) throws Exception {
        final CommandDispatcher<Object> d = a.dispatcher();
        if (d == null || d == registeredOn) return;
        registeredOn = d;
        a.onServerThread(new Runnable() {
            @Override
            public void run() {
                try {
                    register(a, d);
                } catch (Throwable t) {
                    log.error("Could not register the announcement command", t);
                }
            }
        });
    }

    private void register(final ServerAccess a, CommandDispatcher<Object> d) {
        String name = config.getString("command.name", "announcement");
        if (d.getRoot().getChild(name) != null) return;
        CommandNode<Object> tellraw = d.getRoot().getChild("tellraw");
        // same permission rule as vanilla /tellraw (operators, or whatever permission mod hooks into it)
        Predicate<Object> requirement = tellraw != null ? tellraw.getRequirement() : new Predicate<Object>() {
            @Override
            public boolean test(Object o) {
                return false;
            }
        };

        LiteralArgumentBuilder<Object> root = LiteralArgumentBuilder.<Object>literal(name).requires(requirement);
        root.then(LiteralArgumentBuilder.<Object>literal("reload").requires(requirement)
                .executes(ctx -> {
                    boolean ok = reload();
                    reply(a, ctx.getSource(), ok ? "Config reloaded successfully!" : "Reload failed, see the log.",
                            ok ? "green" : "red");
                    return ok ? 1 : 0;
                }));
        root.then(RequiredArgumentBuilder.<Object, String>argument("message", StringArgumentType.greedyString())
                .requires(requirement)
                .executes(ctx -> {
                    try {
                        send(a, new Entry(StringArgumentType.getString(ctx, "message"), null));
                    } catch (Exception e) {
                        log.error("Announcement failed", e);
                        return 0;
                    }
                    return 1;
                }));
        d.register(root);
    }

    private void reply(ServerAccess a, Object source, String text, String color) {
        log.info(text);
        try {
            // "if entity @s" keeps the console from getting a "no entity" error
            a.runAs(source, "execute if entity @s run tellraw @s {\"text\":\"" + text + "\",\"color\":\"" + color + "\"}");
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ client side

    private void clientTick() {
        AnnConfig c = config;
        if (!c.clientEnabled()) return;
        Object mc = info.findClient();
        if (mc == null) return;
        if (mc != lastClient) {
            lastClient = mc;
            client = null;
            clientProbed = false;
        }
        if (!clientProbed) {
            clientProbed = true;
            client = ClientChat.probe(mc);
            if (client == null) log.warn("Announcement: client side display is not supported on this Minecraft version.");
        }
        if (client == null) return;
        if (!client.inWorld()) {
            wasInWorld = false;
            return;
        }
        if (!wasInWorld) {
            wasInWorld = true;
            nextClientAnnounce = now() + initialDelayMs();
        }
        if (now() < nextClientAnnounce) return;
        nextClientAnnounce = now() + c.delaySeconds() * 1000L;
        Entry e = selector.next(c);
        if (e == null) return;
        final String player = client.playerName();
        Function<String, String> pre = new Function<String, String>() {
            @Override
            public String apply(String text) {
                return Placeholders.builtin(text, player, false);
            }
        };
        client.show(MiniText.toLegacy(MiniText.renderTemplate(c.template(), e.message, pre, c.customMiniMessage())));
    }
}
