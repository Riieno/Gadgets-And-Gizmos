package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerRuntimePayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WearableBindingPayloadTest{
    @Test void boundItemTypesSurviveRuntimePacketsWithoutChangingPairNames(){
        UUID hat = UUID.randomUUID(), goggles = UUID.randomUUID();
        var labels = Map.of(hat, "Flight deck", goggles, "Goggles 1");
        var items = Map.of(hat, "createthrusters:pilot_cap", goggles, "createthrusters:physics_goggles");
        var packet = new AdvancedControllerRuntimePayload(BlockPos.ZERO, null, 1, labels,
                Map.of(), Map.of(), Map.of(), Map.of(), items);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try{
            AdvancedControllerRuntimePayload.STREAM_CODEC.encode(buffer, packet);
            var decoded = AdvancedControllerRuntimePayload.STREAM_CODEC.decode(buffer);
            assertEquals(labels, decoded.gogglesTrackerPairs());
            assertEquals(items, decoded.gogglesTrackerItems());
        }finally{ buffer.release(); }
    }
}
