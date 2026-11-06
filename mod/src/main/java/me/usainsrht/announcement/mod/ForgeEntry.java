package me.usainsrht.announcement.mod;

import net.minecraftforge.fml.common.Mod;

/** Forge entry point (1.16.5 through the latest Forge). */
@Mod("announcement")
public final class ForgeEntry {
    public ForgeEntry() {
        ModRuntime.start(new ForgeLikeInfo("forge",
                "net.minecraftforge.server.ServerLifecycleHooks",
                "net.minecraftforge.fml.server.ServerLifecycleHooks"));
    }
}
