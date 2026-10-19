package com.rieno.gadgetsandgizmos.registry;

import com.rieno.gadgetsandgizmos.content.advanced.CreateFlapDisplayDataAdapter;
import com.rieno.gadgetsandgizmos.content.advanced.GenericBlockEntityDataAdapter;
import com.rieno.gadgetsandgizmos.content.advanced.IndustrialMotorDataAdapter;
import com.rieno.gadgetsandgizmos.content.advanced.PhysicsAssemblerDataAdapter;
import com.rieno.gadgetsandgizmos.content.advanced.VanillaSignDataAdapter;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataProvider;
import com.rieno.gadgetsandgizmos.lib.probe.ScrollValueDataAdapter;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.Map;
import com.rieno.gadgetsandgizmos.lib.probe.ReadOnlyBooleanDataAdapter;
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity;
import com.rieno.gadgetsandgizmos.compat.scm.SynaxisDataAdapter;

// Register the addon block entity graph data adapters
public final class CTBlockEntityDataAdapters {
    private static boolean registered;

    private CTBlockEntityDataAdapters() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlockEntityDataAdapterRegistry.register(100, new VanillaSignDataAdapter());
        BlockEntityDataAdapterRegistry.register(100, new CreateFlapDisplayDataAdapter());
        BlockEntityDataAdapterRegistry.register(100, new IndustrialMotorDataAdapter());
        BlockEntityDataAdapterRegistry.register(100, new PhysicsAssemblerDataAdapter());
        BlockEntityDataAdapterRegistry.register(100, new ReadOnlyBooleanDataAdapter<>(
                DockingConnectorBlockEntity.class, "connected",
                target -> !target.isRemoved() && target.isLocked() && target.hasOtherConnector()));
        BlockEntityDataAdapterRegistry.register(100, new SynaxisDataAdapter());
        BlockEntityDataAdapterRegistry.register(0, new ScrollValueDataAdapter<>(SmartBlockEntity.class,
                target -> target instanceof BlockEntityDataProvider ? Map.of() : ScrollValueDataAdapter.fieldBehaviours(target)));
        BlockEntityDataAdapterRegistry.register(-1000, new GenericBlockEntityDataAdapter());
    }


}
