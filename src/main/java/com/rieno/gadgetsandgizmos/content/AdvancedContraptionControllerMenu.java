package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerGraphSnapshotPayload;
import com.rieno.gadgetsandgizmos.registry.CTMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

// Sync Advanced Contraption Controller settings
public class AdvancedContraptionControllerMenu extends AnalogueContraptionControllerMenu {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

<<<<<<< Updated upstream
    public static final int LINKER_SLOT_X = 122;
    public static final int LINKER_SLOT_Y = 44;
    public static final int GOGGLES_INPUT_SLOT_X = 78;
    public static final int GOGGLES_INPUT_SLOT_Y = 112;
    public static final int GOGGLES_OUTPUT_SLOT_X = 160;
    public static final int GOGGLES_OUTPUT_SLOT_Y = 112;
=======
    public static final int PLAYER_SLOTS_X = 50;
    public static final int PLAYER_SLOTS_Y = 232;
    public static final int GHOST_SLOT_OUTPUT_FIRST_X = 103;
    public static final int GHOST_SLOT_OUTPUT_SECOND_X = 121;
    public static final int GHOST_SLOTS_Y = 186;
    public static final int BIND_GOGGLES = 0;
    public static final int UNBIND_GOGGLES = 1;
    public static final int LINKER_MODAL_BASE_X = 42;
    public static final int LINKER_MODAL_BASE_Y = 95;
    public static final int LINKER_SLOT_X = 96;
    public static final int LINKER_SLOT_Y = 120;
    public static final int GOGGLES_INPUT_SLOT_X = 140;
    public static final int GOGGLES_INPUT_SLOT_Y = 148;
    public static final int GOGGLES_OUTPUT_SLOT_X = 185;
    public static final int GOGGLES_OUTPUT_SLOT_Y = 148;
>>>>>>> Stashed changes

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initial draft
    private AdvancedGraphDocument initialDraft;
    // Initial active
    private AdvancedGraphDocument initialActive;
<<<<<<< Updated upstream
    // Tracks whether tablet remote access is set
        // Initial hidden Schedule Scratch graphs
    private AdvancedGraphDocument initialShippingScheduleDraft;

    private AdvancedGraphDocument initialShippingScheduleActive;
private boolean tabletRemoteAccess;
=======
    private final com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagPublication scmPublication =
            new com.rieno.gadgetsandgizmos.lib.nbt.CompoundTagPublication();
    private boolean scmConfigurationWatching;
    private boolean scmPermissionsWatching;
    private long scmWatchTick = Long.MIN_VALUE;
    private boolean scmPreviewInitialized;
    private java.util.UUID scmPreviewRoot;
    // Initial hidden Schedule Scratch graphs
    private AdvancedGraphDocument initialShippingScheduleDraft;
    private AdvancedGraphDocument initialShippingScheduleActive;
    // Tracks whether tablet remote access is set
    private boolean tabletRemoteAccess;
    // Retain the server's zone independently of the editor client's operating system
    private String serverTimezone;
    private final TimedItemSlotAction gogglesAction = new TimedItemSlotAction();
    private long gogglesActionRequestId;
    // Keep unsaved execution private to this authenticated menu
    private com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDraftSession draftSession;
    private UUID draftSessionId;
    private long draftPublishedRevision = -1;
>>>>>>> Stashed changes

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller menu
    public AdvancedContraptionControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        super(CTMenuTypes.ADVANCED_CONTRAPTION_CONTROLLER.get(), id, inventory, data);
    }

    // Initialize the advanced contraption controller menu
    public AdvancedContraptionControllerMenu(MenuType<?> type, int id, Inventory inventory, RegistryFriendlyByteBuf data) {
        super(type, id, inventory, data);
    }

    // Initialize the advanced contraption controller menu
    public AdvancedContraptionControllerMenu(int id, Inventory inventory, AdvancedContraptionControllerBlockEntity blockEntity) {
        super(CTMenuTypes.ADVANCED_CONTRAPTION_CONTROLLER.get(), id, inventory, blockEntity);
        tabletRemoteAccess = DiagnosticTabletRemoteSessions.consumeMenuAuthorization(
                inventory.player, blockEntity.getBlockPos(),
                SimulatedHelper.getContainingSubLevelId(blockEntity));
        initialDraft = blockEntity.getDraftGraph();
        initialActive = blockEntity.getActiveGraph();
        initialShippingScheduleDraft = blockEntity.getShippingScheduleDraftGraph();
        initialShippingScheduleActive = blockEntity.getShippingScheduleActiveGraph();
    }

    // Initialize the advanced contraption controller menu
    public AdvancedContraptionControllerMenu(MenuType<?> type, int id, Inventory inventory,
                                             AdvancedContraptionControllerBlockEntity blockEntity) {
        super(type, id, inventory, blockEntity);
        tabletRemoteAccess = DiagnosticTabletRemoteSessions.consumeMenuAuthorization(
                inventory.player, blockEntity.getBlockPos(),
                SimulatedHelper.getContainingSubLevelId(blockEntity));
        initialDraft = blockEntity.getDraftGraph();
        initialActive = blockEntity.getActiveGraph();
        initialShippingScheduleDraft = blockEntity.getShippingScheduleDraftGraph();
        initialShippingScheduleActive = blockEntity.getShippingScheduleActiveGraph();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Read the extra open data
    @Override
    public void readExtraOpenData(RegistryFriendlyByteBuf buffer) {
        super.readExtraOpenData(buffer);
        int draftRevision = buffer.readVarInt();
        int activeRevision = buffer.readVarInt();
        AdvancedControllerGraphSnapshotPayload.GraphSnapshot snapshot =
                AdvancedControllerGraphSnapshotPayload.clientSnapshot(
                        getContentPos(), getContentSubLevelId(), draftRevision, activeRevision);
        initialDraft = AdvancedGraphDocument.fromTag(snapshot.draft());
        initialActive = AdvancedGraphDocument.fromTag(snapshot.active());
        initialShippingScheduleDraft = AdvancedGraphDocument.fromTag(snapshot.scheduleDraft());
        initialShippingScheduleActive = AdvancedGraphDocument.fromTag(snapshot.scheduleActive());
    }

    // Get the initial draft
    public AdvancedGraphDocument getInitialDraft() {
        return initialDraft == null ? new AdvancedGraphDocument() : initialDraft.copy();
    }

    // Get the initial active
    public AdvancedGraphDocument getInitialActive() {
        return initialActive == null ? new AdvancedGraphDocument() : initialActive.copy();
    }

    // Check if the still is valid
    @Override
    public boolean stillValid(Player player) {
        if (!tabletRemoteAccess) return super.stillValid(player);
        return contentHolder != null && contentHolder.getLevel() != null
                && !contentHolder.isRemoved()
                && contentHolder.getLevel().getBlockEntity(contentHolder.getBlockPos()) == contentHolder;
    }

<<<<<<< Updated upstream
=======
    // Keep player slots aligned with the advanced controller inventory panel
    @Override
    protected void addPlayerSlots(int x, int y){
        super.addPlayerSlots(PLAYER_SLOTS_X, PLAYER_SLOTS_Y);
    }

    // Align the frequency items with the painted claw panel while retaining their slot ids
    @Override
    protected SlotItemHandler createGhostSlot(int idx, int x, int y){
        if(idx < 2) return super.createGhostSlot(idx,
                idx == 0 ? GHOST_SLOT_OUTPUT_FIRST_X : GHOST_SLOT_OUTPUT_SECOND_X, GHOST_SLOTS_Y);
        return super.createGhostSlot(idx, x, y);
    }

>>>>>>> Stashed changes
    // Get the linker slot x
    @Override
    protected int linkerSlotX() {
        return LINKER_SLOT_X;
    }

    // Get the linker slot y
    @Override
    protected int linkerSlotY() {
        return LINKER_SLOT_Y;
    }

    // Get the goggles input slot x
    @Override
    protected int gogglesInputSlotX() {
        return GOGGLES_INPUT_SLOT_X;
    }

    // Get the goggles input slot y
    @Override
    protected int gogglesInputSlotY() {
        return GOGGLES_INPUT_SLOT_Y;
    }

    // Get the goggles output slot x
    @Override
    protected int gogglesOutputSlotX() {
        return GOGGLES_OUTPUT_SLOT_X;
    }

    // Get the goggles output slot y
    @Override
    protected int gogglesOutputSlotY() {
        return GOGGLES_OUTPUT_SLOT_Y;
    }

    // Get the menu config target block entity
    @Override
    public AdvancedContraptionControllerBlockEntity getMenuConfigTargetBlockEntity() {
        if (contentHolder == null && getContentPos() != null && playerInventory != null && playerInventory.player != null) {
            BlockEntity blockEntity = SimulatedHelper.findBlockEntity(
                    playerInventory.player.level(), getContentSubLevelId(), getContentPos());
            if (blockEntity instanceof AdvancedContraptionControllerBlockEntity controller) {
                contentHolder = controller;
            }
        }
        return contentHolder instanceof AdvancedContraptionControllerBlockEntity controller ? controller : null;
    }

<<<<<<< Updated upstream
=======
    // Accept only ordered edits to the current menu's independent draft
    public void editDraftSession(net.minecraft.server.level.ServerPlayer player,
            com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerDraftPayload payload){
        if(!java.util.Objects.equals(draftSessionId, payload.sessionId())){
            if(!payload.full() || payload.baseRevision() != -1) return;
            closeDraftSession();
            draftSessionId = payload.sessionId();
            draftSession = new com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDraftSession(
                    getMenuConfigTargetBlockEntity(), player.getUUID());
        }
        if(payload.patch().contains("DraftBinding")){
            if(payload.revision() == draftSession.revision()){
                draftSession.binding(payload.patch().getString("DraftBinding"), payload.patch().getBoolean("Active"));
            }
            return;
        }
        boolean accepted;
        try{ accepted = draftSession.edit(payload.baseRevision(), payload.revision(), payload.full(), payload.patch()); }
        catch(RuntimeException err){ accepted = false; }
        if(!accepted){
            PacketDistributor.sendToPlayer(player,
                    new com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerDraftRuntimePayload(
                            containerId, draftSessionId, draftSession.revision(), false, new CompoundTag()));
        }
    }

    // Execute drafts on the server tick even when no edits or saves arrive
    @Override
    public void broadcastChanges(){
        super.broadcastChanges();
        publishWatchedScmConfiguration();
        if(draftSession == null || !(playerInventory.player instanceof net.minecraft.server.level.ServerPlayer player)) return;
        var controller = getMenuConfigTargetBlockEntity();
        if(controller == null || !stillValid(player)
                || !ShipPermissions.allows(player, controller, com.rieno.gadgetsandgizmos.lib.scm.ShipPermission.ACC_GRAPH)){
            closeDraftSession();
            return;
        }
        draftSession.tick(player.serverLevel().getGameTime());
        var update = draftSession.changes();
        if(!update.reset() && update.events().isEmpty() && draftPublishedRevision == draftSession.revision()) return;
        PacketDistributor.sendToPlayer(player,
                new com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerDraftRuntimePayload(
                        containerId, draftSessionId, draftSession.revision(), true,
                        com.rieno.gadgetsandgizmos.lib.graph.GraphRuntimeNbt.write(update, AdvancedGraphDocument.Value::toTag)));
        draftPublishedRevision = draftSession.revision();
    }

    @Override
    public void removed(Player player){
        closeDraftSession();
        super.removed(player);
    }

    private void closeDraftSession(){
        if(draftSession != null) draftSession.close();
        draftSession = null;
        draftSessionId = null;
        draftPublishedRevision = -1;
    }

    // Recover a recreated SCM document cache with a fresh publication stream
    public void resetScmPublication(){ scmPublication.reset(); }

    public void watchScmConfiguration(boolean configuration, boolean permissions){
        scmConfigurationWatching = configuration;
        scmPermissionsWatching = permissions;
        scmWatchTick = Long.MIN_VALUE;
    }

    public void markScmPreview(java.util.UUID root){ scmPreviewRoot = root; scmPreviewInitialized = true; }

    // Keep watched state current without repeated client polling requests
    private void publishWatchedScmConfiguration(){
        if(!scmConfigurationWatching && !scmPermissionsWatching) return;
        if(!(playerInventory.player instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if(!(contentHolder instanceof AdvancedContraptionControllerBlockEntity controller)){
            watchScmConfiguration(false, false);
            return;
        }
        if(controller.isRemoved() || !stillValid(player) || !ShipPermissions.allows(player, controller,
                com.rieno.gadgetsandgizmos.lib.scm.ShipPermission.ACC_GRAPH)
                || !com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy.canAccessLocal(player, player.serverLevel(),
                com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi.containingId(controller), controller.getBlockPos())){
            watchScmConfiguration(false, false);
            return;
        }
        long now = player.serverLevel().getGameTime();
        if(scmWatchTick != Long.MIN_VALUE && now >= scmWatchTick && now - scmWatchTick < 10) return;
        scmWatchTick = now;
        boolean preview = scmConfigurationWatching && (!scmPreviewInitialized
                || !java.util.Objects.equals(scmPreviewRoot, controller.getScmConfigurationRootSubLevelId()));
        com.rieno.gadgetsandgizmos.neoforge.network.AdvancedContraptionControllerGraphPayload.sendScmConfiguration(
                player, com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget.of(getContentPos(), getContentSubLevelId()), controller, preview);
    }

    // Publish changed SCM fields for this menu
    public void publishScmConfiguration(net.minecraft.server.level.ServerPlayer player,
            com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget target, CompoundTag document, boolean acknowledge){
        var update = scmPublication.publish(document, acknowledge);
        if(update == null) return;
        PacketDistributor.sendToPlayer(player, new com.rieno.gadgetsandgizmos.neoforge.network.ScmConfigurationDeltaPayload(
                target, containerId, update.toTag()));
    }

    // ACC bindings change only through the two inventory actions
    @Override
    protected boolean automaticallyLinkGoggles(){ return false; }

    public boolean canBindGoggles(){
        return !gogglesAction.isActive() && getGogglesLinkProgress() == 0
                && PhysicsGogglesItem.isFunctionalGoggles(getGogglesInputStack()) && getGogglesOutputStack().isEmpty();
    }

    public boolean canUnbindGoggles(){
        return canBindGoggles() && !ItemBindingData.read(getGogglesInputStack(), GOGGLES_BIND_ROOT).isEmpty();
    }

    // Apply the action to the real input item on the owning server thread
    @Override
    public boolean clickMenuButton(Player player, int action){
        if(action != BIND_GOGGLES && action != UNBIND_GOGGLES) return super.clickMenuButton(player, action);
        return startGogglesAction(player, action, 0L);
    }

    public boolean startGogglesAction(Player player, int action, long requestId){
        if(action != BIND_GOGGLES && action != UNBIND_GOGGLES) return false;
        if(player == null || player.level().isClientSide || playerInventory.player != player || !stillValid(player)) return false;
        if(action == BIND_GOGGLES ? !canBindGoggles() : !canUnbindGoggles()) return false;
        if(getMenuConfigTargetBlockEntity() == null) return false;
        if(!gogglesAction.start(gogglesContainer(), 0, 1, action, getGogglesLinkDuration())) return false;
        gogglesActionRequestId = requestId;
        return true;
    }

    @Override
    protected void tickGogglesLinking(){
        if(playerInventory == null || playerInventory.player == null || playerInventory.player.level().isClientSide) return;
        Player player = playerInventory.player;
        if(gogglesAction == null || !gogglesAction.isActive()){
            setGogglesLinkProgress(0);
            return;
        }
        if(!stillValid(player)){
            gogglesAction.cancel();
            setGogglesLinkProgress(0);
            sendGogglesActionResult(player, false, "Binding cancelled: controller unavailable");
            return;
        }
        var state = gogglesAction.tick(gogglesContainer());
        setGogglesLinkProgress(gogglesAction.progress());
        if(state == TimedItemSlotAction.State.CANCELLED){
            sendGogglesActionResult(player, false, "Binding cancelled: input or output changed");
        }else if(state == TimedItemSlotAction.State.READY){
            int action = gogglesAction.action();
            String item = getGogglesInputStack().getItem() instanceof PilotCapItem ? "Pilot Hat" : "Smart Glasses";
            gogglesAction.cancel();
            boolean changed = finishGogglesAction(player, action);
            setGogglesLinkProgress(0);
            sendGogglesActionResult(player, changed, changed
                    ? (action == BIND_GOGGLES ? "Bound " : "Unbound ") + item : "Could not update headwear binding");
        }
    }

    private void sendGogglesActionResult(Player player, boolean success, String message){
        if(gogglesActionRequestId > 0 && player instanceof ServerPlayer serverPlayer){
            PacketDistributor.sendToPlayer(serverPlayer, new AdvancedControllerGraphActionResultPayload(
                    getContentPos(), getContentSubLevelId(), gogglesActionRequestId, success, message,
                    -1, false, false, java.util.List.of()));
        }
        gogglesActionRequestId = 0;
    }

    // Complete the action on the original input after the timer has finished
    private boolean finishGogglesAction(Player player, int action){
        AdvancedContraptionControllerBlockEntity controller = getMenuConfigTargetBlockEntity();
        if(controller == null) return false;
        boolean changed = ItemSlotActions.apply(gogglesContainer(), 0, 1,
                stack -> PhysicsGogglesItem.isFunctionalGoggles(stack)
                        && (action == BIND_GOGGLES || !ItemBindingData.read(stack, GOGGLES_BIND_ROOT).isEmpty()),
                stack -> {
                    CompoundTag prev = ItemBindingData.read(stack, GOGGLES_BIND_ROOT);
                    String prevLabel = prev.getString(GOGGLES_BIND_PAIR_LABEL);
                    if(action == UNBIND_GOGGLES){
                        ItemBindingData.clear(stack, GOGGLES_BIND_ROOT, prevLabel);
                    }else{
                        UUID pairId = UUID.randomUUID();
                        String label = controller.nextGogglesTrackerPairLabel(stack);
                        if(!bindGogglesToController(stack, getContentPos(), getContentSubLevelId(), pairId, label)) return ItemStack.EMPTY;
                        controller.registerGogglesTrackerPair(pairId, label, stack);
                        renameLinkedGoggles(stack, prevLabel, label);
                    }
                    unregisterPreviousGoggles(player, controller, prev);
                    return stack;
                });
        if(changed){
            if(this instanceof PortableAdvancedContraptionControllerMenu portable) portable.savePortableState();
        }
        return changed;
    }

    // Clear a loaded previous controller's roster; an unloaded owner cannot prevent item unbinding
    private void unregisterPreviousGoggles(Player player, AdvancedContraptionControllerBlockEntity current, CompoundTag prev){
        if(!prev.hasUUID(GOGGLES_BIND_PAIR_ID)) return;
        BlockPos pos = new BlockPos(prev.getInt(GOGGLES_BIND_X), prev.getInt(GOGGLES_BIND_Y), prev.getInt(GOGGLES_BIND_Z));
        UUID bodyId = prev.hasUUID(GOGGLES_BIND_SUBLEVEL) ? prev.getUUID(GOGGLES_BIND_SUBLEVEL) : null;
        BlockEntity owner;
        if(pos.equals(getContentPos()) && java.util.Objects.equals(bodyId, getContentSubLevelId())) owner = current;
        else if(bodyId != null) owner = SubLevelBlockEntityCollector.getBlockEntity(
                SubLevelBlockEntityCollector.getSubLevel(player.level(), bodyId), pos);
        else owner = player.level().isLoaded(pos) ? player.level().getBlockEntity(pos) : null;
        if(owner instanceof AdvancedContraptionControllerBlockEntity controller) controller.unregisterGogglesTrackerPair(prev.getUUID(GOGGLES_BIND_PAIR_ID));
    }

>>>>>>> Stashed changes
    // Save the data
    @Override
    protected void saveData(AnalogueContraptionControllerBlockEntity contentHolder) {
        if (contentHolder == null || contentHolder.getLevel() == null || contentHolder.getLevel().isClientSide || slots.isEmpty()) {
            return;
        }
        ItemStack linker = getCurrentLinkerStack();
        linker.setCount(linker.isEmpty() ? 0 : 1);
        if (!sameSingleItem(contentHolder.getStoredLinker(), linker)) {
            contentHolder.getLinkerSlotHandler().setStackInSlot(0, linker);
        }
    }



    // Get the initial Schedule Scratch graph draft
    public AdvancedGraphDocument getInitialShippingScheduleDraft() {
        return initialShippingScheduleDraft == null
                ? new AdvancedGraphDocument() : initialShippingScheduleDraft.copy();
    }


    // Get the initial active Schedule Scratch graph
    public AdvancedGraphDocument getInitialShippingScheduleActive() {
        return initialShippingScheduleActive == null
                ? new AdvancedGraphDocument() : initialShippingScheduleActive.copy();
    }
}
