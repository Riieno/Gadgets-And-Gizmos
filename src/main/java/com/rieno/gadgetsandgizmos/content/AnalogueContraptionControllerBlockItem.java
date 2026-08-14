package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryService;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Handle Analogue Contraption Controller Block
public class AnalogueContraptionControllerBlockItem extends CTTooltipBlockItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String STORED_TARGETS_TAG = "StoredTargets";
    private static final String EMBEDDED_SLAB_TAG = "EmbeddedSlab";
    private static final String EMBEDDED_SLAB_MATERIAL_TAG = "EmbeddedSlabMaterial";
    private static final String EMBEDDED_BLOCK_ENTITY_TAG = "EmbeddedBlockEntity";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the analogue contraption controller block item
    public AnalogueContraptionControllerBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the inventory
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        PortableContraptionControllerItem.migrateLegacyStorage(
                stack,
                level,
                entity == null ? BlockPos.ZERO : entity.blockPosition());
    }

    // Place the analogue contraption controller block item
    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        int mountOffset = requestedEmbeddedMountOffset(ctx);
        InteractionResult res = super.place(ctx);
        if (!res.consumesAction()) {
            return res;
        }

        BlockPos placedPos = ctx.getClickedPos();
        BlockState placedState = ctx.getLevel().getBlockState(placedPos);
        BlockState correctedState = applyPlacementMount(placedState, mountOffset);
        if (correctedState != placedState) {
            ctx.getLevel().setBlock(placedPos, correctedState, 2);
        }
        return res;
    }

    // Handle analogue contraption controller block item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        InteractionResult embeddedPlacement = tryPlaceEmbedded(ctx);
        if (embeddedPlacement != InteractionResult.PASS) {
            return embeddedPlacement;
        }

        ControllerDiscoveryNode target = classifyTarget(ctx.getLevel(), ctx.getClickedPos());
        if (target == null) {
            return super.useOn(ctx);
        }

        if (ctx.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = ctx.getItemInHand();
        Player player = ctx.getPlayer();
        boolean added = storeCapturedTarget(stack, target, ctx.getLevel());
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    added ? "createthrusters.analogue_controller.capture.added" : "createthrusters.analogue_controller.capture.already_added",
                    target.label()).withStyle(added ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }

    // Try to place embedded
    private InteractionResult tryPlaceEmbedded(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState supportState = level.getBlockState(pos);
        BlockEntity supportBlockEntity = level.getBlockEntity(pos);
        EmbeddedCopycatBlockSnapshot.Snapshot backingSnapshot = supportBlockEntity == null
                ? null
                : EmbeddedCopycatBlockSnapshot.capture(level, supportBlockEntity);
        Direction clickedFace = ctx.getClickedFace();

        int mountOffset;
        if (backingSnapshot != null) {
            Vec3 localHit = ctx.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            mountOffset = ControllerEmbeddedMount.surfaceOffsetPixels(localHit, clickedFace);
            if (mountOffset <= 0 || mountOffset >= ControllerEmbeddedMount.PIXELS_PER_BLOCK) {
                return InteractionResult.PASS;
            }
        } else if (AnalogueContraptionControllerBlock.isClickedMissingSlabHalf(supportState, clickedFace)) {
            mountOffset = ControllerEmbeddedMount.PIXELS_PER_BLOCK / 2;
        } else {
            return InteractionResult.PASS;
        }

        BlockPlaceContext placeContext = new EmbeddedBlockPlaceContext(ctx, mountOffset);
        if (getPlacementState(placeContext) == null) {
            return InteractionResult.FAIL;
        }

        if (backingSnapshot != null && supportBlockEntity != null) {
            EmbeddedCopycatBlockSnapshot.clearConsumedItems(supportBlockEntity);
        }
        InteractionResult res = place(placeContext);
        if (res.consumesAction()) {
            BlockEntity placed = level.getBlockEntity(pos);
            if (placed instanceof AnalogueContraptionControllerBlockEntity controller) {
                controller.setEmbeddedBlockState(
                        supportState,
                        backingSnapshot == null ? null : backingSnapshot.material(),
                        backingSnapshot == null ? null : backingSnapshot.blockEntityData());
            }
        } else if (backingSnapshot != null) {
            BlockEntity remaining = level.getBlockEntity(pos);
            if (remaining != null && remaining.getBlockState().is(supportState.getBlock())) {
                EmbeddedCopycatBlockSnapshot.reload(remaining, backingSnapshot.blockEntityData());
            }
        }
        return res;
    }

    // Get the requested embedded mount offset
    static int requestedEmbeddedMountOffset(BlockPlaceContext ctx) {
        return ctx instanceof EmbeddedBlockPlaceContext embedded ? embedded.mountOffset : 0;
    }

    // Apply the placement mount
    static BlockState applyPlacementMount(BlockState state, int mountOffset) {
        if (!state.hasProperty(AnalogueContraptionControllerBlock.EMBEDDED_SLAB)
                || !state.hasProperty(AnalogueContraptionControllerBlock.MOUNT_OFFSET)) {
            return state;
        }
        PlacementMount placementMount = resolvePlacementMount(mountOffset);
        return state
                .setValue(AnalogueContraptionControllerBlock.EMBEDDED_SLAB, placementMount.embedded())
                .setValue(AnalogueContraptionControllerBlock.MOUNT_OFFSET, placementMount.offset());
    }

    // Resolve the placement mount
    static PlacementMount resolvePlacementMount(int mountOffset) {
        boolean embedded = mountOffset > 0 && mountOffset < ControllerEmbeddedMount.PIXELS_PER_BLOCK;
        return new PlacementMount(
                embedded,
                embedded ? mountOffset : ControllerEmbeddedMount.PIXELS_PER_BLOCK / 2);
    }

    // Update the custom block entity tag
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player,
                                                 ItemStack stack, BlockState state) {
        CustomData blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData != null) {
            CompoundTag blockEntityTag = blockEntityData.copyTag();
            if (stripEmbeddedSupportData(blockEntityTag)) {
                if (blockEntityTag.isEmpty()) {
                    stack.remove(DataComponents.BLOCK_ENTITY_DATA);
                } else {
                    stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(blockEntityTag));
                }
            }
        }
        return super.updateCustomBlockEntityTag(pos, level, player, stack, state);
    }

    // Strip embedded support data
    static boolean stripEmbeddedSupportData(CompoundTag blockEntityTag) {
        boolean changed = blockEntityTag.contains(EMBEDDED_SLAB_TAG)
                || blockEntityTag.contains(EMBEDDED_SLAB_MATERIAL_TAG)
                || blockEntityTag.contains(EMBEDDED_BLOCK_ENTITY_TAG);
        blockEntityTag.remove(EMBEDDED_SLAB_TAG);
        blockEntityTag.remove(EMBEDDED_SLAB_MATERIAL_TAG);
        blockEntityTag.remove(EMBEDDED_BLOCK_ENTITY_TAG);
        return changed;
    }

    // Store the placement mount
    record PlacementMount(boolean embedded, int offset) {
    }

    // Store embedded block place context
    private static final class EmbeddedBlockPlaceContext extends BlockPlaceContext {
        // Mount offset
        private final int mountOffset;

        // Initialize the embedded block place context
        private EmbeddedBlockPlaceContext(UseOnContext ctx, int mountOffset) {
            super(ctx);
            this.mountOffset = mountOffset;
            replaceClicked = true;
        }
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        int storedCount = getStoredTargets(stack).size();
        if (storedCount > 0) {
            tooltip.add(Component.translatable("item.createthrusters.analogue_contraption_controller.stored_targets", storedCount)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    // Get the stored targets
    public static List<ControllerDiscoveryNode> getStoredTargets(ItemStack stack) {
        return getStoredTargets(stack, null);
    }

    // Get the stored targets
    public static List<ControllerDiscoveryNode> getStoredTargets(ItemStack stack, @Nullable Level level) {
        if (stack.isEmpty()) {
            return List.of();
        }
        CompoundTag blockEntityTag = level == null
                ? ((CustomData) stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)).copyTag()
                : PortableContraptionControllerItem.readControllerData(stack, level);
        return storedTargets(blockEntityTag);
    }

    // Store the captured controller target
    public static boolean storeCapturedTarget(ItemStack stack, ControllerDiscoveryNode target) {
        return storeCapturedTarget(stack, target, null);
    }

    // Store the captured controller target
    public static boolean storeCapturedTarget(ItemStack stack, ControllerDiscoveryNode target,
                                              @Nullable Level level) {
        if (stack.isEmpty() || target == null || !target.isValid()) {
            return false;
        }

        CompoundTag blockEntityTag = level == null
                ? ((CustomData) stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)).copyTag()
                : PortableContraptionControllerItem.readControllerData(stack, level);
        List<ControllerDiscoveryNode> existingTargets = storedTargets(blockEntityTag);
        for (ControllerDiscoveryNode existing : existingTargets) {
            if (existing.nodeId().equals(target.nodeId())) {
                return false;
            }
        }

        ListTag storedTargetsTag = blockEntityTag.getList(STORED_TARGETS_TAG, Tag.TAG_COMPOUND);
        storedTargetsTag.add(target.toTag());
        blockEntityTag.put(STORED_TARGETS_TAG, storedTargetsTag);
        if (level != null && !level.isClientSide) {
            return PortableContraptionControllerItem.saveControllerDataToStackIfChanged(
                    stack,
                    blockEntityTag,
                    isAdvancedStack(stack),
                    level,
                    BlockPos.ZERO,
                    SimulatedHelper.getSubLevelId(level));
        }
        BlockEntity.addEntityType(blockEntityTag, isAdvancedStack(stack)
                ? CTBlockEntities.ADVANCED_CONTRAPTION_CONTROLLER.get()
                : CTBlockEntities.ANALOGUE_CONTRAPTION_CONTROLLER.get());
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(blockEntityTag));
        return true;
    }

    // Get the stored targets
    private static List<ControllerDiscoveryNode> storedTargets(CompoundTag blockEntityTag) {
        ListTag storedTargetsTag = blockEntityTag.getList(STORED_TARGETS_TAG, Tag.TAG_COMPOUND);
        Map<String, ControllerDiscoveryNode> nodes = new LinkedHashMap<>();
        for (int idx = 0; idx < storedTargetsTag.size(); idx++) {
            ControllerDiscoveryNode node = ControllerDiscoveryNode.fromTag(storedTargetsTag.getCompound(idx));
            if (node != null && node.isValid()) {
                nodes.putIfAbsent(node.nodeId(), node);
            }
        }
        return new ArrayList<>(nodes.values());
    }

    // Check if this is an advanced stack
    public static boolean isAdvancedStack(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof AdvancedContraptionControllerBlockItem
                || stack.getItem() instanceof PortableContraptionControllerItem portable && portable.isAdvanced());
    }

    // Get the classify target
    public static @Nullable ControllerDiscoveryNode classifyTarget(LevelAccessor level, BlockPos pos) {
        BlockEntity blockEntity = level instanceof Level actualLevel
                ? SimulatedHelper.findBlockEntityIncludingSubLevels(actualLevel, pos)
                : level.getBlockEntity(pos);
        if (blockEntity == null || blockEntity.isRemoved()) {
            return null;
        }

        UUID subLevelId = SimulatedHelper.getContainingSubLevelId(blockEntity);
        String groupId = subLevelId == null ? "world" : "sublevel:" + subLevelId;
        return ControllerDiscoveryService.classify(blockEntity, subLevelId, groupId);
    }
}
