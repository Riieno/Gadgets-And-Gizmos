package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

// Draw the Embedded Copycats Kinetic
final class EmbeddedCopycatsKineticRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Access ACCESS = Access.create();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the embedded copycats kinetic
    private EmbeddedCopycatsKineticRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the embedded copycats kinetic
    static boolean render(BlockEntity preview, BlockState state, PoseStack poseStack,
                          MultiBufferSource bufferSource, int light, int overlay) {
        KineticBacking backing = KineticBacking.from(state);
        if (ACCESS == null || preview == null || backing == null) {
            return false;
        }

        return switch (backing) {
            case SHAFT -> renderPart(preview, "SHAFT", null, poseStack, bufferSource, light, overlay);
            case COGWHEEL -> renderPart(preview, "SHAFT", "shaft", poseStack, bufferSource, light, overlay)
                    | renderPart(preview, "COGWHEEL", "cogwheel", poseStack, bufferSource, light, overlay);
            case LARGE_COGWHEEL -> renderPart(preview, "LARGE_COGWHEEL", "cogwheel",
                    poseStack, bufferSource, light, overlay)
                    | renderPart(preview, "SHAFT", "shaft", poseStack, bufferSource, light, overlay);
        };
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the part
    private static boolean renderPart(BlockEntity preview, String partialName, String materialProperty,
                                      PoseStack poseStack, MultiBufferSource bufferSource,
                                      int light, int overlay) {
        try {
            SuperByteBuffer buffer = ACCESS.buffer(preview, partialName, materialProperty);
            BlockState material = ACCESS.material(preview, materialProperty);
            if (buffer == null || buffer.isEmpty() || material == null) {
                return false;
            }
            buffer.reset()
                    .light(light)
                    .overlay(overlay)
                    .renderInto(poseStack, bufferSource.getBuffer(renderType(material)));
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    // Draw the type
    private static RenderType renderType(BlockState material) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
        ChunkRenderTypeSet renderTypes = model.getRenderTypes(
                material,
                RandomSource.create(42L),
                ModelData.EMPTY);
        List<RenderType> layers = RenderType.chunkBufferLayers();
        for (int idx = layers.size() - 1; idx >= 0; idx--) {
            RenderType layer = layers.get(idx);
            if (renderTypes.contains(layer)) {
                return layer;
            }
        }
        return RenderType.cutoutMipped();
    }

    // Define the kinetic backing values
    private enum KineticBacking {
        SHAFT,
        COGWHEEL,
        LARGE_COGWHEEL;

        // Create the kinetic backing
        private static KineticBacking from(BlockState state) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (!"copycats".equals(id.getNamespace())) {
                return null;
            }
            return switch (id.getPath()) {
                case "copycat_shaft" -> SHAFT;
                case "copycat_cogwheel" -> COGWHEEL;
                case "copycat_large_cogwheel" -> LARGE_COGWHEEL;
                default -> null;
            };
        }
    }

    // Expose access
    private record Access(Class<?> copycatBlockEntityType, Class<?> multiStateBlockEntityType,
                          Object shaftPartial, Object cogwheelPartial, Object largeCogwheelPartial,
                          Method simpleBufferGetter, Method multiBufferGetter,
                          Method materialGetter, Method storageGetter,
                          Method materialItemGetter, Method storedMaterialGetter) {
        // Create the access
        private static Access create() {
            try {
                ClassLoader loader = EmbeddedCopycatsKineticRenderer.class.getClassLoader();
                Class<?> partialType = Class.forName(
                        "com.copycatsplus.copycats.foundation.copycat.model.kinetic.ICopycatPartialModel",
                        false,
                        loader);
                Class<?> copycatType = Class.forName(
                        "com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity",
                        false,
                        loader);
                Class<?> multiStateType = Class.forName(
                        "com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlockEntity",
                        false,
                        loader);
                Class<?> partialModels = Class.forName(
                        "com.copycatsplus.copycats.CCCopycatPartialModels",
                        true,
                        loader);
                Class<?> renderer = Class.forName(
                        "com.copycatsplus.copycats.foundation.copycat.model.kinetic.KineticCopycatRenderer",
                        true,
                        loader);
                Class<?> storageType = Class.forName(
                        "com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage",
                        false,
                        loader);
                Class<?> materialItemType = Class.forName(
                        "com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage$MaterialItem",
                        false,
                        loader);

                return new Access(
                        copycatType,
                        multiStateType,
                        fieldValue(partialModels, "SHAFT"),
                        fieldValue(partialModels, "COGWHEEL"),
                        fieldValue(partialModels, "LARGE_COGWHEEL"),
                        renderer.getMethod("getRenderedBuffer", partialType, copycatType),
                        renderer.getMethod("getRenderedBuffer", partialType, multiStateType, String.class),
                        copycatType.getMethod("getMaterial"),
                        multiStateType.getMethod("getMaterialItemStorage"),
                        storageType.getMethod("getMaterialItem", String.class),
                        materialItemType.getMethod("material"));
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        // Get the field value
        private static Object fieldValue(Class<?> owner, String name) throws ReflectiveOperationException {
            Field field = owner.getField(name);
            return field.get(null);
        }

        // Get the buffer
        private SuperByteBuffer buffer(BlockEntity preview, String partialName, String materialProperty)
                throws ReflectiveOperationException {
            if (!copycatBlockEntityType.isInstance(preview)) {
                return null;
            }
            Object partial = switch (partialName) {
                case "SHAFT" -> shaftPartial;
                case "COGWHEEL" -> cogwheelPartial;
                case "LARGE_COGWHEEL" -> largeCogwheelPartial;
                default -> null;
            };
            if (partial == null) {
                return null;
            }

            Object res;
            if (materialProperty == null) {
                res = simpleBufferGetter.invoke(null, partial, preview);
            } else {
                if (!multiStateBlockEntityType.isInstance(preview)) {
                    return null;
                }
                res = multiBufferGetter.invoke(null, partial, preview, materialProperty);
            }
            return res instanceof SuperByteBuffer buffer ? buffer : null;
        }

        // Get the material
        private BlockState material(BlockEntity preview, String property) throws ReflectiveOperationException {
            Object res;
            if (property == null) {
                res = materialGetter.invoke(preview);
            } else {
                Object storage = storageGetter.invoke(preview);
                Object materialItem = materialItemGetter.invoke(storage, property);
                res = storedMaterialGetter.invoke(materialItem);
            }
            return res instanceof BlockState material ? material : null;
        }
    }
}
