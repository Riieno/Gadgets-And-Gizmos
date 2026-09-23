package com.rieno.gadgetsandgizmos.compat.jade;

import com.rieno.gadgetsandgizmos.CreateThrusters;
import com.rieno.gadgetsandgizmos.content.SmartBatteryBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

// Display exact Smart Battery energy beyond Jade's integer capability range
final class SmartBatteryJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    static final SmartBatteryJadeProvider INSTANCE = new SmartBatteryJadeProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(CreateThrusters.MOD_ID,
            "smart_battery_energy");
    private static final String DATA_KEY = "CreateThrustersSmartBatteryEnergy";
    private static final String STORED = "Stored";
    private static final String CAPACITY = "Capacity";

    // Initialize the Smart Battery Jade provider
    private SmartBatteryJadeProvider() {
    }

    // Add exact Smart Battery FE data
    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SmartBatteryBlockEntity battery)) return;
        CompoundTag batteryData = new CompoundTag();
        batteryData.putLong(STORED, battery.getLongEnergyStored());
        batteryData.putLong(CAPACITY, battery.getLongCapacity());
        data.put(DATA_KEY, batteryData);
    }

    // Replace Jade's integer-only FE row with the exact Smart Battery value
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData().getCompound(DATA_KEY);
        if (data.isEmpty()) return;
        tooltip.remove(JadeIds.UNIVERSAL_ENERGY_STORAGE);
        tooltip.add(Component.translatable("jade.createthrusters.smart_battery.energy",
                number(data.getLong(STORED)), number(data.getLong(CAPACITY))).withStyle(ChatFormatting.AQUA));
    }

    // Get the Jade component id
    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    // Keep this display after Jade's universal capability components
    @Override
    public int getDefaultPriority() {
        return 1000;
    }

    // Format a long FE value with the unit used by the Smart Battery UI.
    private static String number(long value) {
        long normalized = Math.max(0L, value);
        if (normalized >= 1_000_000_000L) return compact(normalized, 1_000_000_000L, "GFE");
        if (normalized >= 1_000_000L) return compact(normalized, 1_000_000L, "MFE");
        if (normalized >= 1_000L) return compact(normalized, 1_000L, "KFE");
        return normalized + " FE";
    }

    // Render an FE unit without trailing decimal zeroes.
    private static String compact(long value, long divisor, String unit) {
        if (value % divisor == 0L) return value / divisor + " " + unit;
        String decimal = String.format(Locale.ROOT, "%.2f", (double) value / divisor)
                .replaceAll("\\.?0+$", "");
        return decimal + " " + unit;
    }
}
