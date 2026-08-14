package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.AdvancedNavigationTableBlockEntity;
import com.rieno.gadgetsandgizmos.content.AlternatorBlockEntity;
import com.rieno.gadgetsandgizmos.content.AndesiteCableBlockEntity;
import com.rieno.gadgetsandgizmos.content.IndustrialMotorBlockEntity;
import com.rieno.gadgetsandgizmos.content.FuelOxidizerBlockEntity;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerCustomKeyPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerDiscoveryNodePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerDiscoveryRequestPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerDiscoveryResultsPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerGhostSlotsPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerKeyPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedContraptionControllerGraphPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphActionResultPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphHistoryPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerPublicSharePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerSharedGraphsPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerMouseInputPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerPhysicalInteractionPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerProfilerPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudImageCatalogPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudImageDataPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudImageRequestPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudImageUploadPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudInteractionPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayTextInputOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayTextInputPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayModePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AccDisplayComputerInputPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AileronBearingConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueJoystickConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueJoystickDragPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueJoystickGhostSlotsPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ArmorStandPoseOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ArmorStandPosePreferencePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ArmorStandPoseSyncPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ClawGhostSlotsPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSyncPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ControllerRuntimeSyncPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.BiDirectionalGearshiftConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DoubleButtonAppearancePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DoubleButtonConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DoubleButtonHoldPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletActionPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletRemoteOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletReopenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.FeatureToggleSyncPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.GizmosLinkHighlightPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.GraphV2ThemeSyncPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.GraphSoundPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.HardwareControllerInputPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.GyroscopeLinkConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.LecternPortableContraptionControllerKeyPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.LecternPortableContraptionControllerModePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.NavigationTableActionPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.FunctionPlotterDataPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PhysicsGogglesDataPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PhysicsGogglesDataRequestPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PortableContraptionControllerKeyPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PortableContraptionControllerModePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.PortableContraptionControllerOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundRopeKnotAttachPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherInputPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherAnchorControlPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundEntityLauncherPowerModePacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineAttachPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineConfigPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineFollowChainPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineInputPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ServerboundZiplineMountPacket;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingManifestOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingManifestRefreshPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ShippingAutoRefuelPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ShipDockConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ShipDockOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterConfigPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterFuelTankPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterSlotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ThrusterBearingRangePayload;
import com.rieno.gadgetsandgizmos.neoforge.network.VectorBearingConfigPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import com.rieno.gadgetsandgizmos.util.MobHauntingConversions;
import com.rieno.gadgetsandgizmos.util.ThrusterFuelData;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.lang.reflect.Method;

// Connect server lifecycle and gameplay events to the addon's stateful systems
public final class CTCommonEvents {
        /*--------------------------------------------------------##---------------------------------------------------------

        =======================================================================================================================
                                                               Constants
        =======================================================================================================================

        ------------------------------------------------------------##-----------------------------------------------------*/

        private static final Logger LOGGER = LogUtils.getLogger();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT common events
    private CTCommonEvents() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the reload listeners
    public static void addReloadListeners(AddReloadListenerEvent evt) {
        evt.addListener((PreparableReloadListener) ThrusterFuelData.RELOAD_LISTENER);
        evt.addListener((PreparableReloadListener) MobHauntingConversions.RELOAD_LISTENER);
        evt.addListener((PreparableReloadListener) GraphV2ThemeData.RELOAD_LISTENER);
    }

    // Register the entity attributes
    public static void registerEntityAttributes(EntityAttributeCreationEvent evt) {
        if (CTEntityTypes.PLAYER_MANNEQUIN != null) {
            evt.put(CTEntityTypes.PLAYER_MANNEQUIN.get(), PlayerMannequinEntity.createAttributes().build());
        }
    }

        // Register the payload handlers
        public static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
                PayloadRegistrar registrar = event.registrar("2");
                // ------------------------------------THRUSTERS / ANALOGUE CONTROLS------------------------------------
                registrar.playToServer(ThrusterBearingRangePayload.TYPE, ThrusterBearingRangePayload.STREAM_CODEC,
                                ThrusterBearingRangePayload::handle);
                registrar.playToServer(ThrusterConfigPayload.TYPE, ThrusterConfigPayload.STREAM_CODEC,
                                ThrusterConfigPayload::handle);
                registrar.playToServer(ThrusterFuelTankPayload.TYPE, ThrusterFuelTankPayload.STREAM_CODEC,
                                ThrusterFuelTankPayload::handle);
                registrar.playToServer(ThrusterSlotPayload.TYPE, ThrusterSlotPayload.STREAM_CODEC,
                                ThrusterSlotPayload::handle);
                registrar.playToServer(AnalogueJoystickConfigPayload.TYPE, AnalogueJoystickConfigPayload.STREAM_CODEC,
                                AnalogueJoystickConfigPayload::handle);
                registrar.playToServer(AnalogueJoystickGhostSlotsPayload.TYPE, AnalogueJoystickGhostSlotsPayload.STREAM_CODEC,
                                AnalogueJoystickGhostSlotsPayload::handle);
                registrar.playToServer(AnalogueJoystickDragPayload.TYPE, AnalogueJoystickDragPayload.STREAM_CODEC,
                                AnalogueJoystickDragPayload::handle);
                registrar.playToServer(AnalogueContraptionControllerGhostSlotsPayload.TYPE, AnalogueContraptionControllerGhostSlotsPayload.STREAM_CODEC,
                                AnalogueContraptionControllerGhostSlotsPayload::handle);
                registrar.playToServer(ClawGhostSlotsPayload.TYPE, ClawGhostSlotsPayload.STREAM_CODEC,
                                ClawGhostSlotsPayload::handle);
                registrar.playToServer(AnalogueContraptionControllerDiscoveryRequestPayload.TYPE, AnalogueContraptionControllerDiscoveryRequestPayload.STREAM_CODEC,
                                AnalogueContraptionControllerDiscoveryRequestPayload::handle);
                registrar.playToServer(AnalogueContraptionControllerDiscoveryNodePayload.TYPE, AnalogueContraptionControllerDiscoveryNodePayload.STREAM_CODEC,
                                AnalogueContraptionControllerDiscoveryNodePayload::handle);
                registrar.playToServer(AnalogueContraptionControllerKeyPayload.TYPE, AnalogueContraptionControllerKeyPayload.STREAM_CODEC,
                                AnalogueContraptionControllerKeyPayload::handle);
                registrar.playToServer(AnalogueContraptionControllerConfigPayload.TYPE, AnalogueContraptionControllerConfigPayload.STREAM_CODEC,
                                AnalogueContraptionControllerConfigPayload::handle);
                registrar.playToServer(AnalogueContraptionControllerCustomKeyPayload.TYPE, AnalogueContraptionControllerCustomKeyPayload.STREAM_CODEC,
                                AnalogueContraptionControllerCustomKeyPayload::handle);
                // ------------------------------------ADVANCED GRAPH / HUD------------------------------------
                registrar.playToServer(AdvancedContraptionControllerGraphPayload.TYPE, AdvancedContraptionControllerGraphPayload.STREAM_CODEC,
                                AdvancedContraptionControllerGraphPayload::handle);
                registrar.playToServer(AdvancedControllerMouseInputPayload.TYPE, AdvancedControllerMouseInputPayload.STREAM_CODEC,
                                AdvancedControllerMouseInputPayload::handle);
                registrar.playToServer(AdvancedControllerPhysicalInteractionPayload.TYPE,
                                AdvancedControllerPhysicalInteractionPayload.STREAM_CODEC,
                                AdvancedControllerPhysicalInteractionPayload::handle);
                registrar.playToServer(HardwareControllerInputPayload.TYPE, HardwareControllerInputPayload.STREAM_CODEC,
                                HardwareControllerInputPayload::handle);
                registrar.playToServer(AdvancedControllerProfilerPayload.TYPE, AdvancedControllerProfilerPayload.STREAM_CODEC,
                                AdvancedControllerProfilerPayload::handle);
                registrar.playToServer(AdvancedHudImageRequestPayload.TYPE, AdvancedHudImageRequestPayload.STREAM_CODEC,
                                AdvancedHudImageRequestPayload::handle);
                registrar.playToServer(AdvancedHudImageUploadPayload.TYPE, AdvancedHudImageUploadPayload.STREAM_CODEC,
                                AdvancedHudImageUploadPayload::handle);
                registrar.playToServer(AdvancedHudInteractionPayload.TYPE, AdvancedHudInteractionPayload.STREAM_CODEC,
                                AdvancedHudInteractionPayload::handle);
                // ------------------------------------DISPLAYS / PORTABLE CONTROLS------------------------------------
                registrar.playToServer(AccDisplayTextInputPayload.TYPE, AccDisplayTextInputPayload.STREAM_CODEC,
                                AccDisplayTextInputPayload::handle);
                registrar.playToServer(AccDisplayModePayload.TYPE, AccDisplayModePayload.STREAM_CODEC,
                                AccDisplayModePayload::handle);
                registrar.playToServer(AccDisplayComputerInputPayload.TYPE,
                                AccDisplayComputerInputPayload.STREAM_CODEC,
                                AccDisplayComputerInputPayload::handle);
                registrar.playToServer(PortableContraptionControllerKeyPayload.TYPE, PortableContraptionControllerKeyPayload.STREAM_CODEC,
                                PortableContraptionControllerKeyPayload::handle);
                registrar.playToServer(PortableContraptionControllerModePayload.TYPE, PortableContraptionControllerModePayload.STREAM_CODEC,
                                PortableContraptionControllerModePayload::handle);
                registrar.playToServer(PortableContraptionControllerOpenPayload.TYPE, PortableContraptionControllerOpenPayload.STREAM_CODEC,
                                PortableContraptionControllerOpenPayload::handle);
                registrar.playToServer(LecternPortableContraptionControllerKeyPayload.TYPE, LecternPortableContraptionControllerKeyPayload.STREAM_CODEC,
                                LecternPortableContraptionControllerKeyPayload::handle);
                registrar.playToServer(LecternPortableContraptionControllerModePayload.TYPE, LecternPortableContraptionControllerModePayload.STREAM_CODEC,
                                LecternPortableContraptionControllerModePayload::handle);
                // ------------------------------------BLOCK CONFIG / NAVIGATION------------------------------------
                registrar.playToServer(GyroscopeLinkConfigPayload.TYPE, GyroscopeLinkConfigPayload.STREAM_CODEC,
                                GyroscopeLinkConfigPayload::handle);
                registrar.playToServer(DoubleButtonConfigPayload.TYPE, DoubleButtonConfigPayload.STREAM_CODEC,
                                DoubleButtonConfigPayload::handle);
                registrar.playToServer(DoubleButtonAppearancePayload.TYPE, DoubleButtonAppearancePayload.STREAM_CODEC,
                                DoubleButtonAppearancePayload::handle);
                registrar.playToServer(DoubleButtonHoldPayload.TYPE, DoubleButtonHoldPayload.STREAM_CODEC,
                                DoubleButtonHoldPayload::handle);
                registrar.playToServer(BiDirectionalGearshiftConfigPayload.TYPE, BiDirectionalGearshiftConfigPayload.STREAM_CODEC,
                                BiDirectionalGearshiftConfigPayload::handle);
                registrar.playToServer(VectorBearingConfigPayload.TYPE, VectorBearingConfigPayload.STREAM_CODEC,
                                VectorBearingConfigPayload::handle);
                registrar.playToServer(AileronBearingConfigPayload.TYPE, AileronBearingConfigPayload.STREAM_CODEC,
                                AileronBearingConfigPayload::handle);
                registrar.playToServer(NavigationTableActionPayload.TYPE, NavigationTableActionPayload.STREAM_CODEC,
                                NavigationTableActionPayload::handle);
                registrar.playToServer(PhysicsGogglesDataRequestPayload.TYPE, PhysicsGogglesDataRequestPayload.STREAM_CODEC,
                                PhysicsGogglesDataRequestPayload::handle);
                // ------------------------------------SHIPPING / TOOLS------------------------------------
                registrar.playToServer(ShippingManifestRefreshPayload.TYPE, ShippingManifestRefreshPayload.STREAM_CODEC,
                                ShippingManifestRefreshPayload::handle);
                registrar.playToServer(ShippingAutoRefuelPayload.TYPE, ShippingAutoRefuelPayload.STREAM_CODEC,
                                ShippingAutoRefuelPayload::handle);
                registrar.playToServer(ShipDockConfigPayload.TYPE, ShipDockConfigPayload.STREAM_CODEC,
                                ShipDockConfigPayload::handle);
                registrar.playToServer(DiagnosticTabletActionPayload.TYPE, DiagnosticTabletActionPayload.STREAM_CODEC,
                                DiagnosticTabletActionPayload::handle);
                registrar.playToServer(ContraptionNetworkLinkerSyncPayload.TYPE, ContraptionNetworkLinkerSyncPayload.STREAM_CODEC,
                                ContraptionNetworkLinkerSyncPayload::handle);
                registrar.playToServer(ArmorStandPoseSyncPayload.TYPE, ArmorStandPoseSyncPayload.STREAM_CODEC,
                                ArmorStandPoseSyncPayload::handle);
                registrar.playToServer(ArmorStandPosePreferencePayload.TYPE, ArmorStandPosePreferencePayload.STREAM_CODEC,
                                ArmorStandPosePreferencePayload::handle);
                // ------------------------------------ROPES / LAUNCHERS------------------------------------
                registrar.playToServer(ServerboundZiplineMountPacket.TYPE, ServerboundZiplineMountPacket.STREAM_CODEC,
                                ServerboundZiplineMountPacket::handle);
                registrar.playToServer(ServerboundZiplineInputPacket.TYPE, ServerboundZiplineInputPacket.STREAM_CODEC,
                                ServerboundZiplineInputPacket::handle);
                registrar.playToServer(ServerboundZiplineAttachPacket.TYPE, ServerboundZiplineAttachPacket.STREAM_CODEC,
                                ServerboundZiplineAttachPacket::handle);
                registrar.playToServer(ServerboundZiplineConfigPacket.TYPE, ServerboundZiplineConfigPacket.STREAM_CODEC,
                                ServerboundZiplineConfigPacket::handle);
                registrar.playToServer(ServerboundZiplineFollowChainPacket.TYPE, ServerboundZiplineFollowChainPacket.STREAM_CODEC,
                                ServerboundZiplineFollowChainPacket::handle);
                registrar.playToServer(ServerboundRopeKnotAttachPacket.TYPE, ServerboundRopeKnotAttachPacket.STREAM_CODEC,
                                ServerboundRopeKnotAttachPacket::handle);
                registrar.playToServer(ServerboundEntityLauncherInputPacket.TYPE, ServerboundEntityLauncherInputPacket.STREAM_CODEC,
                                ServerboundEntityLauncherInputPacket::handle);
                registrar.playToServer(ServerboundEntityLauncherAnchorControlPacket.TYPE, ServerboundEntityLauncherAnchorControlPacket.STREAM_CODEC,
                                ServerboundEntityLauncherAnchorControlPacket::handle);
                registrar.playToServer(ServerboundEntityLauncherPowerModePacket.TYPE, ServerboundEntityLauncherPowerModePacket.STREAM_CODEC,
                                ServerboundEntityLauncherPowerModePacket::handle);
                // ------------------------------------CLIENT MENUS / GRAPH SYNC------------------------------------
                registrar.playToClient(AnalogueContraptionControllerDiscoveryResultsPayload.TYPE, AnalogueContraptionControllerDiscoveryResultsPayload.STREAM_CODEC,
                                AnalogueContraptionControllerDiscoveryResultsPayload::handle);
                registrar.playToClient(PhysicsGogglesDataPayload.TYPE, PhysicsGogglesDataPayload.STREAM_CODEC,
                                PhysicsGogglesDataPayload::handle);
                registrar.playToClient(ShippingManifestOpenPayload.TYPE, ShippingManifestOpenPayload.STREAM_CODEC,
                                ShippingManifestOpenPayload::handle);
                registrar.playToClient(ShipDockOpenPayload.TYPE, ShipDockOpenPayload.STREAM_CODEC,
                                ShipDockOpenPayload::handle);
                registrar.playToClient(ArmorStandPoseOpenPayload.TYPE, ArmorStandPoseOpenPayload.STREAM_CODEC,
                                ArmorStandPoseOpenPayload::handle);
                registrar.playToClient(AdvancedControllerSharedGraphsPayload.TYPE, AdvancedControllerSharedGraphsPayload.STREAM_CODEC,
                                AdvancedControllerSharedGraphsPayload::handle);
                registrar.playToClient(AdvancedControllerPublicSharePayload.TYPE,
                                AdvancedControllerPublicSharePayload.STREAM_CODEC,
                                AdvancedControllerPublicSharePayload::handle);
                registrar.playToClient(AdvancedControllerGraphActionResultPayload.TYPE, AdvancedControllerGraphActionResultPayload.STREAM_CODEC,
                                AdvancedControllerGraphActionResultPayload::handle);
                registrar.playToClient(AdvancedControllerGraphHistoryPayload.TYPE, AdvancedControllerGraphHistoryPayload.STREAM_CODEC,
                                AdvancedControllerGraphHistoryPayload::handle);
                registrar.playToClient(AdvancedControllerGraphSnapshotPayload.TYPE,
                                AdvancedControllerGraphSnapshotPayload.STREAM_CODEC,
                                AdvancedControllerGraphSnapshotPayload::handle);
                registrar.playToClient(AdvancedControllerRuntimePayload.TYPE, AdvancedControllerRuntimePayload.STREAM_CODEC,
                                AdvancedControllerRuntimePayload::handle);
                registrar.playToClient(FunctionPlotterDataPayload.TYPE,
                                FunctionPlotterDataPayload.STREAM_CODEC,
                                FunctionPlotterDataPayload::handle);
                registrar.playToClient(AdvancedHudImageCatalogPayload.TYPE, AdvancedHudImageCatalogPayload.STREAM_CODEC,
                                AdvancedHudImageCatalogPayload::handle);
                registrar.playToClient(AdvancedHudImageDataPayload.TYPE, AdvancedHudImageDataPayload.STREAM_CODEC,
                                AdvancedHudImageDataPayload::handle);
                registrar.playToClient(AccDisplayTextInputOpenPayload.TYPE, AccDisplayTextInputOpenPayload.STREAM_CODEC,
                                AccDisplayTextInputOpenPayload::handle);
                registrar.playToClient(GizmosLinkHighlightPayload.TYPE, GizmosLinkHighlightPayload.STREAM_CODEC,
                                GizmosLinkHighlightPayload::handle);
                // ------------------------------------CLIENT RUNTIME / CONFIG------------------------------------
                registrar.playToClient(ControllerRuntimeSyncPayload.TYPE, ControllerRuntimeSyncPayload.STREAM_CODEC,
                                ControllerRuntimeSyncPayload::handle);
                registrar.playToClient(ContraptionNetworkLinkerSnapshotPayload.TYPE,
                                ContraptionNetworkLinkerSnapshotPayload.STREAM_CODEC,
                                ContraptionNetworkLinkerSnapshotPayload::handle);
                registrar.playToClient(DiagnosticTabletAppSnapshotPayload.TYPE,
                                DiagnosticTabletAppSnapshotPayload.STREAM_CODEC,
                                DiagnosticTabletAppSnapshotPayload::handle);
                registrar.playToClient(DiagnosticTabletRemoteOpenPayload.TYPE,
                                DiagnosticTabletRemoteOpenPayload.STREAM_CODEC,
                                DiagnosticTabletRemoteOpenPayload::handle);
                registrar.playToClient(DiagnosticTabletReopenPayload.TYPE,
                                DiagnosticTabletReopenPayload.STREAM_CODEC,
                                DiagnosticTabletReopenPayload::handle);
                                registrar.playToClient(FeatureToggleSyncPayload.TYPE, FeatureToggleSyncPayload.STREAM_CODEC,
                                                                FeatureToggleSyncPayload::handle);
                registrar.playToClient(GraphV2ThemeSyncPayload.TYPE,
                                                GraphV2ThemeSyncPayload.STREAM_CODEC,
                                                GraphV2ThemeSyncPayload::handle);
                registrar.playToClient(GraphSoundPayload.TYPE,
                                GraphSoundPayload.STREAM_CODEC,
                                GraphSoundPayload::handle);
        }

        // Sync the feature toggles to player
        public static void syncFeatureTogglesToPlayer(ServerPlayer player) {
                CTConfigs.FeatureToggleSnapshot snapshot = CTConfigs.createFeatureToggleSnapshot();
                PacketDistributor.sendToPlayer(player, new FeatureToggleSyncPayload(
                                snapshot.blocks(),
                                snapshot.items(),
                                snapshot.entities()));
        }

        // Sync the feature toggles to all players
        public static void syncFeatureTogglesToAllPlayers() {
                MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                        return;
                }
                CTConfigs.FeatureToggleSnapshot snapshot = CTConfigs.createFeatureToggleSnapshot();
                FeatureToggleSyncPayload payload = new FeatureToggleSyncPayload(
                                snapshot.blocks(),
                                snapshot.items(),
                                snapshot.entities());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        PacketDistributor.sendToPlayer(player, payload);
                }
        }

        // Sync the graph V2 theme to player
        public static void syncGraphV2ThemeToPlayer(ServerPlayer player) {
                PacketDistributor.sendToPlayer(player,
                                new GraphV2ThemeSyncPayload(GraphV2ThemeData.current()));
        }

        // Sync the graph V2 theme to all players
        public static void syncGraphV2ThemeToAllPlayers() {
                MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                        return;
                }
                GraphV2ThemeSyncPayload payload = new GraphV2ThemeSyncPayload(GraphV2ThemeData.current());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        PacketDistributor.sendToPlayer(player, payload);
                }
        }

    // Register the capabilities
    public static void registerCapabilities(RegisterCapabilitiesEvent evt) {
        // ------------------------------------STORAGE CAPABILITIES------------------------------------
        if (CTBlockEntities.SHIP_DOCK != null) {
            evt.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CTBlockEntities.SHIP_DOCK.get(),
                    (com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity be, Direction side) -> be.getItemBuffer());
            evt.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CTBlockEntities.SHIP_DOCK.get(),
                    (com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity be, Direction side) -> be.getFluidBuffer());
            evt.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CTBlockEntities.SHIP_DOCK.get(),
                    (com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity be, Direction side) -> be.getEnergyBuffer());
        }
        if (CTBlockEntities.THRUSTER != null) {
            evt.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CTBlockEntities.THRUSTER.get(),
                    (ThrusterBlockEntity be, Direction side) -> be.canAcceptFuel() ? be.getFuelTank() : null);
            evt.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CTBlockEntities.THRUSTER.get(),
                    (ThrusterBlockEntity be, Direction side) -> be.getItemInventory());
            evt.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CTBlockEntities.THRUSTER.get(),
                    (ThrusterBlockEntity be, Direction side) -> be.isFocusedMode() ? be.getFocusInput() : null);
        }
        if (CTBlockEntities.FUEL_OXIDIZER != null) {
            evt.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CTBlockEntities.FUEL_OXIDIZER.get(),
                    (FuelOxidizerBlockEntity be, Direction side) -> be.getFluidHandler(side));
        }
        if (CTBlockEntities.ALTERNATOR != null) {
            evt.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CTBlockEntities.ALTERNATOR.get(),
                    (AlternatorBlockEntity be, Direction side) -> be.canExtractEnergyFrom(side) ? be.getEnergyStorage() : null);
        }
        if (CTBlockEntities.THRUSTER_BEARING != null) {
            evt.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CTBlockEntities.THRUSTER_BEARING.get(),
                    (ThrusterBearingBlockEntity be, Direction side) -> be.canAcceptAssemblyEnergyFrom(side)
                            ? be.getAssemblyEnergyHandler()
                            : null);
            evt.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CTBlockEntities.THRUSTER_BEARING.get(),
                    (ThrusterBearingBlockEntity be, Direction side) -> be.canAcceptAssemblyFluidFrom(side)
                            ? be.getAssemblyFluidHandler()
                            : null);
        }
        if (CTBlockEntities.VECTOR_BEARING != null) {
            evt.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CTBlockEntities.VECTOR_BEARING.get(),
                    (VectorBearingBlockEntity be, Direction side) -> be.canAcceptAssemblyEnergyFrom(side)
                            ? be.getAssemblyEnergyHandler()
                            : null);
            evt.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CTBlockEntities.VECTOR_BEARING.get(),
                    (VectorBearingBlockEntity be, Direction side) -> be.canAcceptAssemblyFluidFrom(side)
                            ? be.getAssemblyFluidHandler()
                            : null);
        }
        if (CTBlockEntities.ANDESITE_CABLE != null) {
            evt.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CTBlockEntities.ANDESITE_CABLE.get(),
                    (AndesiteCableBlockEntity be, Direction side) -> be.getEnergyView(side));
        }
        if (CTBlockEntities.ADVANCED_NAVIGATION_TABLE != null) {
            evt.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CTBlockEntities.ADVANCED_NAVIGATION_TABLE.get(),
                    (AdvancedNavigationTableBlockEntity be, Direction side) -> be.getItemHandler());
        }
        if (CTBlockEntities.INDUSTRIAL_MOTOR != null) {
            evt.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CTBlockEntities.INDUSTRIAL_MOTOR.get(),
                    (IndustrialMotorBlockEntity be, Direction side) -> be.getEnergyStorage());
        }
        // ------------------------------------OPTIONAL COMPAT------------------------------------
        if (ModList.get().isLoaded("computercraft")) {

            try {
                Class<?> compatRegistrarClass = Class.forName("com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftCompatRegistrar");
                Method registerMethod = compatRegistrarClass.getMethod("registerCapabilities", RegisterCapabilitiesEvent.class);
                registerMethod.invoke(null, evt);
            } catch (ReflectiveOperationException err) {
                LOGGER.error("Failed to register ComputerCraft compatibility capabilities", err);
            }
        }
                if (ModList.get().isLoaded("curios")) {

                        try {
                                Class<?> compatRegistrarClass = Class.forName("com.rieno.gadgetsandgizmos.compat.curios.CuriosCompatRegistrar");
                                Method registerMethod = compatRegistrarClass.getMethod("register", RegisterCapabilitiesEvent.class);
                                registerMethod.invoke(null, evt);
                        } catch (ReflectiveOperationException err) {
                                LOGGER.error("Failed to register Curios compatibility capabilities", err);
                        }
                }
                if (ModList.get().isLoaded("accessories")) {

                        try {
                                Class<?> compatRegistrarClass = Class.forName("com.rieno.gadgetsandgizmos.compat.accessories.AccessoriesCompatRegistrar");
                                Method registerMethod = compatRegistrarClass.getMethod("register");
                                registerMethod.invoke(null);
                        } catch (ReflectiveOperationException err) {
                                LOGGER.error("Failed to register Accessories compatibility", err);
                        }
                }
    }
}
