package com.draconicvelum.justenoughserverlessrecipes;

import mezz.jei.fabric.events.JeiLifecycleEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.Event;

import java.lang.reflect.Field;

public class JustEnoughServerlessRecipesMod implements ClientModInitializer {

    public static boolean isSingleplayer = false;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            isSingleplayer = client.hasSingleplayerServer();

            JustEnoughServerlessRecipesLog.LOGGER.info("Mode: {}", isSingleplayer ? "Singleplayer" : "Multiplayer");

            client.execute(() -> {
                boolean injected = JustEnoughServerlessRecipesPlugin.tryInjectDatapackRecipes("ClientPlayConnectionEvents.JOIN");
                if (injected) {
                    notifyJeiRecipesUpdated();
                }
            });
        });
    }

    private static void notifyJeiRecipesUpdated() {
        for (String eventName : new String[]{"AFTER_RECIPES_UPDATED", "AFTER_RECIPE_SYNC"}) {
            try {
                Field eventField = JeiLifecycleEvents.class.getField(eventName);
                Object event = eventField.get(null);
                ((Runnable) ((Event<?>) event).invoker()).run();
                return;
            } catch (NoSuchFieldException ignored) {
                // Try the next JEI 30.x Fabric event name.
            } catch (IllegalAccessException | ClassCastException e) {
                JustEnoughServerlessRecipesLog.LOGGER.warn("Could not notify JEI recipes were updated.", e);
                return;
            }
        }

        JustEnoughServerlessRecipesLog.LOGGER.warn("Could not find a JEI recipe update lifecycle event.");
    }
}
