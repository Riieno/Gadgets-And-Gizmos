package com.rieno.gadgetsandgizmos.content;

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.view.RemoteViewSessions;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

// Exercise exclusive remote ownership and explicit sensing against real camera block entities
@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class CameraRemoteControlsGameTests{
    private CameraRemoteControlsGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 40)
    public static void predictedAimKeepsCapturesSeparateAndFlushesWithinTheTickBudget(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(2, 24, 2));
        level.setBlock(pos, CTBlocks.CAMERA.get().defaultBlockState(), 3);
        var camera = (CameraBlockEntity)level.getBlockEntity(pos);
        camera.configure(16, 0, new ViewRaycast.Filter(Set.of(), false), ViewRig.Mode.MANUAL, ViewRig.Orientation.LOCAL, 0, -90);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[PredictionTest]"));
        var ref = ViewReference.of(camera);
        try{
            ctx.assertTrue(RemoteViewSessions.open(player, ref), "Camera refused first-person control");
            var original = camera.viewAim();
            var preview = camera.viewPose(0, new ViewRig.State(20, -80, 60));
            ctx.assertTrue(camera.viewAim().equals(original), "Preview changed the replicated camera aim");
            ctx.assertTrue(preview.forward().distanceTo(camera.viewPose(0).forward()) > .1, "Preview did not apply frame input");
            RemoteViewSessions.input(player, new com.rieno.gadgetsandgizmos.lib.view.RemoteViewInputPayload(1, 10, 5, 1));
            RemoteViewSessions.input(player, new com.rieno.gadgetsandgizmos.lib.view.RemoteViewInputPayload(2, 20, 3, 1));
            ctx.assertTrue(camera.viewAim().equals(new ViewRig.State(30, -82, 60)), "Same-tick action flush discarded camera input");
            RemoteViewSessions.input(player, new com.rieno.gadgetsandgizmos.lib.view.RemoteViewInputPayload(3, 100, 0, 0));
            ctx.assertTrue(camera.viewAim().pan() == 45, "Flushed input exceeded the tick's motion allowance");
            RemoteViewSessions.input(player, new com.rieno.gadgetsandgizmos.lib.view.RemoteViewInputPayload(2, -20, 0, 0));
            ctx.assertTrue(camera.viewAim().pan() == 45, "Duplicate input was applied twice");
        }finally{
            RemoteViewSessions.close(player);
            level.removeBlock(pos, false);
        }
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 80)
    public static void controlledLensTogglesItsLightAndPublishesTheCentreRay(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(2, 24, 2));
        level.setBlock(pos, CTBlocks.CAMERA.get().defaultBlockState().setValue(CameraBlock.FACING, Direction.NORTH), 3);
        var camera = (CameraBlockEntity)level.getBlockEntity(pos);
        camera.configure(16, 0, new ViewRaycast.Filter(Set.of(), false), ViewRig.Mode.MANUAL, ViewRig.Orientation.LOCAL, 0, 0);
        var pose = camera.viewPose(0);
        BlockPos target = BlockPos.containing(pose.position().add(pose.forward().scale(4)));
        level.getChunk(target);
        var previous = level.getBlockState(target);
        level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[CameraTest]"));
        var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[CameraOther]"));
        var ref = ViewReference.of(camera);
        try{
            ctx.assertTrue(RemoteViewSessions.open(player, ref), "Camera refused first-person control");
            ctx.assertTrue(RemoteViewSessions.isLocked(player), "The player body was not locked");
            ctx.assertTrue(!RemoteViewSessions.open(other, ref), "A second player took the occupied camera");
            var before = camera.viewControlState();
            ctx.assertTrue(RemoteViewSessions.toggleFlashlight(player), "Owned camera light did not toggle");
            ctx.assertTrue(camera.viewControlState().flashlight(), "Camera flashlight is off");
            ctx.assertTrue(before.mode() == camera.viewControlState().mode() && before.fov() == camera.viewControlState().fov(),
                    "Flashlight changed the camera mode or zoom");
            ctx.assertTrue(!RemoteViewSessions.toggleFlashlight(other), "Another player toggled the light");
            var ray = RemoteViewSessions.raycast(player);
            ctx.assertTrue(ray != null && ray.hit() && ray.localBlockPos().equals(target), "The centre ray missed its target");
            ctx.assertTrue(camera.graphValue("hit").asBoolean(), "The manual ray was not published to graph outputs");
            ctx.assertTrue(RemoteViewSessions.raycast(player) == null, "Raycast was repeated in the same server tick");
        }finally{
            RemoteViewSessions.close(player);
            RemoteViewSessions.close(other);
            level.removeBlock(pos, false);
            level.setBlock(target, previous, 3);
        }
        ctx.runAfterDelay(3, ctx::succeed);
    }

    @GameTest(template = "empty_container", timeoutTicks = 40)
    public static void displayCannotTakeOverAnUnselectedCamera(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(1, 3, 1));
        BlockPos cameraPos = ctx.absolutePos(new BlockPos(3, 3, 1));
        level.setBlock(pos, CTBlocks.ACC_DISPLAY.get().defaultBlockState(), 3);
        level.setBlock(cameraPos, CTBlocks.CAMERA.get().defaultBlockState(), 3);
        var display = (AccDisplayBlockEntity)level.getBlockEntity(pos);
        var camera = (CameraBlockEntity)level.getBlockEntity(cameraPos);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[DisplayTest]"));
        player.setPos(pos.getCenter());
        ctx.assertTrue(!display.takeCameraControl(player, ViewReference.of(camera)), "Display controlled a camera outside its live feed");
        ctx.assertTrue(!RemoteViewSessions.isLocked(player), "A denied display request locked the player");
        display.displayFrame().putString("State", "external");
        display.displayFrame().put("External", camera.displayFrame());
        try{
            ctx.assertTrue(display.takeCameraControl(player, ViewReference.of(camera)), "The selected accessible feed refused takeover");
            ctx.assertTrue(RemoteViewSessions.isLocked(player), "Display takeover did not lock the player's body");
        }finally{ RemoteViewSessions.close(player); }
        level.removeBlock(pos, false); level.removeBlock(cameraPos, false);
        ctx.runAfterDelay(3, ctx::succeed);
    }
}
