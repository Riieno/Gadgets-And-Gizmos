package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipDoorApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipDoorControl;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.AllBlocks;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.Map;

// Exercise ship-relative entrance discovery and native door operation in loaded Sable plots
@GameTestHolder("createthrusters_ship_doors")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "createthrusters", bus = EventBusSubscriber.Bus.MOD)
public final class ShipDoorGameTests{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register a two-part door that does not inherit vanilla DoorBlock behavior
    @SubscribeEvent
    public static void registerTestDoor(RegisterEvent event){
        event.register(Registries.BLOCK, registry -> {
            registry.register(ResourceLocation.parse("createthrusters:test_full_size_door"), new FullSizeDoorBlock());
            registry.register(ResourceLocation.parse("createthrusters:test_full_height_door"), new FullHeightDoorBlock());
            registry.register(ResourceLocation.parse("createthrusters:test_full_size_trap_door"), new FullSizeDoorBlock());
        });
    }

    // Full-height modded doors need not expose vanilla's paired-half property
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void namedFullHeightDoorWithoutHalfIsControlled(GameTestHelper helper){
        var body = cabin(helper, Blocks.OAK_DOOR.defaultBlockState(), false);
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            var pos = new BlockPos(0, 1, -3);
            var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createthrusters:test_full_height_door")).defaultBlockState();
            view.setBlock(pos, state, 2);
            view.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
            var doors = ShipDoorApi.discover(body, List.of(body), new Vec3(0, 0, -1), new Vec3(0, 1, 0));
            helper.assertTrue(doors.size() == 4, "A named full-height door without a half property was omitted");
            var control = new ShipDoorControl();
            control.replace(doors);
            control.apply(helper.getLevel(), ShipDoorApi.Side.NORTH.mask());
            helper.assertTrue(view.getBlockState(pos).getValue(DoorBlock.OPEN), "The named door did not open");
            control.close(helper.getLevel());
            helper.assertTrue(!view.getBlockState(pos).getValue(DoorBlock.OPEN), "The named door did not close");
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Trapdoor names remain excluded even when a mod supplies ordinary door properties
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void untaggedModdedTrapDoorNamesRemainExcluded(GameTestHelper helper){
        var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createthrusters:test_full_size_trap_door")).defaultBlockState();
        var body = cabin(helper, state, false);
        try{
            helper.assertTrue(ShipDoorApi.discover(body, List.of(body), new Vec3(0, 0, -1), new Vec3(0, 1, 0)).isEmpty(),
                    "An untagged modded trapdoor was assigned as a full-size door");
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Update both halves of standard modded doors without requiring a DoorBlock subclass
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void genericFullSizeDoorsOpenBothHalves(GameTestHelper helper){
        var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("createthrusters:test_full_size_door")).defaultBlockState();
        var body = cabin(helper, state, false);
        try{
            verifySelection(helper, body);
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Explicit selection bypasses hull rays and normalizes either selected door half
    @GameTest(template = "empty", timeoutTicks = 80, batch = "selectedDoors")
    public static void selectedHullDoorsIgnoreAutomaticDiscovery(GameTestHelper helper){
        var body = cabin(helper, AllBlocks.TRAIN_DOOR.getDefaultState(), false);
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            var center = body.getPlot().getCenterBlock();
            SableSubLevelLifecycleApi.transfer(helper.getLevel(), body, () -> {
                view.setBlock(new BlockPos(0, 1, -4), Blocks.STONE.defaultBlockState(), 3);
                view.setBlock(new BlockPos(0, 2, -4), Blocks.STONE.defaultBlockState(), 3);
                view.setBlock(new BlockPos(0, 4, 0), Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);
            });
            var north = center.offset(0, 1, -3);
            var east = center.offset(3, 1, 0);
            var selection = List.of(new ShipDoorApi.Reference(body.getUniqueId(), north),
                    new ShipDoorApi.Reference(body.getUniqueId(), north.above()),
                    new ShipDoorApi.Reference(body.getUniqueId(), east.above()),
                    new ShipDoorApi.Reference(body.getUniqueId(), center.offset(0, 4, 0)),
                    new ShipDoorApi.Reference(UUID.randomUUID(), north));
            var doors = ShipDoorApi.selected(body, List.of(body), selection, center.getCenter(), new Vec3(0, 0, -1), new Vec3(0, 1, 0));
            helper.assertTrue(doors.size() == 2, "Explicit door selection did not retain exactly the two selected full-size doors");
            helper.assertTrue(doors.stream().anyMatch(door -> door.pos().equals(north) && door.side() == ShipDoorApi.Side.NORTH),
                    "The selected north door was hidden by an automatic hull test");
            var control = new ShipDoorControl();
            control.replace(doors);
            control.apply(helper.getLevel(), ShipDoorApi.Side.NORTH.mask());
            helper.assertTrue(view.getBlockState(new BlockPos(0, 1, -3)).getValue(DoorBlock.OPEN), "The selected north door did not open");
            helper.assertTrue(!view.getBlockState(new BlockPos(3, 1, 0)).getValue(DoorBlock.OPEN), "The unrequested east door opened");
            control.close(helper.getLevel());
            helper.assertTrue(!view.getBlockState(new BlockPos(0, 1, -3)).getValue(DoorBlock.OPEN), "The selected north door did not close");
            var rotated = ShipDoorApi.selected(body, List.of(body), selection, center.getCenter(), new Vec3(1, 0, 0), new Vec3(0, 1, 0));
            helper.assertTrue(rotated.stream().anyMatch(door -> door.pos().equals(east) && door.side() == ShipDoorApi.Side.NORTH),
                    "Changing SCM forward did not reclassify the selected doors");
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Save Hull Doors through server validation without exposing them as flight actuators
    @GameTest(template = "empty", timeoutTicks = 80, batch = "selectedDoorProfile")
    public static void hullDoorGroupSurvivesServerSave(GameTestHelper helper){
        var body = cabin(helper, AllBlocks.TRAIN_DOOR.getDefaultState(), false);
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            SableSubLevelLifecycleApi.transfer(helper.getLevel(), body, () -> {
                view.setBlock(new BlockPos(0, 1, 0), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
                view.setBlock(new BlockPos(0, 2, 0), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
                view.setBlock(new BlockPos(1, 4, 1), Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);
            });
            var center = body.getPlot().getCenterBlock();
            var ctrl = (AdvancedContraptionControllerBlockEntity) body.getLevel().getBlockEntity(center.offset(0, 2, 0));
            var door = new ScmConfigurationProfile.UnitReference(body.getUniqueId(), center.offset(0, 2, -3), "");
            var trap = new ScmConfigurationProfile.UnitReference(body.getUniqueId(), center.offset(1, 4, 1), "");
            var profile = ScmConfigurationProfile.empty();
            profile.replace(ctrl.getScmConfigurationMapId(), List.of(new ScmConfigurationProfile.Group("hull_doors", "Hull Doors", Set.of(door, trap))),
                    Map.of(ScmConfigurationProfile.HULL_DOORS_ACTION, "hull_doors"), Set.of());
            String prev = ctrl.getScmConfigurationStatus();
            helper.assertTrue(ctrl.replaceScmConfigurationProfile(profile), "The server rejected the Hull Doors group");
            var saved = ScmConfigurationProfile.fromTag(ctrl.getScmConfigurationProfile().toTag());
            helper.assertTrue(saved.hullDoors().equals(Set.of(door)), "Server save discarded the selected door or retained a trapdoor");
            helper.assertTrue(saved.controlGroups().isEmpty() && !saved.hasActionBindings(), "Hull doors became propulsion bindings");
            helper.assertTrue(prev.equals(ctrl.getScmConfigurationStatus()), "Selecting hull doors started actuator calibration");
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Open only the requested cabin entrance and close both halves before departure
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void selectedCabinDoorsOpenAndClose(GameTestHelper helper){
        var body = cabin(helper, Blocks.IRON_DOOR.defaultBlockState(), false);
        try{
            verifySelection(helper, body);
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Retain Create's visibility and paired-half updates through its native door operation
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void createSlidingDoorsOpenAndClose(GameTestHelper helper){
        var body = cabin(helper, AllBlocks.ANDESITE_DOOR.getDefaultState(), false);
        try{
            verifySelection(helper, body);
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Keep an entrance discoverable when its threshold contains carpet
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void cabinDoorsWithCarpetRemainExterior(GameTestHelper helper){
        var body = cabin(helper, Blocks.OAK_DOOR.defaultBlockState(), true);
        try{
            verifySelection(helper, body);
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Keep a raised slab threshold from hiding the exterior doorway
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void slabThresholdsRemainExterior(GameTestHelper helper){
        var body = cabin(helper, AllBlocks.ANDESITE_DOOR.getDefaultState(), false);
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            for(Direction dir : Direction.Plane.HORIZONTAL){
                var pos = new BlockPos(dir.getStepX() * 3, 1, dir.getStepZ() * 3);
                view.setBlock(pos.relative(dir), Blocks.STONE_SLAB.defaultBlockState(), 3);
                view.setBlock(pos.relative(dir.getOpposite()), Blocks.STONE_SLAB.defaultBlockState(), 3);
            }
            verifySelection(helper, body);
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Find train doors above full-height stairs without treating the steps as cabin walls
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void trainDoorsWithStairsRemainExterior(GameTestHelper helper){
        var body = cabin(helper, AllBlocks.TRAIN_DOOR.getDefaultState(), false);
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            for(Direction dir : Direction.Plane.HORIZONTAL){
                var pos = new BlockPos(dir.getStepX() * 4, 1, dir.getStepZ() * 4);
                view.setBlock(pos, Blocks.OAK_STAIRS.defaultBlockState(), 2);
            }
            verifySelection(helper, body);
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Ignore narrow panels beside the doorway's clear opening
    @GameTest(template = "empty", timeoutTicks = 80, batch = "doorPanels")
    public static void sidePanelsDoNotHideTrainDoors(GameTestHelper helper){
        var body = cabin(helper, AllBlocks.TRAIN_DOOR.getDefaultState(), false);
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            SableSubLevelLifecycleApi.transfer(helper.getLevel(), body, () -> {
                for(Direction dir : Direction.Plane.HORIZONTAL){
                    var pos = new BlockPos(dir.getStepX() * 4, 1, dir.getStepZ() * 4);
                    var panel = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
                            .setValue(TrapDoorBlock.FACING, dir.getClockWise());
                    view.setBlock(pos, panel, 2);
                    view.setBlock(pos.above(), panel, 2);
                }
            });
            verifySelection(helper, body);
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Operate full-size doors while leaving roof trapdoors and deck gates untouched
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void trapdoorsAndGatesAreExcluded(GameTestHelper helper){
        var body = cabin(helper, AllBlocks.TRAIN_DOOR.getDefaultState(), false);
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            view.setBlock(new BlockPos(1, 4, 1), AllBlocks.TRAIN_TRAPDOOR.getDefaultState(), 2);
            view.setBlock(new BlockPos(1, 1, -4), Blocks.OAK_FENCE_GATE.defaultBlockState(), 2);
            verifySelection(helper, body);
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Classify a furnished train hull against an east-facing SCM with inward-facing outer doors
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void furnishedTrainHullUsesScmForward(GameTestHelper helper) throws Exception{
        var body = newBody(helper);
        var plot = body.getPlot();
        try(var stream = ShipDoorGameTests.class.getResourceAsStream(
                "/data/createthrusters_ship_doors/test_hulls/train_cabin.nbt")){
            if(stream == null) throw new IllegalStateException("Missing train cabin test hull");
            var hull = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
            var view = plot.getEmbeddedLevelAccessor();
            var registry = helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK);
            SableSubLevelLifecycleApi.transfer(helper.getLevel(), body, () -> {
                for(var val : hull.getList("Blocks", Tag.TAG_COMPOUND)){
                    var block = (net.minecraft.nbt.CompoundTag) val;
                    var pos = block.getIntArray("Pos");
                    var state = block.getCompound("State");
                    if(!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(state.getString("Name"))))
                        state = block.getCompound("Fallback");
                    view.setBlock(new BlockPos(pos[0], pos[1], pos[2]), NbtUtils.readBlockState(registry, state), 2);
                }
            });
            var doors = ShipDoorApi.discover(body, List.of(body), new Vec3(1, 0, 0), new Vec3(0, 1, 0));
            helper.assertTrue(doors.size() == 4, "Expected four train hull doors, found " + doors.size());
            for(var door : doors){
                int z = door.pos().getZ() - plot.getCenterBlock().getZ();
                helper.assertTrue(door.side() == (z == 1 ? ShipDoorApi.Side.WEST : ShipDoorApi.Side.EAST),
                        "A train door ignored the SCM's forward vector");
            }
            var control = new ShipDoorControl();
            control.replace(doors);
            control.apply(helper.getLevel(), ShipDoorApi.Side.EAST.mask());
            for(var door : doors) helper.assertTrue(body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN)
                    == (door.side() == ShipDoorApi.Side.EAST), "The wrong side of the train hull opened");
            control.close(helper.getLevel());
            for(var door : doors) helper.assertTrue(!body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN),
                    "A train hull door stayed open before departure");
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Keep doors between sealed rooms out of the dock's exterior selection
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void interiorDoorsRemainExcluded(GameTestHelper helper){
        var body = cabin(helper, Blocks.OAK_DOOR.defaultBlockState(), false);
        try{
            var view = body.getPlot().getEmbeddedLevelAccessor();
            for(int x = -2; x <= 2; x++) for(int y = 1; y <= 3; y++)
                view.setBlock(new BlockPos(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
            var state = Blocks.OAK_DOOR.defaultBlockState();
            view.setBlock(new BlockPos(0, 1, 0), state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
            view.setBlock(new BlockPos(0, 2, 0), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            var doors = ShipDoorApi.discover(body, List.of(body), new Vec3(0, 0, -1), new Vec3(0, 1, 0));
            var internal = body.getPlot().getCenterBlock().offset(0, 1, 0);
            helper.assertTrue(doors.stream().noneMatch(door -> door.pos().equals(internal)),
                    "An enclosed internal door was included in exterior controls");
            helper.succeed();
        }finally{ SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body)); }
    }

    // Apply the live Ship Dock selection through a locked connector and close before native release
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lockedShipDockControlsDoorsBeforeRelease(GameTestHelper helper){
        verifyDockControl(helper, true);
    }

    // Use the same native locked-pair state as Schedule and Dock At
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeLockedDockControlsDoorsBeforePhysicsRestore(GameTestHelper helper){
        verifyDockControl(helper, false);
    }

    // A copied or restored connector must resolve its real station despite an obsolete owner binding
    @GameTest(template = "empty", timeoutTicks = 100, batch = "dockDoorPath")
    public static void lockedDockWithStaleOwnerStillControlsDoors(GameTestHelper helper){
        verifyDockControl(helper, true, true);
    }

    // Apply live dock selections and release through the native connector lifecycle
    private static void verifyDockControl(GameTestHelper helper, boolean physical){
        verifyDockControl(helper, physical, false);
    }

    // Drive saved dock settings through the actual ACC-owned SCM runtime
    private static void verifyDockControl(GameTestHelper helper, boolean physical, boolean staleOwner){
        var body = cabin(helper, AllBlocks.TRAIN_DOOR.getDefaultState(), false);
        var view = body.getPlot().getEmbeddedLevelAccessor();
        view.setBlock(new BlockPos(0, 1, 0), CTBlocks.SHIP_CONTROL_MODULE.get().defaultBlockState(), 3);
        view.setBlock(new BlockPos(0, 2, 0), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3);
        var ctrlPos = body.getPlot().getCenterBlock().offset(0, 2, 0);
        var ctrl = (AdvancedContraptionControllerBlockEntity) body.getLevel().getBlockEntity(ctrlPos);
        var runtime = controllerRuntime(ctrl);
        var connectorState = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:docking_connector")).defaultBlockState();
        view.setBlock(new BlockPos(0, 1, 5), connectorState, 3);
        var ship = (DockingConnectorBlockEntity) body.getLevel().getBlockEntity(body.getPlot().getCenterBlock().offset(0, 1, 5));
        helper.setBlock(new BlockPos(1, 1, 1), CTBlocks.SHIP_DOCK.get());
        helper.setBlock(new BlockPos(1, 1, 4), connectorState);
        var dock = (ShipDockBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
        var peer = (DockingConnectorBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 4));
        dock.addLinkedDockingConnector(new ShipDockBlockEntity.ConnectorReference(null, peer.getBlockPos()));
        dock.configure("Door test", false, false, false, false, ShipDoorApi.Side.NORTH.mask(),
                List.of(), List.of(), List.of());
        var savedDock = dock.saveWithoutMetadata(helper.getLevel().registryAccess());
        dock.loadWithComponents(savedDock, helper.getLevel().registryAccess());
        var map = new ShipControlMap(UUID.randomUUID(), helper.getLevel().dimension().location().toString(),
                body.getUniqueId(), ctrlPos, Vec3.ZERO, List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        ShipControlMapStore.save(helper.getLevel(), map);
        runtime.setMapId(map.id());
        var selectedDoors = ShipDoorApi.discover(body, List.of(body), ctrl.getScmOrientation().forwardVector(),
                ctrl.getScmOrientation().upVector());
        var profile = ScmConfigurationProfile.empty();
        profile.replace(map.id(), List.of(new ScmConfigurationProfile.Group("hull_doors", "Hull Doors", selectedDoors.stream()
                .map(door -> new ScmConfigurationProfile.UnitReference(door.subLevelId(), door.pos(), ""))
                .collect(java.util.stream.Collectors.toSet()))), Map.of(ScmConfigurationProfile.HULL_DOORS_ACTION, "hull_doors"), Set.of());
        ctrl.setScmConfigurationProfile(ScmConfigurationProfile.fromTag(profile.toTag()));
        ship.powered = true;
        peer.powered = true;
        ship.extension.startWithValue(1);
        peer.extension.startWithValue(1);
        if(physical) ship.pairTo(peer);
        else{
            ship.setDock(peer, true, null, null, null);
            peer.setDock(ship, true, null, null, null);
        }
        if(staleOwner) DockingConnectorAutomation.bindToShipDock(peer, UUID.randomUUID(), "Old dock", 0);
        Runnable verify = () -> {
            try{
                ctrl.tick();
                var doors = ShipDoorApi.discover(body, List.of(body), ctrl.getScmOrientation().forwardVector(),
                        ctrl.getScmOrientation().upVector());
                helper.assertTrue(doors.size() == 4, "The docked cabin lost its entrances");
                for(var door : doors) helper.assertTrue(!body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN),
                        "A saved disabled dock opened a selected hull door");
                dock.configure("Door test", false, false, false, true, ShipDoorApi.Side.NORTH.mask(),
                        List.of(), List.of(), List.of());
                dock.loadWithComponents(dock.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
                if(staleOwner) DockingConnectorAutomation.bindToShipDock(peer, UUID.randomUUID(), "Old dock", 0);
                ctrl.tick();
                if(!physical) helper.assertTrue(runtime.isRestoringAfterLoad(), "Expected door control while flight control retains its load hold");
                for(var door : doors) helper.assertTrue(body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN)
                        == (door.side() == ShipDoorApi.Side.NORTH), "The locked Ship Dock did not apply its selected side; "
                                + doorPathState(runtime, ctrl, dock, ship, peer));
                dock.configure("Door test", false, false, false, true, ShipDoorApi.Side.EAST.mask(),
                        List.of(), List.of(), List.of());
                ctrl.tick();
                for(var door : doors) helper.assertTrue(body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN)
                        == (door.side() == ShipDoorApi.Side.EAST), "The locked Ship Dock did not refresh its selected side");
                dock.configure("Door test", false, false, false, false, ShipDoorApi.Side.EAST.mask(),
                        List.of(), List.of(), List.of());
                ctrl.tick();
                for(var door : doors) helper.assertTrue(!body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN),
                        "Disabling the locked Ship Dock did not close its selected doors");
                dock.configure("Door test", false, false, false, true, ShipDoorApi.Side.EAST.mask(),
                        List.of(), List.of(), List.of());
                ctrl.tick();
                helper.assertTrue(doors.stream().filter(door -> door.side() == ShipDoorApi.Side.EAST)
                        .allMatch(door -> body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN)),
                        "The connected Ship Dock did not reopen its selected doors");
                ship.unDock();
                for(var door : doors) helper.assertTrue(!body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN),
                        "Native undocking left a door open");
            }finally{
                runtime.close();
                SableSubLevelLifecycleApi.remove(helper.getLevel(), List.of(body));
            }
        };
        if(physical) helper.startSequence().thenWaitUntil(() -> helper.assertTrue(SimulatedDockingApi.hasPhysicalConnection(ship),
                "Waiting for the native docking constraint")).thenExecute(verify).thenSucceed();
        else{
            helper.assertTrue(DockingConnectorAutomation.isLockedPair(ship, peer)
                    && !SimulatedDockingApi.hasPhysicalConnection(ship), "Expected a native locked pair before physics restoration");
            verify.run();
            helper.succeed();
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    ========================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Use the controller-owned runtime that normal block entity ticks execute
    private static ShipControlModuleRuntime controllerRuntime(AdvancedContraptionControllerBlockEntity ctrl){
        try{
            var field = AdvancedContraptionControllerBlockEntity.class.getDeclaredField("shipControlRuntime");
            field.setAccessible(true);
            return (ShipControlModuleRuntime)field.get(ctrl);
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
    }

    // Report the production request and mapping state when an integration assertion fails
    private static String doorPathState(ShipControlModuleRuntime runtime, AdvancedContraptionControllerBlockEntity ctrl,
                                        ShipDockBlockEntity dock, DockingConnectorBlockEntity ship, DockingConnectorBlockEntity peer){
        StringBuilder res = new StringBuilder("enabled=" + dock.isDoorControlEnabled() + ", requested=" + dock.requestedDoorMask()
                + ", locked=" + DockingConnectorAutomation.isLockedPair(ship, peer) + ", selected=" + ctrl.getScmConfigurationProfile().hullDoors().size());
        try{
            for(String key : List.of("phase", "status", "mapId", "resumePoseHoldAfterLoad", "dockDoorMask", "doorConnectors", "dockDoors")){
                var field = ShipControlModuleRuntime.class.getDeclaredField(key);
                field.setAccessible(true);
                Object val = field.get(runtime);
                res.append(", ").append(key).append('=').append(val instanceof ShipDoorControl control ? control.doors() : val);
            }
        }catch(ReflectiveOperationException err){ throw new IllegalStateException(err); }
        return res.toString();
    }

    // Build a closed cabin with entrances facing each ship-local cardinal direction
    static ServerSubLevel cabin(GameTestHelper helper, BlockState door, boolean carpet){
        var body = newBody(helper);
        var plot = body.getPlot();
        var view = plot.getEmbeddedLevelAccessor();
        SableSubLevelLifecycleApi.transfer(helper.getLevel(), body, () -> {
            for(int x = -3; x <= 3; x++) for(int z = -3; z <= 3; z++) for(int y = 0; y <= 4; y++){
                if(Math.abs(x) == 3 || Math.abs(z) == 3 || y == 0 || y == 4)
                    view.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
            for(Direction dir : Direction.Plane.HORIZONTAL){
                var pos = new BlockPos(dir.getStepX() * 3, 1, dir.getStepZ() * 3);
                var state = door.setValue(DoorBlock.FACING, dir);
                view.setBlock(pos, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
                view.setBlock(pos.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
                if(carpet){
                    view.setBlock(pos.relative(dir.getOpposite()), Blocks.WHITE_CARPET.defaultBlockState(), 3);
                    view.setBlock(pos.relative(dir), Blocks.WHITE_CARPET.defaultBlockState(), 3);
                }
            }
        });
        return body;
    }

    // Anchor each test body in its loaded test area instead of leaving plot coordinates in world space
    static ServerSubLevel newBody(GameTestHelper helper){
        return newBody(helper, helper.absolutePos(BlockPos.ZERO).getCenter().add(0, 16, 0));
    }

    // Supply the initial world pose to native physics before allocating the body
    static ServerSubLevel newBody(GameTestHelper helper, Vec3 target){
        var container = SubLevelContainer.getContainer(helper.getLevel());
        var pose = new Pose3d();
        pose.position().set(target.x, target.y, target.z);
        var body = (ServerSubLevel) container.allocateNewSubLevel(pose);
        var plot = body.getPlot();
        var origin = plot.getCenterBlock().getCenter();
        body.logicalPose().rotationPoint().set(origin.x, origin.y, origin.z);
        body.updateLastPose();
        plot.newEmptyChunk(plot.getCenterChunk());
        return body;
    }

    // Change cardinal selections without opening other entrances or leaving either half open
    private static void verifySelection(GameTestHelper helper, ServerSubLevel body){
        helper.assertTrue(!body.isRemoved(), "The fixture body was removed during construction");
        var doors = ShipDoorApi.discover(body, List.of(body), new Vec3(0, 0, -1), new Vec3(0, 1, 0));
        helper.assertTrue(doors.size() == 4, "Expected four exterior doors, found " + doors.size()
                + "; bounds=" + body.getPlot().getBoundingBox() + "; chunks=" + body.getPlot().getLoadedChunks().size());
        for(var door : doors){
            var offset = door.pos().subtract(body.getPlot().getCenterBlock());
            var expected = offset.getX() > 0 ? ShipDoorApi.Side.EAST : offset.getX() < 0 ? ShipDoorApi.Side.WEST
                    : offset.getZ() > 0 ? ShipDoorApi.Side.SOUTH : ShipDoorApi.Side.NORTH;
            helper.assertTrue(door.side() == expected, "A doorway mapped to the wrong ship-local cardinal direction");
        }
        var control = new ShipDoorControl();
        control.replace(doors);
        for(var side : ShipDoorApi.Side.values()){
            control.apply(helper.getLevel(), side.mask());
            for(var door : doors){
                boolean expected = door.side() == side;
                helper.assertTrue(body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN) == expected,
                        "Wrong lower door state for " + door.side());
                helper.assertTrue(body.getLevel().getBlockState(door.pos().above()).getValue(DoorBlock.OPEN) == expected,
                        "Wrong upper door state for " + door.side());
            }
        }
        control.close(helper.getLevel());
        for(var door : doors) helper.assertTrue(!body.getLevel().getBlockState(door.pos()).getValue(DoorBlock.OPEN),
                "Door remained open before departure");
    }

    // Supply a two-block-high model without paired-half state
    private static final class FullHeightDoorBlock extends Block{
        private FullHeightDoorBlock(){
            super(BlockBehaviour.Properties.of());
            registerDefaultState(defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder){
            builder.add(DoorBlock.FACING, DoorBlock.OPEN);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx){
            return Block.box(0, 0, 0, 16, 32, 3);
        }
    }

    // Supply standard door properties without vanilla neighbor synchronization
    private static final class FullSizeDoorBlock extends Block{
        // Initialize the modded door's closed lower half
        private FullSizeDoorBlock(){
            super(BlockBehaviour.Properties.of());
            registerDefaultState(defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.OPEN, false));
        }

        // Expose the common full-size door state contract
        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder){
            builder.add(DoorBlock.FACING, DoorBlock.HALF, DoorBlock.OPEN);
        }
    }
}
