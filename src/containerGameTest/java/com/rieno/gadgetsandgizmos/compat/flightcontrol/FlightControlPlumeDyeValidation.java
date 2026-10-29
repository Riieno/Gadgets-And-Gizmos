package com.rieno.gadgetsandgizmos.compat.flightcontrol;

import ace.flight.block.SmartVectorThrusterBlock;
import ace.flight.block.SmartVectorThrusterBlockEntity;
import ace.flight.neoforge.client.SmartVectorThrusterBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectRenderTypes;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectPreset;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Exercise the transformed native plume overloads without packaging validation classes
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class FlightControlPlumeDyeValidation{
    private static boolean complete;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post evt){
        String output = System.getProperty("createthrusters.flightControlDyeValidation");
        if(output == null || complete || Minecraft.getInstance().getOverlay() != null) return;
        complete = true;
        boolean prev = CTConfigs.COMMON.dyeApplicatorFlightControl.get();
        String result;
        try{
            int count = verify();
            result = "PASSED: " + count + " native plume draws across all six variants, facings, flame modes and available overloads";
        }catch(Throwable err){
            LogUtils.getLogger().error("Flight Control plume dye validation failed", err);
            result = "FAILED: " + err;
        }finally{
            CTConfigs.COMMON.dyeApplicatorFlightControl.set(prev);
        }
        try{ Files.writeString(Path.of(output), result); }
        catch(java.io.IOException err){ throw new IllegalStateException(err); }
        LogUtils.getLogger().info(result);
        Minecraft.getInstance().stop();
    }

    private static int verify() throws ReflectiveOperationException{
        int count = 0;
        for(String name : List.of("smart_vector_thruster", "smart_vector_thruster_small", "smart_vector_ion_thruster",
                "smart_vector_ion_thruster_small", "creative_smart_vector_thruster", "creative_smart_vector_thruster_small")){
            var block = (SmartVectorThrusterBlock)BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create_flight_control:" + name));
            for(Direction facing : Direction.values()){
                var state = block.defaultBlockState().setValue(SmartVectorThrusterBlock.FACING, facing);
                var owner = (SmartVectorThrusterBlockEntity)block.newBlockEntity(BlockPos.ZERO, state);
                for(var method : SmartVectorThrusterBlockEntityRenderer.class.getDeclaredMethods()){
                    if(!method.getName().equals("renderConeFame") && !method.getName().equals("renderExhaustParticles")) continue;
                    if(method.getParameterCount() < 7) continue;
                    method.setAccessible(true);
                    for(int mode = 0; mode < 4; mode++){
                        boolean enabled = mode != 3;
                        boolean additive = mode == 2;
                        var preset = mode == 0 ? ColorEffectPreset.DEFAULT
                                : ColorEffectPreset.fromColors(List.of(0x40A0E0), additive).withAnimated(false);
                        var tag = new CompoundTag();
                        tag.put(ColorEffectState.NBT_KEY, preset.write());
                        owner.loadWithComponents(tag, RegistryAccess.EMPTY);
                        owner.flameColor = 0xFFFFFF;
                        owner.flameLengthMultiplier = 1;
                        CTConfigs.COMMON.dyeApplicatorFlightControl.set(enabled);
                        var capture = new Capture(mode == 0 || !enabled ? 0xFFFFFF : 0x40A0E0);
                        MultiBufferSource buffers = type -> {
                            capture.type = type;
                            capture.requests++;
                            return capture;
                        };
                        var direction = new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ());
                        Object[] args = method.getParameterCount() == 7
                                ? new Object[]{owner, facing, new PoseStack(), buffers, 1F, direction, new Vector3f(.5F)}
                                : new Object[]{owner, facing, new PoseStack(), buffers, 1F, direction, new Vector3f(.5F), .6F};
                        method.invoke(new SmartVectorThrusterBlockEntityRenderer(null), args);
                        if(capture.vertices == 0 || capture.requests != 1) throw new AssertionError("Native plume produced no geometry or requested multiple buffers: " + name);
                        if(additive && capture.type != ColorEffectRenderTypes.untextured(true)) throw new AssertionError("Additive plume retained the native blend");
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static final class Capture implements VertexConsumer{
        private final int color;
        private int vertices;
        private int requests;
        private RenderType type;

        private Capture(int color){ this.color = color; }

        @Override public VertexConsumer addVertex(float x, float y, float z){ vertices++; return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha){
            if((red << 16 | green << 8 | blue) != color) throw new AssertionError("Native plume missed or repeated the tint: " + Integer.toHexString(red << 16 | green << 8 | blue));
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v){ return this; }
        @Override public VertexConsumer setUv1(int u, int v){ return this; }
        @Override public VertexConsumer setUv2(int u, int v){ return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z){ return this; }
    }
}
