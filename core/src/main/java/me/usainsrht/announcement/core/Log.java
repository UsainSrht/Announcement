package me.usainsrht.announcement.core;

/** Tiny logging abstraction so the core does not care which platform it runs on. */
public interface Log {
    void info(String message);

    void warn(String message);

    void error(String message, Throwable t);
}
