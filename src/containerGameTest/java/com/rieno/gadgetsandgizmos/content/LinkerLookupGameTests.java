package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.access.LoadedBlockEntities;
import com.rieno.gadgetsandgizmos.lib.probe.LoadedBlockAccess;
import dev.ryanhcode.sable.sublevel.tracking_points.SubLevelTrackingPointSavedData;
import dev.ryanhcode.sable.sublevel.tracking_points.TrackingPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.UUID;

// Exercise passive lookups against real chunks without waking absent craft
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class LinkerLookupGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                       FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Persisted tracking points must not turn tracker maintenance into synchronous chunk loads
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void unloadedTrackingPointsDoNotLoadWorldChunks(GameTestHelper helper) throws Exception{
        var level = helper.getLevel();
        UUID body = UUID.randomUUID();
        UUID point = UUID.randomUUID();
        BlockPos far = new BlockPos(1_234_567, 70, -1_234_567);
        BlockPos local = new BlockPos(1, 64, 1);
        helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "The unloaded fixture was already loaded");
        var tracking = SubLevelTrackingPointSavedData.getOrLoad(level);
        tracking.setTrackingPoint(point, new TrackingPoint(true, body, null,
                new Vector3d(local.getX(), local.getY(), local.getZ()),
                new Vector3d(far.getX(), far.getY(), far.getZ())));
        try{
            CompoundTag target = new CompoundTag();
            target.putString("NodeId", "unloaded:plane");
            target.putString("BlockId", "createthrusters:contraption_network_linker_plane");
            target.putString("Scope", "face");
            target.putString("Mode", "output");
            target.putUUID("CurrentSubLevelId", body);
            target.putUUID("TrackingPointId", point);
            target.putLong("CurrentLocalPos", local.asLong());
            CompoundTag controller = target.copy();
            controller.putString("Dimension", level.dimension().location().toString());
            ListTag targets = new ListTag(); targets.add(target);
            CompoundTag linker = new CompoundTag();
            linker.putUUID("LinkerId", UUID.randomUUID());
            linker.putString("Dimension", level.dimension().location().toString());
            linker.put("Targets", targets);
            linker.put("Controller", controller);
            ListTag linkers = new ListTag(); linkers.add(linker);
            CompoundTag data = new CompoundTag(); data.put("Linkers", linkers);
            var load = ContraptionNetworkLinkerTracker.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
            load.setAccessible(true);
            var tracker = (ContraptionNetworkLinkerTracker)load.invoke(null, data, level.registryAccess());
            tracker.tick(level.getServer());
            helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                    "A passive linker lookup loaded a tracking-point chunk");
            var saved = tracker.save(new CompoundTag(), level.registryAccess()).getList("Linkers", 10);
            helper.assertTrue(saved.size() == 1 && saved.getCompound(0).contains("Controller")
                    && saved.getCompound(0).getList("Targets", 10).size() == 1,
                    "Unavailability removed a stored controller or target");
        }finally{ tracking.removeTrackingPoint(point); }
        helper.succeed();
    }

    // A wrong or absent scope must not fall back to a live world block at the same coordinates
    @GameTest(template = "acc_presentation_empty", timeoutTicks = 20)
    public static void loadedLookupKeepsWorldAndSubLevelScopesSeparate(GameTestHelper helper){
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Blocks.BARREL);
        BlockPos world = helper.absolutePos(pos);
        var level = helper.getLevel();
        helper.assertTrue(LoadedBlockAccess.blockState(level, null, world).is(Blocks.BARREL), "Loaded world state was unavailable");
        helper.assertTrue(LoadedBlockAccess.blockEntity(level, null, world) == helper.getBlockEntity(pos), "Loaded entity identity changed");
        helper.assertTrue(LoadedBlockEntities.get(level, world) == helper.getBlockEntity(pos), "Installed-only lookup changed identity");
        UUID missing = UUID.randomUUID();
        helper.assertTrue(LoadedBlockAccess.blockState(level, missing, world) == null
                && LoadedBlockAccess.blockEntity(level, missing, world) == null, "An absent craft fell back to the world");
        helper.succeed();
    }

}
