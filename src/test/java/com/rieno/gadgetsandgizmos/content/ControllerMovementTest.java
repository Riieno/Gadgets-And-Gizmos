package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.compat.create.CTCreateContraptionCompat;
import com.simibubi.create.api.contraption.BlockMovementChecks.CheckResult;
import com.google.gson.JsonParser;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControllerMovementTest {
    private static List<AnalogueContraptionControllerBlock> controllers;

    @BeforeAll
    static void bootstrap() {
        ControllerTestBootstrap.bootstrap();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            controllers = List.of(
                    Registry.register(registry, ResourceLocation.fromNamespaceAndPath("controller_test", "analogue"),
                            new AnalogueContraptionControllerBlock(BlockBehaviour.Properties.of())),
                    Registry.register(registry, ResourceLocation.fromNamespaceAndPath("controller_test", "advanced"),
                            new AdvancedContraptionControllerBlock(BlockBehaviour.Properties.of())));
        } finally {
            registry.freeze();
        }
    }

    private static List<AnalogueContraptionControllerBlock> controllers() {
        return controllers;
    }

    @Test
    void removingAnyNeighbourLeavesEveryControllerMountIntact() {
        Level level = mock(Level.class);
        BlockPos pos = new BlockPos(0, 64, 0);
        for (var block : controllers()) {
            for (Direction mount : Direction.values()) {
                for (boolean embedded : new boolean[]{false, true}) {
                    BlockState state = block.defaultBlockState()
                            .setValue(CTDirectionalBlock.FACING, mount)
                            .setValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB, embedded);
                    when(level.getBlockState(pos)).thenReturn(state);
                    assertTrue(state.canSurvive(level, pos));
                    for (Direction side : Direction.values()) {
                        // This is the vanilla path when a neighbouring support is replaced with air.
                        NeighborUpdater.executeShapeUpdate(level, side, Blocks.AIR.defaultBlockState(),
                                pos, pos.relative(side), Block.UPDATE_ALL, 512);
                        NeighborUpdater.executeUpdate(level, state, pos, Blocks.STONE, pos.relative(side), false);
                    }
                }
            }
        }
        verify(level, never()).destroyBlock(any(), anyBoolean(), any(), anyInt());
        verify(level, never()).setBlock(any(), any(), anyInt(), anyInt());
        verify(level, never()).removeBlockEntity(any());
        verify(level, never()).addFreshEntity(any());
    }

    @Test
    void removingLastStoneFromSubLevelDoesNotDestroyRemainingController() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        BlockPos stonePos = new BlockPos(0, 64, 0);
        BlockPos controllerPos = stonePos.above();
        for (var block : controllers()) {
            String id = block instanceof AdvancedContraptionControllerBlock
                    ? "advanced_contraption_controller" : "analogue_contraption_controller";
            double controllerMass;
            try (var reader = new InputStreamReader(requireNonNullResource(id), StandardCharsets.UTF_8)) {
                controllerMass = JsonParser.parseReader(reader).getAsJsonObject()
                        .getAsJsonObject("properties").get("sable:mass").getAsDouble();
            }
            BlockState controllerState = block.defaultBlockState();
            MassTracker mass = new MassTracker();
            mass.addBlockMass(level, Blocks.STONE.defaultBlockState(), stonePos, 1, null);
            if (controllerMass != 0) {
                mass.addBlockMass(level, controllerState, controllerPos, controllerMass, null);
            }
            ServerSubLevel ship = mock(ServerSubLevel.class);
            ServerLevelPlot plot = mock(ServerLevelPlot.class);
            when(ship.getSelfMassTracker()).thenReturn(mass);
            when(ship.getLevel()).thenReturn(level);
            when(ship.getPlot()).thenReturn(plot);
            SubLevelPhysicsSystem physics = mock(SubLevelPhysicsSystem.class, CALLS_REAL_METHODS);
            try (var properties = mockStatic(PhysicsBlockPropertyHelper.class)) {
                // Keep Sable's real mass accounting/removal branch; only its registry-backed property lookup is stubbed.
                properties.when(() -> PhysicsBlockPropertyHelper.getMass(any(), eq(stonePos),
                        eq(Blocks.STONE.defaultBlockState()))).thenReturn(1.0);
                physics.updateMassDataFromBlockChange(ship, stonePos,
                        Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), false);
            }
            assertFalse(mass.isInvalid(), id + " leaves a zero-mass sub-level after its support is removed");
            assertEquals(controllerMass, mass.getMass(), 1.0E-12);
            assertTrue(Double.isFinite(mass.getInverseMass()));
            assertTrue(mass.getCenterOfMass().isFinite());
            assertTrue(mass.getInverseInertiaTensor().isFinite());
            verify(plot, never()).destroyAllBlocks();
            verify(ship, never()).markRemoved();
        }
    }

    private static java.io.InputStream requireNonNullResource(String id) {
        return java.util.Objects.requireNonNull(ControllerMovementTest.class.getResourceAsStream(
                "/data/createthrusters/physics_block_properties/" + id + ".json"));
    }

    @Test
    void createCanCarryControllersIncludingActiveAndEmbeddedOnes() throws Exception {
        Level level = mock(Level.class);
        BlockPos pos = new BlockPos(0, 64, 0);
        for (var block : controllers()) {
            AnalogueContraptionControllerBlockEntity controller = block instanceof AdvancedContraptionControllerBlock
                    ? mock(AdvancedContraptionControllerBlockEntity.class)
                    : mock(AnalogueContraptionControllerBlockEntity.class);
            when(level.getBlockEntity(pos)).thenReturn(controller);
            // A controller with live outputs or embedded backing must not be refused.
            when(controller.isControllerRuntimeIdle()).thenReturn(false);
            when(controller.getEmbeddedSlabState()).thenReturn(Blocks.STONE_SLAB.defaultBlockState());
            for (boolean embedded : new boolean[]{false, true}) {
                BlockState state = block.defaultBlockState()
                        .setValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB, embedded);
                assertEquals(CheckResult.SUCCESS, movementCheck("isMovementNecessary", state, level, pos));
                assertEquals(CheckResult.SUCCESS, movementCheck("isMovementAllowed", state, level, pos));
                var brittle = CTCreateContraptionCompat.class.getDeclaredMethod("isBrittle", BlockState.class);
                brittle.setAccessible(true);
                assertEquals(CheckResult.FAIL, brittle.invoke(null, state));
            }
            doCallRealMethod().when(controller).canMoveWithCreateContraption();
            assertTrue(controller.canMoveWithCreateContraption());
        }
    }

    @Test
    void relocationKeepsInventoryAndAssemblyNeverProducesAnExtraDrop() {
        ServerLevel origin = mock(ServerLevel.class);
        ServerLevel destination = mock(ServerLevel.class);
        BlockPos oldPos = new BlockPos(0, 64, 0);
        BlockPos newPos = new BlockPos(1024, 64, 1024);
        for (var block : controllers()) {
            AnalogueContraptionControllerBlockEntity source = mock(AnalogueContraptionControllerBlockEntity.class);
            AnalogueContraptionControllerBlockEntity moved = mock(AnalogueContraptionControllerBlockEntity.class);
            when(origin.getBlockEntity(oldPos)).thenReturn(source);
            when(destination.getBlockEntity(newPos)).thenReturn(moved);
            BlockState state = block.defaultBlockState();
            block.beforeMove(origin, destination, state, oldPos, newPos);
            verify(source).beginAssemblyTransfer(oldPos, newPos);
            when(source.isAssemblyTransferPending()).thenReturn(true);
            LootParams.Builder loot = mock(LootParams.Builder.class);
            when(loot.getOptionalParameter(LootContextParams.BLOCK_ENTITY)).thenReturn(source);
            assertTrue(block.getDrops(state, loot).isEmpty());
            state.onRemove(origin, oldPos, Blocks.AIR.defaultBlockState(), true);
            block.afterMove(origin, destination, state, oldPos, newPos);
            verify(moved).finishAssemblyTransfer();
            verify(source, never()).onDestroyed();
            verify(source, never()).onExternalRelocation();

            // Create also removes block entities for storage in its contraption NBT.
            when(source.isAssemblyTransferPending()).thenReturn(false);
            state.onRemove(origin, oldPos, Blocks.AIR.defaultBlockState(), false);
            verify(source).onExternalRelocation();
            verify(source, never()).onDestroyed();

            // Actual destruction still performs normal inventory/output cleanup.
            when(source.isDestructiveRemovalPending()).thenReturn(true);
            state.onRemove(origin, oldPos, Blocks.AIR.defaultBlockState(), false);
            verify(source).onDestroyed();
        }
        verify(origin, never()).addFreshEntity(any());
        verify(destination, never()).addFreshEntity(any());
    }

    private static CheckResult movementCheck(String name, BlockState state, Level level, BlockPos pos)
            throws Exception {
        var method = CTCreateContraptionCompat.class.getDeclaredMethod(name, BlockState.class, Level.class, BlockPos.class);
        method.setAccessible(true);
        return (CheckResult) method.invoke(null, state, level, pos);
    }
}
