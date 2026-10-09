package com.rieno.gadgetsandgizmos.content;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.ryanhcode.sable.api.schematic.SubLevelSchematicSerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Keep embedded controller graphs portable through Create's safe schematic writer
class ControllerSchematicPayloadTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    // Retain multi-chunk graph data in every controller variant without a database identity
    @Test void safeWriterPreservesChunkedGraphsForEveryController() throws Exception{
        CompoundTag payload = payload();
        for(var type : List.of(AnalogueContraptionControllerBlockEntity.class,
                AdvancedContraptionControllerBlockEntity.class,
                PortableAnalogueContraptionControllerBlockEntity.class,
                PortableAdvancedContraptionControllerBlockEntity.class)){
            var controller = imported(type, payload);
            CompoundTag tag = new CompoundTag();
            controller.writeSafe(tag, mock(HolderLookup.Provider.class));
            assertFalse(ControllerManifestStore.hasControllerMetadata(tag));
            assertTrue(tag.getCompound("ControllerSchematicPayload").getList("Chunks", Tag.TAG_COMPOUND).size() > 1);
            CompoundTag decoded = ControllerSchematicPayload.take(tag);
            assertNotNull(decoded, type.getSimpleName());
            assertFalse(decoded.getBoolean("PlacementPrepared"));
            CompoundTag expected = payload.copy(); expected.remove("PlacementPrepared");
            assertEquals(expected, decoded, type.getSimpleName());
            assertTrue(payload.getBoolean("PlacementPrepared"));
        }
    }

    // Map saved template references and prepare the reconstructed graph for server persistence
    @Test void safeWriterUsesSaveAndPlacementReferenceMappings() throws Exception{
        UUID source = UUID.randomUUID(), template = UUID.randomUUID(), placed = UUID.randomUUID();
        CompoundTag payload = payload();
        CompoundTag endpoint = new CompoundTag();
        endpoint.putUUID("SubLevelId", source); endpoint.putLong("BlockPos", new BlockPos(100, 64, 200).asLong());
        payload.getCompound("ControllerData").put("Endpoint", endpoint);
        var save = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.SAVE, null);
        save.getMappings().put(source, new SubLevelSchematicSerializationContext.SchematicMapping(null, null, template,
                pos -> ((BlockPos)pos).subtract(new BlockPos(100, 64, 200))));
        var prev = SubLevelSchematicSerializationContext.getCurrentContext();
        try{
            SubLevelSchematicSerializationContext.setCurrentContext(save);
            CompoundTag tag = new CompoundTag();
            imported(AnalogueContraptionControllerBlockEntity.class, payload).writeSafe(tag, mock(HolderLookup.Provider.class));
            CompoundTag saved = ControllerSchematicPayload.take(tag);
            assertNotNull(saved);
            CompoundTag ref = saved.getCompound("ControllerData").getCompound("Endpoint");
            assertEquals(template, ref.getUUID("SubLevelId"));
            assertEquals(BlockPos.ZERO.asLong(), ref.getLong("BlockPos"));
            assertFalse(saved.getBoolean("PlacementPrepared"));
            var place = new SubLevelSchematicSerializationContext(SubLevelSchematicSerializationContext.Type.PLACE, null);
            place.getMappings().put(template, new SubLevelSchematicSerializationContext.SchematicMapping(null, null, placed,
                    pos -> ((BlockPos)pos).offset(1000, 64, 2000)));
            SubLevelSchematicSerializationContext.setCurrentContext(place);
            tag = new CompoundTag();
            imported(AnalogueContraptionControllerBlockEntity.class, saved).writeSafe(tag, mock(HolderLookup.Provider.class));
            CompoundTag built = ControllerSchematicPayload.take(tag);
            assertNotNull(built);
            ref = built.getCompound("ControllerData").getCompound("Endpoint");
            assertEquals(placed, ref.getUUID("SubLevelId"));
            assertEquals(new BlockPos(1000, 64, 2000).asLong(), ref.getLong("BlockPos"));
            assertTrue(built.getBoolean("PlacementPrepared"));
            assertEquals(saved.getCompound("ControllerData").getCompound("AdvancedDraftGraph"),
                    built.getCompound("ControllerData").getCompound("AdvancedDraftGraph"));
        }finally{ SubLevelSchematicSerializationContext.setCurrentContext(prev); }
    }

    // Supply the pending imported payload before invoking the real safe writer
    private static AnalogueContraptionControllerBlockEntity imported(
            Class<? extends AnalogueContraptionControllerBlockEntity> type, CompoundTag payload) throws Exception{
        var controller = mock(type, CALLS_REAL_METHODS);
        Field behaviours = SmartBlockEntity.class.getDeclaredField("behaviours"); behaviours.setAccessible(true);
        behaviours.set(controller, new HashMap<>());
        Field pending = AnalogueContraptionControllerBlockEntity.class.getDeclaredField("pendingControllerSchematicPayload");
        pending.setAccessible(true); pending.set(controller, payload.copy());
        return controller;
    }

    // Include graph data larger than one compressed chunk and distinct active and draft revisions
    private static CompoundTag payload(){
        byte[] data = new byte[96 * 1024]; new Random(31).nextBytes(data);
        CompoundTag draft = new CompoundTag(); draft.putInt("Revision", 12); draft.putByteArray("EditorData", data);
        CompoundTag active = draft.copy(); active.putInt("Revision", 7);
        CompoundTag body = new CompoundTag(); body.put("AdvancedDraftGraph", draft); body.put("AdvancedActiveGraph", active);
        CompoundTag payload = new CompoundTag(); payload.putString("ControllerKind", "advanced_controller");
        payload.put("ControllerData", body); payload.putBoolean("PlacementPrepared", true);
        return payload;
    }
}
