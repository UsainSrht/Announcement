package net.fabricmc.api;

/** Compile-time stand-in for Fabric's ModInitializer. Never shipped; Fabric Loader provides the real one. */
public interface ModInitializer {
    void onInitialize();
}
