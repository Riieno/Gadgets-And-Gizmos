package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.ScmConfigurationProfile;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Store the browser entry
record BrowserEntry(String id, boolean category) {
}

// Store the graph template option
record GraphTemplateOption(String id, String label) {
}

// Store the registry entry
record RegistryEntry(String id, ItemStack stack, boolean header) {
}

// Store the SCM configuration sidebar layout
record ScmConfigurationSidebarLayout(int contentTop, int viewportTop, int viewportBottom,
                                     int contentHeight, int maximumScroll) {
    // Get the viewport height
    int viewportHeight() {
        return Math.max(1, viewportBottom - viewportTop);
    }
}

// Store the port hit
record PortHit(AdvancedGraphDocument.Node node, String port, boolean output) {
}

// Store the force write hit
record ForceWriteHit(AdvancedGraphDocument.Node node, String port) {
}

// Store the port position
record PortPosition(int x, int y) {
}

// Hold the precomputed node port layout
final class NodePortLayout {
    // Tracked inputs
    private final Map<String, String> inputs;
    // Tracked outputs
    private final Map<String, String> outputs;
    // Tracked visible inputs
    private final Set<String> visibleInputs;
    // Tracked visible outputs
    private final Set<String> visibleOutputs;
    // Tracked connected inputs
    private final Set<String> connectedInputs;
    // Output offsets
    private final Map<String, Integer> outputOffsets;
    // Output labels
    private final Map<String, List<FormattedCharSequence>> outputLabels;
    // Tracks whether collapsible is set
    private final boolean collapsible;
    // Tracks whether collapsed is set
    private final boolean collapsed;
    // Output data height
    private int outputDataHeight;

    // Initialize the node port layout
    NodePortLayout(Map<String, String> inputs, Map<String, String> outputs,
                   Set<String> visibleInputs, Set<String> visibleOutputs,
                   Set<String> connectedInputs, Map<String, Integer> outputOffsets,
                   Map<String, List<FormattedCharSequence>> outputLabels,
                   boolean collapsible, boolean collapsed) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.visibleInputs = visibleInputs;
        this.visibleOutputs = visibleOutputs;
        this.connectedInputs = connectedInputs;
        this.outputOffsets = outputOffsets;
        this.outputLabels = outputLabels;
        this.collapsible = collapsible;
        this.collapsed = collapsed;
    }

    // Get the inputs
    Map<String, String> inputs() {
        return inputs;
    }

    // Get the outputs
    Map<String, String> outputs() {
        return outputs;
    }

    // Get the visible inputs
    Set<String> visibleInputs() {
        return visibleInputs;
    }

    // Get the visible outputs
    Set<String> visibleOutputs() {
        return visibleOutputs;
    }

    // Get the connected inputs
    Set<String> connectedInputs() {
        return connectedInputs;
    }

    // Get the output offsets
    Map<String, Integer> outputOffsets() {
        return outputOffsets;
    }

    // Get the output labels
    Map<String, List<FormattedCharSequence>> outputLabels() {
        return outputLabels;
    }

    // Check if the node can be collapsed
    boolean collapsible() {
        return collapsible;
    }

    // Check if the node is collapsed
    boolean collapsed() {
        return collapsed;
    }

    // Get the output data height
    int outputDataHeight() {
        return outputDataHeight;
    }

    // Set the output data height
    void setOutputDataHeight(int outputDataHeight) {
        this.outputDataHeight = Math.max(0, outputDataHeight);
    }
}

// Store the sticky edit line
record StickyEditLine(String text, int start, int end) {
}

// Store the slider track
record SliderTrack(int left, int right) {
    // Get the width
    int width() {
        return Math.max(1, right - left);
    }

    // Check if this contains x
    boolean containsX(double mouseX, int margin) {
        return mouseX >= left - margin && mouseX <= right + margin;
    }
}

// Store the clipboard bounds
record ClipboardBounds(double left, double top, double right, double bottom) {
    // Get the center x
    double centerX() {
        return (left + right) * 0.5D;
    }

    // Get the center y
    double centerY() {
        return (top + bottom) * 0.5D;
    }
}

// Store the inspector sections
record InspectorSections(int optionsHeaderTop, int optionsTop, int optionsBottom,
                         int optionsDividerTop, int targetsHeaderTop, int targetsTop,
                         int targetsBottom, int targetsDividerTop, int variablesHeaderTop,
                         int variablesTop, int variablesBottom) {
}

// Define the inspector divider values
enum InspectorDivider {
    OPTIONS,
    TARGETS,
    SCHEDULE_PROPERTIES
}

// Define the HUD designer color control values
enum HudDesignerColorControl {
    HUE,
    SATURATION,
    VALUE,
    ALPHA
}

// Store the HUD designer hsv
record HudDesignerHsv(int rgb, float hue, float saturation, float value) {
}

// Store the UI rect
record UiRect(int x, int y, int width, int height) {
    // Get the right
    int right() {
        return x + width;
    }

    // Get the bottom
    int bottom() {
        return y + height;
    }

    // Check if this contains the value
    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
    }
}

// Store the SCM calibration point
record ScmCalibrationPoint(int x, int y) {
}

// Store the SCM configuration group row
record ScmConfigurationGroupRow(String groupId, String label,
                                ScmConfigurationProfile.UnitReference unit,
                                int color, int memberCount) {
    // Check if this is a group header
    boolean header() {
        return unit == null;
    }
}

// Store the SCM block key
record ScmBlockKey(UUID subLevelId, BlockPos blockPosition, Direction face) {
    ScmBlockKey {
        blockPosition = blockPosition == null ? BlockPos.ZERO : blockPosition.immutable();
    }
}

// Store the SCM simulation field
record ScmSimulationField(String label, String port) {
}

// Define the SCM control binding mode values
enum ScmControlBindingMode {
    AUTO("Auto"),
    BLOCK("Block"),
    FACE("Face");

    private final String label;

    ScmControlBindingMode(String label) {
        this.label = label;
    }

    // Get the mode label
    String label() {
        return label;
    }
}

// Store the context menu
record ContextMenu(int x, int y, List<String> items, String query, PortContext port) {
}

// Store the port context
record PortContext(String nodeId, String port, boolean output) {
}

// Store the option dropdown
record OptionDropdown(int x, int y, int width, String nodeId, String port, String type,
                      List<String> options, boolean property, int scroll, boolean searchable) {
}

// Store the schedule property choice
record SchedulePropertyChoice(String value, String label) {
}

// Store the dropdown scrollbar
record DropdownScrollbar(int trackX, int trackTop, int trackHeight, int thumbTop,
                         int thumbHeight, int thumbTravel, int maximumScroll) {
}

// Store the embedded image
record EmbeddedImage(String mimeType, String base64) {
}

// Signal an oversized embedded image reference
final class ImageReferenceSizeException extends RuntimeException {
}

// Store the mini browser
record MiniBrowser(int x, int y, List<AdvancedGraphCatalog.Definition> definitions,
                   String wireNode, String wirePort, boolean wireOutput) {
}
