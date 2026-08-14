package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractions;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionHandler;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletAppSnapshotPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletRemoteOpenPayload;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.UUID;

// Implement the tablet's remote display page and controller-session actions
final class DiagnosticTabletRdpApp {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet rdp app
    private DiagnosticTabletRdpApp() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Run the diagnostic tablet rdp app
    static TabletActionHandler.Result execute(TabletActionContext ctx, TabletAction action,
                                              AdvancedContraptionControllerBlockEntity controller) {
        // -----------------------------------------------------ACTION SETUP-----------------------------------------------------
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return failure("The tablet identity is not available");
        String val = action.arguments().getOrDefault("value", "").strip();
        // ------------------------------------CONTROLLER LIST------------------------------------
        if ("remove_controller".equals(action.actionId())) {
            DiagnosticTabletAppStorage.removeBinding(ctx.player().server, tabletId,
                    DiagnosticTabletData.appId("rdp"), val);
            sendSnapshot(ctx, snapshot(ctx));
            return quietSuccess();
        }
        if ("rename_controller".equals(action.actionId())) {
            int separator = val.indexOf('|');
            if (separator <= 0 || !DiagnosticTabletAppStorage.renameBinding(
                    ctx.player().server, tabletId, DiagnosticTabletData.appId("rdp"),
                    val.substring(0, separator), val.substring(separator + 1))) {
                return failure("Enter a name for the paired controller");
            }
            sendSnapshot(ctx, snapshot(ctx));
            return quietSuccess();
        }
        if ("refresh".equals(action.actionId()) || "select".equals(action.actionId())) {
            sendSnapshot(ctx, snapshot(ctx));
            return quietSuccess();
        }
        // -----------------------------------------------------REMOTE TARGET-----------------------------------------------------
        BlockEntity selectedTarget = selectedTarget(ctx, val);
        if ("remote_control".equals(action.actionId()) && isWirelessComputer(selectedTarget)) {
            DiagnosticTabletRemoteSessions.authorize(ctx.player(), selectedTarget);
            if (!openWirelessComputer(ctx.player(), selectedTarget)) {
                return failure("That ComputerCraft computer is no longer available");
            }
            return quietSuccess();
        }
        controller = selectedController(ctx, val);
        if (controller == null) {
            sendSnapshot(ctx, snapshot(ctx));
            return failure("Select a loaded Advanced Controller first");
        }
        if ("remote_control".equals(action.actionId())) {
            DiagnosticTabletRemoteSessions.authorize(ctx.player(), controller);
            PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletRemoteOpenPayload(
                    "interact", controller.getBlockPos(),
                    SimulatedHelper.getContainingSubLevelId(controller)));
            return quietSuccess();
        }
        if ("open_graph".equals(action.actionId()) || "open_plotter".equals(action.actionId())) {
            DiagnosticTabletRemoteSessions.authorize(ctx.player(), controller);
            ctx.player().openMenu(controller, controller::sendToMenu);
            PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletRemoteOpenPayload(
                    "open_plotter".equals(action.actionId()) ? "plotter" : "graph",
                    controller.getBlockPos(), SimulatedHelper.getContainingSubLevelId(controller)));
            return quietSuccess();
        }
        // -----------------------------------------------------WIDGET CHECKS-----------------------------------------------------
        if (!action.actionId().startsWith("widget:")) {
            return failure("Unknown RDP action");
        }

        String[] parts = action.actionId().split(":", 3);
        if (parts.length < 2 || parts[1].isBlank()) {
            return failure("Invalid RDP widget");
        }
        AdvancedGraphDocument.Node node = controller.getActiveGraph().nodes().stream()
                .filter(candidate -> parts[1].equals(candidate.id()) && isWidget(candidate))
                .findFirst().orElse(null);
        if (node == null) {
            return failure("The RDP widget is no longer available");
        }
        CompoundTag elm = interactiveElement(node, parts.length == 3 ? parts[2] : "");
        if (elm == null) {
            return failure("The RDP control is no longer available");
        }
        String interactionId = elm.getString("InteractionId");
        String type = elm.getString("Type");
        String requested = action.arguments().getOrDefault("value", "");
        Map<String, AdvancedGraphDocument.Value> inputs = controller.getAccDisplayRuntimeInputsSnapshot();
        Map<String, AdvancedGraphDocument.Value> outputs = controller.getAccDisplayRuntimeOutputsSnapshot();
        java.util.function.Function<String, AdvancedGraphDocument.Value> values = port ->
                inputs.getOrDefault(node.id() + ":" + port,
                        outputs.getOrDefault(node.id() + ":" + port,
                                AdvancedGraphDocument.Value.number(0.0D)));
        CompoundTag resolved = AdvancedHudElementBinding.resolvedCopy(elm, values);

        // -----------------------------------------------------WIDGET ACTION-----------------------------------------------------
        switch (type) {
            case "button" -> {
                controller.handleHudInteraction(node.id(), interactionId,
                        AdvancedGraphDocument.Value.bool(true));
                controller.handleHudInteraction(node.id(), interactionId,
                        AdvancedGraphDocument.Value.bool(false));
            }
            case "toggle" -> {
                String valuePort = elm.getString("ValuePort");
                boolean current = !valuePort.isBlank() && values.apply(valuePort).asBoolean();
                controller.handleHudInteraction(node.id(), interactionId,
                        AdvancedGraphDocument.Value.bool(!current));
            }
            case "slider" -> {
                double minimum = resolved.getDouble("Min");
                double maximum = resolved.getDouble("Max");
                if (!(maximum > minimum)) maximum = minimum + 1.0D;
                double next;
                try {
                    next = Double.parseDouble(requested);
                } catch (NumberFormatException ignored) {
                    return failure("Enter a numeric slider value");
                }
                double step = resolved.getDouble("Step");
                if (step > 0.0D && Double.isFinite(step)) {
                    next = minimum + Math.round((next - minimum) / step) * step;
                }
                controller.handleHudInteraction(node.id(), interactionId,
                        AdvancedGraphDocument.Value.number(Mth.clamp(next, minimum, maximum)));
            }
            case "text_input" -> {
                String textValue = requested == null ? "" : requested.strip();
                if (textValue.length() > 64) textValue = textValue.substring(0, 64);
                controller.handleHudInteraction(node.id(), interactionId,
                        AdvancedGraphDocument.Value.string(textValue));
            }
            default -> {
                return failure("This RDP element is read-only");
            }
        }
        sendSnapshot(ctx, snapshot(ctx));
        return quietSuccess();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the selected controller
    private static AdvancedContraptionControllerBlockEntity selectedController(
            TabletActionContext ctx, String requestedKey) {
        BlockEntity target = selectedTarget(ctx, requestedKey);
        return target instanceof AdvancedContraptionControllerBlockEntity advanced ? advanced : null;
    }

    // Get the selected target
    private static BlockEntity selectedTarget(TabletActionContext ctx, String requestedKey) {
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return null;
        if (requestedKey != null && !requestedKey.isBlank()) {
            for (DiagnosticTabletData.Binding binding : DiagnosticTabletAppStorage.bindings(
                    ctx.player().server, tabletId, DiagnosticTabletData.appId("rdp"))) {
                if (requestedKey.equals(DiagnosticTabletAppStorage.key(binding))) {
                    return SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                            binding.subLevelId(), binding.pos());
                }
            }
            return null;
        }
        DiagnosticTabletData.Binding binding = DiagnosticTabletAppStorage.selectedBinding(
                ctx.player().server, tabletId, DiagnosticTabletData.appId("rdp"));
        if (binding == null) return null;
        return SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                binding.subLevelId(), binding.pos());
    }

    // Get the snapshot
    static CompoundTag snapshot(TabletActionContext ctx) {
        CompoundTag root = new CompoundTag();
        UUID tabletId = ctx.sourceTabletId();
        if (tabletId == null) return root;
        DiagnosticTabletData.Binding selected = DiagnosticTabletAppStorage.selectedBinding(
                ctx.player().server, tabletId, DiagnosticTabletData.appId("rdp"));
        ListTag devices = new ListTag();
        for (DiagnosticTabletData.Binding binding : DiagnosticTabletAppStorage.bindings(
                ctx.player().server, tabletId, DiagnosticTabletData.appId("rdp"))) {
            CompoundTag device = new CompoundTag();
            String key = DiagnosticTabletAppStorage.key(binding);
            device.putString("Key", key);
            device.putString("Name", binding.label());
            device.putString("Type", binding.type());
            device.putBoolean("Selected", selected != null
                    && DiagnosticTabletAppStorage.key(selected).equals(key));
            BlockEntity target = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(),
                    binding.subLevelId(), binding.pos());
            device.putBoolean("Online", target instanceof AdvancedContraptionControllerBlockEntity
                    || isWirelessComputer(target));
            devices.add(device);
        }
        root.put("Devices", devices);
        root.put("Settings", DiagnosticTabletAppStorage.data(ctx.player().server,
                tabletId, DiagnosticTabletData.appId("rdp")).getCompound("Settings").copy());
        AdvancedContraptionControllerBlockEntity controller = selectedController(ctx, "");
        if (controller != null) root.merge(snapshot(controller));
        else root.merge(disconnectedSnapshot());
        return root;
    }

    // Get the snapshot
    static CompoundTag snapshot(AdvancedContraptionControllerBlockEntity controller) {
        CompoundTag root = new CompoundTag();
        root.putBoolean("Connected", true);
        root.putString("Controller", controller.getDisplayName().getString());
        root.putInt("GraphRevision", controller.getActiveGraphRevision());
        Map<String, AdvancedGraphDocument.Value> inputs = controller.getAccDisplayRuntimeInputsSnapshot();
        Map<String, AdvancedGraphDocument.Value> outputs = controller.getAccDisplayRuntimeOutputsSnapshot();
        ListTag widgets = new ListTag();
        for (AdvancedGraphDocument.Node node : controller.getActiveGraph().nodes()) {
            if (!isWidget(node)) continue;
            if (widgets.size() >= 64) break;
            CompoundTag widget = new CompoundTag();
            widget.putString("Id", node.id());
            String label = node.data().getString("Label");
            widget.putString("Label", label.isBlank() ? "Remote display" : label);
            widget.putInt("Width", Math.max(1, node.data().getInt("WidgetWidth")));
            widget.putInt("Height", Math.max(1, node.data().getInt("WidgetHeight")));
            ListTag resolvedElements = new ListTag();
            ListTag elements = node.data().getList(AdvancedHudInteractions.ELEMENTS, Tag.TAG_COMPOUND);
            for (int idx = 0; idx < Math.min(64, elements.size()); idx++) {
                CompoundTag elm = elements.getCompound(idx);
                CompoundTag resolved = AdvancedHudElementBinding.resolvedCopy(elm, port ->
                        inputs.getOrDefault(node.id() + ":" + port,
                                outputs.getOrDefault(node.id() + ":" + port,
                                        AdvancedGraphDocument.Value.number(0.0D))));
                String valuePort = elm.getString("ValuePort");
                if (!valuePort.isBlank()) {
                    AdvancedGraphDocument.Value val = inputs.getOrDefault(
                            node.id() + ":" + valuePort,
                            outputs.getOrDefault(node.id() + ":" + valuePort,
                                    AdvancedGraphDocument.Value.number(0.0D)));
                    resolved.putString("CurrentValue", val.asString());
                    resolved.putBoolean("CurrentBoolean", val.asBoolean());
                    resolved.putDouble("CurrentNumber", val.asNumber());
                }
                resolvedElements.add(resolved);
            }
            widget.put("Elements", resolvedElements);
            widgets.add(widget);
        }
        root.put("Widgets", widgets);
        return root;
    }

    // Get the disconnected snapshot
    private static CompoundTag disconnectedSnapshot() {
        CompoundTag root = new CompoundTag();
        root.putBoolean("Connected", false);
        root.put("Widgets", new ListTag());
        return root;
    }

    // Check if this is a widget
    private static boolean isWidget(AdvancedGraphDocument.Node node) {
        return node != null && ("acc_display_widget".equals(node.type())
                || "acc_hologram_widget".equals(node.type())
                || "advanced_hud_element".equals(node.type()));
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

    // Check if this is open wireless computer
    private static boolean openWirelessComputer(net.minecraft.server.level.ServerPlayer player,
                                                BlockEntity target) {
        try {
            Class<?> remoteDesktop = Class.forName(
                    "com.rieno.gadgetsandgizmos.compat.computercraft.ComputerCraftRemoteDesktop");
            Object opened = remoteDesktop.getMethod("open", net.minecraft.server.level.ServerPlayer.class,
                    BlockEntity.class).invoke(null, player, target);
            return opened instanceof Boolean val && val;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    // Get the interactive element
    private static CompoundTag interactiveElement(AdvancedGraphDocument.Node node,
                                                  String interactionId) {
        ListTag elements = node.data().getList(AdvancedHudInteractions.ELEMENTS, Tag.TAG_COMPOUND);
        for (int idx = 0; idx < elements.size(); idx++) {
            CompoundTag elm = elements.getCompound(idx);
            if (!AdvancedHudInteractions.isInteractiveType(elm.getString("Type"))) continue;
            if (interactionId.isBlank() || interactionId.equals(elm.getString("InteractionId"))) {
                return elm;
            }
        }
        return null;
    }

    // Send the snapshot
    private static void sendSnapshot(TabletActionContext ctx, CompoundTag data) {
        PacketDistributor.sendToPlayer(ctx.player(), new DiagnosticTabletAppSnapshotPayload(
                DiagnosticTabletData.appId("rdp"), ctx, data));
    }

    // Get the quiet success
    private static TabletActionHandler.Result quietSuccess() {
        return TabletActionHandler.Result.success(Component.empty());
    }

    // Create a failed diagnostic tablet rdp app
    private static TabletActionHandler.Result failure(String msg) {
        return TabletActionHandler.Result.failure(Component.literal(msg));
    }
}
