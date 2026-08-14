package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Capture the Embedded Copycat Block state needed after its source unloads
final class EmbeddedCopycatBlockSnapshot {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String COPYCATS_BLOCK_ENTITY_INTERFACE =
            "com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity";
    private static final String FRAMED_BLOCK_ENTITY_CLASS =
            "xfacthd.framedblocks.api.block.blockentity.FramedBlockEntity";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the embedded copycat block snapshot
    private EmbeddedCopycatBlockSnapshot() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is supported backing
    static boolean isSupportedBacking(BlockEntity blockEntity) {
        return blockEntity instanceof CopycatBlockEntity
                || isOptionalType(blockEntity, COPYCATS_BLOCK_ENTITY_INTERFACE)
                || isOptionalType(blockEntity, FRAMED_BLOCK_ENTITY_CLASS);
    }

    // Capture the embedded copycat block snapshot
    static @Nullable Snapshot capture(Level level, BlockEntity blockEntity) {
        if (!isSupportedBacking(blockEntity)) {
            return null;
        }
        CompoundTag blockEntityData = blockEntity.saveWithFullMetadata(level.registryAccess());
        return new Snapshot(blockEntityData, readMaterial(blockEntity));
    }

    // Clear the consumed items
    static boolean clearConsumedItems(BlockEntity blockEntity) {
        if (blockEntity instanceof CopycatBlockEntity copycat) {
            copycat.setConsumedItem(ItemStack.EMPTY);
            return true;
        }

        try {
            Method storageGetter = blockEntity.getClass().getMethod("getMaterialItemStorage");
            Object storage = storageGetter.invoke(blockEntity);
            if (storage == null) {
                return false;
            }
            Method propertiesGetter = storage.getClass().getMethod("getAllProperties");
            Object properties = propertiesGetter.invoke(storage);
            if (properties instanceof Collection<?> propertyNames) {
                Method setter = blockEntity.getClass().getMethod("setConsumedItem", String.class, ItemStack.class);
                for (Object propertyName : propertyNames) {
                    if (propertyName instanceof String name) {
                        setter.invoke(blockEntity, name, ItemStack.EMPTY);
                    }
                }
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method setter = blockEntity.getClass().getMethod("setConsumedItem", ItemStack.class);
            setter.invoke(blockEntity, ItemStack.EMPTY);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    // Reload the embedded copycat block snapshot
    static void reload(BlockEntity blockEntity, CompoundTag blockEntityData) {
        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        blockEntity.loadWithComponents(blockEntityData.copy(), level.registryAccess());
        sync(blockEntity);
    }

    // Apply the material
    static void applyMaterial(BlockEntity blockEntity, BlockState material) {
        if (blockEntity instanceof CopycatBlockEntity copycat) {
            copycat.setMaterial(material);
        } else {
            try {
                Method setter = blockEntity.getClass().getMethod("setMaterial", BlockState.class);
                setter.invoke(blockEntity, material);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        sync(blockEntity);
    }

    // Create the preview
    static @Nullable BlockEntity createPreview(Level level, BlockPos pos, BlockState state,
                                                CompoundTag blockEntityData) {
        BlockEntity preview = BlockEntity.loadStatic(pos, state, blockEntityData.copy(), level.registryAccess());
        if (preview == null) {
            return null;
        }
        preview.setLevel(level);
        preview.clearRemoved();
        return preview;
    }

    // Get the consumed items
    static List<ItemStack> consumedItems(@Nullable BlockEntity blockEntity) {
        if (blockEntity == null) {
            return List.of();
        }
        if (blockEntity instanceof CopycatBlockEntity copycat) {
            return copyNonEmpty(List.of(copycat.getConsumedItem()));
        }

        if (isOptionalType(blockEntity, FRAMED_BLOCK_ENTITY_CLASS)) {
            List<ItemStack> drops = new ArrayList<>();
            try {
                Method additionalDrops = blockEntity.getClass()
                        .getMethod("addAdditionalDrops", List.class, boolean.class);
                additionalDrops.invoke(blockEntity, drops, true);
                return copyNonEmpty(drops);
            } catch (ReflectiveOperationException ignored) {
                return List.of();
            }
        }

        try {
            Method storageGetter = blockEntity.getClass().getMethod("getMaterialItemStorage");
            Object storage = storageGetter.invoke(blockEntity);
            if (storage == null) {
                return List.of();
            }
            Method itemsGetter = storage.getClass().getMethod("getAllConsumedItems");
            Object items = itemsGetter.invoke(storage);
            if (items instanceof Collection<?> collection) {
                List<ItemStack> stacks = new ArrayList<>();
                for (Object item : collection) {
                    if (item instanceof ItemStack stack && !stack.isEmpty()) {
                        stacks.add(stack.copy());
                    }
                }
                return stacks;
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method getter = blockEntity.getClass().getMethod("getConsumedItem");
            Object item = getter.invoke(blockEntity);
            if (item instanceof ItemStack stack && !stack.isEmpty()) {
                return List.of(stack.copy());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return List.of();
    }

    // Read the material
    private static @Nullable BlockState readMaterial(BlockEntity blockEntity) {
        if (blockEntity instanceof CopycatBlockEntity copycat && copycat.hasCustomMaterial()) {
            return copycat.getMaterial();
        }
        try {
            Method customMaterialGetter = blockEntity.getClass().getMethod("hasCustomMaterial");
            if (!Boolean.TRUE.equals(customMaterialGetter.invoke(blockEntity))) {
                return null;
            }
            Method materialGetter = blockEntity.getClass().getMethod("getMaterial");
            Object material = materialGetter.invoke(blockEntity);
            return material instanceof BlockState state ? state : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    // Check if this is an optional type
    private static boolean isOptionalType(BlockEntity blockEntity, String className) {
        try {
            Class<?> optionalType = Class.forName(
                    className,
                    false,
                    blockEntity.getClass().getClassLoader());
            return optionalType.isInstance(blockEntity);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    // Sync the embedded copycat block snapshot
    private static void sync(BlockEntity blockEntity) {
        blockEntity.setChanged();
        try {
            Method modelDataUpdate = blockEntity.getClass().getMethod("requestModelDataUpdate");
            modelDataUpdate.invoke(blockEntity);
        } catch (ReflectiveOperationException ignored) {
        }

        Level level = blockEntity.getLevel();
        if (level == null) {
            return;
        }
        if (!level.isClientSide && blockEntity instanceof SyncedBlockEntity syncedBlockEntity) {
            syncedBlockEntity.notifyUpdate();
        } else {
            BlockState state = blockEntity.getBlockState();
            level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, 3);
        }

        if (!level.isClientSide
                && Sable.HELPER.getContaining(blockEntity) instanceof ServerSubLevel subLevel) {
            subLevel.playerSink().sendPacket(new ClientboundBlockUpdatePacket(
                    blockEntity.getBlockPos(),
                    blockEntity.getBlockState()));
            Packet<?> updatePacket = blockEntity.getUpdatePacket();
            subLevel.playerSink().sendPacket(updatePacket == null
                    ? ClientboundBlockEntityDataPacket.create(blockEntity)
                    : updatePacket);
        }
    }

    // Copy the non-empty item stacks
    private static List<ItemStack> copyNonEmpty(List<ItemStack> items) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack item : items) {
            if (!item.isEmpty()) {
                copies.add(item.copy());
            }
        }
        return copies;
    }

    // Store the snapshot
    record Snapshot(CompoundTag blockEntityData, @Nullable BlockState material) {
        // Initialize the snapshot
        Snapshot {
            blockEntityData = blockEntityData.copy();
        }
    }
}
