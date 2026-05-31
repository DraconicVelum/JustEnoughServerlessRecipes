package com.draconicvelum.justenoughserverlessrecipes;

import com.draconicvelum.justenoughserverlessrecipes.recipes.DatapackRecipeMapBuilder;
import mezz.jei.fabric.events.JeiLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class JustEnoughServerlessRecipesMod implements ClientModInitializer {

    public static boolean isSingleplayer = false;
    private static boolean handlingRecipeSync = false;

    @Override
    public void onInitializeClient() {
        JeiLifecycleEvents.AFTER_RECIPE_SYNC.register(() -> {
            if (handlingRecipeSync) {
                return;
            }
            handlingRecipeSync = true;
            try {
                var level = Minecraft.getInstance().level;
                if (level != null) {
                    DatapackRecipeMapBuilder.setClientRegistryAccess(level.registryAccess());
                }
                JustEnoughServerlessRecipesPlugin.tryInjectDatapackRecipes("JeiLifecycleEvents.AFTER_RECIPE_SYNC");
            } finally {
                handlingRecipeSync = false;
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            isSingleplayer = client.isSingleplayer();

            JustEnoughServerlessRecipesLog.LOGGER.info("Mode: {}", isSingleplayer ? "Singleplayer" : "Multiplayer");

            client.execute(() -> {
                if (client.level != null) {
                    DatapackRecipeMapBuilder.setClientRegistryAccess(client.level.registryAccess());
                }
                boolean injected = JustEnoughServerlessRecipesPlugin.tryInjectDatapackRecipes("ClientPlayConnectionEvents.JOIN");
                if (injected) {
                    JeiLifecycleEvents.AFTER_RECIPE_SYNC.invoker().run();
                }
            });
        });
    }
}
