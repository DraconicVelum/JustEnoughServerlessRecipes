package com.draconicvelum.justenoughserverlessrecipes;

import net.fabricmc.api.ClientModInitializer;

public final class JustEnoughServerlessRecipesClient implements ClientModInitializer {
    @Override public void onInitializeClient() { JesrConfig.load(); }
}
