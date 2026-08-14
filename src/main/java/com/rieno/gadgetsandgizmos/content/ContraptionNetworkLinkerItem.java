package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.util.CTInteractionGestures;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Select, inspect and link controller targets across blocks and contraption diagrams
public class ContraptionNetworkLinkerItem extends Item {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the contraption network linker item
    public ContraptionNetworkLinkerItem(Properties properties) {
        super(properties);
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
        if (level instanceof ServerLevel serverLevel
                && Math.floorMod(entity.tickCount + slotId, 20) == 0) {
            ContraptionNetworkLinkerData.migrateLegacyStorage(stack);
            ContraptionNetworkLinkerTracker.get(serverLevel.getServer()).observeLinker(serverLevel, stack);
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
                ContraptionNetworkLinkerSnapshotPayload.sendIfChanged(player, stack);
            }
        }
    }

    // Check if this is foil
    @Override
    public boolean isFoil(ItemStack stack) {
        return !ContraptionNetworkLinkerData.readClientTargets(stack).isEmpty()
                || ContraptionNetworkLinkerData.clientHasBindings(stack);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle contraption network linker item use
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (CTInteractionGestures.shouldOpenConfigMenu(player)) {
            if (level.isClientSide) {
                openClientScreen(hand, stack);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    // Handle contraption network linker item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        // -----------------------------------------------------CONFIG MENU-----------------------------------------------------
        if (CTInteractionGestures.shouldOpenConfigMenu(player)) {

            if (level.isClientSide) {
                openClientScreen(ctx.getHand(), stack);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel
                && !ContraptionNetworkLinkerTracker.get(serverLevel.getServer())
                .canMutateLinker(serverLevel, stack)) {
            return InteractionResult.FAIL;
        }

        // ------------------------------------TARGET RESOLUTION------------------------------------
        BlockPos clickedPos = ctx.getClickedPos();
        BlockEntity clickedEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, clickedPos);
        BlockEntityLookupApi.ResolvedBlockPosition resolvedClick = resolveClickedBlock(level, clickedPos, clickedEntity);
        BlockPos targetPos = resolvedClick.blockPos();
        UUID subLevelId = resolvedClick.subLevelId();

        BlockState clickedState = clickedEntity != null ? clickedEntity.getBlockState() : level.getBlockState(targetPos);
        Direction clickedFace = ctx.getClickedFace();
        ContraptionNetworkLinkerData.TargetMode targetMode = ContraptionNetworkLinkerData.getTargetMode(stack);
        ContraptionNetworkLinkerData.LinkMode editMode = ContraptionNetworkLinkerData.getEditMode(stack);
        // -----------------------------------------------------TARGET MODE-----------------------------------------------------
        ContraptionNetworkLinkerData.TargetScope targetScope = ContraptionNetworkLinkerData.resolveTargetScope(
            level,
            targetPos,
            clickedState,
            clickedFace,
            targetMode);
        String faceSignalKey = targetScope.usesFaces()
            ? ContraptionNetworkLinkerData.resolveFaceSignalKeyForBinding(clickedState, clickedFace)
            : null;
        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(clickedState.getBlock()).toString();
        String blockLabel = clickedState.getBlock().getName().getString();
        if (java.lang.Boolean.parseBoolean(System.getProperty("createthrusters.debug.linker_face_io", "true"))) {
            com.simibubi.create.Create.LOGGER.info(
                "[CT-LinkerFaceIO] bind-click pos={} face={} scope={} block={} prop={}",
                targetPos,
                clickedFace,
                targetScope,
                blockLabel,
                faceSignalKey);
        }

        if (targetScope.usesFaces()) {
            return useFacePlane(ctx, targetPos, subLevelId, clickedState, clickedFace, stack);
        }

        // -----------------------------------------------------TARGET CYCLE-----------------------------------------------------
        ContraptionNetworkLinkerData.FaceCycleState state = ContraptionNetworkLinkerData.cycleTarget(
                stack,
                targetPos,
                subLevelId,
                blockId,
                blockLabel,
                clickedFace,
                editMode,
                targetScope,
                faceSignalKey);
        observeLinker(level, stack);
        String faceOrBlockLabel = targetScope == ContraptionNetworkLinkerData.TargetScope.BLOCK
                ? "Block"
                : faceLabel(clickedFace);
        switch (state) {
            case ADDED_OUTPUT -> player.displayClientMessage(Component.translatable(
                "item.createthrusters.contraption_network_linker.face_added",
                blockLabel,
                faceOrBlockLabel,
                ContraptionNetworkLinkerData.LinkMode.OUTPUT.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.GREEN), true);
            case ADDED_INPUT, MOVED_TO_INPUT -> player.displayClientMessage(Component.translatable(
                "item.createthrusters.contraption_network_linker.face_added",
                blockLabel,
                faceOrBlockLabel,
                ContraptionNetworkLinkerData.LinkMode.INPUT.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.AQUA), true);
            case ADDED_SCM -> player.displayClientMessage(Component.translatable(
                "item.createthrusters.contraption_network_linker.face_added",
                blockLabel,
                faceOrBlockLabel,
                ContraptionNetworkLinkerData.LinkMode.SCM.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.GREEN), true);
            case MOVED_TO_OUTPUT -> player.displayClientMessage(Component.translatable(
                "item.createthrusters.contraption_network_linker.face_added",
                blockLabel,
                faceOrBlockLabel,
                ContraptionNetworkLinkerData.LinkMode.OUTPUT.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.GREEN), true);
            case REMOVED -> player.displayClientMessage(Component.translatable(
                "item.createthrusters.contraption_network_linker.face_removed",
                blockLabel,
                faceOrBlockLabel).withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResult.SUCCESS;
    }

    // Handle the contraption diagram
    public InteractionResult useOnContraptionDiagram(Entity diagram, SubLevel subLevel,
                                                      Player player, InteractionHand hand) {
        if (diagram == null || player == null) {
            return InteractionResult.PASS;
        }
        Level level = diagram.level();
        ItemStack stack = player.getItemInHand(hand);
        if (CTInteractionGestures.shouldOpenConfigMenu(player)) {
            if (level.isClientSide) {
                openClientScreen(hand, stack);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || subLevel == null || subLevel.isRemoved()) {
            player.displayClientMessage(Component.literal("This diagram is not attached to a contraption")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!ContraptionNetworkLinkerTracker.get(serverLevel.getServer())
                .canMutateLinker(serverLevel, stack)) {
            return InteractionResult.FAIL;
        }

        String subLevelName = subLevel.getName();
        String label = subLevelName == null || subLevelName.isBlank()
                ? "Contraption Diagram"
                : "Contraption Diagram - " + subLevelName;
        ContraptionNetworkLinkerData.FaceCycleState state =
                ContraptionNetworkLinkerData.toggleContraptionDiagramTarget(
                        stack, diagram.blockPosition(), subLevel.getUniqueId(), label);
        observeLinker(level, stack);
        if (state == ContraptionNetworkLinkerData.FaceCycleState.REMOVED) {
            player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.face_removed",
                    label,
                    "Contraption").withStyle(ChatFormatting.YELLOW), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.face_added",
                    label,
                    "Contraption",
                    ContraptionNetworkLinkerData.LinkMode.INPUT.id().toUpperCase(Locale.ROOT))
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.SUCCESS;
    }

    // Resolve the clicked block
    private static BlockEntityLookupApi.ResolvedBlockPosition resolveClickedBlock(
            Level level, BlockPos clickedPos, BlockEntity clickedEntity) {
        if (clickedEntity != null) {
            return new BlockEntityLookupApi.ResolvedBlockPosition(
                    clickedEntity.getBlockPos(), SimulatedHelper.getContainingSubLevelId(clickedEntity));
        }
        return SimulatedHelper.resolveBlockPositionIncludingSubLevels(level, clickedPos);
    }

    // Handle the face plane
    private InteractionResult useFacePlane(UseOnContext ctx,
                                           BlockPos clickedPos,
                                           UUID subLevelId,
                                           BlockState clickedState,
                                           Direction clickedFace,
                                           ItemStack stack) {
        Player player = ctx.getPlayer();
        Level level = ctx.getLevel();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockPos planePos = clickedPos.relative(clickedFace);
        BlockState planeState = level.getBlockState(planePos);
        boolean hasPlaneBlock = planeState.getBlock() instanceof ContraptionNetworkLinkerPlaneBlock;
        boolean placedPlaneBlock = false;
        if (!hasPlaneBlock) {
            if (!planeState.canBeReplaced()
                    || !level.setBlock(planePos, CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.get().defaultBlockState(), 3)) {
                return InteractionResult.FAIL;
            }
            placedPlaneBlock = true;
        }

        if (!(level.getBlockState(planePos).getBlock() instanceof ContraptionNetworkLinkerPlaneBlock)
                || !(level.getBlockEntity(planePos) instanceof ContraptionNetworkLinkerPlaneBlockEntity plane)) {
            if (placedPlaneBlock
                    && level.getBlockState(planePos).getBlock() instanceof ContraptionNetworkLinkerPlaneBlock) {
                level.removeBlock(planePos, false);
            }
            return InteractionResult.FAIL;
        }

        String planeBlockId = CTBlocks.CONTRAPTION_NETWORK_LINKER_PLANE.getId().toString();
        String blockLabel = clickedState.getBlock().getName().getString();
        String planeLabel = blockLabel + " " + faceLabel(clickedFace) + " Linker Plane";
        ContraptionNetworkLinkerData.FaceCycleState state = ContraptionNetworkLinkerData.cycleTarget(
                stack,
                planePos,
                subLevelId,
                planeBlockId,
                planeLabel,
                clickedFace,
                ContraptionNetworkLinkerData.getEditMode(stack),
                ContraptionNetworkLinkerData.TargetScope.FACE,
                null);

        ContraptionNetworkLinkerData.LinkMode nextMode = modeForCycleState(state);
        if (nextMode == null) {
            plane.removePlane(clickedFace);
        } else {
            plane.setPlane(clickedFace, nextMode);
        }
        observeLinker(level, stack);

        String faceLabel = faceLabel(clickedFace);
        switch (state) {
            case ADDED_OUTPUT, MOVED_TO_OUTPUT -> player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.face_added",
                    blockLabel,
                    faceLabel,
                    ContraptionNetworkLinkerData.LinkMode.OUTPUT.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.GREEN), true);
            case ADDED_INPUT, MOVED_TO_INPUT -> player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.face_added",
                    blockLabel,
                    faceLabel,
                    ContraptionNetworkLinkerData.LinkMode.INPUT.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.AQUA), true);
            case ADDED_SCM -> player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.face_added",
                    blockLabel,
                    faceLabel,
                    ContraptionNetworkLinkerData.LinkMode.SCM.id().toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.GREEN), true);
            case REMOVED -> player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.face_removed",
                    blockLabel,
                    faceLabel).withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResult.SUCCESS;
    }

    // Get the mode for cycle state
    private static ContraptionNetworkLinkerData.LinkMode modeForCycleState(ContraptionNetworkLinkerData.FaceCycleState state) {
        return switch (state) {
            case ADDED_OUTPUT, MOVED_TO_OUTPUT -> ContraptionNetworkLinkerData.LinkMode.OUTPUT;
            case ADDED_INPUT, MOVED_TO_INPUT -> ContraptionNetworkLinkerData.LinkMode.INPUT;
            case ADDED_SCM -> ContraptionNetworkLinkerData.LinkMode.SCM;
            case REMOVED -> null;
        };
    }

    // Observe the linker
    private static void observeLinker(Level level, ItemStack stack) {
        if (level instanceof ServerLevel serverLevel) {
            ContraptionNetworkLinkerTracker.get(serverLevel.getServer()).observeLinker(serverLevel, stack);
            ContraptionNetworkLinkerData.ensureClientSnapshot(stack);
        }
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        List<ContraptionNetworkLinkerData.LinkedTarget> targets = ContraptionNetworkLinkerData.readClientTargets(stack);
        int faceCount = 0;
        for (ContraptionNetworkLinkerData.LinkedTarget target : targets) {
            faceCount += target.faces().size();
        }
        tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.count",
                targets.size(), faceCount).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.target_mode",
            ContraptionNetworkLinkerData.getClientTargetMode(stack).id().toUpperCase(Locale.ROOT))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.open")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.use")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.cycle_mode")
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    // Get the face label
    private static String faceLabel(Direction dir) {
        String serialized = dir.getSerializedName();
        return Character.toUpperCase(serialized.charAt(0)) + serialized.substring(1);
    }

    // Open the client screen
    private static void openClientScreen(InteractionHand hand, ItemStack stack) {
        try {
            Class<?> clientClass = Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.ContraptionNetworkLinkerClient");
            clientClass.getMethod("openScreen", InteractionHand.class, ItemStack.class).invoke(null, hand, stack);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
