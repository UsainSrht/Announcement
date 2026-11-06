package me.usainsrht.announcement.mod;

/** What differs between Fabric, Forge and NeoForge. Everything else is loader independent. */
interface LoaderInfo {
    String name();

    /** The running MinecraftServer (dedicated or integrated), or null when there is none right now. */
    Object findServer();

    /** The Minecraft client instance, or null on a dedicated server. */
    Object findClient();
}
