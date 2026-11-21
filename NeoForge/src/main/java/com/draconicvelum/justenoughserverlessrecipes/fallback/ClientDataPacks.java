package com.draconicvelum.justenoughserverlessrecipes.fallback;

import net.neoforged.fml.ModList;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.resource.JarContentsPackResources;
import java.util.List;

final class ClientDataPacks {
    static List<PackResources> open() {
        var packs = LocalDataLoader.openPacks(List.of());
        for (var mod : ModList.get().getModFiles()) {
            var info = new PackLocationInfo("jesr-local-" + packs.size(), Component.literal("Local mod data"),
                    PackSource.BUILT_IN, java.util.Optional.empty());
            packs.add(new JarContentsPackResources(info, mod.getFile().getContents(), ""));
        }
        return packs;
    }
}
