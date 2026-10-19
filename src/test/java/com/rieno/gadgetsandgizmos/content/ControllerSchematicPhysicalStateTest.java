package com.rieno.gadgetsandgizmos.content;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

// Exercise schematic attachments independently of graph size and backend recovery snapshots
class ControllerSchematicPhysicalStateTest{
    @Test void largeProgramKeepsItsModuleAcrossAnEmptyIntermediateSnapshot(){
        CompoundTag program = module();
        byte[] graphData = new byte[192 * 1024];
        new Random(42).nextBytes(graphData);
        CompoundTag graph = new CompoundTag();
        graph.putByteArray("Data", graphData);
        program.put("AdvancedDraftGraph", graph);
        program.put("AdvancedActiveGraph", graph.copy());
        CompoundTag saved = new CompoundTag();
        saved.put(ControllerPhysicalState.TAG_KEY, new CompoundTag());

        CompoundTag payload = new CompoundTag();
        payload.put(ControllerSchematicPayload.CONTROLLER_DATA_TAG, program);
        assertTrue(ControllerSchematicPayload.write(saved, payload));
        assertTrue(saved.getCompound(ControllerSchematicPayload.BLOCK_ENTITY_TAG).getInt("CompressedBytes") > 32 * 1024);
        CompoundTag decoded = ControllerSchematicPayload.take(saved);
        assertNotNull(decoded);
        CompoundTag restored = ControllerPhysicalState.readSchematicBlock(
                decoded.getCompound(ControllerSchematicPayload.CONTROLLER_DATA_TAG), saved);
        assertEquals(module(), ControllerPhysicalState.materialData(restored));
        assertArrayEquals(graphData, restored.getCompound("AdvancedActiveGraph").getByteArray("Data"));
        assertFalse(ControllerPhysicalState.readSavedBlock(program, saved).contains(ControllerPhysicalState.EMBEDDED_BLOCK));
    }

    @Test void materialPreviewDoesNotCopyOrDecodeTheCompressedProgram(){
        CompoundTag saved = new CompoundTag();
        saved.put(ControllerPhysicalState.TAG_KEY, module());
        saved.put(ControllerSchematicPayload.BLOCK_ENTITY_TAG, new CompoundTag(){
            @Override public CompoundTag copy(){ throw new AssertionError("Material scan visited the graph payload"); }
        });
        saved.putString(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID, "existing_program");
        CompoundTag preview = ControllerPhysicalState.materialData(saved);
        assertEquals(module(), preview);
        assertFalse(preview.contains(ControllerSchematicPayload.BLOCK_ENTITY_TAG));
        assertFalse(preview.contains(ControllerManifestStore.TAG_CONTROLLER_MANIFEST_ID));
    }

    @Test void legacyPhysicalFieldsAndOuterSnapshotsRemainSupported(){
        assertEquals(module(), ControllerPhysicalState.materialData(module()));
        CompoundTag saved = new CompoundTag();
        saved.put(ControllerPhysicalState.TAG_KEY, module());
        assertEquals(module(), ControllerPhysicalState.readSchematicBlock(new CompoundTag(), saved));
    }

    private static CompoundTag module(){
        CompoundTag physical = new CompoundTag();
        CompoundTag state = new CompoundTag();
        state.putString("Name", "createthrusters:ship_control_module");
        physical.put(ControllerPhysicalState.EMBEDDED_BLOCK, state);
        return physical;
    }
}
