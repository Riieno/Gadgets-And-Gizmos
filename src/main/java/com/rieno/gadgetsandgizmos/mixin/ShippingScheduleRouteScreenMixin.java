package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShippingScheduleItem;
import com.rieno.gadgetsandgizmos.content.ShippingScheduleRouteData;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingRouteVisibilityPayload;
import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// Add an independent SCM route visibility toggle to the Shipping Schedule screen
@Mixin(value = ScheduleScreen.class, remap = false)
public abstract class ShippingScheduleRouteScreenMixin
        extends AbstractSimiContainerScreen<ScheduleMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Route visibility toggle
    @Unique
    private IconButton createthrusters$routeButton;
    // Whether this screen instance has synchronized its retained item preference
    @Unique
    private boolean createthrusters$routeVisibilitySynchronized;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the mixed-in Schedule screen superclass
    protected ShippingScheduleRouteScreenMixin(
            ScheduleMenu menu, Inventory inventory, Component title
    ) {
        super(menu, inventory, title);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the route toggle after Create rebuilds its schedule widgets
    @Inject(method = "init", at = @At("TAIL"))
    private void createthrusters$addRouteToggle(CallbackInfo callback) {
        if (!(menu.contentHolder.getItem() instanceof ShippingScheduleItem)) return;
        boolean visible = ShippingScheduleRouteData.visible(menu.contentHolder);
        createthrusters$routeButton = new IconButton(
                leftPos + 87, topPos + 196, AllIcons.I_VIEW_SCHEDULE);
        createthrusters$routeButton.withCallback(() -> {
            boolean selected = !ShippingScheduleRouteData.visible(menu.contentHolder);
            ShippingScheduleRouteData.setVisible(menu.contentHolder, selected);
            createthrusters$configureRouteButton(selected);
            PacketDistributor.sendToServer(new ShippingRouteVisibilityPayload(
                    InteractionHand.MAIN_HAND, selected));
        });
        createthrusters$routeButton.active = ShippingScheduleRouteData.owner(menu.contentHolder) != null;
        createthrusters$configureRouteButton(visible);
        addRenderableWidget(createthrusters$routeButton);
        if (!createthrusters$routeVisibilitySynchronized) {
            PacketDistributor.sendToServer(new ShippingRouteVisibilityPayload(
                    InteractionHand.MAIN_HAND, visible));
            createthrusters$routeVisibilitySynchronized = true;
        }
    }

    // Hide the route toggle while editing a schedule card
    @Inject(method = "startEditing", at = @At("TAIL"))
    private void createthrusters$hideRouteToggle(
            IScheduleInput input, Consumer<Boolean> onClose,
            boolean allowDeletion, CallbackInfo callback
    ) {
        if (createthrusters$routeButton != null) createthrusters$routeButton.visible = false;
    }

    // Restore the route toggle after editing a schedule card
    @Inject(method = "stopEditing", at = @At("TAIL"))
    private void createthrusters$showRouteToggle(CallbackInfo callback) {
        if (createthrusters$routeButton != null) createthrusters$routeButton.visible = true;
    }

    // Configure the route toggle state and concise interaction guidance
    @Unique
    private void createthrusters$configureRouteButton(boolean visible) {
        if (createthrusters$routeButton == null) return;
        createthrusters$routeButton.green = visible;
        createthrusters$routeButton.getToolTip().clear();
        createthrusters$routeButton.getToolTip().add(Component.translatable(
                "createthrusters.shipping_schedule.show_route"));
        createthrusters$routeButton.getToolTip().add(Component.translatable(visible
                        ? "createthrusters.shipping_schedule.show_route.enabled"
                        : "createthrusters.shipping_schedule.show_route.disabled")
                .withStyle(visible ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        createthrusters$routeButton.getToolTip().add(Component.translatable(
                "createthrusters.shipping_schedule.show_route.edit_hint")
                .withStyle(ChatFormatting.GRAY));
    }
}
