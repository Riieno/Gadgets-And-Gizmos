package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.DoubleButtonBlock;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.List;

// Apply copycat materials to each half of the double-button model
public class CopycatDoubleButtonModel extends CopycatModel {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the copycat double button model
    public CopycatDoubleButtonModel(BakedModel originalModel) {
        super(originalModel);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the cropped quads
    @Override
    protected List<BakedQuad> getCroppedQuads(BlockState state, Direction side, RandomSource random,
                                              BlockState material, ModelData wrappedData, RenderType renderType) {
        ArrayList<BakedQuad> quads = new ArrayList<>();
        if (renderType == null || originalModel.getRenderTypes(state, random, ModelData.EMPTY).contains(renderType)) {
            quads.addAll(originalModel.getQuads(state, side, random, ModelData.EMPTY, renderType));
        }

        BakedModel materialModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
        List<BakedQuad> materialQuads = materialModel.getQuads(material, side, random, wrappedData, renderType);
        for (DoubleButtonBlockEntity.ButtonHalf btn : DoubleButtonBlockEntity.ButtonHalf.values()) {
            AABB bounds = DoubleButtonBlock.buttonBounds(state, btn);
            for (BakedQuad quad : materialQuads) {
                quads.add(BakedQuadHelper.cloneWithCustomGeometry(quad,
                        BakedModelHelper.cropAndMove(quad.getVertices(), quad.getSprite(), bounds,
                                net.minecraft.world.phys.Vec3.ZERO)));
            }
        }
        return quads;
    }

    // Get the render types
    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        BlockState material = CopycatModel.getMaterial(data);
        BakedModel materialModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
        return ChunkRenderTypeSet.union(
                originalModel.getRenderTypes(state, random, ModelData.EMPTY),
                materialModel.getRenderTypes(material, random, ModelData.EMPTY));
    }
}
