package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import com.rieno.gadgetsandgizmos.lib.geometry.BlockVolumeProvider;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

// Break real moving arm cells and verify the shorter assembly retains its head and hitboxes
@GameTestHolder("createthrusters_scissor_piston")
@PrefixGameTestTemplate(false)
public final class ScissorPistonGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Rebuild interior cells after removal without losing or duplicating extension items
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingWhileExtendingKeepsEveryFacingConnected(GameTestHelper helper){
        for(Direction facing : Direction.values()){
            try(var fixture = new Fixture(helper, facing, 6)){
                fixture.extendTo(5.5);
                double prev = fixture.piston.getExtension();
                fixture.breakCell(2);
                helper.assertTrue(fixture.piston.getMaxRange() == 5, "Breaking did not remove exactly one extension");
                helper.assertTrue(fixture.piston.getExtension() <= 5, "Breaking retained an excessive extension");
                helper.assertTrue(fixture.arm(2) == null, "The arm was recreated inside its removal callback");
                fixture.piston.tick();
                fixture.assertCells(5);
                var arm = fixture.arm(2);
                CompoundTag data = arm.saveWithoutMetadata(helper.getLevel().registryAccess());
                helper.assertTrue(data.getDouble("PreviousExtension") == data.getDouble("Extension"),
                        "A recreated arm interpolated from zero instead of the shared pose");
                helper.assertTrue(fixture.arm(6) == null, "The removed capacity retained a trailing arm cell");
                helper.assertTrue(fixture.items() == 1, "Breaking did not drop exactly one Scissor Arms item");
                helper.assertTrue(prev > fixture.piston.getExtension(), "The head did not shorten with its arm chain");
                fixture.piston.setSpeed(0);
                double stopped = fixture.piston.getExtension();
                for(int idx = 0; idx < 5; idx++) fixture.piston.tick();
                helper.assertTrue(fixture.piston.getExtension() == stopped, "The stopped piston changed its span");
                fixture.assertCells(5);
            }
        }
        helper.succeed();
    }

    // The built-in arm capacity must recover its cell without creating a removable item
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingTheMinimumRangeRepairsItsHitbox(GameTestHelper helper){
        try(var fixture = new Fixture(helper, Direction.UP, 1)){
            fixture.extendTo(.75);
            fixture.breakCell(1);
            fixture.piston.tick();
            fixture.assertCells(1);
            helper.assertTrue(fixture.piston.getMaxRange() == 1, "Breaking reduced the built-in arm capacity");
            helper.assertTrue(fixture.items() == 0, "The built-in arm capacity dropped an extension item");
        }
        helper.succeed();
    }

    // Let normal world ticks process two breaks without splitting off stale arm blocks
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void repeatedBreaksSurviveTheFollowingPhysicsTicks(GameTestHelper helper){
        var fixture = new Fixture(helper, Direction.UP, 6);
        fixture.extendTo(5.5);
        fixture.breakCell(2);
        helper.startSequence().thenExecuteAfter(3, () -> {
            fixture.assertCells(5);
            fixture.breakCell(1);
        }).thenExecuteAfter(3, () -> {
            try{
                fixture.assertCells(4);
                helper.assertTrue(fixture.items() == 2, "Repeated breaking duplicated or lost extension drops");
            }finally{ fixture.close(); }
        }).thenSucceed();
    }

    // Every query cell must select the same full column in every piston direction
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hitboxesShareOneContinuousVolume(GameTestHelper helper){
        for(Direction facing : Direction.values()){
            try(var fixture = new Fixture(helper, facing, 6)){
                fixture.extendTo(5.5);
                var head = (ScissorPistonLinkBlockEntity)helper.getLevel().getBlockEntity(fixture.piston.getMountedLocalPos());
                var volume = head.getBlockVolume();
                AABB expected = volume.bounds().move(volume.origin());
                for(int distance = 0; distance <= 5; distance++){
                    BlockPos pos = volume.origin().relative(facing.getOpposite(), distance);
                    var be = helper.getLevel().getBlockEntity(pos);
                    var provider = (BlockVolumeProvider)be;
                    helper.assertTrue(provider.getBlockVolume() == volume, "A forwarding cell created its own hitbox");
                    var shape = helper.getLevel().getBlockState(pos).getShape(helper.getLevel(), pos);
                    helper.assertTrue(shape.toAabbs().size() == 1 && shape.bounds().move(pos).equals(expected),
                            "A forwarding cell exposed a segmented selection outline");
                }
                var hidden = fixture.arm(6);
                helper.assertTrue(hidden.getBlockState().getShape(helper.getLevel(), hidden.getBlockPos()).isEmpty(),
                        "An unoccupied forwarding cell retained an invisible hitbox");
            }
        }
        helper.succeed();
    }

    // The head position must control hitbox height during extension and retraction
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theContinuousVolumeFollowsTheHead(GameTestHelper helper){
        try(var fixture = new Fixture(helper, Direction.UP, 6)){
            fixture.extendTo(2);
            var head = (ScissorPistonLinkBlockEntity)helper.getLevel().getBlockEntity(fixture.piston.getMountedLocalPos());
            double small = head.getBlockVolume().bounds().getYsize();
            fixture.extendTo(5.5);
            double large = head.getBlockVolume().bounds().getYsize();
            helper.assertTrue(large > small && Math.abs(large - fixture.piston.getExtension() - .25) < 1E-6,
                    "The volume did not grow with the moving head");
            for(int idx = 0; idx < 64 && fixture.piston.getExtension() > 1.5; idx++){
                fixture.piston.setSpeed(-128);
                fixture.piston.tick();
            }
            var volume = head.getBlockVolume();
            helper.assertTrue(volume.bounds().getYsize() < small
                            && Math.abs(volume.bounds().getYsize() - fixture.piston.getExtension() - .25) < 1E-6,
                    "The volume did not shrink with the moving head");
            var arm = fixture.arm(1);
            var data = arm.saveWithoutMetadata(helper.getLevel().registryAccess());
            data.putDouble("Extension", 0);
            data.putInt("MaxRange", 1);
            arm.loadWithComponents(data, helper.getLevel().registryAccess());
            helper.assertTrue(arm.getBlockVolume() == volume, "Stale arm data changed the shared hitbox");
        }
        helper.succeed();
    }

    // Mining either the upper arm or a forwarding cell must preserve the actual head
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void continuousVolumeMiningShortensWithoutDestroyingTheHead(GameTestHelper helper){
        for(int distance : new int[]{0, 2}){
            try(var fixture = new Fixture(helper, Direction.UP, 6)){
                fixture.extendTo(5.5);
                Player player = fixture.player(GameType.SURVIVAL, distance == 0 ? .25 : -1.5);
                BlockPos pos = fixture.piston.getMountedLocalPos().below(distance);
                var state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(!state.onDestroyedByPlayer(helper.getLevel(), pos, player, true,
                        helper.getLevel().getFluidState(pos)), "Mining removed a shared-volume block");
                fixture.piston.tick();
                fixture.assertCells(5);
                helper.assertTrue(fixture.items() == 1, "Mining did not drop exactly one extension");
            }
        }
        helper.succeed();
    }

    // Creative shortening must not create items and the head plate must remain independently removable
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theHeadPlateAndCreativeMiningKeepTheirOwnRules(GameTestHelper helper){
        try(var fixture = new Fixture(helper, Direction.UP, 6)){
            fixture.extendTo(5.5);
            Player player = fixture.player(GameType.CREATIVE, .25);
            BlockPos pos = fixture.piston.getMountedLocalPos();
            var state = helper.getLevel().getBlockState(pos);
            String ray = fixture.ray(player);
            helper.assertTrue(!state.onDestroyedByPlayer(helper.getLevel(), pos, player, true,
                    helper.getLevel().getFluidState(pos)), "Creative arm mining removed the actual head: " + ray);
            fixture.piston.tick();
            fixture.assertCells(5);
            helper.assertTrue(fixture.items() == 0, "Creative arm mining spawned an item");
        }
        try(var fixture = new Fixture(helper, Direction.UP, 6)){
            fixture.extendTo(5.5);
            Player player = fixture.player(GameType.SURVIVAL, .9);
            BlockPos pos = fixture.piston.getMountedLocalPos();
            var state = helper.getLevel().getBlockState(pos);
            int before = fixture.piston.getMaxRange();
            helper.assertTrue(state.onDestroyedByPlayer(helper.getLevel(), pos, player, true,
                    helper.getLevel().getFluidState(pos)), "The head plate could no longer be broken");
            boolean queued = fixture.piston.saveWithoutMetadata(helper.getLevel().registryAccess()).getBoolean("PendingHeadDetach");
            int after = fixture.piston.getMaxRange();
            fixture.piston.tick();
            helper.assertTrue(fixture.piston.getMaxRange() == 6 && fixture.piston.getMountedSubLevelId() == null,
                    "Breaking the head plate changed range to " + fixture.piston.getMaxRange()
                            + " with mounted id " + fixture.piston.getMountedSubLevelId() + " and detach queued " + queued
                            + " before " + before + " after " + after);
        }
        helper.succeed();
    }

    // A wooden axe must recover an extension from both ends of the shared mining path
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void woodenAxesHarvestArmsAndPickaxesDoNot(GameTestHelper helper){
        var armState = CTBlocks.SCISSOR_PISTON_ARM.get().defaultBlockState();
        for(Item item : new Item[]{Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE,
                Items.DIAMOND_AXE, Items.NETHERITE_AXE}){
            helper.assertTrue(new ItemStack(item).isCorrectToolForDrops(armState), "An axe could not harvest the wooden arms");
        }
        for(Item item : new Item[]{Items.WOODEN_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE}){
            helper.assertTrue(!new ItemStack(item).isCorrectToolForDrops(armState), "A pickaxe still harvested the wooden arms");
        }
        helper.assertTrue(!ItemStack.EMPTY.isCorrectToolForDrops(armState), "An empty hand counted as an axe");
        for(int distance : new int[]{0, 2}){
            for(Item item : new Item[]{Items.WOODEN_AXE, Items.IRON_PICKAXE}){
                try(var fixture = new Fixture(helper, Direction.UP, 6)){
                    fixture.extendTo(5.5);
                    Player player = fixture.player(GameType.SURVIVAL, distance == 0 ? .25 : -1.5);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
                    BlockPos pos = fixture.piston.getMountedLocalPos().below(distance);
                    var state = helper.getLevel().getBlockState(pos);
                    helper.assertTrue(!state.onDestroyedByPlayer(helper.getLevel(), pos, player,
                            state.canHarvestBlock(helper.getLevel(), pos, player), helper.getLevel().getFluidState(pos)),
                            "Mining removed a forwarding block instead of shortening the arms");
                    fixture.piston.tick();
                    fixture.assertCells(5);
                    helper.assertTrue(fixture.items() == (item == Items.WOODEN_AXE ? 1 : 0),
                            "The shared arm hitbox ignored the axe drop requirement");
                }
            }
        }
        helper.succeed();
    }

    // Keep one isolated base and its live native head available to each assertion
    private static final class Fixture implements AutoCloseable{
        // Retain the assembly and its world drop area until cleanup
        private final GameTestHelper helper;
        private final ScissorPistonBlockEntity piston;
        private final ServerSubLevel child;
        private final Direction facing;
        private final AABB drops;

        // Assemble a bare moving head with the requested persisted arm capacity
        private Fixture(GameTestHelper helper, Direction facing, int range){
            this.helper = helper;
            this.facing = facing;
            BlockPos pos = helper.absolutePos(new BlockPos(8, 8, 8));
            if(helper.getLevel().getBlockEntity(pos) instanceof ScissorPistonBlockEntity old) old.detachMountedSubLevel(true);
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, CTBlocks.SCISSOR_PISTON.get().defaultBlockState()
                    .setValue(ScissorPistonBlock.FACING, facing), 3);
            piston = (ScissorPistonBlockEntity)helper.getLevel().getBlockEntity(pos);
            CompoundTag data = piston.saveWithoutMetadata(helper.getLevel().registryAccess());
            data.putInt("MaxRange", range);
            piston.loadWithComponents(data, helper.getLevel().registryAccess());
            piston.tick();
            helper.assertTrue(piston.tryAssembleMountedBlock(), "The test piston could not assemble its head for "
                    + facing + " against " + helper.getLevel().getBlockState(pos.relative(facing))
                    + " with mounted id " + piston.getMountedSubLevelId());
            child = (ServerSubLevel)piston.getMountedSubLevelHandle();
            drops = new AABB(pos).inflate(12);
            piston.setSpeed(facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 128 : -128);
        }

        // Advance real kinetic movement far enough to expose the interior cells
        private void extendTo(double extension){
            for(int idx = 0; idx < 64 && piston.getExtension() < extension; idx++){
                piston.setSpeed(facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 128 : -128);
                piston.tick();
            }
            helper.assertTrue(piston.getExtension() >= extension, "The test piston did not extend for "
                    + facing + " from " + piston.getExtension());
        }

        // Remove the actual embedded block so the ordinary removal callback runs
        private void breakCell(int distance){
            BlockPos pos = piston.getMountedLocalPos().relative(facing.getOpposite(), distance);
            child.getPlot().getEmbeddedLevelAccessor().setBlock(pos.subtract(child.getPlot().getCenterBlock()),
                    Blocks.AIR.defaultBlockState(), 3);
        }

        // Resolve a surviving arm directly from the head's plot
        private ScissorPistonArmBlockEntity arm(int distance){
            BlockPos pos = piston.getMountedLocalPos().relative(facing.getOpposite(), distance);
            var be = helper.getLevel().getBlockEntity(pos);
            return be instanceof ScissorPistonArmBlockEntity arm ? arm : null;
        }

        // Aim a real player ray at the requested part of the moving column
        private Player player(GameType mode, double localHeight){
            Player player = helper.makeMockPlayer(mode);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_AXE));
            Vec3 target = child.logicalPose().transformPosition(new Vec3(piston.getMountedLocalPos().getX() + .5,
                    piston.getMountedLocalPos().getY() + localHeight, piston.getMountedLocalPos().getZ() + .5));
            player.setPos(target.x, target.y - player.getEyeHeight(), target.z + 2);
            player.setYRot(180);
            player.setYHeadRot(180);
            player.setXRot(0);
            return player;
        }

        // Include the active local ray when a head-part assertion fails
        private String ray(Player player){
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
            Vec3 from = SableTransformApi.contextualPosition(helper.getLevel(), null,
                    SableTransformApi.projectOut(helper.getLevel(), eye), child).subtract(piston.getMountedLocalPos().getCenter());
            Vec3 to = SableTransformApi.contextualPosition(helper.getLevel(), null,
                    SableTransformApi.projectOut(helper.getLevel(), end), child).subtract(piston.getMountedLocalPos().getCenter());
            return from + " to " + to + " direction " + player.getViewVector(1)
                    + " volume " + ((ScissorPistonLinkBlockEntity)helper.getLevel().getBlockEntity(piston.getMountedLocalPos())).getBlockVolume();
        }

        // Check parent ownership, complete cell coverage and the head's physical position
        private void assertCells(int range){
            for(int distance = 1; distance <= range; distance++){
                var arm = arm(distance);
                helper.assertTrue(arm != null && arm.getMaxRange() == range
                                && arm.getCellDistanceFromHead() == distance
                                && arm.getExtension() == piston.getExtension(),
                        facing + " lost or desynchronized arm cell " + distance);
            }
            var link = (ScissorPistonLinkBlockEntity)helper.getLevel().getBlockEntity(piston.getMountedLocalPos());
            helper.assertTrue(link != null && link.getParentPiston() == piston, "The moving head lost its parent");
            Vec3 head = child.logicalPose().transformPosition(piston.getMountedLocalPos().getCenter());
            Vec3 local = piston.getBlockPos().getCenter().add(facing.getStepX() * piston.getExtension(),
                    facing.getStepY() * piston.getExtension(), facing.getStepZ() * piston.getExtension());
            Vector3d world = NestedAssemblyFrame.resolve(piston).toWorldPosition(new Vector3d(local.x, local.y, local.z));
            Vec3 expected = new Vec3(world.x, world.y, world.z);
            // Allow the native float precision at Sable's distant test coordinates
            for(Direction.Axis axis : Direction.Axis.values()){
                double tolerance = .02 + Math.ulp((float)expected.get(axis)) * .5;
                helper.assertTrue(Math.abs(head.get(axis) - expected.get(axis)) < tolerance,
                        "The moving head disagrees with the rebuilt span: " + head + " expected " + expected);
            }
        }

        // Count only extension drops generated by this fixture
        private int items(){
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, drops).stream()
                    .filter(entity -> entity.getItem().is(CTItems.SCISSOR_ARMS.get()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
        }

        // Remove the native head and test drops before trying another facing
        @Override
        public void close(){
            piston.detachMountedSubLevel(true);
            helper.getLevel().setBlock(piston.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            for(var entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, drops)) entity.discard();
        }
    }
}
