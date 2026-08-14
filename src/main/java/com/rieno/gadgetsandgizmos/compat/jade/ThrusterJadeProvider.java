package com.rieno.gadgetsandgizmos.compat.jade;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

// Add solid-fuel burn time which Jade cannot read from the thruster's normal capabilities
final class ThrusterJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    static final ThrusterJadeProvider INSTANCE = new ThrusterJadeProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
            "thruster_resources");
    private static final String FUEL = "Fuel";
    private static final String SOLID_FUEL = "SolidFuel";
    private static final String INFINITE_FUEL = "InfiniteFuel";
    private static final String DATA_KEY = "CreateThrustersResources";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the thruster jade provider
    private ThrusterJadeProvider() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the server data
    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ThrusterBlockEntity thruster)) {
            return;
        }

        // Jade already handles the normal energy, fluid and item storage displays
        // Only add the solid fuel burn time which Jade cannot read itself
        if (thruster.isFocusedMode()) {
            return;
        }

        boolean solidFuel = thruster.hasInfiniteSolidFuel() || thruster.getSolidFuelTicks() > 0;
        if (!solidFuel) {
            return;
        }

        CompoundTag resourceData = new CompoundTag();
        resourceData.putBoolean(SOLID_FUEL, true);
        resourceData.putBoolean(INFINITE_FUEL, thruster.hasInfiniteSolidFuel());
        resourceData.putInt(FUEL, thruster.getSolidFuelTicks());
        data.put(DATA_KEY, resourceData);
    }

    // Add the tooltip
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData().getCompound(DATA_KEY);
        if (data.isEmpty()) {
            return;
        }

        if (!data.getBoolean(SOLID_FUEL)) {
            return;
        }

        MutableComponent burnTime = data.getBoolean(INFINITE_FUEL)
                ? Component.translatable("jade.createthrusters.thruster.solid_burn_infinite")
                : Component.translatable("jade.createthrusters.thruster.solid_burn", number(data.getInt(FUEL)));
        tooltip.add(burnTime.withStyle(ChatFormatting.GOLD));
    }

    // Get the uid
    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    // Get the default priority
    @Override
    public int getDefaultPriority() {
        return 1000;
    }

    // Read the numeric value
    private static String number(int val) {
        return String.format(Locale.ROOT, "%,d", Math.max(0, val));
    }
}
