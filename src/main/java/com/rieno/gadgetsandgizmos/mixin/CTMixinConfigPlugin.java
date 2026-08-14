package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

// Load optional mixins only when the addon and version they target are actually available
public final class CTMixinConfigPlugin implements IMixinConfigPlugin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger CT_LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean CT_LOGGED_WHEEL_MOUNT_SKIP = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_WHEEL_MOUNT_DBW_COMPAT = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_TOMS_RANGE_COMPAT = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_AEROWORKS_COMPAT = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_SIMULATED_ROPE_LEGACY = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_SIMULATED_ROPE_CURRENT = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_DIMENSIONAL_SABLE_BRIDGE = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_BITS_N_BOBS_GANTRY_BELT = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_PROPULSION_VECTOR_ANGLES = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_PROPULSION_CC_ANGLES = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_PROPULSION_PRECISE_THROTTLE = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_BASIC_NAVIGATION_CC = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_COMPUTED_EVENTS = new AtomicBoolean(false);
    private static final AtomicBoolean CT_LOGGED_DOCKING_ENERGY_SKIP = new AtomicBoolean(false);

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the load event
    @Override
    public void onLoad(String mixinPackage) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the ref mapper config
    @Override
    public String getRefMapperConfig() {
        return null;
    }

    // Check if this should apply mixin
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // ---------------------------------------------------DIRECT MOD GATES---------------------------------------------------
        if ("com.rieno.gadgetsandgizmos.mixin.ComputerCraftRemoteDesktopMenuMixin"
                .equals(mixinClassName)) {
            return isModLoadedDuringMixinSelection("computercraft");
        }
        if ("com.rieno.gadgetsandgizmos.mixin.CreateAdditionEnergyThresholdConditionMixin"
                .equals(mixinClassName)) {
            return isModLoadedDuringMixinSelection("createaddition");
        }

        // ------------------------------------------------SIMULATED / SABLE APIS------------------------------------------------
        if (isDockingConnectorEnergyMixin(mixinClassName)) {
            boolean apply = supportsDockingConnectorEnergyTransfer();
            if (!apply && CT_LOGGED_DOCKING_ENERGY_SKIP.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Disabled docking-connector FE transfer for the legacy Sable/Simulated API");
            }
            return apply;
        }

        if (isSimulatedCreateRopeMixin(mixinClassName)) {
            boolean currentApi = hasSimulatedCreateRopeBooleanParameter();
            logSimulatedRopeApi(currentApi);
            return mixinClassName.endsWith("LegacyMixin") ? !currentApi : currentApi;
        }

        if (isSimulatedDestroyRopeMixin(mixinClassName)) {
            boolean currentApi = hasSimulatedDestroyRopeBooleanParameter();
            logSimulatedRopeApi(currentApi);
            return mixinClassName.endsWith("LegacyMixin") ? !currentApi : currentApi;
        }

        if ("com.rieno.gadgetsandgizmos.mixin.SableServerLevelPlotContraptionsBridgeMixin".equals(mixinClassName)) {
            boolean apply = isModLoadedDuringMixinSelection("dimensional_sable")
                    && needsSablePlotContraptionsBridge();
            if (apply && CT_LOGGED_DIMENSIONAL_SABLE_BRIDGE.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Dimensional Sable ServerLevelPlot#getContraptions bridge");
            }
            return apply;
        }

        // ------------------------------------------------CONTROLLER / STORAGE-------------------------------------------------
        if ("com.rieno.gadgetsandgizmos.mixin.AeroworksMechanicalServoRedstoneMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.AeroworksStepperServoRedstoneMixin".equals(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("aeroworks");
            if (loaded && CT_LOGGED_AEROWORKS_COMPAT.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Aeroworks controller redstone compatibility");
            }
            return loaded;
        }

        if ("com.rieno.gadgetsandgizmos.mixin.WheelMountDirectControlMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.WheelMountDirectControlDbwCompatMixin".equals(mixinClassName)) {
            ModList modList = ModList.get();
            if (modList == null) {
                if ("com.rieno.gadgetsandgizmos.mixin.WheelMountDirectControlMixin".equals(mixinClassName)) {

                    if (CT_LOGGED_WHEEL_MOUNT_SKIP.compareAndSet(false, true)) {
                        CT_LOGGER.warn("[CT][Compat] Skipped WheelMountDirectControlMixin during early bootstrap because ModList is not ready");
                    }
                    return false;
                }

                if (CT_LOGGED_WHEEL_MOUNT_DBW_COMPAT.compareAndSet(false, true)) {
                    CT_LOGGER.warn("[CT][Compat] Applied WheelMountDirectControlDbwCompatMixin during early bootstrap fallback");
                }
                return true;
            }

            boolean driveByWireLoaded = modList.isLoaded("drivebywire");
            if ("com.rieno.gadgetsandgizmos.mixin.WheelMountDirectControlMixin".equals(mixinClassName)) {
                if (!driveByWireLoaded) {
                    return true;
                }

                if (CT_LOGGED_WHEEL_MOUNT_SKIP.compareAndSet(false, true)) {
                    CT_LOGGER.warn("[CT][Compat] Disabled WheelMountDirectControlMixin because drivebywire is loaded");
                }
                return false;
            }

            if (!driveByWireLoaded) {
                return false;
            }
            if (CT_LOGGED_WHEEL_MOUNT_DBW_COMPAT.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled WheelMountDirectControlDbwCompatMixin because drivebywire is loaded");
            }
            return true;
        }

        if ("com.rieno.gadgetsandgizmos.mixin.TomsStorageTerminalRangeCompatMixin".equals(mixinClassName)) {
            ModList modList = ModList.get();
            if (modList == null) {
                return false;
            }
            boolean loaded = modList.isLoaded("toms_storage");

            if (loaded && CT_LOGGED_TOMS_RANGE_COMPAT.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Tom's Storage terminal range shim");
            }
            return loaded;
        }

        if ("com.rieno.gadgetsandgizmos.mixin.AeroworksConsoleControllerCompatMixin".equals(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("aeroworks");
            String version = getLoadedModVersion("aeroworks");
            boolean apply = loaded && version != null
                    && compareVersions(version, "1.3.0") >= 0
                    && compareVersions(version, "1.4.0") < 0;
            if (apply && CT_LOGGED_AEROWORKS_COMPAT.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Aeroworks controller compatibility");
            }
            return apply;
        }

        if ("com.rieno.gadgetsandgizmos.mixin.SophisticatedCoreMusicDiscFilterMixin".equals(mixinClassName)) {
            return isModLoadedDuringMixinSelection("sophisticatedcore");
        }

        if ("com.rieno.gadgetsandgizmos.mixin.BitsNBobsCogwheelChainPlacementInteractionMixin".equals(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("bits_n_bobs");
            if (loaded && CT_LOGGED_BITS_N_BOBS_GANTRY_BELT.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Bits'n'Bobs gantry belt wheel interaction compatibility");
            }
            return loaded;
        }

        // -----------------------------------------------------PROPULSION------------------------------------------------------
        if (isPropulsionVectorAngleMixin(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("createpropulsion");
            if (loaded && CT_LOGGED_PROPULSION_VECTOR_ANGLES.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Create Propulsion vector angle controls");
            }
            return loaded;
        }

        if (isPropulsionComputerCraftAngleMixin(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("createpropulsion")
                    && isModLoadedDuringMixinSelection("computercraft");
            if (loaded && CT_LOGGED_PROPULSION_CC_ANGLES.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Create Propulsion ComputerCraft vector angle controls");
            }
            return loaded;
        }

        if (isPropulsionComputerCraftThrottleMixin(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("createpropulsion")
                    && isModLoadedDuringMixinSelection("computercraft");
            if (loaded && CT_LOGGED_PROPULSION_PRECISE_THROTTLE.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled precise Create Propulsion ComputerCraft throttle control");
            }
            return loaded;
        }

        if (isPropulsionGraphThrottleMixin(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("createpropulsion");
            if (loaded && CT_LOGGED_PROPULSION_PRECISE_THROTTLE.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Create Propulsion direct graph and ship throttle control");
            }
            return loaded;
        }

        // --------------------------------------------------COMPUTERS / EVENTS--------------------------------------------------
        if (isBasicNavTableComputerCraftMixin(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("simulated")
                    && isModLoadedDuringMixinSelection("computercraft");
            if (loaded && CT_LOGGED_BASIC_NAVIGATION_CC.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled basic navigation table ComputerCraft position methods");
            }
            return loaded;
        }

        if (isComputedEventMixin(mixinClassName)) {
            boolean loaded = isModLoadedDuringMixinSelection("computed");
            if (loaded && CT_LOGGED_COMPUTED_EVENTS.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Enabled Computed named event compatibility");
            }
            return loaded;
        }

        return true;
    }

    // Check if the mod is loaded during mixin selection
    private static boolean isModLoadedDuringMixinSelection(String modId) {
        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList != null) {
            return loadingModList.getModFileById(modId) != null;
        }
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    // Check if this is a simulated create rope mixin
    private static boolean isSimulatedCreateRopeMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.PoweredZiplineMultiRopeCreateMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.PoweredZiplineMultiRopeCreateLegacyMixin".equals(mixinClassName);
    }

    // Check if this is a simulated destroy rope mixin
    private static boolean isSimulatedDestroyRopeMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.PoweredZiplineRopeHolderDestroyMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.PoweredZiplineRopeHolderDestroyLegacyMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.EntityLauncherRopeDropSuppressMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.EntityLauncherRopeDropSuppressLegacyMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.EntityLauncherRopeSubLevelValidationMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.EntityLauncherRopeSubLevelValidationLegacyMixin".equals(mixinClassName);
    }

    // Check if this is propulsion ComputerCraft angle mixin
    private static boolean isPropulsionComputerCraftAngleMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.PropulsionVectorThrusterPeripheralMixin".equals(mixinClassName);
    }

    // Check if this is propulsion vector angle mixin
    private static boolean isPropulsionVectorAngleMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.PropulsionVectorThrusterAngleMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.PropulsionLiquidVectorThrusterAngleMixin".equals(mixinClassName);
    }

    // Check if this is propulsion ComputerCraft throttle mixin
    private static boolean isPropulsionComputerCraftThrottleMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.PropulsionPreciseThrottleMixin".equals(mixinClassName);
    }

    // Check if this is propulsion graph throttle mixin
    private static boolean isPropulsionGraphThrottleMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.PropulsionThrusterGraphDataMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.PropulsionSpecializedThrusterDirectPowerMixin"
                .equals(mixinClassName);
    }

    // Check if this is basic nav table ComputerCraft mixin
    private static boolean isBasicNavTableComputerCraftMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.BasicNavigationTablePeripheralMixin".equals(mixinClassName);
    }

    // Check if this is computed event mixin
    private static boolean isComputedEventMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.ComputedComputerBlockEntityMixin".equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.ComputedPendingLuaInvocationMixin"
                .equals(mixinClassName);
    }

    // Check if this is docking connector energy mixin
    private static boolean isDockingConnectorEnergyMixin(String mixinClassName) {
        return "com.rieno.gadgetsandgizmos.mixin.ShippingDockingConnectorBatteryOwnerMixin"
                .equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.ShippingDockingConnectorBatteryMixin"
                .equals(mixinClassName)
                || "com.rieno.gadgetsandgizmos.mixin.ShippingDockingConnectorBatteryExtractionMixin"
                .equals(mixinClassName);
    }

    // Check if this supports docking connector energy transfer
    private static boolean supportsDockingConnectorEnergyTransfer() {
        String sableVersion = getLoadedModVersion("sable");
        if (sableVersion != null && !sableVersion.isBlank()
                && compareVersions(sableVersion, "2.0.0") < 0) {
            return false;
        }
        String simulatedVersion = getLoadedModVersion("simulated");
        return simulatedVersion == null || simulatedVersion.isBlank()
                || compareVersions(simulatedVersion, "1.3.0") >= 0;
    }

    // Check if this has simulated create rope boolean parameter
    private static boolean hasSimulatedCreateRopeBooleanParameter() {
        return isCurrentSimulatedRopeApi();
    }

    // Check if this has simulated destroy rope boolean parameter
    private static boolean hasSimulatedDestroyRopeBooleanParameter() {
        return isCurrentSimulatedRopeApi();
    }

    // Check if this is current simulated rope API
    private static boolean isCurrentSimulatedRopeApi() {
        String version = getLoadedModVersion("simulated");
        if (version == null || version.isBlank()) {
            return true;
        }
        return compareVersions(version, "1.3.0") >= 0;
    }

    // Get the loaded mod version
    private static String getLoadedModVersion(String modId) {
        LoadingModList loadingModList = LoadingModList.get();
        if (loadingModList != null) {
            ModFileInfo fileInfo = loadingModList.getModFileById(modId);
            if (fileInfo != null && !fileInfo.getMods().isEmpty()) {
                return fileInfo.getMods().getFirst().getVersion().toString();
            }
        }
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(null);
        }
        return null;
    }

    // Check if this needs sable plot contraptions bridge
    private static boolean needsSablePlotContraptionsBridge() {
        String version = getLoadedModVersion("sable");
        return version != null && compareVersions(version, "2.0.0") >= 0;
    }

    // Get the compare versions
    private static int compareVersions(String left, String right) {
        int[] leftParts = parseVersionParts(left);
        int[] rightParts = parseVersionParts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            int leftPart = i < leftParts.length ? leftParts[i] : 0;
            int rightPart = i < rightParts.length ? rightParts[i] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    // Parse the version parts
    private static int[] parseVersionParts(String version) {
        String sanitized = version == null ? "" : version;
        int plusIndex = sanitized.indexOf('+');
        if (plusIndex >= 0) {
            sanitized = sanitized.substring(0, plusIndex);
        }
        String[] tokens = sanitized.split("[^0-9]+");
        int count = 0;
        for (String token : tokens) {
            if (!token.isEmpty()) {
                count++;
            }
        }
        int[] parts = new int[count];
        int idx = 0;
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            try {
                parts[idx++] = Integer.parseInt(token);
            } catch (NumberFormatException ignored) {
                parts[idx - 1] = 0;
            }
        }
        return parts;
    }

    // Log the simulated rope API
    private static void logSimulatedRopeApi(boolean currentApi) {
        if (currentApi) {
            if (CT_LOGGED_SIMULATED_ROPE_CURRENT.compareAndSet(false, true)) {
                CT_LOGGER.info("[CT][Compat] Detected Simulated rope API with boolean create/destroy parameters");
            }
            return;
        }
        if (CT_LOGGED_SIMULATED_ROPE_LEGACY.compareAndSet(false, true)) {
            CT_LOGGER.info("[CT][Compat] Detected legacy Simulated rope API without boolean create/destroy parameters");
        }
    }

    // Accept the targets
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    // Get the mixins
    @Override
    public List<String> getMixins() {
        return null;
    }

    // Prepare a mod mixin application
    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    // Post the apply
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
