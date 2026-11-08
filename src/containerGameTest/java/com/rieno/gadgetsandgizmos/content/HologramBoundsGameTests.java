package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.hologram.HologramRegion;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramSlice;
import com.rieno.gadgetsandgizmos.lib.hologram.SurfaceHologramSampler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Verify bounded surface sampling against actual heightmaps and exposed underground passages
@GameTestHolder("createthrusters_hologram_bounds")
@PrefixGameTestTemplate(false)
public final class HologramBoundsGameTests{
    @GameTest(template = "empty", batch = "surfaceBand", timeoutTicks = 200)
    public static void surfaceKeepsMountainsAndTenBlocksOfGroundWithoutDeepCaves(GameTestHelper helper){
        var pos = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        var chunk = flat(helper, pos, 64);
        for(int x = 0; x < 16; x++) set(chunk, x, 20, 8, Blocks.AIR);
        for(int x = 6; x <= 9; x++) for(int z = 6; z <= 9; z++){
            for(int y = 65; y <= 100; y++) set(chunk, x, y, z, Blocks.STONE);
        }
        var slice = HologramSlice.surface(65, helper.getLevel().getMinBuildHeight(), helper.getLevel().getMaxBuildHeight());
        var sampled = SurfaceHologramSampler.sample(helper.getLevel(), pos, false, slice);
        helper.assertTrue(!sampled.blocks().isEmpty(), "The surface band was empty");
        helper.assertTrue(sampled.blocks().stream().allMatch(block -> slice.contains(block.pos().getY())),
                "Deep terrain escaped the surface band");
        helper.assertTrue(sampled.blocks().stream().anyMatch(block -> block.pos().getY() == 100),
                "Bounding the surface removed the mountain peak");
        helper.assertTrue(sampled.blocks().stream().anyMatch(block -> block.pos().getY() == 64),
                "The flat surface beside the mountain disappeared");
        helper.assertTrue(sampled.blocks().stream().anyMatch(block -> block.pos().equals(new BlockPos(8, 54, 8))
                        && (block.faces() & 1 << Direction.DOWN.ordinal()) != 0),
                "The bottom cut did not retain ten blocks below the flat surface or close its exposed faces");
        var automatic = SurfaceHologramSampler.sample(helper.getLevel(), pos, false);
        helper.assertTrue(automatic.blocks().stream().noneMatch(block -> block.pos().getY() <= 21),
                "The compatibility surface API still exported the deep passage");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "surfaceBase", timeoutTicks = 200)
    public static void lowestChunkAveragePreservesValleysWithoutFollowingOneDeepRavine(GameTestHelper helper){
        var center = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        var region = new HologramRegion(center, 3);
        for(int x = region.minX(); x <= region.maxX(); x++) for(int z = region.minZ(); z <= region.maxZ(); z++){
            flat(helper, new ChunkPos(x, z), x == region.maxX() && z == region.maxZ() ? 120 : 64);
        }
        var valley = helper.getLevel().getChunk(center.x, center.z);
        for(int y = 6; y <= 64; y++) set(valley, 8, y, 8, Blocks.AIR);
        set(valley, 8, 5, 8, Blocks.STONE);
        double lowest = SurfaceHologramSampler.lowestAverageHeight(helper.getLevel(), region, 200);
        helper.assertTrue(Math.abs(lowest - (65 - 59D / 256)) < 1E-6,
                "Surface base followed an individual ravine instead of the lowest chunk mean: " + lowest);
        helper.assertTrue(SurfaceHologramSampler.averageHeight(helper.getLevel(), region, 200) > lowest + 5,
                "The mountain fixture did not distinguish the lowest mean from the regional average");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "cutawayBand", timeoutTicks = 200)
    public static void manualCutawaysStillShowSelectedUndergroundTerrainAndMaskOres(GameTestHelper helper){
        var pos = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        var chunk = flat(helper, pos, 64);
        for(int x = 0; x < 16; x++) set(chunk, x, 20, 8, Blocks.AIR);
        set(chunk, 8, 19, 8, Blocks.DIAMOND_ORE);
        var slice = HologramSlice.at(65, -40);
        var sampled = SurfaceHologramSampler.sample(helper.getLevel(), pos, true, slice);
        helper.assertTrue(sampled.blocks().stream().allMatch(block -> slice.contains(block.pos().getY())),
                "A manual cutaway escaped its existing bounds");
        helper.assertTrue(sampled.blocks().stream().anyMatch(block -> block.pos().equals(new BlockPos(8, 19, 8))
                        && block.state().is(Blocks.STONE)), "The selected underground passage or ore masking was lost");
        helper.assertTrue(chunk.getBlockState(new BlockPos(pos.getMinBlockX() + 8, 19, pos.getMinBlockZ() + 8))
                .is(Blocks.DIAMOND_ORE), "Sampling modified its source ore");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "unloadedSurface", timeoutTicks = 100)
    public static void missingChunksRemainUnloadedAndUseTheFallbackHeight(GameTestHelper helper){
        var level = helper.getLevel();
        var pos = new ChunkPos(1_000_000, 1_000_000);
        helper.assertTrue(level.getChunkSource().getChunkNow(pos.x, pos.z) == null, "The remote test chunk was already loaded");
        helper.assertTrue(SurfaceHologramSampler.lowestAverageHeight(level, new HologramRegion(pos, 1), 70) == 70,
                "Missing terrain did not use the fallback height");
        helper.assertTrue(SurfaceHologramSampler.sample(level, pos, false).blocks().isEmpty(), "Missing terrain produced geometry");
        helper.assertTrue(level.getChunkSource().getChunkNow(pos.x, pos.z) == null, "Hologram sampling loaded its source chunk");
        helper.succeed();
    }

    private static LevelChunk flat(GameTestHelper helper, ChunkPos pos, int top){
        var chunk = helper.getLevel().getChunk(pos.x, pos.z);
        for(int x = 0; x < 16; x++) for(int z = 0; z < 16; z++){
            int prev = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            for(int y = 10; y <= top; y++) set(chunk, x, y, z, Blocks.STONE);
            for(int y = top + 1; y <= prev; y++) set(chunk, x, y, z, Blocks.AIR);
        }
        return chunk;
    }

    private static void set(LevelChunk chunk, int x, int y, int z, net.minecraft.world.level.block.Block block){
        chunk.setBlockState(new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z),
                block.defaultBlockState(), false);
    }
}
