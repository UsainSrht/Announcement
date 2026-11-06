package me.usainsrht.announcement.mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/** Forge and NeoForge: ServerLifecycleHooks is loader API, so its name is the same in every obfuscation. */
final class ForgeLikeInfo implements LoaderInfo {
    private final String name;
    private final String[] hookClasses;
    private Method currentServer;

    ForgeLikeInfo(String name, String... hookClasses) {
        this.name = name;
        this.hookClasses = hookClasses;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object findServer() {
        try {
            if (currentServer == null) {
                Class<?> hooks = Probe.firstClass(hookClasses);
                if (hooks == null) return null;
                currentServer = hooks.getMethod("getCurrentServer");
            }
            return currentServer.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Object findClient() {
        Class<?> mc = Probe.firstClass("net.minecraft.client.Minecraft", "net.minecraft.class_310");
        return mc == null ? null : Probe.staticNoArg(mc, mc);
    }

    /** Looks inside a client for its integrated server (used by Fabric, where there is no lifecycle hook object). */
    static Object integratedServer(Object client) {
        for (Class<?> c = client.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().isPrimitive() || !Executor.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(client);
                    if (v != null && v != client && ServerAccess.probe(v) != null) return v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }
}
