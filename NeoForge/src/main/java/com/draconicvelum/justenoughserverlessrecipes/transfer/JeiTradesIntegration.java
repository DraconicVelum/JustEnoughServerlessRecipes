package com.draconicvelum.justenoughserverlessrecipes.transfer;

import mezz.jei.api.registration.IRecipeTransferRegistration;

/** Uses JEI ingredients independently of the addon providing trade recipes. */
public final class JeiTradesIntegration {
    private JeiTradesIntegration() {}

    public static void register(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new MerchantRecipeTransferHandler(registration.getTransferHelper()));
    }
}
