package com.draconicvelum.justenoughserverlessrecipes;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class JustEnoughServerlessRecipesNeoForgeClient {
    private JustEnoughServerlessRecipesNeoForgeClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, JustEnoughServerlessRecipesNeoForgeClient::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RecipesReceivedEvent.class, JustEnoughServerlessRecipesNeoForgeClient::onRecipesReceived);
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        boolean singleplayer = Minecraft.getInstance().hasSingleplayerServer();

        JustEnoughServerlessRecipesLog.LOGGER.info("Mode: {}", singleplayer ? "Singleplayer" : "Multiplayer");
    }

    private static void onRecipesReceived(RecipesReceivedEvent event) {
        if (!event.getRecipeMap().values().isEmpty()) {
            return;
        }

        JustEnoughServerlessRecipesPlugin.tryInjectDatapackRecipes("RecipesReceivedEvent");
    }
}
