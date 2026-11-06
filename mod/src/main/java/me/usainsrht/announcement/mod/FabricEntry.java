package me.usainsrht.announcement.mod;

import net.fabricmc.api.ModInitializer;

/** Fabric entry point (server and client). */
public final class FabricEntry implements ModInitializer {
    @Override
    public void onInitialize() {
        ModRuntime.start(new FabricInfo());
    }
}
