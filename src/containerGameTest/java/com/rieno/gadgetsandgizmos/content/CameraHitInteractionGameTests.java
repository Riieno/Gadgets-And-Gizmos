package com.rieno.gadgetsandgizmos.content;

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.view.ViewBlockInteractions;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewRig;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class CameraHitInteractionGameTests{
    private CameraHitInteractionGameTests(){}

    private static CameraBlockEntity camera(GameTestHelper ctx, BlockPos pos){
        ctx.getLevel().setBlock(pos, CTBlocks.CAMERA.get().defaultBlockState().setValue(CameraBlock.FACING, Direction.UP), 3);
        var camera = (CameraBlockEntity)ctx.getLevel().getBlockEntity(pos);
        camera.configure(16, 0, new ViewRaycast.Filter(Set.of(), false), ViewRig.Mode.MANUAL, ViewRig.Orientation.LOCAL, 0, -90);
        return camera;
    }

    @GameTest(template = "empty_container", timeoutTicks = 80)
    public static void steeringWheelRimUsesItsNativeInteractionShape(GameTestHelper ctx){
        var level = ctx.getLevel();
        var pos = ctx.absolutePos(new BlockPos(3, 72, 3));
        var target = pos.north(4);
        level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        var state = dev.simulated_team.simulated.index.SimBlocks.STEERING_WHEEL.getDefaultState()
                .setValue(dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock.FACING, Direction.SOUTH);
        level.setBlock(target, state, 3);
        var shape = (state.getValue(dev.simulated_team.simulated.content.blocks.steering_wheel.SteeringWheelBlock.ON_FLOOR)
                ? dev.simulated_team.simulated.index.SimBlockShapes.STEERING_WHEEL_FLOOR
                : dev.simulated_team.simulated.index.SimBlockShapes.STEERING_WHEEL_CEILING).get(Direction.SOUTH);
        var point = Vec3.atLowerCornerOf(target).add(shape.bounds().getCenter()).add(0.28, 0, 0);
        var camera = camera(ctx, pos);
        var aim = com.rieno.gadgetsandgizmos.lib.view.ViewTargetFraming.aim(pos.getCenter(), point, camera.mountOrientation(), ViewRig.Orientation.LOCAL);
        camera.configure(16, 0, new ViewRaycast.Filter(Set.of(), false), ViewRig.Mode.MANUAL, ViewRig.Orientation.LOCAL, aim.pan(), aim.tilt());
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[CameraWheel]"));
        player.setPos(pos.east(40).getCenter());
        ViewBlockInteractions.interact(player, ViewReference.of(camera));
        var cursor = com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.cursor(player);
        ctx.assertTrue(cursor != null && cursor.target().reference().blockPos().equals(target), "Wheel rim did not establish native interaction context");
        ctx.assertTrue(dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction.inInteractionRange(player, target.getCenter(), 1), "Native wheel handler rejected camera reach");
        level.removeBlock(pos, false); level.removeBlock(target, false); level.removeBlock(target.below(), false);
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 80)
    public static void cameraReachesTheActualPublishedHologramWidget(GameTestHelper ctx) throws Exception{
        var level = ctx.getLevel();
        var displayPos = ctx.absolutePos(new BlockPos(4, 64, 4));
        var controllerPos = displayPos.east(4);
        level.setBlock(displayPos, CTBlocks.ACC_DISPLAY_PANEL.get().defaultBlockState().setValue(AccDisplayBlock.FACING, Direction.SOUTH), 3);
        level.setBlock(controllerPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var display = (AccDisplayBlockEntity)level.getBlockEntity(displayPos);
        var controller = (AdvancedContraptionControllerBlockEntity)level.getBlockEntity(controllerPos);
        var data = com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory.createDefaultData("acc_hologram_widget", null);
        data.put("TargetData", new com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode(
                "acc_display::world::" + displayPos.asLong(), com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind.DISPLAY,
                "world", "createthrusters:acc_display_panel", "Projector", null, displayPos).toTag());
        var button = new net.minecraft.nbt.CompoundTag();
        button.putString("Type", "button"); button.putString("InteractionId", "remote_button");
        button.putInt("W", 320); button.putInt("H", 180); button.putBoolean("Visible", true);
        var elements = new net.minecraft.nbt.ListTag(); elements.add(button); data.put("WidgetElements", elements);
        var node = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument.Node("hologram", "acc_hologram_widget", "", 0, 0, data);
        var graph = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument(); graph.nodes().add(node);
        com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractions.synchronize(node, graph.edges());
        var active = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("activeGraph"); active.setAccessible(true); active.set(controller, graph);
        var runtimeField = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime"); runtimeField.setAccessible(true);
        var runtime = (com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime)runtimeField.get(controller);
        runtime.compile(graph); runtime.tick(graph, false);
        var publish = AdvancedContraptionControllerBlockEntity.class.getDeclaredMethod("publishAccDisplayUpdates"); publish.setAccessible(true); publish.invoke(controller);
        var state = display.getBlockState();
        var right = Vec3.atLowerCornerOf(display.screenRight().getNormal());
        var down = new Vec3(0, -1, 0);
        double ppb = AccDisplayBlockEntity.PIXELS_PER_BLOCK;
        double plane = AccDisplayWidgetGeometry.renderPlane(AccDisplayBlock.DisplayType.PANEL, state.getValue(AccDisplayBlock.Z_ALIGNMENT));
        var layout = AccDisplayWidgetGeometry.layout(display, node, display.surfacePixelWidth(), display.surfacePixelHeight());
        var point = displayPos.getCenter().add(0, 0, -plane)
                .subtract(right.scale(display.surfacePixelWidth() / (2 * ppb)))
                .subtract(down.scale(display.surfacePixelHeight() / (2 * ppb)))
                .add(right.scale((layout.x() + layout.width() / 2) / ppb))
                .add(down.scale((layout.y() + layout.height() / 2) / ppb))
                .add(right.cross(down).scale(layout.z() / ppb));
        var cameraPos = BlockPos.containing(point.add(0, 0, 4));
        var camera = camera(ctx, cameraPos);
        var aim = com.rieno.gadgetsandgizmos.lib.view.ViewTargetFraming.aim(cameraPos.getCenter(), point, camera.mountOrientation(), ViewRig.Orientation.LOCAL);
        camera.configure(16, 0, new ViewRaycast.Filter(Set.of(), false), ViewRig.Mode.MANUAL, ViewRig.Orientation.LOCAL, aim.pan(), aim.tilt());
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[CameraHologram]"));
        player.setPos(cameraPos.east(40).getCenter());
        ctx.runAfterDelay(2, () -> {
            ctx.assertTrue(ViewBlockInteractions.interact(player, ViewReference.of(camera)), "Published hologram was not camera-interactive");
            var cursor = com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.cursor(player);
            ctx.assertTrue(cursor != null && !cursor.target().useBlock() && cursor.target().reference().blockPos().equals(displayPos), "Camera selected the backing panel instead of its hologram");
            ctx.assertTrue(display.interactHologram(player, "hologram", "remote_button", false), "Published hologram rejected the authenticated lens eye and player");
            ctx.assertTrue(!display.interactHologram(player, "hologram", "another_button", false), "Hologram accepted another control identity");
            level.removeBlock(cameraPos, false); level.removeBlock(displayPos, false); level.removeBlock(controllerPos, false);
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 80)
    public static void nativeThrottleInputRetainsOnlyTheAuthorizedCameraTarget(GameTestHelper ctx){
        var level = ctx.getLevel();
        var pos = ctx.absolutePos(new BlockPos(3, 48, 3));
        var camera = camera(ctx, pos);
        var target = pos.north(4);
        level.getChunk(target);
        level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(target, dev.simulated_team.simulated.index.SimBlocks.THROTTLE_LEVER.getDefaultState()
                .setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR), 3);
        var point = Vec3.atLowerCornerOf(target).add(level.getBlockState(target).getShape(level, target).bounds().getCenter());
        var aim = com.rieno.gadgetsandgizmos.lib.view.ViewTargetFraming.aim(camera.viewPose(0).position(), point,
                camera.mountOrientation(), ViewRig.Orientation.LOCAL);
        camera.configure(16, 0, new ViewRaycast.Filter(Set.of(), false), ViewRig.Mode.MANUAL, ViewRig.Orientation.LOCAL, aim.pan(), aim.tilt());
        var profile = new GameProfile(UUID.randomUUID(), "[CameraThrottle]");
        var player = FakePlayerFactory.get(level, profile);
        player.setPos(pos.offset(40, 0, 0).getCenter());
        ctx.assertTrue(!dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction.inInteractionRange(player, target.getCenter(), 1), "Fixture was within body reach");
        boolean used = ViewBlockInteractions.interact(player, ViewReference.of(camera));
        var cursor = com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.cursor(player);
        ctx.assertTrue(cursor != null && cursor.target().reference().blockPos().equals(target), "Throttle hit did not establish native interaction context: used=" + used + ", ray=" + camera.castViewRay());
        ctx.assertTrue(dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction.inInteractionRange(player, target.getCenter(), 1), "Native throttle packet still used physical body reach");
        ctx.assertTrue(!dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction.inInteractionRange(player, target.east(1).getCenter(), 1), "Camera reach leaked to an unrelated block");
        var packetContext = new foundry.veil.api.network.handler.ServerPacketContext(){
            @Override public net.minecraft.server.level.ServerPlayer player(){ return player; }
            @Override public net.minecraft.network.protocol.Packet<?> createPacket(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload){ return null; }
            @Override public void sendPacket(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener){}
            @Override public void disconnect(net.minecraft.network.chat.Component reason){}
        };
        new dev.simulated_team.simulated.network.packets.ThrottleLeverSignalPacket(target, 11).handle(packetContext);
        var lever = (dev.simulated_team.simulated.content.blocks.throttle_lever.ThrottleLeverBlockEntity)level.getBlockEntity(target);
        ctx.assertTrue(lever.getState() == 11, "Native throttle payload did not update the camera target");
        level.removeBlock(pos, false);
        ctx.assertTrue(!com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.canUse(player, target), "Removed source retained drag access");
        level.removeBlock(target, false);
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 80)
    public static void projectedTargetsRespectObstructionAndNeverUseTheirBackingBlock(GameTestHelper ctx){
        var level = ctx.getLevel();
        var pos = ctx.absolutePos(new BlockPos(3, 56, 3));
        var camera = camera(ctx, pos);
        var target = pos.north(5);
        level.getChunk(target);
        level.setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
        var entity = level.getBlockEntity(target);
        com.rieno.gadgetsandgizmos.lib.view.ViewInteractionTargets.register(entity, (be, pose, reach) -> {
            var point = pose.position().add(pose.forward().scale(3));
            return new com.rieno.gadgetsandgizmos.lib.view.ViewInteractionTargets.Target(ViewReference.of(be), point, Direction.SOUTH, 3, false);
        });
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[CameraSurface]"));
        player.setPos(pos.offset(40, 0, 0).getCenter());
        ctx.assertTrue(ViewBlockInteractions.interact(player, ViewReference.of(camera)), "A projected surface without a block collider could not be used");
        ctx.assertTrue(player.containerMenu == player.inventoryMenu, "A projected control opened its backing block");
        var cursor = com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.cursor(player);
        ctx.assertTrue(cursor != null && !cursor.target().useBlock() && cursor.target().reference().blockPos().equals(target), "Projected target identity was lost");
        var wall = BlockPos.containing(camera.viewPose(0).position().add(camera.viewPose(0).forward().scale(2)));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        var pick = com.rieno.gadgetsandgizmos.lib.view.ViewInteractionContext.pick(player);
        ctx.assertTrue(pick != null && pick.useBlock() && pick.reference().blockPos().equals(wall), "A projected control was picked through a wall");
        level.removeBlock(pos, false); level.removeBlock(target, false); level.removeBlock(wall, false);
        ctx.succeed();
    }

    @GameTest(template = "empty_container", timeoutTicks = 60)
    public static void cameraUsesTheActualPlayerFaceAndHonorsCancellation(GameTestHelper ctx){
        var level = ctx.getLevel();
        var pos = ctx.absolutePos(new BlockPos(3, 24, 3));
        var camera = camera(ctx, pos);
        var target = BlockPos.containing(camera.viewPose(0).position().add(camera.viewPose(0).forward().scale(4)));
        level.getChunk(target);
        level.setBlock(target.north(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(target, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.SOUTH), 3);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "[CameraUse]"));
        player.setPos(pos.offset(30, 0, 0).getCenter());
        var origin = player.position();
        var observed = new AtomicReference<PlayerInteractEvent.RightClickBlock>();
        Consumer<PlayerInteractEvent.RightClickBlock> listener = evt -> {
            if(evt.getEntity() == player && evt.getPos().equals(target)) observed.set(evt);
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try{
            var ray = camera.castViewRay();
            ctx.assertTrue("block".equals(ray.kind()) && ray.localBlockPos().equals(target), "Lever ray did not reach its target: " + ray);
            ctx.assertTrue(level.getWorldBorder().isWithinBounds(target), "Lever fixture is outside the world border");
            ctx.assertTrue(level.mayInteract(player, target), "Fixture player may not use the lever position");
            ctx.assertTrue(com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy.canAccessLocal(player, level, null, target), "Fixture target is protected");
            boolean used = ViewBlockInteractions.interact(player, ViewReference.of(camera));
            ctx.assertTrue(used, "Camera did not use the lever; event=" + observed.get() + ", ray=" + ray);
            ctx.assertTrue(level.getBlockState(target).getValue(LeverBlock.POWERED), "Lever did not turn on");
            ctx.assertTrue(observed.get() != null && observed.get().getFace() == Direction.SOUTH,
                    "Right-click event lost its player or hit face");
            ctx.assertTrue(player.position().equals(origin), "Remote interaction moved the player's body");
            ctx.assertTrue(!ViewBlockInteractions.interact(player, ViewReference.of(camera)), "Repeated use was not rate limited");
        }finally{ NeoForge.EVENT_BUS.unregister(listener); }
        Consumer<PlayerInteractEvent.RightClickBlock> cancel = evt -> {
            if(evt.getEntity() == player && evt.getPos().equals(target)){
                evt.setCancellationResult(InteractionResult.FAIL); evt.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(cancel);
        ctx.runAfterDelay(2, () -> {
            try{
                ctx.assertTrue(!ViewBlockInteractions.interact(player, ViewReference.of(camera)), "A canceled use succeeded");
                ctx.assertTrue(level.getBlockState(target).getValue(LeverBlock.POWERED), "Canceled use changed the lever");
                ctx.succeed();
            }finally{
                NeoForge.EVENT_BUS.unregister(cancel);
                level.removeBlock(pos, false); level.removeBlock(target, false); level.removeBlock(target.north(), false);
            }
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 80)
    public static void remoteChestStaysUsableAndLosesAccessWhenItsCameraIsRemoved(GameTestHelper ctx){
        var level = ctx.getLevel();
        var pos = ctx.absolutePos(new BlockPos(3, 32, 3));
        var camera = camera(ctx, pos);
        var target = BlockPos.containing(camera.viewPose(0).position().add(camera.viewPose(0).forward().scale(4)));
        level.getChunk(target);
        level.setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
        var profile = new GameProfile(UUID.randomUUID(), "[CameraMenu]");
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = FakePlayerFactory.get(level, profile).connection;
        player.setPos(pos.offset(30, 0, 0).getCenter());
        try{
            ctx.assertTrue(!player.canInteractWithBlock(target, 4), "Fixture player was within ordinary reach");
            ctx.assertTrue(ViewBlockInteractions.interact(player, ViewReference.of(camera)), "Remote chest refused to open");
            var menu = player.containerMenu;
            ctx.assertTrue(menu != player.inventoryMenu && menu.stillValid(player), "Remote menu immediately became invalid");
            ctx.assertTrue(!ViewBlockInteractions.menuReach(player, target.offset(10, 0, 0)), "Menu access extended to another block");
            level.removeBlock(pos, false);
            ctx.assertTrue(!menu.stillValid(player), "Removed camera retained remote menu access");
            ctx.succeed();
        }finally{
            player.closeContainer();
            level.removeBlock(pos, false); level.removeBlock(target, false);
        }
    }
}
