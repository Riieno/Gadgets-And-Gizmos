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
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

// Select, inspect and link controller targets across blocks and contraption diagrams
public class ContraptionNetworkLinkerItem extends Item {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ThreadLocal<Boolean> RENDER_FOIL_OVERRIDE = new ThreadLocal<>();

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
        Boolean renderFoil = RENDER_FOIL_OVERRIDE.get();
        if (renderFoil != null) {
            return renderFoil;
        }
        return !ContraptionNetworkLinkerData.readClientTargets(stack).isEmpty()
                || !ContraptionNetworkLinkerData.readClientAreas(stack).isEmpty()
                || ContraptionNetworkLinkerData.clientHasBindings(stack);
    }

    // Render the linker without a foil effect
    public static void renderWithoutFoil(Runnable render) {
        Boolean previous = RENDER_FOIL_OVERRIDE.get();
        RENDER_FOIL_OVERRIDE.set(false);
        try {
            render.run();
        } finally {
            if (previous == null) {
                RENDER_FOIL_OVERRIDE.remove();
            } else {
                RENDER_FOIL_OVERRIDE.set(previous);
            }
        }
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
        var targetMode = ContraptionNetworkLinkerData.getTargetMode(stack);
        if(ContraptionNetworkLinkerData.getEditMode(stack) == ContraptionNetworkLinkerData.LinkMode.SCM
                && (targetMode == ContraptionNetworkLinkerData.TargetMode.AREA
                || targetMode == ContraptionNetworkLinkerData.TargetMode.NO_ENTRY)
                && !CTInteractionGestures.shouldOpenConfigMenu(player)){
            if(!level.isClientSide && level instanceof ServerLevel serverLevel
                    && ContraptionNetworkLinkerTracker.get(serverLevel.getServer())
                    .canMutateLinker(serverLevel, stack)){
                BlockPos corner = BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(5.0D)));
                selectAreaCorner(level, player, hand, stack, corner, null, targetMode);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
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
        BlockPos clickedPos = ctx.getClickedPos();
        BlockEntity clickedEntity = SimulatedHelper.findBlockEntityIncludingSubLevels(level, clickedPos);
        BlockEntityLookupApi.ResolvedBlockPosition resolvedClick = resolveClickedBlock(level, clickedPos, clickedEntity);
        BlockPos targetPos = resolvedClick.blockPos();
        UUID subLevelId = resolvedClick.subLevelId();
        // -----------------------------------------------------CONFIG MENU-----------------------------------------------------
        boolean areaGesture = CTInteractionGestures.shouldOpenConfigMenu(player)
                && (level.isClientSide ? ContraptionNetworkLinkerData.readClientAreas(stack)
                : ContraptionNetworkLinkerData.readAreas(stack)).stream().anyMatch(area ->
                area.kind() == ContraptionNetworkLinkerData.AreaKind.MACHINE
                        && java.util.Objects.equals(area.subLevelId(), subLevelId)
                        && area.bounds().contains(targetPos));
        if (CTInteractionGestures.shouldOpenConfigMenu(player) && !areaGesture) {

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
        BlockState clickedState = clickedEntity != null ? clickedEntity.getBlockState() : level.getBlockState(targetPos);
        Direction clickedFace = ctx.getClickedFace();
        ContraptionNetworkLinkerData.LinkMode editMode = ContraptionNetworkLinkerData.getEditMode(stack);
        var targetMode = ContraptionNetworkLinkerData.getTargetMode(stack);
        var machineArea = ContraptionNetworkLinkerData.machineAreaAt(stack, targetPos, subLevelId);
        if(machineArea != null && CTInteractionGestures.shouldOpenConfigMenu(player)){
            var next = targetMode == ContraptionNetworkLinkerData.TargetMode.MACHINE_OUTPUT
                    ? ContraptionNetworkLinkerData.TargetMode.MACHINE_INPUT
                    : ContraptionNetworkLinkerData.TargetMode.MACHINE_OUTPUT;
            ContraptionNetworkLinkerData.setTargetMode(stack, next);
            player.displayClientMessage(Component.literal(next == ContraptionNetworkLinkerData.TargetMode.MACHINE_INPUT
                    ? "Machine input: right-click faces inside the area" : "Machine output: right-click output faces")
                    .withStyle(ChatFormatting.YELLOW), true);
            observeLinker(level, stack);
            return InteractionResult.SUCCESS;
        }
        if(editMode == ContraptionNetworkLinkerData.LinkMode.SCM
                && (targetMode == ContraptionNetworkLinkerData.TargetMode.AREA
                || targetMode == ContraptionNetworkLinkerData.TargetMode.NO_ENTRY)){
            return selectAreaCorner(level, player, ctx.getHand(), stack, targetPos, subLevelId, targetMode);
        }
        if(machineArea != null && (editMode == ContraptionNetworkLinkerData.LinkMode.SCM
                || editMode == ContraptionNetworkLinkerData.LinkMode.INPUT
                || editMode == ContraptionNetworkLinkerData.LinkMode.OUTPUT)){
            var role = targetMode == ContraptionNetworkLinkerData.TargetMode.MACHINE_OUTPUT
                    || editMode == ContraptionNetworkLinkerData.LinkMode.OUTPUT
                    ? ContraptionNetworkLinkerData.MachinePortRole.OUTPUT
                    : ContraptionNetworkLinkerData.MachinePortRole.INPUT;
            var assigned = ContraptionNetworkLinkerData.cycleMachinePortState(stack, machineArea.id(), targetPos,
                    clickedFace, role);
            if(assigned == ContraptionNetworkLinkerData.MachinePortCycleState.INVALID) return InteractionResult.FAIL;
            observeLinker(level, stack);
            player.displayClientMessage(Component.literal(switch(assigned){
                case INPUT -> "Machine input face";
                case OUTPUT -> "Machine output face";
                case CLEARED -> "Machine face cleared";
                case INVALID -> "Invalid machine face";
            }).withStyle(assigned == ContraptionNetworkLinkerData.MachinePortCycleState.CLEARED
                    ? ChatFormatting.GRAY : ChatFormatting.YELLOW), true);
            return InteractionResult.SUCCESS;
        }
        if(editMode == ContraptionNetworkLinkerData.LinkMode.SCM
                && (targetMode == ContraptionNetworkLinkerData.TargetMode.MACHINE_INPUT
                || targetMode == ContraptionNetworkLinkerData.TargetMode.MACHINE_OUTPUT)){
            player.displayClientMessage(Component.literal("Select a face inside a defined machine area")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        // -----------------------------------------------------TARGET MODE-----------------------------------------------------
        ContraptionNetworkLinkerData.TargetScope targetScope = ContraptionNetworkLinkerData.resolveTargetScope(
            level,
            targetPos,
            clickedState,
            clickedFace,
            editMode == ContraptionNetworkLinkerData.LinkMode.SCM
                    ? targetMode : ContraptionNetworkLinkerData.TargetMode.FACE);
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
                modeLabel(ContraptionNetworkLinkerData.LinkMode.OUTPUT)).withStyle(ChatFormatting.GREEN), true);
            case ADDED_INPUT, MOVED_TO_INPUT -> player.displayClientMessage(Component.translatable(
                "item.createthrusters.contraption_network_linker.face_added",
                blockLabel,
                faceOrBlockLabel,
                modeLabel(ContraptionNetworkLinkerData.LinkMode.INPUT)).withStyle(ChatFormatting.AQUA), true);
            case ADDED_SCM -> player.displayClientMessage(Component.translatable(
                "item.createthrusters.contraption_network_linker.face_added",
                blockLabel,
                faceOrBlockLabel,
                modeLabel(ContraptionNetworkLinkerData.LinkMode.SCM)).withStyle(ChatFormatting.GREEN), true);
            case MOVED_TO_OUTPUT -> player.displayClientMessage(Component.translatable(
                "item.createthrusters.contraption_network_linker.face_added",
                blockLabel,
                faceOrBlockLabel,
                modeLabel(ContraptionNetworkLinkerData.LinkMode.OUTPUT)).withStyle(ChatFormatting.GREEN), true);
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
                    modeLabel(ContraptionNetworkLinkerData.LinkMode.INPUT))
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

    // Finish a machine or no-entry box from block or air corners
    private static InteractionResult selectAreaCorner(Level level, Player player, InteractionHand hand,
                                                       ItemStack stack, BlockPos corner, @Nullable UUID subLevelId,
                                                       ContraptionNetworkLinkerData.TargetMode mode){
        var start = ContraptionNetworkLinkerData.areaStart(stack);
        if(start == null || !java.util.Objects.equals(start.subLevelId(), subLevelId)){
            ContraptionNetworkLinkerData.startArea(stack, corner, subLevelId);
            player.displayClientMessage(Component.literal("First area corner selected; select the opposite corner")
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.SUCCESS;
        }
        var kind = mode == ContraptionNetworkLinkerData.TargetMode.NO_ENTRY
                ? ContraptionNetworkLinkerData.AreaKind.NO_ENTRY : ContraptionNetworkLinkerData.AreaKind.MACHINE;
        var area = ContraptionNetworkLinkerData.addArea(stack, start.pos(), corner, subLevelId, kind);
        if(area == null){
            player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.area_too_large").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if(kind == ContraptionNetworkLinkerData.AreaKind.MACHINE)
            ContraptionNetworkLinkerData.setTargetMode(stack, ContraptionNetworkLinkerData.TargetMode.MACHINE_INPUT);
        observeLinker(level, stack);
        if(kind == ContraptionNetworkLinkerData.AreaKind.MACHINE
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer){
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerAreaConfigPayload(
                            hand, area.id(), area.label(), area.recipeId(), true));
        }
        player.displayClientMessage(Component.literal(kind == ContraptionNetworkLinkerData.AreaKind.MACHINE
                ? "Machine area saved. Mark input faces; sneak + right-click to mark outputs."
                : "No-entry area saved; workers will route around it.")
                .withStyle(kind == ContraptionNetworkLinkerData.AreaKind.MACHINE
                        ? ChatFormatting.YELLOW : ChatFormatting.RED), true);
        return InteractionResult.SUCCESS;
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
                    modeLabel(ContraptionNetworkLinkerData.LinkMode.OUTPUT)).withStyle(ChatFormatting.GREEN), true);
            case ADDED_INPUT, MOVED_TO_INPUT -> player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.face_added",
                    blockLabel,
                    faceLabel,
                    modeLabel(ContraptionNetworkLinkerData.LinkMode.INPUT)).withStyle(ChatFormatting.AQUA), true);
            case ADDED_SCM -> player.displayClientMessage(Component.translatable(
                    "item.createthrusters.contraption_network_linker.face_added",
                    blockLabel,
                    faceLabel,
                    modeLabel(ContraptionNetworkLinkerData.LinkMode.SCM)).withStyle(ChatFormatting.GREEN), true);
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
        int areaCount = ContraptionNetworkLinkerData.readClientAreas(stack).size();
        if(areaCount > 0){
            tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.areas",
                    areaCount).withStyle(ChatFormatting.GREEN));
        }
        tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.open")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.use")
                .withStyle(ChatFormatting.GRAY));
        if(ContraptionNetworkLinkerData.getClientEditMode(stack) == ContraptionNetworkLinkerData.LinkMode.SCM
                && ContraptionNetworkLinkerData.getClientTargetMode(stack) == ContraptionNetworkLinkerData.TargetMode.AREA){
            tooltip.add(Component.translatable("item.createthrusters.contraption_network_linker.tooltip.area_use")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // Get the face label
    private static String faceLabel(Direction dir) {
        String serialized = dir.getSerializedName();
        return Character.toUpperCase(serialized.charAt(0)) + serialized.substring(1);
    }

    // Get the player-facing linker mode label
    private static Component modeLabel(ContraptionNetworkLinkerData.LinkMode mode) {
        return Component.translatable(mode.translationKey());
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
