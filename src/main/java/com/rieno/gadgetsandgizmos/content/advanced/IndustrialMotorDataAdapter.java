package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.IndustrialMotorBlockEntity;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapter;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataPort;

import java.util.List;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        MAIN
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

// Expose the Industrial Motor's configured target speed and enabled state to graph data nodes
public final class IndustrialMotorDataAdapter implements BlockEntityDataAdapter<IndustrialMotorBlockEntity> {
    private static final List<BlockEntityDataPort> PORTS = List.of(
            BlockEntityDataPort.readWrite("speed", "number"),
            BlockEntityDataPort.readWrite("power", "boolean")
    );

    @Override
    public Class<IndustrialMotorBlockEntity> targetType() {
        return IndustrialMotorBlockEntity.class;
    }

    @Override
    public List<BlockEntityDataPort> ports(IndustrialMotorBlockEntity target) {
        return PORTS;
    }

    @Override
    public GraphValue read(IndustrialMotorBlockEntity target, String port) {
        return switch (port) {
            case "speed" -> GraphValue.number(target.getTargetSpeedRPM());
            case "power" -> GraphValue.bool(target.isEnabled());
            default -> null;
        };
    }

    @Override
    public boolean write(IndustrialMotorBlockEntity target, String port, GraphValue value) {
        return switch (port) {
            case "speed" -> target.setTargetSpeedRPM(value.asNumber());
            case "power" -> setPower(target, value.asBoolean());
            default -> false;
        };
    }

    private static boolean setPower(IndustrialMotorBlockEntity target, boolean enabled) {
        if (target.isEnabled() == enabled) {
            return false;
        }
        target.setEnabled(enabled);
        return true;
    }
}
