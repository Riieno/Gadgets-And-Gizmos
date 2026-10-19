package com.rieno.gadgetsandgizmos.content;

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.physics.EntityMotionTelemetry;
import com.rieno.gadgetsandgizmos.lib.physics.EntityTelemetryApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class NodeVelocityGameTests{
    private NodeVelocityGameTests(){}

    @GameTest(template = "empty_container", timeoutTicks = 40)
    public static void gogglesTrackerReportsZeroWhileGravityPushesIntoTheGround(GameTestHelper ctx) throws Exception{
        ctx.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        Vec3 feet = ctx.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter();
        var wearer = new ArmorStand(ctx.getLevel(), feet.x, feet.y, feet.z);
        UUID pair = UUID.randomUUID();ItemStack goggles = new ItemStack(CTItems.PHYSICS_GOGGLES.get());
        CompoundTag data = new CompoundTag();CompoundTag binding = new CompoundTag();
        binding.putUUID(AnalogueContraptionControllerMenu.GOGGLES_BIND_PAIR_ID, pair);
        data.put(AnalogueContraptionControllerMenu.GOGGLES_BIND_ROOT, binding);
        goggles.set(DataComponents.CUSTOM_DATA, CustomData.of(data));wearer.setItemSlot(EquipmentSlot.HEAD, goggles);
        ctx.getLevel().addFreshEntity(wearer);EntityMotionTelemetry.velocity(wearer);
        BlockPos controllerPos = new BlockPos(1, 1, 1);ctx.setBlock(controllerPos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get());
        var controller = (AdvancedContraptionControllerBlockEntity)ctx.getBlockEntity(controllerPos);
        controller.registerGogglesTrackerPair(pair, "Velocity test");
        var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("graphRuntime");field.setAccessible(true);
        var runtime = (GraphRuntime)field.get(controller);
        var graph = new AdvancedGraphDocument();
        var node = new AdvancedGraphDocument.Node("goggles", "portable_tracker", "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData("portable_tracker", null));
        node.data().putString("GogglesPair", pair.toString());graph.nodes().add(node);
        ctx.runAtTickTime(8, () -> {
            ctx.assertTrue(wearer.onGround() && wearer.getDeltaMovement().y < -0.07,
                    "The vanilla grounded gravity bias was not reproduced");
            runtime.beginPreviewSample(graph);
            ctx.assertTrue(runtime.previewOutput(graph, node, "available").asBoolean(), "Paired goggles wearer was not found");
            Vec3 velocity = vector(GraphRuntime.toLibraryValue(runtime.previewOutput(graph, node, "velocity")));
            assertVector(ctx, Vec3.ZERO, velocity, "Grounded goggles velocity");
            ctx.assertTrue(runtime.previewOutput(graph, node, "speed").asNumber() == 0,
                    "Stationary goggles speed retained the gravity magnitude");
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 40)
    public static void playerVelocityUsesActualPositionsOnEveryAxisAndClearsAfterStopping(GameTestHelper ctx){
        ServerPlayer player = player(ctx);Vec3 start = player.position();
        EntityMotionTelemetry.velocity(player);
        ctx.runAtTickTime(2, () -> {
            player.setDeltaMovement(12, -0.0784, 9);player.setKnownMovement(new Vec3(30, 30, 30));
            player.setOnGround(true);player.setPos(start.add(0.25, -0.125, -0.5));
        });
        ctx.runAtTickTime(3, () -> {
            var sample = sample(player);
            assertVector(ctx, new Vec3(5, -2.5, -10), vector(sample.get("velocity")), "Player world velocity");
            ctx.assertTrue(Math.abs(sample.get("speed").asNumber() - new Vec3(5, -2.5, -10).length()) < 1.0E-8,
                    "Player speed and velocity magnitude disagree");
        });
        ctx.runAtTickTime(4, () -> {
            var sample = sample(player);assertVector(ctx, Vec3.ZERO, vector(sample.get("velocity")), "Stopped player velocity");
            ctx.assertTrue(sample.get("speed").asNumber() == 0, "Stopped player retained old movement");ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 40)
    public static void passengersIncludeCarriedMovementWhenTheirOwnMotionIsZero(GameTestHelper ctx){
        Vec3 pos = ctx.absolutePos(new BlockPos(2, 2, 2)).getCenter();
        var vehicle = new Boat(ctx.getLevel(), pos.x, pos.y, pos.z);vehicle.setNoGravity(true);
        var rider = new ArmorStand(ctx.getLevel(), pos.x, pos.y, pos.z);rider.setNoGravity(true);
        ctx.getLevel().addFreshEntity(vehicle);ctx.getLevel().addFreshEntity(rider);
        ctx.assertTrue(rider.startRiding(vehicle, true), "Passenger did not mount the vehicle");
        EntityMotionTelemetry.velocity(rider);vehicle.setDeltaMovement(0.2, 0, 0);
        ctx.runAtTickTime(5, () -> {
            var sample = sample(rider);Vec3 velocity = vector(sample.get("velocity"));
            ctx.assertTrue(rider.getDeltaMovement().lengthSqr() == 0, "Passenger has independent motion");
            ctx.assertTrue(velocity.x > 0 && sample.get("speed").asNumber() > 0, "Carried passenger incorrectly reports zero speed");
            ctx.assertTrue(Math.abs(velocity.length() - sample.get("speed").asNumber()) < 1.0E-8,
                    "Passenger speed and velocity magnitude disagree");ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", timeoutTicks = 40)
    public static void teleportEventsStartANewBaselineInsteadOfReportingASpeedSpike(GameTestHelper ctx){
        ServerPlayer player = player(ctx);Vec3 start = player.position();EntityMotionTelemetry.velocity(player);
        ctx.runAtTickTime(2, () -> player.setPos(start.add(0.5, 0, 0)));
        ctx.runAtTickTime(3, () -> {
            assertVector(ctx, new Vec3(10, 0, 0), EntityMotionTelemetry.velocity(player), "Pre-teleport velocity");
            Vec3 target = start.add(100, 20, 100);
            NeoForge.EVENT_BUS.post(new EntityTeleportEvent.TeleportCommand(player, target.x, target.y, target.z));
            player.setPos(target);assertVector(ctx, Vec3.ZERO, EntityMotionTelemetry.velocity(player), "Teleport velocity");
        });
        ctx.runAtTickTime(4, () -> {
            assertVector(ctx, Vec3.ZERO, EntityMotionTelemetry.velocity(player), "Post-teleport velocity");ctx.succeed();
        });
    }

    private static ServerPlayer player(GameTestHelper ctx){
        ServerPlayer player = FakePlayerFactory.get(ctx.getLevel(), new GameProfile(UUID.randomUUID(), "VelocityTest"));
        player.setPos(ctx.absolutePos(new BlockPos(2, 2, 2)).getCenter());return player;
    }

    private static Map<String, GraphValue> sample(net.minecraft.world.entity.LivingEntity entity){
        return EntityTelemetryApi.sample(entity, Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 1, 0),
                entity.level().dimension().location().toString(), 0);
    }

    private static Vec3 vector(GraphValue value){
        return new Vec3(value.member("x").asNumber(), value.member("y").asNumber(), value.member("z").asNumber());
    }

    private static void assertVector(GameTestHelper ctx, Vec3 expected, Vec3 actual, String label){
        ctx.assertTrue(expected.subtract(actual).length() < 1.0E-8, label + ": expected " + expected + ", got " + actual);
    }
}
