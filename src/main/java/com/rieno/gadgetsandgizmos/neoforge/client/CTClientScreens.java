package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterMenu;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkMenu;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardContent;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import java.util.UUID;

// Register the client screens
public final class CTClientScreens {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT client screens
    private CTClientScreens() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Open the thruster bearing range screen
    public static void openThrusterBearingRangeScreen(ThrusterBearingBlockEntity blockEntity) {
        ScreenOpener.open(new ThrusterBearingRangeScreen(blockEntity));
    }

    // Open the thruster config screen
    public static void openThrusterConfigScreen(ThrusterBlockEntity blockEntity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ThrusterMenu menu = new ThrusterMenu(0, minecraft.player.getInventory(), blockEntity);
        ScreenOpener.open(new ThrusterConfigScreen(
                menu,
                minecraft.player.getInventory(),
                Component.translatable("createthrusters.thruster.config_screen.title")));
    }

    // Open the gyroscope link config screen
    public static void openGyroscopeLinkConfigScreen(GyroscopeLinkBlockEntity blockEntity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        GyroscopeLinkMenu menu = new GyroscopeLinkMenu(0, minecraft.player.getInventory(), blockEntity);
        ScreenOpener.open(new GyroscopeLinkConfigScreen(
                menu,
                minecraft.player.getInventory(),
                Component.translatable("createthrusters.gyroscope_link.config.title")));
    }

    // Open the double button mode screen
    public static void openDoubleButtonModeScreen(DoubleButtonBlockEntity blockEntity) {
        ScreenOpener.open(new DoubleButtonModeScreen(blockEntity));
    }

    // Open the shipping manifest
    public static void openShippingManifest(ClipboardContent content) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        DataComponentMap components = DataComponentMap.builder()
                .set(AllDataComponents.CLIPBOARD_CONTENT, content)
                .build();
        ScreenOpener.open(new ShippingManifestClipboardScreen(minecraft.player.getInventory().selected, components));
    }

    // Open the ship dock
    public static void openShipDock(
            BlockPos pos,
            UUID subLevelId,
            String name,
            boolean refuel,
            boolean restock,
            boolean packages,
            boolean doorControlEnabled,
            int doorControlMask,
            java.util.List<ShipDockBlockEntity.ConnectorReference> availableConnectors,
            java.util.List<ShipDockBlockEntity.ConnectorReference> refuelConnectors,
            java.util.List<ShipDockBlockEntity.ConnectorReference> restockConnectors,
            java.util.List<ShipDockBlockEntity.ConnectorReference> packageConnectors
    ) {
        ScreenOpener.open(new ShipDockScreen(
                pos, subLevelId, name, refuel, restock, packages, doorControlEnabled,
                doorControlMask, availableConnectors, refuelConnectors, restockConnectors,
                packageConnectors));
    }

    // Register the menu screens
    public static void registerMenuScreens(RegisterMenuScreensEvent evt) {
        if (CTMenuTypes.ANALOGUE_JOYSTICK != null) evt.register(CTMenuTypes.ANALOGUE_JOYSTICK.get(), AnalogueJoystickConfigScreen::new);
        if (CTMenuTypes.ANALOGUE_CONTRAPTION_CONTROLLER != null) evt.register(CTMenuTypes.ANALOGUE_CONTRAPTION_CONTROLLER.get(), AnalogueContraptionControllerConfigScreen::new);
        if (CTMenuTypes.ADVANCED_CONTRAPTION_CONTROLLER != null) evt.register(CTMenuTypes.ADVANCED_CONTRAPTION_CONTROLLER.get(), AdvancedContraptionControllerScreen::new);
        if (CTMenuTypes.PORTABLE_CONTRAPTION_CONTROLLER != null) evt.register(CTMenuTypes.PORTABLE_CONTRAPTION_CONTROLLER.get(), AnalogueContraptionControllerConfigScreen::new);
        if (CTMenuTypes.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER != null) evt.register(CTMenuTypes.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER.get(), AdvancedContraptionControllerScreen::new);
        if (CTMenuTypes.THRUSTER != null) evt.register(CTMenuTypes.THRUSTER.get(), ThrusterConfigScreen::new);
        if (CTMenuTypes.RCS_THRUSTER != null) evt.register(CTMenuTypes.RCS_THRUSTER.get(), RcsThrusterConfigScreen::new);
        if (CTMenuTypes.CLAW != null) evt.register(CTMenuTypes.CLAW.get(), ClawConfigScreen::new);
        if (CTMenuTypes.GYROSCOPE_LINK != null) evt.register(CTMenuTypes.GYROSCOPE_LINK.get(), GyroscopeLinkConfigScreen::new);
        if (CTMenuTypes.BI_DIRECTIONAL_GEARSHIFT != null) evt.register(CTMenuTypes.BI_DIRECTIONAL_GEARSHIFT.get(), BiDirectionalGearshiftScreen::new);
        if (CTMenuTypes.VECTOR_BEARING != null) evt.register(CTMenuTypes.VECTOR_BEARING.get(), VectorBearingScreen::new);
        if (CTMenuTypes.AILERON_BEARING != null) evt.register(CTMenuTypes.AILERON_BEARING.get(), AileronBearingScreen::new);
        if (CTMenuTypes.POWERED_ZIPLINE != null) evt.register(CTMenuTypes.POWERED_ZIPLINE.get(), PoweredZiplineScreen::new);
        evt.register(CTMenuTypes.NAVIGATION_TABLE.get(), NavigationTableScreen::new);
    }
}
