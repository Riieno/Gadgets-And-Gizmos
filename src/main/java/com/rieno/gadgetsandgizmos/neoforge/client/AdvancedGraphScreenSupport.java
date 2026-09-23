package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.create.CreateRotationSpeedControllerGraphCompat;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;

import java.util.Set;

// Hold addon-specific graph editor text and classification helpers
final class AdvancedGraphScreenSupport {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the graph screen support
    private AdvancedGraphScreenSupport() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the human port label
    static String humanPort(String val) {
        if (val == null || val.isBlank()) return "Unset";
        String specialized = switch (val) {
            case "primary_orange_cw", "secondary_cyan_cw" -> "Clockwise";
            case "primary_orange_ccw", "secondary_cyan_ccw" -> "Counter Clockwise";
            case "primary_orange_operation_mode", "secondary_cyan_operation_mode" -> "Control Mode";
            default -> "";
        };
        if (!specialized.isBlank()) return specialized;
        String[] words = val.split("_");
        StringBuilder res = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!res.isEmpty()) res.append(' ');
            res.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return res.isEmpty() ? "Unset" : res.toString();
    }

    // Check rotation speed controller speed port
    static boolean isRotationSpeedControllerSpeed(AdvancedGraphDocument.Node node, String port) {
        return "set_block_data".equals(node.type()) && "speed".equals(port)
                && CreateRotationSpeedControllerGraphCompat.BLOCK_ID.equalsIgnoreCase(
                node.data().getCompound("TargetData").getString("BlockId"));
    }

    // Trim a label to the visible limit
    static String trim(String val, int max) {
        if (val == null) return "";
        return val.length() <= max ? val : val.substring(0, Math.max(0, max - 3)) + "...";
    }

    // Check if a pointer is inside a UI bounds
    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // Get a value label with an unset fallback
    static String valueOrUnset(String val) {
        return val == null || val.isBlank() ? "unset" : val;
    }

    // Check if a node uses configured bindings
    static boolean usesBinding(AdvancedGraphDocument.Node node) {
        return node != null && (node.type().startsWith("controller_")
                || "gamepad_input".equals(node.type())
                || node.type().startsWith("local_redstone")
                || "event_channel_change".equals(node.type())
                || "event_redstone_change".equals(node.type()));
    }

    // Get the configured binding title
    static String bindingOptionsTitle(AdvancedGraphDocument.Node node) {
        return node != null && "gamepad_input".equals(node.type())
                ? "Gamepad Controls" : "Configured Key Bindings";
    }

    // Check if a node uses discovery targets
    static boolean usesTarget(AdvancedGraphDocument.Node node) {
        return node != null && (node.type().contains("target") || node.type().startsWith("linker_face")
                || node.type().startsWith("acc_display_")
                || "get_block_data".equals(node.type()) || "set_block_data".equals(node.type()));
    }

    // Check if a node supports an SCM block target
    static boolean supportsScmBlockTarget(AdvancedGraphDocument.Node node) {
        return node != null && ("get_block_data".equals(node.type())
                || "set_block_data".equals(node.type())
                || "discovered_target_input".equals(node.type())
                || "direct_target_output".equals(node.type()));
    }

    // Check if a node supports the direct block picker
    static boolean supportsScmBlockPicker(AdvancedGraphDocument.Node node) {
        return node != null && ("get_block_data".equals(node.type())
                || "set_block_data".equals(node.type()));
    }

    // Check if a node provides shipping information
    static boolean isShippingInformationNodeType(String type) {
        return "acc_display_crn".equals(type) || "acc_display_shipping_information".equals(type);
    }

    // Check if a discovery target is an ACC display
    static boolean isAccDisplayTarget(ControllerDiscoveryNode target) {
        if (target == null) return false;
        return target.kind() == ControllerDiscoveryKind.DISPLAY
                || target.blockId().startsWith("createthrusters:acc_display");
    }

    // Check if a discovery target is a display adapter
    static boolean isDisplayAdapterTarget(ControllerDiscoveryNode target) {
        return target != null && (target.kind() == ControllerDiscoveryKind.DISPLAY_ADAPTER
                || "createthrusters:universal_display_adapter".equals(target.blockId()));
    }

    // Toggle a value in a selection set
    static <T> void toggle(Set<T> values, T val) {
        if (!values.add(val)) values.remove(val);
    }

    // Check if a pointer is near a node bounds
    static boolean pointerNearNode(double mouseX, double mouseY, int nodeX, int nodeY,
                                   int nodeWidth, int nodeHeight, int margin) {
        return mouseX >= nodeX - margin && mouseX <= nodeX + nodeWidth + margin
                && mouseY >= nodeY - margin && mouseY <= nodeY + nodeHeight + margin;
    }

    // Check if bounds intersect a viewport
    static boolean intersectsViewport(int x, int y, int width, int height,
                                      int viewportLeft, int viewportTop,
                                      int viewportRight, int viewportBottom, int margin) {
        int right = x + Math.max(1, width);
        int bottom = y + Math.max(1, height);
        return right >= viewportLeft - margin && x <= viewportRight + margin
                && bottom >= viewportTop - margin && y <= viewportBottom + margin;
    }
}
