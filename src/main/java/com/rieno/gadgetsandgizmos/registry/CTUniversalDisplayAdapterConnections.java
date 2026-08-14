package com.rieno.gadgetsandgizmos.registry;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.GyroscopeLinkBlockEntity;
import com.rieno.gadgetsandgizmos.content.UniversalDisplayAdapterBlockEntity;
import com.rieno.gadgetsandgizmos.lib.display.AccDisplayConnectionRegistry;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

// Register the blocks which can connect to the universal display adapter
public final class CTUniversalDisplayAdapterConnections {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the universal display adapter connections
    private CTUniversalDisplayAdapterConnections() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Register the universal display adapter connections
    public static void register() {
        AccDisplayConnectionRegistry.registerSourceIfAbsent(id("terminal"),
                UniversalDisplayAdapterBlockEntity::supportsTerminalSource);
        AccDisplayConnectionRegistry.registerSourceIfAbsent(id("computed_computer"),
                CTUniversalDisplayAdapterConnections::isComputedComputer);
        AccDisplayConnectionRegistry.registerSourceIfAbsent(id("display_link"),
                blockEntity -> blockEntity instanceof DisplayLinkBlockEntity);
        AccDisplayConnectionRegistry.registerSourceIfAbsent(id("advanced_controller"),
                blockEntity -> blockEntity instanceof AdvancedContraptionControllerBlockEntity);
        AccDisplayConnectionRegistry.registerTargetIfAbsent(id("acc_display"),
                blockEntity -> blockEntity instanceof AccDisplayBlockEntity);
        AccDisplayConnectionRegistry.registerTargetIfAbsent(id("display_link"),
                blockEntity -> blockEntity instanceof DisplayLinkBlockEntity);
        AccDisplayConnectionRegistry.registerTargetIfAbsent(id("advanced_controller"),
                blockEntity -> blockEntity instanceof AdvancedContraptionControllerBlockEntity);
        AccDisplayConnectionRegistry.registerTargetIfAbsent(id("gyroscope_link"),
                blockEntity -> blockEntity instanceof GyroscopeLinkBlockEntity);
        AccDisplayConnectionRegistry.registerTargetIfAbsent(id("universal_display_adapter"),
                blockEntity -> blockEntity instanceof UniversalDisplayAdapterBlockEntity);
    }

    // Check if this is a Computed computer
    private static boolean isComputedComputer(BlockEntity blockEntity) {
        ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return id != null && "computed".equals(id.getNamespace());
    }

    // Get the connection ID
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
                "universal_display_adapter/" + path);
    }
}
