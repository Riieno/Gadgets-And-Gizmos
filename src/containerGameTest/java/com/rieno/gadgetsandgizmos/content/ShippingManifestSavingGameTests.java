package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.createdieselgenerators.CreateDieselGeneratorsManifestCompat;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ShippingManifestSavingGameTests{
    private ShippingManifestSavingGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 100)
    public static void unloadedTargetsStayUnloaded(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = new BlockPos(1_000_000, 80, 1_000_000);
        ctx.assertTrue(!level.isLoaded(pos), "Target chunk was already loaded");
        ctx.assertTrue(ShippingManifestBlock.findItemHandler(level, pos, Direction.NORTH) == null,
                "Unavailable target exposed items");
        ctx.assertTrue(ShippingManifestBlock.findFluidHandler(level, pos, Direction.NORTH) == null,
                "Unavailable target exposed fluids");
        ctx.assertTrue(ShippingManifestBlock.findEnergyHandler(level, pos, Direction.NORTH) == null,
                "Unavailable target exposed energy");
        ctx.assertTrue(ShippingManifestBlock.findLongEnergyHandler(level, pos) == null,
                "Unavailable target exposed exact energy");
        ctx.assertTrue(ShippingManifestBlock.resolveFluidHandler(level, pos, Direction.NORTH, true) == null,
                "Unavailable target exposed combined fluids");
        ctx.assertTrue(!CreateDieselGeneratorsManifestCompat.isDistillationTank(level, pos),
                "Unavailable target was treated as a distillation tank");
        ctx.assertTrue(CreateDieselGeneratorsManifestCompat.findCombinedHandler(level, pos) == null,
                "Unavailable tower exposed a combined handler");
        ctx.assertTrue(!level.isLoaded(pos), "Storage discovery loaded the target chunk");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 100)
    public static void loadedStorageStillExposesCapabilities(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(3, 3, 3));
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        var handler = ShippingManifestBlock.findItemHandler(level, pos, Direction.NORTH);
        ctx.assertTrue(handler != null, "Loaded chest lost its sided item capability");
        ctx.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty(),
                "Loaded chest rejected an item");
        var unsided = ShippingManifestBlock.findItemHandler(level, pos, null);
        ctx.assertTrue(unsided != null && unsided.getStackInSlot(0).is(Items.DIAMOND),
                "Unsided lookup did not expose the same inventory");
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 100)
    public static void savingDoesNotLoadAttachedChunk(GameTestHelper ctx){
        var level = ctx.getLevel();
        var state = CTBlocks.SHIPPING_MANIFEST.get().defaultBlockState();
        BlockPos pos = new BlockPos(1_000_128, 80, 1_000_128);
        BlockPos target = ShippingManifestBlock.attachedTargetPos(pos, state);
        ctx.assertTrue(!level.isLoaded(target), "Attached chunk was already loaded");
        var manifest = new ShippingManifestBlockEntity(pos, state);
        manifest.setLevel(level);
        CompoundTag initial = new CompoundTag();
        initial.putBoolean("CombinedManifest", true);
        initial.putInt("ManifestColor", 0x123456);
        initial.putBoolean("ManifestGlowing", true);
        manifest.loadWithComponents(initial, level.registryAccess());
        CompoundTag saved = manifest.saveWithoutMetadata(level.registryAccess());
        CompoundTag safe = new CompoundTag();
        manifest.writeSafe(safe, level.registryAccess());
        CompoundTag packet = new CompoundTag();
        manifest.write(packet, level.registryAccess(), true);
        for(CompoundTag tag : new CompoundTag[]{saved, safe, packet}){
            ctx.assertTrue(tag.getBoolean("CombinedManifest"), "Saving lost combined mode");
            ctx.assertTrue(tag.getInt("ManifestColor") == 0x123456, "Saving lost the manifest color");
            ctx.assertTrue(tag.getBoolean("ManifestGlowing"), "Saving lost glow mode");
        }
        ctx.assertTrue(!level.isLoaded(target), "Saving loaded the attached chunk");
        ctx.succeed();
    }
}
