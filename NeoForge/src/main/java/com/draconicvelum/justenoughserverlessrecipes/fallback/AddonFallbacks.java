package com.draconicvelum.justenoughserverlessrecipes.fallback;

import com.draconicvelum.justenoughserverlessrecipes.JesrConfig;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AddonFallbacks {
    static final Logger LOG = LoggerFactory.getLogger("justenoughserverlessrecipes");
    private static volatile IJeiRuntime current;

    public static void available(IJeiRuntime runtime) {
        current = runtime;
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        minecraft.execute(() -> {
            if (current != runtime || connection == null || minecraft.getConnection() != connection
                    || minecraft.getSingleplayerServer() != null) return;
            var config = JesrConfig.load();
            boolean trades = config.tradesFallback() && present("de.larsensmods.jeitrades.jei.JEITradesPlugin");
            boolean breeding = config.breedingFallback() && present("com.christofmeg.justenoughbreeding.jei.JEIPlugin");
            boolean resources = config.resourcesFallback() && present("jeresources.jei.JEIConfig");
            if (!trades && !breeding && !resources) return;
            try (var data = LocalDataLoader.load(connection.registryAccess(), ClientDataPacks.open())) {
                if (current != runtime || minecraft.getConnection() != connection) return;
                if (trades) attempt("JEI Trades", () -> TradesFallback.apply(runtime, data));
                if (breeding) attempt("Just Enough Breeding", () -> BreedingFallback.apply(runtime, data));
                if (resources) attempt("Just Enough Resources", () -> ResourcesFallback.apply(runtime, data.registries()));
            } catch (RuntimeException | LinkageError e) {
                LOG.warn("Unable to load local addon fallback data", e);
            }
        });
    }

    public static void unavailable() { current = null; }

    private static boolean present(String name) {
        try {
            Class.forName(name, false, AddonFallbacks.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static void attempt(String name, Action action) {
        try { action.run(); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.warn("Unable to apply {} fallback; existing addon data preserved", name, e);
        }
    }

    @FunctionalInterface private interface Action { void run() throws ReflectiveOperationException; }
}
