package com.rieno.gadgetsandgizmos.neoforge.client;

import com.simibubi.create.foundation.model.BakedModelHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Retexture the existing docking connector base model without changing its geometry
public final class DockingConnectorRetexturedModel extends BakedModelWrapper<BakedModel> {
    public static final ModelProperty<Boolean> BOUND_TEXTURE = new ModelProperty<>();

    private static final ResourceLocation DOCK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("simulated", "block/dock");
    private static final ResourceLocation DOCK_POWERED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("simulated", "block/dock_powered");
    private static final ResourceLocation DOCK_DETAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("simulated", "block/dock_2");
    private static final ResourceLocation SHIP_DOCK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "block/ship_docking_connector/dock");
    private static final ResourceLocation SHIP_DOCK_POWERED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "block/ship_docking_connector/dock_powered");
    private static final ResourceLocation SHIP_DOCK_DETAIL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "block/ship_docking_connector/dock_2");

    // Initialize the runtime texture wrapper
    public DockingConnectorRetexturedModel(BakedModel originalModel) {
        super(originalModel);
    }

    // Retexture legacy quad access
    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random
    ) {
        return originalModel.getQuads(state, side, random);
    }

    // Retexture the chunk render quads
    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType
    ) {
        List<BakedQuad> quads = originalModel.getQuads(state, side, random, modelData, renderType);
        return Boolean.TRUE.equals(modelData.get(BOUND_TEXTURE)) ? retexture(quads) : quads;
    }

    // Replace each original docking connector texture atlas region
    private static List<BakedQuad> retexture(List<BakedQuad> quads) {
        return BakedModelHelper.swapSprites(quads, sprite -> {
            TextureAtlasSprite replacement = replacementSprite(sprite.contents().name());
            return replacement == null ? sprite : replacement;
        });
    }

    // Resolve the target atlas sprite
    private static @Nullable TextureAtlasSprite replacementSprite(ResourceLocation original) {
        ResourceLocation replacement = DOCK_TEXTURE.equals(original) ? SHIP_DOCK_TEXTURE
                : DOCK_POWERED_TEXTURE.equals(original) ? SHIP_DOCK_POWERED_TEXTURE
                : DOCK_DETAIL_TEXTURE.equals(original) ? SHIP_DOCK_DETAIL_TEXTURE : null;
        return replacement == null ? null : Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(replacement);
    }

}
