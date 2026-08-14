package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

// Register addon data components
public final class CTDataComponents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final DeferredRegister.DataComponents REGISTRAR =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateThrusters.MOD_ID);

    public static final DataComponentType<Boolean> OXIDIZED = register("oxidized",
            builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    public static final DataComponentType<String> PLAYER_MANNEQUIN_VARIANT = register("player_mannequin_variant",
            builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static final DataComponentType<Boolean> ENTITY_LAUNCHER_INSTANT = register("entity_launcher_instant",
            builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT data components
    private CTDataComponents() {
    }

    // Register the CT data components
    public static void register(IEventBus modEventBus) {
        REGISTRAR.register(modEventBus);
    }

    // Register the CT data components
    private static <T> DataComponentType<T> register(String id,
            java.util.function.UnaryOperator<DataComponentType.Builder<T>> factory) {
        DataComponentType<T> type = factory.apply(DataComponentType.builder()).build();
        REGISTRAR.register(id, () -> type);
        return type;
    }
}
