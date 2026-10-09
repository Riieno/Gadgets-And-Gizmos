package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import ace.flight.block.SmartVectorThrusterBlockEntity;
import ace.flight.block.SmartVectorIonThrusterBlockEntity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlNodes;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlProfiles;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlScmAllocator;
import com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlGraphRuntime;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.control.VectorThrustReceiver;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.scm.ScmVectorAllocationRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Check native ABI, editable graph metadata and physical engine constraints against the installed jar
class FlightControlCompatibilityTest{
    private static List<FlightControlNodes.Spec> specs;

    @BeforeAll
    static void bootstrap() throws Exception{
        ControllerTestBootstrap.bootstrap();
        try(var stream = FlightControlNodes.class.getResourceAsStream("/data/createthrusters/compat/flight_control_nodes.json")){
            specs = new Gson().fromJson(new InputStreamReader(stream), new TypeToken<List<FlightControlNodes.Spec>>(){}.getType());
        }
        FlightControlNodes.register();
    }

    // Every graph setting must match the actual 0.7.7 constructor and native setter ABI
    @Test
    void allControlBlocksHaveCompatibleNativeBindings() throws Exception{
        assertEquals(15, specs.size());
        for(var spec : specs){
            ClassNode nativeType = bytecode(spec.className());
            assertTrue(nativeType.methods.stream().anyMatch(method -> method.name.equals("<init>")
                    && method.desc.equals("(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V")), spec.path());
            if(spec.config().isEmpty()) continue;
            var setter = nativeType.methods.stream().filter(method -> method.name.equals("setConfig")
                    && Type.getArgumentTypes(method.desc).length == spec.config().size()).findFirst().orElseThrow();
            Type[] types = Type.getArgumentTypes(setter.desc);
            for(int idx = 0; idx < types.length; idx++){
                String name = spec.config().get(idx);
                var field = spec.fields().stream().filter(candidate -> candidate.name().equals(name)).findFirst().orElseThrow();
                String expected = switch(types[idx].getSort()){
                    case Type.BOOLEAN -> "boolean";
                    case Type.INT, Type.DOUBLE -> "number";
                    default -> "string";
                };
                assertEquals(expected, field.type(), spec.path() + ":" + name);
                if(types[idx].getSort() == Type.OBJECT){
                    ClassNode enumeration = bytecode(types[idx].getClassName());
                    assertTrue(enumeration.fields.stream().anyMatch(option -> option.name.equalsIgnoreCase(field.defaultValue().asString())), spec.path() + ":" + name);
                }
            }
        }
    }

    // Every exposed action must match the production jar's native redstone action list
    @Test
    void graphSignalsMatchEveryNativeAction() throws Exception{
        for(var spec : specs){
            java.util.Set<String> expected = nativeActions(spec.className());
            if(spec.path().equals("creative_flight_control_computer")){
                for(String action : nativeActions("ace.flight.block.MouseFlightControllerBlockEntity")) expected.add("flight_terminal." + action);
            }
            java.util.Set<String> actual = spec.actions().stream().map(FlightControlNodes.Action::id)
                    .collect(java.util.stream.Collectors.toSet());
            assertEquals(expected, actual, spec.path());
        }
    }

    // Native mouse packets and lease pruning must resolve the same virtual identity
    @Test
    void mouseNetworkingRedirectsMatchProductionBytecode() throws Exception{
        assertInvocation("ace.flight.neoforge.FlightNetworkingNeoForge", "lambda$handleSetMouseFlightTarget$0",
                "net/minecraft/world/level/Level", "getBlockEntity");
        assertInvocation("ace.flight.neoforge.MouseFlightControlAuthority", "prune",
                "net/minecraft/server/level/ServerLevel", "getBlockEntity");
    }

    // Hosted rendering must continue to use the installed mod's HUD layout and native render type
    @Test
    void nativeHudRenderingMatchesProductionDescriptors() throws Exception{
        var renderer = bytecode("ace.flight.neoforge.client.HudProjectorBlockEntityRenderer");
        assertTrue(renderer.methods.stream().anyMatch(val -> val.name.equals("projectorMatrix")
                && val.desc.equals("(Lorg/joml/Matrix4f;Lace/flight/block/HudProjectorBlockEntity;)Lorg/joml/Matrix4f;")));
        assertTrue(renderer.methods.stream().anyMatch(val -> val.name.equals("renderHud")
                && val.desc.equals("(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZDDDD)V")));
        assertTrue(renderer.methods.stream().anyMatch(val -> val.name.equals("facingYaw")
                && val.desc.equals("(Lnet/minecraft/world/level/block/state/BlockState;)F")));
        assertTrue(renderer.methods.stream().anyMatch(val -> val.name.equals("render")
                && val.desc.equals("(Lace/flight/block/HudProjectorBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V")));
        var types = bytecode("ace.flight.neoforge.client.FlightRenderTypes");
        assertTrue(types.fields.stream().anyMatch(val -> val.name.equals("HUD_PROJECTOR")
                && val.desc.equals("Lnet/minecraft/client/renderer/RenderType;")));
    }

    // Projection hooks retain the installed native miniature and pointer implementations
    @Test
    void nativeProjectionHooksMatchProductionDescriptors() throws Exception{
        var display = bytecode("ace.flight.neoforge.client.AttitudeDisplayBlockEntityRenderer");
        assertTrue(display.methods.stream().anyMatch(val -> val.name.equals("cacheSubLevel")
                && val.desc.equals("(Lace/flight/block/AttitudeDisplayBlockEntity;Lcom/mojang/blaze3d/vertex/PoseStack;)V")));
        var world = bytecode("ace.flight.neoforge.client.AttitudeDisplaySubLevelWorldRenderer");
        assertTrue(world.methods.stream().anyMatch(val -> val.name.equals("resolveConnectedSubLevels")
                && val.desc.equals("(Ldev/ryanhcode/sable/sublevel/ClientSubLevel;)Ljava/util/List;")));
        assertTrue(world.methods.stream().anyMatch(val -> val.name.equals("renderMiniatureStructure")
                && Type.getArgumentTypes(val.desc).length == 7));
        var transform = bytecode("ace.flight.neoforge.client.AttitudeDisplaySubLevelWorldRenderer$HologramTransform");
        assertTrue(transform.methods.stream().anyMatch(val -> val.name.equals("create")
                && Type.getArgumentTypes(val.desc).length == 2));
        assertTrue(display.methods.stream().anyMatch(val -> val.name.equals("resolveOrientation")
                && val.desc.equals("(Lace/flight/block/AttitudeDisplayBlockEntity;)Lorg/joml/Quaterniond;")));
        var trail = bytecode("ace.flight.neoforge.client.sonic.SonicBoomClientEffects");
        assertTrue(trail.methods.stream().anyMatch(val -> val.name.equals("sampleTrailCreator")
                && val.desc.equals("(Ldev/ryanhcode/sable/sublevel/ClientSubLevel;Ldev/ryanhcode/sable/companion/math/Pose3dc;Lace/flight/block/AerodynamicTrailCreatorBlockEntity;)V")));
        assertTrue(trail.methods.stream().anyMatch(val -> val.name.equals("renderAerodynamicTrailLocal")
                && val.desc.equals("(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lace/flight/neoforge/client/sonic/SonicBoomClientEffects$ClientTrail;Ldev/ryanhcode/sable/companion/math/Pose3dc;Lnet/minecraft/core/BlockPos;Lorg/joml/Vector3d;)V")));
        for(String name : List.of("TRAIL_ENTITY_KEYS", "TRAILS")){
            assertTrue(trail.fields.stream().anyMatch(val -> val.name.equals(name) && val.desc.equals("Ljava/util/Map;")));
        }
        var trailTypes = bytecode("ace.flight.neoforge.client.sonic.SonicBoomRenderTypes");
        assertTrue(trailTypes.methods.stream().anyMatch(val -> val.name.equals("sonicCloudGlow")
                && val.desc.equals("()Lnet/minecraft/client/renderer/RenderType;")));
        var terminal = bytecode("ace.flight.neoforge.client.NavigationTerminalBlockEntityRenderer");
        assertTrue(terminal.methods.stream().anyMatch(val -> val.name.equals("pointerRotation")
                && val.desc.equals("(Lace/flight/block/NavigationTerminalBlockEntity;)F")));
        assertTrue(terminal.methods.stream().anyMatch(val -> val.name.equals("renderSafe")
                && val.desc.equals("(Lace/flight/block/NavigationTerminalBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V")));
    }

    // Easy integration may host ordinary nodes but the creative computer retains its block requirement
    @Test
    void creativeComputerNodeAlwaysRequiresItsPhysicalBlock(){
        var runtime = new FlightControlGraphRuntime(mock(AdvancedContraptionControllerBlockEntity.class));
        var tag = new net.minecraft.nbt.CompoundTag();
        String creative = FlightControlNodes.PREFIX + "creative_flight_control_computer";
        String altitude = FlightControlNodes.PREFIX + "altitude_hold";
        String hud = FlightControlNodes.PREFIX + "hud_projector";
        for(boolean easy : new boolean[]{false, true}){
            tag.putBoolean("EasyIntegration", easy);
            runtime.load(tag);
            assertFalse(runtime.nodeAvailable(creative));
            assertEquals(easy, runtime.nodeAvailable(altitude));
            assertTrue(runtime.nodeAvailable(hud));
        }
        var available = new net.minecraft.nbt.ListTag();
        available.add(net.minecraft.nbt.StringTag.valueOf(creative));
        tag.put("AvailableTypes", available);
        tag.putBoolean("EasyIntegration", true);
        runtime.load(tag);
        assertTrue(runtime.nodeAvailable(creative));
        assertTrue(runtime.nodeAvailable(altitude));
        available.clear();
        tag.putBoolean("EasyIntegration", false);
        runtime.load(tag);
        assertFalse(runtime.nodeAvailable(creative));
        assertFalse(runtime.nodeAvailable(altitude));
        assertTrue(runtime.nodeAvailable(hud));
    }

    // Piloting one mouse node must leave other mouse nodes idle unless their own graph signals request control
    @Test
    void mousePilotAndGraphSignalsRetainIndependentIntent() throws Exception{
        var runtime = new FlightControlGraphRuntime(mock(AdvancedContraptionControllerBlockEntity.class));
        var first = mock(ace.flight.block.MouseFlightControllerBlockEntity.class);
        var second = mock(ace.flight.block.MouseFlightControllerBlockEntity.class);
        first.lastTotalPowered = second.lastTotalPowered = true;
        first.mouseViewLockEnabled = second.mouseViewLockEnabled = true;
        var entryType = Class.forName(FlightControlGraphRuntime.class.getName() + "$Entry");
        var constructor = entryType.getDeclaredConstructor(FlightControlNodes.Spec.class, BlockEntity.class, int.class);
        constructor.setAccessible(true);
        var spec = specs.stream().filter(val -> val.path().equals("mouse_flight_controller")).findFirst().orElseThrow();
        Object firstEntry = constructor.newInstance(spec, first, 1);
        Object secondEntry = constructor.newInstance(spec, second, 2);
        var snapshot = FlightControlGraphRuntime.class.getDeclaredField("physicsEntries");
        snapshot.setAccessible(true);
        snapshot.set(runtime, List.of(firstEntry, secondEntry));
        var pilot = FlightControlGraphRuntime.class.getDeclaredField("mousePilotActive");
        pilot.setAccessible(true);
        pilot.setBoolean(runtime, true);
        assertSame(first, runtime.pilotMouse());
        assertTrue(runtime.mouseRequested(first));
        assertFalse(runtime.mouseRequested(second));
        first.mouseViewLockEnabled = false;
        assertSame(second, runtime.pilotMouse());
        assertFalse(runtime.mouseRequested(first));
        assertTrue(runtime.mouseRequested(second));
        pilot.setBoolean(runtime, false);
        assertFalse(runtime.mouseRequested(second));
        var signal = entryType.getDeclaredField("mouseSignalActive");
        signal.setAccessible(true);
        signal.setBoolean(firstEntry, true);
        assertTrue(runtime.mouseRequested(first));
        assertFalse(runtime.mouseRequested(second));
        first.lastTotalPowered = second.lastTotalPowered = false;
        assertNull(runtime.pilotMouse());
    }

    // Native physics wrappers must match the installed jar, including composite bodies and environment support
    @Test
    void nativeForceRequestHooksMatchProductionDescriptors() throws Exception{
        var computer = bytecode("ace.flight.block.FlightControlComputerBlockEntity");
        assertTrue(computer.methods.stream().anyMatch(val -> val.name.equals("computeCompositeBodyState")
                && val.desc.equals("(Ljava/lang/Object;)Lace/flight/physics/FlightPhysicsController$BodyStateOverride;")));
        var physics = bytecode("ace.flight.physics.FlightPhysicsController");
        assertTrue(physics.methods.stream().anyMatch(val -> val.name.equals("getBodyStateOverride")
                && val.desc.equals("(Ljava/lang/Object;)Lace/flight/physics/FlightPhysicsController$BodyStateOverride;")));
        var creative = bytecode("ace.flight.block.CreativeFlightControlComputerBlockEntity");
        assertTrue(creative.methods.stream().anyMatch(val -> val.name.equals("compensateLinearEnvironment")
                && val.desc.equals("(Ljava/lang/Object;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;DLorg/joml/Vector3d;Lorg/joml/Vector3d;)V")));
        var semantics = bytecode("ace.flight.physics.SableVelocitySemantics");
        assertTrue(semantics.methods.stream().anyMatch(val -> val.name.equals("predictLinearEnvironmentStep")
                && Type.getArgumentTypes(val.desc).length == 4));
    }

    // Category ports stay adjacent and typed defaults survive the editor's NBT format
    @Test
    void nativeNodesHaveCollapsibleSectionsAndRoundTripDefaults(){
        for(var spec : specs){
            String type = FlightControlNodes.PREFIX + spec.path();
            var data = AdvancedGraphNodeFactory.createDefaultData(type, AdvancedGraphNodeFactory.Context.EMPTY);
            var node = new AdvancedGraphDocument.Node("native", type, "", 0, 0, data);
            assertEquals("flight_control", AdvancedGraphCatalog.get(type).category());
            for(String port : FlightControlNodes.RETIRED_INPUTS) assertFalse(AdvancedGraphCatalog.inputs(node).containsKey(port), spec.path() + ":" + port);
            assertFalse(data.getCompound("PortSections").getCompound("Inputs").isEmpty(), spec.path());
            for(var field : spec.fields()){
                var saved = data.getCompound("Defaults").getCompound(field.port());
                var val = new AdvancedGraphDocument.Value(saved.getString("Type"), saved.getCompound("Payload"));
                assertEquals(GraphRuntime.fromLibraryValue(field.defaultValue()), val, spec.path() + ":" + field.port());
            }
            var ports = List.copyOf(AdvancedGraphCatalog.inputs(node).keySet());
            for(String group : data.getCompound("PortSections").getCompound("Inputs").getAllKeys()){
                var section = data.getCompound("PortSections").getCompound("Inputs").getCompound(group).getList("Ports", 8);
                int first = ports.indexOf(section.getString(0));
                for(int idx = 0; idx < section.size(); idx++) assertEquals(first + idx, ports.indexOf(section.getString(idx)), spec.path());
            }
        }
    }

    // Saved block address wires are retired while navigation profile wires remain usable
    @Test
    void savedBlockReferencesMigrateWithoutRemovingNavigationInputs(){
        var controller = mock(AdvancedContraptionControllerBlockEntity.class);
        var graph = new AdvancedGraphDocument();
        String computerType = FlightControlNodes.PREFIX + "flight_control_computer";
        String holdType = FlightControlNodes.PREFIX + "attitude_hold";
        String terminalType = FlightControlNodes.PREFIX + "navigation_terminal";
        var computer = new AdvancedGraphDocument.Node("computer", computerType, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(computerType, AdvancedGraphNodeFactory.Context.EMPTY));
        var hold = new AdvancedGraphDocument.Node("hold", holdType, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(holdType, AdvancedGraphNodeFactory.Context.EMPTY));
        var terminal = new AdvancedGraphDocument.Node("terminal", terminalType, "", 0, 0,
                AdvancedGraphNodeFactory.createDefaultData(terminalType, AdvancedGraphNodeFactory.Context.EMPTY));
        hold.data().getCompound("Defaults").put("block_position", AdvancedGraphDocument.Value.string("obsolete").toTag());
        hold.data().put("DynamicInputs", new net.minecraft.nbt.CompoundTag());
        hold.data().getCompound("DynamicInputs").putString("computer", "string");
        graph.nodes().addAll(List.of(computer, hold, terminal));
        graph.edges().add(new AdvancedGraphDocument.Edge("computer-wire", "computer", "component", "hold", "computer"));
        graph.edges().add(new AdvancedGraphDocument.Edge("profile-wire", "computer", "profile", "terminal", "profile"));
        try(var outputs = mockStatic(com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.class)){
            outputs.when(() -> com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlCompatibility.outputs(controller, "computer"))
                    .thenReturn(Map.of("component", GraphValue.string("computer"),
                            "profile", GraphValue.map(Map.of("component", "computer"))));
            var migrated = graph.copy();
            assertEquals(List.of("profile-wire"), migrated.edges().stream().map(AdvancedGraphDocument.Edge::id).toList());
            var migratedHold = migrated.nodes().stream().filter(node -> node.id().equals("hold")).findFirst().orElseThrow();
            assertFalse(migratedHold.data().getCompound("Defaults").contains("block_position"));
            assertFalse(AdvancedGraphCatalog.inputs(migratedHold).containsKey("computer"));
            var runtime = new GraphRuntime(controller, true);
            assertEquals("computer", GraphRuntime.toLibraryValue(runtime.previewInput(graph, terminal, "profile")).member("component").asString());
        }
    }

    // Native guidance receives orbit, angular velocity, hover and self-frame settings unchanged
    @Test
    void profilesRetainAdvancedNativeGuidance(){
        var vals = Map.of("profile_name", GraphValue.string("Route"), "end_behavior", GraphValue.string("hover"),
                "hover_attitude", GraphValue.map(Map.of("pitch", 10, "roll", 20, "yaw", 30)), "paths", GraphValue.list(List.of(Map.of(
                        "x", 10, "y", 20, "z", 30, "pitch", Map.of("mode", "fixed_angular_velocity", "value", .2),
                        "yaw", Map.of("mode", "velocity_direction"), "linear_velocity_throttle", 7,
                        "self_offset_frame", true, "x_reference", Map.of("source", "self_static", "component", "x"),
                        "flight_preset", Map.of("mode", "orbit", "radius", 15)))));
        var draft = FlightControlProfiles.draft(vals, List.of());
        var path = draft.paths().getFirst();
        assertEquals(7, path.linearVelocityThrottle());
        assertEquals(ace.flight.navigation.NavigationProfileDraft.Mode.FIXED_ANGULAR_VELOCITY, path.pitch().mode());
        assertTrue(path.selfOffsetFrame());
        assertInstanceOf(ace.flight.navigation.NavigationProfileDefinition.OrbitFlightPreset.class, path.flightPreset());
        assertEquals(20, draft.hoverAttitude().rollDegrees());
        assertThrows(IllegalArgumentException.class, () -> FlightControlProfiles.path(GraphValue.map(Map.of(
                "roll", Map.of("mode", "velocity_direction")))));
    }

    // All ion subclasses use the same scalar discovery path as the native vector base
    @Test
    void ionThrustersAreDiscoveredByScm() throws Exception{
        var method = ShipControlModuleRuntime.class.getDeclaredMethod("isFlightControlVectorThruster", BlockEntity.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(null, mock(SmartVectorIonThrusterBlockEntity.class)));
    }

    // Vector allocation observes the live fuel, nozzle and capacity gates instead of stale map values
    @Test
    void vectorAllocationRetainsNativeEngineLimits(){
        var engine = mock(SmartVectorThrusterBlockEntity.class, withSettings().extraInterfaces(VectorThrustReceiver.class));
        engine.active = true;
        engine.hasNozzle = true;
        engine.halfAngleDeg = 30;
        when(engine.hasPropellantForThrust()).thenReturn(true);
        when(engine.getEffectiveMaxThrustForComputer()).thenReturn(100F);
        var target = new com.rieno.gadgetsandgizmos.lib.scm.ScmTarget(null, net.minecraft.core.BlockPos.ZERO, "flight_control:smart_vector_thruster", "");
        var unit = new ScmVectorAllocationRegistry.Unit(target, engine, Vec3.ZERO, new Vec3(0, 100, 0), Vec3.ZERO);
        var request = new ScmVectorAllocationRegistry.Request(List.of(unit), new Vec3(20, 60, 0), Vec3.ZERO);
        var allocator = new FlightControlScmAllocator();
        assertTrue(allocator.supports(request));
        Vec3 force = allocator.allocate(request).forces().getFirst();
        assertTrue(force.x > 10);
        assertTrue(force.length() <= 100.01);
        assertTrue(Math.atan2(Math.abs(force.x), force.y) <= Math.toRadians(30) + 1.0E-5);
        when(engine.hasPropellantForThrust()).thenReturn(false);
        assertEquals(0, allocator.allocate(request).forces().getFirst().length(), 1.0E-5);
        when(engine.hasPropellantForThrust()).thenReturn(true);
        engine.hasNozzle = false;
        assertEquals(0, allocator.allocate(request).forces().getFirst().length(), 1.0E-5);
        engine.hasNozzle = true;
        when(engine.isClusterMember()).thenReturn(true);
        assertEquals(0, allocator.allocate(request).forces().getFirst().length(), 1.0E-5);
        when(engine.getLinkedComputerPos()).thenReturn(net.minecraft.core.BlockPos.ZERO);
        assertFalse(allocator.supports(request));
        when(engine.getLinkedComputerPos()).thenReturn(null);
        assertFalse(allocator.supportsBatch(List.of(request, request)));
    }

    // Saturated translation must retain native steering authority rather than rotating the craft away
    @Test
    void sharedSteeringUsesNativeTorquePriorityUnderFullTranslation(){
        List<ScmVectorAllocationRegistry.Unit> units = new java.util.ArrayList<>();
        for(int x : new int[]{-2, 2}) for(int z : new int[]{-2, 2}){
            var engine = mock(SmartVectorThrusterBlockEntity.class, withSettings().extraInterfaces(VectorThrustReceiver.class));
            engine.active = engine.hasNozzle = true;
            engine.halfAngleDeg = 80;
            when(engine.hasPropellantForThrust()).thenReturn(true);
            when(engine.getEffectiveMaxThrustForComputer()).thenReturn(1000F);
            Vec3 arm = new Vec3(x, -0.6, z);
            Vec3 force = new Vec3(0, 1000, 0);
            var target = new com.rieno.gadgetsandgizmos.lib.scm.ScmTarget(null, net.minecraft.core.BlockPos.ZERO, "test:engine", "");
            units.add(new ScmVectorAllocationRegistry.Unit(target, engine, arm, force, arm.cross(force)));
        }
        Vec3 force = new Vec3(0, 350, -4000);
        Vec3 torque = new Vec3(0, -40, 0);
        var allocator = new FlightControlScmAllocator();
        var balanced = allocator.allocate(new ScmVectorAllocationRegistry.Request(units, force, torque));
        var steering = allocator.allocate(new ScmVectorAllocationRegistry.Request(null, null, null, units, force, torque, true));
        Vec3 balancedTorque = Vec3.ZERO;
        Vec3 steeringTorque = Vec3.ZERO;
        Vec3 steeringForce = Vec3.ZERO;
        for(int idx = 0; idx < units.size(); idx++){
            balancedTorque = balancedTorque.add(units.get(idx).momentArm().cross(balanced.forces().get(idx)));
            steeringTorque = steeringTorque.add(units.get(idx).momentArm().cross(steering.forces().get(idx)));
            steeringForce = steeringForce.add(steering.forces().get(idx));
        }
        assertTrue(steeringTorque.distanceTo(torque) < balancedTorque.distanceTo(torque) * 0.1);
        assertEquals(torque.y, steeringTorque.y, 1.0);
        assertTrue(steeringForce.z < -100);
        assertTrue(steeringForce.y > 0);
    }

    // Preserve SCM vertical and leveling requests through the native vector allocator
    @Test
    void sidewaysVectorEnginesProduceScmLiftAndLevelingThroughNativeAllocation(){
        List<ScmVectorAllocationRegistry.Unit> nativeUnits = new java.util.ArrayList<>();
        List<com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator.Unit> feedbackUnits = new java.util.ArrayList<>();
        for(int sign : new int[]{1, -1}){
            var engine = mock(SmartVectorThrusterBlockEntity.class, withSettings().extraInterfaces(VectorThrustReceiver.class));
            engine.active = engine.hasNozzle = true;
            engine.halfAngleDeg = 30;
            when(engine.hasPropellantForThrust()).thenReturn(true);
            when(engine.getEffectiveMaxThrustForComputer()).thenReturn(100F);
            Vec3 force = new Vec3(sign * 100, 0, 0);
            Vec3 arm = new Vec3(sign * 2, 0, 0);
            var target = new com.rieno.gadgetsandgizmos.lib.scm.ScmTarget(null, net.minecraft.core.BlockPos.ZERO, "test:engine", "");
            nativeUnits.add(new ScmVectorAllocationRegistry.Unit(target, engine, arm, force, arm.cross(force)));
            feedbackUnits.add(new com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator.Unit(force, arm.cross(force), false, arm, 30));
        }
        var allocator = new FlightControlScmAllocator();
        for(double lift : new double[]{20, -20}){
            Vec3 correction = com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator.normalizedAcceleration(
                    feedbackUnits, new Vec3(0, lift / 10, 0), 10);
            Vec3 requested = com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator.physicalForce(feedbackUnits, correction);
            var request = new ScmVectorAllocationRegistry.Request(nativeUnits, requested, new Vec3(0, 0, 10));
            assertTrue(allocator.supports(request));
            var res = allocator.allocate(request);
            Vec3 actualForce = Vec3.ZERO;
            Vec3 actualTorque = Vec3.ZERO;
            for(int idx = 0; idx < res.forces().size(); idx++){
                Vec3 force = res.forces().get(idx);
                actualForce = actualForce.add(force);
                actualTorque = actualTorque.add(nativeUnits.get(idx).momentArm().cross(force));
                assertTrue(force.length() <= 100.01);
            }
            assertEquals(lift, actualForce.y, 1);
            assertEquals(10, actualTorque.z, 1);
        }
    }

    // The live SCM map must retain the receiver's vector cone before generating physical demand
    @Test
    void liveScmGeometryReadsTheNativeVectorRange() throws Exception{
        var host = mock(AdvancedContraptionControllerBlockEntity.class);
        var runtime = new ShipControlModuleRuntime(host);
        var engine = mock(SmartVectorThrusterBlockEntity.class, withSettings().extraInterfaces(
                VectorThrustReceiver.class, com.rieno.gadgetsandgizmos.lib.control.IDirectControlReceiver.class));
        when(((VectorThrustReceiver) engine).controllerThrustConeDegrees()).thenReturn(30D);
        Class<?> type = Class.forName(ShipControlModuleRuntime.class.getName() + "$FlightControlVectorThrusterActuator");
        var ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object actuator = ctor.newInstance(engine, engine);
        var field = ShipControlModuleRuntime.class.getDeclaredField("controlActuators");
        field.setAccessible(true);
        ((Map<Integer, Object>) field.get(runtime)).put(0, actuator);
        var unit = new ShipControlMap.PropulsionUnit(0, java.util.UUID.randomUUID(), net.minecraft.core.BlockPos.ZERO,
                "create_flight_control:smart_vector_thruster", "flight_control_vector_thruster_v1", true,
                new Vec3(2, 0, 0), new Vec3(1, 0, 0), 0, 1, 0, 100, 1, List.of());
        var read = ShipControlModuleRuntime.class.getDeclaredMethod("precisionUnit", ShipControlMap.PropulsionUnit.class, Vec3.class);
        read.setAccessible(true);
        var geometry = (com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator.Unit) read.invoke(runtime, unit, Vec3.ZERO);
        assertEquals(30, geometry.coneDegrees());
        assertEquals(50, com.rieno.gadgetsandgizmos.lib.scm.ScmPrecisionAllocator.physicalForce(
                List.of(geometry), new Vec3(0, 1, 0)).y, 1.0E-8);
    }

    @Test
    void controllerOwnershipReplacesTheNativeLinkLineAndPreservesDiagnostics(){
        var tooltip = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        tooltip.add(net.minecraft.network.chat.Component.translatable("goggle.create_flight_control.smart_vector_thruster.active"));
        tooltip.add(net.minecraft.network.chat.Component.translatable("goggle.create_flight_control.smart_vector_thruster.not_linked"));
        var owner = new com.rieno.gadgetsandgizmos.lib.control.ControllerOwnership(
                "ship_control_module", "createthrusters.flight_control.owner.scm");
        com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlOwnership.updateTooltip(tooltip, owner);
        assertEquals(2, tooltip.size());
        assertEquals("goggle.create_flight_control.smart_vector_thruster.active",
                ((net.minecraft.network.chat.contents.TranslatableContents) tooltip.getFirst().getContents()).getKey());
        assertEquals(owner.displayKey(),
                ((net.minecraft.network.chat.contents.TranslatableContents) tooltip.getLast().getContents()).getKey());
    }

    @Test
    void nativeRateDampingDoesNotSuppressScmLeveling(){
        var component = org.mockito.Mockito.mock(ace.flight.block.AngularVelocityCouplerBlockEntity.class);
        org.mockito.Mockito.when(component.isContributing()).thenReturn(true);
        var state = ace.flight.control.FlightControllerStore.get(component);
        state.enableWx = true;
        state.enableWy = true;
        state.enableWz = true;
        assertEquals(java.util.Set.of(), com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests.rotationActions(component));
        state.targetWx = 0.2D;
        assertEquals(java.util.Set.of("ship_pitch"), com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests.rotationActions(component));
        state.enableWx = false;
        assertEquals(java.util.Set.of(), com.rieno.gadgetsandgizmos.compat.flightcontrol.FlightControlRequests.rotationActions(component));
    }

    // Automatic computer links preserve native GUI choices and fall back to the ACC's hosted coordinator
    @Test
    void computerDiscoveryRetainsNativeLinksAndRejectsForeignOwners() throws Exception{
        var level = mock(net.minecraft.world.level.Level.class);
        var host = mock(AdvancedContraptionControllerBlockEntity.class);
        when(host.getLevel()).thenReturn(level);
        when(host.getBlockPos()).thenReturn(net.minecraft.core.BlockPos.ZERO);
        var first = mock(ace.flight.block.FlightControlComputerBlockEntity.class);
        var second = mock(ace.flight.block.FlightControlComputerBlockEntity.class);
        var virtual = mock(ace.flight.block.FlightControlComputerBlockEntity.class);
        when(first.getLevel()).thenReturn(level);
        when(second.getLevel()).thenReturn(level);
        when(first.getBlockPos()).thenReturn(new net.minecraft.core.BlockPos(2, 0, 0));
        when(second.getBlockPos()).thenReturn(new net.minecraft.core.BlockPos(8, 0, 0));
        when(virtual.getBlockPos()).thenReturn(new net.minecraft.core.BlockPos(32, 0, 0));
        var controller = mock(ace.flight.block.AttitudeHoldBlockEntity.class);
        var entryType = Class.forName(FlightControlGraphRuntime.class.getName() + "$Entry");
        var constructor = entryType.getDeclaredConstructor(FlightControlNodes.Spec.class, BlockEntity.class, int.class);
        constructor.setAccessible(true);
        Object entry = constructor.newInstance(specs.stream().filter(val -> val.path().equals("attitude_hold")).findFirst().orElseThrow(), controller, 1);
        var runtime = new FlightControlGraphRuntime(host);
        var roster = FlightControlGraphRuntime.class.getDeclaredField("physicalComponents");
        roster.setAccessible(true);
        roster.set(runtime, List.of(first, second));
        var computer = FlightControlGraphRuntime.class.getDeclaredField("computer");
        computer.setAccessible(true);
        computer.set(runtime, virtual);
        var select = FlightControlGraphRuntime.class.getDeclaredMethod("coordinatedComputer", entryType, ace.flight.api.WrenchContributor.class);
        select.setAccessible(true);
        assertSame(first, select.invoke(runtime, entry, controller));
        var linkedPos = second.getBlockPos();
        when(controller.getLinkedComputerPos()).thenReturn(linkedPos);
        assertSame(second, select.invoke(runtime, entry, controller));
        var other = mock(BlockEntity.class);
        when(other.getLevel()).thenReturn(level);
        try{
            com.rieno.gadgetsandgizmos.lib.physics.BlockEntityBindings.replace(other, List.of(first, second));
            assertSame(virtual, select.invoke(runtime, entry, controller));
        }finally{ com.rieno.gadgetsandgizmos.lib.physics.BlockEntityBindings.remove(other); }
    }

    // Added 0.7.7 settings must satisfy the same native record validation as the real GUI
    @Test
    void creativeFixedWingDefaultsMatchNativeConfiguration() throws Exception{
        var spec = specs.stream().filter(val -> val.path().equals("creative_flight_control_computer")).findFirst().orElseThrow();
        Map<String, GraphValue> vals = new java.util.LinkedHashMap<>();
        spec.fields().forEach(field -> vals.put(field.port(), field.defaultValue()));
        var config = new ace.flight.navigation.CreativeFixedWingConfig(
                vals.get("fixed_wing_enabled").asBoolean(), vals.get("fixed_wing_minimum_speed").asNumber(),
                vals.get("fixed_wing_rotation_speed").asNumber(), (int) vals.get("fixed_wing_climb_degrees").asNumber(),
                (int) vals.get("fixed_wing_takeoff_height").asNumber(), (int) vals.get("fixed_wing_approach_height").asNumber(),
                (int) vals.get("fixed_wing_glide_degrees").asNumber(), (int) vals.get("fixed_wing_stabilization_distance").asNumber(),
                vals.get("fixed_wing_simulate_lift").asBoolean(), vals.get("fixed_wing_minimum_turn_radius").asNumber());
        assertEquals(ace.flight.navigation.CreativeFixedWingConfig.DEFAULT, config);
        assertInvocation(specs.stream().filter(val -> val.path().equals("navigation_terminal")).findFirst().orElseThrow().className(),
                "refreshVisualState", "net/minecraft/world/level/Level", "setBlock");
        assertInvocation("ace.flight.neoforge.client.MouseFlightControllerClient", "resolveAircraftFrame",
                "ace/flight/block/MouseFlightControllerBlockEntity", "getBlockPos");
    }

    @Test
    void hudMouseSettingsDriveNativeSensitivityTurnLimitsAndBankMode() throws Exception{
        var mouse = mock(ace.flight.block.MouseFlightControllerBlockEntity.class);
        var configure = FlightControlGraphRuntime.class.getDeclaredMethod("configureHudMouse",
                ace.flight.block.MouseFlightControllerBlockEntity.class, Map.class);
        configure.setAccessible(true);
        var spec = specs.stream().filter(val -> val.path().equals("mouse_flight_controller")).findFirst().orElseThrow();
        for(String mode : List.of("bank", "pitch_yaw")){
            clearInvocations(mouse);
            configure.invoke(null, mouse, Map.of("mouse_sensitivity", GraphValue.number(2),
                    "mouse_turn_speed_degrees", GraphValue.number(30), "mouse_turn_mode", GraphValue.string(mode)));
            var args = mockingDetails(mouse).getInvocations().stream().filter(call -> call.getMethod().getName().equals("setConfig"))
                    .findFirst().orElseThrow().getArguments();
            assertEquals(0.24, (double) args[spec.config().indexOf("mouseSensitivity")], 1.0E-9);
            assertEquals(mode.equals("bank") ? 65D : 0D, (double) args[spec.config().indexOf("maxBankDegrees")]);
            for(String limit : List.of("maxPitchVelocity", "maxYawVelocity", "maxRollVelocity")){
                assertEquals(Math.toRadians(30), (double) args[spec.config().indexOf(limit)], 1.0E-9);
            }
        }
        var hud = specs.stream().filter(val -> val.path().equals("hud_projector")).findFirst().orElseThrow();
        assertTrue(hud.fields().stream().filter(field -> field.group().equals("mouse_flight"))
                .map(FlightControlNodes.Setting::port).toList().containsAll(List.of("mouse_sensitivity", "mouse_turn_speed_degrees", "mouse_turn_mode")));
    }

    @Test
    void nativeMouseCameraPathsExposeOnlyPlayerDeltasForSensitivity() throws Exception{
        var type = bytecode("ace.flight.neoforge.client.MouseFlightControllerClient");
        for(String name : List.of("cameraAngles", "trackWorldSpaceUnlockedCamera", "trackFixedShoulderCamera")){
            var method = type.methods.stream().filter(val -> val.name.equals(name)).findFirst().orElseThrow();
            assertTrue(method.localVariables.stream().anyMatch(val -> val.name.equals("mouseYawDelta")), name);
            assertTrue(method.localVariables.stream().anyMatch(val -> val.name.equals("mousePitchDelta")), name);
        }
    }

    @Test
    void nativeHudHorizonStaysLevelForBothBankDirectionsAndEveryMount() throws Exception{
        var renderer = Class.forName("ace.flight.neoforge.client.HudProjectorBlockEntityRenderer");
        var projector = renderer.getDeclaredMethod("projectorMatrix", org.joml.Matrix4f.class, ace.flight.block.HudProjectorBlockEntity.class);
        var render = renderer.getDeclaredMethod("renderHud", org.joml.Matrix4f.class, com.mojang.blaze3d.vertex.VertexConsumer.class,
                boolean.class, double.class, double.class, double.class, double.class);
        projector.setAccessible(true);
        render.setAccessible(true);
        var hud = mock(ace.flight.block.HudProjectorBlockEntity.class);
        when(hud.getBlockState()).thenReturn(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        hud.projectionHeight = 2;
        hud.projectionDepth = 0.53;
        hud.hudScale = 1;
        for(var forward : net.minecraft.core.Direction.values()) for(var up : net.minecraft.core.Direction.values()){
            if(!com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation.isValid(forward, up)) continue;
            var frame = new com.rieno.gadgetsandgizmos.lib.scm.ScmOrientation(forward, up);
            for(double bank : new double[]{-40, 40}){
                var body = new org.joml.Quaterniond().rotateZ(Math.toRadians(-bank)).mul(frame.rotation().conjugate());
                var attitude = com.rieno.gadgetsandgizmos.lib.scm.ScmAttitude.measure(body, frame);
                var matrix = new org.joml.Matrix4f().rotate(new org.joml.Quaternionf(body.mul(frame.rotation())));
                var projection = (org.joml.Matrix4f) projector.invoke(null, matrix, hud);
                float centerY = projection.transformPosition(new org.joml.Vector3f()).y;
                var points = new java.util.ArrayList<org.joml.Vector3f>();
                var point = new java.util.concurrent.atomic.AtomicReference<org.joml.Vector3f>();
                var buffer = mock(com.mojang.blaze3d.vertex.VertexConsumer.class, RETURNS_SELF);
                doAnswer(call -> {
                    point.set(call.<org.joml.Matrix4f>getArgument(0).transformPosition(new org.joml.Vector3f(
                            call.getArgument(1), call.getArgument(2), call.getArgument(3))));
                    return buffer;
                }).when(buffer).addVertex(any(org.joml.Matrix4f.class), anyFloat(), anyFloat(), anyFloat());
                doAnswer(call -> {
                    if(Math.abs(call.<Float>getArgument(1) - 218F / 255F) < 1.0E-6F) points.add(point.get());
                    return buffer;
                }).when(buffer).setColor(anyFloat(), anyFloat(), anyFloat(), anyFloat());
                render.invoke(null, projection, buffer, true, attitude.pitch(), attitude.yaw(), attitude.roll(), 0D);
                assertFalse(points.isEmpty());
                // The first two yellow quads form the horizon; later quads are its vertical ticks
                for(var vertex : points.subList(0, 8)) assertEquals(centerY, vertex.y, 0.01, frame + " bank=" + bank);
            }
        }
    }

    private static java.util.Set<String> nativeActions(String name) throws Exception{
        java.util.Set<String> res = new java.util.LinkedHashSet<>();
        ClassNode type = bytecode(name);
        for(var method : type.methods){
            boolean action = false;
            for(var instruction : method.instructions){
                if(instruction instanceof org.objectweb.asm.tree.TypeInsnNode allocation
                        && allocation.getOpcode() == org.objectweb.asm.Opcodes.NEW && allocation.desc.equals("ace/flight/redstone/RedstoneAction")) action = true;
                else if(action && instruction instanceof org.objectweb.asm.tree.LdcInsnNode literal && literal.cst instanceof String id){
                    res.add(id);
                    action = false;
                }
            }
        }
        if(type.superName.startsWith("ace/flight/block/")) res.addAll(nativeActions(type.superName.replace('/', '.')));
        return res;
    }

    private static ClassNode bytecode(String name) throws Exception{
        try(var stream = FlightControlCompatibilityTest.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")){
            assertNotNull(stream, name);
            ClassNode res = new ClassNode();
            new ClassReader(stream).accept(res, 0);
            return res;
        }
    }

    private static void assertInvocation(String name, String method, String owner, String call) throws Exception{
        var nativeMethod = bytecode(name).methods.stream().filter(candidate -> candidate.name.equals(method)).findFirst().orElseThrow();
        for(var instruction : nativeMethod.instructions){
            if(instruction instanceof MethodInsnNode invocation && invocation.owner.equals(owner) && invocation.name.equals(call)) return;
        }
        fail("Missing native injection point " + name + ":" + method + " -> " + owner + ":" + call);
    }
}
