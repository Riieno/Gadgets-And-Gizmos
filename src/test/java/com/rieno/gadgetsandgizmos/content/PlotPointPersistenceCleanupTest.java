package com.rieno.gadgetsandgizmos.content;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlotPointPersistenceCleanupTest {
    @Test
    void controllerStorageDropsLegacyPointsWithoutChangingOtherData() {
        CompoundTag data = new CompoundTag();
        data.putString("CustomName", "Ship Controller");
        data.put("PlotPoints", new CompoundTag());

        CompoundTag stored = ControllerSqliteStore.controllerDataForStorage(data);
        assertFalse(stored.contains("PlotPoints"));
        assertEquals("Ship Controller", stored.getString("CustomName"));
        assertTrue(data.contains("PlotPoints"));
    }

    @Test
    void schematicPayloadDropsLegacyPoints() {
        CompoundTag controller = new CompoundTag();
        controller.putString("CustomName", "Ship Controller");
        controller.put("PlotPoints", new CompoundTag());
        CompoundTag payload = new CompoundTag();
        payload.put(ControllerSchematicPayload.CONTROLLER_DATA_TAG, controller);
        CompoundTag blockEntity = new CompoundTag();

        assertTrue(ControllerSchematicPayload.write(blockEntity, payload));
        assertFalse(controller.contains("PlotPoints"));
        CompoundTag restored = ControllerSchematicPayload.take(blockEntity);
        assertNotNull(restored);
        CompoundTag restoredController = restored.getCompound(ControllerSchematicPayload.CONTROLLER_DATA_TAG);
        assertFalse(restoredController.contains("PlotPoints"));
        assertEquals("Ship Controller", restoredController.getString("CustomName"));
    }

    @Test
    void oversizedChunkRecoverySkipsLegacyPointsAndKeepsTheController() {
        CompoundTag controller = new CompoundTag();
        controller.putString("id", "createthrusters:advanced_contraption_controller");
        controller.putString("CustomName", "Ship Controller");
        controller.put("PlotPoints", new CompoundTag());
        ListTag blockEntities = new ListTag();
        blockEntities.add(controller);
        CompoundTag chunk = new CompoundTag();
        chunk.put("block_entities", blockEntities);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(chunk);
            CompoundTag recovered = OversizedNbtRecovery.readSanitized(buffer, 0, null);
            assertNotNull(recovered);
            CompoundTag retained = recovered.getList("block_entities", 10).getCompound(0);
            assertFalse(retained.contains("PlotPoints"));
            assertEquals("Ship Controller", retained.getString("CustomName"));
        } finally {
            buffer.release();
        }
    }
}
