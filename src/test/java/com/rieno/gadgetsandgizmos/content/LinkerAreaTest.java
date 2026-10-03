package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.lib.worker.WorkerArea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LinkerAreaTest{
    @BeforeAll static void bootstrap(){ ControllerTestBootstrap.bootstrap(); }

    @Test void clientEditRetainsSavedGeometryAndCanRenameAnArea(){
        UUID id = UUID.randomUUID();
        UUID subLevelId = UUID.randomUUID();
        WorkerArea bounds = new WorkerArea(new BlockPos(1, 2, 3), new BlockPos(16, 6, 18));
        var saved = new ContraptionNetworkLinkerData.LinkedArea(id, subLevelId, bounds, "Old");
        CompoundTag existing = ContraptionNetworkLinkerData.writeClientEditRoot(null, List.of(), List.of(saved),
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetMode.AREA);
        var renamed = new ContraptionNetworkLinkerData.LinkedArea(id, subLevelId, bounds, "Press line");
        CompoundTag incoming = ContraptionNetworkLinkerData.writeClientEditRoot(null, List.of(), List.of(renamed),
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetMode.AREA);
        CompoundTag merged = ContraptionNetworkLinkerData.sanitizeAndMergeClientEdit(incoming, existing);
        assertNotNull(merged);
        assertEquals(List.of(renamed), ContraptionNetworkLinkerData.readAreas(merged));
        assertEquals("area", merged.getString("TargetMode"));
    }

    @Test void clientCannotMoveAnAreaOrSubmitOversizedBounds(){
        UUID id = UUID.randomUUID();
        var saved = new ContraptionNetworkLinkerData.LinkedArea(id, null,
                new WorkerArea(BlockPos.ZERO, new BlockPos(1, 1, 1)), "Saved");
        CompoundTag existing = ContraptionNetworkLinkerData.writeClientEditRoot(null, List.of(), List.of(saved),
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetMode.AREA);
        var moved = new ContraptionNetworkLinkerData.LinkedArea(id, null,
                new WorkerArea(BlockPos.ZERO, new BlockPos(2, 1, 1)), "Moved");
        CompoundTag incoming = ContraptionNetworkLinkerData.writeClientEditRoot(null, List.of(), List.of(moved),
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetMode.AREA);
        assertNull(ContraptionNetworkLinkerData.sanitizeAndMergeClientEdit(incoming, existing));

        CompoundTag oversized = new CompoundTag();
        oversized.putUUID("Id", UUID.randomUUID());
        oversized.putLong("Min", BlockPos.ZERO.asLong());
        oversized.putLong("Max", new BlockPos(256, 0, 0).asLong());
        ListTag list = new ListTag();
        list.add(oversized);
        CompoundTag root = new CompoundTag();
        root.put("Areas", list);
        assertTrue(ContraptionNetworkLinkerData.readAreas(root).isEmpty());
    }

    @Test void areaRecipeAssignmentSurvivesClientEdits(){
        UUID id = UUID.randomUUID();
        WorkerArea bounds = new WorkerArea(BlockPos.ZERO, new BlockPos(4, 4, 4));
        var saved = new ContraptionNetworkLinkerData.LinkedArea(id, null, bounds,
                "Engine line", "createdieselgenerators:engine_assembly");
        CompoundTag existing = ContraptionNetworkLinkerData.writeClientEditRoot(null, List.of(), List.of(saved),
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetMode.AREA);
        var edited = new ContraptionNetworkLinkerData.LinkedArea(id, null, bounds,
                "Engine line 2", saved.recipeId());
        CompoundTag incoming = ContraptionNetworkLinkerData.writeClientEditRoot(null, List.of(), List.of(edited),
                ContraptionNetworkLinkerData.LinkMode.SCM, ContraptionNetworkLinkerData.TargetMode.AREA);
        CompoundTag merged = ContraptionNetworkLinkerData.sanitizeAndMergeClientEdit(incoming, existing);
        assertNotNull(merged);
        assertEquals(List.of(edited), ContraptionNetworkLinkerData.readAreas(merged));
    }

    @Test void machinePortsAndNoEntryBoundsSurviveSavingWithoutClientPortInjection(){
        UUID id = UUID.randomUUID();
        WorkerArea bounds = new WorkerArea(BlockPos.ZERO, new BlockPos(4, 4, 4));
        var input = new ContraptionNetworkLinkerData.MachinePort(new BlockPos(0, 1, 0),
                Direction.WEST, ContraptionNetworkLinkerData.MachinePortRole.INPUT);
        var output = new ContraptionNetworkLinkerData.MachinePort(new BlockPos(4, 1, 0),
                Direction.EAST, ContraptionNetworkLinkerData.MachinePortRole.OUTPUT);
        var machine = new ContraptionNetworkLinkerData.LinkedArea(id, null, bounds, "Assembly", "",
                ContraptionNetworkLinkerData.AreaKind.MACHINE, List.of(input, output),
                List.of("create:precision_mechanism", "createdieselgenerators:engine_assembly"));
        var noEntry = new ContraptionNetworkLinkerData.LinkedArea(UUID.randomUUID(), null, bounds,
                "Keep clear", "", ContraptionNetworkLinkerData.AreaKind.NO_ENTRY, List.of(), List.of());
        CompoundTag existing = ContraptionNetworkLinkerData.writeClientEditRoot(null, List.of(),
                List.of(machine, noEntry), ContraptionNetworkLinkerData.LinkMode.SCM,
                ContraptionNetworkLinkerData.TargetMode.MACHINE_INPUT);
        assertEquals(List.of(machine, noEntry), ContraptionNetworkLinkerData.readAreas(existing));
        var forged = new ContraptionNetworkLinkerData.LinkedArea(id, null, bounds, "Assembly", "",
                ContraptionNetworkLinkerData.AreaKind.MACHINE, List.of(), List.of("create:precision_mechanism"));
        CompoundTag incoming = ContraptionNetworkLinkerData.writeClientEditRoot(null, List.of(),
                List.of(forged, noEntry), ContraptionNetworkLinkerData.LinkMode.SCM,
                ContraptionNetworkLinkerData.TargetMode.MACHINE_OUTPUT);
        CompoundTag merged = ContraptionNetworkLinkerData.sanitizeAndMergeClientEdit(incoming, existing);
        assertNotNull(merged);
        assertEquals(List.of(input, output), ContraptionNetworkLinkerData.readAreas(merged).getFirst().ports());
        assertEquals(List.of("create:precision_mechanism"),
                ContraptionNetworkLinkerData.readAreas(merged).getFirst().recipeIds());
        assertEquals(ContraptionNetworkLinkerData.AreaKind.NO_ENTRY,
                ContraptionNetworkLinkerData.readAreas(merged).getLast().kind());
    }

    @Test void machineFaceCyclesFromInputThroughOutputToClear(){
        BlockPos pos = new BlockPos(3, 4, 5);
        Direction face = Direction.NORTH;
        var input = ContraptionNetworkLinkerData.nextMachinePort(List.of(), pos, face,
                ContraptionNetworkLinkerData.MachinePortRole.INPUT);
        assertEquals(ContraptionNetworkLinkerData.MachinePortCycleState.INPUT, input.state());
        assertEquals(ContraptionNetworkLinkerData.MachinePortRole.INPUT, input.ports().getFirst().role());

        var output = ContraptionNetworkLinkerData.nextMachinePort(input.ports(), pos, face,
                ContraptionNetworkLinkerData.MachinePortRole.INPUT);
        assertEquals(ContraptionNetworkLinkerData.MachinePortCycleState.OUTPUT, output.state());
        assertEquals(ContraptionNetworkLinkerData.MachinePortRole.OUTPUT, output.ports().getFirst().role());

        var cleared = ContraptionNetworkLinkerData.nextMachinePort(output.ports(), pos, face,
                ContraptionNetworkLinkerData.MachinePortRole.INPUT);
        assertEquals(ContraptionNetworkLinkerData.MachinePortCycleState.CLEARED, cleared.state());
        assertTrue(cleared.ports().isEmpty());
    }
}
