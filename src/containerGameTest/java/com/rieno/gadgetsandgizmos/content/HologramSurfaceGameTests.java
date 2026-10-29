package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Check surface invalidation using registered block states without creating client resources
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class HologramSurfaceGameTests{
    @GameTest(template = "acc_presentation_empty")
    public static void exteriorHullRespectsActualGlassAndWallReplacement(GameTestHelper helper){
        for(int x = 1; x <= 5; x++) for(int y = 2; y <= 6; y++) for(int z = 1; z <= 5; z++){
            if(x == 1 || x == 5 || y == 2 || y == 6 || z == 1 || z == 5) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        for(var window : java.util.List.of(Blocks.STONE, Blocks.GLASS, Blocks.AIR, Blocks.STONE)){
            helper.setBlock(new BlockPos(1, 4, 3), window);
            var hull = new com.rieno.gadgetsandgizmos.lib.geometry.ExteriorHull(1, 2, 1, 5, 6, 5, (x, y, z) -> {
                var pos = helper.absolutePos(new BlockPos(x, y, z));
                return com.rieno.gadgetsandgizmos.lib.client.render.HologramBlockModels.opaque(helper.getLevel().getBlockState(pos), helper.getLevel(), pos);
            });
            helper.assertTrue(hull.visibleFaces(3, 4, 3) == (window == Blocks.STONE ? 0 : 63),
                    "Hull did not follow glass, openings or a restored opaque wall");
        }
        helper.succeed();
    }
    @GameTest(template = "acc_presentation_empty")
    public static void closedTankModelsPreserveTheSourceState(GameTestHelper helper){
        for(var block : java.util.List.of(com.simibubi.create.AllBlocks.FLUID_TANK.get(), com.rieno.gadgetsandgizmos.registry.CTBlocks.SMART_TANK.get())){
            for(var shape : com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.values()){
                var state = block.defaultBlockState().setValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE, shape);
                var closed = com.rieno.gadgetsandgizmos.lib.client.render.HologramBlockModels.closedTank(state);
                helper.assertTrue(closed.getBlock() == block && closed.getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE)
                        == com.simibubi.create.content.fluids.tank.FluidTankBlock.Shape.PLAIN, "Projected tank did not select a closed housing");
                helper.assertTrue(state.getValue(com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE) == shape, "Projection changed its source tank");
                for(var property : state.getProperties()) if(property != com.simibubi.create.content.fluids.tank.FluidTankBlock.SHAPE){
                    helper.assertTrue(state.getValue(property).equals(closed.getValue(property)), "Closing a tank changed its multiblock properties");
                }
            }
        }
        helper.succeed();
    }
    @GameTest(template = "acc_presentation_empty")
    public static void animationChangesKeepTheBakedSurface(GameTestHelper helper){
        var first = surface(Blocks.BARREL.defaultBlockState(), 63);
        var spinning = new HologramChunk(first.pos(), 1, 2, 3, first.blocks(), Map.of(BlockPos.ZERO, 256F));
        helper.assertTrue(first.hasSameSurface(spinning) && spinning.hasSameSurface(first), "Animation invalidated static geometry");
        helper.assertTrue(!first.equals(spinning), "Snapshot equality lost the kinetic metadata");
        helper.assertTrue(first.hasSameSurface(first) && !first.hasSameSurface(null), "Surface identity or missing snapshot failed");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty")
    public static void blocksFacesAndRemovalInvalidateTheSurface(GameTestHelper helper){
        var first = surface(Blocks.BARREL.defaultBlockState(), 63);
        helper.assertTrue(!first.hasSameSurface(surface(Blocks.STONE.defaultBlockState(), 63)), "Block replacement reused geometry");
        helper.assertTrue(!first.hasSameSurface(surface(Blocks.BARREL.defaultBlockState(), 1)), "Face changes reused geometry");
        var moved = new HologramChunk(first.pos(), 1, 2, 3,
                List.of(new HologramChunk.SurfaceBlock(new BlockPos(3, 4, 6), Blocks.BARREL.defaultBlockState(), 63)));
        helper.assertTrue(!first.hasSameSurface(moved), "Block movement reused geometry");
        helper.assertTrue(!first.hasSameSurface(new HologramChunk(first.pos(), 1, 2, 3, List.of())), "Removal retained geometry");
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty")
    public static void networkSnapshotsRetainKineticMetadata(GameTestHelper helper){
        var first = surface(Blocks.BARREL.defaultBlockState(), 63);
        var spinning = new HologramChunk(first.pos(), 1, 2, 3, first.blocks(), Map.of(new BlockPos(2, 4, 6), -128F));
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try{
            spinning.encode(buf);
            var decoded = HologramChunk.decode(buf);
            helper.assertTrue(spinning.equals(decoded), "Wire round trip changed the complete snapshot");
            helper.assertTrue(first.hasSameSurface(decoded), "Wire round trip invalidated the baked surface");
            helper.assertTrue(!buf.isReadable(), "Wire round trip left unconsumed bytes");
        }finally{ buf.release(); }
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty")
    public static void chunkAndBiomeChangesInvalidateTheSurface(GameTestHelper helper){
        var first = surface(Blocks.GRASS_BLOCK.defaultBlockState(), 63);
        helper.assertTrue(!first.hasSameSurface(new HologramChunk(new ChunkPos(1, 0), 1, 2, 3, first.blocks())), "Chunk movement reused geometry");
        for(int idx = 0; idx < 3; idx++){
            var changed = new HologramChunk(first.pos(), idx == 0 ? 4 : 1, idx == 1 ? 4 : 2, idx == 2 ? 4 : 3, first.blocks());
            helper.assertTrue(!first.hasSameSurface(changed), "Biome channel " + idx + " reused geometry");
        }
        helper.succeed();
    }

    @GameTest(template = "acc_presentation_empty")
    public static void snapshotsRetainImmutableBlockPositionsAndLists(GameTestHelper helper){
        var pos = new BlockPos.MutableBlockPos(2, 4, 6);
        var blocks = new ArrayList<HologramChunk.SurfaceBlock>();
        blocks.add(new HologramChunk.SurfaceBlock(pos, Blocks.STONE.defaultBlockState(), 63));
        var first = new HologramChunk(new ChunkPos(0, 0), 1, 2, 3, blocks);
        var copy = new HologramChunk(first.pos(), 1, 2, 3, List.copyOf(blocks));
        pos.set(3, 5, 7);
        blocks.clear();
        helper.assertTrue(first.blocks().size() == 1 && first.blocks().getFirst().pos().equals(new BlockPos(2, 4, 6)), "Source mutation changed cached geometry");
        helper.assertTrue(first.hasSameSurface(copy), "Equivalent detached snapshots differ");
        helper.succeed();
    }

    private static HologramChunk surface(net.minecraft.world.level.block.state.BlockState state, int faces){
        return new HologramChunk(new ChunkPos(0, 0), 1, 2, 3,
                List.of(new HologramChunk.SurfaceBlock(new BlockPos(2, 4, 6), state, faces)));
    }
}
