package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Verify portable Photomancy references become native template coordinates
class PhotomancySchematicImportTest{
    // Restore a linked bearing before the library assigns fresh native identities
    @Test void bearingReferencesKeepTheirTemplateBodyAndLocalPosition(){
        UUID id = UUID.randomUUID();
        SubLevelSchematic schematic = template(id);
        CompoundTag tag = new CompoundTag();
        CompoundTag ref = reference(id);
        ref.put("local_pos", NbtUtils.writeBlockPos(new BlockPos(1, 2, 3)));
        tag.put("createthrusters:photomancy_bearing_ref", ref);
        SchematicBlockEntityConfigPayload.capture(tag);
        PhotomancyBlueprintCompat.importSchematicTag("vector_bearing", schematic, tag);
        assertEquals(id, tag.getUUID("MountedSubLevel"));
        assertEquals(new BlockPos(1, 2, 3).asLong(), tag.getLong("MountedLocalPos"));
        assertTrue(tag.getBoolean("MountedAssemblyPresent"));
        assertFalse(tag.contains("createthrusters:photomancy_bearing_ref"));
    }

    // Restore compressed controller payload references and their serialized endpoint strings
    @Test void controllerReferencesAreReadyForNativePlacement() throws Exception{
        UUID id = UUID.randomUUID();
        BlockPos original = new BlockPos(100, 200, 300), local = new BlockPos(1, 2, 3);
        CompoundTag ref = reference(id);
        ref.putBoolean("has_position", true); ref.put("local_pos", NbtUtils.writeBlockPos(local));
        ref.putLong("source_pos", original.asLong());
        CompoundTag endpoint = new CompoundTag();
        endpoint.put("createthrusters:photomancy_controller_ref", ref);
        endpoint.putString("Id", "storage@" + original.asLong() + "#" + id);
        CompoundTag payload = new CompoundTag();
        payload.put("ControllerData", endpoint);
        payload.putBoolean("PlacementPrepared", true);
        CompoundTag tag = new CompoundTag();
        assertTrue(ControllerSchematicPayload.write(tag, payload));
        PhotomancyBlueprintCompat.importSchematicTag("advanced_contraption_controller", template(id), tag);
        CompoundTag imported = ControllerSchematicPayload.take(tag);
        assertNotNull(imported);
        CompoundTag data = imported.getCompound("ControllerData");
        assertEquals(id, data.getUUID("SubLevelId"));
        assertEquals(local.asLong(), data.getLong("BlockPos"));
        assertEquals("storage@" + local.asLong() + "#" + id, data.getString("Id"));
        assertFalse(imported.getBoolean("PlacementPrepared"));
    }

    // Remove attachment fields for bodies skipped because their blocks are unavailable
    @Test void missingBearingBodiesDoNotBecomeWorldReferences(){
        UUID id = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        CompoundTag ref = reference(UUID.randomUUID());
        ref.put("local_pos", NbtUtils.writeBlockPos(BlockPos.ZERO));
        tag.put("createthrusters:photomancy_bearing_ref", ref);
        tag.putUUID("SubLevelID", UUID.randomUUID()); tag.putLong("SwivelPlate", 0);
        PhotomancyBlueprintCompat.importSchematicTag("thruster_bearing", template(id), tag);
        assertFalse(tag.contains("SubLevelID")); assertFalse(tag.contains("SwivelPlate"));
        assertFalse(tag.contains("createthrusters:photomancy_bearing_ref"));
    }

    // Keep a controller import usable after a referenced body has been removed
    @Test void missingControllerBodiesClearTheirPosition() throws Exception{
        UUID supported = UUID.randomUUID();
        CompoundTag endpoint = new CompoundTag();
        CompoundTag ref = reference(UUID.randomUUID()); ref.putBoolean("has_position", true);
        ref.put("local_pos", NbtUtils.writeBlockPos(BlockPos.ZERO));
        endpoint.put("createthrusters:photomancy_controller_ref", ref);
        endpoint.putUUID("SubLevelId", UUID.randomUUID()); endpoint.putLong("BlockPos", 0);
        CompoundTag child = new CompoundTag(); child.put("createthrusters:photomancy_controller_ref", reference(supported));
        endpoint.put("Supported", child);
        CompoundTag payload = new CompoundTag(); payload.put("ControllerData", endpoint);
        CompoundTag tag = new CompoundTag(); assertTrue(ControllerSchematicPayload.write(tag, payload));
        PhotomancyBlueprintCompat.importSchematicTag("advanced_contraption_controller", template(supported), tag);
        CompoundTag imported = ControllerSchematicPayload.take(tag).getCompound("ControllerData");
        assertFalse(imported.contains("SubLevelId")); assertFalse(imported.contains("BlockPos"));
        assertFalse(imported.contains("createthrusters:photomancy_controller_ref"));
        assertEquals(supported, imported.getCompound("Supported").getUUID("SubLevelId"));
    }

    // Describe an imported body without requiring addon block registries
    private static SubLevelSchematic template(UUID id){
        return new SubLevelSchematic(List.of(new SubLevelSchematic.Body(id, Vec3.ZERO, new Quaterniond(),
                new BlockPos(4, 4, 4), List.of(), new SubLevelSchematic.ImportFrame(
                SubLevelSchematic.Format.PHOTOMANCY, id, BlockPos.ZERO, 7))));
    }

    // Encode a portable source identity using Photomancy's blueprint-local index
    private static CompoundTag reference(UUID id){
        CompoundTag ref = new CompoundTag(); ref.putUUID("source_uuid", id); ref.putInt("sub_level_id", 7); return ref;
    }
}
