package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.SubLevelBlockEntityCollector;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Resolve the seated pilot assigned to a shipping schedule without scanning unrelated entities
public final class ShippingSchedulePilot {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String CREATE_TRAIN_HAT_TAG = "TrainHat";
    private static final String SHIPPING_PILOT_TRAIN_HAT_TAG =
            "CreateThrustersShippingPilotTrainHat";
    private static final String SHIPPING_PILOT_TAG = "CreateThrustersShippingPilot";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the shipping schedule pilot
    private ShippingSchedulePilot() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Try to interact
    public static boolean tryInteract(PlayerInteractEvent.EntityInteractSpecific evt) {
        return tryInteract(evt, evt.getTarget());
    }

    // Try to interact
    public static boolean tryInteract(PlayerInteractEvent.EntityInteract evt) {
        return tryInteract(evt, evt.getTarget());
    }

    // Try to interact
    public static boolean tryInteract(PlayerInteractEvent.RightClickBlock evt) {
        if (evt.getHand() != InteractionHand.MAIN_HAND) {
            return false;
        }
        ItemStack held = evt.getItemStack();
        if (!held.isEmpty() && !(held.getItem() instanceof ShippingScheduleItem)) {
            return false;
        }
        if (evt.getLevel().getBlockState(evt.getPos()).getBlock() instanceof BlazeBurnerBlock) {
            return tryBlazeBurnerInteraction(evt);
        }
        if (!(evt.getLevel().getBlockState(evt.getPos()).getBlock() instanceof SeatBlock)) {
            return false;
        }
        for (SeatEntity seat : evt.getLevel().getEntitiesOfClass(
                SeatEntity.class, new AABB(evt.getPos()).inflate(0.125D))) {
            for (Entity passenger : seat.getPassengers()) {
                if (passenger instanceof LivingEntity pilot
                        && tryInteract(evt, pilot)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Try to interact
    private static boolean tryInteract(PlayerInteractEvent evt, Entity target) {
        LivingEntity pilot = resolvePilot(target);
        if (evt.getHand() != InteractionHand.MAIN_HAND || pilot == null
                || !(pilot.getVehicle() instanceof SeatEntity seat)) {
            return false;
        }
        ItemStack held = evt.getItemStack();
        boolean assigning = held.getItem() instanceof ShippingScheduleItem;
        boolean removing = held.isEmpty() && pilot.getMainHandItem().getItem() instanceof ShippingScheduleItem;
        if (!assigning && !removing) {
            return false;
        }
        AdvancedContraptionControllerBlockEntity controller = findController(pilot, seat);
        if (evt.getLevel().isClientSide) {
            finish(evt, controller == null ? InteractionResult.FAIL : InteractionResult.SUCCESS);
            return true;
        }
        if (!(evt.getEntity() instanceof ServerPlayer player) || controller == null) {
            if (evt.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable(
                        "createthrusters.shipping_schedule.pilot.no_controller").withStyle(ChatFormatting.RED), true);
            }
            finish(evt, InteractionResult.FAIL);
            return true;
        }
        if (!controller.hasShipControlModule()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.no_module").withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return true;
        }
        if (assigning && !controller.getShipControlGraphValue("ready").asBoolean()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.needs_initialization")
                    .withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return true;
        }

        if (assigning) {
            assign(evt, player, pilot, controller, held);
        } else {
            remove(evt, player, pilot, controller);
        }
        return true;
    }

    // Resolve the pilot
    private static @org.jetbrains.annotations.Nullable LivingEntity resolvePilot(
            Entity target
    ) {
        if (target instanceof LivingEntity living) {
            return living;
        }
        if (target instanceof SeatEntity seat) {
            for (Entity passenger : seat.getPassengers()) {
                if (passenger instanceof LivingEntity living) {
                    return living;
                }
            }
        }
        return null;
    }

    // Assign the shipping schedule pilot
    private static void assign(PlayerInteractEvent evt, ServerPlayer player,
                               LivingEntity pilot, AdvancedContraptionControllerBlockEntity controller,
                               ItemStack held) {
        Schedule schedule = ScheduleItem.getSchedule(player.registryAccess(), held);
        if (schedule == null || schedule.entries.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.no_stops").withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return;
        }
        if (controller.hasShippingSchedule() && controller.hasActiveShippingSchedulePilot()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.already_running").withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return;
        }
        ItemStack pilotHand = pilot.getMainHandItem();
        if (!pilotHand.isEmpty() && !(pilotHand.getItem() instanceof ShippingScheduleItem)) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.hands_full").withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return;
        }
        if (!controller.installShippingSchedule(schedule, pilot.getUUID(),
                ShippingAutoRefuelSettings.fromSchedule(schedule))) {
            finish(evt, InteractionResult.FAIL);
            return;
        }
        ItemStack displayCopy = held.copyWithCount(1);
        ShippingScheduleRouteData.bind(displayCopy, controller.getScmPersistenceId());
        pilot.setItemSlot(EquipmentSlot.MAINHAND, displayCopy);
        pilot.getPersistentData().putBoolean(SHIPPING_PILOT_TAG, true);
        prepareTrainHat(player, pilot);
        faceController(pilot, controller);
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
        player.displayClientMessage(Component.translatable(
                "createthrusters.shipping_schedule.pilot.assigned", pilot.getDisplayName())
                .withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6F, 1.2F);
        finish(evt, InteractionResult.SUCCESS);
    }

    // Remove the shipping schedule pilot
    private static void remove(PlayerInteractEvent evt, ServerPlayer player,
                               LivingEntity pilot, AdvancedContraptionControllerBlockEntity controller) {
        if (controller.getShippingSchedulePilotId() != null
                && !controller.getShippingSchedulePilotId().equals(pilot.getUUID())) {
            finish(evt, InteractionResult.FAIL);
            return;
        }
        ItemStack returned = controller.hasShippingSchedule()
                ? controller.removeShippingSchedule() : pilot.getMainHandItem().copyWithCount(1);
        pilot.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        clearTrainHat(pilot);
        if (!returned.isEmpty() && !player.getInventory().add(returned)) {
            player.drop(returned, false);
        }
        player.displayClientMessage(Component.translatable(
                "createthrusters.shipping_schedule.pilot.removed").withStyle(ChatFormatting.YELLOW), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.PLAYERS, 0.6F, 1.0F);
        finish(evt, InteractionResult.SUCCESS);
    }

    // Try to interact with a blaze burner pilot
    private static boolean tryBlazeBurnerInteraction(PlayerInteractEvent.RightClickBlock evt) {
        AdvancedContraptionControllerBlockEntity controller = findBlazeBurnerController(
                evt.getLevel(), evt.getPos());
        if (controller == null) {
            return false;
        }
        if (evt.getLevel().isClientSide) {
            finish(evt, InteractionResult.SUCCESS);
            return true;
        }
        if (!(evt.getEntity() instanceof ServerPlayer player)) {
            finish(evt, InteractionResult.FAIL);
            return true;
        }
        if (!controller.hasShipControlModule()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.no_module").withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return true;
        }
        ItemStack held = evt.getItemStack();
        if (!held.isEmpty()) {
            assignBlazeBurner(evt, player, controller, held);
            return true;
        }
        if (!controller.hasBlazeBurnerShippingPilot()) {
            return false;
        }
        removeBlazeBurner(evt, player, controller);
        return true;
    }

    // Assign the blaze burner shipping pilot
    private static void assignBlazeBurner(PlayerInteractEvent evt, ServerPlayer player,
                                          AdvancedContraptionControllerBlockEntity controller,
                                          ItemStack held) {
        Schedule schedule = ScheduleItem.getSchedule(player.registryAccess(), held);
        if (schedule == null || schedule.entries.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.no_stops").withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return;
        }
        if (controller.hasShippingSchedule() && controller.hasActiveShippingSchedulePilot()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.already_running").withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return;
        }
        if (!controller.getShipControlGraphValue("ready").asBoolean()) {
            player.displayClientMessage(Component.translatable(
                    "createthrusters.shipping_schedule.pilot.needs_initialization")
                    .withStyle(ChatFormatting.RED), true);
            finish(evt, InteractionResult.FAIL);
            return;
        }
        if (!controller.installShippingSchedule(schedule, UUID.randomUUID(),
                ShippingAutoRefuelSettings.fromSchedule(schedule), true)) {
            finish(evt, InteractionResult.FAIL);
            return;
        }
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
        player.displayClientMessage(Component.translatable(
                "createthrusters.shipping_schedule.pilot.assigned",
                evt.getLevel().getBlockState(evt.getPos()).getBlock().getName())
                .withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6F, 1.2F);
        finish(evt, InteractionResult.SUCCESS);
    }

    // Remove the blaze burner shipping pilot
    private static void removeBlazeBurner(PlayerInteractEvent evt, ServerPlayer player,
                                          AdvancedContraptionControllerBlockEntity controller) {
        ItemStack returned = controller.removeShippingSchedule();
        if (!returned.isEmpty() && !player.getInventory().add(returned)) {
            player.drop(returned, false);
        }
        player.displayClientMessage(Component.translatable(
                "createthrusters.shipping_schedule.pilot.removed").withStyle(ChatFormatting.YELLOW), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.PLAYERS, 0.6F, 1.0F);
        finish(evt, InteractionResult.SUCCESS);
    }

    // Find the controller
    private static AdvancedContraptionControllerBlockEntity findController(LivingEntity pilot, SeatEntity seat) {
        Object subLevel = SimulatedHelper.getEntityTrackingSubLevel(pilot);
        List<BlockEntity> candidates = new ArrayList<>();
        if (subLevel != null) {
            candidates.addAll(SubLevelBlockEntityCollector.getBlockEntities(subLevel));
        } else {
            BlockPos center = seat.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
                BlockEntity blockEntity = pilot.level().getBlockEntity(pos);
                if (blockEntity != null) {
                    candidates.add(blockEntity);
                }
            }
        }
        BlockPos seatPos = seat.blockPosition();
        AdvancedContraptionControllerBlockEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (BlockEntity blockEntity : candidates) {
            if (!(blockEntity instanceof AdvancedContraptionControllerBlockEntity controller)) {
                continue;
            }
            Direction facing = controller.getBlockState().hasProperty(
                    AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                    ? controller.getBlockState().getValue(AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                    : Direction.NORTH;
            BlockPos frontSeat = controller.getBlockPos().relative(facing);
            BlockPos oppositeSeat = controller.getBlockPos().relative(facing.getOpposite());
            double distance = Math.min(frontSeat.distSqr(seatPos), oppositeSeat.distSqr(seatPos));
            if (distance <= 2.0D && distance < closestDistance) {
                closest = controller;
                closestDistance = distance;
            }
        }
        return closest;
    }

    // Find the blaze burner controller
    private static @org.jetbrains.annotations.Nullable AdvancedContraptionControllerBlockEntity
    findBlazeBurnerController(Level level, BlockPos burnerPos) {
        BlockEntity burner = level.getBlockEntity(burnerPos);
        UUID subLevelId = burner == null ? null : SimulatedHelper.getContainingSubLevelId(burner);
        Object subLevel = subLevelId == null ? null
                : SubLevelBlockEntityCollector.getSubLevel(level, subLevelId);
        if (subLevel != null) {
            for (BlockEntity candidate : SubLevelBlockEntityCollector.getBlockEntities(subLevel)) {
                if (candidate instanceof AdvancedContraptionControllerBlockEntity controller
                        && isBlazeBurnerPilotAt(controller, burnerPos)) {
                    return controller;
                }
            }
            return null;
        }
        for (BlockPos pos : BlockPos.betweenClosed(
                burnerPos.offset(-1, -1, -1), burnerPos.offset(1, 1, 1))) {
            BlockEntity candidate = level.getBlockEntity(pos);
            if (candidate instanceof AdvancedContraptionControllerBlockEntity controller
                    && isBlazeBurnerPilotAt(controller, burnerPos)) {
                return controller;
            }
        }
        return null;
    }

    // Check if the blaze burner is the pilot
    public static boolean isBlazeBurnerPilot(AdvancedContraptionControllerBlockEntity controller) {
        if (controller == null || controller.getLevel() == null) {
            return false;
        }
        BlockPos burnerPos = controller.getBlockPos().relative(controllerFacing(controller));
        return controller.getLevel().getBlockState(burnerPos).getBlock() instanceof BlazeBurnerBlock;
    }

    // Resolve an adjacent seated pilot for the ACC Schedule workspace. This deliberately does not require a held item:
    // new schedule graphs are allowed to start a shipping route without creating a shipping schedule item.
    public static @org.jetbrains.annotations.Nullable LivingEntity findAdjacentSeatedPilot(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        if (controller == null || controller.getLevel() == null) {
            return null;
        }
        AABB bounds = new AABB(controller.getBlockPos()).inflate(2.0D);
        for (SeatEntity seat : controller.getLevel().getEntitiesOfClass(SeatEntity.class, bounds)) {
            for (Entity passenger : seat.getPassengers()) {
                if (passenger instanceof LivingEntity pilot && isSeatedAtController(pilot, controller)) {
                    return pilot;
                }
            }
        }
        return null;
    }

    // Check whether the controller has a currently valid Schedule workspace pilot.
    public static boolean hasScheduleWorkspacePilot(AdvancedContraptionControllerBlockEntity controller) {
        return isBlazeBurnerPilot(controller) || findAdjacentSeatedPilot(controller) != null;
    }

    // Read the legacy item held by the adjacent seated pilot. This fallback is
    // deliberately read-only: it lets an ACC surface an already-running old
    // schedule even when its runtime graph cache predates the Scratch feature.
    public static @org.jetbrains.annotations.Nullable Schedule adjacentPilotSchedule(
            AdvancedContraptionControllerBlockEntity controller
    ) {
        if (controller == null || controller.getLevel() == null) return null;
        LivingEntity pilot = findAdjacentSeatedPilot(controller);
        if (pilot == null) return null;
        ItemStack stack = pilot.getMainHandItem();
        return stack.getItem() instanceof ShippingScheduleItem
                ? ScheduleItem.getSchedule(controller.getLevel().registryAccess(), stack) : null;
    }

    // Write an SCM-owned schedule to the adjacent pilot's already-held item.
    // This is intentionally an explicit item operation: it does not create an
    // item, install a runtime, or alter a controller-owned schedule.
    public static boolean writeAdjacentPilotSchedule(AdvancedContraptionControllerBlockEntity controller,
                                                     Schedule schedule) {
        if (controller == null || schedule == null || controller.getLevel() == null) return false;
        LivingEntity pilot = findAdjacentSeatedPilot(controller);
        if (pilot == null) return false;
        ItemStack held = pilot.getMainHandItem();
        if (!(held.getItem() instanceof ShippingScheduleItem)) return false;
        held.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(controller.getLevel().registryAccess()));
        ShippingAutoRefuelSettings.write(held, ShippingAutoRefuelSettings.fromSchedule(schedule));
        pilot.setItemSlot(EquipmentSlot.MAINHAND, held);
        return true;
    }

    // Update the legacy physical schedule item when a paired ACC Scratch graph changes.
    public static void syncPairedScheduleItem(AdvancedContraptionControllerBlockEntity controller,
                                              Schedule schedule) {
        if (controller == null || schedule == null || controller.getLevel() == null
                || controller.getLevel().getServer() == null) {
            return;
        }
        UUID pilotId = controller.getShippingSchedulePilotId();
        if (pilotId == null || controller.hasBlazeBurnerShippingPilot()) {
            return;
        }
        for (ServerLevel level : controller.getLevel().getServer().getAllLevels()) {
            Entity entity = level.getEntity(pilotId);
            if (!(entity instanceof LivingEntity pilot)) continue;
            ItemStack paired = pilot.getMainHandItem();
            if (!(paired.getItem() instanceof ShippingScheduleItem)) {
                return;
            }
            paired.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(level.registryAccess()));
            ShippingAutoRefuelSettings.write(paired, ShippingAutoRefuelSettings.fromSchedule(schedule));
            pilot.setItemSlot(EquipmentSlot.MAINHAND, paired);
            return;
        }
    }

    // Check if this burner belongs to the controller
    private static boolean isBlazeBurnerPilotAt(AdvancedContraptionControllerBlockEntity controller,
                                                BlockPos burnerPos) {
        return isBlazeBurnerPilot(controller)
                && controller.getBlockPos().relative(controllerFacing(controller)).equals(burnerPos);
    }

    // Get the controller facing
    private static Direction controllerFacing(AdvancedContraptionControllerBlockEntity controller) {
        return controller.getBlockState().hasProperty(
                AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                ? controller.getBlockState().getValue(
                AnalogueContraptionControllerBlock.HORIZONTAL_FACING) : Direction.NORTH;
    }

    // Check if this is ship controller seat
    public static boolean isShipControllerSeat(LivingEntity passenger, SeatEntity seat) {
        AdvancedContraptionControllerBlockEntity controller = findController(passenger, seat);
        return controller != null && controller.hasShipControlModule();
    }

    // Check if the seated is at the controller
    public static boolean isSeatedAtController(
            LivingEntity pilot,
            AdvancedContraptionControllerBlockEntity controller
    ) {
        if (pilot == null || controller == null || !pilot.isAlive()
                || !(pilot.getVehicle() instanceof SeatEntity seat)) {
            return false;
        }
        Object pilotSubLevel = SimulatedHelper.getEntityTrackingSubLevel(pilot);
        if (!Objects.equals(
                SimulatedHelper.getSubLevelId(pilotSubLevel),
                SimulatedHelper.getContainingSubLevelId(controller))) {
            return false;
        }
        Direction facing = controller.getBlockState().hasProperty(
                AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                ? controller.getBlockState().getValue(
                        AnalogueContraptionControllerBlock.HORIZONTAL_FACING)
                : Direction.NORTH;
        BlockPos seatPos = seat.blockPosition();
        boolean seated = Math.min(
                controller.getBlockPos().relative(facing).distSqr(seatPos),
                controller.getBlockPos().relative(facing.getOpposite()).distSqr(seatPos))
                <= 2.0D;
        if (seated && !pilot.level().isClientSide) {
            ensureTrainHat(pilot);
        }
        return seated;
    }

    // Prepare the train hat
    private static void prepareTrainHat(ServerPlayer player, LivingEntity pilot) {
        ItemStack prev = pilot.getItemBySlot(EquipmentSlot.HEAD);
        if (!prev.isEmpty()) {
            ItemStack returned = prev.copy();
            if (!player.getInventory().add(returned)) {
                player.drop(returned, false);
            }
        }
        pilot.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ensureTrainHat(pilot);
    }

    // Face the controller
    private static void faceController(
            LivingEntity pilot,
            AdvancedContraptionControllerBlockEntity controller
    ) {
        double dx = controller.getBlockPos().getX() + 0.5D - pilot.getX();
        double dy = controller.getBlockPos().getY() + 0.5D
                - pilot.getEyeY();
        double dz = controller.getBlockPos().getZ() + 0.5D - pilot.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        pilot.setYRot(yaw);
        pilot.setYHeadRot(yaw);
        pilot.setYBodyRot(yaw);
        pilot.setXRot(pitch);
    }

    // Ensure the train hat
    static void ensureTrainHat(LivingEntity pilot) {
        if (pilot == null || !pilot.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return;
        }
        if (!pilot.getPersistentData().contains(CREATE_TRAIN_HAT_TAG)) {
            pilot.getPersistentData().putBoolean(CREATE_TRAIN_HAT_TAG, true);
            pilot.getPersistentData().putBoolean(SHIPPING_PILOT_TRAIN_HAT_TAG, true);
        }
    }

    // Clear the train hat
    static void clearTrainHat(LivingEntity pilot) {
        if (pilot == null) {
            return;
        }
        if (pilot.getPersistentData().getBoolean(SHIPPING_PILOT_TRAIN_HAT_TAG)) {
            pilot.getPersistentData().remove(CREATE_TRAIN_HAT_TAG);
        }
        pilot.getPersistentData().remove(SHIPPING_PILOT_TRAIN_HAT_TAG);
        pilot.getPersistentData().remove(SHIPPING_PILOT_TAG);
    }

    // Check if this is pilot
    public static boolean isPilot(LivingEntity entity) {
        return entity != null && entity.getPersistentData().getBoolean(SHIPPING_PILOT_TAG);
    }

    // Finish the shipping schedule pilot
    private static void finish(PlayerInteractEvent evt, InteractionResult res) {
        if (evt instanceof PlayerInteractEvent.EntityInteractSpecific specific) {
            specific.setCancellationResult(res);
            specific.setCanceled(true);
        } else if (evt instanceof PlayerInteractEvent.EntityInteract general) {
            general.setCancellationResult(res);
            general.setCanceled(true);
        } else if (evt instanceof PlayerInteractEvent.RightClickBlock block) {
            block.setCancellationResult(res);
            block.setCanceled(true);
        }
    }
}
