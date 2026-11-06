package me.usainsrht.announcement.core;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Chooses the next announcement (ordered or random). */
public final class Selector {
    private final AtomicInteger index = new AtomicInteger();

    public Entry next(AnnConfig config) {
        List<Entry> all = config.getAnnouncements();
        if (all.isEmpty()) return null;
        if (config.ordered()) {
            return all.get(Math.floorMod(index.getAndIncrement(), all.size()));
        }
        return all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }
}
