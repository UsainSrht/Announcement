package me.usainsrht.announcement.mod;

import com.mojang.brigadier.CommandDispatcher;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Mapping independent access to a MinecraftServer:
 * <ul>
 *   <li>the server is an {@link Executor}, which gives us its main thread,</li>
 *   <li>the object that owns the Brigadier {@link CommandDispatcher} is the Commands manager,</li>
 *   <li>the Commands manager's only game-typed (source, String) method runs a command line, and the type of that
 *       first parameter is the command source class,</li>
 *   <li>the server's no-argument method returning that source class makes a console (permission 4) source.</li>
 * </ul>
 */
final class ServerAccess {
    final Object server;
    private final Executor executor;
    private final Method commandsGetter;
    private final Method dispatcherGetter;
    private final Method perform;
    private final Method sourceFactory;

    private ServerAccess(Object server, Method commandsGetter, Method dispatcherGetter, Method perform,
                         Method sourceFactory) {
        this.server = server;
        this.executor = (Executor) server;
        this.commandsGetter = commandsGetter;
        this.dispatcherGetter = dispatcherGetter;
        this.perform = perform;
        this.sourceFactory = sourceFactory;
    }

    /** @return null when the object does not look like a MinecraftServer */
    static ServerAccess probe(Object server) {
        if (!(server instanceof Executor)) return null;
        Class<?> sc = server.getClass();
        for (Method holder : sc.getMethods()) {
            if (holder.getParameterCount() != 0) continue;
            Class<?> commandsType = holder.getReturnType();
            if (!Probe.isGameType(commandsType)) continue;

            Method dispatcher = null;
            Method perform = null;
            Class<?> sourceType = null;
            for (Method m : commandsType.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == CommandDispatcher.class) dispatcher = m;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[1] == String.class && Probe.isGameType(p[0])
                        && (m.getReturnType() == void.class || m.getReturnType() == int.class)) {
                    // performPrefixedCommand(source, String) / handleCommand(source, String)
                    if (perform == null || m.getName().toLowerCase().contains("prefixed")) {
                        perform = m;
                        sourceType = p[0];
                    }
                }
            }
            if (dispatcher == null || perform == null) continue;

            Method factory = null;
            for (Method m : sc.getMethods()) {
                if (m.getParameterCount() == 0 && sourceType.isAssignableFrom(m.getReturnType())
                        && m.getReturnType() != Object.class) {
                    factory = m;
                    break;
                }
            }
            if (factory == null) continue;
            return new ServerAccess(server, holder, dispatcher, perform, factory);
        }
        return null;
    }

    private Object commands() throws Exception {
        return Probe.invoke(commandsGetter, server);
    }

    /** The live Brigadier dispatcher (a new object after every /reload), or null while the server is still booting. */
    @SuppressWarnings("unchecked")
    CommandDispatcher<Object> dispatcher() throws Exception {
        Object commands = commands();
        if (commands == null) return null;
        return (CommandDispatcher<Object>) Probe.invoke(dispatcherGetter, commands);
    }

    /** Runs a command with console permissions. Must be called on the server thread. */
    void run(String command) throws Exception {
        runAs(sourceFactory == null ? null : Probe.invoke(sourceFactory, server), command);
    }

    /** Runs a command as the given command source (any source object handed to a Brigadier command). */
    void runAs(Object source, String command) throws Exception {
        Object commands = commands();
        if (commands == null) return;
        Probe.invoke(perform, commands, source, command);
    }

    /**
     * Runs on the server thread. While a server is shutting down its executor runs tasks inline on the calling
     * thread instead; the first task (which always arrives while the server is alive) records the real server
     * thread so that such late tasks can be dropped.
     */
    void onServerThread(final Runnable r) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Thread current = Thread.currentThread();
                if (serverThread == null) serverThread = current;
                else if (serverThread != current) return;
                r.run();
            }
        });
    }

    private volatile Thread serverThread;
}
