package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.PhysicsAssemblerAssemblyState;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapter;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataPort;
import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity;

import java.util.List;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

// Expose the Simulated Physics Assembler's assembled state to graph data nodes
public final class PhysicsAssemblerDataAdapter implements BlockEntityDataAdapter<PhysicsAssemblerBlockEntity> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String ASSEMBLED_PORT = "assembled";
    private static final List<BlockEntityDataPort> PORTS = List.of(
            BlockEntityDataPort.readWrite(ASSEMBLED_PORT, "boolean")
    );

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the adapted block entity type
    @Override
    public Class<PhysicsAssemblerBlockEntity> targetType() {
        return PhysicsAssemblerBlockEntity.class;
    }

    // Get the Physics Assembler graph data ports
    @Override
    public List<BlockEntityDataPort> ports(PhysicsAssemblerBlockEntity target) {
        return PORTS;
    }

    // Read the assembled state
    @Override
    public GraphValue read(PhysicsAssemblerBlockEntity target, String port) {
        return ASSEMBLED_PORT.equals(port) ? GraphValue.bool(isAssembled(target)) : null;
    }

    // Set the assembled state through Simulated's validated interaction path
    @Override
    public boolean write(PhysicsAssemblerBlockEntity target, String port, GraphValue value) {
        if (!ASSEMBLED_PORT.equals(port) || value == null || !"boolean".equals(value.type())
                || target.getLevel() == null || target.getLevel().isClientSide) {
            return false;
        }
        boolean assembled = isAssembled(target);
        if (assembled == value.asBoolean() || isAssemblyTransitioning(target)) {
            return false;
        }
        target.assembleOrDisassemble();
        return true;
    }

    // Check whether the assembler belongs to a Sable sublevel
    private static boolean isAssembled(PhysicsAssemblerBlockEntity target) {
        return target != null && Sable.HELPER.getContaining(target) != null;
    }

    // Check whether Simulated is still carrying out a disassembly
    private static boolean isAssemblyTransitioning(PhysicsAssemblerBlockEntity target) {
        return target instanceof PhysicsAssemblerAssemblyState state
                && state.createthrusters$isAssemblyTransitioning();
    }
}
