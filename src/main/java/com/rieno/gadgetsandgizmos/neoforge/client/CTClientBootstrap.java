package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.render.AreaHighlightRenderTypes;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.ZiplineRidingController;
import com.rieno.gadgetsandgizmos.neoforge.client.tablet.apps.AppStore;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.ponder.CTPonderPlugin;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import com.simibubi.create.foundation.block.render.ReducedDestroyEffects;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

// Register the client screens, renderers, input handlers and overlays
public final class CTClientBootstrap {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger CT_LOGGER = LogUtils.getLogger();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT client bootstrap
    private CTClientBootstrap() {
    }

    // Register the CT client bootstrap
    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        AdvancedControllerGraphSnapshotPayload.installClientHandler(
                AnalogueContraptionControllerClientHandler::applyAdvancedGraphSnapshot);
        ZiplineRidingController.install(new ZiplineRidingControllerImpl());
        CTClientConfigScreen.register(modContainer);
        CTPartialModels.init();
        modEventBus.addListener((FMLClientSetupEvent evt) -> {
            evt.enqueueWork(() -> {
                TabletAppClientRegistry.registerIfAbsent(DiagnosticTabletData.appId("app_store"), new AppStore());
                AccDisplayConnectedTextures.register();
                registerAccDisplayRenderLayers();
                CTClientRenderers.registerVisualizers();
            });
        });
        modEventBus.addListener((FMLLoadCompleteEvent evt) -> evt.enqueueWork(CTClientBootstrap::ct$runLateClientBootstrap));
        modEventBus.addListener(CTPhysicsGogglesClient::registerKeyMappings);
        modEventBus.addListener(ContraptionNetworkLinkerClient::registerKeyMappings);
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers evt) -> CTClientRenderers.registerRenderers(evt));
        modEventBus.addListener(PortableContraptionControllerPlayerLayer::register);
        modEventBus.addListener((ModelEvent.ModifyBakingResult evt) -> CTClientRenderers.modifyBakingResult(evt));
        modEventBus.addListener(CTClientBootstrap::registerBlockColors);
        modEventBus.addListener((RegisterMenuScreensEvent evt) -> CTClientScreens.registerMenuScreens(evt));
        modEventBus.addListener((RegisterParticleProvidersEvent evt) -> CTClientParticles.registerParticleProviders(evt));
        modEventBus.addListener(CTClientBootstrap::registerClientExtensions);

        modEventBus.addListener((RegisterShadersEvent evt) -> {
            try {
                AreaHighlightRenderTypes.onRegisterShaders(evt);
            } catch (java.io.IOException err) {
                throw new RuntimeException("Failed to register area highlight shader", err);
            }
        });
    }

    // Register the block colors
    private static void registerBlockColors(RegisterColorHandlersEvent.Block evt) {
        evt.register(CopycatBlock.wrappedColor(), CTBlocks.COPYCAT_DOUBLE_BUTTON.get());
    }

    // Register the ACC display render layers
    private static void registerAccDisplayRenderLayers() {
        if (CTBlocks.ACC_DISPLAY == null) {
            return;
        }
        ItemBlockRenderTypes.setRenderLayer(CTBlocks.ACC_DISPLAY.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(CTBlocks.ACC_DISPLAY_BLOCK.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(CTBlocks.ACC_DISPLAY_PANEL.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(CTBlocks.ACC_DISPLAY_HALF_PANEL.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(CTBlocks.ACC_DISPLAY_SLAB.get(), RenderType.cutoutMipped());
    }

    // Register the client extensions
    private static void registerClientExtensions(RegisterClientExtensionsEvent evt) {
        evt.registerBlock(
                new ReducedDestroyEffects(),
                CTBlocks.ANALOGUE_CONTRAPTION_CONTROLLER.get(),
                CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        evt.registerItem(
                SimpleCustomRenderer.create(
                        CTItems.PORTABLE_CONTRAPTION_CONTROLLER.get(),
                        new PortableContraptionControllerItemRenderer()),
                CTItems.PORTABLE_CONTRAPTION_CONTROLLER.get());
        evt.registerItem(
                SimpleCustomRenderer.create(
                        CTItems.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER.get(),
                        new PortableContraptionControllerItemRenderer()),
                CTItems.ADVANCED_PORTABLE_CONTRAPTION_CONTROLLER.get());
        evt.registerItem(
                SimpleCustomRenderer.create(
                        CTItems.DIAGNOSTIC_TABLET.get(), new DiagnosticTabletItemRenderer()),
                CTItems.DIAGNOSTIC_TABLET.get());
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the late client bootstrap
    private static void ct$runLateClientBootstrap() {

        ct$ensurePonderPluginRegistered();
        FtbLibraryScreenCompat.registerAdvancedControllerScreenBlacklist();

        NeoForge.EVENT_BUS.addListener(CTClientCommands::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(CTPhysicsStaffOverlayRenderer::onRenderWorld);
        NeoForge.EVENT_BUS.addListener(PortableContraptionControllerItemRenderer::onRenderHand);
        NeoForge.EVENT_BUS.addListener(SupporterHeadClientEvents::onItemTooltip);

        NeoForge.EVENT_BUS.addListener(CTGantryAnchorDebugRenderer::onRenderWorld);
        NeoForge.EVENT_BUS.addListener(ScmPathDebugRenderer::onRenderWorld);
        NeoForge.EVENT_BUS.addListener(ShippingManifestRenderer::onRenderWorld);
        NeoForge.EVENT_BUS.addListener(PoweredZiplinePlacementHandler::onRenderWorld);
        NeoForge.EVENT_BUS.addListener(ContraptionNetworkLinkerFaceRenderer::onRenderWorld);
        NeoForge.EVENT_BUS.addListener(PoweredZiplinePlacementHandler::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(ContraptionNetworkLinkerClient::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletClientInteraction::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletClientInteraction::onKeyInput);
        NeoForge.EVENT_BUS.addListener(EntityLauncherClientInputHandler::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(ShippingManifestClientHandler::onMouseScrolling);
        NeoForge.EVENT_BUS.addListener(AccDisplayGuiProjection::onMouseScrolling);
        NeoForge.EVENT_BUS.addListener(AccDisplayGuiProjection::onClientTick);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletGuiProjection::onClientTick);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletGuiProjection::onRenderFrame);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletLandingZoneClient::onRenderWorld);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletLandingZoneClient::onMouseScrolling);
        NeoForge.EVENT_BUS.addListener(CTPhysicsGogglesClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(AnalogueJoystickHudOverlay::onRenderGui);
        NeoForge.EVENT_BUS.addListener(CTClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(CTClientEvents::onScreenRender);
        NeoForge.EVENT_BUS.addListener(CTClientEvents::onScreenMousePressed);
        NeoForge.EVENT_BUS.addListener(CTClientEvents::onClientLogout);
    }

    // Ensure the ponder plugin registered
    private static void ct$ensurePonderPluginRegistered() {
        try {
            if (CTItems.THRUSTER == null) {
                return;
            }
            long pluginCount = PonderIndex.streamPlugins()
                    .filter(plugin -> "createthrusters".equals(plugin.getModId()))
                    .count();
            boolean hasThrusterScenes = PonderIndex.getSceneAccess().doScenesExistForId(CTItems.THRUSTER.getId());

            if (!hasThrusterScenes) {
                if (pluginCount == 0) {
                    PonderIndex.addPlugin(new CTPonderPlugin());
                    CT_LOGGER.warn("[CT][Ponder] Injected fallback CTPonderPlugin because no CT scenes were discoverable");
                }
                PonderIndex.reload();
                pluginCount = PonderIndex.streamPlugins()
                    .filter(plugin -> "createthrusters".equals(plugin.getModId()))
                    .count();
                hasThrusterScenes = PonderIndex.getSceneAccess().doScenesExistForId(CTItems.THRUSTER.getId());
            }

            CT_LOGGER.info("[CT][Ponder] Registered plugins for mod id createthrusters={}, thruster scenes discoverable={}", pluginCount,
                    hasThrusterScenes);
        } catch (Throwable throwable) {
            CT_LOGGER.error("[CT][Ponder] Failed to ensure ponder registration state", throwable);
        }
    }
}
