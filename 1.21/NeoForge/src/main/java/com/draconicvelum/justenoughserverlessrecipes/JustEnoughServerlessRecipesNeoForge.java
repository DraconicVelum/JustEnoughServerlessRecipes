package com.draconicvelum.justenoughserverlessrecipes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod("justenoughserverlessrecipes")
public class JustEnoughServerlessRecipesNeoForge {
    public JustEnoughServerlessRecipesNeoForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            JustEnoughServerlessRecipesNeoForgeClient.init();
        }
    }
}
