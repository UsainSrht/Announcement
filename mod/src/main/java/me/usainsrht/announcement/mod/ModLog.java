package me.usainsrht.announcement.mod;

import me.usainsrht.announcement.core.Log;

import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Logs through log4j (what every Minecraft version uses) via reflection, falling back to java.util.logging. */
final class ModLog implements Log {
    private Object log4j;
    private Method info, warn, error;
    private final Logger jul = Logger.getLogger("Announcement");

    ModLog() {
        try {
            Class<?> manager = Class.forName("org.apache.logging.log4j.LogManager");
            log4j = manager.getMethod("getLogger", String.class).invoke(null, "Announcement");
            Class<?> type = Class.forName("org.apache.logging.log4j.Logger");
            info = type.getMethod("info", String.class);
            warn = type.getMethod("warn", String.class);
            error = type.getMethod("error", String.class, Throwable.class);
        } catch (Throwable t) {
            log4j = null;
        }
    }

    @Override
    public void info(String message) {
        try {
            if (log4j != null) {
                info.invoke(log4j, message);
                return;
            }
        } catch (Throwable ignored) {
        }
        jul.info(message);
    }

    @Override
    public void warn(String message) {
        try {
            if (log4j != null) {
                warn.invoke(log4j, message);
                return;
            }
        } catch (Throwable ignored) {
        }
        jul.warning(message);
    }

    @Override
    public void error(String message, Throwable t) {
        try {
            if (log4j != null) {
                error.invoke(log4j, message, t);
                return;
            }
        } catch (Throwable ignored) {
        }
        jul.log(Level.SEVERE, message, t);
    }
}
