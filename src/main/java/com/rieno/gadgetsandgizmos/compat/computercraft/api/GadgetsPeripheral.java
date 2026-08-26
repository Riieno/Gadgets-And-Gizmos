package com.rieno.gadgetsandgizmos.compat.computercraft.api;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;

// Share target identity and liveness for ordinary block-entity peripherals
public abstract class GadgetsPeripheral<T extends BlockEntity>
        implements DocumentedPeripheral {
    protected final T blockEntity;
    private final String peripheralType;

    protected GadgetsPeripheral(T blockEntity, String peripheralType) {
        this.blockEntity = Objects.requireNonNull(blockEntity, "blockEntity");
        if (peripheralType == null || peripheralType.isBlank()) {
            throw new IllegalArgumentException("A peripheral type is required");
        }
        this.peripheralType = peripheralType;
    }

    @Override
    public final String getType() {
        return peripheralType;
    }

    @Override
    public final boolean equals(IPeripheral other) {
        return other != null && other.getClass() == getClass()
                && ((GadgetsPeripheral<?>) other).blockEntity == blockEntity
                && Objects.equals(
                        ((GadgetsPeripheral<?>) other).peripheralType,
                        peripheralType);
    }

    protected final T requireTarget() throws LuaException {
        if (blockEntity.isRemoved() || blockEntity.getLevel() == null) {
            throw new LuaException("peripheral target is no longer available");
        }
        return blockEntity;
    }
}
