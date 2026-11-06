package me.usainsrht.announcement.mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Reflection helpers. Minecraft is obfuscated differently on every loader and version (Mojang names, SRG,
 * Yarn/intermediary), so nothing here relies on a game method name. Members are found by their shape instead:
 * JDK types, Brigadier types and loader API types are stable everywhere.
 */
final class Probe {
    private Probe() {
    }

    static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name, false, Probe.class.getClassLoader());
        } catch (Throwable t) {
            try {
                ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                return ctx == null ? null : Class.forName(name, false, ctx);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    static Class<?> firstClass(String... names) {
        for (String n : names) {
            Class<?> c = classOrNull(n);
            if (c != null) return c;
        }
        return null;
    }

    static boolean isGameType(Class<?> c) {
        if (c.isPrimitive() || c.isArray() || c == Object.class) return false;
        String n = c.getName();
        return !n.startsWith("java.") && !n.startsWith("javax.") && !n.startsWith("com.mojang.brigadier.");
    }

    static Object invoke(Method m, Object target, Object... args) throws Exception {
        try {
            m.setAccessible(true);
        } catch (Throwable ignored) {
        }
        return m.invoke(target, args);
    }

    static Object staticNoArg(Class<?> owner, Class<?> returnType) {
        for (Method m : owner.getMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == returnType) {
                try {
                    Object o = invoke(m, null);
                    if (o != null) return o;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Value of the first instance field (searching superclasses) whose declared type name satisfies the matcher. */
    static Object fieldBySimpleTypeName(Object target, Set<String> simpleNames) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                String full = f.getType().getName();
                String simple = full.substring(full.lastIndexOf('.') + 1);
                if (simpleNames.contains(simple)) {
                    try {
                        f.setAccessible(true);
                        return f.get(target);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    static Set<String> names(String... n) {
        return new HashSet<>(Arrays.asList(n));
    }
}
