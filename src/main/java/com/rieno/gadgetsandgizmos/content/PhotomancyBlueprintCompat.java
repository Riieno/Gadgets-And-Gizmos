package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// Clean and remap controller data while Photomancy copies a ship blueprint
public final class PhotomancyBlueprintCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MAPPER_INTERFACE =
            "dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintBlockMapper";
    private static final String MAPPER_REGISTRY =
            "dev.rew1nd.sableschematicapi.api.blueprint.SableBlueprintMapperRegistry";
    private static final String PORTABLE_REF = "createthrusters:photomancy_bearing_ref";
    private static final String PORTABLE_CONTROLLER_REF =
            "createthrusters:photomancy_controller_ref";
    private static final String PORTABLE_SHIP_MAP =
            "createthrusters:photomancy_ship_map";
    private static final String REF_SUB_LEVEL_ID = "sub_level_id";
    private static final String REF_LOCAL_POS = "local_pos";
    private static final String REF_SOURCE_UUID = "source_uuid";
    private static final String REF_SOURCE_POS = "source_pos";
    private static final String REF_HAS_POSITION = "has_position";
    private static final String SHIP_MAP_FRAMES = "frames";
    private static final String SHIP_MAP_FRAME_ID = "blueprint_id";
    private static final String SHIP_CONTROL_MAP = "ShipControlMap";
    private static final String NATIVE_SUB_LEVEL_ID = "SubLevelId";
    private static final String NATIVE_BLOCK_POS = "BlockPos";
    private static final String MOUNTED_SUB_LEVEL = "MountedSubLevel";
    private static final String MOUNTED_LOCAL_POS = "MountedLocalPos";
    private static final String MOUNTED_ASSEMBLY_PRESENT = "MountedAssemblyPresent";
    private static final String PARENT_POS = "ParentPos";
    private static final String PARENT_SUB_LEVEL_ID = "ParentSubLevelId";
    private static final String THRUSTER_PARENT_POS = "ThrusterParentPos";
    private static final String THRUSTER_PARENT_SUB_LEVEL_ID = "ThrusterParentSubLevelId";
    private static final String SUB_LEVEL_ID = "SubLevelID";
    private static final String SWIVEL_PLATE = "SwivelPlate";
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether photomancy blueprint compat is registered
    private static boolean registered;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the photomancy blueprint compat
    private PhotomancyBlueprintCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the common setup event
    public static void onCommonSetup(FMLCommonSetupEvent evt) {
        evt.enqueueWork(PhotomancyBlueprintCompat::registerIfPresent);
    }

    // Register the photomancy blueprint compat if present
    private static synchronized void registerIfPresent() {
        if (registered) {
            return;
        }
        ClassLoader loader = PhotomancyBlueprintCompat.class.getClassLoader();
        Class<?> mapperInterface;
        Class<?> mapperRegistry;
        try {
            mapperInterface = Class.forName(MAPPER_INTERFACE, false, loader);
            mapperRegistry = Class.forName(MAPPER_REGISTRY, false, loader);
        } catch (ClassNotFoundException ignored) {
            return;
        }

        try {
            Method register = mapperRegistry.getMethod(
                    "register", BlockEntityType.class, mapperInterface);
            Set<BlockEntityType<?>> specializedTypes = new HashSet<>();
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.AILERON_BEARING.get(), NbtKind.AILERON_BEARING);
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.AILERON_BEARING_LINK.get(), NbtKind.AILERON_LINK);
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.VECTOR_BEARING.get(), NbtKind.VECTOR_BEARING);
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.VECTOR_BEARING_LINK.get(), NbtKind.VECTOR_LINK);
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.THRUSTER_BEARING.get(), NbtKind.THRUSTER_BEARING);
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.THRUSTER_BEARING_LINK.get(), NbtKind.THRUSTER_LINK);
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER.get(), NbtKind.CONTROLLER);
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get(), NbtKind.CONTROLLER);
            registerMapper(register, mapperInterface, specializedTypes,
                    CTBlockEntities.SHIP_DOCK.get(), NbtKind.SHIP_DOCK);
            for (BlockEntityType<?> blockEntityType : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                if (!specializedTypes.contains(blockEntityType)
                        && SchematicBlockEntityConfigPayload.supports(
                        BlockEntityType.getKey(blockEntityType))) {
                    registerMapper(register, mapperInterface, blockEntityType,
                            NbtKind.CONFIGURED_BLOCK);
                }
            }
            registered = true;
            LOGGER.info("Registered Photomancy blueprint compatibility for Create Thrusters");
        } catch (ReflectiveOperationException | LinkageError error) {
            LOGGER.warn("Could not register Photomancy blueprint compatibility", error);
        }
    }

    // Register the mapper
    private static void registerMapper(
            Method register,
            Class<?> mapperInterface,
            Set<BlockEntityType<?>> specializedTypes,
            BlockEntityType<?> blockEntityType,
            NbtKind kind
    ) throws ReflectiveOperationException {
        specializedTypes.add(blockEntityType);
        registerMapper(register, mapperInterface, blockEntityType, kind);
    }

    // Register the mapper
    private static void registerMapper(Method register, Class<?> mapperInterface,
                                       BlockEntityType<?> blockEntityType, NbtKind kind)
            throws ReflectiveOperationException {
        ClassLoader loader = mapperInterface.getClassLoader();
        if (loader == null) {
            loader = PhotomancyBlueprintCompat.class.getClassLoader();
        }
        Object mapper = Proxy.newProxyInstance(
                loader,
                new Class<?>[]{mapperInterface},
                new BlueprintMapperHandler(kind));
        register.invoke(null, blockEntityType, mapper);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Save the tag
    static CompoundTag saveTag(NbtKind kind, Object ctx, CompoundTag defaultTag)
            throws ReflectiveOperationException {
        CompoundTag tag = defaultTag.copy();
        switch (kind) {
            case AILERON_BEARING -> {
                saveAileronHead(ctx, tag, "primary");
                saveAileronHead(ctx, tag, "secondary");
            }
            case AILERON_LINK, VECTOR_LINK ->
                    captureConnection(ctx, tag, PARENT_SUB_LEVEL_ID, PARENT_POS, PositionEncoding.COMPOUND);
            case VECTOR_BEARING -> {
                captureConnection(
                        ctx, tag, MOUNTED_SUB_LEVEL, MOUNTED_LOCAL_POS, PositionEncoding.PACKED_LONG);
                tag.putBoolean(MOUNTED_ASSEMBLY_PRESENT, false);
            }
            case THRUSTER_BEARING ->
                    captureConnection(ctx, tag, SUB_LEVEL_ID, SWIVEL_PLATE, PositionEncoding.COMPOUND);
            case THRUSTER_LINK -> saveThrusterLink(ctx, tag);
            case CONTROLLER -> saveController(ctx, tag);
            case SHIP_DOCK, CONFIGURED_BLOCK -> {
            }
        }
        if (kind != NbtKind.CONTROLLER) {
            SchematicBlockEntityConfigPayload.capture(tag);
        }
        return tag;
    }

    // Prepare the tag for placement
    static void prepareTagForPlacement(NbtKind kind, Object ctx, CompoundTag tag)
            throws ReflectiveOperationException {
        if (kind != NbtKind.CONTROLLER) {
            SchematicBlockEntityConfigPayload.restore(tag);
        }
        switch (kind) {
            case AILERON_BEARING -> {
                placeAileronHead(ctx, tag, "primary");
                placeAileronHead(ctx, tag, "secondary");
            }
            case AILERON_LINK, VECTOR_LINK ->
                    restoreConnection(ctx, tag, PARENT_SUB_LEVEL_ID, PARENT_POS, PositionEncoding.COMPOUND);
            case VECTOR_BEARING -> {
                boolean restored = restoreConnection(
                        ctx, tag, MOUNTED_SUB_LEVEL, MOUNTED_LOCAL_POS, PositionEncoding.PACKED_LONG);
                tag.putBoolean(MOUNTED_ASSEMBLY_PRESENT, restored);
            }
            case THRUSTER_BEARING ->
                    restoreConnection(ctx, tag, SUB_LEVEL_ID, SWIVEL_PLATE, PositionEncoding.COMPOUND);
            case THRUSTER_LINK -> placeThrusterLink(ctx, tag);
            case CONTROLLER -> prepareCtrlForPlacement(ctx, tag);
            case SHIP_DOCK ->
                    SchematicBlockEntityConfigPayload.clearShipDockPlacementIdentity(tag);
            case CONFIGURED_BLOCK -> {
            }
        }
    }

    // Save the controller
    private static void saveController(Object ctx, CompoundTag tag)
            throws ReflectiveOperationException {
        clearCtrlManifestMetadata(tag);
        Object blockEntity = invokeNoArgs(ctx, "blockEntity");
        Object registryAccess = invokeNoArgs(ctx, "registryAccess");
        if (!(blockEntity instanceof AnalogueContraptionControllerBlockEntity controller)
                || !(registryAccess instanceof HolderLookup.Provider provider)) {
            tag.remove(ControllerSchematicPayload.BLOCK_ENTITY_TAG);
            return;
        }

        CompoundTag payload = controller.createPhotomancySchematicPayload(provider);
        payload.remove(ControllerSchematicPayload.PLACEMENT_PREPARED_TAG);
        makeControllerPayloadPortable(ctx, payload);
        if (!ControllerSchematicPayload.write(tag, payload)) {
            tag.remove(ControllerSchematicPayload.BLOCK_ENTITY_TAG);
        }
    }

    // Prepare the ctrl for placement
    private static void prepareCtrlForPlacement(Object ctx, CompoundTag tag)
            throws ReflectiveOperationException {
        clearCtrlManifestMetadata(tag);
        CompoundTag payload = ControllerSchematicPayload.take(tag);
        if (payload == null) {
            return;
        }

        restoreControllerPayload(ctx, payload);
        payload.putBoolean(ControllerSchematicPayload.PLACEMENT_PREPARED_TAG, true);
        if (!ControllerSchematicPayload.write(tag, payload)) {
            tag.remove(ControllerSchematicPayload.BLOCK_ENTITY_TAG);
        }
    }

    // Make the controller payload portable
    static void makeControllerPayloadPortable(Object ctx, CompoundTag payload)
            throws ReflectiveOperationException {
        if (payload.contains(ControllerSchematicPayload.CONTROLLER_DATA_TAG, Tag.TAG_COMPOUND)) {
            captureCtrlReferences(
                    ctx, payload.getCompound(ControllerSchematicPayload.CONTROLLER_DATA_TAG));
        }
        if (payload.contains(ControllerSchematicPayload.LINKER_DATA_TAG, Tag.TAG_COMPOUND)) {
            captureCtrlReferences(
                    ctx, payload.getCompound(ControllerSchematicPayload.LINKER_DATA_TAG));
        }
        if (payload.contains(SHIP_CONTROL_MAP, Tag.TAG_COMPOUND)) {
            CompoundTag portableMap =
                    makeShipControlMapPortable(ctx, payload.getCompound(SHIP_CONTROL_MAP));
            if (portableMap == null || portableMap.isEmpty()) {
                payload.remove(SHIP_CONTROL_MAP);
            } else {
                payload.put(SHIP_CONTROL_MAP, portableMap);
            }
        }
    }

    // Restore the controller payload
    static void restoreControllerPayload(Object ctx, CompoundTag payload)
            throws ReflectiveOperationException {
        List<IdentifierReplacement> replacements = new ArrayList<>();
        if (payload.contains(ControllerSchematicPayload.CONTROLLER_DATA_TAG, Tag.TAG_COMPOUND)) {
            restoreCtrlReferences(
                    ctx,
                    payload.getCompound(ControllerSchematicPayload.CONTROLLER_DATA_TAG),
                    replacements);
        }
        if (payload.contains(ControllerSchematicPayload.LINKER_DATA_TAG, Tag.TAG_COMPOUND)) {
            restoreCtrlReferences(
                    ctx,
                    payload.getCompound(ControllerSchematicPayload.LINKER_DATA_TAG),
                    replacements);
        }
        rewriteIdStrings(payload, replacements);

        if (payload.contains(SHIP_CONTROL_MAP, Tag.TAG_COMPOUND)) {
            CompoundTag placedMap =
                    placeShipControlMap(ctx, payload.getCompound(SHIP_CONTROL_MAP));
            if (placedMap == null || placedMap.isEmpty()) {
                payload.remove(SHIP_CONTROL_MAP);
            } else {
                payload.put(SHIP_CONTROL_MAP, placedMap);
            }
        }
    }

    // Capture the ctrl references
    private static void captureCtrlReferences(Object ctx, Tag tag)
            throws ReflectiveOperationException {
        if (tag instanceof CompoundTag compound) {
            captureCtrlReference(ctx, compound);
            for (String key : List.copyOf(compound.getAllKeys())) {
                Tag child = compound.get(key);
                if (!PORTABLE_CONTROLLER_REF.equals(key)) {
                    captureCtrlReferences(ctx, child);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                captureCtrlReferences(ctx, child);
            }
        }
    }

    // Capture the ctrl reference
    private static void captureCtrlReference(Object ctx, CompoundTag compound)
            throws ReflectiveOperationException {
        if (!compound.hasUUID(NATIVE_SUB_LEVEL_ID)) {
            return;
        }

        UUID sourceUuid = compound.getUUID(NATIVE_SUB_LEVEL_ID);
        boolean hasPosition = compound.contains(NATIVE_BLOCK_POS, Tag.TAG_LONG);
        BlockPos sourcePos =
                hasPosition ? BlockPos.of(compound.getLong(NATIVE_BLOCK_POS)) : null;
        compound.remove(NATIVE_SUB_LEVEL_ID);
        compound.remove(NATIVE_BLOCK_POS);
        compound.remove(PORTABLE_CONTROLLER_REF);

        Optional<?> subLevelRef = invokeOptional(ctx, "subLevelRef", UUID.class, sourceUuid);
        if (subLevelRef.isEmpty()) {
            return;
        }
        int blueprintId = invokeInt(subLevelRef.get(), "subLevelId");
        BlockPos localPos = null;
        if (hasPosition) {
            Optional<?> blockRef = invokeOptional(ctx, "blockRef", BlockPos.class, sourcePos);
            if (blockRef.isEmpty()
                    || blueprintId != invokeInt(blockRef.get(), "subLevelId")) {
                return;
            }
            localPos = (BlockPos) invokeNoArgs(blockRef.get(), "localPos");
            if (localPos == null) {
                return;
            }
        }

        CompoundTag reference = new CompoundTag();
        reference.putUUID(REF_SOURCE_UUID, sourceUuid);
        reference.putInt(REF_SUB_LEVEL_ID, blueprintId);
        reference.putBoolean(REF_HAS_POSITION, hasPosition);
        if (localPos != null) {
            reference.put(REF_LOCAL_POS, NbtUtils.writeBlockPos(localPos));
            reference.putLong(REF_SOURCE_POS, sourcePos.asLong());
        }
        compound.put(PORTABLE_CONTROLLER_REF, reference);
    }

    // Restore the ctrl references
    private static void restoreCtrlReferences(
            Object ctx,
            Tag tag,
            List<IdentifierReplacement> replacements)
            throws ReflectiveOperationException {
        if (tag instanceof CompoundTag compound) {
            restoreCtrlReference(ctx, compound, replacements);
            for (String key : List.copyOf(compound.getAllKeys())) {
                restoreCtrlReferences(ctx, compound.get(key), replacements);
            }
        } else if (tag instanceof ListTag list) {
            for (Tag child : list) {
                restoreCtrlReferences(ctx, child, replacements);
            }
        }
    }

    // Restore the ctrl reference
    private static void restoreCtrlReference(
            Object ctx,
            CompoundTag compound,
            List<IdentifierReplacement> replacements)
            throws ReflectiveOperationException {
        if (!compound.contains(PORTABLE_CONTROLLER_REF, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag reference = compound.getCompound(PORTABLE_CONTROLLER_REF).copy();
        compound.remove(PORTABLE_CONTROLLER_REF);
        compound.remove(NATIVE_SUB_LEVEL_ID);
        compound.remove(NATIVE_BLOCK_POS);
        if (!reference.hasUUID(REF_SOURCE_UUID)
                || !reference.contains(REF_SUB_LEVEL_ID, Tag.TAG_INT)) {
            return;
        }

        UUID sourceUuid = reference.getUUID(REF_SOURCE_UUID);
        UUID placedUuid = mapPlacedSubLevel(ctx, sourceUuid);
        if (placedUuid == null) {
            return;
        }
        boolean hasPosition = reference.getBoolean(REF_HAS_POSITION);
        BlockPos sourceLocalPos = hasPosition
                ? readNbtPos(reference, REF_LOCAL_POS)
                : null;
        BlockPos sourcePos = hasPosition && reference.contains(REF_SOURCE_POS, Tag.TAG_LONG)
                ? BlockPos.of(reference.getLong(REF_SOURCE_POS))
                : null;
        BlockPos placedPos = hasPosition
                ? mapPlacedPosition(
                ctx, reference.getInt(REF_SUB_LEVEL_ID), sourceLocalPos)
                : null;
        if (hasPosition && placedPos == null) {
            return;
        }

        compound.putUUID(NATIVE_SUB_LEVEL_ID, placedUuid);
        if (placedPos != null) {
            compound.putLong(NATIVE_BLOCK_POS, placedPos.asLong());
        }
        replacements.add(new IdentifierReplacement(
                sourceUuid, sourcePos, placedUuid, placedPos));
    }

    // Rewrite the id strings
    private static void rewriteIdStrings(
            Tag tag,
            List<IdentifierReplacement> replacements) {
        if (tag == null || replacements.isEmpty()) {
            return;
        }
        if (tag instanceof CompoundTag compound) {
            for (String key : List.copyOf(compound.getAllKeys())) {
                Tag child = compound.get(key);
                if (child instanceof StringTag) {
                    compound.putString(
                            key, rewriteIdString(compound.getString(key), replacements));
                } else {
                    rewriteIdStrings(child, replacements);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int idx = 0; idx < list.size(); idx++) {
                Tag child = list.get(idx);
                if (child instanceof StringTag string) {
                    list.set(idx, StringTag.valueOf(
                            rewriteIdString(string.getAsString(), replacements)));
                } else {
                    rewriteIdStrings(child, replacements);
                }
            }
        }
    }

    // Rewrite the id string
    private static String rewriteIdString(
            String val,
            List<IdentifierReplacement> replacements) {
        String rewritten = val == null ? "" : val;
        for (IdentifierReplacement replacement : replacements) {
            rewritten = rewritten.replace(
                    "sublevel:" + replacement.sourceUuid(),
                    "sublevel:" + replacement.placedUuid());
            if (replacement.sourcePos() != null && replacement.placedPos() != null) {
                rewritten = rewritten.replace(
                        "@" + replacement.sourcePos().asLong() + "#"
                                + replacement.sourceUuid(),
                        "@" + replacement.placedPos().asLong() + "#"
                                + replacement.placedUuid());
            }
        }
        return rewritten;
    }

    // Clear the ctrl manifest metadata
    private static void clearCtrlManifestMetadata(CompoundTag tag) {
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID);
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_REVISION);
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_HASH);
        tag.remove(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_STORAGE_VERSION);
        tag.remove("ShipControlMapId");
    }

    // Get the make ship control map portable
    private static CompoundTag makeShipControlMapPortable(
            Object ctx,
            CompoundTag src)
            throws ReflectiveOperationException {
        // -----------------------------------------------------SOURCE MAP-----------------------------------------------------
        ShipControlMap map = ShipControlMapSchematicCodec.read(src);
        if (map == null) {
            return null;
        }

        Map<UUID, Integer> frames = new HashMap<>();
        ShipReference root =
                captureShipReference(ctx, map.rootSubLevelId(), map.controllerPosition(), frames);
        Vec3 centerOfMass =
                captureShipPosition(ctx, map.rootSubLevelId(), map.centerOfMass(), frames);
        if (root == null || centerOfMass == null) {
            return null;
        }

        // ------------------------------------PROPULSION UNITS------------------------------------
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        for (ShipControlMap.PropulsionUnit unit : map.units()) {
            ShipReference reference =
                    captureShipReference(ctx, unit.subLevelId(), unit.blockPosition(), frames);
            Vec3 rootPosition =
                    captureShipPosition(ctx, map.rootSubLevelId(), unit.rootPosition(), frames);
            if (reference == null || rootPosition == null) {
                return null;
            }
            units.add(new ShipControlMap.PropulsionUnit(
                    unit.index(),
                    reference.subLevelId(),
                    reference.blockPos(),
                    unit.blockId(),
                    unit.adapter(),
                    unit.controllable(),
                    rootPosition,
                    unit.forceDirection(),
                    unit.minControl(),
                    unit.maxControl(),
                    unit.minThrust(),
                    unit.maxThrust(),
                    unit.maxSpeed(),
                    unit.samples()));
        }

        // -----------------------------------------------------BEARING UNITS-----------------------------------------------------
        List<ShipControlMap.BearingUnit> bearings = new ArrayList<>();
        for (ShipControlMap.BearingUnit bearing : map.bearings()) {
            ShipReference reference = captureShipReference(
                    ctx, bearing.hostSubLevelId(), bearing.blockPosition(), frames);
            if (reference == null) {
                return null;
            }
            List<UUID> children = new ArrayList<>();
            for (UUID childId : bearing.childSubLevelIds()) {
                if (captureShipFrame(ctx, childId, frames) < 0) {
                    return null;
                }
                children.add(childId);
            }
            List<ShipControlMap.BearingPose> poses = new ArrayList<>();
            for (ShipControlMap.BearingPose pose : bearing.poses()) {
                ShipControlMap.BearingPose portablePose =
                        captureShipPose(ctx, map.rootSubLevelId(), pose, frames);
                if (portablePose == null) {
                    return null;
                }
                poses.add(portablePose);
            }
            bearings.add(new ShipControlMap.BearingUnit(
                    bearing.index(),
                    reference.subLevelId(),
                    reference.blockPos(),
                    bearing.blockId(),
                    bearing.adapter(),
                    children,
                    bearing.minX(),
                    bearing.maxX(),
                    bearing.minZ(),
                    bearing.maxZ(),
                    poses));
        }

        // -----------------------------------------------------PORTABLE MAP-----------------------------------------------------
        ShipControlMap portable = new ShipControlMap(
                map.id(),
                map.dimension(),
                root.subLevelId(),
                root.blockPos(),
                centerOfMass,
                units,
                bearings,
                map.updatedAt());
        CompoundTag portableTag = ShipControlMapSchematicCodec.write(portable);
        writeShipMapFrames(portableTag, frames);
        return portableTag;
    }

    // Capture the ship pose
    private static ShipControlMap.BearingPose captureShipPose(
            Object ctx,
            UUID rootSubLevelId,
            ShipControlMap.BearingPose pose,
            Map<UUID, Integer> frames)
            throws ReflectiveOperationException {
        List<ShipControlMap.BearingResponse> responses = new ArrayList<>();
        for (ShipControlMap.BearingResponse resp : pose.responses()) {
            Vec3 rootPosition =
                    captureShipPosition(ctx, rootSubLevelId, resp.rootPosition(), frames);
            if (rootPosition == null) {
                return null;
            }
            responses.add(new ShipControlMap.BearingResponse(
                    resp.propulsionUnitIndex(),
                    rootPosition,
                    resp.forceDirection(),
                    resp.maxThrust()));
        }

        List<ShipControlMap.AerodynamicSurface> surfaces = new ArrayList<>();
        for (ShipControlMap.AerodynamicSurface surface : pose.aerodynamicSurfaces()) {
            ShipReference reference = captureShipReference(
                    ctx, surface.subLevelId(), surface.blockPosition(), frames);
            Vec3 rootPosition =
                    captureShipPosition(ctx, rootSubLevelId, surface.rootPosition(), frames);
            if (reference == null || rootPosition == null) {
                return null;
            }
            surfaces.add(new ShipControlMap.AerodynamicSurface(
                    surface.surfaceIndex(),
                    reference.subLevelId(),
                    reference.blockPos(),
                    surface.blockId(),
                    rootPosition,
                    surface.normal(),
                    surface.parallelDragScalar(),
                    surface.directionlessDragScalar(),
                    surface.liftScalar()));
        }
        return new ShipControlMap.BearingPose(
                pose.angleX(),
                pose.angleZ(),
                responses,
                surfaces,
                pose.maxAerodynamicForce(),
                pose.maxAerodynamicTorque());
    }

    // Place the ship control map
    private static CompoundTag placeShipControlMap(
            Object ctx,
            CompoundTag src)
            throws ReflectiveOperationException {
        ShipControlMap map = ShipControlMapSchematicCodec.read(src);
        Map<UUID, Integer> frames = readShipMapFrames(src);
        if (map == null || frames.isEmpty()) {
            return null;
        }

        ShipReference root =
                placeShipReference(ctx, map.rootSubLevelId(), map.controllerPosition(), frames);
        Vec3 centerOfMass =
                placeShipPosition(ctx, map.rootSubLevelId(), map.centerOfMass(), frames);
        if (root == null || centerOfMass == null) {
            return null;
        }

        // ------------------------------------PROPULSION UNITS------------------------------------
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        for (ShipControlMap.PropulsionUnit unit : map.units()) {
            ShipReference reference =
                    placeShipReference(ctx, unit.subLevelId(), unit.blockPosition(), frames);
            Vec3 rootPosition =
                    placeShipPosition(ctx, map.rootSubLevelId(), unit.rootPosition(), frames);
            if (reference == null || rootPosition == null) {
                return null;
            }
            units.add(new ShipControlMap.PropulsionUnit(
                    unit.index(),
                    reference.subLevelId(),
                    reference.blockPos(),
                    unit.blockId(),
                    unit.adapter(),
                    unit.controllable(),
                    rootPosition,
                    unit.forceDirection(),
                    unit.minControl(),
                    unit.maxControl(),
                    unit.minThrust(),
                    unit.maxThrust(),
                    unit.maxSpeed(),
                    unit.samples()));
        }

        // -----------------------------------------------------BEARING UNITS-----------------------------------------------------
        List<ShipControlMap.BearingUnit> bearings = new ArrayList<>();
        for (ShipControlMap.BearingUnit bearing : map.bearings()) {
            ShipReference reference = placeShipReference(
                    ctx, bearing.hostSubLevelId(), bearing.blockPosition(), frames);
            if (reference == null) {
                return null;
            }
            List<UUID> children = new ArrayList<>();
            for (UUID childId : bearing.childSubLevelIds()) {
                if (!frames.containsKey(childId)) {
                    return null;
                }
                UUID placedChild = mapPlacedSubLevel(ctx, childId);
                if (placedChild == null) {
                    return null;
                }
                children.add(placedChild);
            }
            List<ShipControlMap.BearingPose> poses = new ArrayList<>();
            for (ShipControlMap.BearingPose pose : bearing.poses()) {
                ShipControlMap.BearingPose placedPose =
                        placeShipPose(ctx, map.rootSubLevelId(), pose, frames);
                if (placedPose == null) {
                    return null;
                }
                poses.add(placedPose);
            }
            bearings.add(new ShipControlMap.BearingUnit(
                    bearing.index(),
                    reference.subLevelId(),
                    reference.blockPos(),
                    bearing.blockId(),
                    bearing.adapter(),
                    children,
                    bearing.minX(),
                    bearing.maxX(),
                    bearing.minZ(),
                    bearing.maxZ(),
                    poses));
        }

        // -----------------------------------------------------PLACED MAP-----------------------------------------------------
        UUID placedRootId = (UUID) invokeNoArgs(ctx, "placedSubLevelUuid");
        BlockPos placedControllerPos = (BlockPos) invokeNoArgs(ctx, "storagePos");
        if (placedRootId == null || placedControllerPos == null) {
            placedRootId = root.subLevelId();
            placedControllerPos = root.blockPos();
        }
        ShipControlMap placed = new ShipControlMap(
                UUID.randomUUID(),
                map.dimension(),
                placedRootId,
                placedControllerPos,
                centerOfMass,
                units,
                bearings,
                System.currentTimeMillis());
        return ShipControlMapSchematicCodec.write(placed);
    }

    // Place the ship pose
    private static ShipControlMap.BearingPose placeShipPose(
            Object ctx,
            UUID sourceRootSubLevelId,
            ShipControlMap.BearingPose pose,
            Map<UUID, Integer> frames)
            throws ReflectiveOperationException {
        List<ShipControlMap.BearingResponse> responses = new ArrayList<>();
        for (ShipControlMap.BearingResponse resp : pose.responses()) {
            Vec3 rootPosition =
                    placeShipPosition(ctx, sourceRootSubLevelId, resp.rootPosition(), frames);
            if (rootPosition == null) {
                return null;
            }
            responses.add(new ShipControlMap.BearingResponse(
                    resp.propulsionUnitIndex(),
                    rootPosition,
                    resp.forceDirection(),
                    resp.maxThrust()));
        }

        List<ShipControlMap.AerodynamicSurface> surfaces = new ArrayList<>();
        for (ShipControlMap.AerodynamicSurface surface : pose.aerodynamicSurfaces()) {
            ShipReference reference = placeShipReference(
                    ctx, surface.subLevelId(), surface.blockPosition(), frames);
            Vec3 rootPosition =
                    placeShipPosition(ctx, sourceRootSubLevelId, surface.rootPosition(), frames);
            if (reference == null || rootPosition == null) {
                return null;
            }
            surfaces.add(new ShipControlMap.AerodynamicSurface(
                    surface.surfaceIndex(),
                    reference.subLevelId(),
                    reference.blockPos(),
                    surface.blockId(),
                    rootPosition,
                    surface.normal(),
                    surface.parallelDragScalar(),
                    surface.directionlessDragScalar(),
                    surface.liftScalar()));
        }
        return new ShipControlMap.BearingPose(
                pose.angleX(),
                pose.angleZ(),
                responses,
                surfaces,
                pose.maxAerodynamicForce(),
                pose.maxAerodynamicTorque());
    }

    // Capture the ship reference
    private static ShipReference captureShipReference(
            Object ctx,
            UUID sourceUuid,
            BlockPos sourcePos,
            Map<UUID, Integer> frames)
            throws ReflectiveOperationException {
        int blueprintId = captureShipFrame(ctx, sourceUuid, frames);
        if (blueprintId < 0 || sourcePos == null) {
            return null;
        }
        Optional<?> blockRef = invokeOptional(ctx, "blockRef", BlockPos.class, sourcePos);
        if (blockRef.isEmpty() || blueprintId != invokeInt(blockRef.get(), "subLevelId")) {
            return null;
        }
        BlockPos localPos = (BlockPos) invokeNoArgs(blockRef.get(), "localPos");
        return localPos == null
                ? null
                : new ShipReference(sourceUuid, localPos.immutable());
    }

    // Capture the ship position
    private static Vec3 captureShipPosition(
            Object ctx,
            UUID sourceUuid,
            Vec3 sourcePosition,
            Map<UUID, Integer> frames)
            throws ReflectiveOperationException {
        if (sourcePosition == null) {
            return null;
        }
        BlockPos sourceFloor = BlockPos.containing(sourcePosition);
        ShipReference floor =
                captureShipReference(ctx, sourceUuid, sourceFloor, frames);
        if (floor == null) {
            return null;
        }
        return new Vec3(
                floor.blockPos().getX() + sourcePosition.x - sourceFloor.getX(),
                floor.blockPos().getY() + sourcePosition.y - sourceFloor.getY(),
                floor.blockPos().getZ() + sourcePosition.z - sourceFloor.getZ());
    }

    // Capture the ship frame
    private static int captureShipFrame(
            Object ctx,
            UUID sourceUuid,
            Map<UUID, Integer> frames)
            throws ReflectiveOperationException {
        if (sourceUuid == null) {
            return -1;
        }
        Integer existing = frames.get(sourceUuid);
        if (existing != null) {
            return existing;
        }
        Optional<?> subLevelRef = invokeOptional(ctx, "subLevelRef", UUID.class, sourceUuid);
        if (subLevelRef.isEmpty()) {
            return -1;
        }
        int blueprintId = invokeInt(subLevelRef.get(), "subLevelId");
        if (blueprintId < 0) {
            return -1;
        }
        frames.put(sourceUuid, blueprintId);
        return blueprintId;
    }

    // Place the ship reference
    private static ShipReference placeShipReference(
            Object ctx,
            UUID sourceUuid,
            BlockPos localPos,
            Map<UUID, Integer> frames)
            throws ReflectiveOperationException {
        Integer blueprintId = frames.get(sourceUuid);
        UUID placedUuid = mapPlacedSubLevel(ctx, sourceUuid);
        BlockPos placedPos =
                blueprintId == null ? null : mapPlacedPosition(ctx, blueprintId, localPos);
        return placedUuid == null || placedPos == null
                ? null
                : new ShipReference(placedUuid, placedPos);
    }

    // Place the ship position
    private static Vec3 placeShipPosition(
            Object ctx,
            UUID sourceUuid,
            Vec3 localPosition,
            Map<UUID, Integer> frames)
            throws ReflectiveOperationException {
        Integer blueprintId = frames.get(sourceUuid);
        if (blueprintId == null || localPosition == null) {
            return null;
        }
        BlockPos origin = placedBlockOrigin(ctx, blueprintId);
        return origin == null
                ? null
                : localPosition.add(origin.getX(), origin.getY(), origin.getZ());
    }

    // Write the ship map frames
    private static void writeShipMapFrames(
            CompoundTag mapTag,
            Map<UUID, Integer> frames) {
        CompoundTag metadata = new CompoundTag();
        ListTag frameTags = new ListTag();
        for (Map.Entry<UUID, Integer> entry : frames.entrySet()) {
            CompoundTag frame = new CompoundTag();
            frame.putUUID(REF_SOURCE_UUID, entry.getKey());
            frame.putInt(SHIP_MAP_FRAME_ID, entry.getValue());
            frameTags.add(frame);
        }
        metadata.put(SHIP_MAP_FRAMES, frameTags);
        mapTag.put(PORTABLE_SHIP_MAP, metadata);
    }

    // Read the ship map frames
    private static Map<UUID, Integer> readShipMapFrames(CompoundTag mapTag) {
        Map<UUID, Integer> frames = new HashMap<>();
        if (!mapTag.contains(PORTABLE_SHIP_MAP, Tag.TAG_COMPOUND)) {
            return frames;
        }
        ListTag frameTags = mapTag.getCompound(PORTABLE_SHIP_MAP)
                .getList(SHIP_MAP_FRAMES, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < frameTags.size(); idx++) {
            CompoundTag frame = frameTags.getCompound(idx);
            if (frame.hasUUID(REF_SOURCE_UUID)
                    && frame.contains(SHIP_MAP_FRAME_ID, Tag.TAG_INT)) {
                frames.put(frame.getUUID(REF_SOURCE_UUID), frame.getInt(SHIP_MAP_FRAME_ID));
            }
        }
        return frames;
    }

    // Save the aileron head
    private static void saveAileronHead(Object ctx, CompoundTag tag, String headKey)
            throws ReflectiveOperationException {
        if (!tag.contains(headKey, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag headTag = tag.getCompound(headKey).copy();
        captureConnection(
                ctx, headTag, MOUNTED_SUB_LEVEL, MOUNTED_LOCAL_POS, PositionEncoding.PACKED_LONG);
        headTag.putBoolean(MOUNTED_ASSEMBLY_PRESENT, false);
        tag.put(headKey, headTag);
    }

    // Place the aileron head
    private static void placeAileronHead(Object ctx, CompoundTag tag, String headKey)
            throws ReflectiveOperationException {
        if (!tag.contains(headKey, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag headTag = tag.getCompound(headKey).copy();
        boolean restored = restoreConnection(
                ctx, headTag, MOUNTED_SUB_LEVEL, MOUNTED_LOCAL_POS, PositionEncoding.PACKED_LONG);
        headTag.putBoolean(MOUNTED_ASSEMBLY_PRESENT, restored);
        tag.put(headKey, headTag);
    }

    // Save the thruster link
    private static void saveThrusterLink(Object ctx, CompoundTag tag)
            throws ReflectiveOperationException {
        UUID sourceUuid = tag.hasUUID(THRUSTER_PARENT_SUB_LEVEL_ID)
                ? tag.getUUID(THRUSTER_PARENT_SUB_LEVEL_ID)
                : readUuid(tag, PARENT_SUB_LEVEL_ID);
        BlockPos sourcePos = tag.contains(THRUSTER_PARENT_POS)
                ? readNbtPos(tag, THRUSTER_PARENT_POS)
                : readNbtPos(tag, PARENT_POS);
        clearThrusterParent(tag);
        captureConnection(ctx, tag, sourceUuid, sourcePos);
    }

    // Place the thruster link
    private static void placeThrusterLink(Object ctx, CompoundTag tag)
            throws ReflectiveOperationException {
        PortableReference reference = readPortableReference(tag);
        clearThrusterParent(tag);
        tag.remove(PORTABLE_REF);
        PlacedReference placed = mapPlacedReference(ctx, reference);
        if (placed == null) {
            return;
        }
        Tag positionTag = NbtUtils.writeBlockPos(placed.position());
        tag.put(PARENT_POS, positionTag.copy());
        tag.put(THRUSTER_PARENT_POS, positionTag);
        tag.putUUID(PARENT_SUB_LEVEL_ID, placed.subLevelId());
        tag.putUUID(THRUSTER_PARENT_SUB_LEVEL_ID, placed.subLevelId());
    }

    // Capture the connection
    private static boolean captureConnection(Object ctx, CompoundTag tag,
                                             String uuidKey, String positionKey,
                                             PositionEncoding positionEncoding)
            throws ReflectiveOperationException {
        UUID sourceUuid = readUuid(tag, uuidKey);
        BlockPos sourcePos = readPosition(tag, positionKey, positionEncoding);
        tag.remove(uuidKey);
        tag.remove(positionKey);
        tag.remove(PORTABLE_REF);
        return captureConnection(ctx, tag, sourceUuid, sourcePos);
    }

    // Capture the connection
    private static boolean captureConnection(Object ctx, CompoundTag tag,
                                             UUID sourceUuid, BlockPos sourcePos)
            throws ReflectiveOperationException {
        tag.remove(PORTABLE_REF);
        if (sourceUuid == null || sourcePos == null) {
            return false;
        }
        Optional<?> subLevelRef = invokeOptional(ctx, "subLevelRef", UUID.class, sourceUuid);
        Optional<?> blockRef = invokeOptional(ctx, "blockRef", BlockPos.class, sourcePos);
        if (subLevelRef.isEmpty() || blockRef.isEmpty()) {
            return false;
        }
        int referencedSubLevel = invokeInt(subLevelRef.get(), "subLevelId");
        int blockSubLevel = invokeInt(blockRef.get(), "subLevelId");
        BlockPos localPos = (BlockPos) invokeNoArgs(blockRef.get(), "localPos");
        if (referencedSubLevel != blockSubLevel || localPos == null) {
            return false;
        }
        writePortableReference(tag,
                new PortableReference(blockSubLevel, localPos.immutable(), sourceUuid));
        return true;
    }

    // Restore the connection
    private static boolean restoreConnection(Object ctx, CompoundTag tag,
                                             String uuidKey, String positionKey,
                                             PositionEncoding positionEncoding)
            throws ReflectiveOperationException {
        PortableReference reference = readPortableReference(tag);
        tag.remove(PORTABLE_REF);
        tag.remove(uuidKey);
        tag.remove(positionKey);
        PlacedReference placed = mapPlacedReference(ctx, reference);
        if (placed == null) {
            return false;
        }
        tag.putUUID(uuidKey, placed.subLevelId());
        writePosition(tag, positionKey, placed.position(), positionEncoding);
        return true;
    }

    // Map the placed reference
    private static PlacedReference mapPlacedReference(Object ctx, PortableReference reference)
            throws ReflectiveOperationException {
        if (reference == null) {
            return null;
        }
        UUID placedSubLevelId = mapPlacedSubLevel(ctx, reference.sourceUuid());
        BlockPos placedPos =
                mapPlacedPosition(ctx, reference.blueprintSubLevelId(), reference.localPos());
        if (placedSubLevelId == null || placedPos == null) {
            return null;
        }
        return new PlacedReference(placedSubLevelId, placedPos);
    }

    // Map the placed sublevel
    private static UUID mapPlacedSubLevel(Object ctx, UUID sourceUuid)
            throws ReflectiveOperationException {
        if (sourceUuid == null) {
            return null;
        }
        Method mapSubLevel = ctx.getClass().getMethod("mapSubLevel", UUID.class);
        Object val = mapSubLevel.invoke(ctx, sourceUuid);
        return val instanceof UUID uuid ? uuid : null;
    }

    // Map the placed position
    private static BlockPos mapPlacedPosition(
            Object ctx,
            int blueprintSubLevelId,
            BlockPos localPos)
            throws ReflectiveOperationException {
        if (blueprintSubLevelId < 0 || localPos == null) {
            return null;
        }
        BlockPos origin = placedBlockOrigin(ctx, blueprintSubLevelId);
        return origin == null ? null : origin.offset(localPos);
    }

    // Get the placed block origin
    private static BlockPos placedBlockOrigin(Object ctx, int blueprintSubLevelId)
            throws ReflectiveOperationException {
        Object session = invokeNoArgs(ctx, "session");
        Method placedBlockOrigin = session.getClass().getMethod("placedBlockOrigin", int.class);
        Object val = placedBlockOrigin.invoke(session, blueprintSubLevelId);
        return val instanceof BlockPos pos ? pos : null;
    }

    // Write the portable reference
    static void writePortableReference(CompoundTag tag, PortableReference reference) {
        CompoundTag referenceTag = new CompoundTag();
        referenceTag.putInt(REF_SUB_LEVEL_ID, reference.blueprintSubLevelId());
        referenceTag.put(REF_LOCAL_POS, NbtUtils.writeBlockPos(reference.localPos()));
        referenceTag.putUUID(REF_SOURCE_UUID, reference.sourceUuid());
        tag.put(PORTABLE_REF, referenceTag);
    }

    // Read the portable reference
    static PortableReference readPortableReference(CompoundTag tag) {
        if (!tag.contains(PORTABLE_REF, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag referenceTag = tag.getCompound(PORTABLE_REF);
        if (!referenceTag.contains(REF_SUB_LEVEL_ID, Tag.TAG_INT)
                || !referenceTag.hasUUID(REF_SOURCE_UUID)
                || !referenceTag.contains(REF_LOCAL_POS)) {
            return null;
        }
        BlockPos localPos = readNbtPos(referenceTag, REF_LOCAL_POS);
        if (localPos == null) {
            return null;
        }
        return new PortableReference(
                referenceTag.getInt(REF_SUB_LEVEL_ID),
                localPos,
                referenceTag.getUUID(REF_SOURCE_UUID));
    }

    // Run the optional
    private static Optional<?> invokeOptional(Object target, String methodName,
                                              Class<?> parameterType, Object argument)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, parameterType);
        Object val = method.invoke(target, argument);
        return val instanceof Optional<?> optional ? optional : Optional.empty();
    }

    // Invoke a method without arguments
    private static Object invokeNoArgs(Object target, String methodName)
            throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    // Run the int
    private static int invokeInt(Object target, String methodName)
            throws ReflectiveOperationException {
        Object val = invokeNoArgs(target, methodName);
        return val instanceof Number num ? num.intValue() : -1;
    }

    // Read the UUID
    private static UUID readUuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }

    // Read the position
    private static BlockPos readPosition(CompoundTag tag, String key, PositionEncoding encoding) {
        return encoding == PositionEncoding.PACKED_LONG
                ? (tag.contains(key, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(key)) : null)
                : readNbtPos(tag, key);
    }

    // Read the NBT pos
    private static BlockPos readNbtPos(CompoundTag tag, String key) {
        return tag.contains(key)
                ? NbtUtils.readBlockPos(tag, key).orElse(null)
                : null;
    }

    // Write the position
    private static void writePosition(CompoundTag tag, String key, BlockPos pos,
                                      PositionEncoding encoding) {
        if (encoding == PositionEncoding.PACKED_LONG) {
            tag.putLong(key, pos.asLong());
        } else {
            tag.put(key, NbtUtils.writeBlockPos(pos));
        }
    }

    // Clear the thruster parent
    private static void clearThrusterParent(CompoundTag tag) {
        tag.remove(PARENT_POS);
        tag.remove("parent");
        tag.remove(PARENT_SUB_LEVEL_ID);
        tag.remove(THRUSTER_PARENT_POS);
        tag.remove(THRUSTER_PARENT_SUB_LEVEL_ID);
    }

    // Schedule the post placement repair
    private static void schedulePostPlacementRepair(BlockEntity blockEntity) {
        ServerLevel level = SableLevelApi.serverLevel(blockEntity.getLevel());
        if (level == null) {
            return;
        }
        BlockPos pos = blockEntity.getBlockPos().immutable();
        level.getServer().execute(() -> {
            BlockEntity placed = level.getBlockEntity(pos);
            if (placed instanceof AileronBearingLinkBlockEntity link) {
                link.finishAssemblyTransfer();
            } else if (placed instanceof VectorBearingLinkBlockEntity link) {
                link.finishAssemblyTransfer();
            } else if (placed instanceof ThrusterBearingLinkBlockEntity link) {
                link.fixParentLinkingWhenMoved();
            } else if (placed instanceof ThrusterBearingBlockEntity bearing
                    && bearing.getSubLevelID() == null
                    && bearing.isAssembled()) {
                bearing.disassemble();
            }
        });
    }

    // Define the NBT kind values
    enum NbtKind {
        AILERON_BEARING,
        AILERON_LINK,
        VECTOR_BEARING,
        VECTOR_LINK,
        THRUSTER_BEARING,
        THRUSTER_LINK,
        CONTROLLER,
        SHIP_DOCK,
        CONFIGURED_BLOCK
    }

    // Define the position encoding values
    enum PositionEncoding {
        PACKED_LONG,
        COMPOUND
    }

    // Store the portable reference
    record PortableReference(int blueprintSubLevelId, BlockPos localPos, UUID sourceUuid) {
    }

    // Store the placed reference
    record PlacedReference(UUID subLevelId, BlockPos position) {
    }

    // Store the ship reference
    private record ShipReference(UUID subLevelId, BlockPos blockPos) {
    }

    // Store the identifier replacement
    private record IdentifierReplacement(
            UUID sourceUuid,
            BlockPos sourcePos,
            UUID placedUuid,
            BlockPos placedPos) {
    }

    // Store the blueprint mapper handler
    private record BlueprintMapperHandler(NbtKind kind) implements InvocationHandler {
        // Run the blueprint mapper handler
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) {
                return switch (name) {
                    case "toString" -> "Create Thrusters Photomancy blueprint mapper (" + kind + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }

            try {
                return switch (name) {
                    case "save" -> args[1] instanceof CompoundTag tag
                            ? saveTag(kind, args[0], tag)
                            : null;
                    case "beforeLoadBlockEntity" -> {
                        prepareTagForPlacement(kind, args[0], (CompoundTag) args[1]);
                        yield null;
                    }
                    case "afterLoadBlockEntity" -> {
                        schedulePostPlacementRepair((BlockEntity) args[1]);
                        yield null;
                    }
                    default -> null;
                };
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                LOGGER.warn("Photomancy blueprint mapper {} failed during {}", kind, name, error);
                if ("save".equals(name) && args != null && args.length > 1
                        && args[1] instanceof CompoundTag original) {
                    CompoundTag fallback = original.copy();
                    if (kind == NbtKind.CONTROLLER) {
                        clearCtrlManifestMetadata(fallback);
                        fallback.remove(ControllerSchematicPayload.BLOCK_ENTITY_TAG);
                    }
                    return fallback;
                }
                return null;
            }
        }
    }
}
