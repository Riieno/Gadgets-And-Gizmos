package com.rieno.gadgetsandgizmos.neoforge;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.computercraft.RopeWinchPeripheralBridge;
import com.rieno.gadgetsandgizmos.content.ClawBlockEntity;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlock;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.CTDirectionalBlock;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlock;
import com.rieno.gadgetsandgizmos.content.DoubleButtonBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlock;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletFriendDatabase;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.content.DockingConnectorAutomation;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShipDockBlockItem;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryCarriageBlockEntity;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlock;
import com.rieno.gadgetsandgizmos.content.PhysicsGantryShaftBlockEntity;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.content.WorkerInventoryMenu;
import com.rieno.gadgetsandgizmos.content.PortableContraptionControllerRuntime;
import com.rieno.gadgetsandgizmos.content.RopeWinchUnstickWindow;
import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.content.ThrusterBlockEntity;
import com.rieno.gadgetsandgizmos.content.ShippingSchedulePilot;
import com.rieno.gadgetsandgizmos.content.SupporterHeads;
import com.rieno.gadgetsandgizmos.content.SupporterMannequinPlacement;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryService;
import com.rieno.gadgetsandgizmos.lib.discovery.INamedBlockEntity;
import com.rieno.gadgetsandgizmos.lib.item.MiningSpeedSafety;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import com.rieno.gadgetsandgizmos.neoforge.network.ArmorStandPoseOpenPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.ContraptionNetworkLinkerSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTFeatureToggles;
import com.rieno.gadgetsandgizmos.registry.CTItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.simibubi.create.foundation.utility.RaycastHelper;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlock;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Keep player-owned controller, tablet and moving-ship sessions valid across login and dimension changes
@EventBusSubscriber
public final class CTPlayerEvents {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final ResourceLocation CREATE_WRENCH = ResourceLocation.parse("create:wrench");
    private static final ResourceLocation STRAW_STATUE = ResourceLocation.fromNamespaceAndPath("strawstatues", "straw_statue");
    private static final Map<UUID, PendingGantryRelink> PENDING_GANTRY_RELINK = new HashMap<>();
    private static final Map<UUID, ArmorStandPoseGuiPreference> ARMOR_STAND_POSE_GUI_PREFERENCES = new HashMap<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT player events
    private CTPlayerEvents() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the player logged in event
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            CTCommonEvents.syncFeatureTogglesToPlayer(serverPlayer);
            CTCommonEvents.syncGraphV2ThemeToPlayer(serverPlayer);
        }
    }

    // Handle the player logged out event
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PortableContraptionControllerRuntime.stopAll(serverPlayer);
            ShippingRouteOverlayService.forget(serverPlayer.getUUID());
        }
        ContraptionNetworkLinkerSnapshotPayload.clearServerState(event.getEntity().getUUID());
        ARMOR_STAND_POSE_GUI_PREFERENCES.remove(event.getEntity().getUUID());
    }

    // Recover invalid mining speeds produced by modded or over-levelled pickaxe attributes
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        event.setNewSpeed(MiningSpeedSafety.recoverInvalidPickaxeSpeed(
                event.getEntity(), event.getState(), event.getNewSpeed()));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Protect the shipping pilots
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectShippingPilots(LivingIncomingDamageEvent event) {
        if (ShippingSchedulePilot.isPilot(event.getEntity())
                && !(event.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player)) {
            event.setCanceled(true);
        }
    }

    // Set the armor stand pose gui preference
    public static void setArmorStandPoseGuiPreference(ServerPlayer player, boolean enabled, boolean preferStrawStatuesGui) {
        if (player != null) {
            ARMOR_STAND_POSE_GUI_PREFERENCES.put(player.getUUID(),
                    new ArmorStandPoseGuiPreference(enabled, preferStrawStatuesGui));
        }
    }

    // Check if this can use armor stand pose GUI
    public static boolean canUseArmorStandPoseGui(ArmorStand armorStand) {
        if (armorStand == null || isStrawStatue(armorStand)) {
            return false;
        }
        if (armorStand instanceof PlayerMannequinEntity) {
            return true;
        }
        if (armorStand.isMarker()) {
            return false;
        }
        return !(armorStand.isInvisible() && armorStand.isInvulnerable() && armorStand.isNoGravity());
    }

    // Check if this is straw statue
    private static boolean isStrawStatue(ArmorStand armorStand) {
        return STRAW_STATUE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(armorStand.getType()));
    }

    // Check if the armor stand pose GUI is enabled
    private static boolean isArmorStandPoseGuiEnabled(ServerPlayer player) {
        return armorStandPoseGuiPreference(player).enabled();
    }

    // Check if the player prefers the Straw Statues pose screen
    private static boolean prefersStrawStatuesPoseGui(ServerPlayer player) {
        return armorStandPoseGuiPreference(player).preferStrawStatuesGui();
    }

    // Get the armor stand pose gui preference
    private static ArmorStandPoseGuiPreference armorStandPoseGuiPreference(ServerPlayer player) {
        return ARMOR_STAND_POSE_GUI_PREFERENCES.getOrDefault(player.getUUID(), ArmorStandPoseGuiPreference.DEFAULT);
    }

    // Handle the right click block event
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryHandlePlacedTabletInteraction(event)) {
            return;
        }

        if (tryHandleTabletTargeting(event)) {
            return;
        }

        if (tryHandleLinkerFaceCapture(event)) {
            return;
        }

        if (tryOpenAccDisplayMode(event)) {
            return;
        }

        if (ShippingSchedulePilot.tryInteract(event)) {
            return;
        }

        if (tryHandleSupporterMannequinSpawn(event)) {
            return;
        }

        tryActivateRopeWinchUnstick(event);

        if (tryHandleDoubleButtonFreq(event)) {
            return;
        }

        if (tryBindDockingConnectorToShipDock(event)) {
            return;
        }

        if (event.getLevel().isClientSide()) {
            return;
        }

        if (tryHandleSneakNameTagRename(event)) {
            return;
        }

        if (tryHandleGantrySlimeRelink(event)) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (isDisabledModItem(held)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(held.getItem());

        if (!CREATE_WRENCH.equals(heldId)) {
            return;
        }
        if (event.getEntity().isShiftKeyDown()) {
            return;
        }

        BlockPos rotatePos = event.getPos();
        BlockState state = event.getLevel().getBlockState(rotatePos);
        boolean isThrusterDirectional = state.getBlock() instanceof ThrusterBlock;
        Direction face = event.getFace();
        if (!isThrusterDirectional || face == null || !state.hasProperty(CTDirectionalBlock.FACING)
                || state.getValue(CTDirectionalBlock.FACING) == face) {
            return;
        }

        event.getLevel().setBlock(rotatePos, state.setValue(CTDirectionalBlock.FACING, face), 3);
        BlockEntity rotatedBE = event.getLevel().getBlockEntity(rotatePos);
        if (rotatedBE instanceof ThrusterBlockEntity thruster) {
            thruster.updateSignal();
        }
        event.setCanceled(true);
    }

    // Handle the left click block event
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !event.getEntity().getMainHandItem().isEmpty()) {
            return;
        }
        AccDisplayBlockEntity display = AccDisplayBlock.findDisplay(
                event.getLevel(), event.getPos());
        if (display == null) {
            return;
        }
        net.minecraft.world.phys.HitResult picked = event.getEntity().pick(6.0D, 1.0F, false);
        if (picked instanceof BlockHitResult hit) {
            if (event.getLevel().isClientSide()) {
                invokeAccDisplayClient("interactProjection",
                        new Class<?>[]{AccDisplayBlockEntity.class, BlockHitResult.class, int.class},
                        display, hit, 0);
            } else {
                display.interact(event.getEntity(), hit, 0);
            }
        }
        event.setCanceled(true);
    }

    // Try to open ACC display mode
    private static boolean tryOpenAccDisplayMode(PlayerInteractEvent.RightClickBlock evt) {
        if (!CREATE_WRENCH.equals(BuiltInRegistries.ITEM.getKey(evt.getItemStack().getItem()))
                || evt.getEntity().isShiftKeyDown()) {
            return false;
        }
        AccDisplayBlockEntity display = AccDisplayBlock.findDisplay(
                evt.getLevel(), evt.getPos());
        if (display == null) {
            return false;
        }
        if (evt.getLevel().isClientSide()) {
            invokeAccDisplayClient("openModeSelection",
                    new Class<?>[]{AccDisplayBlockEntity.class}, display);
        }
        evt.setCancellationResult(InteractionResult.sidedSuccess(evt.getLevel().isClientSide()));
        evt.setCanceled(true);
        return true;
    }

    // Run the ACC display client
    private static void invokeAccDisplayClient(String method, Class<?>[] parameterTypes,
                                               Object... args) {
        try {
            Class.forName("com.rieno.gadgetsandgizmos.neoforge.client.AccDisplayClientScreens")
                    .getMethod(method, parameterTypes).invoke(null, args);
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
    }

    // Handle the entity interact specific event
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (tryHandleTabletFriendReq(event)) {
            return;
        }
        if (ShippingSchedulePilot.tryInteract(event)) {
            return;
        }
        if (!(event.getTarget() instanceof ArmorStand armorStand)) {
            return;
        }
        if (!event.getEntity().isShiftKeyDown() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer serverPlayer)
                ) {
            return;
        }

        if (armorStand instanceof PlayerMannequinEntity mannequin
                && mannequin.assignedWorkerPod().isPresent()) {
            WorkerInventoryMenu.open(serverPlayer, mannequin);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        if (!isArmorStandPoseGuiEnabled(serverPlayer) || !canUseArmorStandPoseGui(armorStand)) return;

        if (prefersStrawStatuesPoseGui(serverPlayer)
                && StrawStatuesPoseGuiCompat.tryOpen(serverPlayer, armorStand)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }

        PacketDistributor.sendToPlayer(serverPlayer, new ArmorStandPoseOpenPayload(armorStand.getId()));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    // Handle the entity interact event
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (tryHandleTabletFriendReq(event)) {
            return;
        }
        ShippingSchedulePilot.tryInteract(event);
    }

    // Try to handle tablet friend req
    private static boolean tryHandleTabletFriendReq(PlayerInteractEvent evt) {
        if (evt.getHand() != InteractionHand.MAIN_HAND
                || !(evt.getEntity() instanceof ServerPlayer player)
                || !(evt.getItemStack().getItem() instanceof DiagnosticTabletItem)) {
            return false;
        }
        DiagnosticTabletData.State state = DiagnosticTabletData.read(evt.getItemStack());
        if (state.mode() != TabletInteractionMode.READER
                || !DiagnosticTabletData.appId("block360").equals(state.app())) return false;
        net.minecraft.world.entity.Entity target = evt instanceof PlayerInteractEvent.EntityInteractSpecific specific
                ? specific.getTarget() : evt instanceof PlayerInteractEvent.EntityInteract general
                ? general.getTarget() : null;
        if (!(target instanceof ServerPlayer other)) {
            return false;
        }
        DiagnosticTabletFriendDatabase.request(player, other);
        if (evt instanceof PlayerInteractEvent.EntityInteractSpecific specific) {
            specific.setCancellationResult(InteractionResult.SUCCESS);
            specific.setCanceled(true);
        } else if (evt instanceof PlayerInteractEvent.EntityInteract general) {
            general.setCancellationResult(InteractionResult.SUCCESS);
            general.setCanceled(true);
        }
        return true;
    }

    // Try to spawn a supporter mannequin from its marked player head
    private static boolean tryHandleSupporterMannequinSpawn(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getEntity() == null || SupporterHeads.getVariant(evt.getItemStack()) == null) {
            return false;
        }

        UseOnContext useContext = new UseOnContext(evt.getEntity(), evt.getHand(), evt.getHitVec());
        InteractionResult res = SupporterMannequinPlacement.useOn(useContext);
        if (res == InteractionResult.PASS) {
            return false;
        }
        evt.setCancellationResult(res);
        evt.setCanceled(true);
        return true;
    }

    // Try to activate rope winch unstick
    private static void tryActivateRopeWinchUnstick(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getEntity() == null) {
            return;
        }

        ItemStack held = evt.getItemStack();
        if (!held.isEmpty() && held.is(SimTags.Items.DESTROYS_ROPE)) {
            return;
        }

        BlockPos pos = evt.getPos();
        BlockState state = evt.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof RopeWinchBlock)) {
            return;
        }
        if (!(evt.getLevel().getBlockEntity(pos) instanceof RopeWinchPeripheralBridge bridge)) {
            return;
        }

        ClawBlockEntity claw = bridge.ct$getAttachedClaw();
        if (claw == null) {
            return;
        }

        RopeWinchUnstickWindow.activate(evt.getEntity(), claw);
    }

    // Bind a held docking connector to a Ship Dock before placement
    private static boolean tryBindDockingConnectorToShipDock(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getEntity() == null || !DockingConnectorAutomation.isDockingConnectorItem(evt.getItemStack())) {
            return false;
        }
        if (!(evt.getLevel().getBlockEntity(evt.getPos()) instanceof ShipDockBlockEntity dock)) {
            return false;
        }
        if (!evt.getLevel().isClientSide()) {
            ShipDockBlockItem.bindDockingConnectorToShipDock(evt.getItemStack(), dock);
            evt.getEntity().displayClientMessage(Component.translatable(
                    "createthrusters.ship_dock.connector_bound_to_dock", dock.getDockName()), true);
        }
        evt.setCancellationResult(InteractionResult.SUCCESS);
        evt.setCanceled(true);
        return true;
    }

    // Try to handle linker face capture
    private static boolean tryHandleLinkerFaceCapture(PlayerInteractEvent.RightClickBlock evt) {
        ItemStack held = evt.getItemStack();
        if (!linkerOwnsInteraction(evt.getEntity() != null,
                held.getItem() instanceof ContraptionNetworkLinkerItem)) {
            return false;
        }
        ContraptionNetworkLinkerItem linkerItem = (ContraptionNetworkLinkerItem) held.getItem();

        UseOnContext useContext = new UseOnContext(evt.getEntity(), evt.getHand(), evt.getHitVec());
        InteractionResult res = linkerItem.useOn(useContext);
        evt.setCancellationResult(res == InteractionResult.PASS ? InteractionResult.SUCCESS : res);
        evt.setCanceled(true);
        return true;
    }

    // Try to handle tablet targeting
    private static boolean tryHandleTabletTargeting(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getEntity() == null || evt.getEntity().isShiftKeyDown()
                || !CTFeatureToggles.isItemEnabled("diagnostic_tablet")) {
            return false;
        }
        InteractionHand tabletHand = evt.getHand();
        ItemStack held = evt.getEntity().getItemInHand(tabletHand);
        if (!isNonStandardTablet(held)) {
            if (evt.getHand() != InteractionHand.MAIN_HAND
                    || mainHandOwnsTabletInteraction(held)
                    || !isNonStandardTablet(evt.getEntity().getOffhandItem())) {
                return false;
            }
            tabletHand = InteractionHand.OFF_HAND;
            held = evt.getEntity().getOffhandItem();
        }
        if (!(held.getItem() instanceof DiagnosticTabletItem tabletItem)) {
            return false;
        }

        UseOnContext ctx = new UseOnContext(evt.getEntity(), tabletHand, evt.getHitVec());
        InteractionResult res = tabletItem.useOn(ctx);
        evt.setCancellationResult(res.consumesAction() ? res
                : InteractionResult.sidedSuccess(evt.getLevel().isClientSide()));
        evt.setCanceled(true);
        return true;
    }

    // Check if this is non-standard tablet
    private static boolean isNonStandardTablet(ItemStack stack) {
        return stack.getItem() instanceof DiagnosticTabletItem
                && DiagnosticTabletData.read(stack).mode() != TabletInteractionMode.STANDARD;
    }

    // Check if the main hand owns the tablet interaction
    private static boolean mainHandOwnsTabletInteraction(ItemStack stack) {
        return isNonStandardTablet(stack)
                || stack.getItem() instanceof ContraptionNetworkLinkerItem
                || (CTItems.POWERED_ZIPLINE != null && stack.is(CTItems.POWERED_ZIPLINE.get()));
    }

    // Try to handle placed tablet interaction
    private static boolean tryHandlePlacedTabletInteraction(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getEntity() == null) {
            return false;
        }
        DiagnosticTabletBlock.PlacedInteraction interaction = DiagnosticTabletBlock.resolveInteraction(
                evt.getLevel(), evt.getHitVec());
        if (interaction == null) return false;
        InteractionResult res = interaction.block().interactPlacedTablet(
                evt.getLevel(), evt.getEntity(), interaction);
        if (!res.consumesAction()) {
            return false;
        }
        evt.setCancellationResult(res);
        evt.setCanceled(true);
        return true;
    }

    // Check if the linker owns the interaction
    static boolean linkerOwnsInteraction(boolean playerPresent, boolean linkerHeld) {
        return playerPresent && linkerHeld;
    }

    // Try to handle double button freq
    private static boolean tryHandleDoubleButtonFreq(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getEntity() == null || evt.getEntity().isShiftKeyDown() || evt.getEntity().isSpectator()) {
            return false;
        }

        ItemStack held = evt.getItemStack();
        if (CREATE_WRENCH.equals(BuiltInRegistries.ITEM.getKey(held.getItem()))) {
            return false;
        }

        BlockPos pos = evt.getPos();
        if (!(evt.getLevel().getBlockEntity(pos) instanceof DoubleButtonBlockEntity doubleButton)) {
            return false;
        }

        BlockHitResult ray = RaycastHelper.rayTraceRange(evt.getLevel(), evt.getEntity(), 10.0D);
        Vec3 hitLocation = ray != null && ray.getBlockPos().equals(pos)
                ? ray.getLocation()
                : evt.getHitVec().getLocation();
        DoubleButtonBlock.Target target = DoubleButtonBlock.targetAt(doubleButton.getBlockState(), pos, hitLocation);
        if (!(target instanceof DoubleButtonBlock.Target.Frequency frequency)) {
            return false;
        }

        if (!evt.getLevel().isClientSide()) {
            doubleButton.setFrequency(frequency.button(), frequency.firstFrequency(), held);
            evt.getLevel().playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.25F, 0.1F);
        }

        evt.setCancellationResult(InteractionResult.SUCCESS);
        evt.setCanceled(true);
        return true;
    }

    // Try to handle gantry slime relink
    private static boolean tryHandleGantrySlimeRelink(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getEntity() == null) {
            return false;
        }

        // -----------------------------------------------------GESTURE CHECK-----------------------------------------------------
        ItemStack held = evt.getItemStack();
        if (!held.is(Items.SLIME_BALL)) {
            return false;
        }

        UUID playerId = evt.getEntity().getUUID();
        BlockPos clickedPos = evt.getPos();
        BlockEntity clickedBe = SimulatedHelper.findBlockEntityIncludingSubLevels(evt.getLevel(), clickedPos);
        BlockState clickedState = clickedBe == null ? evt.getLevel().getBlockState(clickedPos) : clickedBe.getBlockState();

        // ------------------------------------CARRIAGE SELECTION------------------------------------
        if (clickedBe instanceof PhysicsGantryCarriageBlockEntity) {
            UUID clickedSubLevelId = SimulatedHelper.getContainingSubLevelId(clickedBe);
            PendingGantryRelink selected = PENDING_GANTRY_RELINK.get(playerId);
            if (selected != null && !selected.matches(clickedBe.getBlockPos(), clickedSubLevelId)) {

                PENDING_GANTRY_RELINK.remove(playerId);
                evt.getEntity().displayClientMessage(Component.literal(
                        "You cannot attach a Physics Gantry Carriage to a Physics Gantry Carriage, Selection Cleared."), true);
                evt.setCancellationResult(InteractionResult.SUCCESS);
                evt.setCanceled(true);
                return true;
            }

            PENDING_GANTRY_RELINK.put(playerId, new PendingGantryRelink(
                    clickedBe.getBlockPos().immutable(),
                    clickedSubLevelId));
            evt.getEntity().displayClientMessage(Component.literal("Selected gantry carriage base face for relink"), true);
            evt.setCancellationResult(InteractionResult.SUCCESS);
            evt.setCanceled(true);
            return true;
        }

        // -----------------------------------------------------SHAFT TARGET-----------------------------------------------------
        if (!(clickedState.getBlock() instanceof PhysicsGantryShaftBlock)) {
            return false;
        }

        Direction clickedFace = evt.getFace();
        if (clickedFace == null) {
            evt.getEntity().displayClientMessage(Component.literal("Could not resolve shaft side for relink"), true);
            evt.setCancellationResult(InteractionResult.SUCCESS);
            evt.setCanceled(true);
            return true;
        }

        PendingGantryRelink selected = PENDING_GANTRY_RELINK.remove(playerId);
        if (selected == null) {
            evt.getEntity().displayClientMessage(Component.literal("Select a gantry carriage first"), true);
            evt.setCancellationResult(InteractionResult.SUCCESS);
            evt.setCanceled(true);
            return true;
        }

        BlockEntity selectedBe = SimulatedHelper.findBlockEntity(
                evt.getLevel(),
                selected.subLevelId(),
                selected.pos());
        if (selectedBe == null) {
            selectedBe = SimulatedHelper.findBlockEntityIncludingSubLevels(evt.getLevel(), selected.pos());
        }
        if (!(selectedBe instanceof PhysicsGantryCarriageBlockEntity carriage)) {
            evt.getEntity().displayClientMessage(Component.literal("Selected carriage is no longer valid"), true);
            evt.setCancellationResult(InteractionResult.SUCCESS);
            evt.setCanceled(true);
            return true;
        }

        // ------------------------------------RELINK REQUEST------------------------------------
        PhysicsGantryShaftBlockEntity shaft = clickedBe instanceof PhysicsGantryShaftBlockEntity shaftBE ? shaftBE : null;
        boolean started = carriage.beginManualShaftRelink(clickedPos, clickedFace, evt.getEntity(), shaft);
        if (started) {
            evt.getEntity().displayClientMessage(Component.literal("Created new gantry attachment point"), true);
        } else {
            evt.getEntity().displayClientMessage(Component.literal("Could not relink gantry to that shaft side; selection cleared"), true);
        }

        evt.setCancellationResult(InteractionResult.SUCCESS);
        evt.setCanceled(true);
        return true;
    }

    // Store the pending gantry relink
    private record PendingGantryRelink(BlockPos pos, UUID subLevelId) {
        // Check if this matches the value
        private boolean matches(BlockPos otherPos, UUID otherSubLevelId) {
            return pos.equals(otherPos) && java.util.Objects.equals(subLevelId, otherSubLevelId);
        }
    }

    // Store the armor stand pose gui preference
    private record ArmorStandPoseGuiPreference(boolean enabled, boolean preferStrawStatuesGui) {
        private static final ArmorStandPoseGuiPreference DEFAULT = new ArmorStandPoseGuiPreference(true, true);
    }

    // Try to handle sneak name tag rename
    private static boolean tryHandleSneakNameTagRename(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getEntity() == null) {
            return false;
        }
        ItemStack held = evt.getItemStack();
        if (!(held.getItem() instanceof NameTagItem) || !held.has(DataComponents.CUSTOM_NAME)) {
            return false;
        }

        BlockPos pos = evt.getPos();
        BlockEntity be = evt.getLevel().getBlockEntity(pos);
        if (be == null) {
            return false;
        }

        ControllerDiscoveryKind discoveryKind = resolveRenameDiscoveryKind(be);
        if (discoveryKind == null) {
            return false;
        }
        if (discoveryKind != ControllerDiscoveryKind.REDSTONE_LINK
            && discoveryKind != ControllerDiscoveryKind.WHEEL_MOUNT
            && !evt.getEntity().isCrouching()) {
            return false;
        }

        Component customName = held.get(DataComponents.CUSTOM_NAME);
        String name = customName != null ? customName.getString() : null;
        if (!applyBlockEntityName(be, name)) {
            return false;
        }

        if (!evt.getEntity().isCreative()) {
            held.shrink(1);
        }
        evt.getEntity().displayClientMessage(Component.literal("Renamed to: " + (name == null ? "" : name)), true);
        evt.setCancellationResult(InteractionResult.SUCCESS);
        evt.setCanceled(true);
        return true;
    }

    // Resolve the rename discovery kind
    private static ControllerDiscoveryKind resolveRenameDiscoveryKind(BlockEntity be) {
        var node = ControllerDiscoveryService.classify(be, null, "world");
        if (node == null) {
            return null;
        }
        return node.kind() == ControllerDiscoveryKind.UNKNOWN ? null : node.kind();
    }

    // Apply the block entity name
    private static boolean applyBlockEntityName(BlockEntity be, String name) {
        String normalized = (name != null && !name.isBlank()) ? name.strip() : null;
        if (be instanceof INamedBlockEntity named) {
            named.setCustomName(normalized);
            be.setChanged();
            be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
            return true;
        }
        try {
            be.getClass().getMethod("setCustomName", String.class).invoke(be, normalized);
            be.setChanged();
            be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
            return true;
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            be.getClass().getMethod("setCustomName", Component.class)
                    .invoke(be, normalized == null ? Component.empty() : Component.literal(normalized));
            be.setChanged();
            be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
            return true;
        } catch (ReflectiveOperationException ignored) {
        }

        if (normalized == null) {
            be.getPersistentData().remove(ControllerDiscoveryService.CUSTOM_LABEL_PERSISTENT_KEY);
        } else {
            be.getPersistentData().putString(ControllerDiscoveryService.CUSTOM_LABEL_PERSISTENT_KEY, normalized);
        }
        be.setChanged();
        be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
        return true;
    }

    // Check if this is a disabled mod item
    private static boolean isDisabledModItem(ItemStack stack) {
        if (SupporterHeads.isSupporterHead(stack)) {
            return !CTFeatureToggles.isItemEnabled("player_mannequin");
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null
                && com.rieno.gadgetsandgizmos.CreateThrusters.MOD_ID.equals(itemId.getNamespace())
                && !CTFeatureToggles.isItemEnabled(itemId.getPath());
    }
}
