package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ScmYawHoldGameTests{
    private ScmYawHoldGameTests(){}

    @GameTest(template = "empty_container", batch = "scm_yaw_hold", timeoutTicks = 100)
    public static void yawDampingCompletesWithoutInitializingAndManualControlStillWorks(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(pos.below(), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
        level.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var body = SubLevelAssemblyApi.assemble(level, pos.below(), List.of(pos.below(), pos));
        ctx.assertTrue(body != null, "SCM ship did not assemble");
        var lease = SableSubLevelResidency.lease("yaw_hold_test/" + body.getUniqueId());
        lease.retain(body);
        ctx.runAtTickTime(10, () -> {
            try{
                var controller = SubLevelBlockEntityCollector.getBlockEntities(body, AdvancedContraptionControllerBlockEntity.class).getFirst();
                var runtimeField = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
                runtimeField.setAccessible(true);
                var runtime = (ShipControlModuleRuntime)runtimeField.get(controller);
                var map = new ShipControlMap(UUID.randomUUID(), level.dimension().location().toString(),
                        body.getUniqueId(), controller.getBlockPos(), Vec3.ZERO, List.of(), level.getGameTime());
                field("map").set(runtime, map);
                field("rootSubLevel").set(runtime, body);
                var phase = field("phase");
                Object ready = java.util.Arrays.stream(phase.getType().getEnumConstants())
                        .filter(value -> value.toString().equals("READY")).findFirst().orElseThrow();
                phase.set(runtime, ready);
                var stationaryHandle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
                ctx.assertTrue(stationaryHandle != null && stationaryHandle.isValid(), "Ship physics handle is unavailable");
                stationaryHandle.addLinearAndAngularVelocity(new org.joml.Vector3d(),
                        stationaryHandle.getAngularVelocity(new org.joml.Vector3d()).negate());
                ctx.assertTrue(runtime.execute("hold", "ship_hold_yaw", Map.of("strength", 1D)), "Pulse did not activate hold");
                body.logicalPose().orientation().rotateY(0.25D);
                body.updateLastPose();
                Vec3 still = controlTorque(runtime, map);
                ctx.assertTrue(Math.abs(still.y) < 0.01D, "Stationary yaw received heading-restoring torque: " + still);
                var telemetryMethod = ShipControlModuleRuntime.class.getDeclaredMethod("telemetry");
                telemetryMethod.setAccessible(true);
                Object original = telemetryMethod.invoke(runtime);
                Class<?> telemetryType = original.getClass();
                var position = telemetryType.getDeclaredMethod("position");
                position.setAccessible(true);
                Vec3 current = (Vec3)position.invoke(original);
                var ctor = telemetryType.getDeclaredConstructor(boolean.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class);
                ctor.setAccessible(true);
                Vec3 up = worldUp(runtime);
                Vec3 yawSpin = up.scale(2.0D);
                Object moving = ctor.newInstance(true, current, Vec3.ZERO, yawSpin, Vec3.ZERO, Vec3.ZERO);
                Vec3 braking = vector(controlDemand(runtime, map, moving), "controlTorque");
                ctx.assertTrue(braking.y < -0.9D, "Yaw counter torque did not oppose rotation: " + braking);
                var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
                ctx.assertTrue(handle != null && handle.isValid(), "Ship physics handle is unavailable");
                var originalAngular = handle.getAngularVelocity(new org.joml.Vector3d());
                handle.addLinearAndAngularVelocity(new org.joml.Vector3d(), new org.joml.Vector3d(yawSpin.x, yawSpin.y, yawSpin.z).sub(originalAngular));
                runtime.tick();
                ctx.assertTrue(phase.get(runtime) == ready && !runtime.isInitializing(), "Active yaw damping failed during the live physics tick: " + phase.get(runtime));
                handle.addLinearAndAngularVelocity(new org.joml.Vector3d(), new org.joml.Vector3d(originalAngular).sub(handle.getAngularVelocity(new org.joml.Vector3d())));
                var complete = ShipControlModuleRuntime.class.getDeclaredMethod("updateCommandCompletion", telemetryType);
                complete.setAccessible(true);
                Vec3 side = up.cross(Math.abs(up.x) < 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1)).normalize();
                Vec3 pitchRoll = side.add(up.cross(side).scale(2));
                complete.invoke(runtime, ctor.newInstance(true, current, Vec3.ZERO, pitchRoll, Vec3.ZERO, Vec3.ZERO));
                ctx.assertTrue(((java.util.Set<?>)field("completedCommands").get(runtime)).contains("hold\u0000ship_hold_yaw"), "Pitch/roll or changed heading prevented yaw completion");
                Object residual = ctor.newInstance(true, current, Vec3.ZERO, up.scale(0.001D), Vec3.ZERO, Vec3.ZERO);
                Vec3 settled = vector(controlDemand(runtime, map, residual), "controlTorque");
                ctx.assertTrue(settled.dot(braking) > 0.001D, "Completed yaw damping abandoned residual rotation: " + settled);
                for(int tick = 0; tick < 5; tick++) runtime.tick();
                ctx.assertTrue(phase.get(runtime) == ready && !runtime.isInitializing(), "Yaw completion entered initialization or failed: " + phase.get(runtime));
                ctx.assertTrue(!(boolean)field("resumeInitAfterLoad").get(runtime), "Yaw completion requested resumed initialization");
                ctx.assertTrue(runtime.execute("hold", "ship_hold_yaw", Map.of("strength", 1D)), "Repeated hold pulse was refused");
                ctx.assertTrue(!((java.util.Set<?>)field("completedCommands").get(runtime)).contains("hold\u0000ship_hold_yaw"), "Repeated pulse did not rearm damping");
                double previousCorrection = 0.0D;
                for(double strength : new double[]{0.1D, 0.5D, 1.0D}){
                    ctx.assertTrue(runtime.execute("hold", "ship_hold_yaw", Map.of("strength", strength)), "Recovery strength was refused");
                    Vec3 correction = vector(controlDemand(runtime, map, residual), "controlTorque");
                    ctx.assertTrue(correction.length() > previousCorrection, "Strength did not increase yaw recovery speed");
                    previousCorrection = correction.length();
                }
                ctx.assertTrue(runtime.execute("yaw", "ship_yaw", Map.of("amount", 1D)), "Manual yaw did not register");
                Vec3 released = controlTorque(runtime, map);
                ctx.assertTrue(released.y > 0.9D, "Yaw control did not automatically release the hold: " + released);
                runtime.tick();
                ctx.assertTrue(phase.get(runtime) == ready && !runtime.isInitializing(), "Manual control started initialization after Hold Yaw");
                for(String type : List.of("ship_hover", "ship_stabilize", "ship_decelerate", "ship_brake", "ship_hold_yaw")){
                    ctx.assertTrue(runtime.execute("zero/" + type, type, Map.of("strength", 1D)), "Recovery control was refused: " + type);
                    ctx.assertTrue(runtime.execute("zero/" + type, type, Map.of("strength", 0D)), "Zero strength was refused: " + type);
                    ctx.assertTrue(!((Map<?, ?>)field("activeCommands").get(runtime)).containsKey("zero/" + type + "\u0000" + type), "Zero strength retained control: " + type);
                    ctx.assertTrue(phase.get(runtime) == ready && !runtime.isInitializing(), "Recovery disabling changed control phase: " + type);
                }
                ctx.succeed();
            }catch(ReflectiveOperationException error){
                throw new RuntimeException(error);
            }finally{
                lease.close();
                if(dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId()) == body){
                    SableSubLevelLifecycleApi.remove(level, List.of(body));
                }
            }
        });
    }

    @GameTest(template = "empty_container", batch = "scm_linear_brake", timeoutTicks = 100)
    public static void brakingPreservesAirshipLiftAndSteering(GameTestHelper ctx){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(4, 3, 4));
        level.setBlock(pos.below(), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
        level.setBlock(pos, CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var body = SubLevelAssemblyApi.assemble(level, pos.below(), List.of(pos.below(), pos));
        ctx.assertTrue(body != null, "SCM ship did not assemble");
        var lease = SableSubLevelResidency.lease("linear_brake_test/" + body.getUniqueId());
        lease.retain(body);
        ctx.runAtTickTime(10, () -> {
            try{
                var controller = SubLevelBlockEntityCollector.getBlockEntities(body, AdvancedContraptionControllerBlockEntity.class).getFirst();
                var runtimeField = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
                runtimeField.setAccessible(true);
                var runtime = (ShipControlModuleRuntime)runtimeField.get(controller);
                var map = new ShipControlMap(UUID.randomUUID(), level.dimension().location().toString(),
                        body.getUniqueId(), controller.getBlockPos(), Vec3.ZERO, List.of(), level.getGameTime());
                field("map").set(runtime, map);
                field("rootSubLevel").set(runtime, body);
                var phase = field("phase");
                phase.set(runtime, java.util.Arrays.stream(phase.getType().getEnumConstants())
                        .filter(value -> value.toString().equals("READY")).findFirst().orElseThrow());
                ctx.assertTrue(runtime.execute("hover", "ship_hover", Map.of("strength", 1D)), "Hover did not register");
                ctx.assertTrue(runtime.execute("stabilize", "ship_stabilize", Map.of("strength", 1D)), "Stabilize did not register");
                ctx.assertTrue(runtime.execute("yaw", "ship_yaw", Map.of("amount", 1D)), "Yaw did not register");
                ctx.assertTrue(runtime.execute("forward", "ship_forward", Map.of("amount", 1D)), "Forward did not register");
                ctx.assertTrue(runtime.execute("brake", "ship_brake", Map.of("strength", 1D)), "Brake did not register");
                var commands = (Map<?, ?>)field("activeCommands").get(runtime);
                ctx.assertTrue(!commands.containsKey("forward\u0000ship_forward"), "Brake did not cancel propulsion");
                var telemetryMethod = ShipControlModuleRuntime.class.getDeclaredMethod("telemetry");
                telemetryMethod.setAccessible(true);
                Object original = telemetryMethod.invoke(runtime);
                Class<?> telemetryType = original.getClass();
                var position = telemetryType.getDeclaredMethod("position");
                position.setAccessible(true);
                Vec3 current = (Vec3)position.invoke(original);
                var ctor = telemetryType.getDeclaredConstructor(boolean.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class, Vec3.class);
                ctor.setAccessible(true);
                Object moving = ctor.newInstance(true, current, new Vec3(3, 0, 0), new Vec3(0, 1, 0), Vec3.ZERO, Vec3.ZERO);
                Object demand = controlDemand(runtime, map, moving);
                Vec3 force = vector(demand, "controlForce");
                ctx.assertTrue(force.x < -0.1D, "Brake did not oppose linear motion: " + force);
                ctx.assertTrue(vector(demand, "controlTorque").y > 0.9D, "Brake damped manual yaw");
                var completion = ShipControlModuleRuntime.class.getDeclaredMethod("updateCommandCompletion", telemetryType);
                completion.setAccessible(true);
                completion.invoke(runtime, ctor.newInstance(true, current, Vec3.ZERO, new Vec3(0, 1, 0), Vec3.ZERO, Vec3.ZERO));
                ctx.assertTrue(((java.util.Set<?>)field("completedCommands").get(runtime)).contains("brake\u0000ship_brake"), "Brake waited for angular motion to stop");
                var strength = ShipControlModuleRuntime.class.getDeclaredMethod("explicitBrakeStrength");
                strength.setAccessible(true);
                ctx.assertTrue((double)strength.invoke(runtime) == 0D, "Completed brake still applied braking");
                for(String key : List.of("hover\u0000ship_hover", "stabilize\u0000ship_stabilize", "yaw\u0000ship_yaw")){
                    ctx.assertTrue(commands.containsKey(key), "Brake removed " + key);
                }
                Object readyPhase = phase.get(runtime);
                for(int tick = 0; tick < 5; tick++) runtime.tick();
                ctx.assertTrue(phase.get(runtime) == readyPhase && !runtime.isInitializing(), "Completed Brake failed on the following control tick: " + phase.get(runtime));
                ctx.assertTrue(!(boolean)field("resumeInitAfterLoad").get(runtime), "Brake requested initialization");
                ctx.assertTrue(controlTorque(runtime, map).y > 0.9D, "Steering stopped after brake completion");
                var active = ShipControlModuleRuntime.class.getDeclaredMethod("hasActiveControlCommands");
                active.setAccessible(true);
                var retained = commands.get("brake\u0000ship_brake");
                commands.keySet().removeIf(key -> !key.equals("brake\u0000ship_brake"));
                ctx.assertTrue(retained != null && (boolean)active.invoke(runtime), "Standalone brake completion released all SCM control");
                for(int tick = 0; tick < 5; tick++) runtime.tick();
                ctx.assertTrue(phase.get(runtime) == readyPhase && !runtime.isInitializing(), "Standalone completed brake entered an error or initialization phase");
                ctx.assertTrue(runtime.execute("resume", "ship_forward", Map.of("amount", 1D)), "New movement did not register");
                ctx.assertTrue(!commands.containsKey("brake\u0000ship_brake"), "New movement did not release the settled brake");
                for(String type : List.of("ship_yaw", "ship_pitch", "ship_roll", "ship_ascend", "ship_descend", "ship_strafe")){
                    ctx.assertTrue(runtime.execute("after_brake/" + type, type, Map.of("amount", 0.5D)), "Brake blocked manual " + type);
                    runtime.tick();
                    ctx.assertTrue(phase.get(runtime) == readyPhase && !runtime.isInitializing(), "Manual control after Brake failed: " + type);
                }
                ctx.succeed();
            }catch(ReflectiveOperationException error){
                throw new RuntimeException(error);
            }finally{
                lease.close();
                if(dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId()) == body){
                    SableSubLevelLifecycleApi.remove(level, List.of(body));
                }
            }
        });
    }

    private static Field field(String name) throws ReflectiveOperationException{
        var field = ShipControlModuleRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Vec3 worldUp(ShipControlModuleRuntime runtime) throws ReflectiveOperationException{
        var local = ShipControlModuleRuntime.class.getDeclaredMethod("controllerUpRoot");
        local.setAccessible(true);
        var world = ShipControlModuleRuntime.class.getDeclaredMethod("rootDirectionToWorld", Vec3.class);
        world.setAccessible(true);
        return ((Vec3)world.invoke(runtime, local.invoke(runtime))).normalize();
    }

    private static Vec3 controlTorque(ShipControlModuleRuntime runtime, ShipControlMap map) throws ReflectiveOperationException{
        var telemetry = ShipControlModuleRuntime.class.getDeclaredMethod("telemetry");
        telemetry.setAccessible(true);
        return vector(controlDemand(runtime, map, telemetry.invoke(runtime)), "controlTorque");
    }

    private static Object controlDemand(ShipControlModuleRuntime runtime, ShipControlMap map, Object telemetry) throws ReflectiveOperationException{
        var demand = java.util.Arrays.stream(ShipControlModuleRuntime.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("demandFor")).findFirst().orElseThrow();
        demand.setAccessible(true);
        Class<?> gravityType = demand.getParameterTypes()[4];
        var gravity = gravityType.getDeclaredField("NONE");
        gravity.setAccessible(true);
        return demand.invoke(runtime, telemetry, map, null, null, gravity.get(null));
    }

    private static Vec3 vector(Object demand, String name) throws ReflectiveOperationException{
        Method method = demand.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (Vec3)method.invoke(demand);
    }
}
