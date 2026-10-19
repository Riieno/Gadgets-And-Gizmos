package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.scm.ScmGeometryFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Verify additive bearing geometry survives schematic storage and older maps still load
class ShipMapGeometryPersistenceTest{
    // Retain calibrated responses and host frames across a saved-map round trip
    @Test void preservesBearingFrames(){
        UUID root = UUID.randomUUID();
        var frame = new ScmGeometryFrame(new Vec3(3, 4, 5), new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1));
        var response = new ShipControlMap.BearingResponse(0, new Vec3(3, 6, 5), new Vec3(0, 1, 0), 200);
        var bearing = new ShipControlMap.BearingUnit(0, root, new BlockPos(3, 4, 5), "test:bearing", "test",
                List.of(root), -45, 45, -45, 45, List.of(new ShipControlMap.BearingPose(10, 20, List.of(response))), frame);
        var map = new ShipControlMap(UUID.randomUUID(), "minecraft:overworld", root, BlockPos.ZERO, Vec3.ZERO,
                List.of(), List.of(bearing), 100);
        var tag = ShipControlMapSchematicCodec.write(map);
        var restored = ShipControlMapSchematicCodec.read(tag);
        assertNotNull(restored);
        assertEquals(map, restored);
    }

    // Accept older records without inventing a calibration coordinate frame
    @Test void loadsLegacyBearingRecords(){
        UUID root = UUID.randomUUID();
        var bearing = new ShipControlMap.BearingUnit(0, root, BlockPos.ZERO, "test:bearing", "test",
                List.of(root), -45, 45, -45, 45, List.of(new ShipControlMap.BearingPose(0, 0, List.of())));
        var map = new ShipControlMap(UUID.randomUUID(), "minecraft:overworld", root, BlockPos.ZERO, Vec3.ZERO,
                List.of(), List.of(bearing), 100);
        var restored = ShipControlMapSchematicCodec.read(ShipControlMapSchematicCodec.write(map));
        assertNotNull(restored);
        assertNull(restored.bearings().getFirst().calibrationFrame());
        assertEquals(map, restored);
    }
}
