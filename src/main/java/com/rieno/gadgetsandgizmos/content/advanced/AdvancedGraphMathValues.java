package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.control.math.Quaternion;
import com.rieno.gadgetsandgizmos.lib.control.math.Vector3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

// Normalize graph values before vector, quaternion and number operations
final class AdvancedGraphMathValues {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph math values
    private AdvancedGraphMathValues() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the vector
    static Vector3 vector(AdvancedGraphDocument.Value val) {
        CompoundTag data = val == null ? new CompoundTag() : val.payload();
        return new Vector3(component(data, "x"), component(data, "y"), component(data, "z"));
    }

    // Get the quaternion
    static Quaternion quaternion(AdvancedGraphDocument.Value val) {
        CompoundTag data = val == null ? new CompoundTag() : val.payload();
        return new Quaternion(component(data, "x"), component(data, "y"),
                component(data, "z"), component(data, "w"));
    }

    // Get the vector
    static AdvancedGraphDocument.Value vector(Vector3 val) {
        CompoundTag data = new CompoundTag();
        data.putDouble("x", val.x());
        data.putDouble("y", val.y());
        data.putDouble("z", val.z());
        return AdvancedGraphDocument.Value.map(data);
    }

    // Get the quaternion
    static AdvancedGraphDocument.Value quaternion(Quaternion val) {
        Quaternion normalized = val.normalized();
        CompoundTag data = new CompoundTag();
        data.putDouble("x", normalized.x());
        data.putDouble("y", normalized.y());
        data.putDouble("z", normalized.z());
        data.putDouble("w", normalized.w());
        return AdvancedGraphDocument.Value.map(data);
    }

    // Get the component
    private static double component(CompoundTag data, String key) {
        String resolved = data.contains(key) ? key : key.toUpperCase(java.util.Locale.ROOT);
        if (!data.contains(resolved)) {
            return 0.0D;
        }
        Tag tag = data.get(resolved);
        if (tag instanceof CompoundTag wrapped && wrapped.contains("Type")) {
            return AdvancedGraphDocument.Value.fromTag(wrapped).asNumber();
        }
        return data.getDouble(resolved);
    }
}
