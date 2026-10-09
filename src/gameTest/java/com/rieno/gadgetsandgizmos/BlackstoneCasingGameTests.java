package com.rieno.gadgetsandgizmos;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.create.encasing.CustomBeltCasing;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTCasings;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Verify encasing interactions with the registered blocks and active mixins
@GameTestHolder(CreateThrusters.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlackstoneCasingGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @GameTest(template = "empty")
    public static void shaftsAndCogsKeepTheirAxes(GameTestHelper ctx){
        Block[] bases = {AllBlocks.SHAFT.get(), AllBlocks.COGWHEEL.get(), AllBlocks.LARGE_COGWHEEL.get()};
        Block[] variants = {CTBlocks.BLACKSTONE_ENCASED_SHAFT.get(), CTBlocks.BLACKSTONE_ENCASED_COGWHEEL.get(),
                CTBlocks.BLACKSTONE_ENCASED_LARGE_COGWHEEL.get()};
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = CTItems.BLACKSTONE_CASING.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = ctx.absolutePos(new BlockPos(3, 2, 3));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for(int idx = 0; idx < bases.length; idx++){
            for(Direction.Axis axis : Direction.Axis.values()){
                BlockState state = bases[idx].defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
                ctx.getLevel().setBlock(pos, state, 3);
                ItemInteractionResult res = state.useItemOn(stack, ctx.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                BlockState encased = ctx.getLevel().getBlockState(pos);
                ctx.assertTrue(res == ItemInteractionResult.SUCCESS, "Blackstone casing was rejected");
                ctx.assertTrue(encased.is(variants[idx]), "Wrong encased variant");
                ctx.assertTrue(encased.getValue(BlockStateProperties.AXIS) == axis, "Axis changed during encasing");
                BlockEntity be = ctx.getLevel().getBlockEntity(pos);
                ctx.assertTrue(be != null && be.getType().isValid(encased), "Invalid encased block entity");
                ((IWrenchable) variants[idx]).onSneakWrenched(encased, new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                ctx.assertTrue(ctx.getLevel().getBlockState(pos).is(bases[idx]), "Wrench did not restore the base block");
            }
        }
        ctx.assertTrue(stack.getCount() == 1, "Encasing consumed the casing");
        ctx.succeed();
    }

    @GameTest(template = "empty")
    public static void cogsKeepConnectedShafts(GameTestHelper ctx){
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = CTItems.BLACKSTONE_CASING.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = ctx.absolutePos(new BlockPos(3, 2, 3));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        for(Block cog : new Block[]{AllBlocks.COGWHEEL.get(), AllBlocks.LARGE_COGWHEEL.get()}){
            ctx.getLevel().setBlock(pos.above(), AllBlocks.SHAFT.getDefaultState(), 3);
            ctx.getLevel().setBlock(pos.below(), AllBlocks.SHAFT.getDefaultState(), 3);
            BlockState state = cog.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y);
            ctx.getLevel().setBlock(pos, state, 3);
            state.useItemOn(stack, ctx.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            BlockState encased = ctx.getLevel().getBlockState(pos);
            ctx.assertTrue(encased.getValue(EncasedCogwheelBlock.TOP_SHAFT)
                    && encased.getValue(EncasedCogwheelBlock.BOTTOM_SHAFT), "Connected cog shafts were lost");
        }
        ctx.succeed();
    }

    @GameTest(template = "empty")
    public static void beltsSaveAndClearTheirMaterial(GameTestHelper ctx){
        Player player = ctx.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = CTItems.BLACKSTONE_CASING.get().getDefaultInstance();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos pos = ctx.absolutePos(new BlockPos(3, 2, 3));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        BlockState state = AllBlocks.BELT.getDefaultState();
        ctx.getLevel().setBlock(pos, state, 3);
        ctx.assertTrue(state.useItemOn(stack, ctx.getLevel(), player, InteractionHand.MAIN_HAND, hit)
                == ItemInteractionResult.SUCCESS, "Blackstone casing was rejected by the belt");
        BeltBlockEntity belt = (BeltBlockEntity) ctx.getLevel().getBlockEntity(pos);
        ctx.assertTrue(CTCasings.BLACKSTONE.equals(((CustomBeltCasing) belt).getCasingMaterial()), "Belt material was not applied");
        ctx.assertTrue(belt.casing == BeltBlockEntity.CasingType.ANDESITE && belt.getBlockState().getValue(BeltBlock.CASING), "Belt casing mechanics changed");
        CompoundTag tag = belt.saveWithFullMetadata(ctx.getLevel().registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(pos, belt.getBlockState(), tag, ctx.getLevel().registryAccess());
        ctx.assertTrue(loaded instanceof CustomBeltCasing material && CTCasings.BLACKSTONE.equals(material.getCasingMaterial()), "Belt material did not survive reload");
        belt.getBlockState().useItemOn(AllBlocks.ANDESITE_CASING.asStack(), ctx.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        ctx.assertTrue(((CustomBeltCasing) belt).getCasingMaterial() == null, "Andesite casing did not replace Blackstone");
        belt.getBlockState().useItemOn(stack, ctx.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        belt.getBlockState().useItemOn(AllBlocks.BRASS_CASING.asStack(), ctx.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        ctx.assertTrue(belt.casing == BeltBlockEntity.CasingType.BRASS && ((CustomBeltCasing) belt).getCasingMaterial() == null, "Brass casing did not replace Blackstone");
        belt.getBlockState().useItemOn(stack, ctx.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        ((IWrenchable) state.getBlock()).onWrenched(belt.getBlockState(), new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        ctx.assertTrue(belt.casing == BeltBlockEntity.CasingType.NONE && ((CustomBeltCasing) belt).getCasingMaterial() == null, "Wrench did not clear the belt material");
        ctx.assertTrue(stack.getCount() == 1, "Belt encasing consumed the casing");
        ctx.succeed();
    }
}
