package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.authlib.GameProfile;
import com.rieno.gadgetsandgizmos.lib.compat.simulated.SimulatedDockingApi;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerNetworkAccess;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerStorageNetworks;
import com.rieno.gadgetsandgizmos.lib.inventory.ResourceRouting;
import com.rieno.gadgetsandgizmos.lib.physics.SableSubLevelLifecycleApi;
import com.rieno.gadgetsandgizmos.lib.scm.ScmSubLevelRelationRegistry;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

// Exercise storage boundaries against loaded worlds, Sable plots and physical parent-child relations
@GameTestHolder("createthrusters_container_networks")
@PrefixGameTestTemplate(false)
public final class ContainerNetworkBoundaryGameTests{
/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Even an assembly adapter cannot join world storage to a SubLevel
    @GameTest(template = "empty", timeoutTicks = 40, setupTicks = 2, batch = "worldStorage")
    public static void worldAndSubLevelsStaySeparate(GameTestHelper helper){
        try(var fixture = fixture(helper)){
            var all = fixture.all();
            ContainerStorageNetworks.track(fixture.world().getFirst(), ignored -> all,
                    ignored -> List.of(fixture.parent().getUniqueId(), fixture.child().getUniqueId()));
            ContainerStorageNetworks.track(fixture.parentStorage().getFirst(), ignored -> all);
            ContainerStorageNetworks.invalidate();
            verify(helper, fixture.world().getFirst(), fixture.world());
            verify(helper, fixture.parentStorage().getFirst(), fixture.parentStorage());
            verify(helper, fixture.childStorage().getFirst(), List.of(fixture.childStorage().getFirst()));
            helper.succeed();
        }
    }

    // Linked controllers on unrelated ships must not create a physical assembly or bridge overlapping networks
    @GameTest(template = "empty", timeoutTicks = 40, setupTicks = 2, batch = "separateShipStorage")
    public static void controllerLinksDoNotJoinSeparateShips(GameTestHelper helper){
        try(var fixture = fixture(helper)){
            ContainerStorageNetworks.track(fixture.controller(), ignored -> fixture.all(), SmartContainerManifest::storageSubLevels);
            ContainerStorageNetworks.track(fixture.childStorage().getFirst(), ignored -> fixture.all());
            ContainerStorageNetworks.invalidate();
            verify(helper, fixture.parentStorage().getFirst(), fixture.parentStorage());
            verify(helper, fixture.childStorage().getFirst(), fixture.childStorage());
            verify(helper, fixture.world().getFirst(), List.of(fixture.world().getFirst()));
            helper.succeed();
        }
    }

    // Both sides of a loaded physical relation share resources and split again after detachment
    @GameTest(template = "empty", timeoutTicks = 40, setupTicks = 2, batch = "parentChildStorage")
    public static void parentChildNetworksSplitAfterDetachment(GameTestHelper helper){
        try(var fixture = fixture(helper)){
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("createthrusters", "test_storage_relation");
            ScmSubLevelRelationRegistry.register(id, 100, be -> be == fixture.controller(), ctx -> List.of(
                    new ScmSubLevelRelationRegistry.Relation(fixture.parent().getUniqueId(), fixture.child().getUniqueId(), "test")));
            try{
                ContainerStorageNetworks.track(fixture.controller(), ignored -> fixture.all(), SmartContainerManifest::storageSubLevels);
                ContainerStorageNetworks.invalidate();
                List<BlockEntity> joined = new ArrayList<>(fixture.parentStorage());
                joined.addAll(fixture.childStorage());
                verify(helper, fixture.parentStorage().getFirst(), joined);
                verify(helper, fixture.childStorage().getFirst(), joined);
                var retained = new ContainerNetworkAccess(fixture.parentStorage().getFirst());
                int slots = retained.getSlots();
                ScmSubLevelRelationRegistry.unregister(id);
                ContainerStorageNetworks.invalidate();
                verify(helper, fixture.parentStorage().getFirst(), fixture.parentStorage());
                verify(helper, fixture.childStorage().getFirst(), List.of(fixture.childStorage().getFirst()));
                helper.assertTrue(retained.getSlots() < slots, "Cached automation retained detached child storage");
            }finally{ ScmSubLevelRelationRegistry.unregister(id); }
            helper.succeed();
        }
    }

    // Item, fluid and FE capabilities must obey the same boundary as manifest membership
    @GameTest(template = "empty", timeoutTicks = 40, setupTicks = 2, batch = "resourceStorage")
    public static void resourceCapabilitiesCannotReachOtherShips(GameTestHelper helper){
        try(var fixture = fixture(helper)){
            ContainerStorageNetworks.track(fixture.controller(), ignored -> fixture.all(), SmartContainerManifest::storageSubLevels);
            ContainerStorageNetworks.invalidate();
            verify(helper, fixture.parentStorage().getFirst(), fixture.parentStorage());
            var remoteItems = (BarrelBlockEntity)fixture.childStorage().getFirst();
            remoteItems.setItem(0, new ItemStack(Items.DIAMOND, 8));
            var remoteTank = (SmartTankBlockEntity)fixture.childStorage().get(2);
            var remoteBattery = (SmartBatteryBlockEntity)fixture.childStorage().get(3);
            ResourceRouting.insertFluid(remoteTank.localResources(), new FluidStack(Fluids.WATER, 1000), false);
            ResourceRouting.insertEnergy(remoteBattery.localResources(), 10000, false);
            var access = new ContainerNetworkAccess(fixture.parentStorage().getFirst());
            for(int slot = 0; slot < access.getSlots(); slot++){
                helper.assertTrue(access.getStackInSlot(slot).isEmpty(), "Remote inventory leaked through a local slot");
            }
            helper.assertTrue(access.drain(1000, FluidAction.EXECUTE).isEmpty(),
                    "Network drained another ship's fluid");
            helper.assertTrue(access.extractEnergy(10000, false) == 0, "Network drained another ship's FE");
            helper.assertTrue(access.fill(new FluidStack(Fluids.LAVA, 500), FluidAction.EXECUTE) == 500, "Local tank rejected network input");
            helper.assertTrue(access.receiveEnergy(2000, false) == 2000, "Local battery rejected network input");
            helper.assertTrue(remoteItems.getItem(0).getCount() == 8
                    && remoteTank.getTankInventory().getFluidAmount() == 1000
                    && ResourceRouting.extractEnergy(remoteBattery.localResources(), 10000, true) == 10000,
                    "Local transfers changed remote resources");
            helper.succeed();
        }
    }

    // A physical dock remains a bidirectional transfer bridge while the two storage networks stay separate
    @GameTest(template = "empty", timeoutTicks = 100, setupTicks = 2, batch = "dockedStorage")
    public static void physicalDockStillTransfersItemsFluidsAndEnergyBothWays(GameTestHelper helper){
        var fixture = fixture(helper);
        helper.setBlock(new BlockPos(1, 1, 1), CTBlocks.SHIP_DOCK.get());
        helper.setBlock(new BlockPos(3, 1, 1), CTBlocks.SMART_TANK.get());
        helper.setBlock(new BlockPos(5, 1, 1), CTBlocks.SMART_BATTERY.get());
        var dock = (ShipDockBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 1));
        var state = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("simulated:docking_connector")).defaultBlockState();
        helper.setBlock(new BlockPos(1, 1, 4), state);
        SableSubLevelLifecycleApi.transfer(helper.getLevel(), fixture.parent(), () -> fixture.parent().getPlot()
                .getEmbeddedLevelAccessor().setBlock(new BlockPos(0, 1, 5), state, 3));
        var ship = (DockingConnectorBlockEntity)helper.getLevel().getBlockEntity(
                fixture.parent().getPlot().getCenterBlock().offset(0, 1, 5));
        var peer = (DockingConnectorBlockEntity)helper.getBlockEntity(new BlockPos(1, 1, 4));
        dock.addLinkedDockingConnector(new ShipDockBlockEntity.ConnectorReference(null, peer.getBlockPos()));
        List<BlockEntity> station = new ArrayList<>(fixture.world());
        station.add(dock);
        station.add(helper.getBlockEntity(new BlockPos(3, 1, 1)));
        station.add(helper.getBlockEntity(new BlockPos(5, 1, 1)));
        List<BlockEntity> links = new ArrayList<>(station);
        links.addAll(fixture.parentStorage());
        ContainerStorageNetworks.track(dock, ignored -> links);
        ContainerStorageNetworks.invalidate();
        ship.powered = true;
        peer.powered = true;
        ship.extension.startWithValue(1);
        peer.extension.startWithValue(1);
        ship.pairTo(peer);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(SimulatedDockingApi.hasPhysicalConnection(ship)
                && DockingConnectorAutomation.isLockedPair(ship, peer), "Waiting for the physical docking pair"))
                .thenExecute(() -> {
                    try{
                        verify(helper, dock, station);
                        helper.assertTrue(!SmartContainerManifest.storageSubLevels(fixture.controller()).contains(fixture.child().getUniqueId()),
                                "An unrelated ship entered the docked assembly");
                        var items = new ItemStack(Items.IRON_INGOT, 16);
                        var fluid = new FluidStack(Fluids.WATER, 1000);
                        helper.assertTrue(ScmDockingAccess.insertItem(ship, peer, items, true).isEmpty()
                                && ScmDockingAccess.peekItem(ship, peer).isEmpty(), "Dock item simulation changed storage");
                        helper.assertTrue(ScmDockingAccess.insertItem(ship, peer, items, false).isEmpty(), "Ship-to-dock item transfer failed");
                        helper.assertTrue(ScmDockingAccess.insertFluid(ship, peer, fluid, true) == 1000
                                && ScmDockingAccess.peekFluid(ship, peer).isEmpty(), "Dock fluid simulation changed storage");
                        helper.assertTrue(ScmDockingAccess.insertFluid(ship, peer, fluid, false) == 1000, "Ship-to-dock fluid transfer failed");
                        int before = ScmDockingAccess.storedEnergy(ship, peer);
                        helper.assertTrue(ScmDockingAccess.insertEnergy(ship, peer, 2000, true) == 2000
                                && ScmDockingAccess.storedEnergy(ship, peer) == before, "Dock FE simulation changed storage");
                        helper.assertTrue(ScmDockingAccess.insertEnergy(ship, peer, 2000, false) == 2000, "Ship-to-dock FE transfer failed");
                        helper.assertTrue(ScmDockingAccess.extractItem(ship, peer, items, 16, false).getCount() == 16,
                                "Dock-to-ship item transfer failed");
                        helper.assertTrue(ScmDockingAccess.extractFluid(ship, peer, fluid, 1000, false).getAmount() == 1000,
                                "Dock-to-ship fluid transfer failed");
                        helper.assertTrue(ScmDockingAccess.extractEnergy(ship, peer, 2000, false) == 2000,
                                "Dock-to-ship FE transfer failed");
                        ship.unDock();
                        helper.assertTrue(ScmDockingAccess.insertItem(ship, peer, items, false).getCount() == 16
                                && ScmDockingAccess.insertFluid(ship, peer, fluid, false) == 0
                                && ScmDockingAccess.insertEnergy(ship, peer, 2000, false) == 0,
                                "Undocked connectors retained transfer access");
                    }finally{ fixture.close(); }
                }).thenSucceed();
    }

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        HELPERS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    // Check the automation and player-facing views against the same expected physical members
    private static void verify(GameTestHelper helper, BlockEntity entry, List<BlockEntity> expected){
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "StorageTest"), ClientInformation.createDefault());
        var actual = ContainerStorageNetworks.connected(entry);
        helper.assertTrue(new HashSet<>(actual).equals(new HashSet<>(expected)),
                "Automation members at " + entry.getBlockPos() + ": expected " + expected.stream().map(BlockEntity::getBlockPos).toList()
                        + ", found " + actual.stream().map(BlockEntity::getBlockPos).toList() + "; removed=" + entry.isRemoved()
                        + "; loaded=" + com.rieno.gadgetsandgizmos.lib.worker.WorkerContainerAccess.isLoaded(entry.getLevel(), entry.getBlockPos()));
        helper.assertTrue(new HashSet<>(ContainerStorageNetworks.connected(entry, player)).equals(new HashSet<>(expected)),
                "Manifest network disagreed with the storage boundary");
    }

    // Create identical local storage on each side so only ownership determines network membership
    private static Fixture fixture(GameTestHelper helper){
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.BARREL);
        helper.setBlock(new BlockPos(3, 2, 1), Blocks.BARREL);
        var parent = ShipDoorGameTests.newBody(helper);
        List<BlockEntity> parentStorage = storage(parent);
        var child = ShipDoorGameTests.newBody(helper);
        List<BlockEntity> childStorage = storage(child);
        SableSubLevelLifecycleApi.transfer(helper.getLevel(), parent, () -> parent.getPlot().getEmbeddedLevelAccessor()
                .setBlock(new BlockPos(0, 2, 0), CTBlocks.ADVANCED_CONTRAPTION_CONTROLLER.get().defaultBlockState(), 3));
        var controller = (AdvancedContraptionControllerBlockEntity)helper.getLevel().getBlockEntity(
                parent.getPlot().getCenterBlock().offset(0, 2, 0));
        return new Fixture(List.of(helper.getBlockEntity(new BlockPos(1, 2, 1)), helper.getBlockEntity(new BlockPos(3, 2, 1))),
                parent, child, parentStorage, childStorage, controller);
    }

    // Keep native construction alive while placing blocks into a new Sable plot
    private static List<BlockEntity> storage(ServerSubLevel body){
        SableSubLevelLifecycleApi.transfer(body.getLevel(), body, () -> {
            var view = body.getPlot().getEmbeddedLevelAccessor();
            view.setBlock(new BlockPos(0, 0, 0), Blocks.BARREL.defaultBlockState(), 3);
            view.setBlock(new BlockPos(2, 0, 0), Blocks.BARREL.defaultBlockState(), 3);
            view.setBlock(new BlockPos(4, 0, 0), CTBlocks.SMART_TANK.get().defaultBlockState(), 3);
            view.setBlock(new BlockPos(6, 0, 0), CTBlocks.SMART_BATTERY.get().defaultBlockState(), 3);
        });
        var origin = body.getPlot().getCenterBlock();
        return List.of(body.getLevel().getBlockEntity(origin), body.getLevel().getBlockEntity(origin.offset(2, 0, 0)),
                body.getLevel().getBlockEntity(origin.offset(4, 0, 0)), body.getLevel().getBlockEntity(origin.offset(6, 0, 0)));
    }

    private record Fixture(List<BlockEntity> world, ServerSubLevel parent, ServerSubLevel child,
                           List<BlockEntity> parentStorage, List<BlockEntity> childStorage,
                           AdvancedContraptionControllerBlockEntity controller) implements AutoCloseable{
        // Include every scope in the authored links to exercise filtering before component merging
        List<BlockEntity> all(){
            List<BlockEntity> all = new ArrayList<>(world);
            all.addAll(parentStorage);
            all.addAll(childStorage);
            return List.copyOf(all);
        }

        @Override public void close(){
            SableSubLevelLifecycleApi.remove(parent.getLevel(), List.of(parent, child));
            ContainerStorageNetworks.invalidate();
        }
    }
}
