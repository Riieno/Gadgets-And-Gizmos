package com.rieno.gadgetsandgizmos.registry;

import com.rieno.gadgetsandgizmos.content.advanced.CreateFlapDisplayDataAdapter;
import com.rieno.gadgetsandgizmos.content.advanced.GenericBlockEntityDataAdapter;
import com.rieno.gadgetsandgizmos.content.advanced.IndustrialMotorDataAdapter;
import com.rieno.gadgetsandgizmos.content.advanced.VanillaSignDataAdapter;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry;

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
        BlockEntityDataAdapterRegistry.register(-1000, new GenericBlockEntityDataAdapter());
    }
}
