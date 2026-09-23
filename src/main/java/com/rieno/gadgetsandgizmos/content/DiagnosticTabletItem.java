package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityLookupApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;

// Handle the portable Diagnostic Tablet item
public class DiagnosticTabletItem extends BlockItem {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet item
    public DiagnosticTabletItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle diagnostic tablet item use
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!CTFeatureToggles.isItemEnabled("diagnostic_tablet")) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide && invokeClientBoolean("tryCycleMode",
                new Class<?>[]{InteractionHand.class, ItemStack.class}, hand, stack)) {
            return InteractionResultHolder.success(stack);
        }
        DiagnosticTabletData.State state = DiagnosticTabletData.read(stack);
        if (state.mode() == TabletInteractionMode.STANDARD && level.isClientSide) {
            openClientScreen(hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle diagnostic tablet item use on the target
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        // ------------------------------------INTERACTION CHECKS------------------------------------
        if (!CTFeatureToggles.isItemEnabled("diagnostic_tablet")) {
            return InteractionResult.PASS;
        }
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // ------------------------------------MODE SELECTION------------------------------------
        if (player.isShiftKeyDown()) {
            return super.useOn(ctx);
        }
        DiagnosticTabletData.State current = DiagnosticTabletData.read(ctx.getItemInHand());
        if (!ctx.getLevel().isClientSide && current.tabletId() == null) {
            DiagnosticTabletData.ensureTabletId(ctx.getItemInHand());
            current = DiagnosticTabletData.read(ctx.getItemInHand());
        }
        if (!ctx.getLevel().isClientSide && current.tabletId() != null
                && player instanceof ServerPlayer serverPlayer) {
            DiagnosticTabletDatabase.forServer(serverPlayer.server).importLegacy(current.tabletId(), current);
            current = current.withoutLegacyAppData();
            DiagnosticTabletData.write(ctx.getItemInHand(), current);
        }
        if (current.mode() == TabletInteractionMode.STANDARD) {
            if (ctx.getLevel().isClientSide) {
                openClientScreen(ctx.getHand(), ctx.getItemInHand());
            }
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }

        // ------------------------------------TARGET SELECTION------------------------------------
        BlockEntity target = SimulatedHelper.findBlockEntityIncludingSubLevels(
                ctx.getLevel(), ctx.getClickedPos());
        DiagnosticTabletData.Binding selected = bindingFor(target, ctx);
        if (ctx.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // -----------------------------------------------------PUSH MODE-----------------------------------------------------
        if (current.mode() == TabletInteractionMode.PUSH) {
            if (!"controller".equals(selected.type()) && !"scm".equals(selected.type())) {
                return InteractionResult.FAIL;
            }
            if (!DiagnosticTabletData.appId("scm").equals(current.app())) {
                return InteractionResult.FAIL;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                UUID tabletId = current.tabletId() == null
                        ? DiagnosticTabletData.ensureTabletId(ctx.getItemInHand()) : current.tabletId();
                DiagnosticTabletAppStorage.addBinding(serverPlayer.server, tabletId,
                        current.app(), selected, true);
                String val = encodeSelections(DiagnosticTabletAppStorage.selections(
                        serverPlayer.server, tabletId, current.app()));
                DiagnosticTabletApps.dispatch(serverPlayer, ctx.getItemInHand(), current,
                        new TabletAction(current.app(), current.tab(), "push_configured", Map.of("value", val)));
                DiagnosticTabletData.write(ctx.getItemInHand(), current.withMode(
                        TabletInteractionMode.STANDARD, ""));
            }
            return InteractionResult.SUCCESS;
        }

        // ------------------------------------ACTION DISPATCH------------------------------------
        if (!current.pendingAction().isBlank()) {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID tabletId = current.tabletId() == null
                        ? DiagnosticTabletData.ensureTabletId(ctx.getItemInHand()) : current.tabletId();
                if (DiagnosticTabletData.appId("nfc").equals(current.app())
                        && "nfc_scan".equals(current.pendingAction())) {
                    DiagnosticTabletAppStorage.addBinding(serverPlayer.server, tabletId,
                            current.app(), selected, true);
                } else {
                    DiagnosticTabletAppStorage.addSelection(serverPlayer.server, tabletId,
                            current.app(), selected);
                }
                DiagnosticTabletApps.dispatch(serverPlayer, ctx.getItemInHand(), current,
                        new TabletAction(current.app(), current.tab(), current.pendingAction(),
                                Map.of("value", selectionValue(selected))));
                CompoundTag appData = DiagnosticTabletAppStorage.data(
                        serverPlayer.server, tabletId, current.app());
                boolean continueLandingZone = DiagnosticTabletData.appId("scm").equals(current.app())
                        && "landing_zone".equals(current.pendingAction())
                        && appData.contains("LandingDraftStart");
                boolean continueReader = continueLandingZone;
                DiagnosticTabletData.write(ctx.getItemInHand(), current.withMode(
                        continueReader ? TabletInteractionMode.READER : TabletInteractionMode.STANDARD,
                        continueLandingZone ? "landing_zone" : ""));
            }
            return InteractionResult.SUCCESS;
        }

        if (DiagnosticTabletData.appId("nfc").equals(current.app())) {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID tabletId = current.tabletId() == null
                        ? DiagnosticTabletData.ensureTabletId(ctx.getItemInHand()) : current.tabletId();
                DiagnosticTabletAppStorage.addBinding(serverPlayer.server, tabletId,
                        current.app(), selected, true);
                DiagnosticTabletApps.dispatch(serverPlayer, ctx.getItemInHand(), current,
                        new TabletAction(current.app(), current.tab(), "nfc_scan",
                                Map.of("value", selectionValue(selected))));
            }
            return InteractionResult.SUCCESS;
        }

        if ("controller".equals(selected.type()) || "computer".equals(selected.type())
                || "scm".equals(selected.type())
                || "dock".equals(selected.type())) {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID tabletId = current.tabletId() == null
                        ? DiagnosticTabletData.ensureTabletId(ctx.getItemInHand()) : current.tabletId();
                boolean rdp = DiagnosticTabletData.appId("rdp").equals(current.app());
                boolean scm = DiagnosticTabletData.appId("scm").equals(current.app());
                if (rdp && !"controller".equals(selected.type()) && !"computer".equals(selected.type())
                        && !"scm".equals(selected.type())) {
                    return InteractionResult.FAIL;
                }
                if (!rdp && !scm) {
                    return InteractionResult.FAIL;
                }
                DiagnosticTabletAppStorage.addBinding(serverPlayer.server, tabletId,
                        current.app(), selected, true);
                DiagnosticTabletData.write(ctx.getItemInHand(), current.withMode(
                        TabletInteractionMode.STANDARD, ""));
            }
        } else {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID tabletId = current.tabletId() == null
                        ? DiagnosticTabletData.ensureTabletId(ctx.getItemInHand()) : current.tabletId();
                DiagnosticTabletAppStorage.addSelection(serverPlayer.server, tabletId,
                        current.app(), selected);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Handle the living entity
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                  LivingEntity target, InteractionHand hand) {
        if (!CTFeatureToggles.isItemEnabled("diagnostic_tablet")) return InteractionResult.PASS;
        DiagnosticTabletData.State state = DiagnosticTabletData.read(stack);
        if (state.mode() != TabletInteractionMode.READER
                || !DiagnosticTabletData.appId("block360").equals(state.app())
                || !(target instanceof Player)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && target instanceof ServerPlayer friend) {
            DiagnosticTabletFriendDatabase.request(serverPlayer, friend);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    // Get the binding
    private static DiagnosticTabletData.Binding bindingFor(BlockEntity target, UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clickedPos = ctx.getClickedPos();
        BlockEntityLookupApi.ResolvedBlockPosition resolved = target == null
                ? SimulatedHelper.resolveBlockPositionIncludingSubLevels(level, clickedPos)
                : new BlockEntityLookupApi.ResolvedBlockPosition(target.getBlockPos(),
                SimulatedHelper.getContainingSubLevelId(target));
        UUID subLevelId = resolved.subLevelId();
        BlockPos pos = resolved.blockPos();
        BlockEntity exactTarget = SimulatedHelper.findLoadedBlockEntityExact(level, subLevelId, pos);
        if (exactTarget != null) target = exactTarget;

        Level actualLevel = target != null && target.getLevel() != null ? target.getLevel() : level;
        BlockState clickedState = actualLevel.getBlockState(pos);
        BlockEntity support = SimulatedHelper.findLoadedBlockEntityExact(level, subLevelId, pos.below());
        if (support instanceof AdvancedContraptionControllerBlockEntity controller) {
            Level controllerLevel = controller.getLevel();
            if (controllerLevel != null) clickedState = controllerLevel.getBlockState(pos);
        }
        if (clickedState.getBlock() instanceof ShipControlModuleBlock
                && support instanceof AdvancedContraptionControllerBlockEntity controller) {
            return new DiagnosticTabletData.Binding("scm",
                    SimulatedHelper.getContainingSubLevelId(controller), controller.getBlockPos(),
                    "Ship Control Module");
        }
        String type = bindingType(target, clickedState);
        String label = target == null
                ? clickedState.getBlock().getName().getString()
                : target.getBlockState().getBlock().getName().getString();
        return new DiagnosticTabletData.Binding(type, subLevelId, pos, label);
    }

    // Get the binding type
    private static String bindingType(BlockEntity target, BlockState clickedState) {
        if (target instanceof AdvancedContraptionControllerBlockEntity) return "controller";
        if (isWirelessComputer(target)) return "computer";
        if (target instanceof ShipDockBlockEntity) return "dock";
        if (clickedState.getBlock() instanceof ShipControlModuleBlock) return "scm";
        return "block";
    }

    // Check if this is a wireless computer
    private static boolean isWirelessComputer(BlockEntity target) {
        try {
            Class<?> remoteDesktop = Class.forName(
                    "com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftRemoteDesktop");
            Object available = remoteDesktop.getMethod("isAvailable", BlockEntity.class).invoke(null, target);
            return available instanceof Boolean val && val;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    // Get the selection value
    private static String selectionValue(DiagnosticTabletData.Binding binding) {
        return binding.label() + ":" + binding.pos().getX() + ","
                + binding.pos().getY() + "," + binding.pos().getZ();
    }

    // Encode the selections
    private static String encodeSelections(List<DiagnosticTabletData.Binding> selections) {
        return selections.stream().map(DiagnosticTabletItem::selectionValue)
                .collect(java.util.stream.Collectors.joining(";"));
    }

    // Add the hover text
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        DiagnosticTabletData.State state = DiagnosticTabletData.read(stack);
        tooltip.add(Component.translatable("item.createthrusters.diagnostic_tablet.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Mode: " + state.mode().id())
                .withStyle(state.mode() == TabletInteractionMode.STANDARD
                        ? ChatFormatting.GRAY : ChatFormatting.AQUA));
        tooltip.add(Component.literal("Alt + Use: switch Standard / Reader")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (state.tabletId() != null) tooltip.add(Component.literal(
                "Tablet ID: " + state.tabletId().toString().substring(0, 8))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // Open the client screen
    public static void openClientScreen(InteractionHand hand, ItemStack stack) {
        invokeClient("openItem", new Class<?>[]{InteractionHand.class, ItemStack.class}, hand, stack);
    }

    // Open the client screen
    public static void openClientScreen(DiagnosticTabletBlockEntity tablet) {
        invokeClient("openBlock", new Class<?>[]{DiagnosticTabletBlockEntity.class}, tablet);
    }

    // Handle the client projection
    public static boolean interactClientProjection(DiagnosticTabletBlockEntity tablet,
                                                   BlockHitResult hit, int mouseButton) {
        return invokeClientProjectionBoolean("interactBlock",
                new Class<?>[]{DiagnosticTabletBlockEntity.class, BlockHitResult.class, int.class},
                tablet, hit, mouseButton);
    }

    // Run the client
    private static void invokeClient(String method, Class<?>[] parameterTypes, Object... args) {
        try {
            Class<?> type = Class.forName(
                    "com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletScreen");
            Method target = type.getMethod(method, parameterTypes);
            target.invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    // Run the client boolean
    private static boolean invokeClientBoolean(String method, Class<?>[] parameterTypes,
                                               Object... args) {
        try {
            Class<?> type = Class.forName(
                    "com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletClientInteraction");
            Method target = type.getMethod(method, parameterTypes);
            return Boolean.TRUE.equals(target.invoke(null, args));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    // Run the client projection boolean
    private static boolean invokeClientProjectionBoolean(String method, Class<?>[] parameterTypes,
                                                         Object... args) {
        try {
            Class<?> type = Class.forName(
                    "com.rieno.gadgetsandgizmos.neoforge.client.DiagnosticTabletGuiProjection");
            Method target = type.getMethod(method, parameterTypes);
            return Boolean.TRUE.equals(target.invoke(null, args));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
