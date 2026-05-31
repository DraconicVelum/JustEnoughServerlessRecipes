package com.draconicvelum.justenoughserverlessrecipes;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class JustEnoughServerlessRecipesNeoForgeClient {
    private JustEnoughServerlessRecipesNeoForgeClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, JustEnoughServerlessRecipesNeoForgeClient::onLoggingIn);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        boolean singleplayer = Minecraft.getInstance().hasSingleplayerServer();

        JustEnoughServerlessRecipesLog.LOGGER.info("Mode: {}", singleplayer ? "Singleplayer" : "Multiplayer");
    }
}
