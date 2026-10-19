package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class SmartGearboxServoGameTests{
    private SmartGearboxServoGameTests(){}

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo", timeoutTicks = 100)
    public static void changingOneFaceLeavesOtherSourcesConnectedAndPreservesSavedSourceSlots(GameTestHelper ctx){
        var gearbox = setup(ctx);
        for(Direction face : Direction.Plane.HORIZONTAL) gearbox.setFaceMaxAngle(face, 720);
        target(gearbox, 720);
        ctx.runAtTickTime(8, () -> {
            for(Direction face : Direction.Plane.HORIZONTAL){
                var consumer = (com.simibubi.create.content.kinetics.base.KineticBlockEntity)
                        ctx.getLevel().getBlockEntity(gearbox.getBlockPos().relative(face));
                var saved = consumer.saveWithFullMetadata(ctx.getLevel().registryAccess());
                var reloaded = (com.simibubi.create.content.kinetics.base.KineticBlockEntity)
                        consumer.getType().create(consumer.getBlockPos(), consumer.getBlockState());
                ctx.assertTrue(reloaded != null, "Could not recreate the kinetic consumer");
                reloaded.setLevel(ctx.getLevel());
                reloaded.loadWithComponents(saved, ctx.getLevel().registryAccess());
                var source = gearbox.ct$getVirtualKinetics().stream()
                        .filter(virtual -> com.rieno.gadgetsandgizmos.lib.virtualkinetics.VirtualKineticSourceLink
                                .isSourcedBy(reloaded, virtual)).findFirst();
                ctx.assertTrue(source.isPresent(), "Reload lost the virtual source slot for " + face
                        + " source=" + reloaded.source + " saved=" + saved);
            }
            gearbox.writeGraphData("north_angle", AdvancedGraphDocument.Value.number(0));
        });
        ctx.runAtTickTime(25, () -> {
            for(Direction face : Direction.Plane.HORIZONTAL){
                var consumer = (com.simibubi.create.content.kinetics.base.KineticBlockEntity)
                        ctx.getLevel().getBlockEntity(gearbox.getBlockPos().relative(face));
                ctx.assertTrue(face == Direction.NORTH ? consumer.getSpeed() == 0 : consumer.getSpeed() > 0,
                        "Changing one source changed the driven RPM on " + face);
            }
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo", timeoutTicks = 280)
    public static void multipleTurnMovesRetainTheirKineticConnectionsAndStopAtTheirTargets(GameTestHelper ctx){
        var gearbox = setup(ctx);
        for(Direction face : Direction.Plane.HORIZONTAL) gearbox.setFaceMaxAngle(face, 720);
        target(gearbox, 720);
        for(int tick = 5; tick < 70; tick += 5) ctx.runAtTickTime(tick, () -> checkPower(ctx, gearbox, 1));
        ctx.runAtTickTime(85, () -> {
            for(Direction face : Direction.Plane.HORIZONTAL)
                ctx.assertTrue(gearbox.getVisualShaftSpeed(face) == 0, "Completed face kept generating RPM");
            target(gearbox, -720);
        });
        for(int tick = 95; tick < 230; tick += 5) ctx.runAtTickTime(tick, () -> checkPower(ctx, gearbox, -1));
        ctx.runAtTickTime(250, () -> {
            for(Direction face : Direction.Plane.HORIZONTAL){
                ctx.assertTrue(gearbox.getVisualShaftSpeed(face) == 0, "Reverse face kept generating RPM after arrival");
                var consumer = (com.simibubi.create.content.kinetics.base.KineticBlockEntity)
                        ctx.getLevel().getBlockEntity(gearbox.getBlockPos().relative(face));
                ctx.assertTrue(consumer.getSpeed() == 0, "Stopped servo retained a driven kinetic network");
            }
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo", timeoutTicks = 100)
    public static void slowNegativeAngleUpdatesStillPropagateRpmAndStress(GameTestHelper ctx){
        var gearbox = setup(ctx);
        for(int tick = 1; tick <= 12; tick++){
            double angle = -tick * 0.3D;
            ctx.runAtTickTime(tick, () -> target(gearbox, angle));
        }
        ctx.runAtTickTime(8, () -> {
            checkPower(ctx, gearbox, -1);
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo", timeoutTicks = 100)
    public static void craftsAndAdditionsAlternatorsAcceptReverseServoRpm(GameTestHelper ctx){
        if(!net.neoforged.fml.ModList.get().isLoaded("createaddition")){
            ctx.succeed();
            return;
        }
        var gearbox = setup(ctx);
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse("createaddition:alternator"));
        for(Direction face : Direction.Plane.HORIZONTAL){
            ctx.getLevel().setBlock(gearbox.getBlockPos().relative(face), block.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, face.getOpposite()), 3);
        }
        target(gearbox, -90);
        ctx.runAtTickTime(5, () -> {
            for(Direction face : Direction.Plane.HORIZONTAL){
                var alternator = (com.simibubi.create.content.kinetics.base.KineticBlockEntity)
                        ctx.getLevel().getBlockEntity(gearbox.getBlockPos().relative(face));
                ctx.assertTrue(alternator.getSpeed() < 0, "Crafts & Additions lost reverse servo RPM on " + face);
                ctx.assertTrue(alternator.getOrCreateNetwork().calculateCapacity() > 0, "Crafts & Additions lost stress capacity");
                try{
                    var storage = (net.neoforged.neoforge.energy.IEnergyStorage)alternator.getClass()
                            .getMethod("getEnergyStorage", Direction.class).invoke(alternator, Direction.UP);
                    ctx.assertTrue(storage.getEnergyStored() > 0, "Crafts & Additions did not generate from reverse RPM");
                }catch(ReflectiveOperationException ex){ throw new IllegalStateException(ex); }
            }
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo", timeoutTicks = 100)
    public static void setDataAngleLimitsOverrideDefaultAndSurviveSaving(GameTestHelper ctx){
        var gearbox = setup(ctx);
        ctx.assertTrue(gearbox.getFaceMaxAngle(Direction.NORTH) == 90, "Default range changed");
        gearbox.writeGraphData("north_max_angle", AdvancedGraphDocument.Value.number(360));
        gearbox.writeGraphData("east_max_angle", AdvancedGraphDocument.Value.number(30));
        target(gearbox, 720);
        ctx.runAtTickTime(3, () -> {
            ctx.assertTrue(gearbox.getFaceMaxAngle(Direction.NORTH) == 360, "Set Data face override was capped by the global default");
            ctx.assertTrue(gearbox.getFaceAngle(Direction.NORTH) == 360, "Target did not use its explicit limit");
            ctx.assertTrue(gearbox.getFaceAngle(Direction.EAST) == 30, "A different face lost its own limit");
            ctx.assertTrue(gearbox.getFaceAngle(Direction.SOUTH) == 90, "Unconfigured face lost the default limit");
            var saved = gearbox.saveWithoutMetadata(ctx.getLevel().registryAccess());
            gearbox.loadWithComponents(saved, ctx.getLevel().registryAccess());
            ctx.assertTrue(gearbox.getFaceMaxAngle(Direction.NORTH) == 360, "Face override did not survive NBT reload");
            gearbox.clearFaceMaxAngle(Direction.NORTH);
            ctx.assertTrue(gearbox.getFaceMaxAngle(Direction.NORTH) == 90, "Clearing the override did not restore the default");
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo", timeoutTicks = 100)
    public static void repeatedSetDataWritesKeepReverseRpm(GameTestHelper ctx){
        var gearbox = setup(ctx);
        gearbox.setOperationMode("servo");
        for(int tick = 1; tick <= 6; tick++) ctx.runAtTickTime(tick, () -> target(gearbox, 90));
        for(int tick = 7; tick <= 15; tick++) ctx.runAtTickTime(tick, () -> target(gearbox, -90));
        ctx.runAtTickTime(12, () -> {
            checkPower(ctx, gearbox, -1);
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo_ship", timeoutTicks = 100)
    public static void reverseMotionPowersAlternatorsOnASubLevel(GameTestHelper ctx){
        onSubLevel(ctx, -90);
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo_ship", timeoutTicks = 100)
    public static void forwardMotionPowersAlternatorsOnASubLevel(GameTestHelper ctx){
        onSubLevel(ctx, 90);
    }

    private static void onSubLevel(GameTestHelper ctx, double angle){
        var original = setup(ctx);
        java.util.List<BlockPos> blocks = new java.util.ArrayList<>();
        blocks.add(original.getBlockPos());
        for(Direction face : Direction.Plane.HORIZONTAL) blocks.add(original.getBlockPos().relative(face));
        var body = com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi.assemble(
                ctx.getLevel(), original.getBlockPos(), blocks);
        ctx.assertTrue(body != null, "Gearbox fixture failed to assemble");
        var lease = com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency.lease("gearbox_servo_test/" + body.getUniqueId());
        lease.retain(body);
        var gearbox = com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector.getBlockEntities(body).stream()
                .filter(BiDirectionalGearboxBlockEntity.class::isInstance).map(BiDirectionalGearboxBlockEntity.class::cast)
                .findFirst().orElseThrow();
        target(gearbox, angle);
        ctx.runAtTickTime(5, () -> {
            try{
                checkPower(ctx, gearbox, angle < 0 ? -1 : 1);
            }finally{
                lease.close();
                com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi.remove(ctx.getLevel(), java.util.List.of(body));
            }
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo", timeoutTicks = 100)
    public static void reverseMotionPowersAllFourAlternators(GameTestHelper ctx){
        var gearbox = setup(ctx);
        target(gearbox, -90);
        ctx.runAtTickTime(4, () -> {
            checkPower(ctx, gearbox, -1);
            ctx.succeed();
        });
    }

    @GameTest(template = "empty_container", batch = "smart_gearbox_servo", timeoutTicks = 100)
    public static void changingDirectionKeepsRpmAndStress(GameTestHelper ctx){
        var gearbox = setup(ctx);
        target(gearbox, 90);
        ctx.runAtTickTime(4, () -> checkPower(ctx, gearbox, 1));
        ctx.runAtTickTime(15, () -> target(gearbox, -90));
        ctx.runAtTickTime(19, () -> checkPower(ctx, gearbox, -1));
        ctx.runAtTickTime(40, () -> target(gearbox, 90));
        ctx.runAtTickTime(44, () -> {
            checkPower(ctx, gearbox, 1);
            ctx.succeed();
        });
    }

    private static BiDirectionalGearboxBlockEntity setup(GameTestHelper ctx){
        BlockPos pos = new BlockPos(4, 3, 4);
        ctx.setBlock(pos, CTBlocks.BIDIRECTIONAL_GEARBOX.get().defaultBlockState());
        var gearbox = (BiDirectionalGearboxBlockEntity)ctx.getBlockEntity(pos);
        for(Direction face : Direction.Plane.HORIZONTAL){
            ctx.setBlock(pos.relative(face), CTBlocks.ALTERNATOR.get().defaultBlockState()
                    .setValue(AlternatorBlock.FACING, face.getOpposite()));
        }
        gearbox.setOperationMode("servo_locked");
        return gearbox;
    }

    private static void target(BiDirectionalGearboxBlockEntity gearbox, double angle){
        for(Direction face : Direction.Plane.HORIZONTAL){
            gearbox.writeGraphData(face.getName() + "_angle", AdvancedGraphDocument.Value.number(angle));
        }
    }

    private static void checkPower(GameTestHelper ctx, BiDirectionalGearboxBlockEntity gearbox, int direction){
        for(Direction face : Direction.Plane.HORIZONTAL){
            var alternator = (AlternatorBlockEntity)ctx.getLevel().getBlockEntity(gearbox.getBlockPos().relative(face));
            String state = face + " shaft=" + gearbox.getVisualShaftSpeed(face)
                    + " alternator=" + alternator.getSpeed() + " theoretical=" + alternator.getTheoreticalSpeed()
                    + " network=" + alternator.network + " source=" + alternator.source;
            ctx.assertTrue(gearbox.getVisualShaftSpeed(face) * direction > 0, "Wrong servo motion: " + state);
            ctx.assertTrue(alternator.getSpeed() * direction > 0, "Servo lost driven RPM: " + state);
            ctx.assertTrue(alternator.hasNetwork() && alternator.getOrCreateNetwork().calculateCapacity() > 0,
                    "Servo lost stress capacity: " + state);
            ctx.assertTrue(alternator.getCurrentFePerTick() > 0, "Alternator did not generate: " + state);
        }
    }
}
