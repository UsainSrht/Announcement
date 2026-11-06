package me.usainsrht.announcement.mod;

import net.neoforged.fml.common.Mod;

/** NeoForge entry point. */
@Mod("announcement")
public final class NeoForgeEntry {
    public NeoForgeEntry() {
        ModRuntime.start(new ForgeLikeInfo("neoforge", "net.neoforged.neoforge.server.ServerLifecycleHooks"));
    }
}
