package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayDropdownSelectPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayDropdownOpenPayload;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudControlRenderer;
import com.rieno.gadgetsandgizmos.lib.client.ui.HudDropdownScreen;
import com.rieno.gadgetsandgizmos.lib.display.HudStyleCatalog;
import com.rieno.gadgetsandgizmos.lib.display.DropdownOptions;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayTextInputOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayTextInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import net.neoforged.neoforge.network.PacketDistributor;

// Register the ACC display screens
public final class AccDisplayClientScreens {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display client screens
    private AccDisplayClientScreens() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Open the text input
    public static void openTextInput(AccDisplayTextInputOpenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        String prompt = payload.prompt().isBlank() ? "Enter text" : payload.prompt();
        minecraft.setScreen(new ControllerTextInputScreen(
                minecraft.screen,
                Component.translatable("createthrusters.text.acc_display_text_input"),
                Component.literal(prompt),
                payload.value(),
                val -> PacketDistributor.sendToServer(new AccDisplayTextInputPayload(
                        payload.target(), payload.nodeId(), payload.interactionId(), val))));
    }

    public static void openDropdown(AccDisplayDropdownOpenPayload payload){
        var mc = Minecraft.getInstance();
        var elm = payload.element();
        java.util.List<DropdownOptions.Option> options = new java.util.ArrayList<>();
        for(var raw : elm.getList("DropdownEntries", net.minecraft.nbt.Tag.TAG_COMPOUND)){
            var entry = (net.minecraft.nbt.CompoundTag)raw;
            options.add(new DropdownOptions.Option(entry.getString("Key"), entry.getString("Label"), null));
        }
        java.util.function.Supplier<java.util.List<DropdownOptions.Option>> liveOptions = () -> {
            var display = mc.level == null ? null : SimulatedHelper.findBlockEntity(mc.level, payload.target().subLevelId(),
                    payload.target().pos(), AccDisplayBlockEntity.class);
            if(display == null) return options;
            String port = elm.getCompound("PropertyBindings").getString(DropdownOptions.PROPERTY);
            var source = display.value(payload.nodeId(), port);
            return DropdownOptions.accepts(source.type()) ? DropdownOptions.entries(
                    com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime.toLibraryValue(source)) : options;
        };
        int w = Math.clamp(elm.getInt("W"), 100, Math.max(100, mc.getWindow().getGuiScaledWidth() - 8));
        int h = Math.max(20, elm.getInt("H"));
        mc.setScreen(new HudDropdownScreen(mc.screen, liveOptions,
                elm.contains("SelectedKey") ? elm.getString("SelectedKey") : null, elm.getString("Text"), AdvancedHudWidgetRenderer.style(elm),
                HudStyleCatalog.dropdownStyle(elm.getString("Style")),
                new HudControlRenderer.Rect((mc.getWindow().getGuiScaledWidth() - w) / 2,
                        mc.getWindow().getGuiScaledHeight() / 2, w, h),
                key -> PacketDistributor.sendToServer(new AccDisplayDropdownSelectPayload(
                        payload.target(), payload.nodeId(), payload.interactionId(), key))));
    }

    // Handle projected display interaction
    public static boolean interactProjection(AccDisplayBlockEntity display, BlockHitResult hit,
                                             int mouseButton) {
        return AccDisplayGuiProjection.interact(display, hit, mouseButton);
    }

    // Open the mode selection
    public static void openModeSelection(AccDisplayBlockEntity display) {
        if (display == null) {
            return;
        }
        AccDisplayBlockEntity root = display.networkRoot();
        if (root != null) {
            display = root;
        }
        Minecraft.getInstance().setScreen(new AccDisplayModeScreen(
                MenuConfigTarget.of(display.getBlockPos(),
                        SimulatedHelper.getContainingSubLevelId(display)),
                display.displayMode(), display.displayFrame(), display.linkedTextScale()));
    }
}
