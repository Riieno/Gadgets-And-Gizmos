package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SchematicSubLevelReferenceRemapper;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Move SCM map data through schematics while remapping every ship-local id and position
final class ShipControlMapSchematicCodec {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int STORAGE_VERSION = 1;
    private static final String VERSION_TAG = "Version";
    private static final String MAP_ID_TAG = "MapId";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String ROOT_TAG = "Root";
    private static final String CENTER_OF_MASS_TAG = "CenterOfMass";
    private static final String UNITS_TAG = "Units";
    private static final String BEARINGS_TAG = "Bearings";
    private static final String DOCKING_CONNECTORS_TAG = "DockingConnectors";
    private static final String CRN_DISPLAYS_TAG = "CrnDisplays";
    private static final String ACC_DISPLAYS_TAG = "AccDisplays";
    private static final String SEATS_TAG = "Seats";
    private static final String UPDATED_AT_TAG = "UpdatedAt";
    private static final String REFERENCE_TAG = "Reference";
    private static final String SUB_LEVEL_ID_TAG = "SubLevelId";
    private static final String BLOCK_POS_TAG = "BlockPos";
    private static final String ROOT_POSITION_TAG = "RootPosition";
    private static final String FORCE_DIRECTION_TAG = "ForceDirection";
    private static final String CHILDREN_TAG = "Children";
    private static final String POSES_TAG = "Poses";
    private static final String RESPONSES_TAG = "Responses";
    private static final String SURFACES_TAG = "Surfaces";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ship control map schematic codec
    private ShipControlMapSchematicCodec() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Write the ship control map schematic codec
    static CompoundTag write(ShipControlMap map) {
        // -----------------------------------------------------MAP HEADER-----------------------------------------------------
        CompoundTag tag = new CompoundTag();
        if (map == null) {
            return tag;
        }
        tag.putInt(VERSION_TAG, STORAGE_VERSION);
        tag.putUUID(MAP_ID_TAG, map.id());
        tag.putString(DIMENSION_TAG, map.dimension());
        tag.put(ROOT_TAG, reference(map.rootSubLevelId(), map.controllerPosition()));
        putVec3(tag, CENTER_OF_MASS_TAG, map.centerOfMass());
        tag.putLong(UPDATED_AT_TAG, map.updatedAt());

        // ------------------------------------PROPULSION UNITS------------------------------------
        ListTag units = new ListTag();
        for (ShipControlMap.PropulsionUnit unit : map.units()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Index", unit.index());
            entry.put(REFERENCE_TAG, reference(unit.subLevelId(), unit.blockPosition()));
            entry.putString("BlockId", unit.blockId());
            entry.putString("Adapter", unit.adapter());
            entry.putBoolean("Controllable", unit.controllable());
            putVec3(entry, ROOT_POSITION_TAG, unit.rootPosition());
            putVec3(entry, FORCE_DIRECTION_TAG, unit.forceDirection());
            entry.putDouble("MinControl", unit.minControl());
            entry.putDouble("MaxControl", unit.maxControl());
            entry.putDouble("MinThrust", unit.minThrust());
            entry.putDouble("MaxThrust", unit.maxThrust());
            entry.putDouble("MaxSpeed", unit.maxSpeed());
            ListTag samples = new ListTag();
            for (ShipControlMap.CalibrationSample sample : unit.samples()) {
                CompoundTag sampleTag = new CompoundTag();
                sampleTag.putDouble("MinControl", sample.minControl());
                sampleTag.putDouble("MaxControl", sample.maxControl());
                sampleTag.putDouble("Control", sample.control());
                sampleTag.putDouble("Speed", sample.speed());
                sampleTag.putDouble("Thrust", sample.thrust());
                sampleTag.putBoolean("Active", sample.active());
                samples.add(sampleTag);
            }
            entry.put("Samples", samples);
            units.add(entry);
        }
        tag.put(UNITS_TAG, units);

        // -----------------------------------------------------BEARING UNITS-----------------------------------------------------
        ListTag bearings = new ListTag();
        for (ShipControlMap.BearingUnit bearing : map.bearings()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Index", bearing.index());
            entry.put(REFERENCE_TAG, reference(
                    bearing.hostSubLevelId(), bearing.blockPosition()));
            entry.putString("BlockId", bearing.blockId());
            entry.putString("Adapter", bearing.adapter());
            ListTag children = new ListTag();
            for (UUID childId : bearing.childSubLevelIds()) {
                CompoundTag child = new CompoundTag();
                child.putUUID(SUB_LEVEL_ID_TAG, childId);
                children.add(child);
            }
            entry.put(CHILDREN_TAG, children);
            entry.putDouble("MinX", bearing.minX());
            entry.putDouble("MaxX", bearing.maxX());
            entry.putDouble("MinZ", bearing.minZ());
            entry.putDouble("MaxZ", bearing.maxZ());
            ListTag poses = new ListTag();
            for (ShipControlMap.BearingPose pose : bearing.poses()) {
                poses.add(writePose(pose));
            }
            entry.put(POSES_TAG, poses);
            bearings.add(entry);
        }
        tag.put(BEARINGS_TAG, bearings);

        // ------------------------------------DOCKING CONNECTORS------------------------------------
        ListTag dockingConnectors = new ListTag();
        for (ShipControlMap.DockingConnector connector : map.dockingConnectors()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Index", connector.index());
            entry.put(REFERENCE_TAG, reference(
                    connector.subLevelId(), connector.blockPosition()));
            putVec3(entry, "RootTipPosition", connector.rootTipPosition());
            putVec3(entry, "RootFacing", connector.rootFacing());
            dockingConnectors.add(entry);
        }
        tag.put(DOCKING_CONNECTORS_TAG, dockingConnectors);

        // ------------------------------------DISPLAY TARGETS------------------------------------
        ListTag crnDisplays = new ListTag();
        for (ShipControlMap.CrnDisplay display : map.crnDisplays()) {
            CompoundTag entry = new CompoundTag();
            entry.put(REFERENCE_TAG, reference(display.subLevelId(), display.blockPosition()));
            crnDisplays.add(entry);
        }
        tag.put(CRN_DISPLAYS_TAG, crnDisplays);

        ListTag accDisplays = new ListTag();
        for (ShipControlMap.AccDisplay display : map.accDisplays()) {
            CompoundTag entry = new CompoundTag();
            entry.put(REFERENCE_TAG, reference(display.subLevelId(), display.blockPosition()));
            accDisplays.add(entry);
        }
        tag.put(ACC_DISPLAYS_TAG, accDisplays);

        ListTag seats = new ListTag();
        for (ShipControlMap.Seat seat : map.seats()) {
            CompoundTag entry = new CompoundTag();
            entry.put(REFERENCE_TAG, reference(seat.subLevelId(), seat.blockPosition()));
            seats.add(entry);
        }
        tag.put(SEATS_TAG, seats);
        return tag;
    }

    // Read the ship control map schematic codec
    static @Nullable ShipControlMap read(CompoundTag tag) {
        // -----------------------------------------------------MAP HEADER-----------------------------------------------------
        if (tag == null || tag.getInt(VERSION_TAG) != STORAGE_VERSION
                || !tag.hasUUID(MAP_ID_TAG)) {
            return null;
        }
        Reference root = readReference(tag.getCompound(ROOT_TAG));
        if (root == null) {
            return null;
        }

        // ------------------------------------PROPULSION UNITS------------------------------------
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        ListTag unitTags = tag.getList(UNITS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < unitTags.size(); idx++) {
            CompoundTag entry = unitTags.getCompound(idx);
            Reference reference = readReference(entry.getCompound(REFERENCE_TAG));
            if (reference == null) {
                return null;
            }
            List<ShipControlMap.CalibrationSample> samples = new ArrayList<>();
            ListTag sampleTags = entry.getList("Samples", Tag.TAG_COMPOUND);
            for (int sampleIndex = 0; sampleIndex < sampleTags.size(); sampleIndex++) {
                CompoundTag sample = sampleTags.getCompound(sampleIndex);
                samples.add(new ShipControlMap.CalibrationSample(
                        sample.getDouble("MinControl"),
                        sample.getDouble("MaxControl"),
                        sample.getDouble("Control"),
                        sample.getDouble("Speed"),
                        sample.getDouble("Thrust"),
                        sample.getBoolean("Active")));
            }
            units.add(new ShipControlMap.PropulsionUnit(
                    entry.getInt("Index"),
                    reference.subLevelId(),
                    reference.blockPos(),
                    entry.getString("BlockId"),
                    entry.getString("Adapter"),
                    entry.getBoolean("Controllable"),
                    readVec3(entry, ROOT_POSITION_TAG),
                    readVec3(entry, FORCE_DIRECTION_TAG),
                    entry.getDouble("MinControl"),
                    entry.getDouble("MaxControl"),
                    entry.getDouble("MinThrust"),
                    entry.getDouble("MaxThrust"),
                    entry.getDouble("MaxSpeed"),
                    samples));
        }

        // -----------------------------------------------------BEARING UNITS-----------------------------------------------------
        List<ShipControlMap.BearingUnit> bearings = new ArrayList<>();
        ListTag bearingTags = tag.getList(BEARINGS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < bearingTags.size(); idx++) {
            CompoundTag entry = bearingTags.getCompound(idx);
            Reference reference = readReference(entry.getCompound(REFERENCE_TAG));
            if (reference == null) {
                return null;
            }
            List<UUID> children = new ArrayList<>();
            ListTag childTags = entry.getList(CHILDREN_TAG, Tag.TAG_COMPOUND);
            for (int childIndex = 0; childIndex < childTags.size(); childIndex++) {
                CompoundTag child = childTags.getCompound(childIndex);
                if (!child.hasUUID(SUB_LEVEL_ID_TAG)) {
                    return null;
                }
                children.add(child.getUUID(SUB_LEVEL_ID_TAG));
            }
            List<ShipControlMap.BearingPose> poses = new ArrayList<>();
            ListTag poseTags = entry.getList(POSES_TAG, Tag.TAG_COMPOUND);
            for (int poseIndex = 0; poseIndex < poseTags.size(); poseIndex++) {
                poses.add(readPose(poseTags.getCompound(poseIndex)));
            }
            bearings.add(new ShipControlMap.BearingUnit(
                    entry.getInt("Index"),
                    reference.subLevelId(),
                    reference.blockPos(),
                    entry.getString("BlockId"),
                    entry.getString("Adapter"),
                    children,
                    entry.getDouble("MinX"),
                    entry.getDouble("MaxX"),
                    entry.getDouble("MinZ"),
                    entry.getDouble("MaxZ"),
                    poses));
        }

        // ------------------------------------DOCKING CONNECTORS------------------------------------
        List<ShipControlMap.DockingConnector> dockingConnectors = new ArrayList<>();
        ListTag connectorTags = tag.getList(DOCKING_CONNECTORS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < connectorTags.size(); idx++) {
            CompoundTag entry = connectorTags.getCompound(idx);
            Reference reference = readReference(entry.getCompound(REFERENCE_TAG));
            if (reference == null) {
                return null;
            }
            dockingConnectors.add(new ShipControlMap.DockingConnector(
                    entry.getInt("Index"), reference.subLevelId(), reference.blockPos(),
                    readVec3(entry, "RootTipPosition"), readVec3(entry, "RootFacing")));
        }

        // ------------------------------------DISPLAY TARGETS------------------------------------
        List<ShipControlMap.CrnDisplay> crnDisplays = new ArrayList<>();
        ListTag displayTags = tag.getList(CRN_DISPLAYS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < displayTags.size(); idx++) {
            Reference reference = readReference(
                    displayTags.getCompound(idx).getCompound(REFERENCE_TAG));
            if (reference == null) {
                return null;
            }
            crnDisplays.add(new ShipControlMap.CrnDisplay(
                    reference.subLevelId(), reference.blockPos()));
        }

        List<ShipControlMap.AccDisplay> accDisplays = new ArrayList<>();
        ListTag accDisplayTags = tag.getList(ACC_DISPLAYS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < accDisplayTags.size(); idx++) {
            Reference reference = readReference(
                    accDisplayTags.getCompound(idx).getCompound(REFERENCE_TAG));
            if (reference == null) {
                return null;
            }
            accDisplays.add(new ShipControlMap.AccDisplay(
                    reference.subLevelId(), reference.blockPos()));
        }

        List<ShipControlMap.Seat> seats = new ArrayList<>();
        ListTag seatTags = tag.getList(SEATS_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < seatTags.size(); idx++) {
            Reference reference = readReference(
                    seatTags.getCompound(idx).getCompound(REFERENCE_TAG));
            if (reference == null) {
                return null;
            }
            seats.add(new ShipControlMap.Seat(
                    reference.subLevelId(), reference.blockPos()));
        }

        return new ShipControlMap(
                tag.getUUID(MAP_ID_TAG),
                tag.getString(DIMENSION_TAG),
                root.subLevelId(),
                root.blockPos(),
                readVec3(tag, CENTER_OF_MASS_TAG),
                units,
                bearings,
                dockingConnectors,
                crnDisplays,
                accDisplays,
                seats,
                tag.getLong(UPDATED_AT_TAG));
    }

    // Remap the ship control map schematic codec
    static @Nullable CompoundTag remap(
            CompoundTag source,
            SubLevelSchematicSerializationContext ctx) {
        ShipControlMap map = read(source);
        if (map == null || ctx == null) {
            return map == null ? null : source.copy();
        }

        // ------------------------------------ROOT REFERENCE------------------------------------
        ReferenceRemap root = remapReference(
                ctx, map.rootSubLevelId(), map.controllerPosition());
        Vec3 rootCenter = remapRootPosition(
                ctx, map.rootSubLevelId(), map.centerOfMass());
        if (root == null || rootCenter == null) {
            return null;
        }

        // ------------------------------------PROPULSION UNITS------------------------------------
        List<ShipControlMap.PropulsionUnit> units = new ArrayList<>();
        for (ShipControlMap.PropulsionUnit unit : map.units()) {
            ReferenceRemap reference = remapReference(
                    ctx, unit.subLevelId(), unit.blockPosition());
            Vec3 rootPosition = remapRootPosition(
                    ctx, map.rootSubLevelId(), unit.rootPosition());
            if (reference == null || rootPosition == null) {
                return null;
            }
            units.add(new ShipControlMap.PropulsionUnit(
                    unit.index(), reference.subLevelId(), reference.blockPos(),
                    unit.blockId(), unit.adapter(), unit.controllable(),
                    rootPosition, unit.forceDirection(),
                    unit.minControl(), unit.maxControl(), unit.minThrust(),
                    unit.maxThrust(), unit.maxSpeed(), unit.samples()));
        }

        // -----------------------------------------------------BEARING UNITS-----------------------------------------------------
        List<ShipControlMap.BearingUnit> bearings = new ArrayList<>();
        for (ShipControlMap.BearingUnit bearing : map.bearings()) {
            ReferenceRemap reference = remapReference(
                    ctx, bearing.hostSubLevelId(), bearing.blockPosition());
            if (reference == null) {
                return null;
            }
            List<UUID> children = new ArrayList<>();
            for (UUID childId : bearing.childSubLevelIds()) {
                UUID remappedChild = remapSubLevelId(ctx, childId);
                if (remappedChild == null) {
                    return null;
                }
                children.add(remappedChild);
            }
            List<ShipControlMap.BearingPose> poses = new ArrayList<>();
            for (ShipControlMap.BearingPose pose : bearing.poses()) {
                ShipControlMap.BearingPose remappedPose =
                        remapPose(pose, map.rootSubLevelId(), ctx);
                if (remappedPose == null) {
                    return null;
                }
                poses.add(remappedPose);
            }
            bearings.add(new ShipControlMap.BearingUnit(
                    bearing.index(), reference.subLevelId(), reference.blockPos(),
                    bearing.blockId(), bearing.adapter(), children,
                    bearing.minX(), bearing.maxX(), bearing.minZ(), bearing.maxZ(), poses));
        }

        // ------------------------------------DOCKING / DISPLAYS------------------------------------
        List<ShipControlMap.DockingConnector> dockingConnectors = new ArrayList<>();
        for (ShipControlMap.DockingConnector connector : map.dockingConnectors()) {
            ReferenceRemap reference = remapReference(
                    ctx, connector.subLevelId(), connector.blockPosition());
            Vec3 rootTip = remapRootPosition(
                    ctx, map.rootSubLevelId(), connector.rootTipPosition());
            if (reference == null || rootTip == null) {
                return null;
            }
            dockingConnectors.add(new ShipControlMap.DockingConnector(
                    connector.index(), reference.subLevelId(), reference.blockPos(),
                    rootTip, connector.rootFacing()));
        }

        List<ShipControlMap.CrnDisplay> crnDisplays = new ArrayList<>();
        for (ShipControlMap.CrnDisplay display : map.crnDisplays()) {
            ReferenceRemap reference = remapReference(
                    ctx, display.subLevelId(), display.blockPosition());
            if (reference == null) {
                return null;
            }
            crnDisplays.add(new ShipControlMap.CrnDisplay(
                    reference.subLevelId(), reference.blockPos()));
        }


        List<ShipControlMap.AccDisplay> accDisplays = new ArrayList<>();
        for (ShipControlMap.AccDisplay display : map.accDisplays()) {
            ReferenceRemap reference = remapReference(
                    ctx, display.subLevelId(), display.blockPosition());
            if (reference == null) {
                return null;
            }
            accDisplays.add(new ShipControlMap.AccDisplay(
                    reference.subLevelId(), reference.blockPos()));
        }

        List<ShipControlMap.Seat> seats = new ArrayList<>();
        for (ShipControlMap.Seat seat : map.seats()) {
            ReferenceRemap reference = remapReference(
                    ctx, seat.subLevelId(), seat.blockPosition());
            if (reference == null) {
                return null;
            }
            seats.add(new ShipControlMap.Seat(
                    reference.subLevelId(), reference.blockPos()));
        }

        UUID mapId = ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE
                && root.changed() ? UUID.randomUUID() : map.id();
        long updatedAt = ctx.getType() == SubLevelSchematicSerializationContext.Type.PLACE
                && root.changed() ? System.currentTimeMillis() : map.updatedAt();
        // -----------------------------------------------------REBUILD MAP-----------------------------------------------------
        ShipControlMap remapped = new ShipControlMap(
                mapId, map.dimension(), root.subLevelId(), root.blockPos(), rootCenter,
                units, bearings, dockingConnectors, crnDisplays, accDisplays, seats, updatedAt);
        return write(remapped);
    }

    // Copy the ship control map schematic codec with the dimension
    static ShipControlMap withDimension(ShipControlMap map, String dimension) {
        return new ShipControlMap(
                map.id(), dimension, map.rootSubLevelId(), map.controllerPosition(),
                map.centerOfMass(), map.units(), map.bearings(),
                map.dockingConnectors(), map.crnDisplays(), map.accDisplays(),
                map.seats(), map.updatedAt());
    }

    // Write the pose
    private static CompoundTag writePose(ShipControlMap.BearingPose pose) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("AngleX", pose.angleX());
        tag.putDouble("AngleZ", pose.angleZ());
        ListTag responses = new ListTag();
        for (ShipControlMap.BearingResponse resp : pose.responses()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("PropulsionUnitIndex", resp.propulsionUnitIndex());
            putVec3(entry, ROOT_POSITION_TAG, resp.rootPosition());
            putVec3(entry, FORCE_DIRECTION_TAG, resp.forceDirection());
            entry.putDouble("MaxThrust", resp.maxThrust());
            responses.add(entry);
        }
        tag.put(RESPONSES_TAG, responses);
        ListTag surfaces = new ListTag();
        for (ShipControlMap.AerodynamicSurface surface : pose.aerodynamicSurfaces()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("SurfaceIndex", surface.surfaceIndex());
            entry.put(REFERENCE_TAG, reference(
                    surface.subLevelId(), surface.blockPosition()));
            entry.putString("BlockId", surface.blockId());
            putVec3(entry, ROOT_POSITION_TAG, surface.rootPosition());
            putVec3(entry, "Normal", surface.normal());
            entry.putDouble("ParallelDragScalar", surface.parallelDragScalar());
            entry.putDouble("DirectionlessDragScalar", surface.directionlessDragScalar());
            entry.putDouble("LiftScalar", surface.liftScalar());
            surfaces.add(entry);
        }
        tag.put(SURFACES_TAG, surfaces);
        tag.putDouble("MaxAerodynamicForce", pose.maxAerodynamicForce());
        tag.putDouble("MaxAerodynamicTorque", pose.maxAerodynamicTorque());
        return tag;
    }

    // Read the pose
    private static ShipControlMap.BearingPose readPose(CompoundTag tag) {
        List<ShipControlMap.BearingResponse> responses = new ArrayList<>();
        ListTag responseTags = tag.getList(RESPONSES_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < responseTags.size(); idx++) {
            CompoundTag entry = responseTags.getCompound(idx);
            responses.add(new ShipControlMap.BearingResponse(
                    entry.getInt("PropulsionUnitIndex"),
                    readVec3(entry, ROOT_POSITION_TAG),
                    readVec3(entry, FORCE_DIRECTION_TAG),
                    entry.getDouble("MaxThrust")));
        }
        List<ShipControlMap.AerodynamicSurface> surfaces = new ArrayList<>();
        ListTag surfaceTags = tag.getList(SURFACES_TAG, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < surfaceTags.size(); idx++) {
            CompoundTag entry = surfaceTags.getCompound(idx);
            Reference reference = readReference(entry.getCompound(REFERENCE_TAG));
            if (reference == null) {
                continue;
            }
            surfaces.add(new ShipControlMap.AerodynamicSurface(
                    entry.getInt("SurfaceIndex"),
                    reference.subLevelId(),
                    reference.blockPos(),
                    entry.getString("BlockId"),
                    readVec3(entry, ROOT_POSITION_TAG),
                    readVec3(entry, "Normal"),
                    entry.getDouble("ParallelDragScalar"),
                    entry.getDouble("DirectionlessDragScalar"),
                    entry.getDouble("LiftScalar")));
        }
        return new ShipControlMap.BearingPose(
                tag.getDouble("AngleX"),
                tag.getDouble("AngleZ"),
                responses,
                surfaces,
                tag.getDouble("MaxAerodynamicForce"),
                tag.getDouble("MaxAerodynamicTorque"));
    }

    // Remap the pose
    private static @Nullable ShipControlMap.BearingPose remapPose(
            ShipControlMap.BearingPose pose,
            UUID rootSubLevelId,
            SubLevelSchematicSerializationContext ctx) {
        List<ShipControlMap.BearingResponse> responses = new ArrayList<>();
        for (ShipControlMap.BearingResponse resp : pose.responses()) {
            Vec3 rootPosition = remapRootPosition(
                    ctx, rootSubLevelId, resp.rootPosition());
            if (rootPosition == null) {
                return null;
            }
            responses.add(new ShipControlMap.BearingResponse(
                    resp.propulsionUnitIndex(), rootPosition,
                    resp.forceDirection(), resp.maxThrust()));
        }
        List<ShipControlMap.AerodynamicSurface> surfaces = new ArrayList<>();
        for (ShipControlMap.AerodynamicSurface surface : pose.aerodynamicSurfaces()) {
            ReferenceRemap reference = remapReference(
                    ctx, surface.subLevelId(), surface.blockPosition());
            Vec3 rootPosition = remapRootPosition(
                    ctx, rootSubLevelId, surface.rootPosition());
            if (reference == null || rootPosition == null) {
                return null;
            }
            surfaces.add(new ShipControlMap.AerodynamicSurface(
                    surface.surfaceIndex(), reference.subLevelId(), reference.blockPos(),
                    surface.blockId(), rootPosition, surface.normal(),
                    surface.parallelDragScalar(), surface.directionlessDragScalar(),
                    surface.liftScalar()));
        }
        return new ShipControlMap.BearingPose(
                pose.angleX(), pose.angleZ(), responses, surfaces,
                pose.maxAerodynamicForce(), pose.maxAerodynamicTorque());
    }

    // Remap the reference
    private static @Nullable ReferenceRemap remapReference(
            SubLevelSchematicSerializationContext ctx,
            UUID subLevelId,
            BlockPos blockPos) {
        SchematicSubLevelReferenceRemapper.RemappedBlockReference remapped =
                SchematicSubLevelReferenceRemapper.remapBlockReference(
                        ctx, subLevelId, blockPos);
        return remapped == null ? null : new ReferenceRemap(
                remapped.subLevelId(), remapped.blockPos(), remapped.changed());
    }

    // Remap the sublevel id
    private static @Nullable UUID remapSubLevelId(
            SubLevelSchematicSerializationContext ctx,
            UUID subLevelId) {
        ReferenceRemap remapped = remapReference(ctx, subLevelId, BlockPos.ZERO);
        return remapped == null ? null : remapped.subLevelId();
    }

    // Remap the root position
    private static @Nullable Vec3 remapRootPosition(
            SubLevelSchematicSerializationContext ctx,
            UUID rootSubLevelId,
            Vec3 pos) {
        SchematicSubLevelReferenceRemapper.RemappedWorldPosition remapped =
                SchematicSubLevelReferenceRemapper.remapWorldPosition(
                        ctx, rootSubLevelId, pos);
        return remapped == null ? null : remapped.position();
    }

    // Get the reference
    private static CompoundTag reference(UUID subLevelId, BlockPos blockPos) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(SUB_LEVEL_ID_TAG, subLevelId);
        tag.putLong(BLOCK_POS_TAG, blockPos.asLong());
        return tag;
    }

    // Read the reference
    private static @Nullable Reference readReference(CompoundTag tag) {
        if (tag == null || !tag.hasUUID(SUB_LEVEL_ID_TAG)
                || !tag.contains(BLOCK_POS_TAG, Tag.TAG_LONG)) {
            return null;
        }
        return new Reference(
                tag.getUUID(SUB_LEVEL_ID_TAG),
                BlockPos.of(tag.getLong(BLOCK_POS_TAG)));
    }

    // Write a position vector
    private static void putVec3(CompoundTag owner, String key, Vec3 val) {
        Vec3 vector = val == null ? Vec3.ZERO : val;
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", vector.x);
        tag.putDouble("Y", vector.y);
        tag.putDouble("Z", vector.z);
        owner.put(key, tag);
    }

    // Read a position vector
    private static Vec3 readVec3(CompoundTag owner, String key) {
        CompoundTag tag = owner.getCompound(key);
        return new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
    }

    // Store the reference
    private record Reference(UUID subLevelId, BlockPos blockPos) {
    }

    // Store the reference remap
    private record ReferenceRemap(UUID subLevelId, BlockPos blockPos, boolean changed) {
    }
}
