package com.draconicvelum.justenoughserverlessrecipes;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.List;
import org.slf4j.LoggerFactory;

final class JesrConfigScreen extends Screen {
    private static final List<String> OPTIONS = List.of("transfer", "trades", "breeding", "resources");
    private final Screen parent;
    private final boolean[] values;
    private boolean saveFailed;

    JesrConfigScreen(Screen parent) {
        super(Component.translatable("jesr.config.title"));
        this.parent = parent;
        var settings = JesrConfig.load();
        this.values = new boolean[]{settings.tradeTransfer(), settings.tradesFallback(), settings.breedingFallback(), settings.resourcesFallback()};
    }

    @Override
    protected void init() {
        int controlWidth = Math.min(330, width - 30);
        int left = (width - controlWidth) / 2;
        int top = Math.max(36, (height - 170) / 2);
        addRenderableOnly(new StringWidget(left, top - 24, controlWidth, 20, title, font));
        for (int i = 0; i < OPTIONS.size(); i++) {
            int index = i;
            String key = "jesr.config." + OPTIONS.get(i);
            addRenderableWidget(CycleButton.onOffBuilder(values[i])
                    .withTooltip(value -> Tooltip.create(Component.translatable(key + ".tooltip")))
                    .create(left, top + i * 25, controlWidth, 20, Component.translatable(key),
                            (button, value) -> values[index] = value));
        }
        Component note = Component.translatable(saveFailed ? "jesr.config.save_failed" : "jesr.config.restart");
        if (saveFailed) note = note.copy().withStyle(ChatFormatting.RED);
        addRenderableOnly(new MultiLineTextWidget(left, top + 105, note, font).setMaxWidth(controlWidth));
        int buttonWidth = (controlWidth - 10) / 3;
        int bottom = Math.max(top + 140, height - 30);
        addRenderableWidget(Button.builder(Component.translatable("jesr.config.defaults"), button -> {
            java.util.Arrays.fill(values, true);
            rebuildWidgets();
        }).bounds(left, bottom, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(left + buttonWidth + 5, bottom, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(left + (buttonWidth + 5) * 2, bottom, buttonWidth, 20).build());
    }

    private void save() {
        try {
            JesrConfig.save(new JesrConfig.Settings(values[0], values[1], values[2], values[3]));
            onClose();
        } catch (IOException | IllegalArgumentException e) {
            LoggerFactory.getLogger("justenoughserverlessrecipes").warn("Unable to save config", e);
            saveFailed = true;
            rebuildWidgets();
        }
    }

    @Override public void onClose() { minecraft.gui.setScreen(parent); }
}
