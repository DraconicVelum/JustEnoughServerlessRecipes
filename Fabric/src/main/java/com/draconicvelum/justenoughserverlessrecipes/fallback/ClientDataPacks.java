package com.draconicvelum.justenoughserverlessrecipes.fallback;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackResources;
import java.util.List;

final class ClientDataPacks {
    static List<PackResources> open() {
        return LocalDataLoader.openPacks(FabricLoader.getInstance().getAllMods().stream()
                .flatMap(mod -> mod.getRootPaths().stream()).distinct().toList());
    }
}
