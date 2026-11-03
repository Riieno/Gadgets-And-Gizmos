package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.neoforge.CTPlayerEvents;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Exercise the real wrench path and pointer rays for every tablet mounting face
@GameTestHolder("createthrusters_tablet_views")
@PrefixGameTestTemplate(false)
public final class DiagnosticTabletViewGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Wrench use must reach Create, preserve tablet data and complete the model cycle
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wrenchCyclesViewsWithoutChangingTheMount(GameTestHelper helper){
        var level = helper.getLevel();
        var block = CTBlocks.DIAGNOSTIC_TABLET.get();
        BlockPos local = new BlockPos(2, 2, 2);
        BlockPos pos = helper.absolutePos(local);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        ItemStack wrench = new ItemStack(AllItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        for(Direction facing : Direction.values()){
            for(Direction horizontal : Direction.Plane.HORIZONTAL){
                helper.setBlock(local, block.defaultBlockState().setValue(CTDirectionalBlock.FACING, facing)
                        .setValue(DiagnosticTabletBlock.HORIZONTAL_FACING, horizontal));
                var tablet = (DiagnosticTabletBlockEntity)helper.getBlockEntity(local);
                var data = tablet.state();
                for(DiagnosticTabletView expected : new DiagnosticTabletView[]{DiagnosticTabletView.NEG_22_5,
                        DiagnosticTabletView.ANGLED_45, DiagnosticTabletView.ANGLED_22_5, DiagnosticTabletView.FLAT}){
                    var state = level.getBlockState(pos);
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos), facing, pos, false);
                    var evt = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
                    CTPlayerEvents.onRightClickBlock(evt);
                    helper.assertTrue(!evt.isCanceled(), "Tablet interaction intercepted the wrench");
                    helper.assertTrue(block.useItemOn(wrench, state, level, pos, player, InteractionHand.MAIN_HAND, hit)
                            == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "Screen interaction took priority over the wrench");
                    helper.assertTrue(wrench.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
                            "Create rejected tablet wrench use");
                    var updated = level.getBlockState(pos);
                    helper.assertTrue(updated.getValue(DiagnosticTabletBlock.VIEW) == expected, "Wrench selected the wrong model");
                    helper.assertTrue(updated.getValue(CTDirectionalBlock.FACING) == facing, "Wrench changed the mounting face");
                    helper.assertTrue(updated.getValue(DiagnosticTabletBlock.HORIZONTAL_FACING) == horizontal, "Wrench changed the viewing direction");
                    helper.assertTrue(level.getBlockEntity(pos) == tablet && tablet.state().equals(data), "Model cycling replaced tablet data");
                    var saved = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), NbtUtils.writeBlockState(updated));
                    helper.assertTrue(saved == updated, "Saved block state lost the selected model");
                }
            }
        }
        helper.succeed();
    }

    // A hit on the enclosing selection shape must resolve to the rendered screen coordinates
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tiltedScreensMatchPlayerRaysOnEveryMount(GameTestHelper helper){
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 2, 2);
        BlockPos pos = helper.absolutePos(local);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        for(Direction facing : Direction.values()){
            for(Direction horizontal : Direction.Plane.HORIZONTAL){
                for(DiagnosticTabletView view : DiagnosticTabletView.values()){
                    var state = CTBlocks.DIAGNOSTIC_TABLET.get().defaultBlockState()
                            .setValue(CTDirectionalBlock.FACING, facing).setValue(DiagnosticTabletBlock.VIEW, view)
                            .setValue(DiagnosticTabletBlock.HORIZONTAL_FACING, horizontal);
                    helper.setBlock(local, state);
                    var tablet = (DiagnosticTabletBlockEntity)helper.getBlockEntity(local);
                    var screen = DiagnosticTabletSurface.screen(state);
                    Vec3 point = screen.topLeft().add(screen.topRight().subtract(screen.topLeft()).scale(0.25D))
                            .add(screen.bottomLeft().subtract(screen.topLeft()).scale(0.75D)).add(Vec3.atLowerCornerOf(pos));
                    Vec3 eye = point.add(screen.normal().scale(2)).add(screen.topRight().subtract(screen.topLeft()).scale(0.5D));
                    player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
                    var hit = state.getShape(level, pos).clip(eye, point.add(point.subtract(eye).scale(0.5D)), pos);
                    helper.assertTrue(hit != null, "Model selection shape missed the screen ray: " + facing + "/" + view);
                    var projected = DiagnosticTabletSurface.projectScreen(tablet, hit, player);
                    helper.assertTrue(projected != null && Math.abs(projected.x() - 0.25D) < 1.0E-5D
                            && Math.abs(projected.y() - 0.75D) < 1.0E-5D, "Screen pointer drifted: " + facing + "/" + view
                            + ", projected=" + projected + ", eye=" + player.getEyePosition() + ", expected=" + eye
                            + ", hit=" + hit.getLocation() + ", screen=" + screen);
                    Vec3 backEye = point.subtract(screen.normal().scale(2));
                    player.setPos(backEye.x, backEye.y - player.getEyeHeight(), backEye.z);
                    helper.assertTrue(DiagnosticTabletSurface.projectScreen(tablet,
                            new BlockHitResult(point, facing.getOpposite(), pos, false), player) == null, "Back of tablet accepted a screen click");
                }
            }
        }
        helper.succeed();
    }

    // Floor and ceiling placement must face the player and keep raised screens upright
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void floorAndCeilingPlacementFaceThePlayer(GameTestHelper helper){
        var block = CTBlocks.DIAGNOSTIC_TABLET.get();
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos local = new BlockPos(2, 2, 2);
        BlockPos pos = helper.absolutePos(local);
        ItemStack stack = new ItemStack(block.asItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        for(Direction mount : new Direction[]{Direction.UP, Direction.DOWN}){
            BlockPos support = pos.relative(mount.getOpposite());
            helper.setBlock(local.relative(mount.getOpposite()), Blocks.STONE);
            for(Direction looking : Direction.Plane.HORIZONTAL){
                helper.setBlock(local, Blocks.AIR);
                player.setYRot(looking.toYRot());
                var hit = new BlockHitResult(Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(mount.getNormal()).scale(0.5D)),
                        mount, support, false);
                var ctx = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
                var state = block.getStateForPlacement(ctx);
                Direction front = looking.getOpposite();
                helper.assertTrue(ctx.getClickedPos().equals(pos), "Placement fixture targeted the wrong position");
                helper.assertTrue(state.getValue(CTDirectionalBlock.FACING) == mount
                        && state.getValue(DiagnosticTabletBlock.HORIZONTAL_FACING) == front, "Tablet did not face its placer");
                for(DiagnosticTabletView view : DiagnosticTabletView.values()){
                    var screen = DiagnosticTabletSurface.screen(state.setValue(DiagnosticTabletBlock.VIEW, view));
                    if(view == DiagnosticTabletView.FLAT) continue;
                    helper.assertTrue(screen.topLeft().y > screen.bottomLeft().y, "Raised screen is upside down: " + mount + "/" + front + "/" + view);
                    helper.assertTrue(screen.normal().dot(Vec3.atLowerCornerOf(front.getNormal())) > 0.0D,
                            "Raised model faces away from its placer: " + mount + "/" + front + "/" + view);
                }
            }
        }
        helper.succeed();
    }

    // Structure transforms must retain the selected view and transform its viewing direction
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void structureTransformsAndLegacyStatesKeepValidDirections(GameTestHelper helper){
        var block = CTBlocks.DIAGNOSTIC_TABLET.get();
        for(Direction mount : new Direction[]{Direction.UP, Direction.DOWN}){
            for(Direction front : Direction.Plane.HORIZONTAL){
                var state = block.defaultBlockState().setValue(CTDirectionalBlock.FACING, mount)
                        .setValue(DiagnosticTabletBlock.HORIZONTAL_FACING, front)
                        .setValue(DiagnosticTabletBlock.VIEW, DiagnosticTabletView.ANGLED_45);
                var rotated = state.rotate(Rotation.CLOCKWISE_90);
                helper.assertTrue(rotated.getValue(DiagnosticTabletBlock.HORIZONTAL_FACING) == front.getClockWise()
                        && rotated.getValue(CTDirectionalBlock.FACING) == mount
                        && rotated.getValue(DiagnosticTabletBlock.VIEW) == DiagnosticTabletView.ANGLED_45, "Rotation lost tablet orientation");
                helper.assertTrue(state.mirror(Mirror.LEFT_RIGHT).getValue(DiagnosticTabletBlock.HORIZONTAL_FACING)
                        == (front.getAxis() == Direction.Axis.Z ? front.getOpposite() : front), "North/south mirror lost tablet orientation");
                helper.assertTrue(state.mirror(Mirror.FRONT_BACK).getValue(DiagnosticTabletBlock.HORIZONTAL_FACING)
                        == (front.getAxis() == Direction.Axis.X ? front.getOpposite() : front), "East/west mirror lost tablet orientation");
                var legacy = NbtUtils.writeBlockState(state);
                legacy.getCompound("Properties").remove("horizontal_facing");
                var loaded = NbtUtils.readBlockState(helper.getLevel().holderLookup(Registries.BLOCK), legacy);
                helper.assertTrue(loaded.getValue(DiagnosticTabletBlock.HORIZONTAL_FACING) == Direction.NORTH
                        && loaded.getValue(CTDirectionalBlock.FACING) == mount
                        && loaded.getValue(DiagnosticTabletBlock.VIEW) == DiagnosticTabletView.ANGLED_45, "Legacy tablet state did not load safely");
            }
        }
        helper.succeed();
    }
}
