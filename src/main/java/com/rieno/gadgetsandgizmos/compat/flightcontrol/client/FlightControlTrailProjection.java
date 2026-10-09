package com.rieno.gadgetsandgizmos.compat.flightcontrol.client;

import ace.flight.block.AerodynamicTrailCreatorBlockEntity;
import ace.flight.neoforge.client.sonic.SonicBoomClientEffects;
import ace.flight.neoforge.client.sonic.SonicBoomRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

// Draw the native trail history at its anchored emitter without moving stored world points
final class FlightControlTrailProjection{
    private static final Field KEYS;
    private static final Field TRAILS;
    private static final Method RENDER;
    private static final RenderType TYPE;

    static{
        try{
            // Flight Control 0.7.7 retains the local trail renderer as a private, uncalled method.
            KEYS = SonicBoomClientEffects.class.getDeclaredField("TRAIL_ENTITY_KEYS");
            TRAILS = SonicBoomClientEffects.class.getDeclaredField("TRAILS");
            Class<?> trail = Class.forName("ace.flight.neoforge.client.sonic.SonicBoomClientEffects$ClientTrail");
            RENDER = SonicBoomClientEffects.class.getDeclaredMethod("renderAerodynamicTrailLocal",
                    PoseStack.Pose.class, VertexConsumer.class, trail, Pose3dc.class, BlockPos.class, Vector3d.class);
            KEYS.setAccessible(true);
            TRAILS.setAccessible(true);
            RENDER.setAccessible(true);
            Method type = SonicBoomRenderTypes.class.getDeclaredMethod("sonicCloudGlow");
            type.setAccessible(true);
            TYPE = (RenderType) type.invoke(null);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException("Unavailable native trail projection", err); }
    }

    private FlightControlTrailProjection(){}

    static void render(AerodynamicTrailCreatorBlockEntity component, Pose3dc pose, PoseStack ms,
            MultiBufferSource buffers, Vec3 camera){
        try{
            Object key = ((Map<?, ?>) KEYS.get(null)).get(component);
            Object trail = key == null ? null : ((Map<?, ?>) TRAILS.get(null)).get(key);
            if(trail == null) return;
            BlockPos origin = component.getBlockPos();
            Vector3d localCamera = pose.transformPositionInverse(new Vector3d(camera.x, camera.y, camera.z), new Vector3d())
                    .sub(origin.getX(), origin.getY(), origin.getZ());
            RENDER.invoke(null, ms.last(), buffers.getBuffer(TYPE), trail, pose, origin, localCamera);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException("Native trail projection failed", err); }
    }
}
