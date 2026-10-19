package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTCasings;
import com.rieno.gadgetsandgizmos.lib.client.create.BeltCasingModels;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogCTBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.createmod.catnip.data.Couple;

// Register connected textures for Blackstone Casing
public final class BlackstoneCasingConnectedTextures {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final CTSpriteShiftEntry BLACKSTONE_CASING = CTSpriteShifter.getCT(
            AllCTTypes.OMNIDIRECTIONAL,
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/blackstone_casing"),
            ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/blackstone_casing_connected"));
    private static final ConnectedTextureBehaviour BEHAVIOUR = new EncasedCTBehaviour(BLACKSTONE_CASING);
    private static final ConnectedTextureBehaviour COG_BEHAVIOUR = new EncasedCogCTBehaviour(
            BLACKSTONE_CASING, Couple.create(BLACKSTONE_CASING, BLACKSTONE_CASING));
    private static final ConnectedTextureBehaviour LARGE_COG_BEHAVIOUR = new EncasedCogCTBehaviour(BLACKSTONE_CASING);
    private static boolean registered;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the Blackstone Casing connected textures
    private BlackstoneCasingConnectedTextures() {
    }

    // Register the Blackstone Casing connectivity
    public static void register() {
        if (registered || CTBlocks.BLACKSTONE_CASING == null) return;
        CreateClient.CASING_CONNECTIVITY.makeCasing(CTBlocks.BLACKSTONE_CASING.get(), BLACKSTONE_CASING);
        CreateClient.CASING_CONNECTIVITY.make(CTBlocks.BLACKSTONE_ENCASED_SHAFT.get(), BLACKSTONE_CASING,
                (state, face) -> face.getAxis() != state.getValue(BlockStateProperties.AXIS));
        CreateClient.CASING_CONNECTIVITY.make(CTBlocks.BLACKSTONE_ENCASED_COGWHEEL.get(), BLACKSTONE_CASING,
                (state, face) -> face.getAxis() != state.getValue(BlockStateProperties.AXIS)
                        || !state.getValue(face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? EncasedCogwheelBlock.TOP_SHAFT : EncasedCogwheelBlock.BOTTOM_SHAFT));
        CreateClient.CASING_CONNECTIVITY.make(CTBlocks.BLACKSTONE_ENCASED_LARGE_COGWHEEL.get(), BLACKSTONE_CASING,
                (state, face) -> face.getAxis() != state.getValue(BlockStateProperties.AXIS)
                        || !state.getValue(face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? EncasedCogwheelBlock.TOP_SHAFT : EncasedCogwheelBlock.BOTTOM_SHAFT));
        BeltCasingModels.register(CTCasings.BLACKSTONE,
                ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/blackstone_casing"));
        registered = true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Wrap the Blackstone Casing model with its connected texture behaviour
    public static BakedModel wrap(BakedModel model) {
        return model instanceof CTModel ? model : new CTModel(model, BEHAVIOUR);
    }

    // Wrap each registered Blackstone casing model with its matching connectivity
    public static BakedModel wrap(BakedModel model, Block block){
        if(model instanceof CTModel) return model;
        if(block == CTBlocks.BLACKSTONE_ENCASED_COGWHEEL.get()) return new CTModel(model, COG_BEHAVIOUR);
        if(block == CTBlocks.BLACKSTONE_ENCASED_LARGE_COGWHEEL.get()) return new CTModel(model, LARGE_COG_BEHAVIOUR);
        if(block == CTBlocks.BLACKSTONE_CASING.get() || block == CTBlocks.BLACKSTONE_ENCASED_SHAFT.get()) return wrap(model);
        return model;
    }
}
