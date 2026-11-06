package me.usainsrht.announcement.mod;

import java.lang.reflect.Method;

/** Fabric: FabricLoader.getGameInstance() is the MinecraftServer on a dedicated server, the client otherwise. */
final class FabricInfo implements LoaderInfo {
    private Object loader;
    private Method gameInstance;
    private boolean clientEnv;
    private boolean resolved;

    @Override
    public String name() {
        return "fabric";
    }

    private Object game() {
        try {
            if (!resolved) {
                Class<?> type = Class.forName("net.fabricmc.loader.api.FabricLoader");
                loader = type.getMethod("getInstance").invoke(null);
                gameInstance = type.getMethod("getGameInstance");
                Object env = type.getMethod("getEnvironmentType").invoke(loader);
                clientEnv = "CLIENT".equals(String.valueOf(env));
                resolved = true;
            }
            return gameInstance.invoke(loader);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Object findServer() {
        Object game = game();
        if (game == null) return null;
        if (!clientEnv) return game;
        return ForgeLikeInfo.integratedServer(game);
    }

    @Override
    public Object findClient() {
        game();
        return clientEnv ? game() : null;
    }
}
