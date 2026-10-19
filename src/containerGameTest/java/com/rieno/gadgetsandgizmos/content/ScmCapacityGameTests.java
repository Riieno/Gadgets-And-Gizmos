package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionDirectThrottleAccess;
import com.rieno.gadgetsandgizmos.compat.createpropulsion.PropulsionVectorThrusterAngleAccess;
import com.rieno.gadgetsandgizmos.lib.discovery.SableSubLevelResidency;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.rieno.gadgetsandgizmos.lib.kinetics.BearingHead;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.physics.SubLevelAssemblyApi;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("createthrusters")
@PrefixGameTestTemplate(false)
public final class ScmCapacityGameTests{
    private ScmCapacityGameTests(){}

    @GameTest(template = "empty_container", batch = "scm_native_capacity", timeoutTicks = 100)
    public static void autoVectorThrustersInitializeImmediatelyWithoutChangingControls(GameTestHelper ctx){
        withShip(ctx, List.of("vector_thruster", "liquid_vector_thruster"), (controller, runtime, body, targets) -> {
            var refs = new LinkedHashSet<ScmConfigurationProfile.UnitReference>();
            var previous = new HashMap<BlockPos, Map<String,Object>>();
            for(BlockEntity target : targets){
                var vector = (PropulsionVectorThrusterAngleAccess)target;
                vector.createThrusters$setVectorAngles(12, -6);
                ((PropulsionDirectThrottleAccess)target).createThrusters$setDirectThrottle(0.35);
                previous.put(target.getBlockPos(), Map.copyOf(vector.createThrusters$getVectorAngles()));
                refs.add(new ScmConfigurationProfile.UnitReference(body.getUniqueId(), target.getBlockPos(), ""));
            }
            configure(controller, refs, Set.of());
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
            var velocity = handle.getLinearVelocity(new org.joml.Vector3d());
            var rotation = handle.getAngularVelocity(new org.joml.Vector3d());
            ctx.assertTrue(runtime.rebuildConfiguredControlMap(), "Native setup was rejected");
            var map = controlMap(runtime);
            ctx.assertTrue(!runtime.isInitializing() && map != null && phase(runtime).equals("READY"), "Auto group entered physical calibration: " + phase(runtime));
            ctx.assertTrue(map.units().size() == 2 && map.bearings().size() == 2, "Vector throttle or steering geometry is missing");
            for(BlockEntity target : targets){
                ctx.assertTrue(previous.get(target.getBlockPos()).equals(((PropulsionVectorThrusterAngleAccess)target).createThrusters$getVectorAngles()), "Setup moved a nozzle");
                ctx.assertTrue(Math.abs(((PropulsionDirectThrottleAccess)target).createThrusters$getDirectThrottle() - 0.35) < 1e-6, "Setup fired or restored propulsion");
            }
            ctx.assertTrue(map.units().stream().allMatch(unit -> unit.maxThrust() > 0 && unit.samples().isEmpty()), "Setup requires observed thrust samples");
            ctx.assertTrue(handle.getLinearVelocity(new org.joml.Vector3d()).distance(velocity) < 1e-9
                    && handle.getAngularVelocity(new org.joml.Vector3d()).distance(rotation) < 1e-9, "Setup changed ship motion");
            ctx.assertTrue(runtime.execute("capacity_yaw", "ship_yaw", Map.of("amount",0.5D,"strength",1D)), "Manual yaw failed after setup");
            runtime.tick();
            ctx.assertTrue(phase(runtime).equals("READY") && !runtime.isInitializing(), "Manual control restarted calibration");
        });
    }

    @GameTest(template = "empty_container", batch = "scm_strafe", timeoutTicks = 100)
    public static void strafeSelectsOnlyTheRequestedLateralEngine(GameTestHelper ctx){
        withShip(ctx, List.of("vector_thruster", "liquid_vector_thruster"), (controller, runtime, body, targets) -> {
            try{
                var forwardMethod = ShipControlModuleRuntime.class.getDeclaredMethod("controllerForwardRoot");forwardMethod.setAccessible(true);
                var upMethod = ShipControlModuleRuntime.class.getDeclaredMethod("controllerUpRoot");upMethod.setAccessible(true);
                Vec3 right = ((Vec3)forwardMethod.invoke(runtime)).cross((Vec3)upMethod.invoke(runtime)).normalize();
                Direction rightFacing = Direction.getNearest(right.x,right.y,right.z);
                for(int idx = 0; idx < targets.size(); idx++){
                    BlockEntity target = targets.get(idx);
                    var state = target.getBlockState().setValue(BlockStateProperties.FACING,idx == 0 ? rightFacing : rightFacing.getOpposite());
                    body.getPlot().getLoadedChunks().stream().map(plotChunk -> plotChunk.getChunk())
                            .filter(chunk -> chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(target.getBlockPos())))
                            .findFirst().orElseThrow().setBlockState(target.getBlockPos(),state,false);
                    target.setBlockState(state);
                }
                var refs = new LinkedHashSet<ScmConfigurationProfile.UnitReference>();
                targets.forEach(target -> refs.add(new ScmConfigurationProfile.UnitReference(body.getUniqueId(),target.getBlockPos(),"")));
                configure(controller,refs,Set.of());
                ctx.assertTrue(runtime.rebuildConfiguredControlMap(), "Strafe profile setup failed");
                ctx.assertTrue(runtime.execute("strafe_direction", "ship_strafe", Map.of("amount",0.8D)), "Strafe command rejected");
            }catch(ReflectiveOperationException err){ throw new AssertionError(err); }
        }, (controller,runtime,body,targets) -> {
                runtime.tick();
                double positive = ((PropulsionDirectThrottleAccess)targets.get(0)).createThrusters$getDirectThrottle();
                double negative = ((PropulsionDirectThrottleAccess)targets.get(1)).createThrusters$getDirectThrottle();
                ctx.assertTrue(positive > 0.1D && negative < 1e-3, "Right strafe drove opposing engines: " + positive + " / " + negative);
                runtime.execute("strafe_direction", "ship_strafe", Map.of("amount",-0.8D));
                runtime.tick();
                positive = ((PropulsionDirectThrottleAccess)targets.get(0)).createThrusters$getDirectThrottle();
                negative = ((PropulsionDirectThrottleAccess)targets.get(1)).createThrusters$getDirectThrottle();
                ctx.assertTrue(negative > 0.1D && positive < 1e-3, "Left strafe drove opposing engines: " + positive + " / " + negative);
        });
    }

    @GameTest(template = "empty_container", batch = "scm_vector_yaw", timeoutTicks = 100)
    public static void autoVectorYawUsesOpposedNativeGimbals(GameTestHelper ctx){
        withShip(ctx, List.of("vector_thruster", "liquid_vector_thruster", "vector_thruster", "liquid_vector_thruster"), (controller, runtime, body, targets) -> {
            var refs = new LinkedHashSet<ScmConfigurationProfile.UnitReference>();
            targets.forEach(target -> refs.add(new ScmConfigurationProfile.UnitReference(body.getUniqueId(),target.getBlockPos(),"")));
            configure(controller,refs,Set.of());
            ctx.assertTrue(runtime.rebuildConfiguredControlMap(), "Vector profile setup failed");
            // Establish live geometry and actuators without a physical angle sweep
            ctx.assertTrue(runtime.execute("vector_hover", "ship_hover", Map.of("strength",1D)), "Hover was rejected");
            ctx.assertTrue(runtime.execute("vector_yaw", "ship_yaw", Map.of("amount",0.25D)), "Yaw was rejected");
        }, (controller,runtime,body,targets) -> {
            runtime.tick();
            ShipControlMap live = (ShipControlMap)get(runtime,"activeAssemblyMap");
            if(live == null) live = controlMap(runtime);
            try{
                var unitsMethod = ShipControlModuleRuntime.class.getDeclaredMethod("precisionUnits",ShipControlMap.class);
                unitsMethod.setAccessible(true);
                @SuppressWarnings("unchecked") var units = (List<com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator.Unit>)unitsMethod.invoke(runtime,live);
                ctx.assertTrue(units.stream().allMatch(unit -> unit.coneDegrees() == 30D), "Native vector capacity omitted its steering cone");
                Vec3 support = units.stream().map(unit -> unit.force().scale(0.6D)).reduce(Vec3.ZERO,Vec3::add);
                Vec3 yaw = com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator.physicalTorque(units,new Vec3(0,0.2D,0));
                var solved = com.rieno.gadgetsandgizmos.lib.scm.ScmVectorThrustAllocator.allocate(units,support,yaw,true);
                var apply = ShipControlModuleRuntime.class.getDeclaredMethod("applyNativeVectorForces",ShipControlMap.class,List.class);
                apply.setAccessible(true);apply.invoke(runtime,live,solved.forces());
                Vec3 actual = Vec3.ZERO;
                Vec3 torque = Vec3.ZERO;
                for(int idx = 0; idx < live.units().size(); idx++){
                    var unit = live.units().get(idx);
                    BlockEntity target = targets.stream().filter(be -> be.getBlockPos().equals(unit.blockPosition())).findFirst().orElseThrow();
                    for(int step = 0; step < 80; step++) target.getClass().getMethod("tick").invoke(target);
                    var nativeDirection = (org.joml.Vector3dc)target.getClass().getMethod("getThrustDirectionLocal").invoke(target);
                    Vec3 direction = new Vec3(nativeDirection.x(),nativeDirection.y(),nativeDirection.z());
                    Vec3 predicted = solved.forces().get(idx);
                    if(predicted.lengthSqr() <= 1e-8) continue;
                    // All engines share this native body; convert their directions through the root
                    var local = new org.joml.Vector3d(direction.x,direction.y,direction.z);
                    body.logicalPose().orientation().transform(local);
                    var convert = ShipControlModuleRuntime.class.getDeclaredMethod("worldDirectionToRoot",Vec3.class);
                    convert.setAccessible(true);
                    Vec3 rootDirection = (Vec3)convert.invoke(runtime,new Vec3(local.x,local.y,local.z));
                    ctx.assertTrue(rootDirection.normalize().dot(predicted.normalize()) > 0.99999D,
                            "Native gimbal direction differs from allocated force: " + rootDirection + " / " + predicted);
                    Vec3 force = rootDirection.scale(predicted.length());
                    actual = actual.add(force);
                    torque = torque.add(units.get(idx).momentArm().cross(force));
                }
                ctx.assertTrue(Math.abs(actual.x) + Math.abs(actual.z) < support.length()*0.01D,
                        "Yaw allocation produced unwanted strafe: " + actual);
                ctx.assertTrue(torque.y * yaw.y > 0 && Math.abs(torque.y-yaw.y) < Math.abs(yaw.y)*0.05D,
                        "Native engines did not produce the requested yaw torque: " + torque + " / " + yaw);
            }catch(ReflectiveOperationException err){ throw new AssertionError(err); }
        });
    }

    @GameTest(template = "empty_container", batch = "scm_capacity_blacklist", timeoutTicks = 100)
    public static void blacklistSkipsEveryAdapterBeforeDiscoveryOrInitialization(GameTestHelper ctx){
        withShip(ctx, List.of("vector_thruster", "liquid_vector_thruster"), (controller, runtime, body, targets) -> {
            var refs = new LinkedHashSet<ScmConfigurationProfile.UnitReference>();
            targets.forEach(be -> refs.add(new ScmConfigurationProfile.UnitReference(body.getUniqueId(),be.getBlockPos(),"")));
            BlockEntity excluded = targets.getFirst();
            ((PropulsionVectorThrusterAngleAccess)excluded).createThrusters$setVectorAngles(8, 4);
            ((PropulsionDirectThrottleAccess)excluded).createThrusters$setDirectThrottle(0.4);
            var original = Map.copyOf(((PropulsionVectorThrusterAngleAccess)excluded).createThrusters$getVectorAngles());
            configure(controller, refs, Set.of(new ScmConfigurationProfile.UnitReference(body.getUniqueId(), excluded.getBlockPos(), "scm:obsolete_adapter", Direction.NORTH)));
            ctx.assertTrue(runtime.rebuildConfiguredControlMap(), "Filtered setup failed");
            var map = controlMap(runtime);
            ctx.assertTrue(map != null && map.units().size() == 1 && map.bearings().size() == 1, "Blacklisted propulsion or bearing was initialized");
            ctx.assertTrue(runtime.configurationCandidates().stream().noneMatch(ref -> ref.blockPosition().equals(excluded.getBlockPos())), "Blacklisted target reached configured discovery");
            ctx.assertTrue(original.equals(((PropulsionVectorThrusterAngleAccess)excluded).createThrusters$getVectorAngles())
                    && Math.abs(((PropulsionDirectThrottleAccess)excluded).createThrusters$getDirectThrottle() - 0.4) < 1e-6, "Blacklist changed excluded controls");
            ctx.assertTrue(runtime.initializeControlModule(), "Repeated native setup failed");
            ctx.assertTrue(!runtime.isInitializing() && controlMap(runtime).units().size() == 1, "Repeated setup bypassed blacklist");
            configure(controller, refs, refs);
            ctx.assertTrue(runtime.rebuildConfiguredControlMap() && !runtime.isInitializing(), "All-blacklisted profile entered calibration");
            ctx.assertTrue(controlMap(runtime) == null && phase(runtime).equals("CONFIGURING"), "All-blacklisted profile retained active actuators");
            ctx.assertTrue(((Set<?>)get(runtime,"initProtectedSubLevelIds")).isEmpty(), "All-blacklisted profile left ship controls protected");

        });
    }

    @GameTest(template = "empty_container", batch = "scm_capacity_bearings", timeoutTicks = 100)
    public static void everyAddonBearingUsesCapacityWithoutAnAngleSweep(GameTestHelper ctx){
        withShip(ctx, List.of("vector_thruster"), (controller, runtime, body, targets) -> {
            var level = ctx.getLevel();
            List<ServerSubLevel> children = new ArrayList<>();
            var lease = SableSubLevelResidency.lease("capacity_bearing_children/" + body.getUniqueId());
            try{
                BlockPos base = controller.getBlockPos().offset(0,0,2);
                var refs = new LinkedHashSet<ScmConfigurationProfile.UnitReference>();
                refs.add(new ScmConfigurationProfile.UnitReference(body.getUniqueId(),targets.getFirst().getBlockPos(),""));
                var states = List.of(CTBlocks.VECTOR_BEARING.get().defaultBlockState(), CTBlocks.THRUSTER_BEARING.get().defaultBlockState(), CTBlocks.AILERON_BEARING.get().defaultBlockState());
                List<BlockEntity> bearings = new ArrayList<>();
                for(int i=0;i<states.size();i++){
                    BlockPos pos = base.offset(i*2,0,0);
                    body.getLevel().setBlock(pos,states.get(i),3);
                    BlockEntity bearing = SubLevelBlockEntityCollector.getBlockEntity(body,pos);
                    ctx.assertTrue(bearing != null, "Bearing was not placed");
                    bearings.add(bearing);
                    BlockPos childPos = ctx.absolutePos(new BlockPos(2+i*3,6,8));
                    var engine = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("createpropulsion","vector_thruster"));
                    level.setBlock(childPos,engine.defaultBlockState(),3);
                    var child = SubLevelAssemblyApi.assembleSingle(level,childPos);
                    ctx.assertTrue(child != null,"Bearing child did not assemble");
                    children.add(child);lease.retain(child);
                    BlockPos local = SubLevelBlockEntityCollector.getBlockEntities(child).getFirst().getBlockPos();
                    if(bearing instanceof VectorBearingBlockEntity vector){
                        vector.setMountedAssembly(child.getUniqueId(),local);
                        set(vector,"mountedSailPower",2D);
                    }else if(bearing instanceof AileronBearingBlockEntity aileron){
                        aileron.setMountedAssembly(BearingHead.PRIMARY,child.getUniqueId(),local);
                    }else{
                        try{ bearing.getClass().getMethod("setSubLevelID",UUID.class).invoke(bearing,child.getUniqueId()); }
                        catch(ReflectiveOperationException err){ throw new AssertionError(err); }
                    }
                    refs.add(new ScmConfigurationProfile.UnitReference(body.getUniqueId(),pos,""));
                }
                configure(controller,refs,Set.of());
                ctx.assertTrue(runtime.rebuildConfiguredControlMap(),"Bearing native setup failed");
                var map = controlMap(runtime);
                ctx.assertTrue(!runtime.isInitializing() && phase(runtime).equals("READY"),"Bearing setup entered calibration");
                for(String adapter : List.of("vector_bearing","thruster_bearing","aileron_primary")){
                    var bearing = map.bearings().stream().filter(val -> adapter.equals(val.adapter())).findFirst().orElse(null);
                    ctx.assertTrue(bearing != null && !bearing.poses().isEmpty(),"Missing declared bearing geometry: " + adapter);
                    ctx.assertTrue(bearing.poses().stream().allMatch(pose -> !pose.responses().isEmpty()
                            && pose.responses().stream().allMatch(response -> response.maxThrust() > 0)),"Bearing has no declared capacity: " + adapter);
                }
                ctx.assertTrue(!((VectorBearingBlockEntity)bearings.get(0)).hasComputerOverride(),"Setup moved vector bearing");
                ctx.assertTrue(!((ThrusterBearingBlockEntity)bearings.get(1)).hasPivotOverride(),"Setup moved thruster bearing");
                ctx.assertTrue(!((AileronBearingBlockEntity)bearings.get(2)).hasHeadTargetOverride(BearingHead.PRIMARY),"Setup moved aileron bearing");
            }finally{
                lease.close();
                SableSubLevelLifecycleApi.remove(level,children.stream().filter(child -> dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(child.getUniqueId()) == child).toList());
            }
        });
    }

    private static void withShip(GameTestHelper ctx,List<String> engines,Exercise exercise){
        withShip(ctx,engines,null,exercise);
    }

    private static void withShip(GameTestHelper ctx,List<String> engines,Exercise prepare, Exercise exercise){
        var level = ctx.getLevel();
        BlockPos pos = ctx.absolutePos(new BlockPos(3,3,3));
        List<BlockPos> blocks = new ArrayList<>(List.of(pos.below(),pos));
        level.setBlock(pos.below(),CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(),3);
        level.setBlock(pos,CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(),3);
        for(int i=0;i<engines.size();i++){
            BlockPos target = pos.offset(2+i*2,0,0);
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("createpropulsion",engines.get(i)));
            ctx.assertTrue(block != Blocks.AIR,"Missing native Propulsion test block");
            level.setBlock(target,block.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP),3);blocks.add(target);
        }
        for(int i=1;i<engines.size()*2+1;i+=2){
            BlockPos bridge = pos.offset(i,0,0);
            level.setBlock(bridge,Blocks.STONE.defaultBlockState(),3);blocks.add(bridge);
        }
        var body = SubLevelAssemblyApi.assemble(level,pos.below(),blocks);
        ctx.assertTrue(body != null,"SCM ship did not assemble");
        var lease = SableSubLevelResidency.lease("capacity_ship/" + body.getUniqueId());lease.retain(body);
        ctx.runAtTickTime(10,() -> {
            try{
                var controller = SubLevelBlockEntityCollector.getBlockEntities(body,AdvancedContraptionControllerBlockEntity.class).getFirst();
                var runtime = (ShipControlModuleRuntime)get(controller,"shipControlRuntime");
                var targets = SubLevelBlockEntityCollector.getBlockEntities(body).stream()
                        .filter(be -> be instanceof PropulsionVectorThrusterAngleAccess).toList();
                ctx.assertTrue(targets.size() == engines.size(),"Propulsion mixin is missing: " + SubLevelBlockEntityCollector.getBlockEntities(body).stream().map(be -> be.getClass().getName() + " at " + be.getBlockPos()).toList());
                Runnable verify = () -> {
                    try{
                        exercise.run(controller,runtime,body,targets);
                        ctx.succeed();
                    }finally{
                        lease.close();
                        if(dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId()) == body)
                            SableSubLevelLifecycleApi.remove(level,List.of(body));
                    }
                };
                if(prepare == null) verify.run();
                else{
                    prepare.run(controller,runtime,body,targets);
                    ctx.runAfterDelay(1,runtime::tick);
                    ctx.runAfterDelay(2,runtime::tick);
                    ctx.runAfterDelay(4,verify);
                }
            }catch(RuntimeException | Error err){
                lease.close();
                if(dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(body.getUniqueId()) == body)
                    SableSubLevelLifecycleApi.remove(level,List.of(body));
                throw err;
            }
        });
    }

    private static void configure(AdvancedContraptionControllerBlockEntity controller,Set<ScmConfigurationProfile.UnitReference> units,
                                  Set<ScmConfigurationProfile.UnitReference> excluded){
        var profile = ScmConfigurationProfile.empty();
        profile.replace(UUID.randomUUID(),List.of(new ScmConfigurationProfile.Group("engines","Engines",units)),
                Map.of(ScmConfigurationProfile.AUTO_ACTION,"engines"),excluded);
        controller.setScmConfigurationProfile(profile);
    }
    private static ShipControlMap controlMap(ShipControlModuleRuntime runtime){ return (ShipControlMap)get(runtime,"map"); }
    private static String phase(ShipControlModuleRuntime runtime){ return get(runtime,"phase").toString(); }
    private static Object get(Object owner,String name){
        try{var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
        catch(ReflectiveOperationException err){throw new AssertionError(err);}
    }
    private static void set(Object owner,String name,Object value){
        try{var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
        catch(ReflectiveOperationException err){throw new AssertionError(err);}
    }
    private interface Exercise{
        void run(AdvancedContraptionControllerBlockEntity controller,ShipControlModuleRuntime runtime,ServerSubLevel body,List<BlockEntity> targets);
    }
}
