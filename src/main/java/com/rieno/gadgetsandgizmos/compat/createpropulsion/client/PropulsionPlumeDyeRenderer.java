package com.rieno.gadgetsandgizmos.compat.createpropulsion.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionDyeCompatibility;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectRenderTypes;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectShader;
import com.rieno.gadgetsandgizmos.lib.client.render.ColorEffectVertexConsumer;
import com.rieno.gadgetsandgizmos.lib.color.ColorEffectTarget;
import dev.propulsionteam.propulsionsimulated.client.render.plume.PlumeRenderType;
import dev.propulsionteam.propulsionsimulated.client.render.plume.PlumeShaders;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

// Keep the native plume shader's shape and detail before desaturating and applying each vertex's preset
public final class PropulsionPlumeDyeRenderer{
    private static final VertexFormat FORMAT = ColorEffectShader.withTint(DefaultVertexFormat.POSITION_TEX_COLOR);
    private static ShaderInstance shader;
    private static final RenderType ALPHA = ColorEffectRenderTypes.shader("createthrusters_propulsion_dye", FORMAT,
            PropulsionPlumeDyeRenderer::shader, false);
    private static final RenderType ADDITIVE = ColorEffectRenderTypes.shader("createthrusters_propulsion_dye_additive", FORMAT,
            PropulsionPlumeDyeRenderer::shader, true);

    private PropulsionPlumeDyeRenderer(){}

    public static void registerShaders(RegisterShadersEvent evt){
        try{
            evt.registerShader(ColorEffectShader.create(evt.getResourceProvider(), ResourceLocation.parse("createpropulsion:plume"),
                    ResourceLocation.parse("createthrusters:propulsion_dye_plume"), FORMAT), val -> shader = val);
        }catch(java.io.IOException err){ throw new IllegalStateException("Failed to load Propulsion dye plume shader", err); }
    }

    private static ShaderInstance shader(){
        ColorEffectRenderTypes.copyFloatUniform(PlumeShaders.plume(), shader, "Time");
        ColorEffectRenderTypes.copyFloatUniform(PlumeShaders.plume(), shader, "Power");
        return shader;
    }

    public static MultiBufferSource buffers(BlockEntity owner, MultiBufferSource buffers, float partialTick){
        if(!PropulsionDyeCompatibility.enabled() || !(owner instanceof ColorEffectTarget target)) return buffers;
        var preset = target.colorEffect();
        if(!preset.active() && !preset.additive()) return buffers;
        double time = owner.getLevel() == null ? 0 : owner.getLevel().getGameTime() + partialTick;
        return type -> type != PlumeRenderType.plume() ? buffers.getBuffer(type)
                : new ColorEffectVertexConsumer(buffers.getBuffer(preset.additive() ? ADDITIVE : ALPHA),
                        preset, time, (x, y, z) -> 0, true, true);
    }
}
