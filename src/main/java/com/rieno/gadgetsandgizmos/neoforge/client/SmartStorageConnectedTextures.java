package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankCTBehaviour;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.rieno.gadgetsandgizmos.lib.client.create.ConnectedTextureModel;
import com.rieno.gadgetsandgizmos.lib.client.create.VaultConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.IdentityHashMap;
import java.util.Map;

// Register connected textures for the local smart storage assets
public final class SmartStorageConnectedTextures {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<Block, ConnectedTextureBehaviour> BEHAVIOURS = new IdentityHashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the smart storage connected textures
    private SmartStorageConnectedTextures() {
    }

    // Register each smart storage connected texture behaviour
    public static void register() {
        if (!BEHAVIOURS.isEmpty() || CTBlocks.SMART_VAULT == null) return;
        BEHAVIOURS.put(CTBlocks.SMART_VAULT.get(), new VaultConnectedTextureBehaviour(texture("smart_vault")));
        BEHAVIOURS.put(CTBlocks.SMART_BATTERY.get(), new VaultConnectedTextureBehaviour(texture("smart_battery")));
        BEHAVIOURS.put(CTBlocks.SMART_TANK.get(), new FluidTankCTBehaviour(
                shift(AllCTTypes.RECTANGLE, "smart_tank/fluid_tank", "smart_tank/fluid_tank_connected"),
                shift(AllCTTypes.RECTANGLE, "smart_tank/fluid_tank_top", "smart_tank/fluid_tank_top_connected"),
                shift(AllCTTypes.RECTANGLE, "smart_tank/fluid_tank_inner", "smart_tank/fluid_tank_inner_connected")));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Wrap a smart storage model with its connected texture behaviour
    public static BakedModel wrap(Block block, BakedModel model) {
        ConnectedTextureBehaviour behaviour = BEHAVIOURS.get(block);
        return behaviour == null || model instanceof ConnectedTextureModel ? model : new ConnectedTextureModel(model, behaviour);
    }

    // Create a connected texture sprite shift
    private static CTSpriteShiftEntry shift(AllCTTypes type, String source, String connected) {
        return CTSpriteShifter.getCT(type,
                texture(source), texture(connected));
    }

    // Resolve a smart storage texture in the addon namespace
    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID, "block/" + path);
    }
}
