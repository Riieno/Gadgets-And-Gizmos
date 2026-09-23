package com.rieno.gadgetsandgizmos;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.compat.scm.OptionalScmCompatibility;
import com.rieno.gadgetsandgizmos.content.EntityLauncherItem;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletRedstoneLinkRuntime;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletDatabase;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletFriendDatabase;
import com.rieno.gadgetsandgizmos.content.PhotomancyBlueprintCompat;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinCrafting;
import com.rieno.gadgetsandgizmos.content.SupporterHeadWanderingTraderTrades;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerRuntime;
import com.rieno.gadgetsandgizmos.content.ShipControlMapStore;
import com.rieno.gadgetsandgizmos.neoforge.CTCommonEvents;
import com.rieno.gadgetsandgizmos.neoforge.ControllerGraphWebServer;
import com.rieno.gadgetsandgizmos.neoforge.CTMobHeadDrops;
import com.rieno.gadgetsandgizmos.neoforge.CTSableTrackingCommands;
import com.rieno.gadgetsandgizmos.neoforge.CTServerFeatureCleanup;
import com.rieno.gadgetsandgizmos.neoforge.PathfinderDebugRenderService;
import com.rieno.gadgetsandgizmos.neoforge.ScmDebugDumpService;
import com.rieno.gadgetsandgizmos.neoforge.ShippingRouteOverlayService;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.registry.CTMountedSeats;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

// Load the addon on NeoForge and attach its event listeners
@Mod(CreateThrusters.MOD_ID)
public final class CreateThrustersNeoForge {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the create thrusters neo forge
    public CreateThrustersNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        CTConfigs.register(modContainer);
        CTFeatureToggles.prepareDedicatedServerRegistration();
        CTMountedSeats.register();
        OptionalScmCompatibility.register();

        NeoForge.EVENT_BUS.addListener(CTCommonEvents::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(CTSableTrackingCommands::registerCommands);
        NeoForge.EVENT_BUS.addListener(CTServerFeatureCleanup::onServerTick);
        NeoForge.EVENT_BUS.addListener(PathfinderDebugRenderService::onServerTick);
        NeoForge.EVENT_BUS.addListener(ScmDebugDumpService::onServerTick);
        NeoForge.EVENT_BUS.addListener(ShippingRouteOverlayService::onServerTick);
        NeoForge.EVENT_BUS.addListener(PortableContraptionControllerRuntime::postServerTick);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletRedstoneLinkRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(PortableContraptionControllerRuntime::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(PortableContraptionControllerRuntime::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(PortableContraptionControllerRuntime::onServerStopped);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletRedstoneLinkRuntime::onServerStopped);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletDatabase::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletDatabase::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ShipControlMapStore::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ShipControlMapStore::onServerStopped);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletFriendDatabase::onServerStarted);
        NeoForge.EVENT_BUS.addListener(DiagnosticTabletFriendDatabase::onServerStopped);
        NeoForge.EVENT_BUS.addListener(CTServerFeatureCleanup::onServerStopped);
        NeoForge.EVENT_BUS.addListener(PathfinderDebugRenderService::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ScmDebugDumpService::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ShippingRouteOverlayService::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ControllerGraphWebServer::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ControllerGraphWebServer::onServerStopped);
        NeoForge.EVENT_BUS.addListener(PlayerMannequinCrafting::onAnvilUpdate);
        NeoForge.EVENT_BUS.addListener(SupporterHeadWanderingTraderTrades::addTrades);
        NeoForge.EVENT_BUS.addListener(CTMobHeadDrops::onLivingDrops);
        if (CTItems.ENTITY_LAUNCHER != null) {
            NeoForge.EVENT_BUS.addListener(EntityLauncherItem::postServerTick);
        }
        CreateThrusters.init(modEventBus);
        modEventBus.addListener(PhotomancyBlueprintCompat::onCommonSetup);
        modEventBus.addListener(CTCommonEvents::registerCapabilities);
        modEventBus.addListener(CTCommonEvents::registerEntityAttributes);
        modEventBus.addListener(CTCommonEvents::registerPayloadHandlers);
        modEventBus.addListener(CTConfigs::onLoad);
        modEventBus.addListener(CTConfigs::onReload);
    }
}
