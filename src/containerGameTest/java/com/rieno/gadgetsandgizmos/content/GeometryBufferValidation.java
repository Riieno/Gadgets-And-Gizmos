package com.rieno.gadgetsandgizmos.content;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rieno.gadgetsandgizmos.lib.client.render.RenderGeometryCache;
import org.joml.Quaternionf;

import java.util.Arrays;

// Compare real native buffer bytes under Minecraft's client runtime
final class GeometryBufferValidation{
    private static final int[] COLORS = {0x01234567, 0x7FABCDEF, 0xFF102030, 0x00F0FF01};

    static void run(){
        compare(DefaultVertexFormat.BLOCK);
        compare(DefaultVertexFormat.NEW_ENTITY);
        compare(DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
    }
    private static void compare(VertexFormat format){
        var poses = new PoseStack();
        poses.translate(10.25D, -4.75D, 2.5D);
        poses.mulPose(new Quaternionf().rotationXYZ(.25F, -.6F, .15F));
        poses.scale(.5F, 2F, 3F);
        var cache = new RenderGeometryCache();
        byte[] original = write(format, out -> {
            for(int idx = 0; idx < 4; idx++){
                out.addVertex(poses.last().pose(), idx - .25F, idx + 1.5F, 3.75F - idx)
                        .setColor(COLORS[idx]).setUv(.125F * idx, .75F)
                        .setUv1(idx == 0 ? 65535 : idx, 15).setUv2(240, idx == 0 ? 65535 : idx)
                        .setNormal(poses.last(), .3F, .8F, -.1F);
            }
        });
        byte[] first = write(format, out -> cache.render(poses, ignored -> out, true, buffers -> {
            var target = buffers.getBuffer(null);
            for(int idx = 0; idx < 4; idx++){
                target.addVertex(idx - .25F, idx + 1.5F, 3.75F - idx)
                        .setColor(COLORS[idx]).setUv(.125F * idx, .75F)
                        .setUv1(idx == 0 ? 65535 : idx, 15).setUv2(240, idx == 0 ? 65535 : idx)
                        .setNormal(.3F, .8F, -.1F);
            }
        }));
        byte[] retained = write(format, out -> cache.render(poses, ignored -> out, false,
                ignored -> { throw new AssertionError("Retained geometry was rebuilt"); }));
        if(!Arrays.equals(original, first)) throw new AssertionError("Packed geometry changed native buffer bytes: " + format);
        if(!Arrays.equals(original, retained)) throw new AssertionError("Retained geometry changed native buffer bytes: " + format);
    }

    private static byte[] write(VertexFormat format, java.util.function.Consumer<VertexConsumer> draw){
        try(var bytes = new ByteBufferBuilder(1024)){
            var builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            draw.accept(builder);
            try(var mesh = builder.buildOrThrow()){
                var data = mesh.vertexBuffer();
                byte[] res = new byte[data.remaining()]; data.get(res);
                int stride = format.getVertexSize();
                if(format == DefaultVertexFormat.BLOCK || format == DefaultVertexFormat.NEW_ENTITY){
                    for(int idx = stride - 1; idx < res.length; idx += stride) res[idx] = 0;
                }
                return res;
            }
        }
    }
}
