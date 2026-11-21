package com.draconicvelum.justenoughserverlessrecipes;

import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = "justenoughserverlessrecipes", dist = Dist.CLIENT)
public class JustEnoughServerlessRecipesNeoForge {
    public JustEnoughServerlessRecipesNeoForge(ModContainer container) {
        JesrConfig.load();
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new JesrConfigScreen(parent));
    }
}
