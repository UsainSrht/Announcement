package me.usainsrht.announcement.mod;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Prints text into the local chat of a Minecraft client (used when the mod runs client side on a server that does
 * not have it).
 * <p>
 * The local player is told a system message through the command-output method every entity has:
 * {@code sendSystemMessage(Component)} on 1.19+ and {@code sendMessage(Component, UUID)} before that.
 * Classes are found by name sets covering Mojang, MCP/SRG and Yarn/intermediary names, methods by shape.
 * Text is passed as a literal with section-sign formatting codes, which the client font renderer understands.
 */
final class ClientChat {
    private static final Set<String> PLAYER = Probe.names("LocalPlayer", "ClientPlayerEntity", "class_746");
    private static final Set<String> SEND_NAMES = Probe.names("sendSystemMessage", "method_43496", "m_213846_");

    private final Object minecraft;
    private final Method send;
    private final boolean needsUuid;
    private final Method literalFactory;      // static (String) -> Component
    private final Constructor<?> literalCtor; // new TextComponent(String) on older versions

    private ClientChat(Object minecraft, Method send, boolean needsUuid, Method literalFactory,
                       Constructor<?> literalCtor) {
        this.minecraft = minecraft;
        this.send = send;
        this.needsUuid = needsUuid;
        this.literalFactory = literalFactory;
        this.literalCtor = literalCtor;
    }

    /** @return null when this Minecraft version could not be mapped */
    static ClientChat probe(Object minecraft) {
        if (!(minecraft instanceof Executor)) return null;
        Class<?> component = Probe.firstClass("net.minecraft.network.chat.Component",
                "net.minecraft.util.text.ITextComponent", "net.minecraft.class_2561");
        if (component == null) return null;

        Method send = null;
        boolean needsUuid = false;
        Class<?> playerType = playerType(minecraft);
        if (playerType == null) return null;
        Method unique = null;
        int uniqueCount = 0;
        for (Method m : playerType.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers())) continue;
            if (p.length == 1 && p[0] == component) {
                if (SEND_NAMES.contains(m.getName())) send = m;
                unique = m;
                uniqueCount++;
            } else if (p.length == 2 && p[0] == component && p[1] == UUID.class && send == null) {
                send = m;
                needsUuid = true;
            }
        }
        if (send == null && uniqueCount == 1) send = unique;
        if (send == null) return null;

        Constructor<?> ctor = null;
        Method factory = null;
        Class<?> literal = Probe.firstClass("net.minecraft.network.chat.TextComponent",
                "net.minecraft.util.text.StringTextComponent", "net.minecraft.class_2585");
        if (literal != null) {
            try {
                ctor = literal.getConstructor(String.class);
            } catch (Throwable ignored) {
            }
        }
        if (ctor == null) {
            for (Method m : component.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) && p.length == 1 && p[0] == String.class
                        && component.isAssignableFrom(m.getReturnType())) {
                    String n = m.getName();
                    if (n.equals("literal") || n.equals("method_43470") || n.equals("m_237113_")) {
                        factory = m;
                        break;
                    }
                    if (factory == null && !n.toLowerCase().contains("transl") && !n.toLowerCase().contains("keybind")) {
                        factory = m;
                    }
                }
            }
        }
        if (ctor == null && factory == null) return null;
        return new ClientChat(minecraft, send, needsUuid, factory, ctor);
    }

    /** The declared type of Minecraft's local player field. */
    private static Class<?> playerType(Object minecraft) {
        for (Class<?> c = minecraft.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                String full = f.getType().getName();
                if (!Modifier.isStatic(f.getModifiers()) && PLAYER.contains(full.substring(full.lastIndexOf('.') + 1))) {
                    return f.getType();
                }
            }
        }
        return null;
    }

    private Object player() {
        return Probe.fieldBySimpleTypeName(minecraft, PLAYER);
    }

    /** true while the player is inside a world (connected to a server). */
    boolean inWorld() {
        return player() != null;
    }

    /** The local player's name through the stable authlib GameProfile type, or null. */
    String playerName() {
        try {
            Object player = player();
            Class<?> profileType = Probe.classOrNull("com.mojang.authlib.GameProfile");
            if (player == null || profileType == null) return null;
            for (Method m : player.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == profileType) {
                    Object profile = Probe.invoke(m, player);
                    for (String n : new String[]{"getName", "name"}) {
                        try {
                            return (String) profileType.getMethod(n).invoke(profile);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Shows legacy-coded text, one chat line per \n, on the render thread. */
    void show(final String legacy) {
        ((Executor) minecraft).execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Object player = player();
                    if (player == null) return;
                    for (String line : legacy.split("\n", -1)) {
                        if (line.isEmpty()) line = " ";
                        Object component = literalCtor != null
                                ? literalCtor.newInstance(line)
                                : Probe.invoke(literalFactory, null, line);
                        if (needsUuid) Probe.invoke(send, player, component, new UUID(0L, 0L));
                        else Probe.invoke(send, player, component);
                    }
                } catch (Throwable ignored) {
                    // not ready; try again next cycle
                }
            }
        });
    }
}
