package com.rieno.gadgetsandgizmos.compat.schematic;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AileronBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.AileronBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonBlockEntity;
import com.rieno.gadgetsandgizmos.content.ScissorPistonArmBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryBeltWheelBlockEntity;
import com.rieno.gadgetsandgizmos.content.RopeKnotBlockEntity;
import com.rieno.gadgetsandgizmos.content.ThrusterBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingBlockEntity;
import com.rieno.gadgetsandgizmos.content.VectorBearingLinkBlockEntity;
import com.rieno.gadgetsandgizmos.lib.compat.OptionalTypeMatcher;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicLinkData;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicLinkData.PositionFormat;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicLinkData.Reference;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicLinkData.Schema;
import com.simibubi.create.content.trains.track.TrackBlockEntity;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity;
import dev.simulated_team.simulated.content.blocks.swivel_bearing.link_block.SwivelBearingPlateBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

// Declare mod-owned connection schemas while the library owns copying and coordinate remapping
public final class SchematicLinkCompatibility{
    // Keep compatibility registration on the common setup hook
    private SchematicLinkCompatibility(){}


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register installed connection formats without loading optional implementation classes
    public static void onCommonSetup(FMLCommonSetupEvent event){
        event.enqueueWork(SchematicLinkCompatibility::registerSchemas);
    }

    // Retain links alongside ordinary safe configuration without storing inventories or energy
    private static void registerSchemas(){
        register("mounted_heads", be -> be instanceof VectorBearingBlockEntity || be instanceof ScissorPistonBlockEntity,
                new Schema(Set.of("MountedSubLevel", "MountedLocalPos", "MountedAssemblyPresent"),
                        List.of(ref(List.of(), "MountedLocalPos", "MountedSubLevel", PositionFormat.LONG))));
        register("aileron_heads", be -> be instanceof AileronBearingBlockEntity,
                new Schema(Set.of("primary", "secondary"), List.of(
                        ref(List.of("primary"), "MountedLocalPos", "MountedSubLevel", PositionFormat.LONG),
                        ref(List.of("secondary"), "MountedLocalPos", "MountedSubLevel", PositionFormat.LONG))));
        register("bearing_parents", be -> be instanceof VectorBearingLinkBlockEntity || be instanceof AileronBearingLinkBlockEntity
                        || be instanceof SwivelBearingPlateBlockEntity || be instanceof ScissorPistonArmBlockEntity,
                new Schema(Set.of("ParentPos", "ParentSubLevelId", "Head"),
                        List.of(ref(List.of(), "ParentPos", "ParentSubLevelId", PositionFormat.BLOCK_POS))));
        register("thruster_parents", be -> be instanceof ThrusterBearingLinkBlockEntity,
                new Schema(Set.of("ThrusterParentPos", "ThrusterParentSubLevelId"),
                        List.of(ref(List.of(), "ThrusterParentPos", "ThrusterParentSubLevelId", PositionFormat.BLOCK_POS))));
        register("gantry_payloads", be -> be instanceof PhysicsGantryCarriageBlockEntity,
                new Schema(Set.of("AssembledToSubLevel", "AttachedShaftPos", "AttachedSubLevelId", "AttachedShaftSubLevelId",
                        "AttachedShaftDirection", "AttachedCarriageFacing", "AttachedShaftProgress", "SequencedMovementLimit"),
                        List.of(ref(List.of(), "AttachedShaftPos", "AttachedShaftSubLevelId", PositionFormat.LONG),
                                ref(List.of(), "AttachedSubLevelId", null, PositionFormat.BODY))));
        register("gantry_belt_links", be -> be instanceof PhysicsGantryBeltWheelBlockEntity,
                new Schema(Set.of("LinkedPos", "LinkedSubLevelId", "ReturnsBeltOnBreak", "AdditionalLinks", "ReceivesFromLinkedWheel"),
                        List.of(ref(List.of(), "LinkedPos", "LinkedSubLevelId", PositionFormat.LONG),
                                ref(List.of("AdditionalLinks"), "Pos", "SubLevelId", PositionFormat.LONG))));
        register("swivel_heads", be -> be instanceof SwivelBearingBlockEntity,
                new Schema(Set.of("SwivelPlate", "SubLevelID", "TargetAngle", "SequencedAngleLimit"),
                        List.of(ref(List.of(), "SwivelPlate", "SubLevelID", PositionFormat.BLOCK_POS))));
        register("spring_links", be -> be instanceof SpringBlockEntity,
                new Schema(Set.of("Goal", "GoalSubLevel", "Controller", "DesiredLength"),
                        List.of(ref(List.of(), "Goal", "GoalSubLevel", PositionFormat.LONG))));
        register("rope_splines", new OptionalTypeMatcher("dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity")::test,
                new Schema(Set.of("OwnStrand", "HasRopeAttached", "Strand"),
                        List.of(ref(List.of("Strand", "attachments"), "blockAttachment", "subLevelID", PositionFormat.BLOCK_POS)),
                        Set.of("HasRopeAttached", "uuid"), List.of(List.of("Strand", "points")), Set.of("Strand")));
        register("rope_anchors", be -> be instanceof RopeKnotBlockEntity,
                new Schema(Set.of("AnchorRopeUUID", "AnchorPathPosition", "AnchorOwnerPos", "AnchorOwnerSubLevelUUID",
                        "AttachmentOffsetX", "AttachmentOffsetY", "AttachmentOffsetZ"),
                        List.of(ref(List.of(), "AnchorOwnerPos", "AnchorOwnerSubLevelUUID", PositionFormat.LONG)),
                        Set.of("AnchorRopeUUID"), List.of(), Set.of()));
        register("create_track_splines", be -> be instanceof TrackBlockEntity,
                new Schema(Set.of("Connections", "Smoothing"), bezierRefs(List.of("Connections"))));
        register("coaster_splines", new OptionalTypeMatcher("dev.silvergold.simulatedcoasters.track.anchor.CoasterAnchorpointBlockEntity")::test,
                new Schema(Set.of("AnchorPeerCurves", "AnchorPeerCurveTints", "JointBank", "Lift", "OpenEndExtension", "ChainLiftShaftAxis",
                        "ChainLiftSpan", "ChainLiftCommittedLength", "ChainLiftFirstHop", "ChainLiftCommittedSegments", "ChainLiftTravelSign"),
                        coasterRefs()));
        register("connector_wires", new OptionalTypeMatcher("com.mrh0.createaddition.blocks.connector.base.AbstractConnectorBlockEntity")::test,
                new Schema(Set.of("nodes"), List.of(ref(List.of("nodes"), "", null, PositionFormat.RELATIVE_VECTOR))));
        register("synaxis_companions", new OptionalTypeMatcher("com.verr1.synaxis.foundation.blockentity.CompanionPhysicsBlockEntity")::test,
                new Schema(Set.of(), List.of(ref(List.of("synaxis_state", "fields"),
                        "physics_body/connection_target", "physics_body/expected_companion_uuid", PositionFormat.BLOCK_POS)),
                        Set.of(), List.of(), Set.of(), List.of(
                        List.of("synaxis_state", "fields", "physics_body/has_connection_intent"),
                        List.of("synaxis_state", "fields", "physics_body/connection_target"),
                        List.of("synaxis_state", "fields", "physics_body/expected_companion_uuid"),
                        List.of("synaxis_state", "fields", "physics_body/companion_direction"),
                        List.of("synaxis_state", "fields", "physics_body/companion_alignment_direction"))));
    }


    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register each adapter under the namespace that owns this integration
    private static void register(String name, Predicate<BlockEntity> matches, Schema schema){
        SchematicLinkData.register(ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, name), matches, schema);
    }

    // Describe one endpoint field using its upstream save encoding
    private static Reference ref(List<String> path, String pos, String id, PositionFormat format){
        return new Reference(path, pos, id, format);
    }

    // Remap both Bezier endpoints and control-point origins while retaining axes and normals
    private static List<Reference> bezierRefs(List<String> path){
        List<String> positions = new ArrayList<>(path); positions.add("Positions");
        List<String> starts = new ArrayList<>(path); starts.add("Starts");
        return List.of(ref(positions, "Pos", null, PositionFormat.RELATIVE_VECTOR),
                ref(starts, "", null, PositionFormat.RELATIVE_VECTOR));
    }

    // Remap peer keys alongside each Coasters curve and its optional colour configuration
    private static List<Reference> coasterRefs(){
        List<Reference> refs = new ArrayList<>(bezierRefs(List.of("AnchorPeerCurves", "Bezier")));
        refs.add(ref(List.of("AnchorPeerCurves"), "Peer", null, PositionFormat.LONG));
        refs.add(ref(List.of("AnchorPeerCurveTints"), "Peer", null, PositionFormat.LONG));
        refs.add(ref(List.of("ChainLiftSpan"), "EndAnchor", null, PositionFormat.LONG));
        refs.add(ref(List.of("ChainLiftSpan"), "EdgeFrom", null, PositionFormat.LONG));
        refs.add(ref(List.of("ChainLiftSpan"), "EdgeTo", null, PositionFormat.LONG));
        refs.add(ref(List.of(), "ChainLiftFirstHop", null, PositionFormat.LONG));
        refs.add(ref(List.of("ChainLiftCommittedSegments"), "EdgeFrom", null, PositionFormat.LONG));
        refs.add(ref(List.of("ChainLiftCommittedSegments"), "EdgeTo", null, PositionFormat.LONG));
        return List.copyOf(refs);
    }
}
