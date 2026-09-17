package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.aeroworks.AeroworksControllerCompat;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerMenu;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.AccDisplayBlockEntity;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerItem;
import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.compat.create.CreateRotationSpeedControllerGraphCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.ContraptionDiagramControllerCompat;
import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryKind;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCurve;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphFunctions;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphNodeFactory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphPortNormalizer;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphImageAssets;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphLiveValue;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphPortState;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphSelection;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphTemplates;
import com.rieno.gadgetsandgizmos.lib.graph.render.GraphWireGeometry;
import com.rieno.gadgetsandgizmos.lib.graph.edit.GraphNodeAlias;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphValidator;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphVersionHistory;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementStyle;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudElementBinding;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudImageStore;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractiveStyles;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudInteractions;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.display.DisplayWidgetProjection;
import com.rieno.gadgetsandgizmos.lib.display.ShipInformationDisplayModes;
import com.rieno.gadgetsandgizmos.neoforge.ControllerGraphWebServer;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedContraptionControllerGraphPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedControllerProfilerPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudImageUploadPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerDiscoveryRequestPayload;
import com.rieno.gadgetsandgizmos.neoforge.network.AnalogueContraptionControllerKeyPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

// Edit ACC graphs and manifests without normal menu sync
public class AdvancedContraptionControllerScreen extends AbstractContainerScreen<AdvancedContraptionControllerMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String NODE_SUMMARY_HELPER_KEY = "createthrusters.controller.node_browser.hold_shift";
    private static final String DOCUMENTATION_CATEGORY = "documentation";
    private static final String HUD_FIELD_LABELS = "HudFieldLabels";
    private static final String CONSTRUCTOR_LABEL_PREFIX = "input_label:";
    private static final String NODE_ALIAS_PROPERTY = "node_alias";
    private static final int MAX_CONSTRUCTOR_INPUTS = 32;
    private static final String WIDGET_ELEMENTS = "WidgetElements";
    private static final int LEFT_WIDTH = 224;
    private static final int RIGHT_WIDTH = 218;
    private static final int SIDEBAR_HANDLE_WIDTH = 14;
    private static final int SIDEBAR_HANDLE_OFFSET = 10;
    private static final int MIN_GRAPH_WIDTH = 180;
    private static final int TOOLBAR_HEIGHT = 28;
    private static final int GRAPH_TAB_HEIGHT = 23;
    private static final int V2_GRID_SPACING = 24;
    private static final int INSPECTOR_SECTION_HEADER_HEIGHT = 16;
    private static final int INSPECTOR_SECTION_DIVIDER_HEIGHT = 5;
    private static final int INSPECTOR_SECTION_MIN_CONTENT_HEIGHT = 24;
    private static final int VARIABLE_BROWSER_ROW_HEIGHT = 19;
    private static final int VARIABLE_BROWSER_BUTTON_WIDTH = 34;
    private static final int NODE_WIDTH = 166;
    private static final int NODE_HEADER = 20;
    private static final int INLINE_MAP_PORT_INDENT = 8;
    /** First body row in graph units when there is no collapse control. */
    private static final int NODE_BODY_TOP = 25;
    /** Height of the dedicated collapse-control row directly below the title bar. */
    private static final int NODE_COLLAPSE_HANDLE_HEIGHT = 10;
    /** Extra graph-unit offset reserved for the collapse row; tune this to adjust port clearance. */
    private static final int NODE_COLLAPSE_PORT_OFFSET = 10;
    private static final int REROUTE_WIDTH = 30;
    private static final int REROUTE_HEIGHT = 18;
    private static final int MINI_BROWSER_WIDTH = 242;
    private static final int MINI_BROWSER_HEIGHT = 270;
    private static final int MINI_BROWSER_ROWS_TOP = 49;
    private static final long WIRE_DOUBLE_CLICK_MILLIS = 300L;
    private static final long CANVAS_DOUBLE_CLICK_MILLIS = 300L;
    private static final long GRAPH_ACTION_TOAST_MILLIS = 3000L;
    private static final int GRAPH_PREVIEW_REFRESH_INTERVAL = 20;
    private static final int PORT_TOOLTIP_MAX_CHARACTERS = 64;
    private static final AtomicLong GRAPH_ACTION_REQ_IDS = new AtomicLong();
    private static final double WIRE_HIT_RADIUS = 6.0D;
    private static final int GRAPH_FAILURE_COLOR = 0xFFFF3B3B;
    private static final int GRAPH_HISTORY_VISIBLE_ROWS = 10;
    private static final DateTimeFormatter GRAPH_VERSION_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final int CURVE_BODY_HEIGHT = 92;
    private static final int HUD_WIDTH = 720;
    private static final int HUD_HEIGHT = 420;
    private static final int HUD_PORT_VISIBLE_ROWS = 5;
    private static final int HUD_IMAGE_VISIBLE_ROWS = 7;
    private static final int OPTION_DROPDOWN_VISIBLE_ROWS = 12;
    private static final int OPTION_DROPDOWN_ITEM_HEIGHT = 18;
    private static final int OPTION_DROPDOWN_SEARCH_HEIGHT = 24;
    private static final int HUD_PASTE_OFFSET = 6;
    private static final int LINKER_BASE_X = 34;
    private static final int LINKER_BASE_Y = 18;
    private static final int LINKER_MODAL_WIDTH = 192;
    private static final int LINKER_MODAL_HEIGHT = 308;
    private static final int SHARE_MODAL_WIDTH = 292;
    private static final int SHARE_MODAL_HEIGHT = 238;
    private static final int TOOLS_MENU_WIDTH = 148;
    private static final int TOOLS_MENU_ROW_HEIGHT = 20;
    private static final int TOOLS_MENU_ROWS = 8;
    private static final int FREQUENCY_MODAL_WIDTH = 228;
    private static final int FREQUENCY_MODAL_HEIGHT = 336;
    private static final int FREQUENCY_MODAL_BASE_X = 34;
    private static final int FREQUENCY_MODAL_BASE_Y = -18;
    private static final int MIN_EXECUTION_OUTPUTS = 2;
    private static final int MAX_EXECUTION_OUTPUTS = 16;
    private static final int MIN_EXECUTION_INPUTS = 2;
    private static final int MAX_EXECUTION_INPUTS = 16;
    private static final List<String> CATEGORY_ORDER = List.of("functions", "events", "profiler", "core", "variables", "logic",
            "flow", "math", "response", "data", "controller", "ship_control", "shipping_schedule", "hud");
    private static final List<String> V2_CATEGORY_ORDER = List.of(
            "core", "functions", "controller", "logic", "math", "data", "ship_control", "shipping_schedule",
            "variables", "events", "profiler", "flow", "response", "hud");
    private static final List<GraphTemplateOption> GRAPH_TEMPLATES = List.of(
            new GraphTemplateOption("blank", "Blank"),
            new GraphTemplateOption("increment_decrement_on_hold", "Increment/Decrement on hold"),
            new GraphTemplateOption("toggle_latch", "Toggle Latch"),
            new GraphTemplateOption("linker_router", "Linker Router"));
    private static final Set<String> DISABLED_NODE_TYPES = Set.of();
    private static final int STICKY_NOTE_DEFAULT_WIDTH = 190;
    private static final int STICKY_NOTE_DEFAULT_HEIGHT = 130;
    private static final int STICKY_NOTE_MIN_WIDTH = 110;
    private static final int STICKY_NOTE_MIN_HEIGHT = 70;
    private static final int STICKY_NOTE_MAX_TEXT_LENGTH = 4096;
    private static final int IMAGE_REFERENCE_MIN_WIDTH = 120;
    private static final int IMAGE_REFERENCE_MIN_HEIGHT = 80;
    private static final int IMAGE_REFERENCE_MAX_BYTES = 256 * 1024;
    private static final List<String> COMPARE_OPERATOR_OPTIONS = List.of("==", ">", "<", ">=", "<=", "!=");
    private static final List<String> SWITCH_TYPE_OPTIONS = List.of(
            AdvancedGraphCatalog.SWITCH_EXECUTION_TYPE, AdvancedGraphCatalog.SWITCH_DATA_TYPE);
    private static final List<String> FILTER_OPERATION_OPTIONS =
            List.of("==", "!=", ">", "<", "contains", "starts_with");
    private static final List<String> VARIABLE_TYPE_OPTIONS = List.of(
            "boolean", "integer", "float", "string", "direction",
            "frequency", "target", "list", "map");
    private static final List<String> FUNCTION_PORT_TYPE_OPTIONS = List.of(
            "exec", "boolean", "number", "string", "direction", "frequency", "target", "list", "map", "any");
    private static final String FUNCTION_PORT_DROPDOWN = "__function_port__";
    private static final double DEFAULT_SMOOTHING_AMOUNT = 0.25D;
    private static final ResourceLocation ADVANCED_GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createthrusters", "textures/gui/advanced_contraption_controller.png");
    private static final int ADVANCED_GUI_TEXTURE_SIZE = 256;
    private static final int GRAPH_TOAST_SRC_X = 146;
    private static final int GRAPH_TOAST_HEIGHT = 17;
    private static final int GRAPH_TOAST_CAP_WIDTH = 16;
    private static final int GRAPH_TOAST_MIDDLE_SRC_X = GRAPH_TOAST_SRC_X + GRAPH_TOAST_CAP_WIDTH;
    private static final int GRAPH_TOAST_MIDDLE_SRC_WIDTH = 32;
    private static final int GRAPH_TOAST_RIGHT_SRC_X = GRAPH_TOAST_MIDDLE_SRC_X + GRAPH_TOAST_MIDDLE_SRC_WIDTH;
    private static final int TNT_GUI_TEXTURE_SIZE = 256;
    private static final WidgetSprites VANILLA_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/button"),
            ResourceLocation.withDefaultNamespace("widget/button_disabled"),
            ResourceLocation.withDefaultNamespace("widget/button_highlighted"));
    private static final int PLAYER_INVENTORY_SRC_X = 74;
    private static final int PLAYER_INVENTORY_SRC_Y = 123;
    private static final int PLAYER_INVENTORY_SRC_W = 176;
    private static final int PLAYER_INVENTORY_SRC_H = 100;
    private static final int PLAYER_INVENTORY_SLOT_OFFSET_X = 8;
    private static final int PLAYER_INVENTORY_SLOT_OFFSET_Y = 18;
    private static final int SINGLE_SLOT_SRC_X = 74;
    private static final int SINGLE_SLOT_SRC_Y = 226;
    private static final int SINGLE_SLOT_SIZE = 28;
    private static final int SINGLE_SLOT_ITEM_OFFSET = 6;
    private static final int RED_FREQUENCY_SLOT_U = 91;
    private static final int BLUE_FREQUENCY_SLOT_U = 110;
    private static final int FREQUENCY_SLOT_V = 126;
    private static final int FREQUENCY_SLOT_SIZE = 18;
    private static final int TITLEBAR_SRC_X = 79;
    private static final int TITLEBAR_SRC_Y = 82;
    private static final int TITLEBAR_SRC_W = 169;
    private static final int TITLEBAR_SRC_H = 14;
    private static final int SIDEBAR_TILE_SRC_X = 79;
    private static final int SIDEBAR_TILE_SRC_Y = 105;
    private static final int SIDEBAR_TILE_SRC_W = 142;
    private static final int SIDEBAR_TILE_SRC_H = 15;
    private static final int NODE_FRAME_SRC_W = 64;
    private static final int NODE_FRAME_SRC_H = 32;
    private static final int NODE_FRAME_TITLE_SRC_H = 9;
    private static final int NODE_FRAME_BODY_SRC_Y = 9;
    private static final int NODE_FRAME_BODY_SRC_H = 13;
    private static final int NODE_FRAME_EXTRA_SRC_Y = 22;
    private static final int NODE_FRAME_EXTRA_SRC_H = 10;
    private static final int NODE_EXTRA_HEIGHT = (int) Math.round(
            NODE_FRAME_EXTRA_SRC_H * NODE_WIDTH / (double) NODE_FRAME_SRC_W);
    private static final int NODE_FRAME_BODY_WIDTH_PAD = 2;
    private static final int NODE_FRAME_BODY_HEIGHT_PAD = 4;
    private static final int[][] NODE_FRAME_SOURCES = {
            {3, 3}, {70, 3}, {3, 38}, {70, 38}, {3, 73}, {3, 108}, {3, 143}, {3, 178}, {3, 213}
    };
    private static final int[] NODE_TITLE_COLORS = {
            0xFF5468AF, 0xFF787C8B, 0xFFB4B44F, 0xFFC93A3A, 0xFF2447AE,
            0xFF4EB55C, 0xFFCCCD35, 0xFFC7833B, 0xFFA650B2
    };
    private static final int NODE_OPTION_SRC_X = 179;
    private static final int NODE_OPTION_SRC_Y = 3;
    private static final int NODE_OPTION_SRC_W = 58;
    private static final int NODE_OPTION_SRC_H = 6;
    private static final int SIDEBAR_CHEVRON_SRC_X = 231;
    private static final int SIDEBAR_CHEVRON_SRC_Y = 12;
    private static final int SIDEBAR_CHEVRON_SRC_W = 6;
    private static final int SIDEBAR_CHEVRON_SRC_H = 58;
    private static final int GRAPH_GRID_BASE_SIZE = 16;
    private static final int GRAPH_GRID_VERTICAL_STRIPE_X = 7;
    private static final int GRAPH_GRID_HORIZONTAL_STRIPE_Y = 8;
    private static final int GRAPH_GRID_BACKGROUND_COLOR = 0xFF3B5870;
    private static final int GRAPH_GRID_LINE_COLOR = 0xFF497B9B;
    private static final double CANVAS_EDGE_PAN_MARGIN = 36.0D;
    private static final double CANVAS_EDGE_PAN_SPEED = 6.0D;
    private static final double RIGHT_PAN_DEADZONE = 4.0D;
    private static final int EXEC_DIAMOND_SRC_X = 248;
    private static final int EXEC_DIAMOND_SRC_Y = 2;
    private static final int EXEC_DIAMOND_SRC_W = 6;
    private static final int EXEC_DIAMOND_SRC_H = 6;
    private static final int DATA_PORT_SRC_X = 250;
    private static final int DATA_PORT_SRC_Y = 12;
    private static final int DATA_PORT_SRC_W = 2;
    private static final int DATA_PORT_SRC_H = 2;
    private static final int NODE_OPTION_ACCENT_SRC_X = 245;
    private static final int NODE_OPTION_ACCENT_SRC_Y = 12;
    private static final int NODE_OPTION_ACCENT_SRC_W = 2;
    private static final int NODE_OPTION_ACCENT_SRC_H = 44;
    private static final int SLIDER_TRACK_SRC_X = 188;
    private static final int SLIDER_TRACK_SRC_Y = 246;
    private static final int SLIDER_TRACK_SRC_W = 52;
    private static final int SLIDER_TRACK_SRC_H = 5;
    private static final int SLIDER_THUMB_SRC_X = 241;
    private static final int SLIDER_THUMB_HOVER_SRC_X = 244;
    private static final int SLIDER_THUMB_ACTIVE_SRC_X = 247;
    private static final int SLIDER_THUMB_SRC_Y = 246;
    private static final int SLIDER_THUMB_SRC_W = 2;
    private static final int SLIDER_THUMB_SRC_H = 5;
    private static final int SLIDER_FILL_SRC_X = 250;
    private static final int SLIDER_FILL_SRC_Y = 247;
    private static final int SLIDER_FILL_SRC_W = 1;
    private static final int SLIDER_FILL_SRC_H = 3;
    private static final int NODE_TEXT_COLOR = 0xFF1D252B;
    private static final int NODE_VALUE_TEXT_COLOR = 0xFF0F171D;
    private static final int NODE_MUTED_TEXT_COLOR = 0xFF3F505A;
    private static final List<String> MOUSE_INPUT_OPTIONS = List.of(
            "left_click", "right_click", "middle_click", "scroll_up", "scroll_down", "mouse_x", "mouse_y");
    private static final List<String> PULSE_BEHAVIOR_OPTIONS = List.of(
            "rising_edge", "falling_edge", "both");
    private static final List<String> ACC_DISPLAY_WIDGET_TYPES = List.of(
            "text", "value", "button", "toggle", "slider", "progress", "text_input", "image", "box");
    private static final List<String> ACC_DISPLAY_CRN_MODES = ShipInformationDisplayModes.ids();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Shared session clipboard
    private static AdvancedGraphDocument sessionClipboard;

    // Tracks whether V2 UI is set
    private boolean v2Ui;
    // Current draft
    private AdvancedGraphDocument draft;
    // Current saved draft
    private AdvancedGraphDocument savedDraft;
    // Tracks whether draft is dirty
    private boolean draftDirty;
    // Draft simulation runtime
    private GraphRuntime draftSimulationRuntime;
    // Draft simulation controller
    private AdvancedContraptionControllerBlockEntity draftSimulationController;
    // Current simulated draft
    private AdvancedGraphDocument simulatedDraft;
    // Current simulated draft fingerprint
    private int simulatedDraftFingerprint;
    // Tracks whether simulated draft needs tick is set
    private boolean simulatedDraftNeedsTick;
    // Draft preview refresh tick count
    private int draftPreviewRefreshTicks;
    // Undo
    private final ArrayDeque<AdvancedGraphDocument> undo = new ArrayDeque<>();
    // Redo
    private final ArrayDeque<AdvancedGraphDocument> redo = new ArrayDeque<>();
    // Selected nodes
    private final Set<String> selectedNodes = new LinkedHashSet<>();
    // Tracked failed nodes
    private final Set<String> failedNodes = new LinkedHashSet<>();
    // Tracked failed edges
    private final Set<String> failedEdges = new LinkedHashSet<>();
    // Tracked collapsed nodes
    private final Set<String> collapsedNodes = new LinkedHashSet<>();
    // Tracked collapsed categories
    private final Set<String> collapsedCategories = new LinkedHashSet<>();
    // Tracked collapsed mini categories
    private final Set<String> collapsedMiniCategories = new LinkedHashSet<>();
    // Tracked collapsed block namespaces
    private final Set<String> collapsedBlockNamespaces = new LinkedHashSet<>();
    // Active graph key bindings
    private final Map<Integer, Set<String>> activeGraphKeyBindings = new LinkedHashMap<>();
    // Tracked live graph targets
    private final List<ControllerDiscoveryNode> liveGraphTargets = new ArrayList<>();

    // Current node search
    private EditBox nodeSearch;
    // Current block search
    private EditBox blockSearch;
    // Current mini browser search
    private EditBox miniBrowserSearch;
    // Current function name editor
    private EditBox functionNameEditor;
    // Current option dropdown search
    private EditBox optionDropdownSearch;
    // Current inspector value
    private EditBox inspectorValue;
    // Current pan x
    private double panX = 150;
    // Current pan y
    private double panY = 80;
    // Current zoom
    private double zoom = 1.0;
    // Current browser scroll
    private int browserScroll;
    // Current block browser scroll
    private int blockBrowserScroll;
    // Current mini browser scroll
    private int miniBrowserScroll;
    // Current inspector options scroll
    private int inspectorOptionsScroll;
    // Current inspector targets scroll
    private int inspectorTargetsScroll;
    // Active function id
    private String activeFunctionId;
    // Current function tab scroll
    private int functionTabScroll;
    // Current editing function id
    private String editingFunctionId;
    // Current function name before edit
    private String functionNameBeforeEdit = "";
    // Tracks whether function name is being synced
    private boolean syncingFunctionName;
    // Tracks whether block browser is open
    private boolean blockBrowserOpen;
    // Tracks whether left sidebar collapsed is set
    private boolean leftSidebarCollapsed;
    // Tracks whether right sidebar collapsed is set
    private boolean rightSidebarCollapsed;
    // Tracks whether inspector options collapsed is set
    private boolean inspectorOptionsCollapsed;
    // Tracks whether inspector targets collapsed is set
    private boolean inspectorTargetsCollapsed;
    // Tracks whether inspector variables collapsed is set
    private boolean inspectorVariablesCollapsed;
    // Tracks whether save on close is set
    private boolean saveOnClose;
    // Tracks whether close save sent is set
    private boolean closeSaveSent;
    // Tracks whether advanced contraption is opening function plotter
    private boolean openingFunctionPlotter;
    // Current save on close req id
    private long saveOnCloseReqId;
    // Tracks whether graph history is open
    private boolean graphHistoryOpen;
    // Current graph history scroll
    private int graphHistoryScroll;
    // Tracked graph history entries
    private final List<AdvancedGraphVersionHistory.Entry> graphHistoryEntries = new ArrayList<>();
    // Current inspector options height
    private int inspectorOptionsHeight;
    // Current inspector targets height
    private int inspectorTargetsHeight;
    // Current dragging inspector divider
    private InspectorDivider draggingInspectorDivider;
    // Tracks whether linker is open
    private boolean linkerOpen;
    // Tracks whether share modal is open
    private boolean shareModalOpen;
    // Tracks whether tools menu is open
    private boolean toolsMenuOpen;
    // Current linker x
    private int linkerX;
    // Current linker y
    private int linkerY;
    // Tracks whether linker is being dragged
    private boolean draggingLinker;
    // Tracks whether advanced contraption is panning
    private boolean panning;
    // Tracks whether right pan is pending
    private boolean rightPanPending;
    // Tracks whether right panning is set
    private boolean rightPanning;
    // Current right pan start x
    private double rightPanStartX;
    // Current right pan start y
    private double rightPanStartY;
    // Tracks whether V2 minimap viewport is being dragged
    private boolean draggingV2MinimapViewport;
    // Current V2 minimap drag offset x
    private double v2MinimapDragOffsetX;
    // Current V2 minimap drag offset y
    private double v2MinimapDragOffsetY;
    // Current V2 minimap drag transform
    private AdvancedControllerMinimapGeometry.Transform v2MinimapDragTransform;
    // Tracks whether marquee is set
    private boolean marquee;
    // Current marquee start x
    private double marqueeStartX;
    // Current marquee start y
    private double marqueeStartY;
    // Current dragging node
    private String draggingNode;
    // Tracks whether node moved is being dragged
    private boolean draggingNodeMoved;
    // Current connecting node
    private String connectingNode;
    // Current connecting port
    private String connectingPort;
    // Tracks whether advanced contraption is connecting output
    private boolean connectingOutput;
    // Current wire mouse x
    private double wireMouseX;
    // Current wire mouse y
    private double wireMouseY;
    // Current context menu
    private ContextMenu contextMenu;
    // Current option dropdown
    private OptionDropdown optionDropdown;
    // Tracked option dropdown all options
    private List<String> optionDropdownAllOptions = List.of();
    // Tracks whether option dropdown thumb is being dragged
    private boolean draggingOptionDropdownThumb;
    // Option dropdown thumb grab offset
    private int optionDropdownThumbGrabOffset;
    // Current mini browser
    private MiniBrowser miniBrowser;
    // Last clicked wire
    private String lastClickedWire;
    // Last wire click time
    private long lastWireClickTime;
    // Last clicked node
    private String lastClickedNode;
    // Last node click time
    private long lastNodeClickTime;
    // Last canvas click time
    private long lastCanvasClickTime;
    // Last canvas click x
    private double lastCanvasClickX;
    // Last canvas click y
    private double lastCanvasClickY;
    // Tracks whether template picker is set
    private boolean templatePicker;
    // Selected input port
    private String selectedInputPort;
    // Current listening key node
    private String listeningKeyNode;
    // Current frequency node
    private String frequencyNode;
    // Tracks whether frequency modal is open
    private boolean frequencyModalOpen;
    // Selected group
    private String selectedGroup;
    // Current dragging group
    private String draggingGroup;
    // Current resizing group
    private String resizingGroup;
    // Current resizing sticky node
    private String resizingStickyNode;
    // Current editing sticky node
    private String editingStickyNode;
    // Current sticky caret
    private int stickyCaret;
    // Current sticky selection anchor
    private int stickySelectionAnchor;
    // Current dragging slider node
    private String draggingSliderNode;
    // Current dragging slider port
    private String draggingSliderPort;
    // Tracks whether inspector slider is being dragged
    private boolean draggingInspectorSlider;
    // Tracks whether output slider is being dragged
    private boolean draggingOutputSlider;
    // Current dragging curve node
    private String draggingCurveNode;
    // Current dragging curve point
    private int draggingCurvePoint = -1;
    // Tracks whether inspector curve is being dragged
    private boolean draggingInspectorCurve;
    // Tracks whether body value is being edited
    private boolean editingBodyValue;
    // Tracks whether inspector value is being synced
    private boolean syncingInspectorValue;
    // Tracks whether HUD is open
    private boolean hudOpen;
    // Tracks whether HUD interactive palette is set
    private boolean hudInteractivePalette;
    // Tracks whether HUD data bindings are set
    private boolean hudDataBindings;
    // Current HUD binding dropdown key
    private String hudBindingDropdownKey;
    // Current HUD binding dropdown scroll
    private int hudBindingDropdownScroll;
    // Current HUD binding prop scroll
    private int hudBindingPropScroll;
    // Current HUD node id
    private String hudNodeId;
    // Current HUD selected idx
    private int hudSelectedIdx = -1;
    // Tracks whether HUD element is being dragged
    private boolean draggingHudElement;
    // Tracks whether resizing HUD element is set
    private boolean resizingHudElement;
    // Current HUD drag offset x
    private int hudDragOffsetX;
    // Current HUD drag offset y
    private int hudDragOffsetY;
    // Tracks whether HUD fields are being synced
    private boolean syncingHudFields;
    // Tracks whether HUD port dropdown is open
    private boolean hudPortDropdownOpen;
    // Current HUD port dropdown scroll
    private int hudPortDropdownScroll;
    // Current dragging HUD color key
    private String draggingHudColorKey;
    // Current dragging HUD color control
    private HudDesignerColorControl draggingHudColorControl;
    // Tracked HUD hsv memory
    private final Map<String, HudDesignerHsv> hudHsvMemory = new LinkedHashMap<>();
    // Current dragging HUD prop slider
    private String draggingHudPropSlider;
    // Tracks whether HUD font style dropdown is open
    private boolean hudFontStyleDropdownOpen;
    // Tracks whether HUD style dropdown is open
    private boolean hudStyleDropdownOpen;
    // Tracks whether HUD image dropdown is open
    private boolean hudImageDropdownOpen;
    // Current HUD image dropdown scroll
    private int hudImageDropdownScroll;
    // Current HUD image status
    private String hudImageStatus = "";
    // Current HUD pending image node id
    private String hudPendingImageNodeId;
    // Current HUD pending image element idx
    private int hudPendingImageElementIdx = -1;
    // Pending embedded image nodes
    private final Set<String> pendingEmbeddedImageNodes = new LinkedHashSet<>();
    // Tracked sound event options
    private List<String> soundEventOptions = List.of();
    // Current HUD clipboard
    private CompoundTag hudClipboard;
    // Current HUD text
    private EditBox hudText;
    // Current HUD texture
    private EditBox hudTexture;
    // Current HUD width
    private EditBox hudWidth;
    // Current HUD height
    private EditBox hudHeight;
    // Current HUD rotation
    private EditBox hudRotation;
    // Hud scale
    private EditBox hudScale;
    // Current HUD border width
    private EditBox hudBorderWidth;
    // Current linker share name
    private EditBox linkerShareName;
    // Initial EMI visibility state
    private Boolean initialEmiVisibility;
    // Last observed linker stack
    private ItemStack lastObservedLinkerStack = ItemStack.EMPTY;
    // Last observed goggles output
    private ItemStack lastObservedGogglesOutput = ItemStack.EMPTY;
    // Tracked shared graph entries
    private final List<ControllerManifestStore.SharedGraphEntry> sharedGraphEntries = new ArrayList<>();
    // Current shared graph scroll
    private int sharedGraphScroll;
    // Selected shared graph index
    private int selectedSharedGraphIndex = -1;
    // Current shared graph conflict name
    private String sharedGraphConflictName = "";
    // Current shared graph save target
    private SharedGraphSaveTarget sharedGraphSaveTarget = SharedGraphSaveTarget.NONE;
    // Current linker status message
    private String linkerStatusMessage = "";
    // Linker status tick count
    private int linkerStatusTicks;
    // Tracks whether public share is available
    private boolean publicShareAvailable;
    // Tracks whether public share is pending
    private boolean publicSharePending;
    // Web graph publish tick count
    private int webGraphPublishTicks;
    // Graph action toast queue
    private final ArrayDeque<GraphActionToast> graphActionToastQueue = new ArrayDeque<>();
    // Current graph action toast
    private GraphActionToast graphActionToast;
    // Graph action toast expiry time
    private long graphActionToastExpiresAt;
    // Active graph action req id
    private long activeGraphActionReqId;
    // Tracks whether advanced contraption is awaiting graph action result
    private boolean awaitingGraphActionResult;
    // Pending graph saves
    private final Map<Long, AdvancedGraphDocument> pendingGraphSaves = new LinkedHashMap<>();
    // Tracked graph render nodes
    private final Map<String, AdvancedGraphDocument.Node> graphRenderNodes = new LinkedHashMap<>();
    // Tracked graph render port layouts
    private final Map<String, NodePortLayout> graphRenderPortLayouts = new LinkedHashMap<>();
    // Current graph render cache document
    private AdvancedGraphDocument graphRenderCacheDocument;
    private int graphRenderCacheSignature;
    // Structured data sync tick count
    private int structuredDataSyncTicks;
    // Profiler report tick count
    private int profilerReportTicks;
    // Tracks whether advanced contraption is projection rendering
    private boolean projectionRendering;
    // Tracks whether this screen is an off-screen display projection
    private boolean projectionScreen;
    // Current projection graph fingerprint
    private int projectionGraphFingerprint;
    // Current projected mouse button
    private int projectedMouseButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    // Define Modal Enum
    private enum tModals{
        LINKER, SHARE, TOOLS
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced contraption controller
    public AdvancedContraptionControllerScreen(AdvancedContraptionControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        v2Ui = advCtrlV2Enabled();
        AdvancedControllerUiPreferences.State preferences = AdvancedControllerUiPreferences.load();
        leftSidebarCollapsed = preferences.leftSidebarCollapsed();
        rightSidebarCollapsed = preferences.rightSidebarCollapsed();
        inspectorOptionsCollapsed = preferences.optionsCollapsed();
        inspectorTargetsCollapsed = preferences.targetsCollapsed();
        inspectorVariablesCollapsed = preferences.variablesCollapsed();
        inspectorOptionsHeight = preferences.optionsHeight();
        inspectorTargetsHeight = preferences.targetsHeight();
        saveOnClose = preferences.saveOnClose();
        collapsedNodes.addAll(preferences.collapsedNodeIds());
        if (v2Ui) {
            collapsedCategories.addAll(V2_CATEGORY_ORDER);
            collapsedCategories.remove("core");
        }
        draft = menu.getInitialDraft();
        restoreViewport();
        migrateSplitListNodes(draft);
        migrateHudNodes(draft);
        migrateSmoothingNodes(draft);
        syncExecSplitters();
        synchronizeVariableNodes();
        AdvancedGraphFunctions.synchronizeCalls(draft);
        synchronizeComparePorts();
        savedDraft = draft.copy();
        lastObservedLinkerStack = copySingle(menu.getCurrentLinkerStack());
        lastObservedGogglesOutput = copySingle(menu.getGogglesOutputStack());
        liveGraphTargets.addAll(sortedGraphTargets(currentGraphTargetSeeds()));
        templatePicker = activeNodes().isEmpty() && draft.templateId().isBlank();
    }

    // Mark this screen as an off-screen display projection before it is initialized
    void configureForProjection() {
        projectionScreen = true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Update the projection graph
    void updateProjectionGraph(AdvancedGraphDocument graph) {
        if (graph == null) {
            return;
        }
        int fingerprint = graph.toTag().hashCode();
        if (projectionGraphFingerprint == fingerprint) {
            return;
        }
        projectionGraphFingerprint = fingerprint;
        draft = graph.copy();
        if (activeFunctionId != null && draft.function(activeFunctionId) == null) {
            activeFunctionId = null;
        }
        restoreViewport();
        migrateSplitListNodes(draft);
        migrateHudNodes(draft);
        migrateSmoothingNodes(draft);
        syncExecSplitters();
        synchronizeVariableNodes();
        AdvancedGraphFunctions.synchronizeCalls(draft);
        synchronizeComparePorts();
        savedDraft = draft.copy();
        templatePicker = false;
        clearGraphRenderCache();
    }

    // Draw the projection
    void renderProjection(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        projectionRendering = true;
        try {
            render(graphics, mouseX, mouseY, partialTick);
        } finally {
            projectionRendering = false;
        }
    }

    // Handle the projected display click
    boolean projectionMouseClicked(double mouseX, double mouseY, int mouseButton) {
        projectedMouseButton = mouseButton;
        return mouseClicked(mouseX, mouseY, projectedMouseButton);
    }

    // Handle the projection mouse dragged
    void projectionMouseDragged(
            double mouseX, double mouseY, int mouseButton, double dragX, double dragY
    ) {
        projectedMouseButton = mouseButton;
        mouseDragged(mouseX, mouseY, mouseButton, dragX, dragY);
    }

    // Handle the projection mouse released
    void projectionMouseReleased(double mouseX, double mouseY, int mouseButton) {
        mouseReleased(mouseX, mouseY, mouseButton);
        projectedMouseButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    // Check if the adv ctrl V2 is enabled
    private static boolean advCtrlV2Enabled() {
        try {
            return Boolean.TRUE.equals(CTConfigs.CLIENT.advancedControllerV2Ui.get());
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    // Update the container
    @Override
    protected void containerTick() {
        super.containerTick();
        if (!projectionScreen) AccGuiScaleOverride.apply();
        detectLinkerSlotChanges();
        reportProfilerSample();
        syncFrequencyNode();
        if (++structuredDataSyncTicks >= 20) {
            structuredDataSyncTicks = 0;
            int previousLayout = graphPortLayoutSignature();
            syncStructuredDataNodes();
            if (previousLayout != graphPortLayoutSignature()) {
                draftDirty = true;
                simulatedDraft = null;
                clearGraphRenderCache();
            }
        }
        tickDraftSimulation();
        publishOpenGraphSnapshot();
        if (linkerStatusTicks > 0) {
            linkerStatusTicks--;
        }
        if (draggingNode == null || minecraft == null) return;
        var window = minecraft.getWindow();
        if (window.getScreenWidth() <= 0 || window.getScreenHeight() <= 0) return;
        double mouseX = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        panWhileDraggingNode(mouseX, mouseY);
    }

    // Report the profiler sample
    private void reportProfilerSample() {
        if (++profilerReportTicks < 5 || minecraft == null) {
            return;
        }
        profilerReportTicks = 0;
        int fps = minecraft.getFps();
        long frameTimeNanos = minecraft.getFrameTimeNs();
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if (controller != null) {
            controller.updateClientProfilerSample(fps, frameTimeNanos);
        }
        PacketDistributor.sendToServer(new AdvancedControllerProfilerPayload(
                MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId()), fps, frameTimeNanos));
    }

    // Update the draft simulation
    private void tickDraftSimulation() {
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if (controller == null || draft == null) {
            clearDraftSimulation();
            return;
        }
        if (draftSimulationRuntime == null || draftSimulationController != controller) {
            draftSimulationRuntime = new GraphRuntime(controller, true);
            draftSimulationController = controller;
            simulatedDraft = null;
            simulatedDraftFingerprint = 0;
            simulatedDraftNeedsTick = false;
            draftPreviewRefreshTicks = 0;
        }
        int draftFingerprint = simulatedDraft == draft
                ? simulatedDraftFingerprint : draft.simulationFingerprint();
        boolean refreshPreview = false;
        if (simulatedDraft != draft || simulatedDraftFingerprint != draftFingerprint) {
            draftSimulationRuntime.clear();
            simulatedDraft = draft;
            simulatedDraftFingerprint = draftFingerprint;
            simulatedDraftNeedsTick = draftSimulationRuntime.needsSimulationTick(draft);
            refreshPreview = true;
        }
        boolean advancedSimulation = hasUnsavedDraft()
                && (simulatedDraftNeedsTick || draftSimulationRuntime.hasPendingWork());
        if (advancedSimulation) {
            draftSimulationRuntime.tick(draft);
        }
        if (refreshPreview || ++draftPreviewRefreshTicks >= GRAPH_PREVIEW_REFRESH_INTERVAL) {
            draftSimulationRuntime.beginPreviewSample(draft);
            draftPreviewRefreshTicks = 0;
        }
    }

    // Clear the draft simulation
    private void clearDraftSimulation() {
        if (draftSimulationRuntime != null) {
            draftSimulationRuntime.clear();
        }
        draftSimulationRuntime = null;
        draftSimulationController = null;
        simulatedDraft = null;
        simulatedDraftFingerprint = 0;
        simulatedDraftNeedsTick = false;
        draftPreviewRefreshTicks = 0;
    }

    // Publish the open graph snapshot
    private void publishOpenGraphSnapshot() {
        if (draft == null || ++webGraphPublishTicks < 20) return;
        webGraphPublishTicks = 0;
        String name = linkerShareName == null ? "" : linkerShareName.getValue().trim();
        if (name.isBlank()) name = title == null ? "Currently Open Graph" : title.getString();
        ControllerGraphWebServer.publishOpenGraph(name, draft, draft.simulationFingerprint());
    }

    // Initialize the advanced contraption controller
    @Override
    protected void init() {
        if (!projectionScreen && AccGuiScaleOverride.apply()) {
            return;
        }
        // -----------------------------------------------------LAYOUT STATE-----------------------------------------------------
        boolean preserveLinkerOpen = linkerOpen && !setLinkerOpen(false);
        boolean preserveShareOpen = shareModalOpen;
        shareModalOpen = false;
        if (initialEmiVisibility == null) initialEmiVisibility = RecipeViewerVisibility.isEmiVisible();
        ensureSidebarFit();
        imageWidth = layoutRight() - layoutLeft();
        imageHeight = height;
        linkerX = Math.max(8, (layoutRight() - LINKER_MODAL_WIDTH) / 2);
        linkerY = Math.max(TOOLBAR_HEIGHT + 8, (height - LINKER_MODAL_HEIGHT) / 2);
        clampLinkerWindow();
        super.init();
        migrateCommentGroups(draft);
        imageWidth = layoutRight() - layoutLeft();
        leftPos = layoutLeft();
        topPos = 0;
        setLinkerOpen(false, false);

        // -----------------------------------------------------SEARCH FIELDS-----------------------------------------------------
        nodeSearch = new EditBox(font, layoutLeft() + 8, 36, Math.max(48, activeLeftWidth() - 16), 18, Component.literal("Search nodes"));
        nodeSearch.setHint(Component.literal(v2Ui ? "Search nodes..." : "Search nodes"));
        if (v2Ui) {
            nodeSearch.setBordered(false);
            nodeSearch.setTextColor(AdvancedControllerV2Theme.PRIMARY);
            nodeSearch.setTextColorUneditable(AdvancedControllerV2Theme.MUTED);
        }
        nodeSearch.setVisible(!leftSidebarCollapsed && !blockBrowserOpen);
        nodeSearch.setResponder(val -> browserScroll = 0);
        addRenderableWidget(nodeSearch);

        blockSearch = new EditBox(font, layoutLeft() + 8, 36, Math.max(48, activeLeftWidth() - 16), 18, Component.literal("Search blocks and items"));
        blockSearch.setHint(Component.literal("Search blocks and items"));
        if (v2Ui) {
            blockSearch.setBordered(false);
            blockSearch.setTextColor(AdvancedControllerV2Theme.PRIMARY);
            blockSearch.setTextColorUneditable(AdvancedControllerV2Theme.MUTED);
        }
        blockSearch.setVisible(!leftSidebarCollapsed && blockBrowserOpen);
        blockSearch.setResponder(val -> blockBrowserScroll = 0);
        addRenderableWidget(blockSearch);

        // -----------------------------------------------------EDITOR FIELDS-----------------------------------------------------
        miniBrowserSearch = new EditBox(font, 0, 0, MINI_BROWSER_WIDTH - 12, 18,
                Component.literal("Search nodes"));
        miniBrowserSearch.setHint(Component.literal("Search nodes..."));
        miniBrowserSearch.setVisible(false);
        miniBrowserSearch.setResponder(val -> miniBrowserScroll = 0);
        addRenderableWidget(miniBrowserSearch);

        functionNameEditor = new EditBox(font, 0, 0, 120, 16, Component.literal("Function name"));
        functionNameEditor.setMaxLength(64);
        functionNameEditor.setBordered(false);
        functionNameEditor.setTextColor(0xFFFFFFFF);
        functionNameEditor.setVisible(false);
        functionNameEditor.setResponder(this::updateFunctionName);
        addRenderableWidget(functionNameEditor);

        optionDropdownSearch = new EditBox(font, 0, 0, 120, 18, Component.literal("Search sounds"));
        optionDropdownSearch.setMaxLength(128);
        optionDropdownSearch.setHint(Component.literal("Search sounds..."));
        optionDropdownSearch.setVisible(false);
        optionDropdownSearch.setResponder(this::filterOptionDropdown);
        addRenderableWidget(optionDropdownSearch);

        // ------------------------------------INSPECTOR / HUD FIELDS------------------------------------
        inspectorValue = new EditBox(font, graphRight() + 8, 118, RIGHT_WIDTH - 16, 18, Component.literal("Node value"));
        inspectorValue.setMaxLength(Integer.MAX_VALUE);
        inspectorValue.setVisible(false);
        inspectorValue.setResponder(this::updateNodeProperty);
        addRenderableWidget(inspectorValue);

        hudText = new EditBox(font, 0, 0, 160, 18, Component.literal("Widget text"));
        hudText.setVisible(false);
        hudText.setResponder(val -> updateHudTextField("Text", val));
        addRenderableWidget(hudText);

        hudTexture = new EditBox(font, 0, 0, 160, 18, Component.literal("Widget texture"));
        hudTexture.setMaxLength(Integer.MAX_VALUE);
        hudTexture.setVisible(false);
        hudTexture.setResponder(val -> updateHudTextField("Texture", val));
        addRenderableWidget(hudTexture);

        hudWidth = new EditBox(font, 0, 0, 72, 18, Component.literal("Widget width"));
        hudWidth.setVisible(false);
        hudWidth.setResponder(val -> updateHudNumField("W", val));
        addRenderableWidget(hudWidth);

        hudHeight = new EditBox(font, 0, 0, 72, 18, Component.literal("Widget height"));
        hudHeight.setVisible(false);
        hudHeight.setResponder(val -> updateHudNumField("H", val));
        addRenderableWidget(hudHeight);

        hudRotation = new EditBox(font, 0, 0, 72, 18,
                Component.literal("Widget rotation"));
        hudRotation.setVisible(false);
        hudRotation.setResponder(val -> updateHudDoubleField(
                "Rotation", val, -3600.0D, 3600.0D));
        addRenderableWidget(hudRotation);

        hudScale = new EditBox(font, 0, 0, 72, 18,
                Component.literal("Widget scale"));
        hudScale.setVisible(false);
        hudScale.setResponder(val -> updateHudDoubleField(
                "Scale", val, 0.01D, 100.0D));
        addRenderableWidget(hudScale);

        hudBorderWidth = new EditBox(font, 0, 0, 72, 18, Component.literal("Widget border width"));
        hudBorderWidth.setMaxLength(2);
        hudBorderWidth.setVisible(false);
        hudBorderWidth.setResponder(val -> updateHudClampedNumField(
                "BorderWidth", val, 0, AdvancedHudElementStyle.MAX_BORDER_WIDTH));
        addRenderableWidget(hudBorderWidth);

        UiRect initialShareBounds = shareModalBounds();
        linkerShareName = new EditBox(font, initialShareBounds.x() + 16,
                initialShareBounds.y() + 34, initialShareBounds.width() - 32, 18,
                Component.literal("Shared graph name"));
        linkerShareName.setHint(Component.literal("Graph name"));
        linkerShareName.setMaxLength(64);
        linkerShareName.setVisible(false);
        addRenderableWidget(linkerShareName);

        // ------------------------------------TOOLBAR / WINDOWS------------------------------------
        addToolbarButton(layoutLeft() + 4, "Save", btn -> {
            long requestId = beginGraphActionToast("Saving and applying graph...");
            saveAndApplyDraft("save_apply", requestId);
        });
        addToolbarButton(layoutLeft() + 52, "Apply", btn -> {
            long requestId = beginGraphActionToast("Applying graph...");
            saveAndApplyDraft("apply_save", requestId);
        });
        addToolbarButton(layoutRight() - 304, blockBrowserOpen ? "EMI/JEI: On" : "EMI/JEI: Off", btn -> setBlockBrowserOpen(!blockBrowserOpen));
        addToolbarButton(layoutRight() - 220, "Linker", btn -> setLinkerOpen(!linkerOpen));
        addToolbarButton(layoutRight() - 154, "Share", btn -> setShareModalOpen(!shareModalOpen));
        addToolbarButton(layoutRight() - 88, "Tools", btn -> toolsMenuOpen = !toolsMenuOpen);
        if (preserveLinkerOpen) {
            setLinkerOpen(true, false);
        }
        if (preserveShareOpen) {
            setShareModalOpen(true);
        }
        if (miniBrowser != null) {
            miniBrowserSearch.setVisible(true);
            posMiniSearch();
        }
        requestDiscoveryRefresh();
    }

    // Add the toolbar button
    private void addToolbarButton(int x, String label, Button.OnPress press) {
        int buttonWidth = Math.max(42, font.width(label) + 12);
        if (v2Ui) {
            addRenderableWidget(new V2ToolbarButton(
                    x, 5, buttonWidth, 18, Component.literal(label), press));
        } else {
            addRenderableWidget(Button.builder(Component.literal(label), press)
                    .bounds(x, 5, buttonWidth, 18)
                    .build());
        }
    }

    // Save the close label
    private String saveOnCloseLabel() {
        return "Save on close: " + (saveOnClose ? "On" : "Off");
    }

    // Get the graph V2 label
    private String graphV2Label() {
        return "Graph V2: " + (v2Ui ? "On" : "Off");
    }

    // Check if this uses V2 UI
    boolean usesV2Ui() {
        return v2Ui;
    }

    // Set the graph V2 enabled
    private void setGraphV2Enabled(boolean enabled) {
        CTConfigs.setAdvancedControllerV2Ui(enabled);
        v2Ui = enabled;
        if (enabled) {
            collapsedCategories.addAll(V2_CATEGORY_ORDER);
            collapsedCategories.remove("core");
        }
        clearGraphRenderCache();
        if (minecraft != null) {
            resize(minecraft, width, height);
        }
    }

    // Get the tools menu bounds
    private UiRect toolsMenuBounds() {
        return new UiRect(layoutRight() - TOOLS_MENU_WIDTH - 4, TOOLBAR_HEIGHT,
                TOOLS_MENU_WIDTH, TOOLS_MENU_ROW_HEIGHT * TOOLS_MENU_ROWS + 8);
    }

    // Get the tools menu row bounds
    private UiRect toolsMenuRowBounds(int row) {
        UiRect bounds = toolsMenuBounds();
        return new UiRect(bounds.x() + 4, bounds.y() + 4 + row * TOOLS_MENU_ROW_HEIGHT,
                bounds.width() - 8, TOOLS_MENU_ROW_HEIGHT - 2);
    }

    // Get the tools toolbar button bounds
    private UiRect toolsToolbarButtonBounds() {
        return new UiRect(layoutRight() - 88, 5, Math.max(42, font.width("Tools") + 12), 18);
    }

    // Draw the tools menu
    private void drawToolsMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect bounds = toolsMenuBounds();
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        List<String> labels = List.of(
                "Validate", "Revert", "Reset Graph", saveOnCloseLabel(), graphV2Label(),
                "Function Plotter", "Templates", "Versions");
        for (int row = 0; row < labels.size(); row++) {
            UiRect item = toolsMenuRowBounds(row);
            renderAdvancedButton(graphics, font, item.x(), item.y(), item.width(), item.height(),
                    Component.literal(labels.get(row)), item.contains(mouseX, mouseY), true);
        }
    }

    // Handle the tools menu click
    private boolean handleToolsMenuClick(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        for (int row = 0; row < TOOLS_MENU_ROWS; row++) {
            if (!toolsMenuRowBounds(row).contains(mouseX, mouseY)) continue;
            toolsMenuOpen = false;
            switch (row) {
                case 0 -> {
                    long requestId = beginGraphActionToast("Validating graph...");
                    send("validate", "", requestId);
                }
                case 1 -> revertDraft();
                case 2 -> resetDraftGraph();
                case 3 -> {
                    saveOnClose = !saveOnClose;
                    if (saveOnClose) {
                        closeSaveSent = false;
                    }
                    saveUiPreferences();
                }
                case 4 -> setGraphV2Enabled(!v2Ui);
                case 5 -> openFunctionPlotter();
                case 6 -> templatePicker = true;
                case 7 -> toggleGraphHistory();
                default -> {
                }
            }
            return true;
        }
        return false;
    }

    // Open the function plotter
    private void openFunctionPlotter() {
        if (minecraft == null) return;
        openingFunctionPlotter = true;
        minecraft.setScreen(new FunctionPlotterScreen(this,
                MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId())));
    }

    // Open the function plotter from tablet
    public void openFunctionPlotterFromTablet() {
        openFunctionPlotter();
    }

    // Toggle the graph history panel
    private void toggleGraphHistory() {
        graphHistoryOpen = !graphHistoryOpen;
        if (graphHistoryOpen) {
            graphHistoryScroll = 0;
            send("history", "");
        }
    }

    // Revert the draft
    private void revertDraft() {
        long requestId = beginGraphActionToast("Reverting graph...");
        checkpoint();
        draft = savedDraft.copy();
        draftDirty = false;
        activeFunctionId = null;
        restoreViewport();
        synchronizeVariableNodes();
        synchronizeComparePorts();
        clearSelection();
        clearDraftSimulation();
        queueGraphActionResult(requestId, true, "Graph Reverted",
                savedDraft.revision(), false, false, List.of());
    }

    // Reset the draft graph
    private void resetDraftGraph() {
        checkpoint();
        AdvancedGraphDocument replacement = new AdvancedGraphDocument();
        replacement.setRevision(savedDraft.revision());
        draft = replacement;
        activeFunctionId = null;
        functionTabScroll = 0;
        templatePicker = false;
        restoreViewport();
        synchronizeVariableNodes();
        synchronizeComparePorts();
        clearSelection();
        failedNodes.clear();
        failedEdges.clear();
        clearDraftSimulation();
        clearGraphRenderCache();
        syncInspector();
        displayGraphActionToast(new GraphActionToast(
                Component.literal("Graph reset"), GraphActionToastSeverity.SUCCESS));
    }

    // Draw the advanced sprite
    private static void blitAdvancedSprite(GuiGraphics graphics, int x, int y, int width, int height,
                                           int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        BufferBuilder buffer = beginAdvancedSpriteBatch();
        addAdvancedSprite(buffer, graphics.pose().last().pose(),
                x, y, width, height, sourceX, sourceY, sourceWidth, sourceHeight);
        finishAdvSpriteBatch(buffer);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                       GRAPH RENDERING
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Begin the advanced sprite batch
    private static BufferBuilder beginAdvancedSpriteBatch() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, ADVANCED_GUI_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
    }

    // Finish the adv sprite batch
    private static void finishAdvSpriteBatch(BufferBuilder buffer) {
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    // Add the advanced sprite
    private static void addAdvancedSprite(BufferBuilder buffer, Matrix4f pose,
                                          int x, int y, int width, int height,
                                          int sourceX, int sourceY, int sourceWidth, int sourceHeight) {
        float u0 = sourceX / (float) ADVANCED_GUI_TEXTURE_SIZE;
        float u1 = (sourceX + sourceWidth) / (float) ADVANCED_GUI_TEXTURE_SIZE;
        float v0 = sourceY / (float) ADVANCED_GUI_TEXTURE_SIZE;
        float v1 = (sourceY + sourceHeight) / (float) ADVANCED_GUI_TEXTURE_SIZE;
        buffer.addVertex(pose, x, y, 0.0F).setUv(u0, v0);
        buffer.addVertex(pose, x, y + height, 0.0F).setUv(u0, v1);
        buffer.addVertex(pose, x + width, y + height, 0.0F).setUv(u1, v1);
        buffer.addVertex(pose, x + width, y, 0.0F).setUv(u1, v0);
    }

    // Draw the advanced nine slice
    private static void blitAdvancedNineSlice(GuiGraphics graphics, int x, int y, int width, int height,
                                               int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                                               int left, int top, int right, int bottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (width <= left + right || height <= top + bottom) {
            blitAdvancedSprite(graphics, x, y, width, height, sourceX, sourceY, sourceWidth, sourceHeight);
            return;
        }

        int centerSourceWidth = sourceWidth - left - right;
        int centerSourceHeight = sourceHeight - top - bottom;
        int centerWidth = width - left - right;
        int centerHeight = height - top - bottom;

        BufferBuilder buffer = beginAdvancedSpriteBatch();
        Matrix4f pose = graphics.pose().last().pose();
        addAdvancedSprite(buffer, pose, x, y, left, top, sourceX, sourceY, left, top);
        addAdvancedSprite(buffer, pose, x + left, y, centerWidth, top,
                sourceX + left, sourceY, centerSourceWidth, top);
        addAdvancedSprite(buffer, pose, x + width - right, y, right, top,
                sourceX + sourceWidth - right, sourceY, right, top);

        addAdvancedSprite(buffer, pose, x, y + top, left, centerHeight, sourceX, sourceY + top,
                left, centerSourceHeight);
        addAdvancedSprite(buffer, pose, x + left, y + top, centerWidth, centerHeight,
                sourceX + left, sourceY + top, centerSourceWidth, centerSourceHeight);
        addAdvancedSprite(buffer, pose, x + width - right, y + top, right, centerHeight,
                sourceX + sourceWidth - right, sourceY + top, right, centerSourceHeight);

        addAdvancedSprite(buffer, pose, x, y + height - bottom, left, bottom,
                sourceX, sourceY + sourceHeight - bottom, left, bottom);
        addAdvancedSprite(buffer, pose, x + left, y + height - bottom, centerWidth, bottom,
                sourceX + left, sourceY + sourceHeight - bottom, centerSourceWidth, bottom);
        addAdvancedSprite(buffer, pose, x + width - right, y + height - bottom, right, bottom,
                sourceX + sourceWidth - right, sourceY + sourceHeight - bottom, right, bottom);
        finishAdvSpriteBatch(buffer);
    }

    // Draw the title bar
    void renderTitleBar(GuiGraphics graphics, int x, int y, int width, int height) {
        if (v2Ui) {
            AdvancedControllerV2Theme.drawTitleBar(graphics, x, y, width, height);
            return;
        }
        blitAdvancedNineSlice(graphics, x, y, width, height, TITLEBAR_SRC_X, TITLEBAR_SRC_Y,
                TITLEBAR_SRC_W, TITLEBAR_SRC_H, 6, 3, 6, 3);
    }

    // Draw the sidebar panel
    void renderSidebarPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (v2Ui) {
            AdvancedControllerV2Theme.drawSidebar(graphics, x, y, width, height);
            return;
        }
        blitAdvancedNineSlice(graphics, x, y, width, height, SIDEBAR_TILE_SRC_X, SIDEBAR_TILE_SRC_Y,
                SIDEBAR_TILE_SRC_W, SIDEBAR_TILE_SRC_H, 8, 4, 8, 4);
        graphics.fill(x, y, x + width, y + height, 0x33101820);
    }

    // Draw the advanced panel
    void renderAdvancedPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (v2Ui) {
            AdvancedControllerV2Theme.drawPanel(graphics, x, y, width, height);
            return;
        }
        renderSidebarPanel(graphics, x, y, width, height);
        renderTitleBar(graphics, x, y, width, Math.min(22, height));
        graphics.renderOutline(x, y, width, height, 0xFF4F3828);
    }

    // Get the node frame source
    private static int[] nodeFrameSource(String category) {
        return NODE_FRAME_SOURCES[nodeFrameIndex(category)];
    }

    // Get the node frame index
    private static int nodeFrameIndex(String category) {
        return switch (category) {
            case "controller" -> 0;
            case "core" -> 1;
            case "response" -> 2;
            case "events", "profiler" -> 3;
            case "data" -> 4;
            case "math" -> 5;
            case "flow", "hud" -> 6;
            case "logic" -> 7;
            case "variables" -> 8;
            default -> 1;
        };
    }

    // Get the node titlebar color
    private static int nodeTitlebarColor(String category) {
        return NODE_TITLE_COLORS[nodeFrameIndex(category)];
    }

    // Get the contrast text color
    private static int contrastTextColor(int backgroundColor) {
        int alpha = (backgroundColor >>> 24) & 0xFF;
        if (alpha == 0) {
            return 0xFFFFFFFF;
        }
        double r = relativeChannel((backgroundColor >> 16) & 0xFF);
        double g = relativeChannel((backgroundColor >> 8) & 0xFF);
        double b = relativeChannel(backgroundColor & 0xFF);
        double luminance = 0.2126D * r + 0.7152D * g + 0.0722D * b;
        double whiteContrast = 1.05D / (luminance + 0.05D);
        double darkContrast = (luminance + 0.05D) / 0.05D;
        return darkContrast > whiteContrast ? NODE_TEXT_COLOR : 0xFFFFFFFF;
    }

    // Get the relative channel
    private static double relativeChannel(int val) {
        double channel = val / 255.0D;
        return channel <= 0.03928D ? channel / 12.92D : Math.pow((channel + 0.055D) / 1.055D, 2.4D);
    }

    // Get the node value text color
    private int nodeValueTextColor() {
        return v2Ui ? AdvancedControllerV2Theme.PRIMARY : NODE_VALUE_TEXT_COLOR;
    }

    // Get the node muted text color
    private int nodeMutedTextColor() {
        return v2Ui ? AdvancedControllerV2Theme.SECONDARY : NODE_MUTED_TEXT_COLOR;
    }

    // Get the node title text color
    private int nodeTitleTextColor(String category) {
        return v2Ui ? AdvancedControllerV2Theme.PRIMARY : contrastTextColor(nodeTitlebarColor(category));
    }

    // Get the interface primary color
    private int interfacePrimaryColor() {
        return v2Ui ? AdvancedControllerV2Theme.PRIMARY : 0xFFFFFFFF;
    }

    // Get the interface secondary color
    private int interfaceSecondaryColor() {
        return v2Ui ? AdvancedControllerV2Theme.SECONDARY : 0xFFC8D7E3;
    }

    // Get the interface muted color
    private int interfaceMutedColor() {
        return v2Ui ? AdvancedControllerV2Theme.MUTED : 0xFF91A9B8;
    }

    // Get the interface accent text color
    private int interfaceAccentTextColor() {
        return v2Ui ? AdvancedControllerV2Theme.ACCENT_LIGHT : 0xFF91D9FF;
    }

    // Get the mix color
    private static int mixColor(int first, int second, double amount) {
        double clamped = Mth.clamp(amount, 0.0D, 1.0D);
        int a = (int) Math.round(Mth.lerp(clamped, (first >>> 24) & 0xFF, (second >>> 24) & 0xFF));
        int r = (int) Math.round(Mth.lerp(clamped, (first >>> 16) & 0xFF, (second >>> 16) & 0xFF));
        int g = (int) Math.round(Mth.lerp(clamped, (first >>> 8) & 0xFF, (second >>> 8) & 0xFF));
        int b = (int) Math.round(Mth.lerp(clamped, first & 0xFF, second & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }

    // Draw the node frame
    private void renderNodeFrame(GuiGraphics graphics, int x, int y, int width, int height,
                                 String category, boolean selected) {
        renderNodeFrame(graphics, x, y, width, height, category, selected,
                Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    // Draw the node frame
    private void renderNodeFrame(GuiGraphics graphics, int x, int y, int width, int height,
                                 String category, boolean selected, int clipTop, int clipBottom) {
        int[] src = nodeFrameSource(category);
        int frameWidth = width + NODE_FRAME_BODY_WIDTH_PAD;
        double frameScale = Math.max(0.1D, width / (double) NODE_FRAME_SRC_W);
        int titleHeight = Math.min(height, Math.max(1, (int) Math.round(NODE_FRAME_TITLE_SRC_H * frameScale)));
        int extraHeight = height > titleHeight
                ? Math.min(Math.max(1, (int) Math.round(NODE_FRAME_EXTRA_SRC_H * frameScale)), height - titleHeight)
                : 0;
        int bodyHeight = Math.max(0, height - titleHeight - extraHeight);
        boolean titleVisible = y + titleHeight >= clipTop && y <= clipBottom;
        boolean bodyVisible = bodyHeight > 0
                && y + titleHeight + bodyHeight >= clipTop && y + titleHeight <= clipBottom;
        boolean extraVisible = extraHeight > 0
                && y + height >= clipTop && y + height - extraHeight <= clipBottom;
        if (!titleVisible && !bodyVisible && !extraVisible) {
            return;
        }
        if (v2Ui) {
            AdvancedControllerV2Theme.drawNodeFrame(
                    graphics, x, y, frameWidth, height, titleHeight, nodeTitlebarColor(category), selected);
            return;
        }

        BufferBuilder buffer = beginAdvancedSpriteBatch();
        Matrix4f pose = graphics.pose().last().pose();
        if (titleVisible) {
            addNodeFrameSection(buffer, pose, x, y, frameWidth, titleHeight,
                    src[0], src[1], NODE_FRAME_TITLE_SRC_H, frameScale);
        }
        if (bodyVisible) {
            addNodeFrameBody(buffer, pose, x, y + titleHeight, frameWidth, bodyHeight,
                    src[0], src[1] + NODE_FRAME_BODY_SRC_Y, frameScale, clipTop, clipBottom);
        }
        if (extraVisible) {
            addNodeFrameSection(buffer, pose, x, y + height - extraHeight, frameWidth, extraHeight,
                    src[0], src[1] + NODE_FRAME_EXTRA_SRC_Y, NODE_FRAME_EXTRA_SRC_H, frameScale);
        }
        finishAdvSpriteBatch(buffer);
        if (selected) {
            graphics.renderOutline(x - 1, y - 1, frameWidth + 2, height + 2, 0xFFFFFFFF);
        }
    }

    // Add the node frame section
    private static void addNodeFrameSection(BufferBuilder buffer, Matrix4f pose,
                                            int x, int y, int width, int height,
                                            int sourceX, int sourceY, int sourceHeight, double frameScale) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int capWidth = Math.max(1, (int) Math.round(5 * frameScale));
        if (width <= capWidth * 2) {
            addAdvancedSprite(buffer, pose, x, y, width, height,
                    sourceX, sourceY, NODE_FRAME_SRC_W, sourceHeight);
            return;
        }
        addAdvancedSprite(buffer, pose, x, y, capWidth, height,
                sourceX, sourceY, 5, sourceHeight);
        addAdvancedSprite(buffer, pose, x + capWidth, y, width - capWidth * 2, height,
                sourceX + 5, sourceY, NODE_FRAME_SRC_W - 10, sourceHeight);
        addAdvancedSprite(buffer, pose, x + width - capWidth, y, capWidth, height,
                sourceX + NODE_FRAME_SRC_W - 5, sourceY, 5, sourceHeight);
    }

    // Add the node frame body
    private static void addNodeFrameBody(BufferBuilder buffer, Matrix4f pose,
                                         int x, int y, int width, int height,
                                         int sourceX, int sourceY, double frameScale,
                                         int clipTop, int clipBottom) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int tileHeight = Math.max(1, (int) Math.round(NODE_FRAME_BODY_SRC_H * frameScale));
        int firstVisibleOffset = Math.max(0, clipTop - y);
        int firstTileOffset = firstVisibleOffset / tileHeight * tileHeight;
        int lastVisibleOffset = Math.min(height, Math.max(0, clipBottom - y + 1));
        for (int offset = firstTileOffset; offset < lastVisibleOffset; offset += tileHeight) {
            int drawHeight = Math.min(tileHeight, height - offset);
            int sourceHeight = Math.max(1, Math.min(NODE_FRAME_BODY_SRC_H,
                    (int) Math.ceil(drawHeight / frameScale)));
            addNodeFrameSection(buffer, pose, x, y + offset, width, drawHeight,
                    sourceX, sourceY, sourceHeight, frameScale);
        }
    }

    // Draw the node option
    static void renderNodeOption(GuiGraphics graphics, int x, int y, int width, int height,
                                 int accentColor, boolean selected) {
        renderNodeOption(graphics, x, y, width, height, accentColor, selected, true);
    }

    // Draw the controller option
    void renderControllerOption(GuiGraphics graphics, int x, int y, int width, int height,
                                int accentColor, boolean selected) {
        renderControllerOption(graphics, x, y, width, height, accentColor, selected, true);
    }

    // Draw the controller option
    private void renderControllerOption(GuiGraphics graphics, int x, int y, int width, int height,
                                        int accentColor, boolean selected, boolean accent) {
        if (v2Ui) {
            AdvancedControllerV2Theme.drawOption(
                    graphics, x, y, width, height, accentColor, selected, accent);
            return;
        }
        renderNodeOption(graphics, x, y, width, height, accentColor, selected, accent);
    }

    // Draw the node option
    private static void renderNodeOption(GuiGraphics graphics, int x, int y, int width, int height,
                                         int accentColor, boolean selected, boolean accent) {
        blitAdvancedNineSlice(graphics, x, y, width, height, NODE_OPTION_SRC_X, NODE_OPTION_SRC_Y,
                NODE_OPTION_SRC_W, NODE_OPTION_SRC_H, 3, 2, 3, 2);
        if (accent && height > 3 && width > 6) {
            blitAdvancedSprite(graphics, x + 1, y + 1, 2, height - 2,
                    NODE_OPTION_ACCENT_SRC_X, NODE_OPTION_ACCENT_SRC_Y,
                    NODE_OPTION_ACCENT_SRC_W, NODE_OPTION_ACCENT_SRC_H);
            graphics.fill(x + 1, y + 1, x + 4, y + height - 1, (accentColor & 0x00FFFFFF) | 0xCC000000);
        }
        if (selected) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0x33FFFFFF);
        }
    }

    // Draw the data port
    private void renderDataPort(GuiGraphics graphics, int centerX, int centerY, String type, boolean output) {
        if (v2Ui) {
            AdvancedControllerV2Theme.drawPort(graphics, centerX, centerY, portColor(type));
            return;
        }
        int size = 8;
        int x = centerX - size / 2;
        int y = centerY - size / 2;
        blitAdvancedSprite(graphics, x, y, size, size,
                DATA_PORT_SRC_X, dataPortSourceY(type), DATA_PORT_SRC_W, DATA_PORT_SRC_H);
    }

    // Get the data port source y
    private static int dataPortSourceY(String type) {
        return switch (type) {
            case "number" -> 21;
            case "boolean" -> 36;
            case "string", "frequency", "target" -> 30;
            case "direction" -> 27;
            case "list", "map" -> 12;
            case "any" -> 33;
            default -> 33;
        };
    }

    // Draw the category swatch
    private static void renderCategorySwatch(GuiGraphics graphics, int centerX, int centerY, String category) {
        int col = nodeTitlebarColor(category);
        int x = centerX - 4;
        int y = centerY - 4;
        graphics.fill(x, y, x + 8, y + 8, mixColor(col, 0xFF000000, 0.28D));
        graphics.fill(x + 1, y + 1, x + 7, y + 7, col);
    }

    // Draw the category chevron
    private void drawCategoryChevron(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                             int x, int y, int size, String category, boolean collapsed) {
        int col = nodeTitlebarColor(category);
        if (v2Ui) {
            AdvancedControllerV2Theme.drawHandle(graphics, x, y, size, size, false);
            graphics.drawCenteredString(font, collapsed ? ">" : "v", x + size / 2, y + 2,
                    AdvancedControllerV2Theme.SECONDARY);
            return;
        }
        graphics.fill(x, y, x + size, y + size, mixColor(col, 0xFF000000, 0.35D));
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, col);
        graphics.fill(x + 1, y + 1, x + size - 1, Math.max(y + 2, y + size / 2),
                mixColor(col, 0xFFFFFFFF, 0.18D));
        graphics.drawCenteredString(font, collapsed ? ">" : "v", x + size / 2, y + 2, contrastTextColor(col));
    }

    // Draw the advanced button
    void renderAdvancedButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                              int x, int y, int width, int height, Component label,
                              boolean hovered, boolean active) {
        if (v2Ui) {
            int border = hovered ? AdvancedControllerV2Theme.ACCENT : AdvancedControllerV2Theme.BORDER;
            AdvancedControllerV2Theme.drawRoundedRect(graphics, x, y, width, height, 4, border);
            AdvancedControllerV2Theme.drawRoundedRect(graphics, x + 1, y + 1, width - 2, height - 2, 3,
                    active ? AdvancedControllerV2Theme.PANEL_RAISED : AdvancedControllerV2Theme.PANEL_BACKGROUND);
            graphics.drawCenteredString(font, label, x + width / 2, y + (height - 8) / 2,
                    active ? AdvancedControllerV2Theme.PRIMARY : AdvancedControllerV2Theme.MUTED);
            return;
        }
        graphics.blitSprite(VANILLA_BUTTON_SPRITES.get(active, hovered), x, y, width, height);
        int col = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        graphics.drawCenteredString(font, label, x + width / 2, y + (height - 8) / 2, col);
    }

    // Draw the advanced slider
    static void renderAdvancedSlider(GuiGraphics graphics, int trackLeft, int centerY,
                                     int trackWidth, double amount, int fillColor, boolean active) {
        if (trackWidth <= 2) {
            return;
        }
        int trackTop = centerY - SLIDER_TRACK_SRC_H / 2;
        blitAdvancedSprite(graphics, trackLeft, trackTop, trackWidth, SLIDER_TRACK_SRC_H,
                SLIDER_TRACK_SRC_X, SLIDER_TRACK_SRC_Y, SLIDER_TRACK_SRC_W, SLIDER_TRACK_SRC_H);
        int fillWidth = (int) Math.round(Mth.clamp(amount, 0.0, 1.0) * trackWidth);
        if (fillWidth > 0) {
            blitAdvancedSprite(graphics, trackLeft, centerY - SLIDER_FILL_SRC_H / 2, fillWidth, SLIDER_FILL_SRC_H,
                    SLIDER_FILL_SRC_X, SLIDER_FILL_SRC_Y, SLIDER_FILL_SRC_W, SLIDER_FILL_SRC_H);
            graphics.fill(trackLeft, centerY - 1, trackLeft + fillWidth, centerY + 2,
                    (fillColor & 0x00FFFFFF) | 0x99000000);
        }
        int thumbSourceX = active ? SLIDER_THUMB_ACTIVE_SRC_X : SLIDER_THUMB_SRC_X;
        int thumbX = trackLeft + fillWidth - 2;
        thumbX = Mth.clamp(thumbX, trackLeft - 2, trackLeft + trackWidth - 2);
        blitAdvancedSprite(graphics, thumbX, centerY - 5, 4, 10,
                thumbSourceX, SLIDER_THUMB_SRC_Y, SLIDER_THUMB_SRC_W, SLIDER_THUMB_SRC_H);
    }

    // Draw the controller slider
    private void renderControllerSlider(GuiGraphics graphics, int trackLeft, int centerY,
                                        int trackWidth, double amount, int fillColor, boolean active) {
        if (v2Ui) {
            AdvancedControllerV2Theme.drawSlider(
                    graphics, trackLeft, centerY, trackWidth, amount, fillColor, active);
            return;
        }
        renderAdvancedSlider(graphics, trackLeft, centerY, trackWidth, amount, fillColor, active);
    }

    // Draw the sidebar chevron
    private static void drawSidebarChevron(GuiGraphics graphics, int x, int y, int width, int height) {
        if (height <= 0 || width <= 0) {
            return;
        }
        if (height <= 16) {
            blitAdvancedSprite(graphics, x, y, width, height, SIDEBAR_CHEVRON_SRC_X, SIDEBAR_CHEVRON_SRC_Y,
                    SIDEBAR_CHEVRON_SRC_W, SIDEBAR_CHEVRON_SRC_H);
            return;
        }
        int cap = 8;
        BufferBuilder buffer = beginAdvancedSpriteBatch();
        Matrix4f pose = graphics.pose().last().pose();
        addAdvancedSprite(buffer, pose, x, y, width, cap,
                SIDEBAR_CHEVRON_SRC_X, SIDEBAR_CHEVRON_SRC_Y,
                SIDEBAR_CHEVRON_SRC_W, cap);
        addAdvancedSprite(buffer, pose, x, y + cap, width, height - cap * 2,
                SIDEBAR_CHEVRON_SRC_X, SIDEBAR_CHEVRON_SRC_Y + cap,
                SIDEBAR_CHEVRON_SRC_W, SIDEBAR_CHEVRON_SRC_H - cap * 2);
        addAdvancedSprite(buffer, pose, x, y + height - cap, width, cap,
                SIDEBAR_CHEVRON_SRC_X, SIDEBAR_CHEVRON_SRC_Y + SIDEBAR_CHEVRON_SRC_H - cap,
                SIDEBAR_CHEVRON_SRC_W, cap);
        finishAdvSpriteBatch(buffer);
    }

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        prepareGraphRenderCache();
        layoutHudWidgets();
        ensureSidebarFit();
        graphics.fill(layoutLeft(), 0, layoutRight(), height,
                v2Ui ? AdvancedControllerV2Theme.CANVAS_BACKGROUND : 0xFF10141C);
        if (graphRight() > graphLeft()) {
            graphics.enableScissor(graphLeft(), graphTop(), graphRight(), graphBottom());
            drawGrid(graphics);
            drawGroups(graphics);
            drawEdges(graphics, mouseX, mouseY);
            drawNodes(graphics, mouseX, mouseY);
            if (connectingNode != null) drawActiveWire(graphics);
            if (marquee) drawMarquee(graphics, mouseX, mouseY);
            drawGraphFps(graphics);
            graphics.disableScissor();
        }
        if (v2Ui) {
            drawV2CanvasOverlay(graphics);
        }

        drawToolbar(graphics);
        drawGraphTabs(graphics, mouseX, mouseY);
        drawLeftBrowser(graphics, mouseX, mouseY);
        drawInspector(graphics);
        drawSidebarHandles(graphics);
        if (toolsMenuOpen) drawToolsMenu(graphics, mouseX, mouseY);
        if (linkerOpen) drawLinkerWindow(graphics, mouseX, mouseY);
        if (shareModalOpen) drawShareWindow(graphics, mouseX, mouseY);
        if (templatePicker) drawTemplatePicker(graphics, mouseX, mouseY);
        if (graphHistoryOpen) drawGraphHistory(graphics, mouseX, mouseY);
        if (contextMenu != null) drawContextMenu(graphics);
        if (miniBrowser != null) drawMiniBrowser(graphics, mouseX, mouseY);
        if (hudOpen) drawHud(graphics, mouseX, mouseY);
        if (frequencyModalOpen) drawFrequencyModal(graphics, mouseX, mouseY);
    }

    // Draw the graph fps
    private void drawGraphFps(GuiGraphics graphics) {
        if (minecraft == null) return;
        String text = minecraft.getFps() + " FPS";
        int x = graphFpsLeft(graphLeft(), leftSidebarHandleX(), SIDEBAR_HANDLE_WIDTH);
        int y = graphTop() + 5;
        graphics.fill(x - 2, y - 2, x + font.width(text) + 2, y + font.lineHeight + 1, 0x990B1118);
        graphics.drawString(font, text, x, y, 0xFFE7F4FF, false);
    }

    // Get the graph fps left
    static int graphFpsLeft(int graphLeft, int sidebarHandleX, int sidebarHandleWidth) {
        return Math.max(graphLeft, sidebarHandleX + sidebarHandleWidth) + 5;
    }

    // Prepare the graph render cache
    private void prepareGraphRenderCache() {
        //if (draft == null || graphRenderCacheDocument == draft) return;
        if (draft == null){
            clearGraphRenderCache();
            return;
        }
        int signature = graphPortLayoutSignature();
        if(graphRenderCacheDocument == draft && graphRenderCacheSignature == signature) return;
        graphRenderNodes.clear();
        graphRenderPortLayouts.clear();
        graphRenderCacheDocument = draft;
        graphRenderCacheSignature = signature;
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            graphRenderNodes.put(node.id(), node);
            Map<String, String> inputs = editorPorts(node, false);
            Map<String, String> outputs = editorPorts(node, true);
            boolean collapsed = collapsedNodes.contains(node.id()) || isPersistedNodeCollapsed(node);
            graphRenderPortLayouts.put(node.id(), new NodePortLayout(
                    inputs, outputs, new LinkedHashSet<>(), new LinkedHashSet<>(),
                    new LinkedHashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                    nonExecPortCount(inputs) + nonExecPortCount(outputs) > 3, collapsed));
        }
        for (AdvancedGraphDocument.Edge edge : activeEdges()) {
            NodePortLayout from = graphRenderPortLayouts.get(edge.fromNode());
            NodePortLayout to = graphRenderPortLayouts.get(edge.toNode());
            if (from == null || to == null) continue;
            to.connectedInputs().add(edge.toPort());
            String fromType = from.outputs().get(edge.fromPort());
            String toType = to.inputs().get(edge.toPort());
            if (fromType == null || toType == null || "exec".equals(fromType) || "exec".equals(toType)) {
                continue;
            }
            from.visibleOutputs().add(edge.fromPort());
            to.visibleInputs().add(edge.toPort());
        }
        for (NodePortLayout layout : graphRenderPortLayouts.values()) {
            if (!layout.visibleInputs().isEmpty() || !layout.visibleOutputs().isEmpty()) continue;
            int visible = 0;
            for (var port : layout.inputs().entrySet()) {
                if (!"exec".equals(port.getValue()) && visible++ < 3) {
                    layout.visibleInputs().add(port.getKey());
                }
            }
            for (var port : layout.outputs().entrySet()) {
                if (!"exec".equals(port.getValue()) && visible++ < 3) {
                    layout.visibleOutputs().add(port.getKey());
                }
            }
        }
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            NodePortLayout layout = graphRenderPortLayouts.get(node.id());
            if (layout == null) continue;
            boolean collapsed = layout.collapsed() && layout.collapsible();
            int offset = 0;
            for (var port : layout.outputs().entrySet()) {
                if ("exec".equals(port.getValue())
                        || collapsed && !layout.visibleOutputs().contains(port.getKey())) {
                    continue;
                }
                List<FormattedCharSequence> lines = wrappedOutputPortLabel(node, port.getKey());
                int rowHeight = Math.max(13, lines.size() * font.lineHeight);
                layout.outputLabels().put(port.getKey(), lines);
                layout.outputOffsets().put(port.getKey(),
                        offset + Math.max(0, (rowHeight - font.lineHeight) / 2));
                offset += rowHeight;
            }
            layout.setOutputDataHeight(offset);
        }
    }

    // Get the graph port layout signature
    private int graphPortLayoutSignature() {
        if (draft == null) return 0;
        int signature = 1;
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            signature = 31 * signature + node.id().hashCode();
            signature = 31 * signature + node.type().hashCode();
            Tag dynamicInputs = node.data().get("DynamicInputs");
            Tag dynamicOutputs = node.data().get("DynamicOutputs");
            Tag outputLabels = node.data().get("OutputLabels");
            Tag dataPortGroups = node.data().get(AdvancedGraphCatalog.DATA_PORT_GROUPS_TAG);
            signature = 31 * signature + (dynamicInputs == null ? 0 : dynamicInputs.hashCode());
            signature = 31 * signature + (dynamicOutputs == null ? 0 : dynamicOutputs.hashCode());
            signature = 31 * signature + (outputLabels == null ? 0 : outputLabels.hashCode());
            signature = 31 * signature + (dataPortGroups == null ? 0 : dataPortGroups.hashCode());
            signature = 31 * signature + Boolean.hashCode(node.data().getBoolean(
                    AdvancedGraphCatalog.COLLAPSE_INPUTS_TO_MAP_TAG));
            signature = 31 * signature + Boolean.hashCode(node.data().getBoolean(
                    AdvancedGraphCatalog.COLLAPSE_OUTPUTS_TO_MAP_TAG));
        }
        for (AdvancedGraphDocument.Edge edge : activeEdges()) {
            signature = 31 * signature + Objects.hash(
                    edge.fromNode(), edge.fromPort(), edge.toNode(), edge.toPort());
        }
        return signature;
    }

    // Clear the graph render cache
    private void clearGraphRenderCache() {
        graphRenderCacheDocument = null;
        graphRenderCacheSignature = 0;
        graphRenderNodes.clear();
        graphRenderPortLayouts.clear();
    }

    // Get the node inputs
    private Map<String, String> nodeInputs(AdvancedGraphDocument.Node node) {
        NodePortLayout layout = graphRenderCacheDocument == draft && node != null
                ? graphRenderPortLayouts.get(node.id()) : null;
        return layout == null ? AdvancedGraphCatalog.inputs(node) : layout.inputs();
    }

    // Get the ports displayed by the editor. The graph runtime retains the individual ports
    // and transparently maps them through input_map/output_map when a side is collapsed.
    private Map<String, String> editorPorts(AdvancedGraphDocument.Node node, boolean output) {
        Map<String, String> ports = output ? AdvancedGraphCatalog.outputs(node)
                : AdvancedGraphCatalog.inputs(node);
        boolean collapsed = node != null && node.data().getBoolean(output
                ? AdvancedGraphCatalog.COLLAPSE_OUTPUTS_TO_MAP_TAG
                : AdvancedGraphCatalog.COLLAPSE_INPUTS_TO_MAP_TAG);
        if (!collapsed) {
            return groupedEditorPorts(node, ports, output);
        }
        Map<String, String> visible = new LinkedHashMap<>();
        for (var entry : ports.entrySet()) {
            if ("exec".equals(entry.getValue())) {
                visible.put(entry.getKey(), entry.getValue());
            }
        }
        String mapPort = output ? AdvancedGraphCatalog.COLLAPSED_OUTPUT_MAP_PORT
                : AdvancedGraphCatalog.COLLAPSED_INPUT_MAP_PORT;
        if (ports.containsKey(mapPort)) {
            visible.put(mapPort, "map");
        }
        return visible;
    }

    // Keep generated MAP ports compact while retaining connected legacy leaf ports
    private Map<String, String> groupedEditorPorts(AdvancedGraphDocument.Node node,
                                                   Map<String, String> ports, boolean output) {
        CompoundTag groups = node == null ? new CompoundTag()
                : node.data().getCompound(AdvancedGraphCatalog.DATA_PORT_GROUPS_TAG);
        if (groups.isEmpty()) {
            return ports;
        }

        Set<String> groupedLeaves = new LinkedHashSet<>();
        for (String group : groups.getAllKeys()) {
            groupedLeaves.addAll(groups.getCompound(group).getAllKeys());
        }

        Map<String, String> visible = new LinkedHashMap<>();
        for (var entry : ports.entrySet()) {
            String port = entry.getKey();
            if (groups.contains(port)) {
                visible.put(port, "map");
            } else if (!groupedLeaves.contains(port) || groupedDataLeafIsVisible(node, port, output)) {
                visible.put(port, entry.getValue());
            }
        }
        return visible;
    }

    // Keep an explicitly wired or configured legacy leaf available in the editor
    private boolean groupedDataLeafIsVisible(AdvancedGraphDocument.Node node, String port, boolean output) {
        if (node == null || port == null || port.isBlank()) {
            return false;
        }
        boolean connected = activeEdges().stream().anyMatch(edge -> output
                ? node.id().equals(edge.fromNode()) && port.equals(edge.fromPort())
                : node.id().equals(edge.toNode()) && port.equals(edge.toPort()));
        if (connected) {
            return true;
        }
        return !output && node.data().getCompound("Defaults").contains(port)
                && !node.data().getCompound("PrefilledInputs").contains(port);
    }

    // Get the node outputs
    private Map<String, String> nodeOutputs(AdvancedGraphDocument.Node node) {
        NodePortLayout layout = graphRenderCacheDocument == draft && node != null
                ? graphRenderPortLayouts.get(node.id()) : null;
        return layout == null ? AdvancedGraphCatalog.outputs(node) : layout.outputs();
    }

    // Draw the toolbar
    private void drawToolbar(GuiGraphics graphics) {
        renderTitleBar(graphics, layoutLeft(), 0, layoutRight() - layoutLeft(), TOOLBAR_HEIGHT);
        graphics.fill(layoutLeft(), TOOLBAR_HEIGHT - 1, layoutRight(), TOOLBAR_HEIGHT,
                v2Ui ? AdvancedControllerV2Theme.BORDER : 0xFF314657);
        String title = "Advanced Contraption Controller";
        int titleLeft = layoutLeft() + 276;
        int titleRight = layoutRight() - 434;
        if (titleRight - titleLeft >= font.width(title) + 8) {
            graphics.drawCenteredString(font, title, (titleLeft + titleRight) / 2, 10,
                    v2Ui ? AdvancedControllerV2Theme.SECONDARY : 0xFFE7F4FF);
        }
    }

    // Draw the graph tabs
    private void drawGraphTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = graphLeft();
        int right = graphRight();
        if (right <= left) {
            return;
        }
        int top = TOOLBAR_HEIGHT;
        graphics.fill(left, top, right, top + GRAPH_TAB_HEIGHT,
                v2Ui ? AdvancedControllerV2Theme.PANEL_BACKGROUND : 0xFF17232D);
        graphics.enableScissor(left, top, right, top + GRAPH_TAB_HEIGHT);
        int x = left + 5 - functionTabScroll;
        x = drawGraphTab(graphics, x, top + 3, "Main Graph",
                activeFunctionId == null, mouseX, mouseY, false, false);
        for (AdvancedGraphDocument.FunctionGraph function : draft.functions()) {
            x = drawGraphTab(graphics, x, top + 3, function.name(),
                    function.id().equals(activeFunctionId), mouseX, mouseY, true,
                    function.id().equals(editingFunctionId));
        }
        graphics.disableScissor();
        graphics.fill(left, top + GRAPH_TAB_HEIGHT - 1, right, top + GRAPH_TAB_HEIGHT,
                v2Ui ? AdvancedControllerV2Theme.BORDER : 0xFF466A82);
    }

    // Draw the graph tab
    private int drawGraphTab(GuiGraphics graphics, int x, int y, String label,
                             boolean selected, int mouseX, int mouseY,
                             boolean renameable, boolean editing) {
        String shown = trim(label, 24);
        int tabWidth = graphTabWidth(shown, renameable);
        boolean hovered = graphTabHovered(mouseX, mouseY, x, y, tabWidth, 18);
        int col = v2Ui
                ? selected ? AdvancedControllerV2Theme.PANEL_SELECTED
                : hovered ? AdvancedControllerV2Theme.PANEL_HOVERED
                : AdvancedControllerV2Theme.PANEL_RAISED
                : selected ? 0xFF365E77 : hovered ? 0xFF294457 : 0xFF202F3B;
        graphics.fill(x, y, x + tabWidth, y + 18, col);
        graphics.renderOutline(x, y, tabWidth, 18,
                v2Ui ? selected ? AdvancedControllerV2Theme.ACCENT : AdvancedControllerV2Theme.BORDER
                        : selected ? 0xFF91D9FF : 0xFF466A82);
        if (!editing) {
            int labelWidth = renameable ? tabWidth - 28 : tabWidth;
            graphics.drawCenteredString(font, shown, x + labelWidth / 2, y + 5,
                    v2Ui ? selected ? AdvancedControllerV2Theme.PRIMARY : AdvancedControllerV2Theme.SECONDARY
                            : selected ? 0xFFFFFFFF : 0xFFC2D3DF);
        }
        if (renameable && !editing && (hovered || selected)) {
            int actionX = x + tabWidth - 28;
            if (hovered && mouseX >= actionX) {
                graphics.fill(actionX, y + 1, x + tabWidth - 1, y + 17,
                        v2Ui ? AdvancedControllerV2Theme.ACCENT_OVERLAY : 0x663B617C);
            }
            graphics.drawCenteredString(font, "Edit", actionX + 14, y + 5,
                    v2Ui ? AdvancedControllerV2Theme.ACCENT_LIGHT : 0xFF91D9FF);
        }
        return x + tabWidth + 3;
    }

    // Check if the graph tab is hovered
    static boolean graphTabHovered(
            double mouseX, double mouseY, int x, int y, int width, int height
    ) {
        return inside(mouseX, mouseY, x, y, width, height);
    }

    // Get the graph tab width
    private int graphTabWidth(String label, boolean renameable) {
        return Math.max(72, font.width(trim(label, 24)) + 20 + (renameable ? 28 : 0));
    }

    // Get the graph tabs content width
    private int graphTabsContentWidth() {
        int width = graphTabWidth("Main Graph", false) + 3;
        for (AdvancedGraphDocument.FunctionGraph function : draft.functions()) {
            width += graphTabWidth(function.name(), true) + 3;
        }
        return width + 7;
    }

    // Handle the graph tab click
    private boolean handleGraphTabClick(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT
                || mouseX < graphLeft() || mouseX >= graphRight()
                || mouseY < TOOLBAR_HEIGHT || mouseY >= graphTop()) {
            return false;
        }
        int x = graphLeft() + 5 - functionTabScroll;
        int width = graphTabWidth("Main Graph", false);
        if (mouseX >= x && mouseX < x + width) {
            selectGraphTab(null);
            return true;
        }
        x += width + 3;
        for (AdvancedGraphDocument.FunctionGraph function : draft.functions()) {
            width = graphTabWidth(function.name(), true);
            if (mouseX >= x && mouseX < x + width) {
                if (mouseX >= x + width - 28) {
                    beginFunctionRename(function, x, width);
                } else {
                    selectGraphTab(function.id());
                }
                return true;
            }
            x += width + 3;
        }
        return true;
    }

    // Begin the function rename
    private void beginFunctionRename(
            AdvancedGraphDocument.FunctionGraph function, int tabX, int tabWidth) {
        if (function == null || functionNameEditor == null) {
            return;
        }
        finishFunctionRename(false);
        selectGraphTab(function.id());
        checkpoint();
        editingFunctionId = function.id();
        functionNameBeforeEdit = function.name();
        int left = Math.max(graphLeft() + 4, tabX + 6);
        int right = Math.min(graphRight() - 4, tabX + tabWidth - 6);
        functionNameEditor.setX(left);
        functionNameEditor.setY(TOOLBAR_HEIGHT + 3);
        functionNameEditor.setWidth(Math.max(32, right - left));
        syncingFunctionName = true;
        functionNameEditor.setValue(function.name());
        syncingFunctionName = false;
        functionNameEditor.setVisible(true);
        functionNameEditor.setFocused(true);
        functionNameEditor.moveCursorToEnd(false);
        setFocused(functionNameEditor);
    }

    // Update the function name
    private void updateFunctionName(String requested) {
        if (syncingFunctionName || editingFunctionId == null) {
            return;
        }
        AdvancedGraphDocument.FunctionGraph function = draft.function(editingFunctionId);
        if (function == null) {
            finishFunctionRename(false);
            return;
        }
        function.setName(requested);
        AdvancedGraphFunctions.synchronizeCalls(draft);
        clearGraphRenderCache();
    }

    // Finish the function rename
    private void finishFunctionRename(boolean revert) {
        if (editingFunctionId != null && revert) {
            AdvancedGraphDocument.FunctionGraph function = draft.function(editingFunctionId);
            if (function != null) {
                function.setName(functionNameBeforeEdit);
                AdvancedGraphFunctions.synchronizeCalls(draft);
                clearGraphRenderCache();
            }
        }
        editingFunctionId = null;
        functionNameBeforeEdit = "";
        if (functionNameEditor != null) {
            functionNameEditor.setVisible(false);
            functionNameEditor.setFocused(false);
        }
        if (getFocused() == functionNameEditor) {
            setFocused(null);
        }
    }

    // Draw the graph action toast
    private void drawGraphActionToast(GuiGraphics graphics) {
        if (graphActionToast == null) {
            return;
        }
        if (Util.getMillis() >= graphActionToastExpiresAt) {
            if (graphActionToastQueue.isEmpty()) {
                graphActionToast = null;
                graphActionToastExpiresAt = 0L;
                if (!awaitingGraphActionResult) {
                    activeGraphActionReqId = 0L;
                }
                return;
            }
            displayGraphActionToast(graphActionToastQueue.removeFirst());
        }

        int availableLeft = graphLeft();
        int availableRight = graphRight();
        if (availableRight - availableLeft < 80) {
            availableLeft = layoutLeft();
            availableRight = layoutRight();
        }
        int maxWidth = Math.max(1, availableRight - availableLeft - 16);
        int toastWidth = Math.min(maxWidth, Math.max(64, font.width(graphActionToast.message()) + 24));
        int toastHeight = GRAPH_TOAST_HEIGHT;
        int toastX = availableLeft + (availableRight - availableLeft - toastWidth) / 2;
        int toastY = Math.max(TOOLBAR_HEIGHT + 8, height - toastHeight - 12);
        drawGraphToastBg(graphics, toastX, toastY, toastWidth, graphActionToast.severity());
        graphics.drawCenteredString(font, graphActionToast.message(),
                toastX + toastWidth / 2, toastY + (toastHeight - 8) / 2, 0xFF1B2024);
    }

    // Draw the graph toast bg
    private static void drawGraphToastBg(GuiGraphics graphics, int x, int y, int width,
                                                       GraphActionToastSeverity severity) {
        int middleWidth = width - GRAPH_TOAST_CAP_WIDTH * 2;
        blitAdvancedSprite(graphics, x, y, GRAPH_TOAST_CAP_WIDTH, GRAPH_TOAST_HEIGHT,
                GRAPH_TOAST_SRC_X, severity.sourceY(), GRAPH_TOAST_CAP_WIDTH, GRAPH_TOAST_HEIGHT);
        blitAdvancedSprite(graphics, x + GRAPH_TOAST_CAP_WIDTH, y, middleWidth, GRAPH_TOAST_HEIGHT,
                GRAPH_TOAST_MIDDLE_SRC_X, severity.sourceY(), GRAPH_TOAST_MIDDLE_SRC_WIDTH, GRAPH_TOAST_HEIGHT);
        blitAdvancedSprite(graphics, x + width - GRAPH_TOAST_CAP_WIDTH, y,
                GRAPH_TOAST_CAP_WIDTH, GRAPH_TOAST_HEIGHT,
                GRAPH_TOAST_RIGHT_SRC_X, severity.sourceY(), GRAPH_TOAST_CAP_WIDTH, GRAPH_TOAST_HEIGHT);
    }

    // Begin the graph action toast
    private long beginGraphActionToast(String msg) {
        long requestId = GRAPH_ACTION_REQ_IDS.incrementAndGet();
        activeGraphActionReqId = requestId;
        awaitingGraphActionResult = true;
        graphActionToastQueue.clear();
        displayGraphActionToast(new GraphActionToast(Component.literal(msg), GraphActionToastSeverity.WARNING));
        return requestId;
    }

    // Queue the graph action result
    private void queueGraphActionResult(long requestId, boolean success, String msg,
                                        int serverRevision, boolean saveAttempted, boolean graphSaved,
                                        List<AdvancedGraphValidator.Diagnostic> diagnostics) {
        if (requestId <= 0L) {
            return;
        }
        AdvancedGraphDocument submittedGraph = pendingGraphSaves.remove(requestId);
        if (requestId == saveOnCloseReqId) {
            saveOnCloseReqId = 0L;
        }
        if (saveAttempted && serverRevision >= 0) {
            if (graphSaved && submittedGraph != null) {
                submittedGraph.setRevision(serverRevision);
                savedDraft = submittedGraph.copy();
                draftDirty = draft != null
                        && !Objects.equals(draft.toTag(), savedDraft.toTag());
            } else if (savedDraft != null) {
                savedDraft.setRevision(serverRevision);
            }
            if (draft != null) {
                draft.setRevision(serverRevision);
            }
        }
        if (requestId != activeGraphActionReqId) {
            return;
        }
        applyFailureDiagnostics(success, diagnostics);
        awaitingGraphActionResult = false;
        GraphActionToast res = new GraphActionToast(Component.literal(msg),
                success ? GraphActionToastSeverity.SUCCESS : GraphActionToastSeverity.ERROR);
        if (graphActionToast == null) {
            displayGraphActionToast(res);
        } else {
            graphActionToastQueue.addLast(res);
        }
    }

    // Apply the failure diagnostics
    private void applyFailureDiagnostics(boolean success, List<AdvancedGraphValidator.Diagnostic> diagnostics) {
        failedNodes.clear();
        failedEdges.clear();
        if (success || diagnostics == null) {
            return;
        }
        for (AdvancedGraphValidator.Diagnostic diagnostic : diagnostics) {
            if (diagnostic == null || !"error".equals(diagnostic.severity())) {
                continue;
            }
            if (!diagnostic.nodeId().isBlank()) {
                failedNodes.add(diagnostic.nodeId());
            }
            if (!diagnostic.edgeId().isBlank()) {
                failedEdges.add(diagnostic.edgeId());
                for (AdvancedGraphDocument.Edge edge : activeEdges()) {
                    if (!diagnostic.edgeId().equals(edge.id())) {
                        continue;
                    }
                    failedNodes.add(edge.fromNode());
                    failedNodes.add(edge.toNode());
                    break;
                }
            }
        }
    }

    // Handle the display graph action toast
    private void displayGraphActionToast(GraphActionToast toast) {
        graphActionToast = toast;
        graphActionToastExpiresAt = Util.getMillis() + GRAPH_ACTION_TOAST_MILLIS;
    }

    // Clear the graph action toast
    private void clearGraphActionToast() {
        graphActionToast = null;
        graphActionToastQueue.clear();
        graphActionToastExpiresAt = 0L;
        activeGraphActionReqId = 0L;
        awaitingGraphActionResult = false;
        pendingGraphSaves.clear();
    }

    // Draw the labels
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    // Draw the tooltip
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hudOpen) super.renderTooltip(graphics, mouseX, mouseY);
    }

    // Draw the slot
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (shouldHideGhostSlot(slot)) return;
        if (!ContraptionNetworkLinkerSlotRenderer.renderControllerSlot(graphics, slot)) {
            super.renderSlot(graphics, slot);
        }
    }

    // Draw the slot highlight
    @Override
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (shouldHideGhostSlot(slot)) return;
        super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
    }

    // Check if this should hide ghost slot
    private boolean shouldHideGhostSlot(Slot slot) {
        return blockingOverlayOpen() && slot instanceof SlotItemHandler itemSlot
                && itemSlot.getItemHandler() == menu.ghostInventory;
    }

    // Check if the blocking overlay is open
    private boolean blockingOverlayOpen() {
        return linkerOpen || shareModalOpen || templatePicker || optionDropdown != null || contextMenu != null
                || miniBrowser != null || hudOpen || graphHistoryOpen || toolsMenuOpen;
    }

    // Check if the node item overlay is open
    private boolean nodeItemOverlayOpen() {
        return blockingOverlayOpen() || frequencyModalOpen || graphActionToast != null;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the advanced contraption controller
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        if (!projectionRendering) {
            RecipeViewerVisibility.setEmiVisible(blockBrowserOpen && !linkerOpen);
        }
        if (!blockBrowserOpen) renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        if (optionDropdown != null) {
            drawOptionDropdown(graphics);
            if (optionDropdown.searchable() && optionDropdownSearch != null) {
                optionDropdownSearch.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        drawGraphActionToast(graphics);
        if (!hudOpen && !blockingOverlayOpen() && !frequencyModalOpen) {
            List<Component> nodeBrowserTooltip = nodeBrowserTooltip(mouseX, mouseY);
            if (nodeBrowserTooltip != null) {
                if (!nodeBrowserTooltip.isEmpty()) {
                    graphics.renderTooltip(font, nodeBrowserTooltip, java.util.Optional.empty(), mouseX, mouseY);
                }
            } else {
                String tooltip = hoverTooltip(mouseX, mouseY);
                if (tooltip != null && !tooltip.isBlank()) {
                    graphics.renderTooltip(font, Component.literal(tooltip), mouseX, mouseY);
                }
            }
        }
    }

    // Pan while dragging the node
    private void panWhileDraggingNode(double mouseX, double mouseY) {
        double deltaX = edgePanDelta(mouseX, graphLeft(), graphRight());
        double deltaY = edgePanDelta(mouseY, graphTop(), graphBottom());
        if (deltaX == 0.0D && deltaY == 0.0D) return;
        panX += deltaX;
        panY += deltaY;
        storeViewport();
        moveSelected(-deltaX / zoom, -deltaY / zoom);
    }

    // Get the edge pan delta
    static double edgePanDelta(double coordinate, double minimum, double maximum) {
        double margin = Math.min(CANVAS_EDGE_PAN_MARGIN, Math.max(0.0D, (maximum - minimum) * 0.5D));
        if (margin == 0.0D) return 0.0D;
        if (coordinate < minimum + margin) {
            double amount = Mth.clamp((minimum + margin - coordinate) / margin, 0.0D, 1.0D);
            return CANVAS_EDGE_PAN_SPEED * amount;
        }
        if (coordinate > maximum - margin) {
            double amount = Mth.clamp((coordinate - maximum + margin) / margin, 0.0D, 1.0D);
            return -CANVAS_EDGE_PAN_SPEED * amount;
        }
        return 0.0D;
    }

    // Check if the pointer moved past the right pan deadzone
    static boolean exceedsRightPanDeadzone(double startX, double startY, double mouseX, double mouseY) {
        double deltaX = mouseX - startX;
        double deltaY = mouseY - startY;
        return deltaX * deltaX + deltaY * deltaY >= RIGHT_PAN_DEADZONE * RIGHT_PAN_DEADZONE;
    }

    // Get the active function
    private AdvancedGraphDocument.FunctionGraph activeFunction() {
        if (draft == null || activeFunctionId == null) {
            return null;
        }
        AdvancedGraphDocument.FunctionGraph function = draft.function(activeFunctionId);
        if (function == null) {
            activeFunctionId = null;
        }
        return function;
    }

    // Get the active nodes
    private List<AdvancedGraphDocument.Node> activeNodes() {
        AdvancedGraphDocument.FunctionGraph function = activeFunction();
        return function == null ? draft.nodes() : function.nodes();
    }

    // Get the active edges
    private List<AdvancedGraphDocument.Edge> activeEdges() {
        AdvancedGraphDocument.FunctionGraph function = activeFunction();
        return function == null ? draft.edges() : function.edges();
    }

    // Get the active groups
    private List<CompoundTag> activeGroups() {
        AdvancedGraphDocument.FunctionGraph function = activeFunction();
        return function == null ? draft.groups() : function.groups();
    }

    // Select the graph tab
    private void selectGraphTab(String functionId) {
        if (Objects.equals(activeFunctionId, functionId)) {
            return;
        }
        storeViewport();
        activeFunctionId = functionId;
        restoreViewport();
        clearSelection();
        clearGraphRenderCache();
    }

    // Get the graph left
    private int graphLeft() {
        return layoutLeft() + activeLeftWidth();
    }

    // Get the graph right
    private int graphRight() {
        return Math.max(graphLeft(), layoutRight() - activeRightWidth());
    }

    // Get the graph top
    private int graphTop() {
        return TOOLBAR_HEIGHT + GRAPH_TAB_HEIGHT;
    }

    // Get the graph bottom
    private int graphBottom() {
        return height;
    }

    // Check if the pointer is in the graph
    private boolean inGraph(double x, double y) {
        return x >= graphLeft() && x < graphRight() && y >= graphTop() && y < graphBottom();
    }

    // Get the layout left
    private int layoutLeft() {
        return 0;
    }

    // Get the layout right
    private int layoutRight() {
        return blockBrowserOpen ? width - recipeViewerLaneWidth() : width;
    }

    // Get the recipe viewer lane width
    private int recipeViewerLaneWidth() {
        return Math.min(360, Math.max(180, width / 5));
    }

    // Get the active left width
    private int activeLeftWidth() {
        return leftSidebarCollapsed ? SIDEBAR_HANDLE_WIDTH : LEFT_WIDTH;
    }

    // Get the active right width
    private int activeRightWidth() {
        return rightSidebarCollapsed ? SIDEBAR_HANDLE_WIDTH : RIGHT_WIDTH;
    }

    // Ensure the sidebar fit
    private void ensureSidebarFit() {
        int available = layoutRight() - layoutLeft();
        if (!rightSidebarCollapsed && available < LEFT_WIDTH + RIGHT_WIDTH + MIN_GRAPH_WIDTH) {
            rightSidebarCollapsed = true;
        }
        if (!leftSidebarCollapsed && available < LEFT_WIDTH + SIDEBAR_HANDLE_WIDTH + MIN_GRAPH_WIDTH) {
            leftSidebarCollapsed = true;
        }
        if (nodeSearch != null) {
            nodeSearch.setVisible(!leftSidebarCollapsed && !blockBrowserOpen);
            nodeSearch.setWidth(Math.max(48, activeLeftWidth() - 16));
        }
        if (blockSearch != null) {
            blockSearch.setVisible(!leftSidebarCollapsed && blockBrowserOpen);
            blockSearch.setWidth(Math.max(48, activeLeftWidth() - 16));
        }
        if (inspectorValue != null && rightSidebarCollapsed) {
            inspectorValue.setVisible(false);
        }
    }

    // Get the stable recipe viewer gui area
    public Rect2i recipeViewerGuiArea() {
        if (linkerOpen) {
            return new Rect2i(0, 0, Math.max(1, width), Math.max(1, height));
        }
        int left = layoutLeft();
        int right = layoutRight();
        return new Rect2i(left, 0, Math.max(1, right - left), Math.max(1, height));
    }

    // Get the recipe viewer exclusion areas
    public List<Rect2i> getRecipeViewerExclusionAreas() {
        if (linkerOpen) {
            return List.of(new Rect2i(0, 0, Math.max(1, width), Math.max(1, height)));
        }
        List<Rect2i> areas = new ArrayList<>();
        if (!leftSidebarCollapsed) {
            areas.add(new Rect2i(layoutLeft(), TOOLBAR_HEIGHT, LEFT_WIDTH, Math.max(0, height - TOOLBAR_HEIGHT)));
        }
        if (!rightSidebarCollapsed) {
            areas.add(new Rect2i(layoutRight() - RIGHT_WIDTH, TOOLBAR_HEIGHT, RIGHT_WIDTH, Math.max(0, height - TOOLBAR_HEIGHT)));
        }
        if (templatePicker) {
            areas.add(new Rect2i((width - 320) / 2, (height - 146) / 2, 320, 146));
        }
        if (contextMenu != null) {
            int menuHeight = Math.max(1, filteredContextItems().size()) * 18 + 8
                    + (contextMenu.query().isBlank() ? 0 : 16);
            areas.add(new Rect2i(contextMenu.x(), contextMenu.y(), 158, menuHeight));
        }
        if (optionDropdown != null) {
            int dropdownHeight = optionDropdownHeight(optionDropdown);
            areas.add(new Rect2i(optionDropdown.x(), optionDropdown.y(), optionDropdown.width(), dropdownHeight));
        }
        if (miniBrowser != null) {
            areas.add(new Rect2i(Mth.clamp(miniBrowser.x(), graphLeft(), graphRight() - MINI_BROWSER_WIDTH),
                    Mth.clamp(miniBrowser.y(), graphTop(), graphBottom() - MINI_BROWSER_HEIGHT),
                    MINI_BROWSER_WIDTH, MINI_BROWSER_HEIGHT));
        }
        if (hudOpen) {
            UiRect bounds = hudBounds();
            areas.add(new Rect2i(bounds.x(), bounds.y(), bounds.width(), bounds.height()));
        }
        return areas;
    }

    // Draw the grid
    private void drawGrid(GuiGraphics graphics) {
        if (v2Ui) {
            drawV2Grid(graphics);
            return;
        }
        int left = graphLeft();
        int top = graphTop();
        int right = graphRight();
        int bottom = graphBottom();
        int tileSize = Math.max(6, (int) Math.round(GRAPH_GRID_BASE_SIZE * zoom));
        int gridSpacing = tileSize;
        while (gridSpacing < 12) {
            gridSpacing += tileSize;
        }
        int lineThickness = Math.max(1, (int) Math.round(tileSize / (double) GRAPH_GRID_BASE_SIZE));
        int xPhase = Math.floorMod((int) Math.round(panX)
                + (int) Math.round(tileSize * GRAPH_GRID_VERTICAL_STRIPE_X / (double) GRAPH_GRID_BASE_SIZE), gridSpacing);
        int yPhase = Math.floorMod((int) Math.round(panY)
                + (int) Math.round(tileSize * GRAPH_GRID_HORIZONTAL_STRIPE_Y / (double) GRAPH_GRID_BASE_SIZE), gridSpacing);

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        addGuiQuad(buffer, pose, left, top, right, bottom, GRAPH_GRID_BACKGROUND_COLOR);
        for (int x = left + xPhase - gridSpacing; x < right; x += gridSpacing) {
            int lineLeft = Math.max(x, left);
            int lineRight = Math.min(x + lineThickness, right);
            if (lineRight > lineLeft) {
                addGuiQuad(buffer, pose, lineLeft, top, lineRight, bottom, GRAPH_GRID_LINE_COLOR);
            }
        }
        for (int y = top + yPhase - gridSpacing; y < bottom; y += gridSpacing) {
            int lineTop = Math.max(y, top);
            int lineBottom = Math.min(y + lineThickness, bottom);
            if (lineBottom > lineTop) {
                addGuiQuad(buffer, pose, left, lineTop, right, lineBottom, GRAPH_GRID_LINE_COLOR);
            }
        }
        graphics.flush();
    }

    // Draw the V2 grid
    private void drawV2Grid(GuiGraphics graphics) {
        int left = graphLeft();
        int top = graphTop();
        int right = graphRight();
        int bottom = graphBottom();
        int spacing = V2_GRID_SPACING;
        int dotSize = zoom >= 1.35D ? 2 : 1;
        int xPhase = Math.floorMod((int) Math.round(panX), spacing);
        int yPhase = Math.floorMod((int) Math.round(panY), spacing);

        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        addGuiQuad(buffer, pose, left, top, right, bottom, AdvancedControllerV2Theme.CANVAS_BACKGROUND);
        for (int y = top + yPhase - spacing; y < bottom; y += spacing) {
            for (int x = left + xPhase - spacing; x < right; x += spacing) {
                if (x >= left && y >= top) {
                    addGuiQuad(buffer, pose, x, y, x + dotSize, y + dotSize,
                            AdvancedControllerV2Theme.CANVAS_DOT);
                }
            }
        }
        graphics.flush();
    }

    // Draw the V2 canvas overlay
    private void drawV2CanvasOverlay(GuiGraphics graphics) {
        if (graphRight() - graphLeft() < 170 || graphBottom() - graphTop() < 120) {
            return;
        }
        drawV2CanvasStatus(graphics);
        drawV2Minimap(graphics);
    }

    // Draw the V2 canvas status
    private void drawV2CanvasStatus(GuiGraphics graphics) {
        String zoomLabel = (int) Math.round(zoom * 100.0D) + "%";
        String nodeLabel = "NODES: " + activeNodes().size();
        UiRect bounds = v2CanvasStatusBounds();
        AdvancedControllerV2Theme.drawPanel(
                graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.drawString(font, "ZOOM", bounds.x() + 8, bounds.y() + 6,
                AdvancedControllerV2Theme.MUTED, false);
        graphics.drawString(font, zoomLabel, bounds.x() + 41, bounds.y() + 6,
                AdvancedControllerV2Theme.PRIMARY, false);
        AdvancedControllerV2Theme.fill(
                graphics, bounds.x() + 41 + font.width(zoomLabel) + 8, bounds.y() + 4,
                bounds.x() + 42 + font.width(zoomLabel) + 8, bounds.y() + 15,
                AdvancedControllerV2Theme.BORDER);
        graphics.drawString(font, nodeLabel,
                bounds.right() - font.width(nodeLabel) - 8, bounds.y() + 6,
                AdvancedControllerV2Theme.SECONDARY, false);
    }

    // Draw the V2 minimap
    private void drawV2Minimap(GuiGraphics graphics) {
        List<AdvancedGraphDocument.Node> nodes = activeNodes();
        UiRect bounds = v2MinimapBounds();
        int width = bounds.width();
        int height = bounds.height();
        int x = bounds.x();
        int y = bounds.y();
        AdvancedControllerV2Theme.drawPanel(graphics, x, y, width, height);
        graphics.drawString(font, "MINIMAP", x + 8, y + 7, AdvancedControllerV2Theme.MUTED, false);
        graphics.drawString(font, "RESET", x + width - font.width("RESET") - 8, y + 7,
                AdvancedControllerV2Theme.PRIMARY, false);
        AdvancedControllerV2Theme.fill(
                graphics, x + 1, y + 21, x + width - 1, y + 22, AdvancedControllerV2Theme.BORDER_SOFT);
        AdvancedControllerMinimapGeometry.Transform transform = v2MinimapDragTransform == null
                ? v2MinimapTransform(nodes) : v2MinimapDragTransform;
        for (AdvancedGraphDocument.Node node : nodes) {
            int nodeLeft = (int) Math.round(transform.mapX(node.x()));
            int nodeTop = (int) Math.round(transform.mapY(node.y()));
            int nodeRight = Math.max(nodeLeft + 2,
                    (int) Math.round(transform.mapX(node.x() + nodeWidth(node))));
            int nodeBottom = Math.max(nodeTop + 2,
                    (int) Math.round(transform.mapY(node.y() + nodeHeight(node))));
            AdvancedGraphCatalog.Definition definition = AdvancedGraphCatalog.get(node.type());
            int col = definition == null
                    ? AdvancedControllerV2Theme.MUTED
                    : nodeTitlebarColor(definition.category());
            AdvancedControllerV2Theme.fill(
                    graphics, nodeLeft, nodeTop, nodeRight, nodeBottom, (col & 0x00FFFFFF) | 0xAA000000);
            AdvancedControllerV2Theme.drawOutline(graphics, nodeLeft, nodeTop,
                    Math.max(1, nodeRight - nodeLeft), Math.max(1, nodeBottom - nodeTop), col);
        }

        AdvancedControllerMinimapGeometry.Rect viewport = v2MinimapViewport(transform);
        if (viewport.width() > 0.0D && viewport.height() > 0.0D) {
            int viewportLeft = (int) Math.floor(viewport.left());
            int viewportTop = (int) Math.floor(viewport.top());
            int viewportRight = Math.max(viewportLeft + 2, (int) Math.ceil(viewport.right()));
            int viewportBottom = Math.max(viewportTop + 2, (int) Math.ceil(viewport.bottom()));
            int viewportColor = AdvancedControllerV2Theme.ACCENT;
            AdvancedControllerV2Theme.fill(graphics, viewportLeft, viewportTop,
                    viewportRight, viewportBottom, (viewportColor & 0x00FFFFFF) | 0x33000000);
            AdvancedControllerV2Theme.drawOutline(graphics, viewportLeft, viewportTop,
                    viewportRight - viewportLeft, viewportBottom - viewportTop, viewportColor);
        }
    }

    // Get the V2 canvas status bounds
    private UiRect v2CanvasStatusBounds() {
        String zoomLabel = (int) Math.round(zoom * 100.0D) + "%";
        String nodeLabel = "NODES: " + activeNodes().size();
        int panelWidth = Math.max(126, font.width(zoomLabel) + font.width(nodeLabel) + 48);
        return new UiRect(graphLeft() + 10, graphBottom() - 27, panelWidth, 19);
    }

    // Get the V2 minimap bounds
    private UiRect v2MinimapBounds() {
        return new UiRect(graphRight() - 132, graphBottom() - 92, 122, 82);
    }

    // Get the V2 minimap transform
    private AdvancedControllerMinimapGeometry.Transform v2MinimapTransform(
            List<AdvancedGraphDocument.Node> nodes) {
        double viewportWorldLeft = graphX(graphLeft());
        double viewportWorldTop = graphY(graphTop());
        double viewportWorldRight = graphX(graphRight());
        double viewportWorldBottom = graphY(graphBottom());
        double minimumX = viewportWorldLeft;
        double minimumY = viewportWorldTop;
        double maximumX = viewportWorldRight;
        double maximumY = viewportWorldBottom;
        for (AdvancedGraphDocument.Node node : nodes) {
            minimumX = Math.min(minimumX, node.x());
            minimumY = Math.min(minimumY, node.y());
            maximumX = Math.max(maximumX, node.x() + nodeWidth(node));
            maximumY = Math.max(maximumY, node.y() + nodeHeight(node));
        }
        double horizontalPadding = Math.max(24.0D, (maximumX - minimumX) * 0.04D);
        double verticalPadding = Math.max(24.0D, (maximumY - minimumY) * 0.04D);
        UiRect bounds = v2MinimapBounds();
        return AdvancedControllerMinimapGeometry.fit(
                minimumX - horizontalPadding, minimumY - verticalPadding,
                maximumX + horizontalPadding, maximumY + verticalPadding,
                bounds.x() + 8, bounds.y() + 28, bounds.width() - 16, bounds.height() - 36);
    }

    // Get the V2 minimap viewport
    private AdvancedControllerMinimapGeometry.Rect v2MinimapViewport(
            AdvancedControllerMinimapGeometry.Transform transform) {
        return AdvancedControllerMinimapGeometry.viewport(
                transform, graphX(graphLeft()), graphY(graphTop()),
                graphX(graphRight()), graphY(graphBottom()));
    }

    // Handle the V2 overlay click
    private boolean clickV2Overlay(double mouseX, double mouseY, int btn) {
        if (graphRight() - graphLeft() < 170 || graphBottom() - graphTop() < 120) {
            return false;
        }
        UiRect minimap = v2MinimapBounds();
        if (minimap.contains(mouseX, mouseY)) {
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && inside(mouseX, mouseY, minimap.right() - 46, minimap.y(), 46, 22)) {
                panX = 150.0D;
                panY = 80.0D;
                zoom = 1.0D;
                draggingV2MinimapViewport = false;
                v2MinimapDragTransform = null;
                storeViewport();
                return true;
            }
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                AdvancedControllerMinimapGeometry.Transform transform =
                        v2MinimapTransform(activeNodes());
                AdvancedControllerMinimapGeometry.Rect content = transform.contentBounds();
                if (content.contains(mouseX, mouseY)) {
                    AdvancedControllerMinimapGeometry.Rect viewport = v2MinimapViewport(transform);
                    if (viewport.contains(mouseX, mouseY)) {
                        v2MinimapDragOffsetX = mouseX - viewport.centerX();
                        v2MinimapDragOffsetY = mouseY - viewport.centerY();
                    } else {
                        v2MinimapDragOffsetX = 0.0D;
                        v2MinimapDragOffsetY = 0.0D;
                        panV2MinimapViewport(mouseX, mouseY, transform);
                    }
                    v2MinimapDragTransform = transform;
                    draggingV2MinimapViewport = true;
                }
            }
            return true;
        }
        return v2CanvasStatusBounds().contains(mouseX, mouseY);
    }

    // Pan the V2 minimap viewport
    private void panV2MinimapViewport(double mouseX, double mouseY,
                                      AdvancedControllerMinimapGeometry.Transform transform) {
        AdvancedControllerMinimapGeometry.Rect content = transform.contentBounds();
        double mapCenterX = Mth.clamp(
                mouseX - v2MinimapDragOffsetX, content.left(), content.right());
        double mapCenterY = Mth.clamp(
                mouseY - v2MinimapDragOffsetY, content.top(), content.bottom());
        double worldCenterX = transform.worldX(mapCenterX);
        double worldCenterY = transform.worldY(mapCenterY);
        panX = AdvancedControllerMinimapGeometry.panForWorldCenter(
                worldCenterX, zoom, graphRight() - graphLeft());
        panY = AdvancedControllerMinimapGeometry.panForWorldCenter(
                worldCenterY, zoom, graphBottom() - graphTop());
        storeViewport();
    }

    // Add the gui quad
    private static void addGuiQuad(VertexConsumer buffer, Matrix4f pose,
                                   int left, int top, int right, int bottom, int col) {
        buffer.addVertex(pose, left, top, 0.0F).setColor(col);
        buffer.addVertex(pose, left, bottom, 0.0F).setColor(col);
        buffer.addVertex(pose, right, bottom, 0.0F).setColor(col);
        buffer.addVertex(pose, right, top, 0.0F).setColor(col);
    }

    // Draw the groups
    private void drawGroups(GuiGraphics graphics) {
        for (CompoundTag group : activeGroups()) {
            int x = screenX(group.getDouble("X"));
            int y = screenY(group.getDouble("Y"));
            int w = (int) (group.getDouble("Width") * zoom);
            int h = (int) (group.getDouble("Height") * zoom);
            if (!intersectsViewport(x, y, w, h,
                    graphLeft(), graphTop(), graphRight(), graphBottom(), 2)) {
                continue;
            }
            int col = group.contains("Color") ? group.getInt("Color") : 0xFF5D9FE3;
            graphics.fill(x, y, x + w, y + h, (col & 0x00FFFFFF) | 0x26000000);
            int titleHeight = Math.max(1, (int) Math.round(18 * zoom));
            int titleOffset = Math.max(1, (int) Math.round(5 * zoom));
            graphics.fill(x, y, x + w, y + titleHeight, (col & 0x00FFFFFF) | 0x77000000);
            graphics.renderOutline(x, y, w, h, group.getString("Id").equals(selectedGroup) ? 0xFFFFFFFF : col);
            String title = group.getString("Title").isBlank() ? "Comment" : group.getString("Title");
            drawNodeString(graphics, title, x + titleOffset, y + titleOffset, 0xFFFFFFFF);
            if (group.getString("Id").equals(selectedGroup)) {
                int handleSize = Math.max(1, (int) Math.round(8 * zoom));
                graphics.fill(x + w - handleSize, y + h - handleSize, x + w, y + h, 0xFFFFFFFF);
            }
        }
    }

    // Draw the edges
    private void drawEdges(GuiGraphics graphics, int mouseX, int mouseY) {
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        AdvancedGraphDocument.Edge hoveredWire = inGraph(mouseX, mouseY)
                ? edgeAt(mouseX, mouseY)
                : null;
        for (AdvancedGraphDocument.Edge edge : activeEdges()) {
            AdvancedGraphDocument.Node from = findNode(edge.fromNode());
            AdvancedGraphDocument.Node to = findNode(edge.toNode());
            if (from == null || to == null) continue;
            Map<String, String> outputs = nodeOutputs(from);
            Map<String, String> inputs = nodeInputs(to);
            String type = outputs.get(edge.fromPort());
            if (type == null || !inputs.containsKey(edge.toPort())) {
                if (failedEdges.contains(edge.id())) {
                    int sourceX = screenX(from.x()) + (int) (nodeWidth(from) * zoom);
                    int sourceY = screenY(from.y()) + (int) (nodeHeight(from) * zoom * 0.5D);
                    int targetX = screenX(to.x());
                    int targetY = screenY(to.y()) + (int) (nodeHeight(to) * zoom * 0.5D);
                    addWire(buffer, pose, sourceX, sourceY, targetX, targetY, GRAPH_FAILURE_COLOR);
                }
                continue;
            }
            PortPosition src = portPosition(from, edge.fromPort(), true);
            PortPosition target = portPosition(to, edge.toPort(), false);
            if (!wireIntersectsViewport(src, target)) {
                continue;
            }
            boolean hovered = hoveredWire != null && hoveredWire.id().equals(edge.id());
            int col = failedEdges.contains(edge.id()) ? GRAPH_FAILURE_COLOR : portColor(type);
            addWire(buffer, pose, src.x(), src.y(), target.x(), target.y(),
                    hovered ? 0xFFFFFFFF : col);
            addExecutionPulse(buffer, pose, edge, src, target, type);
        }
        graphics.flush();
    }

    // Add the execution pulse
    private void addExecutionPulse(VertexConsumer buffer, Matrix4f pose, AdvancedGraphDocument.Edge edge,
                                   PortPosition src, PortPosition target, String type) {
        if (!"exec".equals(type) || minecraft == null || minecraft.level == null) return;
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        long pulse = graphExecutionPulse(controller, GraphRuntime.executionEdgeKey(edge));
        long age = minecraft.level.getGameTime() - pulse;
        if (age < 0 || age > 4) return;
        double t = Math.floorMod(minecraft.level.getGameTime(), 12) / 12.0;
        double smooth = t * t * (3.0 - 2.0 * t);
        GraphWireGeometry.Point point = GraphWireGeometry.pointAt(
                src.x(), src.y(), target.x(), target.y(), smooth);
        int x = (int) Math.round(point.x());
        int y = (int) Math.round(point.y());
        addGuiQuad(buffer, pose, x - 3, y - 3, x + 4, y + 4, 0xFFFFFFFF);
    }

    // Draw the active wire
    private void drawActiveWire(GuiGraphics graphics) {
        AdvancedGraphDocument.Node node = findNode(connectingNode);
        if (node == null) return;
        PortPosition port = portPosition(node, connectingPort, connectingOutput);
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        if (connectingOutput) {
            addWire(buffer, pose, port.x(), port.y(), (int) wireMouseX, (int) wireMouseY, 0xFFFFFFFF);
        } else {
            addWire(buffer, pose, (int) wireMouseX, (int) wireMouseY, port.x(), port.y(), 0xFFFFFFFF);
        }
        graphics.flush();
    }

    // Add the wire
    private static void addWire(VertexConsumer buffer, Matrix4f pose,
                                int x1, int y1, int x2, int y2, int col) {
        for (GraphWireGeometry.Segment segment : GraphWireGeometry.segments(x1, y1, x2, y2)) {
            addOrthogonalSegment(buffer, pose,
                    (int) Math.round(segment.start().x()), (int) Math.round(segment.start().y()),
                    (int) Math.round(segment.end().x()), (int) Math.round(segment.end().y()), col);
        }
    }

    // Add the orthogonal segment
    private static void addOrthogonalSegment(VertexConsumer buffer, Matrix4f pose,
                                             int x1, int y1, int x2, int y2, int col) {
        if (y1 == y2) {
            addGuiQuad(buffer, pose, Math.min(x1, x2), y1, Math.max(x1, x2) + 2, y1 + 2, col);
        } else {
            addGuiQuad(buffer, pose, x1, Math.min(y1, y2), x1 + 2, Math.max(y1, y2) + 2, col);
        }
    }

    // Draw the line
    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int col) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : i / (double) steps;
            int x = (int) Mth.lerp(t, x1, x2);
            int y = (int) Mth.lerp(t, y1, y2);
            graphics.fill(x, y, x + 2, y + 2, col);
        }
    }

    // Draw the nodes
    private void drawNodes(GuiGraphics graphics, double mouseX, double mouseY) {
        AdvancedGraphDocument.Node hoveredNode = Screen.hasControlDown() ? nodeAt(mouseX, mouseY) : null;
        String hoveredVariable = variableNodeName(hoveredNode);
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            int x = screenX(node.x());
            int y = screenY(node.y());
            int w = (int) (nodeWidth(node) * zoom);
            int h = (int) (nodeHeight(node) * zoom);
            if (!intersectsViewport(x, y, w + NODE_FRAME_BODY_WIDTH_PAD, h,
                    graphLeft(), graphTop(), graphRight(), graphBottom(), 12)) {
                continue;
            }
            AdvancedGraphCatalog.Definition definition = AdvancedGraphCatalog.get(node.type());
            if (definition == null) {
                if (failedNodes.contains(node.id())) {
                    graphics.renderOutline(x - 2, y - 2,
                            (int) (NODE_WIDTH * zoom) + 4,
                            (int) (NODE_HEADER * zoom) + 4, GRAPH_FAILURE_COLOR);
                }
                continue;
            }
            boolean selected = selectedNodes.contains(node.id())
                    || !hoveredVariable.isBlank() && hoveredVariable.equals(variableNodeName(node));
            if (isReroute(node)) {
                drawRerouteNode(graphics, node, selected);
                if (failedNodes.contains(node.id())) {
                    graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, GRAPH_FAILURE_COLOR);
                }
                continue;
            }
            if (isStickyNote(node)) {
                drawStickyNote(graphics, node, x, y, w, h, selected);
                if (failedNodes.contains(node.id())) {
                    graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, GRAPH_FAILURE_COLOR);
                }
                continue;
            }
            if (isImageReference(node)) {
                drawImageReference(graphics, node, x, y, w, h, selected);
                if (failedNodes.contains(node.id())) {
                    graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, GRAPH_FAILURE_COLOR);
                }
                continue;
            }
            renderNodeFrame(graphics, x, y, w, h, definition.category(), selected,
                    graphTop() - 2, graphBottom() + 2);
            if (failedNodes.contains(node.id())) {
                graphics.renderOutline(x - 2, y - 2,
                        w + NODE_FRAME_BODY_WIDTH_PAD + 4, h + 4, GRAPH_FAILURE_COLOR);
            }
            if (zoom >= 0.55) {
                drawNodeString(graphics, nodeTitle(node), x + (v2Ui ? 22 : 7), y + 7,
                        nodeTitleTextColor(definition.category()));
                drawRichNodeBody(graphics, node, x, y, w);
                drawPorts(graphics, node, x, y, w);
                drawCollapseHandle(graphics, node, x, y, w, h);
            }
        }
    }

    // Draw the node string
    private void drawNodeString(GuiGraphics graphics, String text, int x, int y, int col) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
        graphics.drawString(font, text, 0, 0, col, false);
        graphics.pose().popPose();
    }

    // Draw the node string
    private void drawNodeString(GuiGraphics graphics, FormattedCharSequence text, int x, int y, int col) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
        graphics.drawString(font, text, 0, 0, col, false);
        graphics.pose().popPose();
    }

    // Draw the node string right
    private void drawNodeStringRight(GuiGraphics graphics, String text, int right, int y, int col) {
        graphics.pose().pushPose();
        graphics.pose().translate(right, y, 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
        graphics.drawString(font, text, -font.width(text), 0, col, false);
        graphics.pose().popPose();
    }

    // Draw the node string centered
    private void drawNodeStringCentered(GuiGraphics graphics, String text, int centerX, int y, int col) {
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, col, false);
        graphics.pose().popPose();
    }

    // Draw the collapse handle
    private void drawCollapseHandle(GuiGraphics graphics, AdvancedGraphDocument.Node node, int x, int y, int width, int height) {
        if (!canCollapse(node)) return;
        int handleHeight = collapseHandleHeight();
        int top = collapseHandleTop(y);
        int inset = Math.max(9, (int) Math.round(9 * zoom));
        renderControllerOption(graphics, x + inset, top, Math.max(1, width - inset * 2), handleHeight,
                AdvancedGraphCatalog.categoryColor(AdvancedGraphCatalog.get(node.type()).category()), false, false);
        String chevron = isNodeCollapsed(node) ? "^" : "v";
        drawNodeStringCentered(graphics, chevron, x + width / 2, top + 1, nodeValueTextColor());
    }

    // Draw the rich node body
    private void drawRichNodeBody(GuiGraphics graphics, AdvancedGraphDocument.Node node, int x, int y, int width) {
        int bodyY = nodeBodyTop(node, y);
        if ("curve".equals(node.type())) {
            drawCurveEditor(graphics, node, x + 7, bodyY, width - 14, (int) (CURVE_BODY_HEIGHT * zoom), false);
            bodyY += (int) (CURVE_BODY_HEIGHT * zoom);
        }
        if (usesBinding(node)) {
            drawBodyControl(graphics, x, bodyY, width, "Key", valueOrUnset(node.data().getString("BindingLabel")),
                    AdvancedGraphCatalog.categoryColor("controller"), false);
            bodyY += (int) (15 * zoom);
        }
        if (hasSwitchTypeControl(node)) {
            drawBodyControl(graphics, x, bodyY, width, "Type",
                    humanPort(AdvancedGraphCatalog.switchType(node)),
                    AdvancedGraphCatalog.categoryColor("logic"), false);
            bodyY += (int) (15 * zoom);
        }
        if (hasPropertyControl(node)) {
            String property = editableProperty(node);
            String propertyLabel = "OutputCount".equals(property)
                    ? AdvancedGraphCatalog.switchDataMode(node)
                    ? "Add data case input"
                    : "Add exec output"
                    : "InputCount".equals(property) ? "Add Exec Input +"
                    : "PulseBehavior".equals(property) ? "Pulse Behavior" : property;
            drawBodyControl(graphics, x, bodyY, width, propertyLabel, propertyValueLabel(node, property),
                    AdvancedGraphCatalog.categoryColor(AdvancedGraphCatalog.get(node.type()).category()),
                    "constant_boolean".equals(node.type()) && node.data().getBoolean(property));
            bodyY += (int) (15 * zoom);
        }
        for (var port : nodeInputs(node).entrySet()) {
            if ("exec".equals(port.getValue()) || ("curve".equals(node.type()) && "value".equals(port.getKey()))) continue;
            if (!isDataPortVisible(node, port.getKey(), false)) continue;
            if (bodyY + 22 >= graphTop() && bodyY <= graphBottom()) {
                boolean inputConnected = isInputConnected(node, port.getKey());
                if (inputConnected) {
                    drawDrivenBodyControl(graphics, node, port.getKey(), port.getValue(), x, bodyY, width);
                } else if ("number".equals(port.getValue())) {
                    drawBodySlider(graphics, node, port.getKey(), x, bodyY, width);
                } else if ("frequency".equals(port.getValue())) {
                    drawBodyFrequency(graphics, node, port.getKey(), x, bodyY, width);
                } else {
                    drawBodyControl(graphics, x, bodyY, width,
                            inputDisplayLabel(node, port.getKey()),
                            inputValueLabel(node, port.getKey(), port.getValue()),
                            portColor(port.getValue()), "boolean".equals(port.getValue()) && inputBoolean(node, port.getKey()),
                            inputControlInset(node, port.getKey()));
                }
                if (!inputConnected && isSetDataForceWriteInput(node, port.getKey())) {
                    drawSetDataForceWriteCheckbox(graphics, node, port.getKey(), x, bodyY);
                }
            }
            bodyY += (int) (15 * zoom);
        }
        if (isHudNode(node) && bodyY + 18 >= graphTop() && bodyY <= graphBottom()) {
            drawBodyControl(graphics, x, bodyY, width, "Add Field", "[+]", AdvancedGraphCatalog.categoryColor("hud"), false);
        } else if (isConstructorNode(node) && bodyY + 18 >= graphTop() && bodyY <= graphBottom()) {
            drawBodyControl(graphics, x, bodyY, width, "Add Input", "[+]",
                    AdvancedGraphCatalog.categoryColor("data"), false);
        } else if (isFunctionInterfaceNode(node) && bodyY + 18 >= graphTop() && bodyY <= graphBottom()) {
            drawBodyControl(graphics, x, bodyY, width,
                    isFunctionInputNode(node) ? "Add Input" : "Add Output", "[+]",
                    AdvancedGraphCatalog.categoryColor("functions"), false);
        }
    }

    // Draw the driven body control
    private void drawDrivenBodyControl(GuiGraphics graphics, AdvancedGraphDocument.Node node, String port, String type,
                                       int x, int y, int width) {
        int right = x + width - 5;
        int rowHeight = Math.max(12, (int) (13 * zoom));
        int controlLeft = x + 5 + inputControlInset(node, port);
        renderControllerOption(graphics, controlLeft, y, right - controlLeft + 1, rowHeight, portColor(type), false);
        String label = inputDisplayLabel(node, port);
        drawNodeString(graphics, trim(label, 11), controlLeft + 6, y + 3, nodeMutedTextColor());
        String shown = trim(wiredInputValueLabel(node, port, type), 11);
        drawNodeStringRight(graphics, shown, right - 3, y + 3, nodeValueTextColor());
    }

    // Draw the body frequency
    private void drawBodyFrequency(GuiGraphics graphics, AdvancedGraphDocument.Node node, String port,
                                   int x, int y, int width) {
        int right = x + width - 5;
        int leadingInset = inputControlInset(node, port);
        int controlLeft = x + 5 + leadingInset;
        renderControllerOption(graphics, controlLeft, y, right - controlLeft + 1,
                Math.max(18, (int) (20 * zoom)),
                portColor("frequency"), false);
        drawNodeString(graphics, "Frequency", controlLeft + 6,
                y + 5, nodeMutedTextColor());
        int firstX = right - 43;
        int secondX = right - 22;
        drawFrequencyGhostSlot(graphics, frequencyStack(node, "FrequencyFirst"), firstX, y, 0xFFE45B67,
                showNodeFreqItem(node, firstX + 1, y + 1));
        drawFrequencyGhostSlot(graphics, frequencyStack(node, "FrequencySecond"), secondX, y, 0xFF5D9FE3,
                showNodeFreqItem(node, secondX + 1, y + 1));
    }

    // Draw the frequency ghost slot
    private void drawFrequencyGhostSlot(GuiGraphics graphics, ItemStack stack, int x, int y, int col,
                                        boolean renderStack) {
        graphics.fill(x, y, x + 18, y + 18, (col & 0x00FFFFFF) | 0x55000000);
        if (renderStack) {
            graphics.renderItem(stack, x + 1, y + 1);
        }
        graphics.renderOutline(x, y, 18, 18, col);
    }

    // Show the node freq item
    private boolean showNodeFreqItem(AdvancedGraphDocument.Node node, int x, int y) {
        if (nodeItemOverlayOpen()) {
            return false;
        }
        boolean afterCurrentNode = false;
        for (AdvancedGraphDocument.Node candidate : activeNodes()) {
            if (!afterCurrentNode) {
                afterCurrentNode = candidate.id().equals(node.id());
                continue;
            }
            if (AdvancedGraphCatalog.get(candidate.type()) == null) {
                continue;
            }
            int candidateX = screenX(candidate.x());
            int candidateY = screenY(candidate.y());
            int candidateWidth = (int) Math.round(nodeWidth(candidate) * zoom);
            int candidateHeight = (int) Math.round(nodeHeight(candidate) * zoom);
            if (x < candidateX + candidateWidth && x + 16 > candidateX
                    && y < candidateY + candidateHeight && y + 16 > candidateY) {
                return false;
            }
        }
        return true;
    }

    // Draw the inspector frequency
    private void drawInspectorFrequency(GuiGraphics graphics, AdvancedGraphDocument.Node node, int x, int y) {
        int right = layoutRight() - 8;
        renderControllerOption(graphics, x + 7, y - 3, right - x - 7, 20, portColor("frequency"), false);
        graphics.drawString(font, "Frequency", x + 14, y + 2, nodeMutedTextColor(), false);
        boolean renderStacks = !nodeItemOverlayOpen();
        drawFrequencyGhostSlot(graphics, frequencyStack(node, "FrequencyFirst"), right - 40, y - 2, 0xFFE45B67,
                renderStacks);
        drawFrequencyGhostSlot(graphics, frequencyStack(node, "FrequencySecond"), right - 19, y - 2, 0xFF5D9FE3,
                renderStacks);
    }

    // Draw the frequency modal
    private void drawFrequencyModal(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect bounds = frequencyModalBounds();
        graphics.fill(layoutLeft(), 0, layoutRight(), height, 0x99000000);
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.drawCenteredString(font, "Redstone Link Frequency", bounds.x() + bounds.width() / 2, bounds.y() + 12, 0xFFFFFFFF);

        int firstX = leftPos + AdvancedContraptionControllerMenu.GHOST_SLOT_OUTPUT_FIRST_X;
        int secondX = leftPos + AdvancedContraptionControllerMenu.GHOST_SLOT_OUTPUT_SECOND_X;
        int slotY = topPos + AdvancedContraptionControllerMenu.GHOST_SLOTS_Y;
        drawPlayerInv(graphics);
        drawFreqSlotBgs(graphics);
        graphics.drawString(font, "A", firstX + 6, slotY - 11, 0xFFE45B67, false);
        graphics.drawString(font, "B", secondX + 6, slotY - 11, 0xFF5D9FE3, false);

        UiRect clear = freqClearBtnBounds();
        renderAdvancedButton(graphics, font, clear.x(), clear.y(), clear.width(), clear.height(),
                Component.literal("Clear Slots"), clear.contains(mouseX, mouseY), true);

        graphics.drawString(font, "Inventory", leftPos + AdvancedContraptionControllerMenu.PLAYER_SLOTS_X,
                topPos + AdvancedContraptionControllerMenu.PLAYER_SLOTS_Y - 12, 0xFF91D9FF, false);
    }

    // Get the frequency modal bounds
    private UiRect frequencyModalBounds() {
        int left = Mth.clamp((width - FREQUENCY_MODAL_WIDTH) / 2, layoutLeft() + 8,
                Math.max(layoutLeft() + 8, layoutRight() - FREQUENCY_MODAL_WIDTH - 8));
        int top = Mth.clamp((height - FREQUENCY_MODAL_HEIGHT) / 2, TOOLBAR_HEIGHT + 4,
                Math.max(TOOLBAR_HEIGHT + 4, height - FREQUENCY_MODAL_HEIGHT - 8));
        return new UiRect(left, top, FREQUENCY_MODAL_WIDTH, FREQUENCY_MODAL_HEIGHT);
    }

    // Get the freq clear btn bounds
    private UiRect freqClearBtnBounds() {
        UiRect bounds = frequencyModalBounds();
        return new UiRect(bounds.right() - 86, bounds.y() + 36, 72, 18);
    }

    // Draw the body slider
    private void drawBodySlider(GuiGraphics graphics, AdvancedGraphDocument.Node node, String port, int x, int y, int width) {
        int left = x + 8;
        int right = x + width - 7;
        String val = compactNumber(inputNumber(node, port));
        SliderTrack track = bodySliderTrack(node, port);
        int trackY = y + Math.max(5, (int) (7 * zoom));
        double[] range = numberRange(node, port);
        double amount = sliderAmount(inputNumber(node, port), range);
        int leadingInset = inputControlInset(node, port);
        int controlLeft = leadingInset == 0 ? left : x + 5 + leadingInset;
        renderControllerOption(graphics, controlLeft, y, right - controlLeft + 1,
                Math.max(12, (int) (13 * zoom)),
                portColor("number"), port.equals(selectedInputPort));
        drawNodeString(graphics, trim(inputDisplayLabel(node, port), 8),
                controlLeft + 5, y + 3,
                nodeMutedTextColor());
        renderControllerSlider(graphics, track.left(), trackY, track.width(), amount, portColor("number"),
                node.id().equals(draggingSliderNode) && port.equals(draggingSliderPort));
        drawNodeStringRight(graphics, val, right - 2, y + 3, nodeValueTextColor());
    }

    // Get the body slider track
    private SliderTrack bodySliderTrack(AdvancedGraphDocument.Node node, String port) {
        int x = screenX(node.x());
        int width = (int) (NODE_WIDTH * zoom);
        int right = x + width - 7;
        int valueWidth = sliderValueWidth(node, port, 28, 6);
        int left = x + Math.max(58, width / 2);
        int trackRight = right - valueWidth - 4;
        return new SliderTrack(left, Math.max(left + 1, trackRight));
    }

    // Draw the body output slider
    private void drawBodyOutputSlider(
            GuiGraphics graphics, AdvancedGraphDocument.Node node,
            String port, int x, int y, int width
    ) {
        int left = x + 8;
        int right = x + width - 7;
        double val = outputNumber(node, port);
        SliderTrack track = bodyOutputSliderTrack(node, port);
        int trackY = y + Math.max(5, (int) (7 * zoom));
        double amount = sliderAmount(val, numberRange(node, port));
        String selection = outputSelection(port);
        renderControllerOption(graphics, left, y, right - left + 1,
                Math.max(12, (int) (13 * zoom)), portColor("number"),
                selection.equals(selectedInputPort));
        drawNodeString(graphics, trim("Output " + humanPort(port), 8),
                left + 5, y + 3, nodeMutedTextColor());
        renderControllerSlider(graphics, track.left(), trackY, track.width(),
                amount, portColor("number"),
                draggingOutputSlider && node.id().equals(draggingSliderNode)
                        && port.equals(draggingSliderPort));
        drawNodeStringRight(graphics, compactNumber(val), right - 2, y + 3,
                nodeValueTextColor());
    }

    // Get the body output slider track
    private SliderTrack bodyOutputSliderTrack(
            AdvancedGraphDocument.Node node, String port
    ) {
        int x = screenX(node.x());
        int width = (int) (NODE_WIDTH * zoom);
        int right = x + width - 7;
        int valueWidth = outputSliderValueWidth(node, port, 28, 6);
        int left = x + Math.max(58, width / 2);
        int trackRight = right - valueWidth - 4;
        return new SliderTrack(left, Math.max(left + 1, trackRight));
    }

    // Get the inspector slider track
    private SliderTrack inspectorSliderTrack(AdvancedGraphDocument.Node node, String port) {
        int x = graphRight();
        int right = layoutRight() - 8;
        int valueWidth = sliderValueWidth(node, port, 30, 8);
        int left = x + 82 + inspectorInlineMapPortIndent(node, port, false);
        int trackRight = right - valueWidth - 4;
        return new SliderTrack(left, Math.max(left + 1, trackRight));
    }

    // Get the inspector output slider track
    private SliderTrack inspectorOutputSliderTrack(
            AdvancedGraphDocument.Node node, String port
    ) {
        int x = graphRight();
        int right = layoutRight() - 8;
        int valueWidth = outputSliderValueWidth(node, port, 30, 8);
        int left = x + 82;
        int trackRight = right - valueWidth - 4;
        return new SliderTrack(left, Math.max(left + 1, trackRight));
    }

    // Get the slider value width
    private int sliderValueWidth(AdvancedGraphDocument.Node node, String port, int minimum, int padding) {
        double[] range = numberRange(node, port);
        int width = font.width(compactNumber(inputNumber(node, port)));
        width = Math.max(width, font.width(compactNumber(range[0])));
        width = Math.max(width, font.width(compactNumber(range[1])));
        return Math.max(minimum, width + padding);
    }

    // Get the output slider value width
    private int outputSliderValueWidth(
            AdvancedGraphDocument.Node node, String port, int minimum, int padding
    ) {
        double[] range = numberRange(node, port);
        int width = font.width(compactNumber(outputNumber(node, port)));
        width = Math.max(width, font.width(compactNumber(range[0])));
        width = Math.max(width, font.width(compactNumber(range[1])));
        return Math.max(minimum, width + padding);
    }

    // Draw the body control
    private void drawBodyControl(GuiGraphics graphics, int x, int y, int width, String label,
                                 String val, int col, boolean checked) {
        drawBodyControl(graphics, x, y, width, label, val, col, checked, 0);
    }

    // Draw the body control
    private void drawBodyControl(GuiGraphics graphics, int x, int y, int width, String label,
                                 String val, int col, boolean checked, int labelOffset) {
        int right = x + width - 5;
        int controlLeft = x + 5 + labelOffset;
        renderControllerOption(graphics, controlLeft, y, right - controlLeft + 1,
                Math.max(12, (int) (13 * zoom)),
                col, false);
        drawNodeString(graphics, trim(label, 11), controlLeft + 6, y + 3,
                nodeMutedTextColor());
        String shown = checked ? "[x]" : val;
        drawNodeStringRight(graphics, trim(shown, 11), right - 3, y + 3, nodeValueTextColor());
    }

    // Draw the ports
    private void drawPorts(GuiGraphics graphics, AdvancedGraphDocument.Node node, int x, int y, int width) {
        for (var port : nodeInputs(node).entrySet()) {
            if ("exec".equals(port.getValue())) {
                PortPosition pos = portPosition(node, port.getKey(), false);
                if (!portWithinViewport(pos)) continue;
                drawExecDiamond(graphics, pos);
                if (isExecutionCombinerNode(node)) {
                    drawNodeString(graphics, humanPort(port.getKey()),
                            pos.x() + 9, pos.y() - 4, 0xFFC8D7E3);
                }
                continue;
            }
            if (!isDataPortVisible(node, port.getKey(), false)) continue;
            PortPosition pos = portPosition(node, port.getKey(), false);
            if (!portWithinViewport(pos)) continue;
            renderDataPort(graphics, pos.x(), pos.y(), port.getValue(), false);
        }
        for (var port : nodeOutputs(node).entrySet()) {
            if ("exec".equals(port.getValue())) {
                PortPosition pos = portPosition(node, port.getKey(), true);
                if (!portWithinViewport(pos)) continue;
                drawExecDiamond(graphics, pos);
                if ("branch".equals(node.type()) || isExecutionSplitterNode(node)
                        || isShipCompletionPort(node, port.getKey())) {
                    String label = humanPort(port.getKey());
                    drawNodeStringRight(graphics, label, x + width - 9, pos.y() - 4, 0xFFC8D7E3);
                }
                continue;
            }
            if (!isDataPortVisible(node, port.getKey(), true)) continue;
            PortPosition pos = portPosition(node, port.getKey(), true);
            int py = pos.y();
            if (!portWithinViewport(pos)) continue;
            renderDataPort(graphics, pos.x(), py, port.getValue(), true);
            drawWrappedOutputPortLabel(graphics, node, port.getKey(),
                    x + width - 9 - inlineMapPortIndent(node, port.getKey(), true), py);
        }
    }

    // Check if the port is inside the viewport
    private boolean portWithinViewport(PortPosition pos) {
        return pos.y() >= graphTop() - 12 && pos.y() <= graphBottom() + 12
                && pos.x() >= graphLeft() - 12 && pos.x() <= graphRight() + 12;
    }

    // Check if the wire intersects the viewport
    private boolean wireIntersectsViewport(PortPosition src, PortPosition target) {
        int left = Math.min(src.x(), target.x());
        int top = Math.min(src.y(), target.y());
        int width = Math.abs(src.x() - target.x());
        int height = Math.abs(src.y() - target.y());
        return intersectsViewport(left, top, width, height,
                graphLeft(), graphTop(), graphRight(), graphBottom(), 16);
    }

    // Draw the sticky note
    private void drawStickyNote(GuiGraphics graphics, AdvancedGraphDocument.Node node, int x, int y,
                                int width, int height, boolean selected) {
        int border = selected ? 0xFFFFFFFF : 0xFF8A7424;
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFFF4D96B);
        int titleHeight = Math.max(16, (int) Math.round(20 * zoom));
        graphics.fill(x + 1, y + 1, x + width - 1, y + titleHeight, 0xFFE5C752);
        if (zoom >= 0.55D) {
            drawNodeString(graphics, nodeTitle(node), x + 7, y + 6, 0xFF3C3318);
            int textLeft = x + 7;
            int textTop = y + titleHeight + 5;
            int textRight = x + width - 7;
            int textBottom = y + height - 9;
            graphics.enableScissor(textLeft, textTop, textRight, textBottom);
            int lineY = textTop;
            int lineHeight = Math.max(1, (int) Math.round(font.lineHeight * zoom));
            int wrapWidth = Math.max(20, (int) Math.floor((textRight - textLeft) / zoom));
            String text = stickyText(node);
            if (node.id().equals(editingStickyNode)) {
                drawStickyEditor(graphics, text, textLeft, textTop, textRight, textBottom);
            } else {
                for (String rawLine : text.split("\\R", -1)) {
                    Component line = stickyMarkdownLine(rawLine);
                    for (FormattedCharSequence wrapped : font.split(line, wrapWidth)) {
                        if (lineY + lineHeight > textBottom) break;
                        drawNodeString(graphics, wrapped, textLeft, lineY, 0xFF3C3318);
                        lineY += lineHeight;
                    }
                    if (lineY + lineHeight > textBottom) break;
                }
            }
            graphics.disableScissor();
        }
        int handle = Math.max(7, (int) Math.round(9 * zoom));
        graphics.fill(x + width - handle, y + height - 2, x + width, y + height, 0xFF7D681D);
        graphics.fill(x + width - 2, y + height - handle, x + width, y + height, 0xFF7D681D);
    }

    // Draw the sticky editor
    private void drawStickyEditor(GuiGraphics graphics, String text, int left, int top, int right, int bottom) {
        int logicalWidth = Math.max(20, (int) Math.floor((right - left) / zoom));
        int lineHeight = Math.max(1, (int) Math.round(font.lineHeight * zoom));
        List<StickyEditLine> lines = stickyEditLines(text, logicalWidth);
        int selectionStart = Math.min(stickyCaret, stickySelectionAnchor);
        int selectionEnd = Math.max(stickyCaret, stickySelectionAnchor);
        int caretLine = stickyCaretLine(lines);
        int y = top;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            StickyEditLine line = lines.get(lineIndex);
            if (y + lineHeight > bottom) break;
            int highlightedStart = Math.max(selectionStart, line.start());
            int highlightedEnd = Math.min(selectionEnd, line.end());
            if (highlightedStart < highlightedEnd) {
                int startX = left + (int) Math.round(
                        font.width(text.substring(line.start(), highlightedStart)) * zoom);
                int endX = left + (int) Math.round(
                        font.width(text.substring(line.start(), highlightedEnd)) * zoom);
                graphics.fill(startX, y - 1, endX, y + lineHeight, 0x665A83B0);
            }
            drawNodeString(graphics, line.text(), left, y, 0xFF3C3318);
            if (lineIndex == caretLine
                    && (Util.getMillis() / 500L) % 2L == 0L) {
                int caretX = left + (int) Math.round(
                        font.width(text.substring(line.start(), stickyCaret)) * zoom);
                graphics.fill(caretX, y - 1, caretX + 1, y + lineHeight, 0xFF2B2410);
            }
            y += lineHeight;
        }
    }

    // Draw the image reference
    private void drawImageReference(GuiGraphics graphics, AdvancedGraphDocument.Node node,
                                    int x, int y, int width, int height, boolean selected) {
        renderNodeFrame(graphics, x, y, width, height, "core", selected);
        int titleHeight = Math.max(16, (int) Math.round(22 * zoom));
        if (zoom >= 0.55D) {
            drawNodeString(graphics, nodeTitle(node), x + (v2Ui ? 22 : 7), y + 7,
                    nodeTitleTextColor("core"));
        }
        int inset = Math.max(5, (int) Math.round(6 * zoom));
        int imageLeft = x + inset;
        int imageTop = y + titleHeight + 1;
        int imageWidth = Math.max(1, width - inset * 2);
        int imageHeight = Math.max(1, height - titleHeight - inset - 2);
        if (!AdvancedHudImageClient.draw(
                graphics, node.data(),
                imageLeft, imageTop, imageWidth, imageHeight)) {
            graphics.fill(imageLeft, imageTop,
                    imageLeft + imageWidth, imageTop + imageHeight, 0xAA17232D);
            String hint = pendingEmbeddedImageNodes.contains(node.id()) ? "Converting image..."
                    : node.data().getString("Source").isBlank() ? "Set URL or upload" : "Loading image...";
            graphics.drawCenteredString(font, trim(hint, 24),
                    imageLeft + imageWidth / 2,
                    imageTop + Math.max(2, (imageHeight - font.lineHeight) / 2), 0xFFA9C4D5);
        }
        int handle = Math.max(7, (int) Math.round(9 * zoom));
        renderControllerOption(graphics, x + width - handle, y + height - handle,
                handle, handle, nodeTitlebarColor("core"), false, selected);
    }

    // Get the sticky markdown line
    private static Component stickyMarkdownLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine;
        if (line.startsWith("### ")) return Component.literal(line.substring(4)).withStyle(ChatFormatting.BOLD);
        if (line.startsWith("## ")) return Component.literal(line.substring(3)).withStyle(ChatFormatting.BOLD);
        if (line.startsWith("# ")) return Component.literal(line.substring(2)).withStyle(ChatFormatting.BOLD, ChatFormatting.UNDERLINE);
        if (line.startsWith("- ")) return Component.literal("• " + line.substring(2));
        if (line.length() >= 4 && line.startsWith("**") && line.endsWith("**")) {
            return Component.literal(line.substring(2, line.length() - 2)).withStyle(ChatFormatting.BOLD);
        }
        if (line.length() >= 2 && line.startsWith("`") && line.endsWith("`")) {
            return Component.literal(line.substring(1, line.length() - 1)).withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.literal(line);
    }

    // Get the sticky text
    private static String stickyText(AdvancedGraphDocument.Node node) {
        return node == null ? "" : node.data().getString("Text").replace("\\n", "\n");
    }

    // Get the sticky edit lines
    private List<StickyEditLine> stickyEditLines(String text, int maxWidth) {
        String val = text == null ? "" : text;
        List<StickyEditLine> lines = new ArrayList<>();
        int start = 0;
        int width = 0;
        for (int idx = 0; idx < val.length(); idx++) {
            char character = val.charAt(idx);
            if (character == '\n') {
                lines.add(new StickyEditLine(val.substring(start, idx), start, idx));
                start = idx + 1;
                width = 0;
                continue;
            }
            int characterWidth = font.width(String.valueOf(character));
            if (idx > start && width + characterWidth > maxWidth) {
                lines.add(new StickyEditLine(val.substring(start, idx), start, idx));
                start = idx;
                width = 0;
            }
            width += characterWidth;
        }
        lines.add(new StickyEditLine(val.substring(start), start, val.length()));
        return lines;
    }

    // Draw the wrapped output port label
    private void drawWrappedOutputPortLabel(GuiGraphics graphics, AdvancedGraphDocument.Node node,
                                            String port, int right, int centerY) {
        NodePortLayout layout = graphRenderPortLayouts.get(node.id());
        List<FormattedCharSequence> lines = graphRenderCacheDocument == draft && layout != null
                ? layout.outputLabels().get(port) : null;
        if (lines == null) {
            lines = wrappedOutputPortLabel(node, port);
        }
        int y = -lines.size() * font.lineHeight / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(right, centerY, 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, -font.width(line), y, nodeMutedTextColor(), false);
            y += font.lineHeight;
        }
        graphics.pose().popPose();
    }

    // Get the wrapped output port label
    private List<FormattedCharSequence> wrappedOutputPortLabel(AdvancedGraphDocument.Node node, String port) {
        int availableWidth = NODE_WIDTH - 18 - inspectorInlineMapPortIndent(node, port, true);
        return font.split(Component.literal(outputDisplayLabel(node, port)), availableWidth);
    }

    // Get the output data port row height
    private int outputDataPortRowHeight(AdvancedGraphDocument.Node node, String port) {
        int normalHeight = 13;
        return Math.max(normalHeight, wrappedOutputPortLabel(node, port).size() * font.lineHeight);
    }

    // Get the output data port offset
    private int outputDataPortOffset(AdvancedGraphDocument.Node node, String port) {
        NodePortLayout layout = graphRenderCacheDocument == draft ? graphRenderPortLayouts.get(node.id()) : null;
        if (layout != null) {
            return layout.outputOffsets().getOrDefault(port, 0);
        }
        int offset = 0;
        for (var entry : nodeOutputs(node).entrySet()) {
            if ("exec".equals(entry.getValue()) || !isDataPortVisible(node, entry.getKey(), true)) {
                continue;
            }
            int rowHeight = outputDataPortRowHeight(node, entry.getKey());
            if (entry.getKey().equals(port)) {
                return offset + Math.max(0, (rowHeight - font.lineHeight) / 2);
            }
            offset += rowHeight;
        }
        return offset;
    }

    // Get the output data ports height
    private int outputDataPortsHeight(AdvancedGraphDocument.Node node) {
        NodePortLayout layout = graphRenderCacheDocument == draft ? graphRenderPortLayouts.get(node.id()) : null;
        if (layout != null) {
            return layout.outputDataHeight();
        }
        int height = 0;
        for (var entry : nodeOutputs(node).entrySet()) {
            if (!"exec".equals(entry.getValue()) && isDataPortVisible(node, entry.getKey(), true)) {
                height += outputDataPortRowHeight(node, entry.getKey());
            }
        }
        return height;
    }

    // Draw the reroute node
    private void drawRerouteNode(GuiGraphics graphics, AdvancedGraphDocument.Node node, boolean selected) {
        String inputType = nodeInputs(node).getOrDefault("value", "any");
        String outputType = nodeOutputs(node).getOrDefault("value", inputType);
        PortPosition input = portPosition(node, "value", false);
        PortPosition output = portPosition(node, "value", true);
        int x = screenX(node.x());
        int y = screenY(node.y());
        int width = Math.max(10, (int) Math.round(REROUTE_WIDTH * zoom));
        int height = Math.max(8, (int) Math.round(REROUTE_HEIGHT * zoom));
        renderControllerOption(graphics, x + 2, y, Math.max(6, width - 4), height,
                AdvancedGraphCatalog.categoryColor("core"), selected, false);
        if ("exec".equals(inputType) || "exec".equals(outputType)) {
            drawExecDiamond(graphics, input);
            drawExecDiamond(graphics, output);
        } else {
            renderDataPort(graphics, input.x(), input.y(), inputType, false);
            renderDataPort(graphics, output.x(), output.y(), outputType, true);
        }
    }

    // Draw the exec diamond
    private void drawExecDiamond(GuiGraphics graphics, PortPosition pos) {
        int size = Math.max(6, (int) (9 * zoom));
        if (v2Ui) {
            AdvancedControllerV2Theme.drawExecPort(graphics, pos.x(), pos.y(), size);
            return;
        }
        blitAdvancedSprite(graphics, pos.x() - size / 2, pos.y() - size / 2, size, size,
                EXEC_DIAMOND_SRC_X, EXEC_DIAMOND_SRC_Y, EXEC_DIAMOND_SRC_W, EXEC_DIAMOND_SRC_H);
    }

    // Get the port start
    private int portStart(AdvancedGraphDocument.Node node) {
        return nodeBodyTopUnits(node) + 6 + bodyControlCount(node) * 15
                + ("curve".equals(node.type()) ? CURVE_BODY_HEIGHT : 0);
    }

    private int nodeBodyTopUnits(AdvancedGraphDocument.Node node) {
        return NODE_BODY_TOP + (canCollapse(node) ? NODE_COLLAPSE_PORT_OFFSET : 0);
    }

    private int nodeBodyTop(AdvancedGraphDocument.Node node, int nodeScreenY) {
        return nodeScreenY + (int) Math.round(nodeBodyTopUnits(node) * zoom);
    }

    private int collapseHandleHeight() {
        return Math.max(7, (int) Math.round(NODE_COLLAPSE_HANDLE_HEIGHT * zoom));
    }

    private int collapseHandleTop(int nodeScreenY) {
        return nodeScreenY + (int) Math.round(NODE_HEADER * zoom);
    }

    // Get the node height
    private int nodeHeight(AdvancedGraphDocument.Node node) {
        if (isReroute(node)) return REROUTE_HEIGHT;
        if (isStickyNote(node)) {
            int height = node.data().contains("Height") ? node.data().getInt("Height") : STICKY_NOTE_DEFAULT_HEIGHT;
            return Mth.clamp(height, STICKY_NOTE_MIN_HEIGHT, 600);
        }
        if (isImageReference(node)) {
            int height = node.data().contains("Height") ? node.data().getInt("Height") : 180;
            return Mth.clamp(height, IMAGE_REFERENCE_MIN_HEIGHT, 800);
        }
        int outputHeight = outputDataPortsHeight(node);
        if ("branch".equals(node.type())) outputHeight = Math.max(outputHeight, 2 * 13);
        if (isExecutionSplitterNode(node)) outputHeight = Math.max(outputHeight, executionOutputCount(node) * 13);
        if (isExecutionCombinerNode(node)) outputHeight = Math.max(outputHeight, executionInputCount(node) * 13);
        if (AdvancedGraphCatalog.isShipControlCompletionType(node.type())) outputHeight += 13;
        return Math.max(54, portStart(node) + outputHeight + NODE_EXTRA_HEIGHT + NODE_FRAME_BODY_HEIGHT_PAD);
    }

    // Get the node width
    private int nodeWidth(AdvancedGraphDocument.Node node) {
        if (isReroute(node)) return REROUTE_WIDTH;
        if (isStickyNote(node)) {
            int width = node.data().contains("Width") ? node.data().getInt("Width") : STICKY_NOTE_DEFAULT_WIDTH;
            return Mth.clamp(width, STICKY_NOTE_MIN_WIDTH, 800);
        }
        if (isImageReference(node)) {
            int width = node.data().contains("Width") ? node.data().getInt("Width") : 240;
            return Mth.clamp(width, IMAGE_REFERENCE_MIN_WIDTH, 1000);
        }
        return NODE_WIDTH;
    }

    // Check if this is reroute
    private static boolean isReroute(AdvancedGraphDocument.Node node) {
        return node != null && "reroute".equals(node.type());
    }

    // Check if this is sticky note
    private static boolean isStickyNote(AdvancedGraphDocument.Node node) {
        return node != null && "sticky_note".equals(node.type());
    }

    // Check if this is image reference
    private static boolean isImageReference(AdvancedGraphDocument.Node node) {
        return node != null && "image_reference".equals(node.type());
    }

    // Check if this is a function input node
    private static boolean isFunctionInputNode(AdvancedGraphDocument.Node node) {
        return node != null && AdvancedGraphFunctions.INPUT_TYPE.equals(node.type());
    }

    // Check if this is a function output node
    private static boolean isFunctionOutputNode(AdvancedGraphDocument.Node node) {
        return node != null && AdvancedGraphFunctions.OUTPUT_TYPE.equals(node.type());
    }

    // Check if this is a function interface node
    private static boolean isFunctionInterfaceNode(AdvancedGraphDocument.Node node) {
        return isFunctionInputNode(node) || isFunctionOutputNode(node);
    }

    // Get the body control count
    private int bodyControlCount(AdvancedGraphDocument.Node node) {
        int count = (usesBinding(node) ? 1 : 0) + propertyControlCount(node);
        for (var port : nodeInputs(node).entrySet()) {
            if (!"exec".equals(port.getValue())
                    && !("curve".equals(node.type()) && "value".equals(port.getKey()))
                    && isDataPortVisible(node, port.getKey(), false)) count++;
        }
        if (isHudNode(node) || isConstructorNode(node) || isFunctionInterfaceNode(node)) count++;
        return count;
    }

    // Check if this can collapse
    private boolean canCollapse(AdvancedGraphDocument.Node node) {
        NodePortLayout layout = graphRenderPortLayouts.get(node.id());
        if (graphRenderCacheDocument == draft && layout != null) {
            return layout.collapsible();
        }
        return nonExecPortCount(AdvancedGraphCatalog.inputs(node))
                + nonExecPortCount(AdvancedGraphCatalog.outputs(node)) > 3;
    }

    // Check if this is node collapsed
    private boolean isNodeCollapsed(AdvancedGraphDocument.Node node) {
        if (node == null) return false;
        NodePortLayout layout = graphRenderCacheDocument == draft
                ? graphRenderPortLayouts.get(node.id()) : null;
        if (layout != null) {
            return layout.collapsed();
        }
        return collapsedNodes.contains(node.id()) || isPersistedNodeCollapsed(node);
    }

    // Set the node collapsed
    private void setNodeCollapsed(AdvancedGraphDocument.Node node, boolean collapsed) {
        if (node == null) return;
        if (collapsed) {
            collapsedNodes.add(node.id());
        } else {
            collapsedNodes.remove(node.id());
        }
        setPersistedNodeCollapsed(node, collapsed);
    }

    // Check if this is persisted node collapsed
    static boolean isPersistedNodeCollapsed(AdvancedGraphDocument.Node node) {
        return node != null && node.data().getBoolean(AdvancedGraphDocument.EDITOR_COLLAPSED_KEY);
    }

    // Set the persisted node collapsed
    static void setPersistedNodeCollapsed(AdvancedGraphDocument.Node node, boolean collapsed) {
        if (node == null) return;
        if (collapsed) {
            node.data().putBoolean(AdvancedGraphDocument.EDITOR_COLLAPSED_KEY, true);
        } else {
            node.data().remove(AdvancedGraphDocument.EDITOR_COLLAPSED_KEY);
        }
    }

    // Check if the data port is visible
    private boolean isDataPortVisible(AdvancedGraphDocument.Node node, String port, boolean output) {
        if (graphRenderCacheDocument != draft) {
            prepareGraphRenderCache();
        }
        NodePortLayout layout = graphRenderPortLayouts.get(node.id());
        if (graphRenderCacheDocument == draft && layout != null) {
            if (!layout.collapsed() || !layout.collapsible()) return true;
            return (output ? layout.visibleOutputs() : layout.visibleInputs()).contains(port);
        }
        return !isNodeCollapsed(node) || !canCollapse(node);
    }

    // Get the non exec port count
    private static int nonExecPortCount(Map<String, String> ports) {
        return (int) ports.values().stream().filter(type -> !"exec".equals(type)).count();
    }

    // Get the port color
    static int portColor(String type) {
        if (type == null) return 0xFFB0BAC5;
        return switch (type) {
            case "exec" -> 0xFFFFFFFF;
            case "boolean" -> 0xFFE45B67;
            case "number" -> 0xFF69D19A;
            case "string" -> 0xFFE79DD2;
            case "direction" -> 0xFFF0B85C;
            case "frequency", "target" -> 0xFF9B8DF1;
            case "list", "map" -> 0xFF65C8D0;
            default -> 0xFFB0BAC5;
        };
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         NODE BROWSER
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the left browser
    private void drawLeftBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = layoutLeft();
        if (leftSidebarCollapsed) {
            renderSidebarPanel(graphics, left, TOOLBAR_HEIGHT, SIDEBAR_HANDLE_WIDTH, height - TOOLBAR_HEIGHT);
            return;
        }
        renderSidebarPanel(graphics, left, TOOLBAR_HEIGHT, activeLeftWidth(), height - TOOLBAR_HEIGHT);
        graphics.fill(left + activeLeftWidth() - 1, TOOLBAR_HEIGHT, left + activeLeftWidth(), height,
                v2Ui ? AdvancedControllerV2Theme.BORDER : 0xFF344A5C);
        graphics.drawString(font, blockBrowserOpen ? "Block Browser" : v2Ui ? "NODE LIBRARY" : "Node Browser",
                left + 8, 62, v2Ui ? AdvancedControllerV2Theme.MUTED : 0xFF91D9FF, false);
        if (v2Ui) {
            EditBox search = blockBrowserOpen ? blockSearch : nodeSearch;
            if (search != null && search.isVisible()) {
                AdvancedControllerV2Theme.drawRoundedRect(
                        graphics, search.getX() - 2, search.getY() - 1,
                        search.getWidth() + 4, search.getHeight() + 2, 4,
                        AdvancedControllerV2Theme.BORDER);
                AdvancedControllerV2Theme.drawRoundedRect(
                        graphics, search.getX() - 1, search.getY(),
                        search.getWidth() + 2, search.getHeight(), 3,
                        AdvancedControllerV2Theme.CANVAS_BACKGROUND);
            }
        }
        if (blockBrowserOpen) drawBlockBrowser(graphics, mouseX, mouseY);
        else drawNodeBrowser(graphics, mouseX, mouseY);
    }

    // Draw the node browser
    private void drawNodeBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        if (v2Ui) {
            drawV2NodeBrowser(graphics, mouseX, mouseY);
            return;
        }
        int left = layoutLeft();
        String query = nodeSearch == null ? "" : nodeSearch.getValue().trim().toLowerCase(Locale.ROOT);
        List<BrowserEntry> entries = nodeBrowserEntries(query);
        int y = 86 - browserScroll;
        graphics.enableScissor(left, 82, left + activeLeftWidth(), height);
        for (BrowserEntry entry : entries) {
            if (y >= 78 && y < height) {
                if (entry.category()) {
                    renderControllerOption(graphics, left + 5, y - 2, activeLeftWidth() - 10, 16,
                            nodeTitlebarColor(entry.id()), false, false);
                    drawCategoryChevron(graphics, font, left + 8, y, 12, entry.id(),
                            collapsedCategories.contains(entry.id()));
                    graphics.drawString(font, AdvancedGraphCatalog.categoryName(entry.id()), left + 25, y + 1,
                            nodeTitleTextColor(entry.id()), false);
                } else {
                    AdvancedGraphCatalog.Definition definition = AdvancedGraphCatalog.get(entry.id());
                    boolean hover = mouseX >= left + 8 && mouseX < left + activeLeftWidth() - 8 && mouseY >= y - 1 && mouseY < y + 13;
                    if (hover) graphics.fill(left + 8, y - 1, left + activeLeftWidth() - 7, y + 13, 0x663B617C);
                    renderCategorySwatch(graphics, left + 15, y + 6, browserEntryCategory(entry.id()));
                    boolean function = entry.id().startsWith("function:");
                    graphics.drawString(font, trim(browserEntryName(entry.id()), function ? 24 : 29),
                            left + 24, y + 1, 0xFFD7E8F4, false);
                    if (function && hover) {
                        int deleteX = left + activeLeftWidth() - 24;
                        CTCreateScreenHelper.renderTextButton(
                                graphics, font, deleteX, y - 1, 15, 14,
                                Component.literal("X"), mouseX >= deleteX, true, false);
                    }
                }
            }
            y += entry.category() ? 19 : 15;
        }
        graphics.disableScissor();
    }

    // Draw the V2 node browser
    private void drawV2NodeBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = layoutLeft();
        String query = nodeSearch == null ? "" : nodeSearch.getValue().trim().toLowerCase(Locale.ROOT);
        List<BrowserEntry> entries = nodeBrowserEntries(query);
        int y = 86 - browserScroll;
        graphics.enableScissor(left, 82, left + activeLeftWidth(), height);
        for (BrowserEntry entry : entries) {
            int rowHeight = browserEntryRowHeight(entry);
            if (y + rowHeight >= 82 && y < height) {
                if (entry.category()) {
                    int col = DOCUMENTATION_CATEGORY.equals(entry.id())
                            ? AdvancedControllerV2Theme.ACCENT
                            : nodeTitlebarColor(entry.id());
                    boolean collapsed = collapsedCategories.contains(entry.id());
                    boolean hovered = inside(mouseX, mouseY,
                            left + 4, y - 1, activeLeftWidth() - 8, rowHeight);
                    AdvancedControllerV2Theme.drawBrowserCategory(
                            graphics, left + 4, y - 1, activeLeftWidth() - 8, rowHeight,
                            col, collapsed, hovered);
                    graphics.drawString(font, collapsed ? ">" : "v", left + 12, y + 7,
                            col, false);
                    graphics.drawString(font, v2CategoryName(entry.id()).toUpperCase(Locale.ROOT),
                            left + 27, y + 7, AdvancedControllerV2Theme.SECONDARY, false);
                } else if ("sticky_note".equals(entry.id())) {
                    boolean hovered = inside(mouseX, mouseY,
                            left + 10, y + 1, activeLeftWidth() - 20, rowHeight - 3);
                    AdvancedControllerV2Theme.drawDocumentNode(
                            graphics, left + 10, y + 1, activeLeftWidth() - 20, rowHeight - 3, hovered);
                    graphics.drawString(font, "STICKY NOTE", left + 27, y + 10,
                            AdvancedControllerV2Theme.PRIMARY, false);
                    graphics.drawString(font, "+", left + activeLeftWidth() - 27, y + 10,
                            AdvancedControllerV2Theme.ACCENT, false);
                } else {
                    boolean hovered = inside(mouseX, mouseY,
                            left + 7, y, activeLeftWidth() - 14, rowHeight - 1);
                    String category = browserEntryCategory(entry.id());
                    AdvancedControllerV2Theme.drawBrowserNode(
                            graphics, left + 7, y, activeLeftWidth() - 14, rowHeight - 1,
                            nodeTitlebarColor(category), hovered);
                    boolean function = entry.id().startsWith("function:");
                    graphics.drawString(font,
                            trim(browserEntryName(entry.id()).toUpperCase(Locale.ROOT), function ? 22 : 27),
                            left + 23, y + 4, AdvancedControllerV2Theme.PRIMARY, false);
                    if (function && hovered) {
                        int deleteX = left + activeLeftWidth() - 24;
                        graphics.drawString(font, "X", deleteX + 4, y + 7,
                                AdvancedControllerV2Theme.DANGER, false);
                    }
                }
            }
            y += rowHeight;
        }
        graphics.disableScissor();
    }

    // Get the browser entry row height
    private int browserEntryRowHeight(BrowserEntry entry) {
        if (!v2Ui) {
            return entry.category() ? 19 : 15;
        }
        if (entry.category()) {
            return 25;
        }
        return "sticky_note".equals(entry.id()) ? 34 : 23;
    }

    // Get the V2 category name
    private String v2CategoryName(String category) {
        return switch (category) {
            case DOCUMENTATION_CATEGORY -> "Documentation";
            case "core" -> "System";
            case "functions" -> "User Library";
            case "controller" -> "Sensors & I/O";
            case "flow" -> "Execution";
            case "data" -> "Data & Strings";
            case "ship_control" -> "Ship Blueprints";
            default -> AdvancedGraphCatalog.categoryName(category);
        };
    }

    // Get the node browser entries
    private List<BrowserEntry> nodeBrowserEntries(String query) {
        List<BrowserEntry> res = new ArrayList<>();
        Map<String, List<AdvancedGraphCatalog.Definition>> categories = new LinkedHashMap<>();
        boolean showDocumentation = false;
        for (AdvancedGraphCatalog.Definition definition : AdvancedGraphCatalog.all()) {
            if (AdvancedGraphFunctions.CALL_TYPE.equals(definition.id())) continue;
            if (!isNodeAvailableInEditor(definition.id())) continue;
            String searchable = definition.id() + " " + AdvancedGraphCatalog.displayName(definition.id()) + " "
                    + AdvancedGraphCatalog.categoryName(definition.category());
            if (!query.isBlank() && !searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
            if (v2Ui && "sticky_note".equals(definition.id())) {
                showDocumentation = true;
                continue;
            }
            categories.computeIfAbsent(definition.category(), ignored -> new ArrayList<>()).add(definition);
        }
        for (AdvancedGraphDocument.FunctionGraph function : draft.functions()) {
            AdvancedGraphCatalog.Definition definition = functionDefinition(function);
            String searchable = function.name() + " function";
            if (!query.isBlank() && !searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
            categories.computeIfAbsent("functions", ignored -> new ArrayList<>()).add(definition);
        }
        if (v2Ui && showDocumentation) {
            res.add(new BrowserEntry(DOCUMENTATION_CATEGORY, true));
            if (!collapsedCategories.contains(DOCUMENTATION_CATEGORY) || !query.isBlank()) {
                res.add(new BrowserEntry("sticky_note", false));
            }
        }
        for (String category : v2Ui ? V2_CATEGORY_ORDER : CATEGORY_ORDER) {
            List<AdvancedGraphCatalog.Definition> definitions = categories.get(category);
            if (definitions == null || definitions.isEmpty()) continue;
            res.add(new BrowserEntry(category, true));
            if (!collapsedCategories.contains(category) || !query.isBlank()) {
                definitions.stream().sorted(Comparator.comparing(definition -> AdvancedGraphCatalog.displayName(definition.id())))
                        .forEach(definition -> res.add(new BrowserEntry(definition.id(), false)));
            }
        }
        return res;
    }

    // Draw the block browser
    private void drawBlockBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RegistryEntry> entries = registryEntries();
        int y = 86 - blockBrowserScroll;
        int left = layoutLeft();
        graphics.enableScissor(left, 82, left + activeLeftWidth(), height);
        for (RegistryEntry entry : entries) {
            if (y >= 78 && y < height) {
                if (entry.header()) {
                    renderControllerOption(graphics, left + 5, y - 2, activeLeftWidth() - 10, 16, 0xFF5D9FE3, false);
                    graphics.drawString(font, (collapsedBlockNamespaces.contains(entry.id()) ? "> " : "v ") + entry.id(),
                            left + 12, y + 1, nodeValueTextColor(), false);
                } else {
                    boolean hover = mouseX >= left + 8 && mouseX < left + activeLeftWidth() - 8 && mouseY >= y - 1 && mouseY < y + 18;
                    if (hover) graphics.fill(left + 8, y - 1, left + activeLeftWidth() - 7, y + 18, 0x663B617C);
                    graphics.renderItem(entry.stack(), left + 10, y);
                    graphics.drawString(font, trim(entry.id(), 29), left + 31, y + 4, 0xFFD7E8F4, false);
                }
            }
            y += entry.header() ? 19 : 20;
        }
        graphics.disableScissor();
    }

    // Get the registry entries
    private List<RegistryEntry> registryEntries() {
        String query = blockSearch == null ? "" : blockSearch.getValue().trim().toLowerCase(Locale.ROOT);
        Map<String, List<RegistryEntry>> groups = new LinkedHashMap<>();
        BuiltInRegistries.BLOCK.stream().forEach(block -> {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null || (!query.isBlank() && !id.toString().contains(query))) return;
            ItemStack stack = new ItemStack(block.asItem());
            if (!stack.isEmpty()) groups.computeIfAbsent(id.getNamespace() + " blocks", ignored -> new ArrayList<>())
                    .add(new RegistryEntry(id.toString(), stack, false));
        });
        BuiltInRegistries.ITEM.stream().forEach(item -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || (!query.isBlank() && !id.toString().contains(query))) return;
            groups.computeIfAbsent(id.getNamespace() + " items", ignored -> new ArrayList<>())
                    .add(new RegistryEntry(id.toString(), new ItemStack(item), false));
        });
        List<RegistryEntry> res = new ArrayList<>();
        groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(group -> {
            res.add(new RegistryEntry(group.getKey(), ItemStack.EMPTY, true));
            if (!collapsedBlockNamespaces.contains(group.getKey()) || !query.isBlank()) {
                group.getValue().stream().sorted(Comparator.comparing(RegistryEntry::id)).limit(400).forEach(res::add);
            }
        });
        return res;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                          INSPECTOR
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the inspector
    private void drawInspector(GuiGraphics graphics) {
        if (rightSidebarCollapsed) return;
        int x = graphRight();
        renderSidebarPanel(graphics, x, TOOLBAR_HEIGHT, layoutRight() - x, height - TOOLBAR_HEIGHT);
        graphics.fill(x, TOOLBAR_HEIGHT, x + 1, height,
                v2Ui ? AdvancedControllerV2Theme.BORDER : 0xFF344A5C);
        graphics.drawString(font, v2Ui ? "CONFIG" : "Inspector", x + 10, 38,
                v2Ui ? AdvancedControllerV2Theme.MUTED : 0xFF91D9FF, false);
        CompoundTag group = selectedGroup();
        AdvancedGraphDocument.Node selectedNode = selectedNode();
        if (group != null) {
            graphics.fill(x + 8, 56, layoutRight() - 8, 76, group.getInt("Color"));
            graphics.drawString(font, "Comment Group", x + 14, 62, interfacePrimaryColor(), false);
            graphics.drawString(font, "Title", x + 10, 104, interfaceSecondaryColor(), false);
            graphics.drawString(font, "Color", x + 10, 150, interfaceAccentTextColor(), false);
            int[] colors = {0xFF5D9FE3, 0xFFE45B67, 0xFF59C58B, 0xFFE09B53, 0xFFB987E8, 0xFF44B8B0, 0xFF9099A5};
            for (int i = 0; i < colors.length; i++) {
                int cx = x + 10 + (i % 4) * 42;
                int cy = 168 + (i / 4) * 24;
                graphics.fill(cx, cy, cx + 32, cy + 16, colors[i]);
                if (group.getInt("Color") == colors[i]) {
                    graphics.renderOutline(cx - 1, cy - 1, 34, 18, interfacePrimaryColor());
                }
            }
            graphics.drawString(font, "Drag the header to move.", x + 10, 225, interfaceMutedColor(), false);
            graphics.drawString(font, "Drag the lower-right handle to resize.", x + 10, 239,
                    interfaceMutedColor(), false);
        } else if (selectedNode != null) {
            AdvancedGraphDocument.Node node = selectedNode;
            if (node != null) {
                int col = AdvancedGraphCatalog.categoryColor(AdvancedGraphCatalog.get(node.type()).category());
                if (v2Ui) {
                    AdvancedControllerV2Theme.drawOption(
                            graphics, x + 8, 56, layoutRight() - x - 16, 20, col, true, true);
                } else {
                    graphics.fill(x + 8, 56, layoutRight() - 8, 76, col);
                }
                graphics.drawString(font, nodeTitle(node), x + 14, 62,
                        v2Ui ? AdvancedControllerV2Theme.PRIMARY : 0xFFFFFFFF, false);
                graphics.drawString(font, node.type(), x + 10, 84,
                        v2Ui ? AdvancedControllerV2Theme.MUTED : 0xFF91A9B8, false);
                graphics.drawString(font, "node_id: " + trim(node.id(), 24), x + 10, 96,
                        v2Ui ? AdvancedControllerV2Theme.MUTED : 0xFF91A9B8, false);
                String property = editableProperty(node);
                String editableInputLabelPort = editableInputLabelPort(node, selectedInputPort);
                String selectedOutputPort = selectedOutputPort(node, selectedInputPort);
                if (NODE_ALIAS_PROPERTY.equals(selectedInputPort)) {
                    graphics.drawString(font, "Alias", x + 10, 108,
                            interfaceSecondaryColor(), false);
                } else if (editableInputLabelPort != null) {
                    graphics.drawString(font, "Input name: " + humanPort(editableInputLabelPort), x + 10, 108,
                            interfaceSecondaryColor(), false);
                } else if (selectedOutputPort != null) {
                    graphics.drawString(font, "Output value: " + humanPort(selectedOutputPort), x + 10, 108,
                            interfaceSecondaryColor(), false);
                } else if (property != null) {
                    graphics.drawString(font, property, x + 10, 108, interfaceSecondaryColor(), false);
                } else if (isHudNode(node) || isConstructorNode(node)) {
                    graphics.drawString(font, "Right-click an input to rename; Shift-right-click removes it.",
                            x + 10, 108,
                            interfaceMutedColor(), false);
                }
                int y = 148;
                if ("curve".equals(node.type())) {
                    graphics.drawString(font, "Custom Curve", x + 10, y, interfaceAccentTextColor(), false);
                    drawCurveEditor(graphics, node, x + 10, y + 15, RIGHT_WIDTH - 20, 116, true);
                    graphics.drawString(font, "Ctrl-click adds a point.", x + 10, y + 136,
                            interfaceMutedColor(), false);
                    graphics.drawString(font, "Right-click deletes a point.", x + 10, y + 150,
                            interfaceMutedColor(), false);
                    y += 170;
                }
                if (isAdvancedHudNode(node)) {
                    UiRect designerButton = hudBtnBounds();
                    boolean active = hudOpen && node.id().equals(hudNodeId);
                    renderAdvancedButton(graphics, font, designerButton.x(), designerButton.y(),
                            designerButton.width(), designerButton.height(), Component.literal("Open Designer"),
                            active, true);
                    y = Math.max(y, designerButton.bottom() + 10);
                }
                if (isImageReference(node)) {
                    UiRect uploadButton = imageReferenceUploadButtonBounds();
                    renderAdvancedButton(graphics, font, uploadButton.x(), uploadButton.y(),
                            uploadButton.width(), uploadButton.height(),
                            Component.literal("Embed Image"), false, true);
                    graphics.drawString(font, "URLs or embedded image data are saved in the graph.",
                            x + 10, uploadButton.bottom() + 7, interfaceMutedColor(), false);
                }
            }
        } else if (selectedNodes.size() > 1) {
            graphics.drawString(font, selectedNodes.size() + " nodes selected", x + 10, 60,
                    interfacePrimaryColor(), false);
            graphics.drawString(font, "Drag any selected node to move all.", x + 10, 78,
                    interfaceMutedColor(), false);
            graphics.drawString(font, "Right-click for group actions.", x + 10, 92,
                    interfaceMutedColor(), false);
        } else {
            graphics.drawString(font, "Select a node to edit it.", x + 10, 60, interfaceMutedColor(), false);
        }

        // -----------------------------------------------------INSPECTOR SECTIONS------------------------------------------------

        InspectorSections sections = inspectorSections(selectedNode);
        drawInspectorSectionHeader(graphics, x, sections.optionsHeaderTop(), "Node Options",
                inspectorOptionsCollapsed);
        if (selectedNode != null && !inspectorOptionsCollapsed
                && sections.optionsBottom() > sections.optionsTop()) {
            int y = sections.optionsTop() - inspectorOptionsScroll;
            graphics.enableScissor(x, sections.optionsTop(), layoutRight(), sections.optionsBottom());
            String property = editableProperty(selectedNode);
            drawInspectorControl(graphics, x, y, "Alias",
                    valueOrUnset(selectedNode.data().getString(GraphNodeAlias.DATA_KEY)),
                    AdvancedGraphCatalog.categoryColor("data"),
                    NODE_ALIAS_PROPERTY.equals(selectedInputPort));
            y += 17;
            if (hasSwitchTypeControl(selectedNode)) {
                drawInspectorControl(graphics, x, y, "Type",
                        humanPort(AdvancedGraphCatalog.switchType(selectedNode)),
                        AdvancedGraphCatalog.categoryColor("logic"),
                        AdvancedGraphCatalog.SWITCH_TYPE_TAG.equals(selectedInputPort));
                y += 17;
            }
            if (hasPropertyControl(selectedNode)) {
                drawInspectorControl(graphics, x, y,
                        "PulseBehavior".equals(property) ? "Pulse Behavior" : property,
                        propertyValueLabel(selectedNode, property),
                        AdvancedGraphCatalog.categoryColor(AdvancedGraphCatalog.get(selectedNode.type()).category()),
                        property.equals(selectedInputPort));
                y += 17;
            }
            for (var port : AdvancedGraphCatalog.inputs(selectedNode).entrySet()) {
                if ("exec".equals(port.getValue())
                        || ("curve".equals(selectedNode.type()) && "value".equals(port.getKey()))) continue;
                if (isInputConnected(selectedNode, port.getKey())) {
                    drawDrivenInspectorControl(graphics, selectedNode, port.getKey(), port.getValue(), x, y);
                } else if ("frequency".equals(port.getValue())) {
                    drawInspectorFrequency(graphics, selectedNode, x, y);
                } else if ("number".equals(port.getValue())) {
                    drawInspectorSlider(graphics, selectedNode, port.getKey(), x, y);
                } else {
                    drawInspectorControl(graphics, x, y,
                            inputDisplayLabel(selectedNode, port.getKey()),
                            inputValueLabel(selectedNode, port.getKey(), port.getValue()),
                            portColor(port.getValue()), port.getKey().equals(selectedInputPort),
                            inspectorInlineMapPortIndent(selectedNode, port.getKey(), false));
                }
                y += "frequency".equals(port.getValue()) ? 22 : 17;
            }
            if (isConstructorNode(selectedNode)) {
                drawInspectorControl(graphics, x, y, "Add Input", "[+]",
                        AdvancedGraphCatalog.categoryColor("data"), false);
            } else if (isFunctionInterfaceNode(selectedNode)) {
                drawInspectorControl(graphics, x, y,
                        isFunctionInputNode(selectedNode) ? "Add Input" : "Add Output", "[+]",
                        AdvancedGraphCatalog.categoryColor("functions"), false);
            }
            graphics.disableScissor();
        }
        drawInspectorDivider(graphics, x, sections.optionsDividerTop());

        drawInspectorSectionHeader(graphics, x, sections.targetsHeaderTop(), "Bindings & Targets",
                inspectorTargetsCollapsed);
        if (selectedNode != null && !inspectorTargetsCollapsed
                && sections.targetsBottom() > sections.targetsTop()) {
            int y = sections.targetsTop() + 8 - inspectorTargetsScroll;
            graphics.enableScissor(x, sections.targetsTop(), layoutRight(), sections.targetsBottom());
            if (usesBinding(selectedNode)) {
                graphics.drawString(font, bindingOptionsTitle(selectedNode), x + 10, y,
                        interfaceAccentTextColor(), false);
                y += 15;
                for (AdvancedContraptionControllerMenu.GraphBindingOption option : bindingOptions(selectedNode)) {
                    boolean selected = option.id().equals(selectedNode.data().getString("BindingId"));
                    if (selected) renderControllerOption(graphics, x + 7, y - 2,
                            layoutRight() - x - 14, 14, AdvancedGraphCatalog.categoryColor("controller"), true);
                    graphics.drawString(font, trim(option.label(), 25), x + 12, y,
                            selected ? nodeValueTextColor() : interfaceSecondaryColor(), false);
                    y += 14;
                }
            }
            if (usesTarget(selectedNode)) {
                y += 5;
                graphics.drawString(font, "Available Targets", x + 10, y,
                        interfaceAccentTextColor(), false);
                y += 15;
                for (ControllerDiscoveryNode target : graphTargetOptions(selectedNode)) {
                    boolean selected = target.nodeId().equals(selectedNode.data().getString("Target"));
                    if (selected) renderControllerOption(graphics, x + 7, y - 2,
                            layoutRight() - x - 14, 14, portColor("target"), true);
                    List<AeroworksControllerCompat.ConsoleSection> consoleSections =
                            selected ? aeroworksSectionsForTarget(selectedNode, target) : List.of();
                    String targetLabel = target.label().isBlank() ? target.nodeId() : target.label();
                    if (!consoleSections.isEmpty()) {
                        targetLabel = "\u25be " + targetLabel;
                    }
                    graphics.drawString(font, trim(targetLabel, 25),
                            x + 12, y, selected ? nodeValueTextColor() : interfaceSecondaryColor(), false);
                    y += 14;
                    for (AeroworksControllerCompat.ConsoleSection section : consoleSections) {
                        boolean sectionSelected = section.id().equals(selectedNode.data().getString(
                                AeroworksControllerCompat.GRAPH_SECTION_ID_KEY));
                        if (sectionSelected) renderControllerOption(graphics, x + 17, y - 2,
                                layoutRight() - x - 24, 14, portColor("target"), true);
                        graphics.drawString(font, trim(section.label(), 23), x + 22, y,
                                sectionSelected ? nodeValueTextColor()
                                        : v2Ui ? AdvancedControllerV2Theme.SECONDARY : 0xFFADC3D4, false);
                        y += 14;
                    }
                }
                List<ControllerDiscoveryNode> scmTargets = graphScmTargetOptions(selectedNode);
                if (!scmTargets.isEmpty()) {
                    y += 5;
                    graphics.drawString(font, "SCM Blocks", x + 10, y,
                            interfaceAccentTextColor(), false);
                    y += 15;
                    for (ControllerDiscoveryNode target : scmTargets) {
                        boolean selected = target.nodeId().equals(
                                selectedNode.data().getString("Target"));
                        if (selected) renderControllerOption(graphics, x + 7, y - 2,
                                layoutRight() - x - 14, 14, portColor("target"), true);
                        graphics.drawString(font, trim(target.label().isBlank()
                                        ? target.nodeId() : target.label(), 25),
                                x + 12, y, selected ? nodeValueTextColor()
                                        : interfaceSecondaryColor(), false);
                        y += 14;
                    }
                }
                if ("acc_display_external".equals(selectedNode.type())) {
                    y += 5;
                    graphics.drawString(font, "External Sources", x + 10, y,
                            interfaceAccentTextColor(), false);
                    y += 15;
                    for (ControllerDiscoveryNode src : graphDisplaySourceOptions()) {
                        boolean selected = src.nodeId().equals(selectedNode.data().getString("Source"));
                        if (selected) renderControllerOption(graphics, x + 7, y - 2,
                                layoutRight() - x - 14, 14, portColor("target"), true);
                        graphics.drawString(font, trim(src.label().isBlank()
                                        ? src.nodeId() : src.label(), 25),
                                x + 12, y, selected ? nodeValueTextColor() : interfaceSecondaryColor(), false);
                        y += 14;
                    }
                }
            }
            graphics.disableScissor();
        }
        drawInspectorDivider(graphics, x, sections.targetsDividerTop());

        drawInspectorSectionHeader(graphics, x, sections.variablesHeaderTop(), "Variables",
                inspectorVariablesCollapsed);
        if (!inspectorVariablesCollapsed && sections.variablesBottom() > sections.variablesTop()) {
            int y = sections.variablesTop() + 3;
            graphics.enableScissor(x, sections.variablesTop(), layoutRight(), sections.variablesBottom());
            if (draft.variables().isEmpty()) {
                graphics.drawString(font, "No variables", x + 10, y + 3, interfaceMutedColor(), false);
            } else {
                for (var variable : draft.variables().entrySet()) {
                    int getX = variableGetButtonX();
                    int setX = variableSetButtonX();
                    graphics.drawString(font, trim(variable.getKey(), 17), x + 10, y + 5,
                            interfaceSecondaryColor(), false);
                    renderAdvancedButton(graphics, font, getX, y + 1, VARIABLE_BROWSER_BUTTON_WIDTH, 16,
                            Component.literal("GET"), false, true);
                    renderAdvancedButton(graphics, font, setX, y + 1, VARIABLE_BROWSER_BUTTON_WIDTH, 16,
                            Component.literal("SET"), false, true);
                    y += VARIABLE_BROWSER_ROW_HEIGHT;
                }
            }
            graphics.disableScissor();
        }
        graphics.drawString(font, activeNodes().size() + " nodes / " + activeEdges().size() + " wires",
                x + 10, height - 14,
                v2Ui ? AdvancedControllerV2Theme.MUTED : 0xFF899CAA, false);
    }

    // Draw the inspector section header
    private void drawInspectorSectionHeader(GuiGraphics graphics, int x, int y, String label, boolean collapsed) {
        int fill = v2Ui ? AdvancedControllerV2Theme.PANEL_RAISED : 0xFF1A2A36;
        int border = v2Ui ? AdvancedControllerV2Theme.BORDER : 0xFF3D637E;
        graphics.fill(x + 5, y, layoutRight() - 5, y + INSPECTOR_SECTION_HEADER_HEIGHT, fill);
        graphics.renderOutline(x + 5, y, layoutRight() - x - 10, INSPECTOR_SECTION_HEADER_HEIGHT, border);
        graphics.drawString(font, collapsed ? ">" : "v", x + 10, y + 4,
                v2Ui ? AdvancedControllerV2Theme.SECONDARY : 0xFFE0EDF4, false);
        graphics.drawString(font, v2Ui ? label.toUpperCase(Locale.ROOT) : label, x + 23, y + 4,
                v2Ui ? AdvancedControllerV2Theme.MUTED : 0xFF91D9FF, false);
    }

    // Draw the inspector divider
    private void drawInspectorDivider(GuiGraphics graphics, int x, int y) {
        if (y < 0) return;
        graphics.fill(x + 8, y + 2, layoutRight() - 8, y + 3,
                v2Ui ? AdvancedControllerV2Theme.BORDER : 0xFF52758D);
        graphics.fill(x + RIGHT_WIDTH / 2 - 10, y + 1, x + RIGHT_WIDTH / 2 + 10, y + 4,
                v2Ui ? AdvancedControllerV2Theme.MUTED : 0xFF7FA9C2);
    }

    // Get the variable get button x
    private int variableGetButtonX() {
        return layoutRight() - VARIABLE_BROWSER_BUTTON_WIDTH * 2 - 13;
    }

    // Get the variable set button x
    private int variableSetButtonX() {
        return layoutRight() - VARIABLE_BROWSER_BUTTON_WIDTH - 8;
    }

    // Draw the sidebar handles
    private void drawSidebarHandles(GuiGraphics graphics) {
        int leftX = leftSidebarHandleX();
        if (v2Ui) {
            int handleY = Math.max(TOOLBAR_HEIGHT + 8, height / 2 - 22);
            AdvancedControllerV2Theme.drawHandle(
                    graphics, leftX, handleY, SIDEBAR_HANDLE_WIDTH, 44, false);
            graphics.drawCenteredString(font, leftSidebarCollapsed ? ">" : "<",
                    leftX + SIDEBAR_HANDLE_WIDTH / 2, handleY + 18, AdvancedControllerV2Theme.SECONDARY);

            int rightX = rightSidebarHandleX();
            AdvancedControllerV2Theme.drawHandle(
                    graphics, rightX, handleY, SIDEBAR_HANDLE_WIDTH, 44, false);
            graphics.drawCenteredString(font, rightSidebarCollapsed ? "<" : ">",
                    rightX + SIDEBAR_HANDLE_WIDTH / 2, handleY + 18, AdvancedControllerV2Theme.SECONDARY);
            return;
        }
        drawSidebarChevron(graphics, leftX, TOOLBAR_HEIGHT, SIDEBAR_HANDLE_WIDTH, height - TOOLBAR_HEIGHT);
        graphics.fill(leftX, TOOLBAR_HEIGHT, leftX + SIDEBAR_HANDLE_WIDTH, height, 0x66101820);
        graphics.renderOutline(leftX, TOOLBAR_HEIGHT, SIDEBAR_HANDLE_WIDTH, height - TOOLBAR_HEIGHT, 0xFF344A5C);
        graphics.drawCenteredString(font, leftSidebarCollapsed ? ">" : "<", leftX + SIDEBAR_HANDLE_WIDTH / 2,
                TOOLBAR_HEIGHT + 10, 0xFFE0EDF4);

        int rightX = rightSidebarHandleX();
        drawSidebarChevron(graphics, rightX, TOOLBAR_HEIGHT, SIDEBAR_HANDLE_WIDTH, height - TOOLBAR_HEIGHT);
        graphics.fill(rightX, TOOLBAR_HEIGHT, rightX + SIDEBAR_HANDLE_WIDTH, height, 0x66101820);
        graphics.renderOutline(rightX, TOOLBAR_HEIGHT, SIDEBAR_HANDLE_WIDTH, height - TOOLBAR_HEIGHT, 0xFF344A5C);
        graphics.drawCenteredString(font, rightSidebarCollapsed ? "<" : ">", rightX + SIDEBAR_HANDLE_WIDTH / 2,
                TOOLBAR_HEIGHT + 10, 0xFFE0EDF4);
    }

    // Get the left sidebar handle x
    private int leftSidebarHandleX() {
        return leftSidebarCollapsed ? layoutLeft() : graphLeft() - SIDEBAR_HANDLE_WIDTH + SIDEBAR_HANDLE_OFFSET;
    }

    // Get the right sidebar handle x
    private int rightSidebarHandleX() {
        return rightSidebarCollapsed ? layoutRight() - SIDEBAR_HANDLE_WIDTH : graphRight() - SIDEBAR_HANDLE_OFFSET;
    }

    // Get the inspector lines
    private List<String> inspectorLines(AdvancedGraphDocument.Node node) {
        List<String> res = new ArrayList<>();
        if (node.type().startsWith("wireless_frequency")) {
            res.add("Frequency A: " + valueOrUnset(node.data().getString("FrequencyFirst")));
            res.add("Frequency B: " + valueOrUnset(node.data().getString("FrequencySecond")));
        }
        if (usesBinding(node)) {
            res.add("Binding: " + valueOrUnset(node.data().getString("BindingLabel")));
        }
        if (usesTarget(node)) {
            String sectionLabel = node.data().getString(AeroworksControllerCompat.GRAPH_SECTION_LABEL_KEY);
            res.add("Target: " + valueOrUnset(
                    sectionLabel.isBlank() ? node.data().getString("TargetLabel") : sectionLabel));
        }
        res.add("Category: " + AdvancedGraphCatalog.categoryName(AdvancedGraphCatalog.get(node.type()).category()));
        return res;
    }

    // Draw the inspector control
    private void drawInspectorControl(GuiGraphics graphics, int x, int y, String label, String val, int col, boolean selected) {
        drawInspectorControl(graphics, x, y, label, val, col, selected, 0);
    }

    // Draw the inspector control
    private void drawInspectorControl(GuiGraphics graphics, int x, int y, String label, String val, int col,
                                      boolean selected, int leadingInset) {
        int controlLeft = x + 7 + leadingInset;
        renderControllerOption(graphics, controlLeft, y - 3, layoutRight() - controlLeft - 7, 15, col, selected);
        graphics.drawString(font, trim(label, 15), controlLeft + 7, y, nodeMutedTextColor(), false);
        String shown = trim(val, 12);
        graphics.drawString(font, shown, layoutRight() - font.width(shown) - 11, y, nodeValueTextColor(), false);
    }

    // Draw the driven inspector control
    private void drawDrivenInspectorControl(GuiGraphics graphics, AdvancedGraphDocument.Node node, String port, String type,
                                            int x, int y) {
        int controlLeft = x + 7 + inspectorInlineMapPortIndent(node, port, false);
        renderControllerOption(graphics, controlLeft, y - 3, layoutRight() - controlLeft - 7, 15, portColor(type), false);
        String label = inputDisplayLabel(node, port);
        graphics.drawString(font, trim(label, 15), controlLeft + 7, y, nodeMutedTextColor(), false);
        String shown = trim(wiredInputValueLabel(node, port, type), 12);
        graphics.drawString(font, shown, layoutRight() - font.width(shown) - 11, y, nodeValueTextColor(), false);
    }

    // Draw the inspector slider
    private void drawInspectorSlider(GuiGraphics graphics, AdvancedGraphDocument.Node node, String port, int x, int y) {
        int left = x + 8 + inspectorInlineMapPortIndent(node, port, false);
        int right = layoutRight() - 8;
        String val = compactNumber(inputNumber(node, port));
        SliderTrack track = inspectorSliderTrack(node, port);
        int trackY = y + 4;
        double[] range = numberRange(node, port);
        double amount = sliderAmount(inputNumber(node, port), range);
        renderControllerOption(graphics, left, y - 3, right - left, 15, portColor("number"), port.equals(selectedInputPort));
        graphics.drawString(font, trim(inputDisplayLabel(node, port), 11), left + 5, y, nodeMutedTextColor(), false);
        renderControllerSlider(graphics, track.left(), trackY, track.width(), amount, portColor("number"),
                draggingInspectorSlider && port.equals(draggingSliderPort));
        graphics.drawString(font, val, right - font.width(val) - 2, y, nodeValueTextColor(), false);
    }

    // Draw the inspector output slider
    private void drawInspectorOutputSlider(
            GuiGraphics graphics, AdvancedGraphDocument.Node node,
            String port, int x, int y
    ) {
        int left = x + 8;
        int right = layoutRight() - 8;
        double num = outputNumber(node, port);
        String val = compactNumber(num);
        SliderTrack track = inspectorOutputSliderTrack(node, port);
        double amount = sliderAmount(num, numberRange(node, port));
        renderControllerOption(graphics, left, y - 3, right - left, 15,
                portColor("number"), outputSelection(port).equals(selectedInputPort));
        graphics.drawString(font, trim("Output " + humanPort(port), 11),
                left + 5, y, nodeMutedTextColor(), false);
        renderControllerSlider(graphics, track.left(), y + 4, track.width(),
                amount, portColor("number"), draggingInspectorSlider
                        && draggingOutputSlider && port.equals(draggingSliderPort));
        graphics.drawString(font, val, right - font.width(val) - 2, y,
                nodeValueTextColor(), false);
    }

    // Get the inspector sections
    private InspectorSections inspectorSections(AdvancedGraphDocument.Node node) {
        int desiredTop = 110;
        if (selectedGroup() != null) {
            desiredTop = 270;
        } else if (node != null) {
            desiredTop = 154;
            if ("curve".equals(node.type())) {
                desiredTop += 170;
            }
            if (isAdvancedHudNode(node)) {
                desiredTop = Math.max(desiredTop, hudBtnBounds().bottom() + 10);
            }
            if (isImageReference(node)) {
                desiredTop = Math.max(desiredTop, imageReferenceUploadButtonBounds().bottom() + 28);
            }
        }

        int dividerSpace = (!inspectorOptionsCollapsed ? INSPECTOR_SECTION_DIVIDER_HEIGHT : 0)
                + (!inspectorTargetsCollapsed ? INSPECTOR_SECTION_DIVIDER_HEIGHT : 0);
        int headersSpace = INSPECTOR_SECTION_HEADER_HEIGHT * 3;
        int contentBottom = Math.max(TOOLBAR_HEIGHT, height - 20);
        int latestTop = Math.max(TOOLBAR_HEIGHT + 28, contentBottom - headersSpace - dividerSpace);
        int top = Math.min(desiredTop, latestTop);
        int contentSpace = Math.max(0, contentBottom - top - headersSpace - dividerSpace);

        int minimumTargets = inspectorTargetsCollapsed ? 0 : INSPECTOR_SECTION_MIN_CONTENT_HEIGHT;
        int minimumVariables = inspectorVariablesCollapsed ? 0 : INSPECTOR_SECTION_MIN_CONTENT_HEIGHT;
        int optionsContent = inspectorOptionsCollapsed ? 0
                : allocateInspectorContent(inspectorOptionsHeight, contentSpace, minimumTargets + minimumVariables);
        int remaining = Math.max(0, contentSpace - optionsContent);
        int targetsContent = inspectorTargetsCollapsed ? 0
                : allocateInspectorContent(inspectorTargetsHeight, remaining, minimumVariables);
        remaining = Math.max(0, remaining - targetsContent);
        int variablesContent = inspectorVariablesCollapsed ? 0 : remaining;

        int cursor = top;
        int optionsHeaderTop = cursor;
        cursor += INSPECTOR_SECTION_HEADER_HEIGHT;
        int optionsTop = cursor;
        cursor += optionsContent;
        int optionsDividerTop = inspectorOptionsCollapsed ? -1 : cursor;
        if (!inspectorOptionsCollapsed) cursor += INSPECTOR_SECTION_DIVIDER_HEIGHT;

        int targetsHeaderTop = cursor;
        cursor += INSPECTOR_SECTION_HEADER_HEIGHT;
        int targetsTop = cursor;
        cursor += targetsContent;
        int targetsDividerTop = inspectorTargetsCollapsed ? -1 : cursor;
        if (!inspectorTargetsCollapsed) cursor += INSPECTOR_SECTION_DIVIDER_HEIGHT;

        int variablesHeaderTop = cursor;
        cursor += INSPECTOR_SECTION_HEADER_HEIGHT;
        int variablesTop = cursor;
        cursor += variablesContent;
        return new InspectorSections(optionsHeaderTop, optionsTop, optionsTop + optionsContent, optionsDividerTop,
                targetsHeaderTop, targetsTop, targetsTop + targetsContent, targetsDividerTop,
                variablesHeaderTop, variablesTop, Math.min(cursor, contentBottom));
    }

    // Allocate the inspector content
    private static int allocateInspectorContent(int preferred, int available, int reserved) {
        int maximum = Math.max(0, available - reserved);
        if (maximum < INSPECTOR_SECTION_MIN_CONTENT_HEIGHT) {
            return maximum;
        }
        return Mth.clamp(preferred, INSPECTOR_SECTION_MIN_CONTENT_HEIGHT, maximum);
    }

    // Check if the pointer is in the inspector header
    private static boolean inInspectorHeader(double mouseY, int headerTop) {
        return mouseY >= headerTop && mouseY < headerTop + INSPECTOR_SECTION_HEADER_HEIGHT;
    }

    // Check if the pointer is in the inspector divider
    private static boolean inInspectorDivider(double mouseY, int dividerTop) {
        return dividerTop >= 0 && mouseY >= dividerTop - 2
                && mouseY < dividerTop + INSPECTOR_SECTION_DIVIDER_HEIGHT + 2;
    }

    // Save the UI preferences
    private void saveUiPreferences() {
        AdvancedControllerUiPreferences.save(new AdvancedControllerUiPreferences.State(
                leftSidebarCollapsed, rightSidebarCollapsed,
                inspectorOptionsCollapsed, inspectorTargetsCollapsed, inspectorVariablesCollapsed,
                inspectorOptionsHeight, inspectorTargetsHeight, saveOnClose,
                Set.copyOf(collapsedNodes)));
    }

    // Draw the curve editor
    private void drawCurveEditor(GuiGraphics graphics, AdvancedGraphDocument.Node node, int x, int y, int width, int height,
                                 boolean inspector) {
        graphics.fill(x, y, x + width, y + height, 0xFF111923);
        graphics.renderOutline(x, y, width, height, inspector ? 0xFF5D9FE3 : 0xFF3D637E);
        for (int step = 1; step < 4; step++) {
            int gx = x + width * step / 4;
            int gy = y + height * step / 4;
            graphics.fill(gx, y + 1, gx + 1, y + height - 1, 0x332F7099);
            graphics.fill(x + 1, gy, x + width - 1, gy + 1, 0x332F7099);
        }
        List<CompoundTag> points = curvePoints(node);
        int previousX = x;
        int previousY = curveScreenY(AdvancedGraphCurve.evaluate(node.data(), 0), y, height);
        for (int step = 1; step <= 64; step++) {
            double pos = step / 64.0;
            int nextX = curveScreenX(pos, x, width);
            int nextY = curveScreenY(AdvancedGraphCurve.evaluate(node.data(), pos), y, height);
            drawLine(graphics, previousX, previousY, nextX, nextY, 0xFF69D19A);
            previousX = nextX;
            previousY = nextY;
        }
        for (CompoundTag point : points) {
            int px = curveScreenX(point, x, width);
            int py = curveScreenY(point, y, height);
            graphics.fill(px - 3, py - 3, px + 4, py + 4, 0xFFE8F6FF);
            graphics.renderOutline(px - 4, py - 4, 9, 9, 0xFF59C58B);
        }
    }

    // Get the curve screen x
    private static int curveScreenX(CompoundTag point, int x, int width) {
        return x + (int) (Mth.clamp(point.getDouble("X"), 0.0, 1.0) * width);
    }

    // Get the curve screen y
    private static int curveScreenY(CompoundTag point, int y, int height) {
        return y + height - (int) (Mth.clamp(point.getDouble("Y"), 0.0, 1.0) * height);
    }

    // Get the curve screen x
    private static int curveScreenX(double val, int x, int width) {
        return x + (int) (Mth.clamp(val, 0.0, 1.0) * width);
    }

    // Get the curve screen y
    private static int curveScreenY(double val, int y, int height) {
        return y + height - (int) (Mth.clamp(val, 0.0, 1.0) * height);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                      LINKER / WINDOWS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the linker window
    private void drawLinkerWindow(GuiGraphics graphics, int mouseX, int mouseY) {
        renderAdvancedPanel(graphics, linkerX, linkerY, LINKER_MODAL_WIDTH, LINKER_MODAL_HEIGHT);
        graphics.drawString(font, "Contraption Network Linker", linkerX + 8, linkerY + 7, 0xFFFFFFFF, false);
        //graphics.drawString(font, "Drag this title bar to move", linkerX + 8, linkerY + 32, 0xFF91A9B8, false);
        int linkerSlotX = leftPos + AdvancedContraptionControllerMenu.LINKER_SLOT_X;
        int linkerSlotY = topPos + AdvancedContraptionControllerMenu.LINKER_SLOT_Y;
        int gogglesInputX = leftPos + AdvancedContraptionControllerMenu.GOGGLES_INPUT_SLOT_X;
        int gogglesInputY = topPos + AdvancedContraptionControllerMenu.GOGGLES_INPUT_SLOT_Y;
        int gogglesOutputX = leftPos + AdvancedContraptionControllerMenu.GOGGLES_OUTPUT_SLOT_X;
        int gogglesOutputY = topPos + AdvancedContraptionControllerMenu.GOGGLES_OUTPUT_SLOT_Y;

        drawPlayerInv(graphics);
        renderSingleSlotSection(graphics, linkerSlotX, linkerSlotY);
        renderSingleSlotSection(graphics, gogglesInputX, gogglesInputY);
        renderSingleSlotSection(graphics, gogglesOutputX, gogglesOutputY);
        graphics.drawCenteredString(font, "Linker", linkerSlotX + 8, linkerSlotY + 22, 0xFFD8E5ED);
        graphics.drawCenteredString(font, "Bind Goggles",
                (gogglesInputX + gogglesOutputX + 16) / 2, gogglesInputY - 30, 0xFFD8E5ED);
        graphics.drawCenteredString(font, "Input", gogglesInputX + 8, gogglesInputY - 14, 0xFFB8CDD8);
        graphics.drawCenteredString(font, "Linked", gogglesOutputX + 8, gogglesOutputY - 14, 0xFFB8CDD8);
        drawGogglesLinkProgress(graphics, gogglesInputX, gogglesInputY, gogglesOutputX, gogglesOutputY);
        graphics.drawString(font, "Player Inventory", linkerX + 16, linkerY + 198, 0xFFD8E5ED, false);
    }

    // Draw the player inv
    private void drawPlayerInv(GuiGraphics graphics) {
        int x = leftPos + AdvancedContraptionControllerMenu.PLAYER_SLOTS_X - PLAYER_INVENTORY_SLOT_OFFSET_X;
        int y = topPos + AdvancedContraptionControllerMenu.PLAYER_SLOTS_Y - PLAYER_INVENTORY_SLOT_OFFSET_Y;
        blitAdvancedSprite(graphics, x, y, PLAYER_INVENTORY_SRC_W, PLAYER_INVENTORY_SRC_H,
                PLAYER_INVENTORY_SRC_X, PLAYER_INVENTORY_SRC_Y,
                PLAYER_INVENTORY_SRC_W, PLAYER_INVENTORY_SRC_H);
    }

    // Draw the single slot section
    private static void renderSingleSlotSection(GuiGraphics graphics, int slotX, int slotY) {
        blitAdvancedSprite(graphics, slotX - SINGLE_SLOT_ITEM_OFFSET, slotY - SINGLE_SLOT_ITEM_OFFSET,
                SINGLE_SLOT_SIZE, SINGLE_SLOT_SIZE,
                SINGLE_SLOT_SRC_X, SINGLE_SLOT_SRC_Y, SINGLE_SLOT_SIZE, SINGLE_SLOT_SIZE);
    }

    // Draw the freq slot bgs
    private void drawFreqSlotBgs(GuiGraphics graphics) {
        for (Slot slot : menu.slots) {
            if (slot.isActive() && slot instanceof SlotItemHandler itemSlot && isFrequencyGhostSlot(itemSlot)) {
                drawFreqSlotBg(graphics, leftPos + slot.x, topPos + slot.y,
                        itemSlot.getSlotIndex() == 1);
            }
        }
    }

    // Draw the freq slot bg
    private static void drawFreqSlotBg(GuiGraphics graphics, int slotX, int slotY, boolean blue) {
        graphics.blit(CTCreateScreenHelper.TNT_GUI_SPRITES,
                slotX - 1, slotY - 1, FREQUENCY_SLOT_SIZE, FREQUENCY_SLOT_SIZE,
                blue ? BLUE_FREQUENCY_SLOT_U : RED_FREQUENCY_SLOT_U,
                FREQUENCY_SLOT_V, FREQUENCY_SLOT_SIZE, FREQUENCY_SLOT_SIZE,
                TNT_GUI_TEXTURE_SIZE, TNT_GUI_TEXTURE_SIZE);
    }

    // Draw the goggles link progress
    private void drawGogglesLinkProgress(GuiGraphics graphics, int inputX, int inputY, int outputX, int outputY) {
        int arrowLeft = inputX + 20;
        int arrowRight = outputX - 4;
        int centerY = inputY + 8;
        int trackTop = centerY - 3;
        int trackBottom = centerY + 4;
        int duration = Math.max(1, menu.getGogglesLinkDuration());
        double progress = menu.getGogglesLinkProgress() / (double) duration;
        renderControllerSlider(graphics, arrowLeft, centerY, Math.max(1, arrowRight - arrowLeft),
                progress, 0xFF59C58B, false);
        graphics.renderOutline(arrowLeft, trackTop, Math.max(1, arrowRight - arrowLeft), trackBottom - trackTop, 0xFF60788A);
        graphics.fill(arrowRight - 6, centerY - 6, arrowRight + 1, centerY + 1, 0xFF60788A);
        graphics.fill(arrowRight - 6, centerY, arrowRight + 1, centerY + 7, 0xFF60788A);
    }

    // Draw the frequency slots
    private void drawFrequencySlots(GuiGraphics graphics) {
        int x = graphRight() + 10;
        int y = 146;
        graphics.drawString(font, "Redstone Link Frequency", x, y, 0xFF91D9FF, false);
        graphics.drawString(font, "Red", x + 8, y + 18, 0xFFE45B67, false);
        graphics.drawString(font, "Blue", x + 58, y + 18, 0xFF5D9FE3, false);
        for (var slot : menu.slots) {
            if (slot.isActive()) {
                int sx = leftPos + slot.x;
                int sy = topPos + slot.y;
                renderControllerOption(graphics, sx - 2, sy - 2, 20, 20, 0xFF9B8DF1, false);
                graphics.renderOutline(sx - 2, sy - 2, 20, 20, 0xFF9B8DF1);
            }
        }
        graphics.drawString(font, "Drag items from EMI/JEI or click inventory items.", x, y + 58, 0xFF91A9B8, false);
    }

    // Check if the set linker is open
    private boolean setLinkerOpen(boolean open) {
        return setLinkerOpen(open, true);
    }

    // Check if the set linker is open
    private boolean setLinkerOpen(boolean open, boolean settleCarriedStack) {
        boolean wasOpen = linkerOpen;
        if (open && frequencyModalOpen && !closeFrequencyEditor()) {
            return false;
        }
        if (open && shareModalOpen) {
            setShareModalOpen(false);
        }
        if (open) toolsMenuOpen = false;
        if (!open && wasOpen && settleCarriedStack && !settleCarriedStackForModal()) {
            return false;
        }
        linkerOpen = open;
        RecipeViewerVisibility.setEmiVisible(blockBrowserOpen && !linkerOpen);
        if (open) clampLinkerWindow();
        menu.playerSlotsActive = open || frequencyModalOpen;
        menu.ghostSlotsActive = frequencyModalOpen && !open;
        menu.ghostSlotMask = 0x3;
        menu.linkerSlotActive = open;
        menu.gogglesSlotActive = open;
        if (open) {
            leftPos = linkerX - LINKER_BASE_X;
            topPos = linkerY - LINKER_BASE_Y;
        } else if (frequencyModalOpen) {
            positionFrequencySlots();
        } else {
            leftPos = layoutLeft();
            topPos = 0;
        }
        if (!open && wasOpen && settleCarriedStack) {
            clearDraggingState();
        }
        return true;
    }

    // Set the share modal open
    private void setShareModalOpen(boolean open) {
        boolean wasOpen = shareModalOpen;
        if (open) {
            if (frequencyModalOpen && !closeFrequencyEditor()) {
                return;
            }
            if (linkerOpen && !setLinkerOpen(false)) {
                return;
            }
        }
        shareModalOpen = open;
        if (open) toolsMenuOpen = false;
        if (linkerShareName != null) {
            positionLinkerShareField();
            linkerShareName.setVisible(open);
            if (!open) {
                linkerShareName.setFocused(false);
            }
        }
        if (!open) {
            sharedGraphConflictName = "";
            sharedGraphSaveTarget = SharedGraphSaveTarget.NONE;
        } else if (!wasOpen) {
            requestSharedGraphs();
            publicShareAvailable = false;
            publicSharePending = false;
            send("public_share_status", "");
        }
    }

    // Switch Modals
    private boolean selectModal(tModals req){
        return switch(req){
            case LINKER -> setLinkerOpen(!linkerOpen);
            case SHARE -> {
                if(shareModalOpen){
                    setShareModalOpen(false);
                    yield true;
                }
                if(linkerOpen && !setLinkerOpen(false)){
                    yield false;
                }
                setShareModalOpen(true);
                yield shareModalOpen;
            }
            case TOOLS -> {
                if(toolsMenuOpen){
                    toolsMenuOpen = false;
                    yield true;
                }
                if(frequencyModalOpen && !closeFrequencyEditor()){
                    yield false;
                }
                if(linkerOpen && !setLinkerOpen(false)){
                    yield false;
                }
                setShareModalOpen(false);
                toolsMenuOpen = true;
                yield true;
            }
        };
    }

    // Clamp the linker window
    private void clampLinkerWindow() {
        int maxX = Math.max(layoutLeft(), layoutRight() - LINKER_MODAL_WIDTH);
        int maxY = Math.max(TOOLBAR_HEIGHT, height - LINKER_MODAL_HEIGHT);
        linkerX = Mth.clamp(linkerX, layoutLeft(), maxX);
        linkerY = Mth.clamp(linkerY, TOOLBAR_HEIGHT, maxY);
    }

    // Handle the position linker share field
    private void positionLinkerShareField() {
        if (linkerShareName == null) {
            return;
        }
        UiRect bounds = shareModalBounds();
        linkerShareName.setX(bounds.x() + 16);
        linkerShareName.setY(bounds.y() + 34);
        linkerShareName.setWidth(bounds.width() - 32);
    }

    // Set the block browser open
    private void setBlockBrowserOpen(boolean open) {
        blockBrowserOpen = open;
        RecipeViewerVisibility.setEmiVisible(open && !linkerOpen);
        ensureSidebarFit();
        imageWidth = layoutRight() - layoutLeft();
        leftPos = layoutLeft();
        resize(minecraft, width, height);
        if (nodeSearch != null) nodeSearch.setVisible(!leftSidebarCollapsed && !blockBrowserOpen);
        if (blockSearch != null) blockSearch.setVisible(!leftSidebarCollapsed && blockBrowserOpen);
        RecipeViewerVisibility.recalculateEmi();
    }

    // Draw the marquee
    private void drawMarquee(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = (int) Math.min(marqueeStartX, mouseX);
        int top = (int) Math.min(marqueeStartY, mouseY);
        int right = (int) Math.max(marqueeStartX, mouseX);
        int bottom = (int) Math.max(marqueeStartY, mouseY);
        graphics.fill(left, top, right, bottom, 0x3355BDF2);
        graphics.renderOutline(left, top, right - left, bottom - top, 0xFF75CCF5);
    }

    // Draw the context menu
    private void drawContextMenu(GuiGraphics graphics) {
        int width = 158;
        List<String> actions = filteredContextItems();
        int header = contextMenu.query().isBlank() ? 0 : 16;
        int height = Math.max(1, actions.size()) * 18 + 8 + header;
        renderAdvancedPanel(graphics, contextMenu.x(), contextMenu.y(), width, height);
        int y = contextMenu.y() + 5;
        if (!contextMenu.query().isBlank()) {
            graphics.drawString(font, "Search: " + trim(contextMenu.query(), 17), contextMenu.x() + 8, y, 0xFF91D9FF, false);
            y += 16;
        }
        if (actions.isEmpty()) {
            graphics.drawString(font, "No matches", contextMenu.x() + 8, y, 0xFF91A9B8, false);
            return;
        }
        for (String item : actions) {
            graphics.drawString(font, item, contextMenu.x() + 8, y, 0xFFE0EDF4, false);
            y += 18;
        }
    }

    // Draw the option dropdown
    private void drawOptionDropdown(GuiGraphics graphics) {
        AdvancedGraphDocument.Node node = findNode(optionDropdown.nodeId());
        if (node == null) {
            closeOptionDropdown();
            return;
        }
        int visibleRows = optionVisibleRows(optionDropdown);
        int maximumScroll = Math.max(0, optionDropdown.options().size() - visibleRows);
        int scroll = Mth.clamp(optionDropdown.scroll(), 0, maximumScroll);
        int height = optionDropdownHeight(optionDropdown);
        renderAdvancedPanel(graphics, optionDropdown.x(), optionDropdown.y(), optionDropdown.width(), height);
        boolean functionPort = FUNCTION_PORT_DROPDOWN.equals(optionDropdown.port());
        String current = functionPort ? "" : optionDropdown.property()
                ? propertyOptionValue(node, optionDropdown.port())
                : inputOptionValue(node, optionDropdown.port(), optionDropdown.type(), optionDropdown.options());
        int y = optionDropdownRowsTop(optionDropdown);
        int textWidth = optionDropdown.width() - (maximumScroll > 0 ? 18 : 12);
        if (visibleRows == 0) {
            graphics.drawString(font, "No matching sounds", optionDropdown.x() + 8, y + 5,
                    0xFF91A9B8, false);
        }
        for (int row = 0; row < visibleRows; row++) {
            String option = optionDropdown.options().get(scroll + row);
            boolean selected = option.equals(current);
            if (selected) renderControllerOption(graphics, optionDropdown.x() + 2, y,
                    optionDropdown.width() - 4, OPTION_DROPDOWN_ITEM_HEIGHT - 2, 0xFF5D9FE3, true);
            boolean variableType = "variable_set".equals(node.type()) && "type".equals(optionDropdown.port());
            String shown = functionPort ? humanPort(option) : optionDropdown.property()
                    ? propertyOptionLabel(node, optionDropdown.port(), option)
                    : isShipFlightBehaviorInput(node, optionDropdown.port())
                    ? shipFlightBehaviorLabel(option)
                    : isShipControlModeInput(node, optionDropdown.port())
                    ? AdvancedGraphCatalog.shipControlModeLabel(option)
                    : variableType || isShipTargetPointInput(node, optionDropdown.port())
                    ? humanPort(option) : option;
            graphics.drawString(font, font.plainSubstrByWidth(shown, Math.max(8, textWidth)),
                    optionDropdown.x() + 8, y + 5,
                    selected ? nodeValueTextColor() : 0xFFE0EDF4, false);
            y += OPTION_DROPDOWN_ITEM_HEIGHT;
        }
        DropdownScrollbar scrollbar = optionDropdownScrollbar(optionDropdown);
        if (scrollbar != null) {
            graphics.fill(scrollbar.trackX(), scrollbar.trackTop(),
                    scrollbar.trackX() + 5, scrollbar.trackTop() + scrollbar.trackHeight(), 0xAA101820);
            graphics.fill(scrollbar.trackX(), scrollbar.thumbTop(),
                    scrollbar.trackX() + 5, scrollbar.thumbTop() + scrollbar.thumbHeight(), 0xFF5D9FE3);
        }
    }

    // Handle the option dropdown click
    private boolean clickOptionDropdown(double mouseX, double mouseY, int btn) {
        // ------------------------------------DROPDOWN CHECKS------------------------------------
        if (optionDropdown == null) return false;
        OptionDropdown dropdown = optionDropdown;
        int visibleRows = optionVisibleRows(dropdown);
        int height = optionDropdownHeight(dropdown);
        boolean inside = mouseX >= dropdown.x() && mouseX < dropdown.x() + dropdown.width()
                && mouseY >= dropdown.y() && mouseY < dropdown.y() + height;
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            closeOptionDropdown();
            return true;
        }
        if (!inside) {
            closeOptionDropdown();
            return true;
        }
        // -----------------------------------------------------SEARCH INPUT-----------------------------------------------------
        if (dropdown.searchable() && optionDropdownSearch != null
                && optionDropdownSearch.mouseClicked(mouseX, mouseY, btn)) {
            optionDropdownSearch.setFocused(true);
            setFocused(optionDropdownSearch);
            return true;
        }
        // -----------------------------------------------------SCROLLBAR-----------------------------------------------------
        DropdownScrollbar scrollbar = optionDropdownScrollbar(dropdown);
        if (scrollbar != null
                && mouseX >= dropdown.x() + dropdown.width() - 12
                && mouseX < dropdown.x() + dropdown.width()
                && mouseY >= scrollbar.trackTop()
                && mouseY < scrollbar.trackTop() + scrollbar.trackHeight()) {
            draggingOptionDropdownThumb = true;
            optionDropdownThumbGrabOffset = mouseY >= scrollbar.thumbTop()
                    && mouseY < scrollbar.thumbTop() + scrollbar.thumbHeight()
                    ? (int) mouseY - scrollbar.thumbTop()
                    : scrollbar.thumbHeight() / 2;
            updateOptionThumb(mouseY);
            return true;
        }
        if (mouseY < optionDropdownRowsTop(dropdown)) {
            return true;
        }
        // ------------------------------------OPTION SELECTION------------------------------------
        int row = (int) ((mouseY - optionDropdownRowsTop(dropdown)) / OPTION_DROPDOWN_ITEM_HEIGHT);
        int idx = dropdown.scroll() + row;
        if (row >= 0 && row < visibleRows && idx >= 0 && idx < dropdown.options().size()) {
            AdvancedGraphDocument.Node node = findNode(dropdown.nodeId());
            if (node != null) {
                String option = dropdown.options().get(idx);
                if (FUNCTION_PORT_DROPDOWN.equals(dropdown.port())) {
                    addFunctionInterfacePort(node, option);
                } else {
                    checkpoint();
                    Object val = "boolean".equals(dropdown.type()) ? Boolean.parseBoolean(option)
                            : "number".equals(dropdown.type()) ? parseNumber(option) : option;
                    if (dropdown.property()
                            && AdvancedGraphCatalog.SWITCH_TYPE_TAG.equals(dropdown.port())
                            && "switch".equals(node.type())) {
                        configureSwitchType(node, String.valueOf(val), true);
                    } else if (dropdown.property()) {
                        if ("acc_display_crn".equals(node.type())
                                && "DisplayMode".equals(dropdown.port())) {
                            configAccCrnMode(node, String.valueOf(val));
                        } else {
                            node.data().putString(dropdown.port(), String.valueOf(val));
                        }
                        if (isAccDisplayWidgetType(node.type())
                                && "WidgetType".equals(dropdown.port())) {
                            configAccWidgetType(node, String.valueOf(val));
                        }
                        if ("event_variable_change".equals(node.type())
                                && "Variable".equals(dropdown.port())) {
                            synchronizeVariableNodes();
                        }
                    }
                    else {
                        putInputDefault(node, dropdown.port(), dropdown.type(), val);
                        if ("face".equals(dropdown.port())
                                && ("get_block_data".equals(node.type())
                                || "set_block_data".equals(node.type()))) {
                            configureDataPorts(node, ControllerDiscoveryNode.fromTag(
                                    node.data().getCompound("TargetData")));
                        }
                        if ("variable_set".equals(node.type()) && "type".equals(dropdown.port())) {
                            configureVariableType(node, option);
                        }
                    }
                    syncInspector();
                }
            }
        }
        closeOptionDropdown();
        return true;
    }

    // Open the option dropdown
    private void openOptionDropdown(AdvancedGraphDocument.Node node, String port, String type,
                                    double x, double y, double width, List<String> opts) {
        boolean searchable = "play_sound".equals(node.type()) && "sound".equals(port);
        int maximumWidth = searchable ? 232 : 158;
        int dropdownWidth = Math.max(54, Math.min(maximumWidth,
                Math.max((int) Math.round(width), maximumWidth == 232 ? 232 : 54)));
        int left = Mth.clamp((int) Math.round(x), layoutLeft() + 4,
                Math.max(layoutLeft() + 4, layoutRight() - dropdownWidth - 4));
        int visibleRows = Math.min(OPTION_DROPDOWN_VISIBLE_ROWS, opts.size());
        int height = Math.max(1, visibleRows) * OPTION_DROPDOWN_ITEM_HEIGHT + 4
                + (searchable ? OPTION_DROPDOWN_SEARCH_HEIGHT : 0);
        int top = (int) Math.round(y);
        if (top + height > this.height - 4) top = (int) Math.round(y) - height - 16;
        top = Mth.clamp(top, TOOLBAR_HEIGHT + 2, Math.max(TOOLBAR_HEIGHT + 2, this.height - height - 4));
        int selectedIndex = Math.max(0, opts.indexOf(inputOptionValue(node, port, type, opts)));
        int initialScroll = Mth.clamp(selectedIndex - visibleRows / 2,
                0, Math.max(0, opts.size() - visibleRows));
        optionDropdownAllOptions = List.copyOf(opts);
        optionDropdown = new OptionDropdown(left, top, dropdownWidth, node.id(), port, type,
                optionDropdownAllOptions, false, initialScroll, searchable);
        configOptionSearch(searchable);
    }

    // Open the property dropdown
    private void openPropertyDropdown(AdvancedGraphDocument.Node node, String property,
                                      double x, double y, double width, List<String> opts) {
        openOptionDropdown(node, property, "string", x, y, width, opts);
        int visibleRows = Math.min(OPTION_DROPDOWN_VISIBLE_ROWS, opts.size());
        int selectedIndex = Math.max(0,
                opts.indexOf(propertyOptionValue(node, property)));
        int initialScroll = Mth.clamp(selectedIndex - visibleRows / 2,
                0, Math.max(0, opts.size() - visibleRows));
        optionDropdown = new OptionDropdown(optionDropdown.x(), optionDropdown.y(), optionDropdown.width(),
                optionDropdown.nodeId(), optionDropdown.port(), optionDropdown.type(),
                optionDropdown.options(), true, initialScroll, optionDropdown.searchable());
    }

    // Configure the option search
    private void configOptionSearch(boolean searchable) {
        if (optionDropdownSearch == null || optionDropdown == null) {
            return;
        }
        optionDropdownSearch.setVisible(searchable);
        optionDropdownSearch.setFocused(searchable);
        if (!searchable) {
            if (getFocused() == optionDropdownSearch) {
                setFocused(null);
            }
            return;
        }
        optionDropdownSearch.setX(optionDropdown.x() + 5);
        optionDropdownSearch.setY(optionDropdown.y() + 3);
        optionDropdownSearch.setWidth(Math.max(32, optionDropdown.width() - 10));
        optionDropdownSearch.setValue("");
        setFocused(optionDropdownSearch);
    }

    // Filter the option dropdown
    private void filterOptionDropdown(String query) {
        if (optionDropdown == null || !optionDropdown.searchable()) {
            return;
        }
        List<String> filtered = filterDropdownOptions(optionDropdownAllOptions, query);
        optionDropdown = new OptionDropdown(
                optionDropdown.x(), optionDropdown.y(), optionDropdown.width(),
                optionDropdown.nodeId(), optionDropdown.port(), optionDropdown.type(),
                filtered, optionDropdown.property(), 0, true);
    }

    // Filter the dropdown options
    static List<String> filterDropdownOptions(List<String> options, String query) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return List.copyOf(options);
        }
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    // Close the option dropdown
    private void closeOptionDropdown() {
        optionDropdown = null;
        optionDropdownAllOptions = List.of();
        draggingOptionDropdownThumb = false;
        optionDropdownThumbGrabOffset = 0;
        if (optionDropdownSearch != null) {
            optionDropdownSearch.setVisible(false);
            optionDropdownSearch.setFocused(false);
        }
        if (getFocused() == optionDropdownSearch) {
            setFocused(null);
        }
    }

    // Get the option visible rows
    private static int optionVisibleRows(OptionDropdown dropdown) {
        return Math.min(OPTION_DROPDOWN_VISIBLE_ROWS, dropdown.options().size());
    }

    // Get the option dropdown rows top
    private static int optionDropdownRowsTop(OptionDropdown dropdown) {
        return dropdown.y() + 2 + (dropdown.searchable() ? OPTION_DROPDOWN_SEARCH_HEIGHT : 0);
    }

    // Get the option dropdown height
    private static int optionDropdownHeight(OptionDropdown dropdown) {
        return Math.max(1, optionVisibleRows(dropdown)) * OPTION_DROPDOWN_ITEM_HEIGHT + 4
                + (dropdown.searchable() ? OPTION_DROPDOWN_SEARCH_HEIGHT : 0);
    }

    // Get the option dropdown scrollbar
    private static DropdownScrollbar optionDropdownScrollbar(OptionDropdown dropdown) {
        int visibleRows = optionVisibleRows(dropdown);
        int maximumScroll = Math.max(0, dropdown.options().size() - visibleRows);
        if (maximumScroll == 0) {
            return null;
        }
        int trackX = dropdown.x() + dropdown.width() - 8;
        int trackTop = optionDropdownRowsTop(dropdown) + 1;
        int trackHeight = Math.max(4, visibleRows * OPTION_DROPDOWN_ITEM_HEIGHT - 2);
        int thumbHeight = Math.max(8,
                Math.round(trackHeight * visibleRows / (float) dropdown.options().size()));
        int thumbTravel = Math.max(0, trackHeight - thumbHeight);
        int scroll = Mth.clamp(dropdown.scroll(), 0, maximumScroll);
        int thumbTop = trackTop + Math.round(thumbTravel * scroll / (float) maximumScroll);
        return new DropdownScrollbar(
                trackX, trackTop, trackHeight, thumbTop, thumbHeight, thumbTravel, maximumScroll);
    }

    // Update the option thumb
    private void updateOptionThumb(double mouseY) {
        if (optionDropdown == null) {
            return;
        }
        DropdownScrollbar scrollbar = optionDropdownScrollbar(optionDropdown);
        if (scrollbar == null) {
            return;
        }
        int scroll = dropdownScrollForPointer(
                mouseY, scrollbar.trackTop(), scrollbar.thumbTravel(),
                optionDropdownThumbGrabOffset, scrollbar.maximumScroll());
        optionDropdown = new OptionDropdown(
                optionDropdown.x(), optionDropdown.y(), optionDropdown.width(),
                optionDropdown.nodeId(), optionDropdown.port(), optionDropdown.type(),
                optionDropdown.options(), optionDropdown.property(), scroll,
                optionDropdown.searchable());
    }

    // Get the dropdown scroll for pointer
    static int dropdownScrollForPointer(
            double mouseY, int trackTop, int thumbTravel, int grabOffset, int maximumScroll
    ) {
        if (thumbTravel <= 0 || maximumScroll <= 0) {
            return 0;
        }
        int thumbTop = Mth.clamp(
                (int) Math.round(mouseY) - grabOffset, trackTop, trackTop + thumbTravel);
        return Mth.clamp(
                Math.round((thumbTop - trackTop) * maximumScroll / (float) thumbTravel),
                0, maximumScroll);
    }

    // Get the input option value
    private String inputOptionValue(AdvancedGraphDocument.Node node, String port, String type, List<String> opts) {
        if ("boolean".equals(type)) return Boolean.toString(inputBoolean(node, port));
        if ("number".equals(type)) return compactNumber(inputNumber(node, port));
        String val = inputString(node, port, opts.isEmpty() ? "" : opts.getFirst());
        return "compare".equals(node.type()) && "operator".equals(port) ? normalizeCompareOperator(val) : val;
    }

    // Get the property option value
    private String propertyOptionValue(AdvancedGraphDocument.Node node, String property) {
        if ("switch".equals(node.type())
                && AdvancedGraphCatalog.SWITCH_TYPE_TAG.equals(property)) {
            return AdvancedGraphCatalog.switchType(node);
        }
        String val = node.data().getString(property);
        if ("portable_tracker".equals(node.type()) && "GogglesPair".equals(property)
                && (val.isBlank() || gogglesTrackerPairLabel(val).isBlank())) {
            return gogglesTrackerPairs().stream().findFirst()
                    .map(pair -> pair.id().toString()).orElse("");
        }
        if ("mouse_input".equals(node.type()) && "MouseInput".equals(property)) {
            String normalized = AdvancedContraptionControllerBlockEntity.normalizeMouseInput(val);
            return normalized.isBlank() ? "left_click" : normalized;
        }
        if ("PulseBehavior".equals(property)) {
            return PULSE_BEHAVIOR_OPTIONS.contains(val) ? val : "both";
        }
        if ("acc_display_crn".equals(node.type()) && "DisplayMode".equals(property)) {
            return ShipInformationDisplayModes.normalize(val);
        }
        return val;
    }

    // Get the property option label
    private String propertyOptionLabel(AdvancedGraphDocument.Node node, String property, String option) {
        if ("portable_tracker".equals(node.type()) && "GogglesPair".equals(property)) {
            String label = gogglesTrackerPairLabel(option);
            return label.isBlank() ? "Linked Goggles" : label;
        }
        if ("event_variable_change".equals(node.type()) && "Variable".equals(property)) {
            return option;
        }
        if ("acc_display_crn".equals(node.type()) && "DisplayMode".equals(property)) {
            return crnDisplayModeLabel(option);
        }
        return humanPort(option);
    }

    // Get the CRN display mode label
    private static String crnDisplayModeLabel(String mode) {
        return ShipInformationDisplayModes.label(mode);
    }

    // Draw the mini browser
    private void drawMiniBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = MINI_BROWSER_WIDTH;
        int height = MINI_BROWSER_HEIGHT;
        int x = Mth.clamp(miniBrowser.x(), graphLeft(), graphRight() - width);
        int y = Mth.clamp(miniBrowser.y(), graphTop(), graphBottom() - height);
        renderAdvancedPanel(graphics, x, y, width, height);
        graphics.drawString(font, miniBrowser.wireNode() == null ? "Add Node" : "Add Compatible Node",
                x + 8, y + 7, 0xFFFFFFFF, false);
        posMiniSearch();
        if (showMiniCreateVar()) {
            int buttonY = y + MINI_BROWSER_ROWS_TOP;
            String variable = miniBrowserVariableName();
            renderAdvancedButton(graphics, font, x + 6, buttonY, width - 12, 17,
                    Component.literal("Create Variable: " + trim(variable, 19)),
                    inside(mouseX, mouseY, x + 6, buttonY, width - 12, 17), true);
        }
        int rowsTop = miniBrowserRowsTop();
        int rowY = y + rowsTop - miniBrowserScroll;
        graphics.enableScissor(x + 2, y + rowsTop - 4, x + width - 2, y + height - 3);
        for (BrowserEntry entry : miniBrowserEntries()) {
            int rowHeight = entry.category() ? 20 : 16;
            if (rowY + rowHeight < y + MINI_BROWSER_ROWS_TOP - 4) {
                rowY += rowHeight;
                continue;
            }
            if (rowY > y + height - 3) break;
            boolean hover = mouseX >= x + 4 && mouseX < x + width - 4 && mouseY >= rowY - 2 && mouseY < rowY + 13;
            if (hover) graphics.fill(x + 4, rowY - 2, x + width - 4, rowY + 13, 0x663B617C);
            if (entry.category()) {
                renderControllerOption(graphics, x + 4, rowY - 2, width - 8, 16,
                        nodeTitlebarColor(entry.id()), false, false);
                drawCategoryChevron(graphics, font, x + 7, rowY, 12, entry.id(),
                        collapsedMiniCategories.contains(entry.id()));
                graphics.drawString(font, AdvancedGraphCatalog.categoryName(entry.id()), x + 24, rowY + 1,
                        nodeTitleTextColor(entry.id()), false);
            } else {
                renderCategorySwatch(graphics, x + 13, rowY + 5, browserEntryCategory(entry.id()));
                graphics.drawString(font, browserEntryName(entry.id()), x + 22, rowY, 0xFFD7E8F4, false);
            }
            rowY += rowHeight;
        }
        graphics.disableScissor();
    }

    // Handle the pos mini search
    private void posMiniSearch() {
        if (miniBrowser == null || miniBrowserSearch == null) return;
        int x = Mth.clamp(miniBrowser.x(), graphLeft(), graphRight() - MINI_BROWSER_WIDTH);
        int y = Mth.clamp(miniBrowser.y(), graphTop(), graphBottom() - MINI_BROWSER_HEIGHT);
        miniBrowserSearch.setX(x + 6);
        miniBrowserSearch.setY(y + 25);
        miniBrowserSearch.setWidth(MINI_BROWSER_WIDTH - 12);
    }

    // Show the mini create var
    private boolean showMiniCreateVar() {
        return miniBrowser != null && miniBrowser.wireNode() == null
                && validVariableName(miniBrowserVariableName());
    }

    // Get the minimum i browser variable name
    private String miniBrowserVariableName() {
        return miniBrowserSearch == null ? "" : miniBrowserSearch.getValue().trim();
    }

    // Get the minimum i browser rows top
    private int miniBrowserRowsTop() {
        return showMiniCreateVar() ? MINI_BROWSER_ROWS_TOP + 22 : MINI_BROWSER_ROWS_TOP;
    }

    // Draw the template picker
    private void drawTemplatePicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int w = 320;
        int h = 146;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        renderAdvancedPanel(graphics, x, y, w, h);
        graphics.drawCenteredString(font, "Choose a starting template", x + w / 2, y + 12, 0xFFFFFFFF);
        for (int i = 0; i < GRAPH_TEMPLATES.size(); i++) {
            GraphTemplateOption template = GRAPH_TEMPLATES.get(i);
            int buttonX = x + 18;
            int buttonY = y + 34 + i * 24;
            int buttonWidth = w - 36;
            renderAdvancedButton(graphics, font, buttonX, buttonY, buttonWidth, 18,
                    Component.literal(template.label()),
                    inside(mouseX, mouseY, buttonX, buttonY, buttonWidth, 18), true);
        }
    }

    // Draw the graph history
    private void drawGraphHistory(GuiGraphics graphics, int mouseX, int mouseY) {
        int w = 340;
        int visibleRows = Math.min(GRAPH_HISTORY_VISIBLE_ROWS, Math.max(1, graphHistoryEntries.size()));
        int h = 54 + visibleRows * 22;
        int x = (width - w) / 2;
        int y = Math.max(TOOLBAR_HEIGHT + 8, (height - h) / 2);
        renderAdvancedPanel(graphics, x, y, w, h);
        graphics.drawCenteredString(font, "Saved graph versions", x + w / 2, y + 12, 0xFFFFFFFF);
        renderAdvancedButton(graphics, font, x + w - 28, y + 7, 18, 16,
                Component.literal("×"),
                inside(mouseX, mouseY, x + w - 28, y + 7, 18, 16), true);
        if (graphHistoryEntries.isEmpty()) {
            graphics.drawCenteredString(font, "No earlier versions have been saved.",
                    x + w / 2, y + 37, 0xFF91A9B8);
            return;
        }
        int end = Math.min(graphHistoryEntries.size(), graphHistoryScroll + GRAPH_HISTORY_VISIBLE_ROWS);
        for (int i = graphHistoryScroll; i < end; i++) {
            AdvancedGraphVersionHistory.Entry entry = graphHistoryEntries.get(i);
            int rowY = y + 32 + (i - graphHistoryScroll) * 22;
            String label = "Revision " + entry.revision() + "  ·  "
                    + GRAPH_VERSION_TIME.format(Instant.ofEpochMilli(entry.savedAt()));
            renderAdvancedButton(graphics, font, x + 14, rowY, w - 28, 18,
                    Component.literal(label),
                    inside(mouseX, mouseY, x + 14, rowY, w - 28, 18), true);
        }
    }

    // Handle the graph history click
    private boolean handleGraphHistoryClick(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        int w = 340;
        int visibleRows = Math.min(GRAPH_HISTORY_VISIBLE_ROWS, Math.max(1, graphHistoryEntries.size()));
        int h = 54 + visibleRows * 22;
        int x = (width - w) / 2;
        int y = Math.max(TOOLBAR_HEIGHT + 8, (height - h) / 2);
        if (inside(mouseX, mouseY, x + w - 28, y + 7, 18, 16)
                || !inside(mouseX, mouseY, x, y, w, h)) {
            graphHistoryOpen = false;
            return true;
        }
        int end = Math.min(graphHistoryEntries.size(), graphHistoryScroll + GRAPH_HISTORY_VISIBLE_ROWS);
        for (int i = graphHistoryScroll; i < end; i++) {
            int rowY = y + 32 + (i - graphHistoryScroll) * 22;
            if (!inside(mouseX, mouseY, x + 14, rowY, w - 28, 18)) {
                continue;
            }
            AdvancedGraphVersionHistory.Entry entry = graphHistoryEntries.get(i);
            long requestId = beginGraphActionToast("Restoring graph version...");
            send("rollback", Integer.toString(entry.index()), requestId);
            graphHistoryOpen = false;
            return true;
        }
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            INPUT
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= 5 && mouseY < 23 && super.mouseClicked(mouseX, mouseY, button)) return true;

        // ------------------------------------OVERLAY INPUT------------------------------------

        if (hudOpen) {
            if (clickHud(mouseX, mouseY, button)) {
                clearHudFieldFocus();
                return true;
            }
            if (inHud(mouseX, mouseY)) return clickHudField(mouseX, mouseY, button);
            clearHudFieldFocus();
            return true;
        }
        if (functionNameEditor != null && functionNameEditor.visible) {
            if (functionNameEditor.mouseClicked(mouseX, mouseY, button)) {
                functionNameEditor.setFocused(true);
                setFocused(functionNameEditor);
                return true;
            }
            finishFunctionRename(false);
        }
        if (toolsMenuOpen) {
            if (handleToolsMenuClick(mouseX, mouseY, button)) return true;
            if (toolsToolbarButtonBounds().contains(mouseX, mouseY)) {
                toolsMenuOpen = false;
                return true;
            }
            toolsMenuOpen = false;
        }
        if (graphHistoryOpen) return handleGraphHistoryClick(mouseX, mouseY, button);
        if (templatePicker && handleTemplateClick(mouseX, mouseY, button)) return true;
        if (optionDropdown != null && clickOptionDropdown(mouseX, mouseY, button)) return true;
        if (contextMenu != null && handleContextMenuClick(mouseX, mouseY, button)) return true;
        if (miniBrowser != null && handleMiniBrowserClick(mouseX, mouseY, button)) return true;
        if (frequencyModalOpen) return clickFreqModal(mouseX, mouseY, button);
        if (shareModalOpen) {
            if (linkerShareName != null && linkerShareName.mouseClicked(mouseX, mouseY, button)) {
                linkerShareName.setFocused(true);
                setFocused(linkerShareName);
                return true;
            }
            if (shareModalBounds().contains(mouseX, mouseY)) {
                return handleShareWindowClick(mouseX, mouseY, button);
            }
            return true;
        }
        if (handleGraphTabClick(mouseX, mouseY, button)) return true;

        AdvancedGraphDocument.Node activeSticky = findNode(editingStickyNode);
        if (activeSticky != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && stickyTextAreaAt(activeSticky, mouseX, mouseY)) {
            positionStickyCaret(activeSticky, mouseX, mouseY, hasShiftDown());
            return true;
        }
        if (editingStickyNode != null) {
            finishStickyEditing();
        }

        if (linkerOpen && mouseX >= linkerX && mouseX <= linkerX + LINKER_MODAL_WIDTH
                && mouseY >= linkerY && mouseY <= linkerY + 22) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                draggingLinker = true;
                return true;
            }
        }
        if (linkerOpen && mouseX >= linkerX && mouseX <= linkerX + LINKER_MODAL_WIDTH
                && mouseY >= linkerY && mouseY <= linkerY + LINKER_MODAL_HEIGHT) {
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (linkerOpen) return true;

        if (handleSidebarHandleClick(mouseX, mouseY, button)) return true;

        if (!leftSidebarCollapsed && mouseX >= layoutLeft() && mouseX < layoutLeft() + activeLeftWidth() && mouseY >= 59) {
            return blockBrowserOpen ? handleBlockBrowserClick(mouseX, mouseY, button)
                    : handleNodeBrowserClick(mouseX, mouseY, button);
        }

        if (!rightSidebarCollapsed && mouseX >= graphRight() && mouseX < layoutRight() && mouseY >= TOOLBAR_HEIGHT) {
            if (handleInspectorCurveClick(mouseX, mouseY, button)) return true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    && (rightClickHudInspector(mouseX, mouseY)
                    || rightClickConstructorInspector(mouseX, mouseY))) return true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && handleInspectorClick(mouseX, mouseY)) return true;
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (v2Ui && clickV2Overlay(mouseX, mouseY, button)) {
            return true;
        }

        // ------------------------------------CANVAS INPUT------------------------------------

        if (inGraph(mouseX, mouseY)) {
            clearTextFocus();
            ForceWriteHit forceWriteHit = setDataForceWriteAt(mouseX, mouseY);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && forceWriteHit != null) {
                checkpoint();
                toggleSetDataForceWrite(forceWriteHit.node(), forceWriteHit.port());
                selectOnly(forceWriteHit.node().id());
                selectedInputPort = forceWriteHit.port();
                syncInspector();
                return true;
            }
            PortHit port = portAt(mouseX, mouseY);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && port != null) {
                if (hasControlDown()) {
                    checkpoint();
                    activeEdges().removeIf(edge -> port.output()
                            ? edge.fromNode().equals(port.node().id()) && edge.fromPort().equals(port.port())
                            : edge.toNode().equals(port.node().id()) && edge.toPort().equals(port.port()));
                    synchronizeComparePorts();
                    return true;
                }
                connectingNode = port.node().id();
                connectingPort = port.port();
                connectingOutput = port.output();
                wireMouseX = mouseX;
                wireMouseY = mouseY;
                return true;
            }
            AdvancedGraphDocument.Node node = nodeAt(mouseX, mouseY);
            CompoundTag group = node == null ? groupAt(mouseX, mouseY) : null;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && node == null && handleWireClick(mouseX, mouseY)) {
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && node != null && collapseHandleAt(node, mouseX, mouseY)) {
                checkpoint();
                setNodeCollapsed(node, !isNodeCollapsed(node));
                saveUiPreferences();
                clearGraphRenderCache();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && stickyResizeHandleAt(node, mouseX, mouseY)) {
                checkpoint();
                selectedGroup = null;
                selectOnly(node.id());
                resizingStickyNode = node.id();
                syncInspector();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && stickyTextAreaAt(node, mouseX, mouseY)) {
                selectedGroup = null;
                selectOnly(node.id());
                beginStickyEditing(node, mouseX, mouseY);
                syncInspector();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                rightPanPending = true;
                rightPanning = false;
                rightPanStartX = mouseX;
                rightPanStartY = mouseY;
                return true;
            }
            if (node != null && handleBodyCurveClick(node, mouseX, mouseY, button)) return true;
            if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                panning = true;
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && node != null) {
                if (doubleClickFunctionNode(node)) {
                    return true;
                }
                selectedGroup = null;
                if (!isReroute(node) && handleRichBodyClick(node, mouseX, mouseY)) return true;
                if (hasControlDown()) {
                    if (!selectedNodes.add(node.id())) selectedNodes.remove(node.id());
                } else if (!selectedNodes.contains(node.id())) {
                    selectOnly(node.id());
                }
                checkpoint();
                draggingNode = node.id();
                draggingNodeMoved = false;
                syncInspector();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && group != null) {
                checkpoint();
                selectGroup(group);
                int gx = screenX(group.getDouble("X"));
                int gy = screenY(group.getDouble("Y"));
                int gw = (int) (group.getDouble("Width") * zoom);
                int gh = (int) (group.getDouble("Height") * zoom);
                int handleSize = Math.max(1, (int) Math.round(12 * zoom));
                if (mouseX >= gx + gw - handleSize && mouseY >= gy + gh - handleSize) resizingGroup = selectedGroup;
                else draggingGroup = selectedGroup;
                syncInspector();
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (node == null && group == null && handleCanvasDoubleClick(mouseX, mouseY)) {
                    AdvancedGraphDocument.Node note = addNode("sticky_note", graphX(mouseX), graphY(mouseY));
                    if (note != null) {
                        beginStickyEditing(note, mouseX, mouseY);
                    }
                    return true;
                }
                if (!hasControlDown()) {
                    selectedGroup = null;
                    clearSelection();
                }
                marquee = true;
                marqueeStartX = mouseX;
                marqueeStartY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Handle the share window click
    private boolean handleShareWindowClick(double mouseX, double mouseY, int btn) {
        if (!sharedGraphConflictName.isBlank()) {
            return clickShareConflict(mouseX, mouseY, btn);
        }
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        UiRect bounds = shareModalBounds();
        int x = bounds.x() + 16;
        int y = bounds.y() + 28;
        if (inside(mouseX, mouseY, x, y + 30, 100, 18)) {
            saveCurrentGraphLocally(false);
            return true;
        }
        if (inside(mouseX, mouseY, x + 108, y + 30, 152, 18)) {
            if (canUploadSharedGraph()) saveCurrentGraphLocally(true);
            return true;
        }
        if (inside(mouseX, mouseY, x, y + 54, 260, 18)) {
            openSharedGraphsDir();
            return true;
        }
        int listY = y + 92;
        if (inside(mouseX, mouseY, x, listY, 132, 58)) {
            int row = ((int) mouseY - listY - 3) / 13;
            int idx = sharedGraphScroll + Math.max(0, row);
            if (idx >= 0 && idx < sharedGraphEntries.size()) {
                selectedSharedGraphIndex = idx;
            }
            return true;
        }
        if (inside(mouseX, mouseY, x, y + 155, 64, 18)) {
            requestSharedGraphs();
            return true;
        }
        if (inside(mouseX, mouseY, x + 68, y + 155, 64, 18)) {
            loadSelectedSharedGraph();
            return true;
        }
        int webX = bounds.x() + 156;
        if (publicShareAvailable && !publicSharePending
                && inside(mouseX, mouseY, webX, bounds.y() + 120, 120, 18)) {
            shareCurrentGraph();
            return true;
        }
        if (inside(mouseX, mouseY, webX, bounds.y() + 144, 120, 18)) {
            Util.getPlatform().openUri(ControllerGraphWebServer.localEditorUri());
            return true;
        }
        return false;
    }

    // Handle the freq modal click
    private boolean clickFreqModal(double mouseX, double mouseY, int btn) {
        UiRect bounds = frequencyModalBounds();
        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT && freqClearBtnBounds().contains(mouseX, mouseY)) {
            clearFrequencySlots();
            return true;
        }

        Slot slot = activeSlotAt(mouseX, mouseY);
        if (slot != null && bounds.contains(mouseX, mouseY)) {
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT && hasShiftDown()
                    && isControllerUtilityItem(slot.getItem())) {
                return true;
            }
            if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT && slot instanceof SlotItemHandler itemSlot
                    && isFrequencyGhostSlot(itemSlot)) {
                clearFrequencySlot(itemSlot.getSlotIndex());
                return true;
            }
            super.mouseClicked(mouseX, mouseY, btn);
            syncFrequencyNode();
            return true;
        }

        if (!bounds.contains(mouseX, mouseY)) {
            closeFrequencyEditor();
        }
        return true;
    }

    // Check if this is a controller utility item
    private static boolean isControllerUtilityItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getItem() instanceof ContraptionNetworkLinkerItem
                || com.rieno.gadgetsandgizmos.content.PhysicsGogglesItem
                .isFunctionalGoggles(stack));
    }

    // Get the active slot
    private Slot activeSlotAt(double mouseX, double mouseY) {
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    // Check if this is a frequency ghost slot
    private boolean isFrequencyGhostSlot(SlotItemHandler slot) {
        return slot.getItemHandler() == menu.ghostInventory
                && slot.getSlotIndex() >= 0
                && slot.getSlotIndex() < 2;
    }

    // Clear the frequency slot
    private void clearFrequencySlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= 2) {
            return;
        }
        checkpoint();
        menu.ghostInventory.setStackInSlot(slotIndex, ItemStack.EMPTY);
        syncFrequencyNode();
    }

    // Clear the frequency slots
    private void clearFrequencySlots() {
        checkpoint();
        menu.ghostInventory.setStackInSlot(0, ItemStack.EMPTY);
        menu.ghostInventory.setStackInSlot(1, ItemStack.EMPTY);
        syncFrequencyNode();
    }

    // Handle the sidebar handle click
    private boolean handleSidebarHandleClick(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseY < TOOLBAR_HEIGHT) return false;
        int leftX = leftSidebarHandleX();
        if (mouseX >= leftX && mouseX < leftX + SIDEBAR_HANDLE_WIDTH) {
            leftSidebarCollapsed = !leftSidebarCollapsed;
            saveUiPreferences();
            ensureSidebarFit();
            resize(minecraft, width, height);
            return true;
        }
        int rightX = rightSidebarHandleX();
        if (mouseX >= rightX && mouseX < rightX + SIDEBAR_HANDLE_WIDTH) {
            rightSidebarCollapsed = !rightSidebarCollapsed;
            saveUiPreferences();
            ensureSidebarFit();
            resize(minecraft, width, height);
            return true;
        }
        return false;
    }

    // Handle mouse dragged
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        wireMouseX = mouseX;
        wireMouseY = mouseY;
        // ------------------------------------OVERLAY DRAGGING------------------------------------
        if (draggingV2MinimapViewport && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            AdvancedControllerMinimapGeometry.Transform transform = v2MinimapDragTransform == null
                    ? v2MinimapTransform(activeNodes()) : v2MinimapDragTransform;
            panV2MinimapViewport(mouseX, mouseY, transform);
            return true;
        }
        if (draggingOptionDropdownThumb && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateOptionThumb(mouseY);
            return true;
        }
        if (hudOpen) {
            if (draggingHudColorControl != null) {
                updateHudColorSlider(mouseX);
                return true;
            }
            if (draggingHudPropSlider != null) {
                updateHudPropSlider(mouseX);
                return true;
            }
            dragHud(mouseX, mouseY, button);
            return true;
        }
        // ------------------------------------EDITOR CONTROLS------------------------------------
        if (draggingInspectorDivider != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            InspectorSections sections = inspectorSections(selectedNode());
            if (draggingInspectorDivider == InspectorDivider.OPTIONS) {
                inspectorOptionsHeight = Mth.clamp((int) mouseY - sections.optionsTop(),
                        INSPECTOR_SECTION_MIN_CONTENT_HEIGHT, 800);
            } else {
                inspectorTargetsHeight = Mth.clamp((int) mouseY - sections.targetsTop(),
                        INSPECTOR_SECTION_MIN_CONTENT_HEIGHT, 800);
            }
            return true;
        }
        if (draggingCurveNode != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateDraggedCurvePoint(mouseX, mouseY);
            return true;
        }
        if (draggingSliderNode != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateDraggedSlider(mouseX);
            return true;
        }
        // ------------------------------------WINDOW / CANVAS------------------------------------
        if (draggingLinker && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            linkerX = Mth.clamp(linkerX + (int) dragX, layoutLeft(),
                    Math.max(layoutLeft(), layoutRight() - LINKER_MODAL_WIDTH));
            linkerY = Mth.clamp(linkerY + (int) dragY, TOOLBAR_HEIGHT,
                    Math.max(TOOLBAR_HEIGHT, height - LINKER_MODAL_HEIGHT));
            setLinkerOpen(true);
            return true;
        }
        if (panning && button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            panX += dragX;
            panY += dragY;
            storeViewport();
            return true;
        }
        if (rightPanPending && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (!rightPanning && exceedsRightPanDeadzone(
                    rightPanStartX, rightPanStartY, mouseX, mouseY)) {
                rightPanning = true;
                panX += mouseX - rightPanStartX;
                panY += mouseY - rightPanStartY;
                storeViewport();
            } else if (rightPanning) {
                panX += dragX;
                panY += dragY;
                storeViewport();
            }
            return true;
        }
        // ------------------------------------GRAPH ELEMENTS------------------------------------
        if (draggingNode != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            moveSelected(dragX / zoom, dragY / zoom);
            draggingNodeMoved |= dragX != 0.0D || dragY != 0.0D;
            return true;
        }
        if (draggingGroup != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            moveGroup(draggingGroup, dragX / zoom, dragY / zoom);
            return true;
        }
        if (resizingGroup != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            resizeGroup(resizingGroup, dragX / zoom, dragY / zoom);
            return true;
        }
        if (resizingStickyNode != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            resizeStickyNode(resizingStickyNode, dragX / zoom, dragY / zoom);
            return true;
        }
        if (marquee && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (connectingNode != null) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // Handle mouse released
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingOptionDropdownThumb = false;
        if (draggingV2MinimapViewport && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingV2MinimapViewport = false;
            v2MinimapDragTransform = null;
            return true;
        }
        if (hudOpen) {
            draggingHudElement = false;
            resizingHudElement = false;
            draggingHudColorKey = null;
            draggingHudColorControl = null;
            draggingHudPropSlider = null;
            return true;
        }
        if (rightPanPending && button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            boolean wasPan = rightPanning || exceedsRightPanDeadzone(
                    rightPanStartX, rightPanStartY, mouseX, mouseY);
            double clickX = rightPanStartX;
            double clickY = rightPanStartY;
            rightPanPending = false;
            rightPanning = false;
            if (!wasPan) {
                rightClickGraph(clickX, clickY);
            }
            return true;
        }
        if (draggingInspectorDivider != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingInspectorDivider = null;
            saveUiPreferences();
            return true;
        }
        if (connectingNode != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            PortHit hit = portAt(mouseX, mouseY);
            if (hit != null && hit.output() != connectingOutput) {
                connectPorts(hit);
                clearConnection();
            } else if (inGraph(mouseX, mouseY)) {
                openMiniBrowser((int) mouseX, (int) mouseY, connectingNode, connectingPort, connectingOutput);
            } else {
                clearConnection();
            }
            return true;
        }
        if (marquee && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selectMarquee(mouseX, mouseY);
            marquee = false;
            syncInspector();
            return true;
        }
        if (draggingNode != null && draggingNodeMoved && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            updateCommentGroupAfterDrop();
        }
        draggingNode = null;
        draggingNodeMoved = false;
        draggingGroup = null;
        resizingGroup = null;
        resizingStickyNode = null;
        draggingLinker = false;
        draggingSliderNode = null;
        draggingSliderPort = null;
        draggingInspectorSlider = false;
        draggingOutputSlider = false;
        draggingCurveNode = null;
        draggingCurvePoint = -1;
        draggingInspectorCurve = false;
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // Handle the right click graph
    private void rightClickGraph(double mouseX, double mouseY) {
        PortHit port = portAt(mouseX, mouseY);
        if (port != null) {
            selectedGroup = null;
            selectOnly(port.node().id());
            PortContext portContext = new PortContext(
                    port.node().id(), port.port(), port.output());
            contextMenu = new ContextMenu((int) mouseX, (int) mouseY,
                    portContextActions(port), "", portContext);
            syncInspector();
            return;
        }
        AdvancedGraphDocument.Node node = nodeAt(mouseX, mouseY);
        CompoundTag group = node == null ? groupAt(mouseX, mouseY) : null;
        if (node != null) {
            if (handleBodyCurveClick(node, mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                    || rightClickHudBody(node, mouseX, mouseY)
                    || rightClickConstructor(node, mouseX, mouseY)) {
                return;
            }
            selectedGroup = null;
            if (!selectedNodes.contains(node.id())) {
                selectOnly(node.id());
            }
            contextMenu = new ContextMenu((int) mouseX, (int) mouseY,
                    nodeContextActions(), "", null);
            return;
        }
        if (group != null) {
            selectGroup(group);
            List<String> actions = new ArrayList<>(nodeContextActions());
            actions.add("Delete Comment Group");
            contextMenu = new ContextMenu((int) mouseX, (int) mouseY,
                    List.copyOf(actions), "", null);
            return;
        }
        openMiniBrowser((int) mouseX, (int) mouseY, null, null, true);
    }

    // Get the node context actions
    private List<String> nodeContextActions() {
        List<String> actions = new ArrayList<>(List.of(
                "Duplicate", "Delete", "Create Comment Group", "Disconnect Wires",
                "Collapse Input to MAP", "Collapse Output to MAP"));
        if (selectedNodes.size() == 1) {
            actions.add("Copy Node ID");
        }
        if (selectedNodes.size() > 1) {
            actions.add("Convert to Function");
        }
        return List.copyOf(actions);
    }

    // Get the port context actions
    private List<String> portContextActions(PortHit hit) {
        List<String> actions = new ArrayList<>();
        boolean persistent = AdvancedGraphPortState.isPersistent(
                hit.node(), hit.port(), hit.output());
        actions.add(persistent ? "Persistent: On" : "Persistent: Off");
        if (switchDataCaseInput(hit.node(), hit.port(), hit.output())) {
            String currentType = AdvancedGraphPortState.switchCaseType(
                    hit.node(), hit.port());
            for (String type : AdvancedGraphPortState.SWITCH_DATA_TYPES) {
                actions.add("Type: " + humanPort(type)
                        + (type.equals(currentType) ? " *" : ""));
            }
        }
        String portType = (hit.output()
                ? AdvancedGraphCatalog.outputs(hit.node())
                : AdvancedGraphCatalog.inputs(hit.node())).get(hit.port());
        if ("map".equals(portType)) {
            actions.add("Breakout");
            if (canCollapseInlineMap(hit.node(), hit.port(), hit.output())) {
                actions.add("Collapse");
            }
        }
        if (portType != null && !"exec".equals(portType)) {
            actions.add("Promote to Variable");
        }
        if (canRemoveDynamicPort(hit.node(), hit.port(), hit.output())) {
            actions.add("Remove Port");
        }
        return List.copyOf(actions);
    }

    // Handle mouse scrolled
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // -----------------------------------------------------MODAL WINDOWS-----------------------------------------------------
        if (frequencyModalOpen) {
            return true;
        }
        if (graphHistoryOpen) {
            int maximum = Math.max(0, graphHistoryEntries.size() - GRAPH_HISTORY_VISIBLE_ROWS);
            graphHistoryScroll = Mth.clamp(
                    graphHistoryScroll - (int) Math.signum(scrollY), 0, maximum);
            return true;
        }
        if (mouseX >= graphLeft() && mouseX < graphRight()
                && mouseY >= TOOLBAR_HEIGHT && mouseY < graphTop()) {
            int maximum = Math.max(0, graphTabsContentWidth() - (graphRight() - graphLeft()));
            functionTabScroll = Mth.clamp(
                    functionTabScroll - (int) Math.round(scrollY * 32.0D), 0, maximum);
            return true;
        }
        // ------------------------------------DROPDOWN SCROLL------------------------------------
        if (optionDropdown != null) {
            int visibleRows = Math.min(OPTION_DROPDOWN_VISIBLE_ROWS, optionDropdown.options().size());
            int maximum = Math.max(0, optionDropdown.options().size() - visibleRows);
            int scroll = Mth.clamp(
                    optionDropdown.scroll() - (int) Math.signum(scrollY), 0, maximum);
            optionDropdown = new OptionDropdown(
                    optionDropdown.x(), optionDropdown.y(), optionDropdown.width(),
                    optionDropdown.nodeId(), optionDropdown.port(), optionDropdown.type(),
                    optionDropdown.options(), optionDropdown.property(), scroll,
                    optionDropdown.searchable());
            return true;
        }
        // -----------------------------------------------------HUD DESIGNER-----------------------------------------------------
        if (hudOpen) {
            if (hudBindingDropdownKey != null) {
                List<String> ports = hudBindingPorts(hudNode());
                int maxScroll = Math.max(0, ports.size() - HUD_PORT_VISIBLE_ROWS);
                hudBindingDropdownScroll = Mth.clamp(
                        hudBindingDropdownScroll - (int) Math.signum(scrollY), 0, maxScroll);
            } else if (hudImageDropdownOpen) {
                List<String> images = AdvancedHudImageClient.uploadedImages();
                int maxScroll = Math.max(0, images.size() - HUD_IMAGE_VISIBLE_ROWS);
                hudImageDropdownScroll = Mth.clamp(
                        hudImageDropdownScroll - (int) Math.signum(scrollY), 0, maxScroll);
            } else if (hudPortDropdownOpen) {
                List<String> ports = hudValPorts(hudNode());
                int maxScroll = Math.max(0, ports.size() - HUD_PORT_VISIBLE_ROWS);
                hudPortDropdownScroll = Mth.clamp(
                        hudPortDropdownScroll - (int) Math.signum(scrollY), 0, maxScroll);
            } else if (hudDataBindings) {
                List<String> properties = hudBindableProps(hudElement());
                int visibleRows = hudBindingVisibleRows();
                int maxScroll = Math.max(0, properties.size() - visibleRows);
                hudBindingPropScroll = Mth.clamp(
                        hudBindingPropScroll - (int) Math.signum(scrollY),
                        0, maxScroll);
            }
            return true;
        }
        if (templatePicker || optionDropdown != null || contextMenu != null) {
            return true;
        }
        if (!sharedGraphConflictName.isBlank()) {
            return true;
        }
        if (miniBrowser != null) {
            miniBrowserScroll = Math.max(0, miniBrowserScroll - (int) (scrollY * 34));
            return true;
        }
        // -----------------------------------------------------SIDE PANELS-----------------------------------------------------
        UiRect shareBounds = shareModalBounds();
        if (shareModalOpen && mouseX >= shareBounds.x() + 16 && mouseX <= shareBounds.x() + 148
                && mouseY >= shareBounds.y() + 120 && mouseY <= shareBounds.y() + 178) {
            int maxScroll = Math.max(0, sharedGraphEntries.size() - 4);
            sharedGraphScroll = Mth.clamp(sharedGraphScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        if (shareModalOpen) {
            return true;
        }
        if (linkerOpen) {
            return true;
        }
        if (!leftSidebarCollapsed && mouseX >= layoutLeft() && mouseX < layoutLeft() + activeLeftWidth()) {
            if (blockBrowserOpen) blockBrowserScroll = Math.max(0, blockBrowserScroll - (int) (scrollY * 34));
            else browserScroll = Math.max(0, browserScroll - (int) (scrollY * 34));
            return true;
        }
        if (!rightSidebarCollapsed && mouseX >= graphRight() && mouseX < layoutRight() && mouseY >= TOOLBAR_HEIGHT) {
            AdvancedGraphDocument.Node node = selectedNode();
            InspectorSections sections = inspectorSections(node);
            if (!inspectorOptionsCollapsed && mouseY >= sections.optionsTop()
                    && mouseY < sections.optionsBottom()) {
                inspectorOptionsScroll = Math.max(0, inspectorOptionsScroll - (int) (scrollY * 34));
            } else if (!inspectorTargetsCollapsed && mouseY >= sections.targetsTop()
                    && mouseY < sections.targetsBottom()) {
                inspectorTargetsScroll = Math.max(0, inspectorTargetsScroll - (int) (scrollY * 34));
            }
            return true;
        }
        // -----------------------------------------------------GRAPH ZOOM-----------------------------------------------------
        if (inGraph(mouseX, mouseY)) {
            double graphMouseX = graphX(mouseX);
            double graphMouseY = graphY(mouseY);
            zoom = Mth.clamp(zoom + scrollY * 0.1, 0.05, 1.75);
            panX = mouseX - graphLeft() - graphMouseX * zoom;
            panY = mouseY - graphTop() - graphMouseY * zoom;
            storeViewport();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Handle key pressed
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // -----------------------------------------------------EDITOR MODALS-----------------------------------------------------
        if (functionNameEditor != null && functionNameEditor.visible) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishFunctionRename(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishFunctionRename(true);
                return true;
            }
            functionNameEditor.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        if (hudOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (hudImageDropdownOpen) {
                    hudImageDropdownOpen = false;
                } else if (hudStyleDropdownOpen) {
                    hudStyleDropdownOpen = false;
                } else if (hudFontStyleDropdownOpen) {
                    hudFontStyleDropdownOpen = false;
                } else if (hudPortDropdownOpen) {
                    hudPortDropdownOpen = false;
                } else {
                    closeHud();
                }
                return true;
            }
            if (getFocused() instanceof EditBox editBox) {
                editBox.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
            return hudKeyPressed(keyCode);
        }
        if (!sharedGraphConflictName.isBlank()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                sharedGraphConflictName = "";
                sharedGraphSaveTarget = SharedGraphSaveTarget.NONE;
            }
            return true;
        }
        if (editingStickyNode != null) {
            return handleStickyEditorKey(keyCode);
        }
        if (frequencyModalOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeFrequencyEditor();
            return true;
        }
        if (listeningKeyNode != null) {
            AdvancedGraphDocument.Node node = findNode(listeningKeyNode);
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (node != null) node.data().putString("BindingLabel", valueOrUnset(node.data().getString("BindingId")));
                listeningKeyNode = null;
                return true;
            }
            if (node != null) {
                String binding = "graph_" + node.id().replaceAll("[^A-Za-z0-9_]", "_");
                node.data().putString("BindingId", binding);
                node.data().putString("BindingLabel", InputConstants.getKey(keyCode, scanCode).getDisplayName().getString());
                node.data().putInt("KeyCode", keyCode);
                node.data().putBoolean("GraphOwnedBinding", true);
                node.data().remove("Channel");
            }
            listeningKeyNode = null;
            syncInspector();
            return true;
        }
        // ------------------------------------WINDOW CONTROLS------------------------------------
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (contextMenu != null || optionDropdown != null || miniBrowser != null
                    || templatePicker || linkerOpen || shareModalOpen || graphHistoryOpen || toolsMenuOpen) {
                contextMenu = null;
                closeOptionDropdown();
                closeMiniBrowser();
                templatePicker = false;
                graphHistoryOpen = false;
                toolsMenuOpen = false;
                setLinkerOpen(false);
                setShareModalOpen(false);
                clearConnection();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (contextMenu != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !contextMenu.query().isEmpty()) {
                contextMenu = new ContextMenu(contextMenu.x(), contextMenu.y(), contextMenu.items(),
                        contextMenu.query().substring(0, contextMenu.query().length() - 1),
                        contextMenu.port());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                List<String> actions = filteredContextItems();
                if (!actions.isEmpty()) runContextMenuAction(actions.getFirst());
                contextMenu = null;
                return true;
            }
        }
        // ------------------------------------DOCUMENT SHORTCUTS------------------------------------
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_S) {
            long requestId = beginGraphActionToast("Saving and applying graph...");
            saveAndApplyDraft("save_apply", requestId);
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
            restoreFrom(hasShiftDown() ? redo : undo, hasShiftDown() ? undo : redo);
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
            restoreFrom(redo, undo);
            return true;
        }
        if (getFocused() instanceof EditBox editBox) {
            editBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        // ------------------------------------SELECTION SHORTCUTS------------------------------------
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && selectedGroup != null) {
            deleteSelectedGroup();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && !selectedNodes.isEmpty()) {
            deleteSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D && hasControlDown()) {
            duplicateSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C && hasControlDown()) {
            copySelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && hasControlDown()) {
            pasteCopied();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G && hasControlDown() && !selectedNodes.isEmpty()) {
            createCommentGroup();
            return true;
        }
        // -----------------------------------------------------GRAPH INPUT-----------------------------------------------------
        sendGraphKeyInput(keyCode, true);
        return true;
    }

    // Handle typed characters
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (hudOpen) {
            if (getFocused() instanceof EditBox editBox) {
                return editBox.charTyped(codePoint, modifiers);
            }
            return true;
        }
        if (!sharedGraphConflictName.isBlank()) {
            return true;
        }
        if (editingStickyNode != null) {
            if (!Character.isISOControl(codePoint)) {
                insertStickyText(Character.toString(codePoint));
            }
            return true;
        }
        if (contextMenu != null && !Character.isISOControl(codePoint)) {
            contextMenu = new ContextMenu(contextMenu.x(), contextMenu.y(), contextMenu.items(),
                    contextMenu.query() + Character.toString(codePoint).toLowerCase(Locale.ROOT),
                    contextMenu.port());
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    // Handle key released
    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (hudOpen) return true;
        if (!sharedGraphConflictName.isBlank()) return true;
        if (editingStickyNode != null) return true;
        if (sendGraphKeyInput(keyCode, false)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    // Send the graph key input
    private boolean sendGraphKeyInput(int keyCode, boolean pressed) {
        Set<String> bindings;
        if (pressed) {
            if (activeGraphKeyBindings.containsKey(keyCode)) return true;
            bindings = new LinkedHashSet<>();
            for (AdvancedGraphDocument.Node node : activeNodes()) {
                if (!node.data().getBoolean("GraphOwnedBinding") || node.data().getInt("KeyCode") != keyCode) continue;
                String binding = node.data().getString("BindingId");
                if (!binding.isBlank()) bindings.add(binding);
            }
            if (bindings.isEmpty()) return false;
            activeGraphKeyBindings.put(keyCode, bindings);
        } else {
            bindings = activeGraphKeyBindings.remove(keyCode);
            if (bindings == null || bindings.isEmpty()) return false;
        }
        for (String binding : bindings) {
            if (draftSimulationRuntime != null) {
                draftSimulationRuntime.setBindingActive(binding, pressed);
            }
            PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(
                    menu.getContentPos(), menu.getContentSubLevelId(), binding, pressed));
        }
        return true;
    }

    // Handle the node browser click
    private boolean handleNodeBrowserClick(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseY < 82) return true;
        int y = 86 - browserScroll;
        for (BrowserEntry entry : nodeBrowserEntries(nodeSearch.getValue().trim().toLowerCase(Locale.ROOT))) {
            int rowHeight = browserEntryRowHeight(entry);
            if (mouseY >= y - 2 && mouseY < y + rowHeight - 2) {
                if (entry.category()) toggle(collapsedCategories, entry.id());
                else if (entry.id().startsWith("function:")
                        && mouseX >= layoutLeft() + activeLeftWidth() - 24
                        && mouseX < layoutLeft() + activeLeftWidth() - 9) {
                    deleteFunction(entry.id().substring("function:".length()));
                } else {
                    addNode(entry.id(), graphCenterX(), graphCenterY());
                }
                return true;
            }
            y += rowHeight;
        }
        return true;
    }

    // Handle the block browser click
    private boolean handleBlockBrowserClick(double mouseX, double mouseY, int btn) {
        if (mouseY < 82) return true;
        int y = 86 - blockBrowserScroll;
        for (RegistryEntry entry : registryEntries()) {
            int rowHeight = entry.header() ? 19 : 20;
            if (mouseY >= y - 2 && mouseY < y + rowHeight - 2) {
                if (entry.header()) {
                    toggle(collapsedBlockNamespaces, entry.id());
                } else {
                    applyRegistryEntry(entry.id(), btn);
                }
                return true;
            }
            y += rowHeight;
        }
        return true;
    }

    // Apply the registry entry
    private void applyRegistryEntry(String id, int btn) {
        AdvancedGraphDocument.Node node = selectedNode();
        if (node == null) return;
        checkpoint();
        if (node.type().startsWith("wireless_frequency")) {
            putFrequencyStack(node,
                    btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? "FrequencySecond" : "FrequencyFirst",
                    stackFromId(id));
            putInputDefault(node, "frequency", "frequency", node.data().getString("FrequencyFirst") + "|" + node.data().getString("FrequencySecond"));
        } else if (node.type().contains("target") || node.type().startsWith("linker_face")) {
            node.data().putString("Target", id);
            node.data().putString("TargetLabel", id);
            putInputDefault(node, "target", "target", id);
        } else {
            node.data().putString("RegistryId", id);
            if (selectedInputPort != null) putInputDefault(node, selectedInputPort, "string", id);
        }
    }

    // Handle the rich body click
    private boolean handleRichBodyClick(AdvancedGraphDocument.Node node, double mouseX, double mouseY) {
        // -----------------------------------------------------BODY HIT TEST-----------------------------------------------------
        int x = screenX(node.x());
        int y = screenY(node.y());
        int bodyTop = nodeBodyTop(node, y);
        int rowHeight = Math.max(10, (int) (15 * zoom));
        if ("curve".equals(node.type())) bodyTop += (int) (CURVE_BODY_HEIGHT * zoom);
        if (mouseX < x + 5 || mouseX > x + NODE_WIDTH * zoom - 5 || mouseY < bodyTop
                || mouseY > bodyTop + bodyControlCount(node) * rowHeight) {
            return false;
        }
        int row = (int) ((mouseY - bodyTop) / rowHeight);
        // ------------------------------------BINDING CONTROL------------------------------------
        if (usesBinding(node)) {
            if (row == 0) {
                cycleBinding(node);
                return true;
            }
            row--;
        }
        // -----------------------------------------------------TYPE CONTROL-----------------------------------------------------
        if (hasSwitchTypeControl(node)) {
            if (row == 0) {
                selectOnly(node.id());
                selectedInputPort = AdvancedGraphCatalog.SWITCH_TYPE_TAG;
                openPropertyDropdown(node, AdvancedGraphCatalog.SWITCH_TYPE_TAG,
                        x + 5, mouseY + 8, NODE_WIDTH * zoom - 10, SWITCH_TYPE_OPTIONS);
                syncInspector();
                return true;
            }
            row--;
        }
        // ------------------------------------PROPERTY CONTROL------------------------------------
        if (hasPropertyControl(node)) {
            if (row == 0) {
                selectOnly(node.id());
                if ("mouse_input".equals(node.type())) {
                    selectedInputPort = "MouseInput";
                    openPropertyDropdown(node, "MouseInput", x + 5, mouseY + 8,
                            NODE_WIDTH * zoom - 10, MOUSE_INPUT_OPTIONS);
                    syncInspector();
                    return true;
                }
                if ("portable_tracker".equals(node.type())) {
                    selectedInputPort = "GogglesPair";
                    openPropertyDropdown(node, "GogglesPair", x + 5, mouseY + 8,
                            NODE_WIDTH * zoom - 10, gogglesTrackerPairIds());
                    syncInspector();
                    return true;
                }
                if ("PulseBehavior".equals(editableProperty(node))) {
                    selectedInputPort = "PulseBehavior";
                    openPropertyDropdown(node, "PulseBehavior", x + 5, mouseY + 8,
                            NODE_WIDTH * zoom - 10, PULSE_BEHAVIOR_OPTIONS);
                    syncInspector();
                    return true;
                }
                if (isAccDisplayWidgetType(node.type())) {
                    selectedInputPort = "WidgetType";
                    openPropertyDropdown(node, "WidgetType", x + 5, mouseY + 8,
                            NODE_WIDTH * zoom - 10, ACC_DISPLAY_WIDGET_TYPES);
                    syncInspector();
                    return true;
                }
                if ("acc_display_crn".equals(node.type())) {
                    selectedInputPort = "DisplayMode";
                    openPropertyDropdown(node, "DisplayMode", x + 5, mouseY + 8,
                            NODE_WIDTH * zoom - 10, ACC_DISPLAY_CRN_MODES);
                    syncInspector();
                    return true;
                }
                if ("event_variable_change".equals(node.type())) {
                    selectedInputPort = "Variable";
                    openPropertyDropdown(node, "Variable", x + 5, mouseY + 8,
                            NODE_WIDTH * zoom - 10, variableSelectorOptions(node));
                    syncInspector();
                    return true;
                }
                applyPropertyControl(node);
                syncInspector();
                if (!List.of("constant_boolean", "convert_type", "curve").contains(node.type())) {
                    openBodyEditor(node, editableProperty(node), bodyTop, rowHeight);
                }
                return true;
            }
            row--;
        }
        // ------------------------------------INPUT CONTROLS------------------------------------
        for (var port : AdvancedGraphCatalog.inputs(node).entrySet()) {
            if ("exec".equals(port.getValue()) || ("curve".equals(node.type()) && "value".equals(port.getKey()))) continue;
            if (!isDataPortVisible(node, port.getKey(), false)) continue;
            if (row-- != 0) continue;
            if (isInputConnected(node, port.getKey())) return false;
            selectOnly(node.id());
            selectedInputPort = port.getKey();
            int portRow = (int) ((mouseY - bodyTop) / rowHeight);
            if ("number".equals(port.getValue()) && bodySliderTrack(node, port.getKey()).containsX(mouseX, 6)) {
                startSliderDrag(node, port.getKey(), false, mouseX);
            } else if (!inputOptions(node, port.getKey()).isEmpty()
                    || List.of("boolean", "direction", "frequency", "target").contains(port.getValue())) {
                int controlInset = inputControlInset(node, port.getKey());
                applyInputControl(node, port.getKey(), port.getValue(), mouseX, mouseY,
                        x + 5 + controlInset, NODE_WIDTH * zoom - 10 - controlInset);
            } else {
                syncInspector();
                openBodyEditor(node, port.getKey(), bodyTop + portRow * rowHeight, rowHeight);
                return true;
            }
            syncInspector();
            return true;
        }
        if (isHudNode(node) && row-- == 0) {
            addHudField(node);
            return true;
        }
        if (isConstructorNode(node) && row-- == 0) {
            addConstructorInput(node);
            return true;
        }
        if (isFunctionInterfaceNode(node) && row-- == 0) {
            openFunctionPortDropdown(node, x + 5, mouseY + 8, NODE_WIDTH * zoom - 10);
            return true;
        }
        return false;
    }

    // Handle a right click on the HUD body
    private boolean rightClickHudBody(AdvancedGraphDocument.Node node, double mouseX, double mouseY) {
        if (!isHudNode(node)) return false;
        String port = hudBodyPortAt(node, mouseX, mouseY);
        if (port == null || isHudReservedPort(port)) return false;
        if (hasShiftDown()) {
            removeHudField(node, port);
            return true;
        }
        checkpoint();
        selectOnly(node.id());
        selectedInputPort = "label:" + port;
        syncInspector();
        int bodyTop = nodeBodyTop(node, screenY(node.y()));
        int rowHeight = Math.max(10, (int) (15 * zoom));
        if ("curve".equals(node.type())) bodyTop += (int) (CURVE_BODY_HEIGHT * zoom);
        int row = (int) ((mouseY - bodyTop) / rowHeight);
        if (usesBinding(node)) row--;
        row -= propertyControlCount(node);
        int visibleRow = 0;
        for (var entry : AdvancedGraphCatalog.inputs(node).entrySet()) {
            if ("exec".equals(entry.getValue()) || ("curve".equals(node.type()) && "value".equals(entry.getKey()))) continue;
            if (!isDataPortVisible(node, entry.getKey(), false)) continue;
            if (entry.getKey().equals(port)) break;
            visibleRow++;
        }
        openBodyEditor(node, "label:" + port, bodyTop + visibleRow * rowHeight, rowHeight);
        return true;
    }

    // Delete the function
    private void deleteFunction(String functionId) {
        AdvancedGraphDocument.FunctionGraph function = draft.function(functionId);
        if (function == null) {
            return;
        }
        checkpoint();
        if (Objects.equals(activeFunctionId, functionId)) {
            selectGraphTab(null);
        }
        if (Objects.equals(editingFunctionId, functionId)) {
            finishFunctionRename(false);
        }
        AdvancedGraphFunctions.removeFunction(draft, functionId);
        clearSelection();
        clearGraphRenderCache();
        syncInspector();
        displayGraphActionToast(new GraphActionToast(
                Component.literal("Function deleted from the graph."),
                GraphActionToastSeverity.SUCCESS));
    }

    // Handle a right click in the HUD inspector
    private boolean rightClickHudInspector(double mouseX, double mouseY) {
        AdvancedGraphDocument.Node node = selectedNode();
        if (!isHudNode(node) || inspectorOptionsCollapsed) return false;
        InspectorSections sections = inspectorSections(node);
        if (mouseY < sections.optionsTop() || mouseY >= sections.optionsBottom()) return false;
        int rowY = sections.optionsTop() - inspectorOptionsScroll;
        rowY += 17;
        rowY += propertyControlCount(node) * 17;
        for (var port : AdvancedGraphCatalog.inputs(node).entrySet()) {
            if ("exec".equals(port.getValue())
                    || ("curve".equals(node.type()) && "value".equals(port.getKey()))) continue;
            int rowHeight = "frequency".equals(port.getValue()) ? 22 : 17;
            if (mouseY >= rowY - 3 && mouseY < rowY + rowHeight - 4) {
                if (isHudReservedPort(port.getKey())) return true;
                if (hasShiftDown()) {
                    removeHudField(node, port.getKey());
                    return true;
                }
                checkpoint();
                selectedInputPort = "label:" + port.getKey();
                editingBodyValue = false;
                syncInspector();
                inspectorValue.setFocused(true);
                setFocused(inspectorValue);
                return true;
            }
            rowY += rowHeight;
        }
        return false;
    }

    // Handle a right click on the constructor
    private boolean rightClickConstructor(AdvancedGraphDocument.Node node,
                                                        double mouseX, double mouseY) {
        if (!isConstructorNode(node)) return false;
        String port = hudBodyPortAt(node, mouseX, mouseY);
        if (port == null || !constructorValuePort(node, port)) return false;
        if (hasShiftDown()) {
            removeConstructorInput(node, port);
            return true;
        }
        checkpoint();
        ensureDynamicConstructor(node);
        if (!AdvancedGraphCatalog.inputs(node).containsKey(port)) return true;
        selectOnly(node.id());
        selectedInputPort = CONSTRUCTOR_LABEL_PREFIX + port;
        syncInspector();
        int bodyTop = nodeBodyTop(node, screenY(node.y()));
        int rowHeight = Math.max(10, (int) (15 * zoom));
        int visibleRow = 0;
        for (var entry : AdvancedGraphCatalog.inputs(node).entrySet()) {
            if ("exec".equals(entry.getValue())) continue;
            if (entry.getKey().equals(port)) break;
            visibleRow++;
        }
        openBodyEditor(node, CONSTRUCTOR_LABEL_PREFIX + port,
                bodyTop + visibleRow * rowHeight, rowHeight);
        return true;
    }

    // Handle a right click in the constructor inspector
    private boolean rightClickConstructorInspector(double mouseX, double mouseY) {
        AdvancedGraphDocument.Node node = selectedNode();
        if (!isConstructorNode(node) || inspectorOptionsCollapsed) return false;
        InspectorSections sections = inspectorSections(node);
        if (mouseY < sections.optionsTop() || mouseY >= sections.optionsBottom()) return false;
        int rowY = sections.optionsTop() - inspectorOptionsScroll;
        rowY += 17;
        rowY += propertyControlCount(node) * 17;
        for (var port : AdvancedGraphCatalog.inputs(node).entrySet()) {
            if ("exec".equals(port.getValue())) continue;
            int rowHeight = "frequency".equals(port.getValue()) ? 22 : 17;
            if (mouseY >= rowY - 3 && mouseY < rowY + rowHeight - 4) {
                if (!constructorValuePort(node, port.getKey())) return true;
                if (hasShiftDown()) {
                    removeConstructorInput(node, port.getKey());
                    return true;
                }
                checkpoint();
                ensureDynamicConstructor(node);
                if (!AdvancedGraphCatalog.inputs(node).containsKey(port.getKey())) return true;
                selectedInputPort = CONSTRUCTOR_LABEL_PREFIX + port.getKey();
                editingBodyValue = false;
                syncInspector();
                inspectorValue.setFocused(true);
                setFocused(inspectorValue);
                return true;
            }
            rowY += rowHeight;
        }
        return false;
    }

    // Handle the inspector click
    private boolean handleInspectorClick(double mouseX, double mouseY) {
        AdvancedGraphDocument.Node node = selectedNode();
        InspectorSections sections = inspectorSections(node);
        if (inInspectorHeader(mouseY, sections.optionsHeaderTop())) {
            inspectorOptionsCollapsed = !inspectorOptionsCollapsed;
            inspectorOptionsScroll = 0;
            saveUiPreferences();
            return true;
        }
        if (inInspectorHeader(mouseY, sections.targetsHeaderTop())) {
            inspectorTargetsCollapsed = !inspectorTargetsCollapsed;
            inspectorTargetsScroll = 0;
            saveUiPreferences();
            return true;
        }
        if (inInspectorHeader(mouseY, sections.variablesHeaderTop())) {
            inspectorVariablesCollapsed = !inspectorVariablesCollapsed;
            saveUiPreferences();
            return true;
        }
        if (inInspectorDivider(mouseY, sections.optionsDividerTop())) {
            draggingInspectorDivider = InspectorDivider.OPTIONS;
            return true;
        }
        if (inInspectorDivider(mouseY, sections.targetsDividerTop())) {
            draggingInspectorDivider = InspectorDivider.TARGETS;
            return true;
        }
        if (clickVarBrowser(mouseX, mouseY, sections)) return true;

        CompoundTag group = selectedGroup();
        if (group != null) {
            int[] colors = {0xFF5D9FE3, 0xFFE45B67, 0xFF59C58B, 0xFFE09B53, 0xFFB987E8, 0xFF44B8B0, 0xFF9099A5};
            for (int i = 0; i < colors.length; i++) {
                int cx = graphRight() + 10 + (i % 4) * 42;
                int cy = 168 + (i / 4) * 24;
                if (mouseX >= cx && mouseX < cx + 32 && mouseY >= cy && mouseY < cy + 16) {
                    checkpoint();
                    group.putInt("Color", colors[i]);
                    return true;
                }
            }
            return false;
        }
        if (node == null) return false;
        if (isImageReference(node) && imageReferenceUploadButtonBounds().contains(mouseX, mouseY)) {
            embedImageReference(node);
            return true;
        }
        if (isAdvancedHudNode(node) && hudBtnBounds().contains((int) mouseX, (int) mouseY)) {
            openHud(node);
            return true;
        }
        // -----------------------------------------------------NODE OPTIONS------------------------------------------------------

        int optionsTop = sections.optionsTop();
        int optionsBottom = sections.optionsBottom();
        int y = optionsTop;
        y -= inspectorOptionsScroll;
        boolean inOptions = !inspectorOptionsCollapsed && mouseY >= optionsTop && mouseY < optionsBottom;
        if (inOptions && mouseY >= y - 3 && mouseY < y + 13) {
            checkpoint();
            selectedInputPort = NODE_ALIAS_PROPERTY;
            syncInspector();
            return true;
        }
        y += 17;
        if (hasSwitchTypeControl(node)) {
            if (inOptions && mouseY >= y - 3 && mouseY < y + 13) {
                selectedInputPort = AdvancedGraphCatalog.SWITCH_TYPE_TAG;
                openPropertyDropdown(node, AdvancedGraphCatalog.SWITCH_TYPE_TAG,
                        graphRight() + 7, mouseY + 8, RIGHT_WIDTH - 14, SWITCH_TYPE_OPTIONS);
                syncInspector();
                return true;
            }
            y += 17;
        }
        if (hasPropertyControl(node)) {
            if (inOptions && mouseY >= y - 3 && mouseY < y + 13) {
                if ("mouse_input".equals(node.type())) {
                    selectedInputPort = "MouseInput";
                    openPropertyDropdown(node, "MouseInput", graphRight() + 7, mouseY + 8,
                            RIGHT_WIDTH - 14, MOUSE_INPUT_OPTIONS);
                    syncInspector();
                    return true;
                }
                if ("portable_tracker".equals(node.type())) {
                    selectedInputPort = "GogglesPair";
                    openPropertyDropdown(node, "GogglesPair", graphRight() + 7, mouseY + 8,
                            RIGHT_WIDTH - 14, gogglesTrackerPairIds());
                    syncInspector();
                    return true;
                }
                if ("PulseBehavior".equals(editableProperty(node))) {
                    selectedInputPort = "PulseBehavior";
                    openPropertyDropdown(node, "PulseBehavior", graphRight() + 7, mouseY + 8,
                            RIGHT_WIDTH - 14, PULSE_BEHAVIOR_OPTIONS);
                    syncInspector();
                    return true;
                }
                if (isAccDisplayWidgetType(node.type())) {
                    selectedInputPort = "WidgetType";
                    openPropertyDropdown(node, "WidgetType", graphRight() + 7, mouseY + 8,
                            RIGHT_WIDTH - 14, ACC_DISPLAY_WIDGET_TYPES);
                    syncInspector();
                    return true;
                }
                if ("acc_display_crn".equals(node.type())) {
                    selectedInputPort = "DisplayMode";
                    openPropertyDropdown(node, "DisplayMode", graphRight() + 7, mouseY + 8,
                            RIGHT_WIDTH - 14, ACC_DISPLAY_CRN_MODES);
                    syncInspector();
                    return true;
                }
                if ("event_variable_change".equals(node.type())) {
                    selectedInputPort = "Variable";
                    openPropertyDropdown(node, "Variable", graphRight() + 7, mouseY + 8,
                            RIGHT_WIDTH - 14, variableSelectorOptions(node));
                    syncInspector();
                    return true;
                }
                applyPropertyControl(node);
                syncInspector();
                return true;
            }
            y += 17;
        }
        for (var port : AdvancedGraphCatalog.inputs(node).entrySet()) {
            if ("exec".equals(port.getValue())
                    || ("curve".equals(node.type()) && "value".equals(port.getKey()))) continue;
            int rowHeight = "frequency".equals(port.getValue()) ? 22 : 17;
            if (isInputConnected(node, port.getKey())) {
                y += rowHeight;
                continue;
            }
            if (inOptions && mouseY >= y - 3 && mouseY < y + rowHeight - 4) {
                selectedInputPort = port.getKey();
                if ("number".equals(port.getValue())) {
                    if (inspectorSliderTrack(node, port.getKey()).containsX(mouseX, 6)) {
                        startSliderDrag(node, port.getKey(), true, mouseX);
                    }
                }
                else {
                    int controlInset = inspectorInlineMapPortIndent(node, port.getKey(), false);
                    applyInputControl(node, port.getKey(), port.getValue(), mouseX, mouseY,
                            graphRight() + 7 + controlInset, RIGHT_WIDTH - 14 - controlInset);
                }
                syncInspector();
                return true;
            }
            y += rowHeight;
        }
        if (isConstructorNode(node) && inOptions && mouseY >= y - 3 && mouseY < y + 13) {
            addConstructorInput(node);
            return true;
        }
        if (isFunctionInterfaceNode(node) && inOptions
                && mouseY >= y - 3 && mouseY < y + 13) {
            openFunctionPortDropdown(node, graphRight() + 7, mouseY + 8, RIGHT_WIDTH - 14);
            return true;
        }
        // -----------------------------------------------------BINDINGS / TARGETS------------------------------------------------

        y = sections.targetsTop() + 8 - inspectorTargetsScroll;
        int targetsTop = sections.targetsTop();
        int targetsBottom = sections.targetsBottom();
        boolean inTargets = !inspectorTargetsCollapsed && mouseY >= targetsTop && mouseY < targetsBottom;
        if (usesBinding(node)) {
            y += 15;
            for (AdvancedContraptionControllerMenu.GraphBindingOption option : bindingOptions(node)) {
                if (inTargets && mouseY >= y - 2 && mouseY < y + 12) {
                    checkpoint();
                    node.data().putString("BindingId", option.id());
                    node.data().putString("BindingLabel", option.label());
                    node.data().remove("Channel");
                    syncInspector();
                    return true;
                }
                y += 14;
                if (y > targetsBottom) break;
            }
        }
        if (usesTarget(node)) {
            y += 20;
            for (ControllerDiscoveryNode target : graphTargetOptions(node)) {
                boolean selected = target.nodeId().equals(node.data().getString("Target"));
                if (inTargets && mouseY >= y - 2 && mouseY < y + 12) {
                    checkpoint();
                    node.data().putString("Target", target.nodeId());
                    node.data().putString("TargetLabel", target.label().isBlank() ? target.nodeId() : target.label());
                    node.data().put("TargetData", target.toTag());
                    if (!selected) {
                        node.data().remove(AeroworksControllerCompat.GRAPH_SECTION_ID_KEY);
                        node.data().remove(AeroworksControllerCompat.GRAPH_SECTION_LABEL_KEY);
                    }
                    configureDataPorts(node, target);
                    syncInspector();
                    return true;
                }
                y += 14;
                if (selected) {
                    for (AeroworksControllerCompat.ConsoleSection section :
                            aeroworksSectionsForTarget(node, target)) {
                        if (inTargets && mouseY >= y - 2 && mouseY < y + 12) {
                            checkpoint();
                            node.data().putString(AeroworksControllerCompat.GRAPH_SECTION_ID_KEY, section.id());
                            node.data().putString(AeroworksControllerCompat.GRAPH_SECTION_LABEL_KEY, section.label());
                            configureDataPorts(node, target);
                            syncInspector();
                            return true;
                        }
                        y += 14;
                    }
                }
                if (y > targetsBottom) break;
            }
            List<ControllerDiscoveryNode> scmTargets = graphScmTargetOptions(node);
            if (!scmTargets.isEmpty()) {
                y += 20;
                for (ControllerDiscoveryNode target : scmTargets) {
                    if (inTargets && mouseY >= y - 2 && mouseY < y + 12) {
                        checkpoint();
                        node.data().putString("Target", target.nodeId());
                        node.data().putString("TargetLabel",
                                target.label().isBlank() ? target.nodeId() : target.label());
                        node.data().put("TargetData", target.toTag());
                        node.data().remove(AeroworksControllerCompat.GRAPH_SECTION_ID_KEY);
                        node.data().remove(AeroworksControllerCompat.GRAPH_SECTION_LABEL_KEY);
                        configureDataPorts(node, target);
                        syncInspector();
                        return true;
                    }
                    y += 14;
                    if (y > targetsBottom) break;
                }
            }
        }
        if ("acc_display_external".equals(node.type())) {
            y += 20;
            for (ControllerDiscoveryNode src : graphDisplaySourceOptions()) {
                if (inTargets && mouseY >= y - 2 && mouseY < y + 12) {
                    checkpoint();
                    node.data().putString("Source", src.nodeId());
                    node.data().putString("SourceLabel",
                            src.label().isBlank() ? src.nodeId() : src.label());
                    node.data().put("SourceData", src.toTag());
                    syncInspector();
                    return true;
                }
                y += 14;
                if (y > targetsBottom) break;
            }
        }
        return false;
    }

    // Handle the var browser click
    private boolean clickVarBrowser(double mouseX, double mouseY, InspectorSections sections) {
        if (inspectorVariablesCollapsed || mouseY < sections.variablesTop()
                || mouseY >= sections.variablesBottom()) return false;
        int y = sections.variablesTop() + 3;
        for (String variable : draft.variables().keySet()) {
            if (mouseY >= y && mouseY < y + VARIABLE_BROWSER_ROW_HEIGHT) {
                if (inside(mouseX, mouseY, variableGetButtonX(), y + 1,
                        VARIABLE_BROWSER_BUTTON_WIDTH, 16)) {
                    addVariableAccessNode(variable, false);
                    return true;
                }
                if (inside(mouseX, mouseY, variableSetButtonX(), y + 1,
                        VARIABLE_BROWSER_BUTTON_WIDTH, 16)) {
                    addVariableAccessNode(variable, true);
                    return true;
                }
                return true;
            }
            y += VARIABLE_BROWSER_ROW_HEIGHT;
        }
        return false;
    }

    // Handle the context menu click
    private boolean handleContextMenuClick(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            contextMenu = null;
            return true;
        }
        int idx = (int) ((mouseY - contextMenu.y() - 5) / 18);
        List<String> actions = filteredContextItems();
        if (mouseX >= contextMenu.x() && mouseX <= contextMenu.x() + 158 && idx >= 0 && idx < actions.size()) {
            runContextMenuAction(actions.get(idx));
        }
        contextMenu = null;
        return true;
    }

    // Get the filtered context items
    private List<String> filteredContextItems() {
        if (contextMenu == null || contextMenu.query().isBlank()) return contextMenu == null ? List.of() : contextMenu.items();
        String query = contextMenu.query().toLowerCase(Locale.ROOT);
        return contextMenu.items().stream()
                .filter(item -> item.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    // Run the context menu action
    private void runContextMenuAction(String action) {
        if (contextMenu != null && contextMenu.port() != null) {
            runPortContextMenuAction(contextMenu.port(), action);
            return;
        }
        switch (action) {
            case "Duplicate" -> duplicateSelected();
            case "Delete" -> deleteSelected();
            case "Create Comment Group" -> createCommentGroup();
            case "Disconnect Wires" -> disconnectSelected();
            case "Copy Node ID" -> copySelectedNodeId();
            case "Delete Comment Group" -> deleteSelectedGroup();
            case "Convert to Function" -> selectionToFunction();
            case "Collapse Input to MAP" -> toggleSelectedPortMap(false);
            case "Collapse Output to MAP" -> toggleSelectedPortMap(true);
        }
    }

    // Toggle the structured MAP view for the selected node side without deleting its ports or wires.
    private void toggleSelectedPortMap(boolean output) {
        if (selectedNodes.isEmpty()) {
            return;
        }
        String key = output ? AdvancedGraphCatalog.COLLAPSE_OUTPUTS_TO_MAP_TAG
                : AdvancedGraphCatalog.COLLAPSE_INPUTS_TO_MAP_TAG;
        boolean next = selectedNodes.stream().map(this::findNode).filter(Objects::nonNull)
                .anyMatch(node -> !node.data().getBoolean(key));
        checkpoint();
        for (String id : selectedNodes) {
            AdvancedGraphDocument.Node node = findNode(id);
            if (node == null) {
                continue;
            }
            if (next) {
                node.data().putBoolean(key, true);
            } else {
                node.data().remove(key);
            }
        }
        showGraphToast((next ? "Collapsed " : "Expanded ")
                + (output ? "outputs" : "inputs") + " as MAP", GraphActionToastSeverity.SUCCESS);
    }

    // Copy the selected node ID
    private void copySelectedNodeId() {
        AdvancedGraphDocument.Node node = selectedNode();
        if (node == null) return;
        minecraft.keyboardHandler.setClipboard(node.id());
        showGraphToast("Node ID copied", GraphActionToastSeverity.SUCCESS);
    }

    // Run the port context menu action
    private void runPortContextMenuAction(PortContext ctx, String action) {
        AdvancedGraphDocument.Node node = findNode(ctx.nodeId());
        if (node == null) {
            return;
        }
        if (action.startsWith("Persistent:")) {
            checkpoint();
            boolean persistent = !AdvancedGraphPortState.isPersistent(
                    node, ctx.port(), ctx.output());
            AdvancedGraphPortState.setPersistent(
                    node, ctx.port(), ctx.output(), persistent,
                    currentPortValue(node, ctx.port(), ctx.output()));
            clearGraphRenderCache();
            syncInspector();
            return;
        }
        if ("Breakout".equals(action) && "map".equals(ctx.output()
                ? AdvancedGraphCatalog.outputs(node).get(ctx.port())
                : AdvancedGraphCatalog.inputs(node).get(ctx.port()))) {
            if (ctx.output()) {
                inlineBreakoutMapOutput(node, ctx.port());
            } else {
                inlineBreakoutMapInput(node, ctx.port());
            }
            return;
        }
        if ("Collapse".equals(action) && canCollapseInlineMap(node, ctx.port(), ctx.output())) {
            collapseInlineMap(node, ctx.port(), ctx.output());
            return;
        }
        if ("Promote to Variable".equals(action)) {
            promotePortToVariable(node, ctx.port(), ctx.output());
            return;
        }
        if ("Remove Port".equals(action)) {
            removeDynamicPort(node, ctx.port(), ctx.output());
            return;
        }
        if (action.startsWith("Type: ")
                && switchDataCaseInput(node, ctx.port(), ctx.output())) {
            checkpoint();
            String label = action.substring("Type: ".length()).replace(" *", "");
            String type = AdvancedGraphPortState.normalizeSwitchDataType(label);
            AdvancedGraphPortState.setSwitchCaseType(node, ctx.port(), type);
            activeEdges().removeIf(edge -> edge.toNode().equals(node.id())
                    && edge.toPort().equals(ctx.port())
                    && !AdvancedGraphCatalog.compatible(
                    graphOutputType(findNode(edge.fromNode()), edge.fromPort()), type));
            selectedInputPort = ctx.port();
            synchronizeComparePorts();
            clearGraphRenderCache();
            syncInspector();
        }
    }

    // Expose the current keys of a MAP output as direct ports on the same node.
    private void inlineBreakoutMapOutput(AdvancedGraphDocument.Node node, String sourcePort) {
        CompoundTag entries = mapBreakoutEntries(node, sourcePort, true);
        if (entries.isEmpty()) {
            showGraphToast("MAP has no readable entries to break out", GraphActionToastSeverity.WARNING);
            return;
        }
        checkpoint();
        CompoundTag mappings = node.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG).copy();
        CompoundTag dynamicOutputs = node.data().getCompound("DynamicOutputs").copy();
        for (String key : entries.getAllKeys()) {
            String inlinePort = inlineMapPortName(node, sourcePort, key, true, mappings);
            CompoundTag mapping = new CompoundTag();
            mapping.putString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG, sourcePort);
            mapping.putString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG, key);
            mappings.put(inlinePort, mapping);
            dynamicOutputs.putString(inlinePort, entries.getString(key));
        }
        node.data().put(AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG, mappings);
        node.data().put("DynamicOutputs", dynamicOutputs);
        clearGraphRenderCache();
        syncInspector();
        showGraphToast("MAP fields added to node outputs", GraphActionToastSeverity.SUCCESS);
    }

    // Expose the current keys of a MAP input as editable direct inputs on the same node.
    private void inlineBreakoutMapInput(AdvancedGraphDocument.Node node, String sourcePort) {
        CompoundTag entries = mapBreakoutEntries(node, sourcePort, false);
        if (entries.isEmpty()) {
            showGraphToast("MAP has no readable entries to break out", GraphActionToastSeverity.WARNING);
            return;
        }
        checkpoint();
        CompoundTag mappings = node.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG).copy();
        CompoundTag dynamicInputs = node.data().getCompound("DynamicInputs").copy();
        CompoundTag inputOptions = node.data().getCompound("InputOptions").copy();
        for (String key : entries.getAllKeys()) {
            String inlinePort = inlineMapPortName(node, sourcePort, key, false, mappings);
            CompoundTag mapping = new CompoundTag();
            mapping.putString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG, sourcePort);
            mapping.putString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG, key);
            mappings.put(inlinePort, mapping);
            dynamicInputs.putString(inlinePort, entries.getString(key));
            if (inputOptions.contains(key, Tag.TAG_LIST)) {
                inputOptions.put(inlinePort, inputOptions.get(key).copy());
            }
            AdvancedGraphDocument.Value initialValue = GraphRuntime.structuredValue(
                    currentPortValue(node, sourcePort, false), key);
            ListTag options = inputOptions.getList(key, Tag.TAG_STRING);
            if ("string".equals(entries.getString(key)) && !options.isEmpty()
                    && options.stream().map(Tag::getAsString)
                    .noneMatch(initialValue.asString()::equals)) {
                initialValue = AdvancedGraphDocument.Value.string(options.getString(0));
            }
            putInputDefault(node, inlinePort, entries.getString(key), initialValue);
        }
        node.data().put(AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG, mappings);
        node.data().put("DynamicInputs", dynamicInputs);
        if (inputOptions.isEmpty()) node.data().remove("InputOptions");
        else node.data().put("InputOptions", inputOptions);
        clearGraphRenderCache();
        syncInspector();
        showGraphToast("MAP fields added to node inputs", GraphActionToastSeverity.SUCCESS);
    }

    // Get typed MAP breakout entries from a generated data-port schema or the current MAP value
    private CompoundTag mapBreakoutEntries(AdvancedGraphDocument.Node node, String sourcePort, boolean output) {
        CompoundTag schema = AdvancedContraptionControllerBlockEntity.dataPortGroup(node, sourcePort);
        return schema.isEmpty() ? GraphRuntime.splitListOutputsFor(
                currentPortValue(node, sourcePort, output)) : schema;
    }

    // Collapse an unwired MAP breakout back into its parent port
    private void collapseInlineMap(AdvancedGraphDocument.Node node, String sourcePort, boolean output) {
        if (!canCollapseInlineMap(node, sourcePort, output)) {
            showGraphToast("Disconnect MAP fields before collapsing", GraphActionToastSeverity.WARNING);
            return;
        }
        AdvancedGraphDocument.Value collapsed = output
                ? null : currentPortValue(node, sourcePort, false);
        String mappingsTag = output ? AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG
                : AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG;
        CompoundTag mappings = node.data().getCompound(mappingsTag).copy();
        List<String> inlinePorts = mappings.getAllKeys().stream()
                .filter(port -> sourcePort.equals(mappings.getCompound(port)
                        .getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG)))
                .toList();
        checkpoint();
        if (!output) {
            putInputDefault(node, sourcePort, "map", collapsed);
        }
        for (String inlinePort : inlinePorts) {
            AdvancedGraphPortState.setPersistent(node, inlinePort, output, false, null);
            mappings.remove(inlinePort);
        }
        if (mappings.isEmpty()) node.data().remove(mappingsTag);
        else node.data().put(mappingsTag, mappings);
        if (output) {
            removeInlineMapPortEntries(node, inlinePorts, "DynamicOutputs", "OutputLabels",
                    "OutputOptions", AdvancedGraphPortState.OUTPUT_DEFAULTS_TAG);
        } else {
            removeInlineMapPortEntries(node, inlinePorts, "DynamicInputs", "Defaults",
                    "PrefilledInputs", "ForceWriteInputs", "InputOptions",
                    AdvancedGraphCatalog.INPUT_LABELS_TAG);
        }
        clearGraphRenderCache();
        syncInspector();
        showGraphToast("MAP fields collapsed", GraphActionToastSeverity.SUCCESS);
    }

    // Check whether every child of one MAP breakout is unwired
    private boolean canCollapseInlineMap(AdvancedGraphDocument.Node node, String sourcePort, boolean output) {
        if (node == null || sourcePort == null || sourcePort.isBlank()) return false;
        CompoundTag mappings = node.data().getCompound(output
                ? AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG : AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG);
        boolean found = false;
        for (String inlinePort : mappings.getAllKeys()) {
            if (!sourcePort.equals(mappings.getCompound(inlinePort)
                    .getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG))) {
                continue;
            }
            found = true;
            boolean wired = activeEdges().stream().anyMatch(edge -> output
                    ? node.id().equals(edge.fromNode()) && inlinePort.equals(edge.fromPort())
                    : node.id().equals(edge.toNode()) && inlinePort.equals(edge.toPort()));
            if (wired) return false;
        }
        return found;
    }

    // Remove stored graph state for collapsed inline MAP fields
    private static void removeInlineMapPortEntries(AdvancedGraphDocument.Node node,
                                                   List<String> ports, String... dataKeys) {
        for (String dataKey : dataKeys) {
            CompoundTag entries = node.data().getCompound(dataKey);
            ports.forEach(entries::remove);
            if (entries.isEmpty()) node.data().remove(dataKey);
            else node.data().put(dataKey, entries);
        }
    }

    private String inlineMapPortName(AdvancedGraphDocument.Node node, String sourcePort, String key,
                                     boolean output, CompoundTag mappings) {
        String base = sourcePort + "." + key;
        String name = base;
        int suffix = 2;
        Map<String, String> ports = output ? AdvancedGraphCatalog.outputs(node) : AdvancedGraphCatalog.inputs(node);
        while (ports.containsKey(name) && !inlineMapPortMatches(mappings.getCompound(name), sourcePort, key)) {
            name = base + "_" + suffix++;
        }
        return name;
    }

    private static boolean inlineMapPortMatches(CompoundTag mapping, String sourcePort, String key) {
        return sourcePort.equals(mapping.getString(AdvancedGraphCatalog.INLINE_MAP_SOURCE_TAG))
                && key.equals(mapping.getString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG));
    }

    // Get the current port value
    private AdvancedGraphDocument.Value currentPortValue(
            AdvancedGraphDocument.Node node, String port, boolean output
    ) {
        return output ? previewDraftOutput(node, port) : previewDraftInput(node, port);
    }

    // Get the graph output type
    private static String graphOutputType(
            AdvancedGraphDocument.Node node, String port
    ) {
        return node == null ? null : AdvancedGraphCatalog.outputs(node).get(port);
    }

    // Check if the port is a switch data case input
    private static boolean switchDataCaseInput(
            AdvancedGraphDocument.Node node, String port, boolean output
    ) {
        return !output && node != null && port != null
                && "switch".equals(node.type())
                && AdvancedGraphCatalog.switchDataMode(node)
                && ("default".equals(port) || port.startsWith("case_"));
    }

    // Check if this can remove dynamic port
    private boolean canRemoveDynamicPort(
            AdvancedGraphDocument.Node node, String port, boolean output
    ) {
        if (node == null || port == null) return false;
        if (!output && constructorValuePort(node, port)) {
            return AdvancedGraphCatalog.inputs(node).size() > 1;
        }
        if (!output && isHudNode(node)) {
            return AdvancedGraphCatalog.inputs(node).containsKey(port)
                    && !isHudRequiredPort(port);
        }
        if (isFunctionInputNode(node) && output) {
            return node.data().getCompound("DynamicOutputs").contains(port);
        }
        if (isFunctionOutputNode(node) && !output) {
            return node.data().getCompound("DynamicInputs").contains(port);
        }
        if (isExecutionSplitterNode(node) && executionOutputCount(node) > MIN_EXECUTION_OUTPUTS) {
            return AdvancedGraphCatalog.switchDataMode(node)
                    ? !output && port.startsWith("case_")
                    : output && port.startsWith(
                    "switch".equals(node.type()) ? "case_" : "exec_");
        }
        return isExecutionCombinerNode(node) && !output
                && executionInputCount(node) > MIN_EXECUTION_INPUTS
                && port.startsWith("exec_");
    }

    // Remove the dynamic port
    private void removeDynamicPort(
            AdvancedGraphDocument.Node node, String port, boolean output
    ) {
        if (!canRemoveDynamicPort(node, port, output)) return;
        if (!output && constructorValuePort(node, port)) {
            removeConstructorInput(node, port);
            return;
        }
        if (!output && isHudNode(node)) {
            removeHudField(node, port);
            return;
        }
        if (isFunctionInputNode(node) && output
                || isFunctionOutputNode(node) && !output) {
            checkpoint();
            String key = output ? "DynamicOutputs" : "DynamicInputs";
            CompoundTag ports = node.data().getCompound(key);
            ports.remove(port);
            if (ports.isEmpty()) node.data().remove(key);
            else node.data().put(key, ports);
            activeEdges().removeIf(edge -> output
                    ? edge.fromNode().equals(node.id()) && edge.fromPort().equals(port)
                    : edge.toNode().equals(node.id()) && edge.toPort().equals(port));
            AdvancedGraphFunctions.synchronizeCalls(draft);
            clearGraphRenderCache();
            syncInspector();
            return;
        }
        if (isExecutionSplitterNode(node)) {
            checkpoint();
            compactExecSplitPort(node, port);
            clearGraphRenderCache();
            syncInspector();
            return;
        }
        if (isExecutionCombinerNode(node)) {
            checkpoint();
            compactExecMergePort(node, port);
            clearGraphRenderCache();
            syncInspector();
        }
    }

    // Compact the exec split port
    private void compactExecSplitPort(AdvancedGraphDocument.Node node, String removedPort) {
        int count = executionOutputCount(node);
        int removedIndex = executionPortIndex(node.type(), removedPort);
        if (removedIndex < 0 || removedIndex >= count) return;
        boolean dataSwitch = AdvancedGraphCatalog.switchDataMode(node);
        activeEdges().removeIf(edge -> dataSwitch
                ? edge.toNode().equals(node.id()) && edge.toPort().equals(removedPort)
                : edge.fromNode().equals(node.id()) && edge.fromPort().equals(removedPort));
        for (int idx = removedIndex + 1; idx < count; idx++) {
            String from = executionOutputPort(node.type(), idx);
            String to = executionOutputPort(node.type(), idx - 1);
            remapPortEdges(node.id(), from, to, !dataSwitch);
            if (dataSwitch) {
                CompoundTag types = node.data().getCompound(
                        AdvancedGraphPortState.SWITCH_CASE_TYPES_TAG);
                CompoundTag defaults = node.data().getCompound("Defaults");
                types.putString(to, AdvancedGraphPortState.switchCaseType(node, from));
                if (defaults.contains(from)) defaults.put(to, defaults.get(from).copy());
                node.data().put(AdvancedGraphPortState.SWITCH_CASE_TYPES_TAG, types);
                node.data().put("Defaults", defaults);
            }
        }
        setExecutionOutputCount(node, count - 1, true);
    }

    // Compact the exec merge port
    private void compactExecMergePort(AdvancedGraphDocument.Node node, String removedPort) {
        int count = executionInputCount(node);
        int removedIndex = executionPortIndex(node.type(), removedPort);
        if (removedIndex < 0 || removedIndex >= count) return;
        activeEdges().removeIf(edge -> edge.toNode().equals(node.id())
                && edge.toPort().equals(removedPort));
        for (int idx = removedIndex + 1; idx < count; idx++) {
            remapPortEdges(node.id(), "exec_" + (idx + 1), "exec_" + idx, false);
        }
        setExecutionInputCount(node, count - 1, true);
    }

    // Get the execution port index
    private static int executionPortIndex(String nodeType, String port) {
        String prefix = "switch".equals(nodeType) ? "case_" : "exec_";
        if (port == null || !port.startsWith(prefix)) return -1;
        try {
            int parsed = Integer.parseInt(port.substring(prefix.length()));
            return "switch".equals(nodeType) ? parsed : parsed - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // Remap the port edges
    private void remapPortEdges(String nodeId, String fromPort, String toPort, boolean output) {
        for (int idx = 0; idx < activeEdges().size(); idx++) {
            AdvancedGraphDocument.Edge edge = activeEdges().get(idx);
            if (output && edge.fromNode().equals(nodeId) && edge.fromPort().equals(fromPort)) {
                activeEdges().set(idx, new AdvancedGraphDocument.Edge(
                        edge.id(), edge.fromNode(), toPort, edge.toNode(), edge.toPort()));
            } else if (!output && edge.toNode().equals(nodeId) && edge.toPort().equals(fromPort)) {
                activeEdges().set(idx, new AdvancedGraphDocument.Edge(
                        edge.id(), edge.fromNode(), edge.fromPort(), edge.toNode(), toPort));
            }
        }
    }

    // Promote the port to variable
    private void promotePortToVariable(
            AdvancedGraphDocument.Node sourceNode, String sourcePort, boolean output
    ) {
        String graphType = (output
                ? AdvancedGraphCatalog.outputs(sourceNode)
                : AdvancedGraphCatalog.inputs(sourceNode)).get(sourcePort);
        if (graphType == null || "exec".equals(graphType)) return;
        double x = output
                ? sourceNode.x() + nodeWidth(sourceNode) + 80.0D
                : sourceNode.x() - NODE_WIDTH - 80.0D;
        AdvancedGraphDocument.Node setter = addNode(
                "variable_set", x, sourceNode.y(), true);
        if (setter == null) return;
        String selectedType = switch (graphType) {
            case "number" -> "float";
            case "boolean", "string", "direction", "frequency", "target", "list", "map" -> graphType;
            default -> "string";
        };
        configureVariableType(setter, selectedType);

        AdvancedGraphDocument.Edge inbound = output ? null : activeEdges().stream()
                .filter(edge -> edge.toNode().equals(sourceNode.id())
                        && edge.toPort().equals(sourcePort))
                .findFirst().orElse(null);
        if (inbound != null) {
            activeEdges().remove(inbound);
            addEdgeWithoutCheckpoint(inbound.fromNode(), inbound.fromPort(), setter.id(), "value");
        } else if (output) {
            addEdgeWithoutCheckpoint(sourceNode.id(), sourcePort, setter.id(), "value");
        }
        if (!output) {
            addEdgeWithoutCheckpoint(setter.id(), "value", sourceNode.id(), sourcePort);
        }
        synchronizeComparePorts();
        clearGraphRenderCache();
        syncInspector();
    }

    // Add the edge without checkpoint
    private void addEdgeWithoutCheckpoint(
            String fromNode, String fromPort, String toNode, String toPort
    ) {
        if (activeEdges().size() >= AdvancedGraphDocument.MAX_EDGES) return;
        activeEdges().removeIf(edge -> edge.toNode().equals(toNode)
                && edge.toPort().equals(toPort));
        activeEdges().add(new AdvancedGraphDocument.Edge(
                UUID.randomUUID().toString(), fromNode, fromPort, toNode, toPort));
    }

    // Get the output selection
    private static String outputSelection(String port) {
        return "out:" + port;
    }

    // Get the selected output port
    private static String selectedOutputPort(
            AdvancedGraphDocument.Node node, String selection
    ) {
        if (node == null || selection == null || !selection.startsWith("out:")) {
            return null;
        }
        String port = selection.substring("out:".length());
        return null;
    }

    // Handle the mini browser click
    private boolean handleMiniBrowserClick(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            closeMiniBrowser();
            clearConnection();
            return true;
        }
        int width = MINI_BROWSER_WIDTH;
        int height = MINI_BROWSER_HEIGHT;
        int x = Mth.clamp(miniBrowser.x(), graphLeft(), graphRight() - width);
        int y = Mth.clamp(miniBrowser.y(), graphTop(), graphBottom() - height);
        posMiniSearch();
        if (miniBrowserSearch != null && miniBrowserSearch.mouseClicked(mouseX, mouseY, btn)) {
            miniBrowserSearch.setFocused(true);
            setFocused(miniBrowserSearch);
            return true;
        }
        if (showMiniCreateVar()
                && inside(mouseX, mouseY, x + 6, y + MINI_BROWSER_ROWS_TOP, width - 12, 17)) {
            createVarFromMini();
            closeMiniBrowser();
            clearConnection();
            return true;
        }
        int rowsTop = miniBrowserRowsTop();
        int rowY = y + rowsTop - miniBrowserScroll;
        if (mouseX >= x && mouseX < x + width
                && mouseY >= y + rowsTop - 4 && mouseY < y + height) {
            for (BrowserEntry entry : miniBrowserEntries()) {
                int rowHeight = entry.category() ? 20 : 16;
                if (mouseY >= rowY - 2 && mouseY < rowY + rowHeight - 2) {
                    if (entry.category()) {
                        toggle(collapsedMiniCategories, entry.id());
                        return true;
                    }
                    AdvancedGraphDocument.Node node = addNode(entry.id(), graphX(miniBrowser.x()), graphY(miniBrowser.y()));
                    if (node != null && miniBrowser.wireNode() != null) autoConnectMiniNode(node);
                    closeMiniBrowser();
                    clearConnection();
                    return true;
                }
                rowY += rowHeight;
            }
        }
        closeMiniBrowser();
        clearConnection();
        return true;
    }

    // Create the var from mini
    private void createVarFromMini() {
        String variable = miniBrowserVariableName();
        if (!validVariableName(variable)) {
            return;
        }
        AdvancedGraphDocument.Node node = addNode("variable_set",
                graphX(miniBrowser.x()), graphY(miniBrowser.y()), false);
        if (node == null) {
            return;
        }
        node.data().putString("Variable", variable);
        node.data().putString("RegisteredVariable", variable);
        node.data().putBoolean("VariableDefinition", true);
        draft.variables().putIfAbsent(variable, AdvancedGraphDocument.Value.bool(false));
        configureVariableType(node, "boolean");
        synchronizeVariableNodes();
        syncInspector();
    }

    // Handle the auto connect mini node
    private void autoConnectMiniNode(AdvancedGraphDocument.Node newNode) {
        AdvancedGraphDocument.Node original = findNode(miniBrowser.wireNode());
        if (original == null) return;
        if (isReroute(newNode)) {
            if (miniBrowser.wireOutput()) {
                addEdge(original.id(), miniBrowser.wirePort(), newNode.id(), "value");
            } else {
                addEdge(newNode.id(), "value", original.id(), miniBrowser.wirePort());
            }
            return;
        }
        if (miniBrowser.wireOutput()) {
            String sourceType = AdvancedGraphCatalog.outputs(original).get(miniBrowser.wirePort());
            AdvancedGraphCatalog.inputs(newNode).entrySet().stream()
                    .filter(entry -> AdvancedGraphCatalog.compatible(sourceType, newNode, entry.getKey())).findFirst()
                    .ifPresent(entry -> addEdge(original.id(), miniBrowser.wirePort(), newNode.id(), entry.getKey()));
        } else {
            AdvancedGraphCatalog.outputs(newNode).entrySet().stream()
                    .filter(entry -> AdvancedGraphCatalog.compatible(entry.getValue(), original, miniBrowser.wirePort())).findFirst()
                    .ifPresent(entry -> addEdge(newNode.id(), entry.getKey(), original.id(), miniBrowser.wirePort()));
        }
    }

    // Handle the template click
    private boolean handleTemplateClick(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        int w = 320;
        int h = 146;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        if (mouseX >= x + 18 && mouseX <= x + w - 18 && mouseY >= y + 34 && mouseY <= y + 134) {
            int idx = Mth.clamp((int) ((mouseY - y - 34) / 24), 0, GRAPH_TEMPLATES.size() - 1);
            String templateId = GRAPH_TEMPLATES.get(idx).id();
            checkpoint();
            draft = AdvancedGraphTemplates.create(templateId);
            restoreViewport();
            draft.setRevision(savedDraft.revision());
            synchronizeComparePorts();
            clearSelection();
            send("template", templateId);
        }
        templatePicker = false;
        return true;
    }

    // Open the mini browser
    private void openMiniBrowser(int x, int y, String wireNode, String wirePort, boolean wireOutput) {
        List<AdvancedGraphCatalog.Definition> definitions = new ArrayList<>();
        if (wireNode == null) {
            for (AdvancedGraphCatalog.Definition definition : AdvancedGraphCatalog.all()) {
                if (AdvancedGraphFunctions.CALL_TYPE.equals(definition.id())) continue;
                if (isNodeAvailableInEditor(definition.id())) definitions.add(definition);
            }
            draft.functions().stream().map(this::functionDefinition).forEach(definitions::add);
        } else {
            AdvancedGraphDocument.Node node = findNode(wireNode);
            AdvancedGraphCatalog.Definition src = node == null ? null : AdvancedGraphCatalog.get(node.type());
            if (src != null) {
                String type = wireOutput ? AdvancedGraphCatalog.outputs(node).get(wirePort) : AdvancedGraphCatalog.inputs(node).get(wirePort);
                for (AdvancedGraphCatalog.Definition definition : AdvancedGraphCatalog.all()) {
                    if (AdvancedGraphFunctions.CALL_TYPE.equals(definition.id())) continue;
                    if (!isNodeAvailableInEditor(definition.id())) continue;
                    if ("reroute".equals(definition.id())) {
                        definitions.add(definition);
                        continue;
                    }
                    boolean compatible = wireOutput
                            ? definition.inputs().values().stream().anyMatch(target -> AdvancedGraphCatalog.compatible(type, target))
                            : definition.outputs().values().stream().anyMatch(output ->
                            AdvancedGraphCatalog.compatible(output, node, wirePort));
                    if (compatible) definitions.add(definition);
                }
                for (AdvancedGraphDocument.FunctionGraph function : draft.functions()) {
                    AdvancedGraphCatalog.Definition definition = functionDefinition(function);
                    boolean compatible = wireOutput
                            ? definition.inputs().values().stream()
                            .anyMatch(target -> AdvancedGraphCatalog.compatible(type, target))
                            : definition.outputs().values().stream()
                            .anyMatch(output -> AdvancedGraphCatalog.compatible(output, node, wirePort));
                    if (compatible) definitions.add(definition);
                }
            }
        }
        definitions.sort(Comparator.comparing(definition -> browserEntryName(definition.id())));
        miniBrowser = new MiniBrowser(x, y, definitions, wireNode, wirePort, wireOutput);
        miniBrowserScroll = 0;
        collapsedMiniCategories.clear();
        if (miniBrowserSearch != null) {
            miniBrowserSearch.setValue("");
            miniBrowserSearch.setVisible(true);
            posMiniSearch();
            miniBrowserSearch.setFocused(true);
            setFocused(miniBrowserSearch);
        }
    }

    // Get the minimum i browser entries
    private List<BrowserEntry> miniBrowserEntries() {
        List<BrowserEntry> res = new ArrayList<>();
        Map<String, List<AdvancedGraphCatalog.Definition>> categories = new LinkedHashMap<>();
        String query = miniBrowserSearch == null ? "" : miniBrowserSearch.getValue().trim().toLowerCase(Locale.ROOT);
        for (AdvancedGraphCatalog.Definition definition : miniBrowser.definitions()) {
            if (!query.isBlank()
                    && !definition.id().toLowerCase(Locale.ROOT).contains(query)
                    && !browserEntryName(definition.id()).toLowerCase(Locale.ROOT).contains(query)
                    && !AdvancedGraphCatalog.categoryName(definition.category()).toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            categories.computeIfAbsent(definition.category(), ignored -> new ArrayList<>()).add(definition);
        }
        for (String category : CATEGORY_ORDER) {
            List<AdvancedGraphCatalog.Definition> definitions = categories.get(category);
            if (definitions == null || definitions.isEmpty()) continue;
            res.add(new BrowserEntry(category, true));
            if (!query.isBlank() || !collapsedMiniCategories.contains(category)) {
                definitions.stream().sorted(Comparator.comparing(definition -> browserEntryName(definition.id())))
                        .forEach(definition -> res.add(new BrowserEntry(definition.id(), false)));
            }
        }
        return res;
    }

    // Close the mini browser
    private void closeMiniBrowser() {
        miniBrowser = null;
        if (miniBrowserSearch != null) {
            miniBrowserSearch.setVisible(false);
            miniBrowserSearch.setFocused(false);
            if (getFocused() == miniBrowserSearch) setFocused(null);
        }
    }

    // Connect the ports
    private void connectPorts(PortHit hit) {
        AdvancedGraphDocument.Node original = findNode(connectingNode);
        if (original == null) return;
        if (connectingOutput) addEdge(original.id(), connectingPort, hit.node().id(), hit.port());
        else addEdge(hit.node().id(), hit.port(), original.id(), connectingPort);
    }

    // Add the edge
    private void addEdge(String fromNode, String fromPort, String toNode, String toPort) {
        AdvancedGraphDocument.Node from = findNode(fromNode);
        AdvancedGraphDocument.Node to = findNode(toNode);
        if (from == null || to == null) return;
        checkpoint();
        AdvancedGraphPortNormalizer.connect(activeNodes(), activeEdges(),
                UUID.randomUUID().toString(), fromNode, fromPort, toNode, toPort);
        synchronizeComparePorts();
    }

    // Handle the wire click
    private boolean handleWireClick(double mouseX, double mouseY) {
        AdvancedGraphDocument.Edge edge = edgeAt(mouseX, mouseY);
        if (edge == null) {
            lastClickedWire = null;
            return false;
        }
        long now = Util.getMillis();
        if (edge.id().equals(lastClickedWire) && now - lastWireClickTime <= WIRE_DOUBLE_CLICK_MILLIS) {
            insertReroute(edge, mouseX, mouseY);
            lastClickedWire = null;
        } else {
            lastClickedWire = edge.id();
            lastWireClickTime = now;
        }
        return true;
    }

    // Get the edge
    private AdvancedGraphDocument.Edge edgeAt(double mouseX, double mouseY) {
        List<AdvancedGraphDocument.Edge> reverse = new ArrayList<>(activeEdges());
        Collections.reverse(reverse);
        double hitDistance = WIRE_HIT_RADIUS * WIRE_HIT_RADIUS;
        for (AdvancedGraphDocument.Edge edge : reverse) {
            AdvancedGraphDocument.Node from = findNode(edge.fromNode());
            AdvancedGraphDocument.Node to = findNode(edge.toNode());
            if (from == null || to == null) continue;
            PortPosition src = portPosition(from, edge.fromPort(), true);
            PortPosition target = portPosition(to, edge.toPort(), false);
            if (GraphWireGeometry.distanceSquaredToWire(mouseX, mouseY,
                    src.x(), src.y(), target.x(), target.y()) <= hitDistance) {
                return edge;
            }
        }
        return null;
    }

    // Insert the reroute
    private void insertReroute(AdvancedGraphDocument.Edge edge, double mouseX, double mouseY) {
        if (activeNodes().size() >= AdvancedGraphDocument.maxNodes()
                || activeEdges().size() >= AdvancedGraphDocument.MAX_EDGES) return;
        AdvancedGraphDocument.Node from = findNode(edge.fromNode());
        AdvancedGraphDocument.Node to = findNode(edge.toNode());
        if (from == null || to == null) return;
        String type = AdvancedGraphCatalog.outputs(from).get(edge.fromPort());
        if (type == null || "any".equals(type)) {
            type = AdvancedGraphCatalog.inputs(to).getOrDefault(edge.toPort(), "any");
        }
        checkpoint();
        CompoundTag data = AdvancedGraphNodeFactory.createDefaultData(
                "reroute", graphNodeFactoryContext());
        AdvancedGraphDocument.Node reroute = new AdvancedGraphDocument.Node(
                UUID.randomUUID().toString(), "reroute", "",
                graphX(mouseX) - REROUTE_WIDTH * 0.5D,
                graphY(mouseY) - REROUTE_HEIGHT * 0.5D, data);
        configureRerouteType(reroute, type);
        activeNodes().add(reroute);
        activeEdges().removeIf(candidate -> candidate.id().equals(edge.id()));
        activeEdges().add(new AdvancedGraphDocument.Edge(UUID.randomUUID().toString(),
                edge.fromNode(), edge.fromPort(), reroute.id(), "value"));
        activeEdges().add(new AdvancedGraphDocument.Edge(UUID.randomUUID().toString(),
                reroute.id(), "value", edge.toNode(), edge.toPort()));
        selectOnly(reroute.id());
        synchronizeComparePorts();
        syncInspector();
    }

    // Get the inferred reroute type
    private static String inferredRerouteType(AdvancedGraphDocument.Node node, String port, String connectedType) {
        if (!isReroute(node) || !"value".equals(port) || connectedType == null || "any".equals(connectedType)) {
            return null;
        }
        String current = AdvancedGraphCatalog.inputs(node).getOrDefault("value", "any");
        return "any".equals(current) ? connectedType : null;
    }

    // Configure the reroute type
    private static void configureRerouteType(AdvancedGraphDocument.Node node, String type) {
        if (!isReroute(node) || type == null || type.isBlank()) return;
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        inputs.putString("value", type);
        node.data().put("DynamicInputs", inputs);
        CompoundTag outputs = node.data().getCompound("DynamicOutputs");
        outputs.putString("value", type);
        node.data().put("DynamicOutputs", outputs);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                     NODE CONSTRUCTION
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Add the node
    private AdvancedGraphDocument.Node addNode(String type, double graphX, double graphY) {
        return addNode(type, graphX, graphY, true);
    }

    // Add the node
    private AdvancedGraphDocument.Node addNode(String type, double graphX, double graphY,
                                               boolean registerVariable) {
        if (!isNodeAvailableInEditor(type) || activeNodes().size() >= AdvancedGraphDocument.maxNodes()) return null;
        checkpoint();
        AdvancedGraphDocument.FunctionGraph spawnedFunction = type.startsWith("function:")
                ? draft.function(type.substring("function:".length())) : null;
        String nodeType = spawnedFunction == null ? type : AdvancedGraphFunctions.CALL_TYPE;
        CompoundTag data = AdvancedGraphNodeFactory.createDefaultData(
                nodeType, graphNodeFactoryContext());
        AdvancedGraphDocument.Node node = new AdvancedGraphDocument.Node(
                UUID.randomUUID().toString(), nodeType,
                spawnedFunction == null ? "" : spawnedFunction.name(),
                graphX, graphY, data);
        activeNodes().add(node);
        if (spawnedFunction != null) {
            AdvancedGraphFunctions.configureCall(node, spawnedFunction);
        }
        if ("variable_set".equals(nodeType)) {
            node.data().putBoolean("VariableDefinition", registerVariable);
            if (registerVariable) {
                String variable = node.data().getString("Variable");
                node.data().putString("RegisteredVariable", variable);
                draft.variables().putIfAbsent(variable, AdvancedGraphDocument.Value.bool(false));
                synchronizeVariableNodes();
            }
        }
        selectOnly(node.id());
        syncInspector();
        return node;
    }

    // Get the shared graph node factory context
    private AdvancedGraphNodeFactory.Context graphNodeFactoryContext() {
        String pairId = gogglesTrackerPairs().stream().findFirst()
                .map(pair -> pair.id().toString()).orElse("");
        String firstVariable = draft.variables().keySet().stream()
                .findFirst().orElse("variable");
        return new AdvancedGraphNodeFactory.Context(
                pairId, nextVariableName(), firstVariable);
    }

    // Get the next variable name
    private String nextVariableName() {
        Set<String> names = new LinkedHashSet<>(draft.variables().keySet());
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if ("variable_set".equals(node.type())) names.add(node.data().getString("Variable"));
        }
        if (!names.contains("variable")) return "variable";
        int suffix = 2;
        while (names.contains("variable_" + suffix)) suffix++;
        return "variable_" + suffix;
    }

    // Add the variable access node
    private void addVariableAccessNode(String variable, boolean setter) {
        AdvancedGraphDocument.Value current = draft.variables().get(variable);
        if (current == null) return;
        String type = setter ? "variable_set" : "variable_get";
        AdvancedGraphDocument.Node node = addNode(type,
                graphCenterX() - NODE_WIDTH * 0.5D, graphCenterY() - (setter ? 55.0D : 35.0D), false);
        if (node == null) return;
        node.data().putString("Variable", variable);
        if (setter) {
            node.data().putString("RegisteredVariable", variable);
            String selectedType = variableTypeOption(variable, current);
            configureVariableType(node, selectedType);
            putInputDefault(node, "default", variableGraphType(selectedType), variableInputValue(current, selectedType));
        }
        draft.variables().put(variable, current);
        synchronizeVariableNodes();
        syncInspector();
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                      GRAPH TRANSFORMS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Move the selected
    private void moveSelected(double dx, double dy) {
        for (int idx = 0; idx < activeNodes().size(); idx++) {
            AdvancedGraphDocument.Node node = activeNodes().get(idx);
            if (selectedNodes.contains(node.id())) {
                replaceMovedNode(idx, node, dx, dy);
            }
        }
    }

    // Replace the moved node
    private void replaceMovedNode(int idx, AdvancedGraphDocument.Node node, double dx, double dy) {
        AdvancedGraphDocument.Node moved = new AdvancedGraphDocument.Node(
                node.id(), node.type(), node.label(), node.x() + dx, node.y() + dy, node.data());
        activeNodes().set(idx, moved);
        if (graphRenderCacheDocument == draft) {
            graphRenderNodes.put(moved.id(), moved);
        }
    }

    // Select the marquee
    private void selectMarquee(double mouseX, double mouseY) {
        double left = Math.min(marqueeStartX, mouseX);
        double top = Math.min(marqueeStartY, mouseY);
        double right = Math.max(marqueeStartX, mouseX);
        double bottom = Math.max(marqueeStartY, mouseY);
        if (!hasControlDown()) selectedNodes.clear();
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            int x = screenX(node.x());
            int y = screenY(node.y());
            int w = (int) (nodeWidth(node) * zoom);
            int h = (int) (nodeHeight(node) * zoom);
            if (x + w >= left && x <= right && y + h >= top && y <= bottom) selectedNodes.add(node.id());
        }
    }

    // Create the comment group
    private void createCommentGroup() {
        if (selectedNodes.isEmpty()) return;
        Set<String> groupMembers = new LinkedHashSet<>();
        for (String nodeId : selectedNodes) {
            if (commentGroupForNode(nodeId) == null) {
                groupMembers.add(nodeId);
            }
        }
        if (groupMembers.isEmpty()) return;
        checkpoint();
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (String id : groupMembers) {
            AdvancedGraphDocument.Node node = findNode(id);
            if (node == null) continue;
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.x() + nodeWidth(node));
            maxY = Math.max(maxY, node.y() + nodeHeight(node));
        }
        CompoundTag group = new CompoundTag();
        group.putString("Id", UUID.randomUUID().toString());
        group.putString("Title", "Comment");
        group.putDouble("X", minX - 24);
        group.putDouble("Y", minY - 36);
        group.putDouble("Width", maxX - minX + 48);
        group.putDouble("Height", maxY - minY + 60);
        group.putInt("Color", 0xFF5D9FE3);
        AdvancedGraphSelection.setCommentGroupNodeIds(group, groupMembers);
        activeGroups().add(group);
        selectedGroup = group.getString("Id");
        selectedNodes.clear();
        selectedNodes.addAll(groupMembers);
        syncInspector();
    }

    // Handle the selection to function
    private void selectionToFunction() {
        if (selectedNodes.size() < 2) {
            return;
        }
        // -----------------------------------------------------SELECTION-----------------------------------------------------
        List<AdvancedGraphDocument.Node> sourceNodes = activeNodes();
        List<AdvancedGraphDocument.Edge> sourceEdges = activeEdges();
        List<CompoundTag> sourceGroups = activeGroups();
        Set<String> selected = sourceNodes.stream()
                .filter(node -> selectedNodes.contains(node.id()) && !isStandaloneExecSource(node.type()))
                .map(AdvancedGraphDocument.Node::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<AdvancedGraphDocument.Node> movedNodes = sourceNodes.stream()
                .filter(node -> selected.contains(node.id())).toList();
        if (movedNodes.isEmpty()) {
            return;
        }

        CompoundTag sourceGroup = selectedGroup();
        String requestedFunctionName = sourceGroup == null
                ? "" : sourceGroup.getString("Title").trim();
        // ------------------------------------FUNCTION GRAPH------------------------------------
        checkpoint();
        String functionId = UUID.randomUUID().toString();
        String functionName = nextFunctionName(requestedFunctionName);
        AdvancedGraphDocument.FunctionGraph function =
                new AdvancedGraphDocument.FunctionGraph(functionId, functionName);
        function.nodes().addAll(movedNodes);

        double minimumX = movedNodes.stream().mapToDouble(AdvancedGraphDocument.Node::x).min().orElse(0.0D);
        double minimumY = movedNodes.stream().mapToDouble(AdvancedGraphDocument.Node::y).min().orElse(0.0D);
        double maximumX = movedNodes.stream()
                .mapToDouble(node -> node.x() + nodeWidth(node)).max().orElse(minimumX + NODE_WIDTH);
        double maximumY = movedNodes.stream()
                .mapToDouble(node -> node.y() + nodeHeight(node)).max().orElse(minimumY + 80.0D);

        // ------------------------------------FUNCTION PORTS------------------------------------
        CompoundTag inputData = new CompoundTag();
        CompoundTag outputData = new CompoundTag();
        CompoundTag inputPorts = new CompoundTag();
        CompoundTag outputPorts = new CompoundTag();
        inputData.put("DynamicOutputs", inputPorts);
        outputData.put("DynamicInputs", outputPorts);
        AdvancedGraphDocument.Node functionInput = new AdvancedGraphDocument.Node(
                UUID.randomUUID().toString(), AdvancedGraphFunctions.INPUT_TYPE, "Inputs",
                minimumX - 230.0D, minimumY, inputData);
        AdvancedGraphDocument.Node functionOutput = new AdvancedGraphDocument.Node(
                UUID.randomUUID().toString(), AdvancedGraphFunctions.OUTPUT_TYPE, "Outputs",
                maximumX + 80.0D, minimumY, outputData);
        function.nodes().add(functionInput);
        function.nodes().add(functionOutput);

        // ------------------------------------BOUNDARY PORTS------------------------------------
        Set<String> usedInputs = new LinkedHashSet<>();
        Set<String> usedOutputs = new LinkedHashSet<>();
        List<AdvancedGraphDocument.Edge> outerEdges = new ArrayList<>();
        for (AdvancedGraphDocument.Edge edge : new ArrayList<>(sourceEdges)) {
            boolean fromSelected = selected.contains(edge.fromNode());
            boolean toSelected = selected.contains(edge.toNode());
            if (fromSelected && toSelected) {
                function.edges().add(edge);
                continue;
            }
            if (!fromSelected && toSelected) {
                AdvancedGraphDocument.Node target = findNodeIn(movedNodes, edge.toNode());
                String type = target == null ? "any"
                        : AdvancedGraphCatalog.inputs(target).getOrDefault(edge.toPort(), "any");
                String port = uniqueFunctionPort(edge.toPort(), usedInputs);
                inputPorts.putString(port, type);
                function.edges().add(new AdvancedGraphDocument.Edge(
                        UUID.randomUUID().toString(), functionInput.id(), port,
                        edge.toNode(), edge.toPort()));
                outerEdges.add(new AdvancedGraphDocument.Edge(
                        UUID.randomUUID().toString(), edge.fromNode(), edge.fromPort(),
                        "", port));
                continue;
            }
            if (fromSelected && !toSelected) {
                AdvancedGraphDocument.Node src = findNodeIn(movedNodes, edge.fromNode());
                String type = src == null ? "any"
                        : AdvancedGraphCatalog.outputs(src).getOrDefault(edge.fromPort(), "any");
                String port = uniqueFunctionPort(edge.fromPort(), usedOutputs);
                outputPorts.putString(port, type);
                function.edges().add(new AdvancedGraphDocument.Edge(
                        UUID.randomUUID().toString(), edge.fromNode(), edge.fromPort(),
                        functionOutput.id(), port));
                outerEdges.add(new AdvancedGraphDocument.Edge(
                        UUID.randomUUID().toString(), "", port,
                        edge.toNode(), edge.toPort()));
            }
        }
        functionInput.data().put("DynamicOutputs", inputPorts);
        functionOutput.data().put("DynamicInputs", outputPorts);

        // ------------------------------------REPLACE SELECTION------------------------------------
        sourceNodes.removeIf(node -> selected.contains(node.id()));
        sourceEdges.removeIf(edge -> selected.contains(edge.fromNode()) || selected.contains(edge.toNode()));
        moveSelectedGroupsToFunction(sourceGroups, function, selected);

        AdvancedGraphDocument.Node call = new AdvancedGraphDocument.Node(
                UUID.randomUUID().toString(), AdvancedGraphFunctions.CALL_TYPE, functionName,
                (minimumX + maximumX - NODE_WIDTH) * 0.5D,
                (minimumY + maximumY - 54.0D) * 0.5D, new CompoundTag());
        function.setViewport(panX, panY, zoom);
        draft.functions().add(function);
        AdvancedGraphFunctions.configureCall(call, function);
        sourceNodes.add(call);
        for (AdvancedGraphDocument.Edge edge : outerEdges) {
            sourceEdges.add(edge.fromNode().isBlank()
                    ? new AdvancedGraphDocument.Edge(edge.id(), call.id(), edge.fromPort(),
                    edge.toNode(), edge.toPort())
                    : new AdvancedGraphDocument.Edge(edge.id(), edge.fromNode(), edge.fromPort(),
                    call.id(), edge.toPort()));
        }
        AdvancedGraphFunctions.synchronizeCalls(draft);
        selectGraphTab(function.id());
        syncInspector();
    }

    // Find the node in the list
    private static AdvancedGraphDocument.Node findNodeIn(
            List<AdvancedGraphDocument.Node> nodes, String id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    // Get the unique function port
    private static String uniqueFunctionPort(String requested, Set<String> used) {
        String base = requested == null ? "" : requested.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = "value";
        }
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    // Open the function port dropdown
    private void openFunctionPortDropdown(
            AdvancedGraphDocument.Node node, double x, double y, double width) {
        if (!isFunctionInterfaceNode(node)) {
            return;
        }
        openOptionDropdown(node, FUNCTION_PORT_DROPDOWN, "string",
                x, y, width, FUNCTION_PORT_TYPE_OPTIONS);
    }

    // Add the function interface port
    private void addFunctionInterfacePort(AdvancedGraphDocument.Node node, String type) {
        if (!isFunctionInterfaceNode(node) || !FUNCTION_PORT_TYPE_OPTIONS.contains(type)) {
            return;
        }
        checkpoint();
        boolean inputNode = isFunctionInputNode(node);
        String key = inputNode ? "DynamicOutputs" : "DynamicInputs";
        CompoundTag ports = node.data().getCompound(key);
        Set<String> used = new LinkedHashSet<>(ports.getAllKeys());
        String requested = "exec".equals(type)
                ? inputNode ? "execute" : "then"
                : inputNode ? "input" : "output";
        String port = AdvancedGraphFunctions.uniquePort(requested, used);
        ports.putString(port, type);
        node.data().put(key, ports);
        AdvancedGraphFunctions.synchronizeCalls(draft);
        clearGraphRenderCache();
        syncInspector();
    }

    // Get the next function name
    private String nextFunctionName() {
        return nextFunctionName("");
    }

    // Handle the function node double click
    private boolean doubleClickFunctionNode(AdvancedGraphDocument.Node node) {
        long now = Util.getMillis();
        boolean doubleClick = node != null && AdvancedGraphFunctions.CALL_TYPE.equals(node.type())
                && node.id().equals(lastClickedNode)
                && now - lastNodeClickTime <= CANVAS_DOUBLE_CLICK_MILLIS;
        lastClickedNode = node == null ? null : node.id();
        lastNodeClickTime = now;
        if (!doubleClick) {
            return false;
        }
        String functionId = node.data().getString(AdvancedGraphFunctions.FUNCTION_ID);
        if (draft.function(functionId) == null) {
            return false;
        }
        selectGraphTab(functionId);
        return true;
    }

    // Handle the canvas double click
    private boolean handleCanvasDoubleClick(double mouseX, double mouseY) {
        long now = Util.getMillis();
        double deltaX = mouseX - lastCanvasClickX;
        double deltaY = mouseY - lastCanvasClickY;
        boolean doubleClick = now - lastCanvasClickTime <= CANVAS_DOUBLE_CLICK_MILLIS
                && deltaX * deltaX + deltaY * deltaY <= 36.0D;
        lastCanvasClickTime = doubleClick ? 0L : now;
        lastCanvasClickX = mouseX;
        lastCanvasClickY = mouseY;
        return doubleClick;
    }

    // Get the variable node name
    private static String variableNodeName(AdvancedGraphDocument.Node node) {
        if (node == null || !("variable_get".equals(node.type())
                || "variable_set".equals(node.type())
                || "event_variable_change".equals(node.type()))) {
            return "";
        }
        return node.data().getString("Variable");
    }

    // Import the plotted function
    String importPlottedFunction(AdvancedGraphDocument.FunctionGraph function) {
        if (function == null || function.nodes().isEmpty()) {
            return "The expression did not produce a graph function.";
        }
        if (draft.totalNodeCount() + function.nodes().size() > AdvancedGraphDocument.maxNodes()) {
            return "The converted function would exceed the graph node limit.";
        }
        if (draft.totalEdgeCount() + function.edges().size() > AdvancedGraphDocument.MAX_EDGES) {
            return "The converted function would exceed the graph connection limit.";
        }
        function.setName(nextFunctionName(function.name()));
        checkpoint();
        closeSaveSent = false;
        draft.functions().add(function);
        AdvancedGraphFunctions.synchronizeCalls(draft);
        selectGraphTab(function.id());
        clearGraphRenderCache();
        syncInspector();
        return "";
    }

    // Get the next function name
    private String nextFunctionName(String requested) {
        Set<String> names = draft.functions().stream()
                .map(function -> function.name().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        String preferred = requested == null ? "" : requested.strip();
        if (!preferred.isBlank()) {
            if (preferred.length() > 64) preferred = preferred.substring(0, 64);
            if (!names.contains(preferred.toLowerCase(Locale.ROOT))) {
                return preferred;
            }
            int preferredSuffix = 2;
            while (names.contains((preferred + " " + preferredSuffix).toLowerCase(Locale.ROOT))) {
                preferredSuffix++;
            }
            return preferred + " " + preferredSuffix;
        }
        if (!names.contains("function")) {
            return "Function";
        }
        int suffix = 2;
        while (names.contains(("Function " + suffix).toLowerCase(Locale.ROOT))) {
            suffix++;
        }
        return "Function " + suffix;
    }

    // Move the selected groups to function
    private static void moveSelectedGroupsToFunction(
            List<CompoundTag> sourceGroups, AdvancedGraphDocument.FunctionGraph function,
            Set<String> selected) {
        for (CompoundTag group : new ArrayList<>(sourceGroups)) {
            Set<String> members = new LinkedHashSet<>(AdvancedGraphSelection.commentGroupNodeIds(group));
            Set<String> movedMembers = new LinkedHashSet<>(members);
            movedMembers.retainAll(selected);
            if (movedMembers.isEmpty()) {
                continue;
            }
            CompoundTag movedGroup = group.copy();
            AdvancedGraphSelection.setCommentGroupNodeIds(movedGroup, movedMembers);
            function.groups().add(movedGroup);
            members.removeAll(selected);
            if (members.isEmpty()) {
                sourceGroups.remove(group);
            } else {
                AdvancedGraphSelection.setCommentGroupNodeIds(group, members);
            }
        }
    }

    // Move the group
    private void moveGroup(String id, double dx, double dy) {
        CompoundTag group = findGroup(id);
        if (group == null) return;
        group.putDouble("X", group.getDouble("X") + dx);
        group.putDouble("Y", group.getDouble("Y") + dy);
        Set<String> groupMembers = AdvancedGraphSelection.commentGroupNodeIds(group);
        for (int idx = 0; idx < activeNodes().size(); idx++) {
            AdvancedGraphDocument.Node node = activeNodes().get(idx);
            if (groupMembers.contains(node.id())) {
                replaceMovedNode(idx, node, dx, dy);
            }
        }
    }

    // Resize the group
    private void resizeGroup(String id, double dx, double dy) {
        CompoundTag group = findGroup(id);
        if (group == null) return;
        group.putDouble("Width", Math.max(96, group.getDouble("Width") + dx));
        group.putDouble("Height", Math.max(64, group.getDouble("Height") + dy));
    }

    // Resize the sticky node
    private void resizeStickyNode(String id, double dx, double dy) {
        AdvancedGraphDocument.Node node = findNode(id);
        if (!isStickyNote(node) && !isImageReference(node)) return;
        int minimumWidth = isImageReference(node) ? IMAGE_REFERENCE_MIN_WIDTH : STICKY_NOTE_MIN_WIDTH;
        int minimumHeight = isImageReference(node) ? IMAGE_REFERENCE_MIN_HEIGHT : STICKY_NOTE_MIN_HEIGHT;
        node.data().putInt("Width", Mth.clamp(
                (int) Math.round(nodeWidth(node) + dx), minimumWidth, 1000));
        node.data().putInt("Height", Mth.clamp(
                (int) Math.round(nodeHeight(node) + dy), minimumHeight, 800));
    }

    // Delete the selected group
    private void deleteSelectedGroup() {
        if (selectedGroup == null) return;
        checkpoint();
        activeGroups().removeIf(group -> selectedGroup.equals(group.getString("Id")));
        selectedGroup = null;
        syncInspector();
    }

    // Get the group
    private CompoundTag groupAt(double mouseX, double mouseY) {
        List<CompoundTag> groups = new ArrayList<>(activeGroups());
        Collections.reverse(groups);
        for (CompoundTag group : groups) {
            int x = screenX(group.getDouble("X"));
            int y = screenY(group.getDouble("Y"));
            int w = (int) (group.getDouble("Width") * zoom);
            int h = (int) (group.getDouble("Height") * zoom);
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) return group;
        }
        return null;
    }

    // Find the group
    private CompoundTag findGroup(String id) {
        if (id == null) return null;
        return activeGroups().stream().filter(group -> id.equals(group.getString("Id"))).findFirst().orElse(null);
    }

    // Get the selected group
    private CompoundTag selectedGroup() {
        return findGroup(selectedGroup);
    }

    // Select the group
    private void selectGroup(CompoundTag group) {
        selectedGroup = group == null ? null : group.getString("Id");
        selectCommentGroupNodes(group);
        selectedInputPort = null;
        editingBodyValue = false;
    }

    // Select the comment group nodes
    private void selectCommentGroupNodes(CompoundTag group) {
        selectedNodes.clear();
        if (group == null) return;
        Set<String> nodeIds = AdvancedGraphSelection.commentGroupNodeIds(group);
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if (nodeIds.contains(node.id())) {
                selectedNodes.add(node.id());
            }
        }
    }

    // Get the comment group for node
    private CompoundTag commentGroupForNode(String nodeId) {
        if (nodeId == null) return null;
        for (CompoundTag group : activeGroups()) {
            if (AdvancedGraphSelection.commentGroupNodeIds(group).contains(nodeId)) {
                return group;
            }
        }
        return null;
    }

    // Update the comment group after drop
    private void updateCommentGroupAfterDrop() {
        for (String nodeId : new LinkedHashSet<>(selectedNodes)) {
            AdvancedGraphDocument.Node node = findNode(nodeId);
            if (node == null) continue;
            CompoundTag currentGroup = commentGroupForNode(nodeId);
            if (currentGroup != null) {
                if (!groupContainsNode(currentGroup, node)) {
                    Set<String> members = AdvancedGraphSelection.commentGroupNodeIds(currentGroup);
                    members.remove(nodeId);
                    AdvancedGraphSelection.setCommentGroupNodeIds(currentGroup, members);
                }
                continue;
            }
            CompoundTag destinationGroup = commentGroupForNode(node);
            if (destinationGroup != null) {
                Set<String> members = AdvancedGraphSelection.commentGroupNodeIds(destinationGroup);
                members.add(nodeId);
                AdvancedGraphSelection.setCommentGroupNodeIds(destinationGroup, members);
            }
        }
    }

    // Get the comment group for node
    private CompoundTag commentGroupForNode(AdvancedGraphDocument.Node node) {
        List<CompoundTag> groups = new ArrayList<>(activeGroups());
        Collections.reverse(groups);
        for (CompoundTag group : groups) {
            if (groupContainsNode(group, node)) {
                return group;
            }
        }
        return null;
    }

    // Check if the group contains the node
    private boolean groupContainsNode(CompoundTag group, AdvancedGraphDocument.Node node) {
        double left = group.getDouble("X");
        double top = group.getDouble("Y");
        double right = left + group.getDouble("Width");
        double bottom = top + group.getDouble("Height");
        return node.x() >= left && node.y() >= top
                && node.x() + nodeWidth(node) <= right
                && node.y() + nodeHeight(node) <= bottom;
    }

    // Duplicate the selected
    private void duplicateSelected() {
        if (selectedNodes.isEmpty() && selectedGroup == null) return;
        Set<String> duplicableNodes = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if (selectedNodes.contains(node.id()) && isNodeAvailableInEditor(node.type())) {
                duplicableNodes.add(node.id());
            }
        }
        Set<String> selectedGroups = selectedGroup == null ? Set.of() : Set.of(selectedGroup);
        if (duplicableNodes.isEmpty() && selectedGroups.isEmpty()) return;
        checkpoint();
        AdvancedGraphDocument destination = activeGraphSnapshot();
        AdvancedGraphDocument selection = AdvancedGraphSelection.copy(
                destination, duplicableNodes, selectedGroups);
        AdvancedGraphSelection.AppendResult copies =
                AdvancedGraphSelection.appendCopyWithGroups(selection, destination, 30.0D, 30.0D);
        replaceActiveGraph(destination);
        selectedNodes.clear();
        selectedNodes.addAll(copies.nodeIds());
        selectedGroup = copies.groupIds().stream().findFirst().orElse(null);
        synchronizeVariableNodes();
        syncInspector();
    }

    // Delete the selected
    private void deleteSelected() {
        if (selectedNodes.isEmpty()) return;
        checkpoint();
        for (CompoundTag group : activeGroups()) {
            Set<String> members = AdvancedGraphSelection.commentGroupNodeIds(group);
            if (members.removeAll(selectedNodes)) {
                AdvancedGraphSelection.setCommentGroupNodeIds(group, members);
            }
        }
        activeNodes().removeIf(node -> selectedNodes.contains(node.id()));
        activeEdges().removeIf(edge -> selectedNodes.contains(edge.fromNode()) || selectedNodes.contains(edge.toNode()));
        synchronizeVariableNodes();
        synchronizeComparePorts();
        clearSelection();
    }

    // Disconnect the selected
    private void disconnectSelected() {
        checkpoint();
        activeEdges().removeIf(edge -> selectedNodes.contains(edge.fromNode()) || selectedNodes.contains(edge.toNode()));
        synchronizeComparePorts();
    }

    // Copy the selected
    private void copySelected() {
        Set<String> selectedGroups = selectedGroup == null ? Set.of() : Set.of(selectedGroup);
        sessionClipboard = AdvancedGraphSelection.copy(
                activeGraphSnapshot(), selectedNodes, selectedGroups);
    }

    // Paste the copied
    private void pasteCopied() {
        if (sessionClipboard == null
                || sessionClipboard.nodes().isEmpty() && sessionClipboard.groups().isEmpty()) return;
        Set<String> pasteableNodes = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node node : sessionClipboard.nodes()) {
            if (isNodeAvailableInEditor(node.type())) pasteableNodes.add(node.id());
        }
        Set<String> pasteableGroups = new LinkedHashSet<>();
        for (CompoundTag group : sessionClipboard.groups()) {
            pasteableGroups.add(group.getString("Id"));
        }
        if (pasteableNodes.isEmpty() && pasteableGroups.isEmpty()) return;
        AdvancedGraphDocument pasteableGraph =
                AdvancedGraphSelection.copy(sessionClipboard, pasteableNodes, pasteableGroups);
        if (!pasteableGraph.nodes().isEmpty()
                && activeNodes().size() >= AdvancedGraphDocument.maxNodes()) return;
        checkpoint();
        selectedNodes.clear();
        ClipboardBounds bounds = clipboardBounds(pasteableGraph);
        double offsetX = graphCenterX() - bounds.centerX();
        double offsetY = graphCenterY() - bounds.centerY();
        AdvancedGraphDocument destination = activeGraphSnapshot();
        AdvancedGraphSelection.AppendResult copies =
                AdvancedGraphSelection.appendCopyWithGroups(
                        pasteableGraph, destination, offsetX, offsetY);
        replaceActiveGraph(destination);
        selectedNodes.addAll(copies.nodeIds());
        selectedGroup = copies.groupIds().stream().findFirst().orElse(null);
        synchronizeVariableNodes();
        syncInspector();
    }

    // Get the active graph snapshot
    private AdvancedGraphDocument activeGraphSnapshot() {
        AdvancedGraphDocument snapshot = new AdvancedGraphDocument();
        snapshot.nodes().addAll(activeNodes());
        snapshot.edges().addAll(activeEdges());
        activeGroups().forEach(group -> snapshot.groups().add(group.copy()));
        draft.variables().forEach(snapshot.variables()::put);
        return snapshot;
    }

    // Replace the active graph
    private void replaceActiveGraph(AdvancedGraphDocument replacement) {
        activeNodes().clear();
        activeNodes().addAll(replacement.nodes());
        activeEdges().clear();
        activeEdges().addAll(replacement.edges());
        activeGroups().clear();
        replacement.groups().forEach(group -> activeGroups().add(group.copy()));
    }

    // Get the clipboard bounds
    private ClipboardBounds clipboardBounds(AdvancedGraphDocument graph) {
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            left = Math.min(left, node.x());
            top = Math.min(top, node.y());
            right = Math.max(right, node.x() + nodeWidth(node));
            bottom = Math.max(bottom, node.y() + nodeHeight(node));
        }
        for (CompoundTag group : graph.groups()) {
            left = Math.min(left, group.getDouble("X"));
            top = Math.min(top, group.getDouble("Y"));
            right = Math.max(right, group.getDouble("X") + group.getDouble("Width"));
            bottom = Math.max(bottom, group.getDouble("Y") + group.getDouble("Height"));
        }
        if (!Double.isFinite(left) || !Double.isFinite(top)
                || !Double.isFinite(right) || !Double.isFinite(bottom)) {
            return new ClipboardBounds(0.0D, 0.0D, 0.0D, 0.0D);
        }
        return new ClipboardBounds(left, top, right, bottom);
    }

    // Check if the node available is in the editor
    private boolean isNodeAvailableInEditor(String type) {
        if (type == null || DISABLED_NODE_TYPES.contains(type)) {
            return false;
        }
        if (type.startsWith("function:")) {
            return draft.function(type.substring("function:".length())) != null;
        }
        if (AdvancedGraphFunctions.INPUT_TYPE.equals(type)
                || AdvancedGraphFunctions.OUTPUT_TYPE.equals(type)) {
            return activeFunction() != null;
        }
        if (activeFunction() != null && isStandaloneExecSource(type)) {
            return false;
        }
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if (AdvancedGraphCatalog.isShipControlType(type)) {
            return controller != null && controller.hasShipControlModule();
        }
        return switch (type) {
            case "portable_tracker" -> controller != null && controller.isGogglesTrackerAvailable();
            case "controller_tracker" -> controller != null && controller.isControllerTrackerAvailable();
            default -> true;
        };
    }

    // Check if this is a standalone exec source
    private static boolean isStandaloneExecSource(String type) {
        AdvancedGraphCatalog.Definition definition = AdvancedGraphCatalog.get(type);
        return definition != null
                && definition.inputs().values().stream().noneMatch("exec"::equals)
                && definition.outputs().values().stream().anyMatch("exec"::equals);
    }

    // Get the function definition
    private AdvancedGraphCatalog.Definition functionDefinition(
            AdvancedGraphDocument.FunctionGraph function) {
        return new AdvancedGraphCatalog.Definition(
                "function:" + function.id(), "functions",
                AdvancedGraphFunctions.inputs(function), AdvancedGraphFunctions.outputs(function), true);
    }

    // Get the browser entry name
    private String browserEntryName(String id) {
        if (id != null && id.startsWith("function:")) {
            AdvancedGraphDocument.FunctionGraph function =
                    draft.function(id.substring("function:".length()));
            return function == null ? "Missing Function" : function.name();
        }
        return AdvancedGraphCatalog.displayName(id);
    }

    // Get the browser entry category
    private String browserEntryCategory(String id) {
        if (id != null && id.startsWith("function:")) {
            return "functions";
        }
        AdvancedGraphCatalog.Definition definition = AdvancedGraphCatalog.get(id);
        return definition == null ? "core" : definition.category();
    }

    // Save the current edit checkpoint
    private void checkpoint() {
        clearGraphRenderCache();
        draftDirty = true;
        simulatedDraft = null;
        undo.push(draft.copy());
        while (undo.size() > 64) undo.removeLast();
        redo.clear();
    }

    // Restore the advanced contraption controller
    private void restoreFrom(ArrayDeque<AdvancedGraphDocument> src, ArrayDeque<AdvancedGraphDocument> destination) {
        if (src.isEmpty()) return;
        clearGraphRenderCache();
        destination.push(draft.copy());
        draft = src.pop();
        draftDirty = true;
        restoreViewport();
        migrateSplitListNodes(draft);
        synchronizeComparePorts();
        clearSelection();
    }

    // Get the port
    private PortHit portAt(double mouseX, double mouseY) {
        prepareGraphRenderCache();
        List<AdvancedGraphDocument.Node> nodes = activeNodes();
        for (int nodeIndex = nodes.size() - 1; nodeIndex >= 0; nodeIndex--) {
            AdvancedGraphDocument.Node node = nodes.get(nodeIndex);
            int nodeX = screenX(node.x());
            int nodeY = screenY(node.y());
            int nodeWidth = (int) Math.round(nodeWidth(node) * zoom);
            int nodeHeight = (int) Math.round(nodeHeight(node) * zoom);
            if (!pointerNearNode(mouseX, mouseY, nodeX, nodeY, nodeWidth, nodeHeight, 9)) {
                continue;
            }
            boolean insideNode = containsNode(node, mouseX, mouseY);
            Map<String, String> inputs = nodeInputs(node);
            for (String port : inputs.keySet()) {
                if (!"exec".equals(inputs.get(port))
                        && !isDataPortVisible(node, port, false)) continue;
                PortPosition pos = portPosition(node, port, false);
                if (Math.abs(mouseX - pos.x()) <= 8 && Math.abs(mouseY - pos.y()) <= 8) return new PortHit(node, port, false);
            }
            Map<String, String> outputs = nodeOutputs(node);
            for (String port : outputs.keySet()) {
                if (!"exec".equals(outputs.get(port))
                        && !isDataPortVisible(node, port, true)) continue;
                PortPosition pos = portPosition(node, port, true);
                if (Math.abs(mouseX - pos.x()) <= 8 && Math.abs(mouseY - pos.y()) <= 8) return new PortHit(node, port, true);
            }
            if (insideNode) return null;
        }
        return null;
    }

    // Get the port position
    private PortPosition portPosition(AdvancedGraphDocument.Node node, String port, boolean output) {
        Map<String, String> portMap = output ? nodeOutputs(node) : nodeInputs(node);
        if (isReroute(node)) {
            int x = screenX(node.x()) + (output ? (int) Math.round(REROUTE_WIDTH * zoom) : 0);
            int y = screenY(node.y()) + (int) Math.round(REROUTE_HEIGHT * zoom * 0.5D);
            return new PortPosition(x, y);
        }
        int x;
        int y;
        if ("exec".equals(portMap.get(port))) {
            x = screenX(node.x()) + (output ? (int) (NODE_WIDTH * zoom) : 0);
            if (output && isShipCompletionPort(node, port)) {
                y = screenY(node.y()) + (int) ((portStart(node)
                        + outputDataPortsHeight(node) + 6) * zoom);
            } else if (output && ("branch".equals(node.type()) || isExecutionSplitterNode(node))) {
                List<String> execPorts = portMap.keySet().stream()
                        .filter(name -> "exec".equals(portMap.get(name))).toList();
                int idx = Math.max(0, execPorts.indexOf(port));
                y = screenY(node.y()) + (int) ((portStart(node) + idx * 13) * zoom);
            } else if (!output && isExecutionCombinerNode(node)) {
                List<String> execPorts = portMap.keySet().stream()
                        .filter(name -> "exec".equals(portMap.get(name))).toList();
                int idx = Math.max(0, execPorts.indexOf(port));
                y = screenY(node.y()) + (int) ((portStart(node) + idx * 13) * zoom);
            } else {
                y = screenY(node.y()) + (int) ((NODE_HEADER / 2.0) * zoom) - 2;
            }
        } else if (!output) {
            x = screenX(node.x()) + inlineMapPortIndent(node, port, false);
            if ("curve".equals(node.type()) && "value".equals(port)) {
                y = screenY(node.y()) + (int) Math.round((nodeBodyTopUnits(node)
                        + CURVE_BODY_HEIGHT / 2.0) * zoom);
            } else {
                y = bodyControlCenterY(node, port);
            }
        } else {
            x = screenX(node.x()) + (int) (NODE_WIDTH * zoom) - inlineMapPortIndent(node, port, true);
            y = screenY(node.y()) + (int) Math.round(portStart(node) * zoom)
                    + (int) Math.round(outputDataPortOffset(node, port) * zoom);
        }
        return new PortPosition(x, y);
    }

    // Check if this is a ship completion port
    private static boolean isShipCompletionPort(AdvancedGraphDocument.Node node, String port) {
        return node != null && "complete".equals(port)
                && AdvancedGraphCatalog.isShipControlCompletionType(node.type());
    }

    // Get the body control center y
    private int bodyControlCenterY(AdvancedGraphDocument.Node node, String port) {
        int row = (usesBinding(node) ? 1 : 0) + propertyControlCount(node);
        for (var entry : nodeInputs(node).entrySet()) {
            if ("exec".equals(entry.getValue())
                    || ("curve".equals(node.type()) && "value".equals(entry.getKey()))) continue;
            if (!isDataPortVisible(node, entry.getKey(), false)) continue;
            if (entry.getKey().equals(port)) break;
            row++;
        }
        int y = nodeBodyTop(node, screenY(node.y()));
        if("curve".equals(node.type())) y += (int) (CURVE_BODY_HEIGHT * zoom);
        for(int idx = 0; idx < row; idx++){
            y += (int) (15 * zoom);
        }
        return y + (int)(6.5 * zoom);
    }

    // Check if the pointer is over the collapse handle
    private boolean collapseHandleAt(AdvancedGraphDocument.Node node, double mouseX, double mouseY) {
        if (!canCollapse(node)) return false;
        int x = screenX(node.x());
        int y = screenY(node.y());
        int width = (int) (NODE_WIDTH * zoom);
        int height = (int) (nodeHeight(node) * zoom);
        int handleHeight = collapseHandleHeight();
        int top = collapseHandleTop(y);
        return mouseX >= x && mouseX <= x + width && mouseY >= top && mouseY <= top + handleHeight;
    }

    // Check if the pointer is over the sticky resize handle
    private boolean stickyResizeHandleAt(AdvancedGraphDocument.Node node, double mouseX, double mouseY) {
        if (!isStickyNote(node) && !isImageReference(node)) return false;
        int x = screenX(node.x());
        int y = screenY(node.y());
        int width = (int) Math.round(nodeWidth(node) * zoom);
        int height = (int) Math.round(nodeHeight(node) * zoom);
        int handle = Math.max(9, (int) Math.round(12 * zoom));
        return mouseX >= x + width - handle && mouseX <= x + width
                && mouseY >= y + height - handle && mouseY <= y + height;
    }

    // Check if the pointer is over the sticky text area
    private boolean stickyTextAreaAt(AdvancedGraphDocument.Node node, double mouseX, double mouseY) {
        if (!isStickyNote(node) || zoom < 0.55D) return false;
        int x = screenX(node.x());
        int y = screenY(node.y());
        int width = (int) Math.round(nodeWidth(node) * zoom);
        int height = (int) Math.round(nodeHeight(node) * zoom);
        int titleHeight = Math.max(16, (int) Math.round(20 * zoom));
        return mouseX >= x + 5 && mouseX <= x + width - 5
                && mouseY >= y + titleHeight + 2 && mouseY <= y + height - 7;
    }

    // Begin the sticky editing
    private void beginStickyEditing(AdvancedGraphDocument.Node node, double mouseX, double mouseY) {
        if (!isStickyNote(node)) return;
        if (!node.id().equals(editingStickyNode)) {
            checkpoint();
            editingStickyNode = node.id();
            String normalized = stickyText(node);
            node.data().putString("Text", normalized);
            stickyCaret = normalized.length();
            stickySelectionAnchor = stickyCaret;
        }
        positionStickyCaret(node, mouseX, mouseY, false);
    }

    // Finish the sticky editing
    private void finishStickyEditing() {
        editingStickyNode = null;
        stickyCaret = 0;
        stickySelectionAnchor = 0;
    }

    // Handle the position sticky caret
    private void positionStickyCaret(AdvancedGraphDocument.Node node, double mouseX, double mouseY,
                                     boolean extendSelection) {
        if (!isStickyNote(node)) return;
        String text = stickyText(node);
        int x = screenX(node.x()) + 7;
        int y = screenY(node.y()) + Math.max(16, (int) Math.round(20 * zoom)) + 5;
        int width = Math.max(20, (int) Math.floor(
                ((int) Math.round(nodeWidth(node) * zoom) - 14) / zoom));
        List<StickyEditLine> lines = stickyEditLines(text, width);
        int lineIndex = Mth.clamp((int) Math.floor(
                        (mouseY - y) / Math.max(1.0D, font.lineHeight * zoom)),
                0, Math.max(0, lines.size() - 1));
        StickyEditLine line = lines.get(lineIndex);
        int relativeX = Math.max(0, (int) Math.round((mouseX - x) / zoom));
        int cursor = line.start();
        int measured = 0;
        for (int idx = line.start(); idx < line.end(); idx++) {
            int characterWidth = font.width(String.valueOf(text.charAt(idx)));
            if (relativeX < measured + characterWidth / 2) break;
            measured += characterWidth;
            cursor = idx + 1;
        }
        stickyCaret = cursor;
        if (!extendSelection) stickySelectionAnchor = cursor;
    }

    // Handle the sticky editor key
    private boolean handleStickyEditorKey(int keyCode) {
        // -----------------------------------------------------EDITOR CHECKS-----------------------------------------------------
        AdvancedGraphDocument.Node node = findNode(editingStickyNode);
        if (!isStickyNote(node)) {
            finishStickyEditing();
            return true;
        }
        String text = stickyText(node);
        boolean selecting = hasShiftDown();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finishStickyEditing();
            return true;
        }
        // ------------------------------------CLIPBOARD SHORTCUTS------------------------------------
        if (hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_A) {
                stickySelectionAnchor = 0;
                stickyCaret = text.length();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_C) {
                int start = stickySelectionStart();
                int end = stickySelectionEnd();
                minecraft.keyboardHandler.setClipboard(start == end ? text : text.substring(start, end));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                int start = stickySelectionStart();
                int end = stickySelectionEnd();
                minecraft.keyboardHandler.setClipboard(start == end ? text : text.substring(start, end));
                if (start == end) {
                    stickySelectionAnchor = 0;
                    stickyCaret = text.length();
                }
                replaceStickySelection("");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                insertStickyText(minecraft.keyboardHandler.getClipboard());
                return true;
            }
        }
        // -----------------------------------------------------TEXT ENTRY-----------------------------------------------------
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            insertStickyText("\n");
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            insertStickyText("    ");
            return true;
        }
        // -----------------------------------------------------TEXT DELETION-----------------------------------------------------
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (stickySelectionStart() != stickySelectionEnd()) {
                replaceStickySelection("");
            } else if (stickyCaret > 0) {
                stickySelectionAnchor = text.offsetByCodePoints(stickyCaret, -1);
                replaceStickySelection("");
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (stickySelectionStart() != stickySelectionEnd()) {
                replaceStickySelection("");
            } else if (stickyCaret < text.length()) {
                stickySelectionAnchor = text.offsetByCodePoints(stickyCaret, 1);
                replaceStickySelection("");
            }
            return true;
        }
        // ------------------------------------CURSOR MOVEMENT------------------------------------
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            int destination = !selecting && stickySelectionStart() != stickySelectionEnd()
                    ? stickySelectionStart()
                    : stickyCaret > 0 ? text.offsetByCodePoints(stickyCaret, -1) : 0;
            moveStickyCaret(destination, selecting);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            int destination = !selecting && stickySelectionStart() != stickySelectionEnd()
                    ? stickySelectionEnd()
                    : stickyCaret < text.length() ? text.offsetByCodePoints(stickyCaret, 1) : text.length();
            moveStickyCaret(destination, selecting);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            moveStickyCaretVertically(node, keyCode, selecting);
            return true;
        }
        return true;
    }

    // Insert the sticky text
    private void insertStickyText(String inserted) {
        if (inserted == null || inserted.isEmpty()) return;
        String normalized = inserted.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder accepted = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\t' || !Character.isISOControl(codePoint)) {
                accepted.appendCodePoint(codePoint);
            }
        });
        replaceStickySelection(accepted.toString());
    }

    // Replace the sticky selection
    private void replaceStickySelection(String replacement) {
        AdvancedGraphDocument.Node node = findNode(editingStickyNode);
        if (!isStickyNote(node)) return;
        String text = stickyText(node);
        int start = stickySelectionStart();
        int end = stickySelectionEnd();
        int available = STICKY_NOTE_MAX_TEXT_LENGTH - (text.length() - (end - start));
        String inserted = replacement == null ? "" : replacement;
        if (inserted.length() > available) inserted = inserted.substring(0, Math.max(0, available));
        String updated = text.substring(0, start) + inserted + text.substring(end);
        node.data().putString("Text", updated);
        stickyCaret = start + inserted.length();
        stickySelectionAnchor = stickyCaret;
    }

    // Move the sticky caret
    private void moveStickyCaret(int destination, boolean selecting) {
        stickyCaret = Math.max(0, destination);
        if (!selecting) stickySelectionAnchor = stickyCaret;
    }

    // Move the sticky caret vertically
    private void moveStickyCaretVertically(AdvancedGraphDocument.Node node, int keyCode, boolean selecting) {
        String text = stickyText(node);
        int maxWidth = Math.max(20, (int) Math.floor(
                ((int) Math.round(nodeWidth(node) * zoom) - 14) / zoom));
        List<StickyEditLine> lines = stickyEditLines(text, maxWidth);
        int currentLine = stickyCaretLine(lines);
        StickyEditLine line = lines.get(currentLine);
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            moveStickyCaret(line.start(), selecting);
            return;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            moveStickyCaret(line.end(), selecting);
            return;
        }
        int targetLine = Mth.clamp(currentLine + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), 0, lines.size() - 1);
        StickyEditLine target = lines.get(targetLine);
        int desiredX = font.width(text.substring(line.start(), Mth.clamp(stickyCaret, line.start(), line.end())));
        int cursor = target.start();
        int measured = 0;
        for (int idx = target.start(); idx < target.end(); idx++) {
            int characterWidth = font.width(String.valueOf(text.charAt(idx)));
            if (desiredX < measured + characterWidth / 2) break;
            measured += characterWidth;
            cursor = idx + 1;
        }
        moveStickyCaret(cursor, selecting);
    }

    // Get the sticky caret line
    private int stickyCaretLine(List<StickyEditLine> lines) {
        int res = 0;
        for (int idx = 0; idx < lines.size(); idx++) {
            StickyEditLine line = lines.get(idx);
            if (stickyCaret < line.start()) break;
            res = idx;
            if (stickyCaret < line.end()) break;
            if (stickyCaret == line.end()
                    && (idx + 1 >= lines.size() || lines.get(idx + 1).start() != stickyCaret)) break;
        }
        return res;
    }

    // Get the sticky selection start
    private int stickySelectionStart() {
        return Math.min(stickyCaret, stickySelectionAnchor);
    }

    // Get the sticky selection end
    private int stickySelectionEnd() {
        return Math.max(stickyCaret, stickySelectionAnchor);
    }

    // Get the node
    private AdvancedGraphDocument.Node nodeAt(double mouseX, double mouseY) {
        List<AdvancedGraphDocument.Node> reverse = new ArrayList<>(activeNodes());
        Collections.reverse(reverse);
        for (AdvancedGraphDocument.Node node : reverse) {
            if (containsNode(node, mouseX, mouseY)) return node;
        }
        return null;
    }

    // Check if this contains node
    private boolean containsNode(AdvancedGraphDocument.Node node, double mouseX, double mouseY) {
        int x = screenX(node.x());
        int y = screenY(node.y());
        return mouseX >= x && mouseX <= x + nodeWidth(node) * zoom
                && mouseY >= y && mouseY <= y + nodeHeight(node) * zoom;
    }

    // Check if the pointer is near the node
    static boolean pointerNearNode(double mouseX, double mouseY, int nodeX, int nodeY,
                                   int nodeWidth, int nodeHeight, int margin) {
        return mouseX >= nodeX - margin && mouseX <= nodeX + nodeWidth + margin
                && mouseY >= nodeY - margin && mouseY <= nodeY + nodeHeight + margin;
    }

    // Check if the bounds intersect the viewport
    static boolean intersectsViewport(int x, int y, int width, int height,
                                      int viewportLeft, int viewportTop,
                                      int viewportRight, int viewportBottom, int margin) {
        int right = x + Math.max(1, width);
        int bottom = y + Math.max(1, height);
        return right >= viewportLeft - margin && x <= viewportRight + margin
                && bottom >= viewportTop - margin && y <= viewportBottom + margin;
    }

    // Find the node
    private AdvancedGraphDocument.Node findNode(String id) {
        if (id == null) return null;
        if (graphRenderCacheDocument == draft) {
            return graphRenderNodes.get(id);
        }
        return activeNodes().stream().filter(node -> node.id().equals(id)).findFirst().orElse(null);
    }

    // Get the selected node
    private AdvancedGraphDocument.Node selectedNode() {
        return selectedNodes.size() == 1 ? findNode(selectedNodes.iterator().next()) : null;
    }

    // Select the only
    private void selectOnly(String id) {
        selectedGroup = null;
        if (selectedNodes.size() != 1 || !selectedNodes.contains(id)) {
            selectedInputPort = null;
            inspectorOptionsScroll = 0;
            inspectorTargetsScroll = 0;
            editingBodyValue = false;
        }
        selectedNodes.clear();
        if (id != null) selectedNodes.add(id);
        AdvancedGraphDocument.Node node = findNode(id);
        if (frequencyModalOpen && (node == null || !node.id().equals(frequencyNode))) {
            closeFrequencyEditor();
        }
    }

    // Clear the selection
    private void clearSelection() {
        finishStickyEditing();
        selectedNodes.clear();
        inspectorOptionsScroll = 0;
        inspectorTargetsScroll = 0;
        editingBodyValue = false;
        closeFrequencyEditor();
        syncInspector();
    }

    // Close the frequency editor
    private boolean closeFrequencyEditor() {
        return closeFrequencyEditor(true);
    }

    // Close the frequency editor
    private boolean closeFrequencyEditor(boolean settleCarriedStack) {
        return closeFrequencyEditor(settleCarriedStack, true);
    }

    // Close the frequency editor
    private boolean closeFrequencyEditor(boolean settleCarriedStack, boolean syncNode) {
        if (frequencyModalOpen && settleCarriedStack && !settleCarriedStackForModal()) {
            return false;
        }
        if (frequencyModalOpen && syncNode && findNode(frequencyNode) != null) {
            syncFrequencyNode();
        }
        frequencyNode = null;
        frequencyModalOpen = false;
        menu.ghostSlotsActive = false;
        menu.playerSlotsActive = linkerOpen;
        if (!linkerOpen) {
            leftPos = layoutLeft();
            topPos = 0;
        }
        if (settleCarriedStack) {
            clearDraggingState();
        }
        return true;
    }

    // Settle the carried stack for the modal
    private boolean settleCarriedStackForModal() {
        if (menu.getCarried().isEmpty()) {
            return true;
        }
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return false;
        }
        return settleCarriedStackInPlayerInventory();
    }

    // Settle the carried stack in the player inventory
    private boolean settleCarriedStackInPlayerInventory() {
        if (menu.getCarried().isEmpty()) {
            return true;
        }
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) {
            return false;
        }
        if (settleCarriedStackInPlayerInventory(false)) {
            return true;
        }
        return settleCarriedStackInPlayerInventory(true);
    }

    // Settle the carried stack in the player inventory
    private boolean settleCarriedStackInPlayerInventory(boolean emptySlots) {
        for (Slot slot : menu.slots) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) {
                return true;
            }
            if (!slot.isActive() || !(slot.container instanceof Inventory) || !slot.mayPlace(carried)) {
                continue;
            }

            ItemStack existing = slot.getItem();
            boolean accepts = emptySlots
                    ? existing.isEmpty()
                    : !existing.isEmpty()
                    && ItemStack.isSameItemSameComponents(existing, carried)
                    && existing.getCount() < slot.getMaxStackSize(carried);
            if (accepts) {
                slotClicked(slot, slot.index, 0, ClickType.PICKUP);
            }
        }
        return menu.getCarried().isEmpty();
    }

    // Clear the connection
    private void clearConnection() {
        connectingNode = null;
        connectingPort = null;
    }

    // Clear the text focus
    private void clearTextFocus() {
        if (nodeSearch != null) nodeSearch.setFocused(false);
        if (blockSearch != null) blockSearch.setFocused(false);
        if (miniBrowserSearch != null) miniBrowserSearch.setFocused(false);
        if (inspectorValue != null) inspectorValue.setFocused(false);
        editingBodyValue = false;
        setFocused(null);
    }

    // Get the screen x
    private int screenX(double graphX) {
        return (int) (graphLeft() + panX + graphX * zoom);
    }

    // Get the screen y
    private int screenY(double graphY) {
        return (int) (graphTop() + panY + graphY * zoom);
    }

    // Get the graph x
    private double graphX(double screenX) {
        return (screenX - graphLeft() - panX) / zoom;
    }

    // Get the graph y
    private double graphY(double screenY) {
        return (screenY - graphTop() - panY) / zoom;
    }

    // Restore the viewport
    private void restoreViewport() {
        if (draft == null) return;
        AdvancedGraphDocument.FunctionGraph function = activeFunction();
        panX = function == null ? draft.viewportX() : function.viewportX();
        panY = function == null ? draft.viewportY() : function.viewportY();
        zoom = Mth.clamp(function == null ? draft.viewportZoom() : function.viewportZoom(), 0.05D, 1.75D);
    }

    // Store the viewport
    private void storeViewport() {
        if (draft == null) return;
        AdvancedGraphDocument.FunctionGraph function = activeFunction();
        if (function == null) {
            if (Float.compare(draft.viewportX(), (float) panX) != 0
                    || Float.compare(draft.viewportY(), (float) panY) != 0
                    || Float.compare(draft.viewportZoom(), (float) zoom) != 0) {
                draftDirty = true;
            }
            draft.setViewport(panX, panY, zoom);
        } else {
            if (Float.compare(function.viewportX(), (float) panX) != 0
                    || Float.compare(function.viewportY(), (float) panY) != 0
                    || Float.compare(function.viewportZoom(), (float) zoom) != 0) {
                draftDirty = true;
            }
            function.setViewport(panX, panY, zoom);
        }
    }

    // Get the graph center x
    private double graphCenterX() {
        return graphX((graphLeft() + graphRight()) / 2.0);
    }

    // Get the graph center y
    private double graphCenterY() {
        return graphY((graphTop() + graphBottom()) / 2.0);
    }

    // Get the node title
    private String nodeTitle(AdvancedGraphDocument.Node node) {
        if (node != null && AdvancedGraphFunctions.CALL_TYPE.equals(node.type())
                && !node.data().getString("FunctionName").isBlank()) {
            return node.data().getString("FunctionName");
        }
        return node.label().isBlank() || node.label().equals(node.type()) ? AdvancedGraphCatalog.displayName(node.type()) : node.label();
    }

    // Get the editable property
    private String editableProperty(AdvancedGraphDocument.Node node) {
        if (node == null) return null;
        return switch (node.type()) {
            case "event_trigger", "event_named_controller", "send_named_controller_event" -> "Event";
            case "mouse_input" -> "MouseInput";
            case "controller_channel_input", "gamepad_input", "local_redstone_input", "wireless_frequency_input" ->
                    "PulseBehavior";
            case "variable_get", "variable_set", "event_variable_change" -> "Variable";
            case "constant_number", "constant_boolean", "constant_string" -> "Value";
            case "delay", "debounce" -> "Ticks";
            case "event_periodic" -> "Period";
            case "parallel_execution", "sequenced_execution", "switch" -> "OutputCount";
            case "exec_combine" -> "InputCount";
            case "convert_type" -> "OutputType";
            case "curve" -> "CurveType";
            case "pid" -> AdvancedGraphCatalog.PID_PREVENT_INTEGRAL_WINDUP_TAG;
            case "portable_tracker" -> gogglesTrackerPairs().size() > 1 ? "GogglesPair" : null;
            case "image_reference" -> "Source";
            case "acc_display_widget", "acc_hologram_widget" -> "WidgetType";
            case "acc_display_crn" -> "DisplayMode";
            default -> null;
        };
    }

    // Check if this has property control
    private boolean hasPropertyControl(AdvancedGraphDocument.Node node) {
        String property = editableProperty(node);
        if (property == null) return false;
        String port = Character.toLowerCase(property.charAt(0)) + property.substring(1);
        return !AdvancedGraphCatalog.inputs(node).containsKey(port);
    }

    // Check if this is a pulse behavior input type
    private static boolean isPulseBehaviorInputType(String type) {
        return "controller_channel_input".equals(type)
                || "gamepad_input".equals(type)
                || "local_redstone_input".equals(type)
                || "wireless_frequency_input".equals(type);
    }

    // Check if this has switch type control
    private static boolean hasSwitchTypeControl(AdvancedGraphDocument.Node node) {
        return node != null && "switch".equals(node.type());
    }

    // Get the property control count
    private int propertyControlCount(AdvancedGraphDocument.Node node) {
        return (hasSwitchTypeControl(node) ? 1 : 0) + (hasPropertyControl(node) ? 1 : 0);
    }

    // Get the goggles tracker pairs
    private List<AdvancedContraptionControllerBlockEntity.GogglesTrackerPair> gogglesTrackerPairs() {
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        return controller == null ? List.of() : controller.getGogglesTrackerPairs();
    }

    // Get the goggles tracker pair ids
    private List<String> gogglesTrackerPairIds() {
        return gogglesTrackerPairs().stream()
                .map(pair -> pair.id().toString())
                .toList();
    }

    // Get the variable selector options
    private List<String> variableSelectorOptions(AdvancedGraphDocument.Node node) {
        LinkedHashSet<String> names = new LinkedHashSet<>(draft.variables().keySet().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
        String current = node == null ? "" : node.data().getString("Variable");
        if (!current.isBlank()) {
            names.add(current);
        }
        return List.copyOf(names);
    }

    // Get the goggles tracker pair label
    private String gogglesTrackerPairLabel(String serializedId) {
        if (serializedId == null || serializedId.isBlank()) {
            return "";
        }
        try {
            UUID pairId = UUID.fromString(serializedId);
            return gogglesTrackerPairs().stream()
                    .filter(pair -> pair.id().equals(pairId))
                    .map(AdvancedContraptionControllerBlockEntity.GogglesTrackerPair::label)
                    .findFirst()
                    .orElse("");
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    // Get the property value label
    private String propertyValueLabel(AdvancedGraphDocument.Node node, String property) {
        if ("constant_boolean".equals(node.type())) return node.data().getBoolean(property) ? "[x]" : "[ ]";
        if ("pid".equals(node.type())) return node.data().getBoolean(property) ? "[x]" : "[ ]";
        if ("constant_number".equals(node.type())) return compactNumber(node.data().getDouble(property));
        if ("convert_type".equals(node.type())) return humanPort(node.data().getString(property));
        if ("mouse_input".equals(node.type())) return humanPort(propertyOptionValue(node, property));
        if ("PulseBehavior".equals(property)) return humanPort(propertyOptionValue(node, property));
        if ("portable_tracker".equals(node.type()) && "GogglesPair".equals(property)) {
            String label = gogglesTrackerPairLabel(propertyOptionValue(node, property));
            return label.isBlank() ? "Linked Goggles" : label;
        }
        if ("curve".equals(node.type())) {
            String curveType = node.data().getString(property);
            return humanPort(curveType.isBlank() ? "linear" : curveType);
        }
        if (isAccDisplayWidgetType(node.type())) {
            String type = node.data().getString("WidgetType");
            return humanPort(type.isBlank() ? "text" : type);
        }
        if ("acc_display_crn".equals(node.type())) {
            return crnDisplayModeLabel(propertyOptionValue(node, property));
        }
        if ("OutputCount".equals(property)) return Integer.toString(executionOutputCount(node));
        if ("InputCount".equals(property)) return Integer.toString(executionInputCount(node));
        if ("Ticks".equals(property) || "Period".equals(property)) return Integer.toString(node.data().getInt(property));
        return valueOrUnset(node.data().getString(property));
    }

    // Apply the property control
    private void applyPropertyControl(AdvancedGraphDocument.Node node) {
        selectedInputPort = editableProperty(node);
        if ("constant_boolean".equals(node.type())) {
            checkpoint();
            node.data().putBoolean("Value", !node.data().getBoolean("Value"));
        } else if ("pid".equals(node.type())) {
            checkpoint();
            configPidWindup(node,
                    !node.data().getBoolean(AdvancedGraphCatalog.PID_PREVENT_INTEGRAL_WINDUP_TAG));
        } else if ("convert_type".equals(node.type())) {
            checkpoint();
            List<String> types = List.of("string", "number", "boolean", "direction", "frequency", "target", "list", "map");
            String current = node.data().getString("OutputType");
            String next = types.get((Math.max(0, types.indexOf(current)) + 1) % types.size());
            node.data().putString("OutputType", next);
            CompoundTag outputs = node.data().getCompound("DynamicOutputs");
            outputs.putString("value", next);
            node.data().put("DynamicOutputs", outputs);
        } else if ("curve".equals(node.type())) {
            checkpoint();
            node.data().putString("CurveType", "bezier".equals(node.data().getString("CurveType")) ? "linear" : "bezier");
        } else if (isExecutionSplitterNode(node)) {
            checkpoint();
            int count = Math.min(MAX_EXECUTION_OUTPUTS, executionOutputCount(node) + 1);
            setExecutionOutputCount(node, count, true);
        } else if (isExecutionCombinerNode(node)) {
            checkpoint();
            int count = Math.min(MAX_EXECUTION_INPUTS, executionInputCount(node) + 1);
            setExecutionInputCount(node, count, true);
        }
    }

    // Configure the ACC widget type
    private void configAccWidgetType(AdvancedGraphDocument.Node node, String widgetType) {
        if (node == null || !isAccDisplayWidgetType(node.type())) {
            return;
        }
        String type = ACC_DISPLAY_WIDGET_TYPES.contains(widgetType) ? widgetType : "text";
        node.data().putString("WidgetType", type);
        ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
        CompoundTag elm;
        if (elements.isEmpty()) {
            elm = new CompoundTag();
            elements.add(elm);
        } else {
            elm = elements.getCompound(0);
        }
        elm.putBoolean("ManagedWidget", true);
        elm.putString("Type", type);
        elm.putString("Text", switch (type) {
            case "button" -> "Button";
            case "toggle" -> "Toggle";
            case "slider" -> "Slider";
            case "progress" -> "Progress";
            case "text_input" -> "Text Input";
            default -> "Widget";
        });
        elm.putInt("X", 4);
        elm.putInt("Y", 4);
        elm.putInt("W", Math.max(16, node.data().getInt("WidgetWidth") - 8));
        elm.putInt("H", Math.max(12, node.data().getInt("WidgetHeight") - 8));
        if ("value".equals(type) || "progress".equals(type)) {
            elm.putString("Port", "value");
        } else {
            elm.remove("Port");
        }
        if ("slider".equals(type) || "progress".equals(type)) {
            elm.putDouble("Min", 0.0D);
            elm.putDouble("Max", 1.0D);
            if ("slider".equals(type)) elm.putDouble("Step", 0.05D);
        }
        bindManagedAccWidget(elm);
        AdvancedHudElementStyle.applyDefaults(elm);
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        CompoundTag defaults = node.data().getCompound("Defaults");
        if ("image".equals(type)) {
            inputs.putString("image", "string");
            if (!defaults.contains("image")) {
                defaults.put("image", graphDefault("string", ""));
            }
        } else {
            inputs.remove("image");
            defaults.remove("image");
        }
        node.data().put("DynamicInputs", inputs);
        node.data().put("Defaults", defaults);
        node.data().put(WIDGET_ELEMENTS, elements);
        syncHudPorts(node);
    }

    // Configure the ACC CRN mode
    private void configAccCrnMode(AdvancedGraphDocument.Node node, String displayMode) {
        if (node == null || !"acc_display_crn".equals(node.type())) {
            return;
        }
        String mode = ShipInformationDisplayModes.normalize(displayMode);
        node.data().putString("DisplayMode", mode);
        CompoundTag defaults = node.data().getCompound("Defaults");
        if (!defaults.contains("text")) {
            defaults.put("text", graphDefault("string", ""));
            node.data().put("Defaults", defaults);
        }
        if (!AdvancedGraphCatalog.isCrnStaticTextMode(mode)) {
            activeEdges().removeIf(edge -> edge.toNode().equals(node.id())
                    && "text".equals(edge.toPort()));
        }
        clearGraphRenderCache();
    }

    // Bind the managed ACC widget
    private static void bindManagedAccWidget(CompoundTag elm) {
        Map<String, String> bindings = Map.ofEntries(
                Map.entry("Text", "label"), Map.entry("X", "x"), Map.entry("Y", "y"),
                Map.entry("W", "width"), Map.entry("H", "height"),
                Map.entry("Rotation", "rotation"), Map.entry("Scale", "scale"),
                Map.entry("FontSize", "font_size"), Map.entry("Color", "color"),
                Map.entry("BackgroundColor", "background_color"),
                Map.entry("AccentColor", "accent_color"), Map.entry("TrackColor", "track_color"),
                Map.entry("BorderColor", "border_color"),
                Map.entry("BorderWidth", "border_width"),
                Map.entry("BorderRadius", "border_radius"),
                Map.entry("Min", "minimum"), Map.entry("Max", "maximum"));
        bindings.forEach((property, port) -> AdvancedHudElementBinding.bind(elm, property, port));
    }

    // Configure the PID windup
    private void configPidWindup(AdvancedGraphDocument.Node node, boolean enabled) {
        node.data().putBoolean(AdvancedGraphCatalog.PID_PREVENT_INTEGRAL_WINDUP_TAG, enabled);
        if (enabled) {
            CompoundTag defaults = node.data().getCompound("Defaults");
            if (!defaults.contains(AdvancedGraphCatalog.PID_INTEGRAL_MIN_PORT)) {
                putInputDefault(node, AdvancedGraphCatalog.PID_INTEGRAL_MIN_PORT, "number", -100.0D);
            }
            if (!defaults.contains(AdvancedGraphCatalog.PID_INTEGRAL_MAX_PORT)) {
                putInputDefault(node, AdvancedGraphCatalog.PID_INTEGRAL_MAX_PORT, "number", 100.0D);
            }
        } else {
            removePidWindupEdges(node.id());
        }
        clearGraphRenderCache();
        syncInspector();
    }

    // Remove the PID windup edges
    private void removePidWindupEdges(String nodeId) {
        activeEdges().removeIf(edge -> edge.toNode().equals(nodeId)
                && (AdvancedGraphCatalog.PID_INTEGRAL_MIN_PORT.equals(edge.toPort())
                || AdvancedGraphCatalog.PID_INTEGRAL_MAX_PORT.equals(edge.toPort())));
    }

    // Draw the share window
    private void drawShareWindow(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect bounds = shareModalBounds();
        positionLinkerShareField();
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.drawString(font, "Share Graph", bounds.x() + 8, bounds.y() + 7, 0xFFFFFFFF, false);
        int x = bounds.x() + 16;
        int y = bounds.y() + 28;
        graphics.drawString(font, "Shared Graph", x, y - 8, 0xFF91D9FF, false);
        drawLinkerActionButton(graphics, x, y + 30, 100, "Save Locally", true, mouseX, mouseY);
        drawLinkerActionButton(graphics, x + 108, y + 30, 152, "Save locally and upload",
                canUploadSharedGraph(), mouseX, mouseY);
        drawLinkerActionButton(graphics, x, y + 54, 260, "Open shared graphs directory",
                true, mouseX, mouseY);
        graphics.drawString(font, "Saved Graphs", x, y + 80, 0xFF91D9FF, false);
        int listY = y + 92;
        int listW = 132;
        int listH = 58;
        renderControllerOption(graphics, x, listY, listW, listH, 0xFF5D9FE3, false);
        graphics.renderOutline(x, listY, listW, listH, 0xFF3D637E);
        int visibleRows = 4;
        int maxScroll = Math.max(0, sharedGraphEntries.size() - visibleRows);
        sharedGraphScroll = Mth.clamp(sharedGraphScroll, 0, maxScroll);
        if (sharedGraphEntries.isEmpty()) {
            graphics.drawString(font, "No shared graphs", x + 6, listY + 7, 0xFF91A9B8, false);
        } else {
            for (int row = 0; row < visibleRows; row++) {
                int idx = sharedGraphScroll + row;
                if (idx >= sharedGraphEntries.size()) {
                    break;
                }
                ControllerManifestStore.SharedGraphEntry entry = sharedGraphEntries.get(idx);
                int rowY = listY + 3 + row * 13;
                if (idx == selectedSharedGraphIndex) {
                    renderControllerOption(graphics, x + 2, rowY - 1, listW - 4, 12, 0xFF5D9FE3, true);
                }
                graphics.drawString(font, trim(entry.name(), 18), x + 5, rowY,
                        idx == selectedSharedGraphIndex ? nodeValueTextColor() : 0xFFE6F6FF, false);
            }
        }
        drawLinkerActionButton(graphics, x, y + 155, 64, "Refresh", true, mouseX, mouseY);
        drawLinkerActionButton(graphics, x + 68, y + 155, 64, "Load", selectedSharedGraphIndex >= 0,
                mouseX, mouseY);
        int webX = bounds.x() + 156;
        graphics.drawString(font, "Public Web Share", webX, bounds.y() + 108, 0xFF91D9FF, false);
        if (publicShareAvailable) {
            drawLinkerActionButton(graphics, webX, bounds.y() + 120, 120,
                    publicSharePending ? "Uploading..." : "Generate Share Code",
                    !publicSharePending, mouseX, mouseY);
        }
        drawLinkerActionButton(graphics, webX, bounds.y() + 144, 120,
                "Open Local Editor", true, mouseX, mouseY);
        if (linkerStatusTicks > 0 && !linkerStatusMessage.isBlank()) {
            graphics.drawString(font, trim(linkerStatusMessage, 38), x, bounds.y() + 216, 0xFFFFD27A, false);
        }
        drawShareConflict(graphics, mouseX, mouseY);
    }

    // Draw the linker action button
    private void drawLinkerActionButton(GuiGraphics graphics, int x, int y, int width, String label, boolean enabled,
                                         int mouseX, int mouseY) {
        renderAdvancedButton(graphics, font, x, y, width, 18, Component.literal(label),
                inside(mouseX, mouseY, x, y, width, 18), enabled);
    }

    // Draw the share conflict
    private void drawShareConflict(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sharedGraphConflictName.isBlank()) {
            return;
        }
        UiRect bounds = sharedGraphConflictBounds();
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.drawCenteredString(font, "File name already exists", bounds.x() + bounds.width() / 2,
                bounds.y() + 10, 0xFFFFFFFF);
        UiRect overwrite = sharedGraphConflictOverwriteBounds();
        UiRect increment = sharedGraphConflictIncrementBounds();
        UiRect cancel = sharedGraphConflictCancelBounds();
        renderAdvancedButton(graphics, font, overwrite.x(), overwrite.y(), overwrite.width(), overwrite.height(),
                Component.literal("Overwrite"), overwrite.contains(mouseX, mouseY), true);
        renderAdvancedButton(graphics, font, increment.x(), increment.y(), increment.width(), increment.height(),
                Component.literal("Increment"), increment.contains(mouseX, mouseY), true);
        renderAdvancedButton(graphics, font, cancel.x(), cancel.y(), cancel.width(), cancel.height(),
                Component.literal("Cancel"), cancel.contains(mouseX, mouseY), true);
    }

    // Handle the share conflict click
    private boolean clickShareConflict(double mouseX, double mouseY, int btn) {
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        String name = sharedGraphConflictName;
        if (sharedGraphConflictOverwriteBounds().contains(mouseX, mouseY)) {
            sharedGraphConflictName = "";
            resolveShareConflict(name, ControllerManifestStore.SharedGraphSaveMode.OVERWRITE);
        } else if (sharedGraphConflictIncrementBounds().contains(mouseX, mouseY)) {
            sharedGraphConflictName = "";
            resolveShareConflict(name, ControllerManifestStore.SharedGraphSaveMode.INCREMENT);
        } else if (sharedGraphConflictCancelBounds().contains(mouseX, mouseY)) {
            sharedGraphConflictName = "";
            sharedGraphSaveTarget = SharedGraphSaveTarget.NONE;
        }
        return true;
    }

    // Get the shared graph conflict bounds
    private UiRect sharedGraphConflictBounds() {
        UiRect share = shareModalBounds();
        return new UiRect(share.x() + 32, share.y() + 91, 228, 62);
    }

    // Get the share modal bounds
    private UiRect shareModalBounds() {
        int modalWidth = SHARE_MODAL_WIDTH;
        int modalHeight = SHARE_MODAL_HEIGHT;
        int left = Mth.clamp((width - modalWidth) / 2, layoutLeft(),
                Math.max(layoutLeft(), layoutRight() - modalWidth));
        int top = Mth.clamp((height - modalHeight) / 2, TOOLBAR_HEIGHT,
                Math.max(TOOLBAR_HEIGHT, height - modalHeight));
        return new UiRect(left, top, modalWidth, modalHeight);
    }

    // Get the shared graph conflict overwrite bounds
    private UiRect sharedGraphConflictOverwriteBounds() {
        UiRect bounds = sharedGraphConflictBounds();
        return new UiRect(bounds.x() + 8, bounds.y() + 36, 68, 18);
    }

    // Get the shared graph conflict increment bounds
    private UiRect sharedGraphConflictIncrementBounds() {
        UiRect overwrite = sharedGraphConflictOverwriteBounds();
        return new UiRect(overwrite.right() + 4, overwrite.y(), 68, overwrite.height());
    }

    // Get the shared graph conflict cancel bounds
    private UiRect sharedGraphConflictCancelBounds() {
        UiRect increment = sharedGraphConflictIncrementBounds();
        return new UiRect(increment.right() + 4, increment.y(), 62, increment.height());
    }

    // Check if the input is connected
    private boolean isInputConnected(AdvancedGraphDocument.Node node, String port) {
        NodePortLayout layout = graphRenderCacheDocument == draft ? graphRenderPortLayouts.get(node.id()) : null;
        if (layout != null) {
            return layout.connectedInputs().contains(port);
        }
        return activeEdges().stream().anyMatch(edge -> edge.toNode().equals(node.id()) && edge.toPort().equals(port));
    }

    // Check if this is set data force write input
    private boolean isSetDataForceWriteInput(AdvancedGraphDocument.Node node, String port) {
        return node != null && "set_block_data".equals(node.type())
                && port != null && !"exec".equals(port) && !"target".equals(port)
                && !isInputConnected(node, port);
    }

    // Check if the set data force write is enabled
    private boolean isSetDataForceWriteEnabled(AdvancedGraphDocument.Node node, String port) {
        return node != null && node.data().getCompound("ForceWriteInputs").getBoolean(port);
    }

    // Toggle forced data writes
    private void toggleSetDataForceWrite(AdvancedGraphDocument.Node node, String port) {
        if (!isSetDataForceWriteInput(node, port)) {
            return;
        }
        CompoundTag forceWriteInputs = node.data().getCompound("ForceWriteInputs");
        if (forceWriteInputs.getBoolean(port)) {
            forceWriteInputs.remove(port);
        } else {
            forceWriteInputs.putBoolean(port, true);
        }
        if (forceWriteInputs.isEmpty()) {
            node.data().remove("ForceWriteInputs");
        } else {
            node.data().put("ForceWriteInputs", forceWriteInputs);
        }
    }

    // Draw the set data force write checkbox
    private void drawSetDataForceWriteCheckbox(GuiGraphics graphics, AdvancedGraphDocument.Node node,
                                                String port, int x, int y) {
        UiRect bounds = setDataForceWriteBounds(node, port, x, y);
        boolean enabled = isSetDataForceWriteEnabled(node, port);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF0B1118);
        graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1,
                enabled ? 0xFF5AA3DD : 0xFF384756);
        if (enabled) {
            graphics.fill(bounds.x() + 2, bounds.y() + 3, bounds.right() - 2, bounds.bottom() - 2, 0xFFFFFFFF);
            graphics.fill(bounds.x() + 3, bounds.y() + 2, bounds.right() - 2, bounds.bottom() - 3, 0xFFFFFFFF);
        }
    }

    // Set the data force write bounds
    private UiRect setDataForceWriteBounds(AdvancedGraphDocument.Node node, String port, int x, int y) {
        int size = Math.max(5, (int) Math.round(8 * zoom));
        int inset = Math.max(1, (int) Math.round(5 * zoom));
        int childIndent = inlineMapPortIndent(node, port, false);
        return new UiRect(x + inset + childIndent, y + inset, size, size);
    }

    // Set the data force write container inset
    private int setDataForceWriteContainerInset(AdvancedGraphDocument.Node node, String port) {
        if (!isSetDataForceWriteInput(node, port)) {
            return 0;
        }
        int checkboxInset = Math.max(1, (int) Math.round(5 * zoom));
        int checkboxSize = Math.max(5, (int) Math.round(8 * zoom));
        int gap = Math.max(2, (int) Math.round(3 * zoom));
        return Math.max(0, checkboxInset + checkboxSize + gap - 5);
    }

    // Get the input control inset
    private int inputControlInset(AdvancedGraphDocument.Node node, String port) {
        return setDataForceWriteContainerInset(node, port) + inlineMapPortIndent(node, port, false);
    }

    // Set the data force write
    private ForceWriteHit setDataForceWriteAt(double mouseX, double mouseY) {
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if (!"set_block_data".equals(node.type())) {
                continue;
            }
            int x = screenX(node.x());
            int bodyY = nodeBodyTop(node, screenY(node.y()));
            if ("curve".equals(node.type())) {
                bodyY += (int) (CURVE_BODY_HEIGHT * zoom);
            }
            if (usesBinding(node)) {
                bodyY += (int) (15 * zoom);
            }
            bodyY += (int) (propertyControlCount(node) * 15 * zoom);
            for (var port : nodeInputs(node).entrySet()) {
                if ("exec".equals(port.getValue())
                        || ("curve".equals(node.type()) && "value".equals(port.getKey()))
                        || !isDataPortVisible(node, port.getKey(), false)) {
                    continue;
                }
                if (isSetDataForceWriteInput(node, port.getKey())
                        && setDataForceWriteBounds(node, port.getKey(), x, bodyY).contains(mouseX, mouseY)) {
                    return new ForceWriteHit(node, port.getKey());
                }
                bodyY += (int) (15 * zoom);
            }
        }
        return null;
    }

    // Get the input value label
    private String inputValueLabel(AdvancedGraphDocument.Node node, String port, String type) {
        if ("frequency".equals(type)) {
            String first = node.data().getString("FrequencyFirst");
            String second = node.data().getString("FrequencySecond");
            return trim(valueOrUnset(first) + " + " + valueOrUnset(second), 18);
        }
        if ("target".equals(type)) {
            String sectionLabel = node.data().getString(AeroworksControllerCompat.GRAPH_SECTION_LABEL_KEY);
            return valueOrUnset(sectionLabel.isBlank() ? node.data().getString("TargetLabel") : sectionLabel);
        }
        if ("variable_set".equals(node.type()) && "type".equals(port)) {
            return humanPort(inputString(node, port, "boolean")) + " v";
        }
        if (isShipTargetPointInput(node, port)) {
            return humanPort(inputString(node, port, "center_of_mass"));
        }
        if (isShipFlightBehaviorInput(node, port)) {
            return shipFlightBehaviorLabel(inputString(
                    node, port, AdvancedGraphCatalog.defaultShipFlightBehavior()));
        }
        if (isShipControlModeInput(node, port)) {
            return AdvancedGraphCatalog.shipControlModeLabel(inputString(
                    node, port, AdvancedGraphCatalog.defaultShipControlMode()));
        }
        return switch (type) {
            case "boolean" -> inputBoolean(node, port) ? "[x]" : "[ ]";
            case "number" -> compactNumber(inputNumber(node, port));
            case "direction" -> valueOrUnset(inputString(node, port, "north"));
            default -> valueOrUnset(inputString(node, port, ""));
        };
    }

    // Get the wired input value label
    private String wiredInputValueLabel(AdvancedGraphDocument.Node node, String port, String type) {
        if (!(menu.getMenuConfigTargetBlockEntity() instanceof AdvancedContraptionControllerBlockEntity controller)) {
            return "...";
        }
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), port);
        if (!hasUnsavedDraft() && liveValue != null) return graphValueLabel(liveValue, type);
        AdvancedGraphLiveValue simulatedValue = simulatedLiveInput(node.id(), port);
        if (simulatedValue != null) return graphValueLabel(simulatedValue, type);
        if (liveValue != null) return graphValueLabel(liveValue, type);
        return graphValueLabel(controller.previewGraphInput(draft, node, port), type);
    }

    // Get the simulated live input
    private AdvancedGraphLiveValue simulatedLiveInput(String nodeId, String port) {
        if (draftSimulationRuntime == null || draft == null) return null;
        AdvancedGraphDocument.Node node = findNode(nodeId);
        AdvancedGraphDocument.Value val = node == null
                ? null : previewDraftInput(node, port);
        return val == null ? null : AdvancedGraphLiveValue.from(val);
    }

    // Get the simulated live output
    private AdvancedGraphLiveValue simulatedLiveOutput(String nodeId, String port) {
        if (draftSimulationRuntime == null || draft == null) return null;
        AdvancedGraphDocument.Node node = findNode(nodeId);
        AdvancedGraphDocument.Value val = node == null
                ? null : previewDraftOutput(node, port);
        return val == null ? null : AdvancedGraphLiveValue.from(val);
    }

    // Get the preview draft input
    private AdvancedGraphDocument.Value previewDraftInput(
            AdvancedGraphDocument.Node node, String port) {
        return activeFunctionId == null
                ? draftSimulationRuntime.previewInput(draft, node, port)
                : draftSimulationRuntime.previewFunctionInput(
                draft, activeFunctionId, node, port);
    }

    // Get the preview draft output
    private AdvancedGraphDocument.Value previewDraftOutput(
            AdvancedGraphDocument.Node node, String port) {
        return activeFunctionId == null
                ? draftSimulationRuntime.previewOutput(draft, node, port)
                : draftSimulationRuntime.previewFunctionOutput(
                draft, activeFunctionId, node, port);
    }

    // Get the graph execution pulse
    private long graphExecutionPulse(AdvancedContraptionControllerBlockEntity controller, String edgeKey) {
        long pulse = controller == null ? Long.MIN_VALUE : controller.getGraphExecutionPulse(edgeKey);
        if (draftSimulationRuntime != null) {
            pulse = Math.max(pulse, draftSimulationRuntime.executionPulse(edgeKey));
        }
        return pulse;
    }

    // Sync the compare ports
    private void synchronizeComparePorts() {
        if (draft == null) return;
        migrateCommentGroups(draft);
        migrateSmoothingNodes(draft);
        synchronizeReroutePorts();
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if ("pid".equals(node.type())
                    && !node.data().getBoolean(AdvancedGraphCatalog.PID_PREVENT_INTEGRAL_WINDUP_TAG)) {
                removePidWindupEdges(node.id());
            }
        }
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if (!"compare".equals(node.type())) continue;
            String matchedType = null;
            for (AdvancedGraphDocument.Edge edge : activeEdges()) {
                if (!edge.toNode().equals(node.id()) || (!"a".equals(edge.toPort()) && !"b".equals(edge.toPort()))) {
                    continue;
                }
                AdvancedGraphDocument.Node src = findNode(edge.fromNode());
                String sourceType = src == null ? null : AdvancedGraphCatalog.outputs(src).get(edge.fromPort());
                if (sourceType == null || "any".equals(sourceType)) continue;
                matchedType = matchedType == null || matchedType.equals(sourceType) ? sourceType : "any";
            }
            CompoundTag inputs = node.data().getCompound("DynamicInputs");
            if (matchedType == null || "any".equals(matchedType)) {
                inputs.remove("a");
                inputs.remove("b");
            } else {
                inputs.putString("a", matchedType);
                inputs.putString("b", matchedType);
            }
            if (inputs.isEmpty()) node.data().remove("DynamicInputs");
            else node.data().put("DynamicInputs", inputs);
            configureCompareOperator(node);
        }
    }

    // Sync the reroute ports
    private void synchronizeReroutePorts() {
        Set<String> visited = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node root : activeNodes()) {
            if (!isReroute(root) || !visited.add(root.id())) continue;
            List<AdvancedGraphDocument.Node> component = new ArrayList<>();
            ArrayDeque<AdvancedGraphDocument.Node> pending = new ArrayDeque<>();
            pending.add(root);
            while (!pending.isEmpty()) {
                AdvancedGraphDocument.Node reroute = pending.removeFirst();
                component.add(reroute);
                for (AdvancedGraphDocument.Edge edge : activeEdges()) {
                    String adjacentId = edge.fromNode().equals(reroute.id()) ? edge.toNode()
                            : edge.toNode().equals(reroute.id()) ? edge.fromNode() : null;
                    AdvancedGraphDocument.Node adjacent = findNode(adjacentId);
                    if (isReroute(adjacent) && visited.add(adjacent.id())) pending.add(adjacent);
                }
            }
            Set<String> componentIds = component.stream().map(AdvancedGraphDocument.Node::id)
                    .collect(java.util.stream.Collectors.toSet());
            String type = null;
            for (AdvancedGraphDocument.Edge edge : activeEdges()) {
                if (!componentIds.contains(edge.toNode()) || componentIds.contains(edge.fromNode())) continue;
                AdvancedGraphDocument.Node src = findNode(edge.fromNode());
                String candidate = src == null ? null : AdvancedGraphCatalog.outputs(src).get(edge.fromPort());
                if (candidate != null && !"any".equals(candidate)) {
                    type = candidate;
                    break;
                }
            }
            if (type == null) {
                for (AdvancedGraphDocument.Edge edge : activeEdges()) {
                    if (!componentIds.contains(edge.fromNode()) || componentIds.contains(edge.toNode())) continue;
                    AdvancedGraphDocument.Node target = findNode(edge.toNode());
                    String candidate = target == null ? null : AdvancedGraphCatalog.inputs(target).get(edge.toPort());
                    if (candidate != null && !"any".equals(candidate)) {
                        type = candidate;
                        break;
                    }
                }
            }
            for (AdvancedGraphDocument.Node reroute : component) {
                if (type == null) {
                    reroute.data().remove("DynamicInputs");
                    reroute.data().remove("DynamicOutputs");
                } else {
                    configureRerouteType(reroute, type);
                }
            }
        }
    }

    // Configure the compare operator
    private void configureCompareOperator(AdvancedGraphDocument.Node node) {
        String operator = normalizeCompareOperator(inputString(node, "operator", "=="));
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.put("operator", graphDefault("string", operator));
        node.data().put("Defaults", defaults);
        putCompareOperatorOptions(node.data());
    }

    // Put the compare operator options
    private static void putCompareOperatorOptions(CompoundTag data) {
        CompoundTag inputOptions = data.getCompound("InputOptions");
        ListTag values = new ListTag();
        for (String option : COMPARE_OPERATOR_OPTIONS) values.add(StringTag.valueOf(option));
        inputOptions.put("operator", values);
        data.put("InputOptions", inputOptions);
    }

    // Normalize the compare operator
    private static String normalizeCompareOperator(String operator) {
        String normalized = operator == null ? "" : operator.trim().toLowerCase(Locale.ROOT);
        if (!List.of("==", "===", "!=", "!==", "~=", "<", "<=", ">", ">=").contains(normalized)) {
            normalized = normalized.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ");
        }
        return switch (normalized) {
            case "equal", "equals", "equal to", "eq", "is", "==", "===" -> "==";
            case "greater", "gt", "greater than", ">" -> ">";
            case "less", "lt", "less than", "<" -> "<";
            case "greater equal", "greater or equal", "greater than or equal",
                 "greater than or equal to", "greater than or equals", "gte", ">=" -> ">=";
            case "less equal", "less or equal", "less than or equal",
                 "less than or equal to", "less than or equals", "lte", "<=" -> "<=";
            case "not equal", "not equals", "not equal to", "ne", "is not", "!=", "!==", "~=" -> "!=";
            default -> "==";
        };
    }

    // Migrate the smoothing nodes
    private void migrateSmoothingNodes(AdvancedGraphDocument graph) {
        if (graph == null) return;
        Set<String> smoothingNodes = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            if (!"smoothing".equals(node.type())) continue;
            smoothingNodes.add(node.id());
            node.data().remove("ResetDelay");
            CompoundTag defaults = node.data().getCompound("Defaults");
            defaults.remove("reset_delay");
            if (!defaults.contains("amount", Tag.TAG_COMPOUND)) {
                defaults.put("amount", graphDefault("number", DEFAULT_SMOOTHING_AMOUNT));
            }
            node.data().put("Defaults", defaults);
            CompoundTag dynamicInputs = node.data().getCompound("DynamicInputs");
            dynamicInputs.remove("reset_delay");
            if (dynamicInputs.isEmpty()) node.data().remove("DynamicInputs");
            else node.data().put("DynamicInputs", dynamicInputs);
            CompoundTag inputOptions = node.data().getCompound("InputOptions");
            inputOptions.remove("reset_delay");
            if (inputOptions.isEmpty()) node.data().remove("InputOptions");
            else node.data().put("InputOptions", inputOptions);
        }
        graph.edges().removeIf(edge -> smoothingNodes.contains(edge.toNode())
                && "reset_delay".equals(edge.toPort()));
    }

    // Apply the input control
    private void applyInputControl(AdvancedGraphDocument.Node node, String port, String type, double mouseX, double mouseY, double x, double width) {
        if ("frequency".equals(type)) {
            openFrequencyEditor(node);
            return;
        }
        if ("target".equals(type)) {
            cycleTarget(node, port);
            return;
        }
        List<String> opts = inputOptions(node, port);
        if (!opts.isEmpty()) {
            openOptionDropdown(node, port, type, x, mouseY + 8, width, opts);
            return;
        }
        checkpoint();
        switch (type) {
            case "boolean" -> putInputDefault(node, port, type, !inputBoolean(node, port));
            case "direction" -> {
                List<String> directions = List.of("north", "east", "south", "west", "up", "down");
                String current = inputString(node, port, "north");
                putInputDefault(node, port, type, directions.get((Math.max(0, directions.indexOf(current)) + 1) % directions.size()));
            }
            case "number" -> {
                double[] range = numberRange(node, port);
                double amount = Mth.clamp((mouseX - x) / Math.max(1.0, width), 0.0, 1.0);
                double val = Mth.lerp(amount, range[0], range[1]);
                putInputDefault(node, port, type, isWholeNumberValue(node, port) ? Math.round(val) : val);
            }
            case "string" -> {
                if (inspectorValue != null) inspectorValue.setFocused(true);
            }
            default -> {
            }
        }
    }

    // Start the slider drag
    private void startSliderDrag(AdvancedGraphDocument.Node node, String port, boolean inspector, double mouseX) {
        checkpoint();
        draggingSliderNode = node.id();
        draggingSliderPort = port;
        draggingInspectorSlider = inspector;
        draggingOutputSlider = false;
        updateDraggedSlider(mouseX);
    }

    // Start the output slider drag
    private void startOutputSliderDrag(
            AdvancedGraphDocument.Node node, String port,
            boolean inspector, double mouseX
    ) {
        checkpoint();
        draggingSliderNode = node.id();
        draggingSliderPort = port;
        draggingInspectorSlider = inspector;
        draggingOutputSlider = true;
        updateDraggedSlider(mouseX);
    }

    // Update the dragged slider
    private void updateDraggedSlider(double mouseX) {
        AdvancedGraphDocument.Node node = findNode(draggingSliderNode);
        if (node == null || draggingSliderPort == null) return;
        SliderTrack track = draggingOutputSlider
                ? draggingInspectorSlider
                        ? inspectorOutputSliderTrack(node, draggingSliderPort)
                        : bodyOutputSliderTrack(node, draggingSliderPort)
                : draggingInspectorSlider
                        ? inspectorSliderTrack(node, draggingSliderPort)
                        : bodySliderTrack(node, draggingSliderPort);
        double amount = Mth.clamp((mouseX - track.left()) / (double) track.width(), 0.0, 1.0);
        double[] range = numberRange(node, draggingSliderPort);
        double val = Mth.lerp(amount, range[0], range[1]);
        if (draggingOutputSlider) {
            AdvancedGraphPortState.setOutputDefault(
                    node, draggingSliderPort, "number",
                    AdvancedGraphDocument.Value.number(val));
            clearGraphRenderCache();
        } else {
            putInputDefault(node, draggingSliderPort, "number",
                    isWholeNumberValue(node, draggingSliderPort)
                            ? Math.round(val) : val);
        }
        syncInspector();
    }

    // Handle the body curve click
    private boolean handleBodyCurveClick(AdvancedGraphDocument.Node node, double mouseX, double mouseY, int btn) {
        if (!"curve".equals(node.type())) return false;
        int x = screenX(node.x()) + 7;
        int y = nodeBodyTop(node, screenY(node.y()));
        int width = (int) (NODE_WIDTH * zoom) - 14;
        int height = (int) (CURVE_BODY_HEIGHT * zoom);
        return handleCurveClick(node, mouseX, mouseY, btn, x, y, width, height, false);
    }

    // Handle the inspector curve click
    private boolean handleInspectorCurveClick(double mouseX, double mouseY, int btn) {
        AdvancedGraphDocument.Node node = selectedNode();
        if (node == null || !"curve".equals(node.type())) return false;
        return handleCurveClick(node, mouseX, mouseY, btn, graphRight() + 10, 163, RIGHT_WIDTH - 20, 116, true);
    }

    // Handle the curve click
    private boolean handleCurveClick(AdvancedGraphDocument.Node node, double mouseX, double mouseY, int btn,
                                     int x, int y, int width, int height, boolean inspector) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        List<CompoundTag> points = curvePoints(node);
        int point = curvePointAt(points, mouseX, mouseY, x, y, width, height);
        if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT && point >= 0) {
            checkpoint();
            points.remove(point);
            writeCurvePoints(node, points);
            return true;
        }
        if (btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        checkpoint();
        if (hasControlDown() && point < 0) {
            CompoundTag inserted = new CompoundTag();
            inserted.putDouble("X", Mth.clamp((mouseX - x) / width, 0.0, 1.0));
            inserted.putDouble("Y", Mth.clamp(1.0 - (mouseY - y) / height, 0.0, 1.0));
            points.add(inserted);
            points.sort(Comparator.comparingDouble(entry -> entry.getDouble("X")));
            point = points.indexOf(inserted);
            writeCurvePoints(node, points);
        }
        if (point >= 0) {
            draggingCurveNode = node.id();
            draggingCurvePoint = point;
            draggingInspectorCurve = inspector;
        }
        return true;
    }

    // Update the dragged curve point
    private void updateDraggedCurvePoint(double mouseX, double mouseY) {
        AdvancedGraphDocument.Node node = findNode(draggingCurveNode);
        if (node == null) return;
        int x = draggingInspectorCurve ? graphRight() + 10 : screenX(node.x()) + 7;
        int y = draggingInspectorCurve ? 163 : nodeBodyTop(node, screenY(node.y()));
        int width = draggingInspectorCurve ? RIGHT_WIDTH - 20 : (int) (NODE_WIDTH * zoom) - 14;
        int height = draggingInspectorCurve ? 116 : (int) (CURVE_BODY_HEIGHT * zoom);
        List<CompoundTag> points = curvePoints(node);
        if (draggingCurvePoint < 0 || draggingCurvePoint >= points.size()) return;
        CompoundTag point = points.get(draggingCurvePoint);
        point.putDouble("X", Mth.clamp((mouseX - x) / width, 0.0, 1.0));
        point.putDouble("Y", Mth.clamp(1.0 - (mouseY - y) / height, 0.0, 1.0));
        points.sort(Comparator.comparingDouble(entry -> entry.getDouble("X")));
        draggingCurvePoint = points.indexOf(point);
        writeCurvePoints(node, points);
    }

    // Get the curve point
    private static int curvePointAt(List<CompoundTag> points, double mouseX, double mouseY,
                                    int x, int y, int width, int height) {
        for (int idx = points.size() - 1; idx >= 0; idx--) {
            CompoundTag point = points.get(idx);
            if (Math.abs(mouseX - curveScreenX(point, x, width)) <= 7
                    && Math.abs(mouseY - curveScreenY(point, y, height)) <= 7) return idx;
        }
        return -1;
    }

    // Get the curve points
    private static List<CompoundTag> curvePoints(AdvancedGraphDocument.Node node) {
        List<CompoundTag> points = new ArrayList<>();
        var tags = node.data().getList("Points", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int idx = 0; idx < tags.size(); idx++) points.add(tags.getCompound(idx).copy());
        if (points.isEmpty()) {
            CompoundTag start = new CompoundTag();
            start.putDouble("X", 0.0);
            start.putDouble("Y", 0.0);
            CompoundTag end = new CompoundTag();
            end.putDouble("X", 1.0);
            end.putDouble("Y", 1.0);
            points.add(start);
            points.add(end);
        }
        points.sort(Comparator.comparingDouble(entry -> entry.getDouble("X")));
        return points;
    }

    // Write the curve points
    private static void writeCurvePoints(AdvancedGraphDocument.Node node, List<CompoundTag> points) {
        net.minecraft.nbt.ListTag tags = new net.minecraft.nbt.ListTag();
        points.stream().sorted(Comparator.comparingDouble(entry -> entry.getDouble("X")))
                .forEach(point -> tags.add(point.copy()));
        node.data().put("Points", tags);
    }

    // Cycle the binding
    private void cycleBinding(AdvancedGraphDocument.Node node) {
        if ("gamepad_input".equals(node.type())) {
            List<AdvancedContraptionControllerMenu.GraphBindingOption> opts = bindingOptions(node);
            if (opts.isEmpty()) {
                return;
            }
            checkpoint();
            String current = node.data().getString("BindingId");
            int idx = 0;
            for (int option = 0; option < opts.size(); option++) {
                if (opts.get(option).id().equals(current)) {
                    idx = (option + 1) % opts.size();
                    break;
                }
            }
            AdvancedContraptionControllerMenu.GraphBindingOption selected = opts.get(idx);
            node.data().putString("BindingId", selected.id());
            node.data().putString("BindingLabel", selected.label());
            syncInspector();
            return;
        }
        checkpoint();
        listeningKeyNode = node.id();
        node.data().putString("BindingLabel", "Press a key...");
    }

    // Open the frequency editor
    private void openFrequencyEditor(AdvancedGraphDocument.Node node) {
        if (linkerOpen && !setLinkerOpen(false)) {
            return;
        }
        frequencyNode = node.id();
        frequencyModalOpen = true;
        contextMenu = null;
        closeOptionDropdown();
        closeMiniBrowser();
        templatePicker = false;
        menu.ghostInventory.setStackInSlot(0, frequencyStack(node, "FrequencyFirst"));
        menu.ghostInventory.setStackInSlot(1, frequencyStack(node, "FrequencySecond"));
        setLinkerOpen(false);
        positionFrequencySlots();
    }

    // Sync the frequency node
    private void syncFrequencyNode() {
        if (!frequencyModalOpen) {
            return;
        }
        AdvancedGraphDocument.Node node = findNode(frequencyNode);
        if (node == null) {
            closeFrequencyEditor(true, false);
            return;
        }
        if (linkerOpen) {
            menu.ghostSlotsActive = false;
            return;
        }
        positionFrequencySlots();
        ItemStack first = copySingle(menu.ghostInventory.getStackInSlot(0));
        ItemStack second = copySingle(menu.ghostInventory.getStackInSlot(1));
        ItemStack storedFirst = frequencyStack(node, "FrequencyFirst");
        ItemStack storedSecond = frequencyStack(node, "FrequencySecond");
        if (!ItemStack.isSameItemSameComponents(first, storedFirst)
                || !ItemStack.isSameItemSameComponents(second, storedSecond)) {
            draftDirty = true;
            putFrequencyStack(node, "FrequencyFirst", first);
            putFrequencyStack(node, "FrequencySecond", second);
            putInputDefault(node, "frequency", "frequency", itemId(first) + "|" + itemId(second));
            syncInspector();
        }
    }

    // Handle the position frequency slots
    private void positionFrequencySlots() {
        UiRect bounds = frequencyModalBounds();
        leftPos = bounds.x() - FREQUENCY_MODAL_BASE_X;
        topPos = bounds.y() - FREQUENCY_MODAL_BASE_Y;
        menu.playerSlotsActive = true;
        menu.ghostSlotsActive = true;
        menu.ghostSlotMask = 0x3;
    }

    // Get the frequency body y
    private int frequencyBodyY(AdvancedGraphDocument.Node node) {
        int y = nodeBodyTop(node, screenY(node.y()));
        if ("curve".equals(node.type())) y += (int) (CURVE_BODY_HEIGHT * zoom);
        if (usesBinding(node)) y += (int) (15 * zoom);
        y += (int) (propertyControlCount(node) * 15 * zoom);
        for (var port : AdvancedGraphCatalog.inputs(node).entrySet()) {
            if ("exec".equals(port.getValue())
                    || ("curve".equals(node.type()) && "value".equals(port.getKey()))) continue;
            if ("frequency".equals(port.getValue()) && !isInputConnected(node, port.getKey())) return y;
            y += (int) (15 * zoom);
        }
        return y;
    }

    // Get the stack from id
    private static ItemStack stackFromId(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(location).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    // Get the frequency stack
    private ItemStack frequencyStack(AdvancedGraphDocument.Node node, String property) {
        if (node == null || property == null || property.isBlank()) {
            return ItemStack.EMPTY;
        }
        if (minecraft != null && minecraft.level != null
                && node.data().contains(property + "Stack", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            ItemStack stored = ItemStack.parseOptional(minecraft.level.registryAccess(),
                    node.data().getCompound(property + "Stack"));
            if (!stored.isEmpty()) {
                return copySingle(stored);
            }
        }
        return stackFromId(node.data().getString(property));
    }

    // Put the frequency stack
    private void putFrequencyStack(AdvancedGraphDocument.Node node, String property, ItemStack stack) {
        if (node == null || property == null || property.isBlank()) {
            return;
        }
        ItemStack copy = copySingle(stack);
        node.data().putString(property, itemId(copy));
        if (copy.isEmpty() || minecraft == null || minecraft.level == null) {
            node.data().remove(property + "Stack");
            return;
        }
        node.data().put(property + "Stack", copy.saveOptional(minecraft.level.registryAccess()));
    }

    // Get the item id
    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    // Copy one item
    private static ItemStack copySingle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                       LINKER / SYNC
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cycle the target
    private void cycleTarget(AdvancedGraphDocument.Node node, String port) {
        List<ControllerDiscoveryNode> opts = new ArrayList<>(graphTargetOptions(node));
        opts.addAll(graphScmTargetOptions(node));
        if (opts.isEmpty()) {
            setBlockBrowserOpen(true);
            return;
        }
        int current = -1;
        for (int i = 0; i < opts.size(); i++) if (opts.get(i).nodeId().equals(node.data().getString("Target"))) current = i;
        ControllerDiscoveryNode target = opts.get((current + 1) % opts.size());
        checkpoint();
        node.data().putString("Target", target.nodeId());
        node.data().putString("TargetLabel", target.label().isBlank() ? target.nodeId() : target.label());
        node.data().put("TargetData", target.toTag());
        node.data().remove(AeroworksControllerCompat.GRAPH_SECTION_ID_KEY);
        node.data().remove(AeroworksControllerCompat.GRAPH_SECTION_LABEL_KEY);
        configureDataPorts(node, target);
        putInputDefault(node, port, "target", target.nodeId());
    }

    // Configure the data ports
    private void configureDataPorts(AdvancedGraphDocument.Node node, ControllerDiscoveryNode target) {
        boolean getData = "get_block_data".equals(node.type());
        boolean setData = "set_block_data".equals(node.type());
        boolean directInput = "discovered_target_input".equals(node.type()) || "linker_face_input".equals(node.type());
        boolean directOutput = "direct_target_output".equals(node.type()) || "linker_face_output".equals(node.type());
        boolean displayMode = "acc_display_mode".equals(node.type());
        if (displayMode) {
            AdvancedContraptionControllerBlockEntity activeController =
                    menu.getMenuConfigTargetBlockEntity();
            if (activeController != null) {
                String mode = activeController.getGraphTargetData(node, "display_mode").asString();
                putInputDefault(node, "mode", "string",
                        AccDisplayBlockEntity.normalizeDisplayMode(mode));
            }
            return;
        }
        if (!getData && !setData && !directInput && !directOutput) return;
        CompoundTag previousDynamicInputs = node.data().getCompound("DynamicInputs").copy();
        if (minecraft == null || minecraft.level == null || target == null || target.blockPos() == null) return;
        AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(node, target);
        boolean writable = setData || directOutput;
        if (ContraptionDiagramControllerCompat.isTarget(target)) {
            CompoundTag ports = getData && !writable
                    ? ContraptionDiagramControllerCompat.readablePorts()
                    : new CompoundTag();
            AdvancedContraptionControllerBlockEntity.clearDataPortGroups(node);
            node.data().remove("DynamicInputs");
            node.data().remove("DynamicOutputs");
            node.data().remove("OutputLabels");
            node.data().put(writable ? "DynamicInputs" : "DynamicOutputs", ports);
            node.data().put(writable ? "InputOptions" : "OutputOptions", new CompoundTag());
            AdvancedContraptionControllerBlockEntity.configureLinkerFaceInputOptions(node, target);
            AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(node, target);
            removeEdgesForMissingPorts(node, true);
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity blockEntity =
                SimulatedHelper.findBlockEntity(minecraft.level, target.subLevelId(), target.blockPos());
        net.minecraft.world.level.Level targetLevel = blockEntity != null && blockEntity.getLevel() != null
                ? blockEntity.getLevel() : minecraft.level;
        net.minecraft.core.BlockPos targetPos = blockEntity == null ? target.blockPos() : blockEntity.getBlockPos();
        if ((getData || setData) && targetLevel.getBlockState(targetPos).getBlock()
                instanceof com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerPlaneBlock) {
            List<ContraptionNetworkLinkerData.FaceOption> faces =
                    ContraptionNetworkLinkerData.faceOptionsForNode(target);
            List<net.minecraft.core.Direction> availableFaces = faces.stream()
                    .map(ContraptionNetworkLinkerData.FaceOption::face)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            net.minecraft.core.Direction side = net.minecraft.core.Direction.byName(
                    inputString(node, "face", ""));
            if (side == null && availableFaces.size() == 1) {
                side = availableFaces.getFirst();
            }
            if (side == null || !availableFaces.contains(side)) {
                return;
            }
            net.minecraft.core.BlockPos attachedPos = targetPos.relative(side.getOpposite());
            if (!targetLevel.isLoaded(attachedPos)
                    || targetLevel.getBlockState(attachedPos).isAir()) {
                return;
            }
            targetPos = attachedPos;
        }
        boolean schemaResolved = targetLevel.isLoaded(targetPos)
                && !targetLevel.getBlockState(targetPos).isAir();
        if (!schemaResolved) {
            return;
        }
        blockEntity = targetLevel.getBlockEntity(targetPos);
        if ((getData || setData) && !com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapterRegistry
                .isDataSchemaReady(blockEntity)) {
            return;
        }
        String aeroworksSection = getData || setData
                ? AdvancedContraptionControllerBlockEntity.configureAeroworksGraphSection(node, blockEntity)
                : null;
        CompoundTag ports = getData || setData
                ? AdvancedContraptionControllerBlockEntity.graphDataPorts(
                        targetLevel, targetPos, writable, aeroworksSection)
                : AdvancedContraptionControllerBlockEntity.graphDirectAxisPorts(blockEntity, writable);
        if (getData || setData) {
            ports = AdvancedContraptionControllerBlockEntity.configureDataPortGroups(node, ports, blockEntity, writable);
        } else {
            AdvancedContraptionControllerBlockEntity.clearDataPortGroups(node);
        }
        AdvancedContraptionControllerBlockEntity.restoreInlineMapPorts(node, ports, !writable);
        CompoundTag options = AdvancedContraptionControllerBlockEntity.graphDataPortOptions(
                targetLevel, targetPos, writable);
        CompoundTag labels = getData && !writable
                ? AdvancedContraptionControllerBlockEntity.graphReadableDataPortLabels(
                targetLevel, targetPos)
                : new CompoundTag();

        node.data().remove("DynamicInputs");
        node.data().remove("DynamicOutputs");
        node.data().remove("OutputLabels");
        node.data().put(writable ? "DynamicInputs" : "DynamicOutputs", ports);
        node.data().put(writable ? "InputOptions" : "OutputOptions", options);
        if (writable) {
            AdvancedContraptionControllerBlockEntity.restoreInlineMapInputOptions(node);
        }
        if (!labels.isEmpty()) {
            node.data().put("OutputLabels", labels);
        }
        AdvancedContraptionControllerBlockEntity.configureLinkerFaceInputOptions(node, target);
        if (writable) {
            prefillTargetInputs(node, target, ports, previousDynamicInputs, directOutput);
        }
        AdvancedContraptionControllerBlockEntity.configureDataTargetFaceOptions(node, target);
        removeEdgesForMissingPorts(node, true);
    }

    // Get the aeroworks sections for target
    private List<AeroworksControllerCompat.ConsoleSection> aeroworksSectionsForTarget(
            AdvancedGraphDocument.Node node, ControllerDiscoveryNode target) {
        if (node == null || target == null || target.blockPos() == null
                || (!"get_block_data".equals(node.type()) && !"set_block_data".equals(node.type()))
                || !AeroworksControllerCompat.CONTROL_DESK.equalsIgnoreCase(target.blockId())
                || minecraft == null || minecraft.level == null) {
            return List.of();
        }
        net.minecraft.world.level.block.entity.BlockEntity blockEntity =
                SimulatedHelper.findBlockEntity(minecraft.level, target.subLevelId(), target.blockPos());
        return AeroworksControllerCompat.consoleSections(blockEntity);
    }

    // Prefill the target inputs
    private void prefillTargetInputs(AdvancedGraphDocument.Node node, ControllerDiscoveryNode target,
                                     CompoundTag ports, CompoundTag previousDynamicInputs,
                                     boolean directOutput) {
        if (node == null || target == null || ports == null) return;
        String targetId = target.nodeId();
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if (controller == null) return;

        CompoundTag defaults = node.data().getCompound("Defaults");
        CompoundTag prefilledInputs = new CompoundTag();
        for (String port : previousDynamicInputs.getAllKeys()) {
            if (!"face".equals(port)) {
                defaults.remove(port);
            }
        }
        for (String port : ports.getAllKeys()) {
            String type = ports.getString(port);
            AdvancedGraphDocument.Value val = controller.getGraphTargetData(node, port);
            defaults.put(port, graphDefault(type, val));
            prefilledInputs.putBoolean(port, true);
        }
        if (directOutput) {
            defaults.put("value", graphDefault("number", controller.getGraphTargetData(node, "direct_signal")));
        }
        node.data().put("Defaults", defaults);
        node.data().put("PrefilledInputs", prefilledInputs);
        node.data().putString("PrefilledTarget", targetId);
    }

    // Remove edges only after a complete target schema was resolved
    private void removeEdgesForMissingPorts(AdvancedGraphDocument.Node node,
                                            boolean schemaResolved) {
        if (node == null || !schemaResolved) return;
        Map<String, String> inputs = AdvancedGraphCatalog.inputs(node);
        Map<String, String> outputs = AdvancedGraphCatalog.outputs(node);
        activeEdges().removeIf(edge -> (edge.fromNode().equals(node.id()) && !outputs.containsKey(edge.fromPort()))
                || (edge.toNode().equals(node.id()) && !inputs.containsKey(edge.toPort())));
    }

    // Get the graph target options
    private List<ControllerDiscoveryNode> graphTargetOptions(AdvancedGraphDocument.Node node) {
        List<ControllerDiscoveryNode> opts = liveGraphTargets.isEmpty()
                ? sortedGraphTargets(currentGraphTargetSeeds())
                : List.copyOf(liveGraphTargets);
        if (node == null) return opts;
        if ("acc_display_crn".equals(node.type())) {
            opts = opts.stream().filter(target ->
                    isAccDisplayTarget(target) || isDisplayAdapterTarget(target)).toList();
        } else if (node.type().startsWith("acc_display_") || isAccDisplayWidgetType(node.type())) {
            opts = opts.stream().filter(AdvancedContraptionControllerScreen::isAccDisplayTarget).toList();
        } else if ("linker_face_input".equals(node.type()) || "discovered_target_input".equals(node.type())) {
            opts = opts.stream().filter(target -> target.kind() != ControllerDiscoveryKind.LINKER_FACE_OUTPUT).toList();
        } else if ("linker_face_output".equals(node.type()) || "direct_target_output".equals(node.type())
                || "set_block_data".equals(node.type())) {
            opts = opts.stream().filter(target -> target.kind() != ControllerDiscoveryKind.LINKER_FACE_INPUT).toList();
        }
        return opts;
    }

    // Get the graph display source options
    private List<ControllerDiscoveryNode> graphDisplaySourceOptions() {
        List<ControllerDiscoveryNode> opts = liveGraphTargets.isEmpty()
                ? sortedGraphTargets(currentGraphTargetSeeds())
                : List.copyOf(liveGraphTargets);
        return opts.stream().filter(AdvancedContraptionControllerScreen::isDisplayAdapterTarget).toList();
    }

    // Get the graph SCM target options
    private List<ControllerDiscoveryNode> graphScmTargetOptions(
            AdvancedGraphDocument.Node node) {
        if (!supportsScmBlockTarget(node)) return List.of();
        ItemStack linker = menu.getCurrentLinkerStack();
        if (linker == null || linker.isEmpty()) return List.of();
        return sortedGraphTargets(ContraptionNetworkLinkerData.scmDiscoveryNodes(linker));
    }

    // Get the current graph target seeds
    private List<ControllerDiscoveryNode> currentGraphTargetSeeds() {
        List<ControllerDiscoveryNode> targets = new ArrayList<>(menu.getGraphTargetOptions());
        ItemStack linker = menu.getCurrentLinkerStack();
        if (linker != null && !linker.isEmpty()) {
            targets.addAll(ContraptionNetworkLinkerData.toDiscoveryNodes(linker));
        }
        return targets;
    }

    // Refresh the graph targets from menu
    private void refreshGraphTargetsFromMenu() {
        List<ControllerDiscoveryNode> merged = mergeGraphTargets(currentGraphTargetSeeds(), liveGraphTargets);
        liveGraphTargets.clear();
        liveGraphTargets.addAll(sortedGraphTargets(merged));
    }

    // Request the discovery refresh
    private void requestDiscoveryRefresh() {
        BlockPos controllerPos = menu.getContentPos();
        if (controllerPos == null) {
            return;
        }
        PacketDistributor.sendToServer(new AnalogueContraptionControllerDiscoveryRequestPayload(controllerPos, menu.getContentSubLevelId()));
    }

    // Request the shared graphs
    private void requestSharedGraphs() {
        send("shared_graphs", "");
    }

    // Save the current graph locally
    private void saveCurrentGraphLocally(boolean uploadToServer) {
        String name = linkerShareName == null ? "" : linkerShareName.getValue().trim();
        if (name.isBlank()) {
            showLinkerStatus("A graph name is required");
            showGraphToast("A graph name is required", GraphActionToastSeverity.ERROR);
            return;
        }
        sharedGraphConflictName = "";
        sharedGraphSaveTarget = uploadToServer
                ? SharedGraphSaveTarget.LOCAL_AND_SERVER : SharedGraphSaveTarget.LOCAL;
        saveLocalSharedGraph(name, ControllerManifestStore.SharedGraphSaveMode.REJECT);
    }

    // Save the local shared graph
    private void saveLocalSharedGraph(String name, ControllerManifestStore.SharedGraphSaveMode mode) {
        AdvancedGraphDocument graphToShare = draft == null ? new AdvancedGraphDocument() : draft.copy();
        graphToShare.removeUnusedVariables();
        CompoundTag sharedRoot = ContraptionNetworkLinkerData.sharedRootWithGraph(name, graphToShare);
        ControllerManifestStore.SharedGraphSaveResult res =
                ControllerManifestStore.saveSharedGraph(name, sharedRoot, mode);
        if (res.status() == ControllerManifestStore.SharedGraphSaveStatus.EXISTS) {
            sharedGraphConflictName = name;
            if (linkerShareName != null) linkerShareName.setFocused(false);
            setFocused(null);
            return;
        }
        if (!res.saved()) {
            sharedGraphSaveTarget = SharedGraphSaveTarget.NONE;
            showLinkerStatus("Failed to save shared graph locally");
            showGraphToast("Failed to save shared graph locally", GraphActionToastSeverity.ERROR);
            return;
        }

        sharedGraphEntries.clear();
        sharedGraphEntries.addAll(ControllerManifestStore.listSharedGraphs());
        selectedSharedGraphIndex = sharedGraphEntries.stream()
                .map(ControllerManifestStore.SharedGraphEntry::id)
                .toList().indexOf(res.id());
        showLinkerStatus("Shared graph saved locally as " + res.id());
        showGraphToast("Graph saved locally", GraphActionToastSeverity.SUCCESS);
        if (sharedGraphSaveTarget == SharedGraphSaveTarget.LOCAL_AND_SERVER) {
            sharedGraphSaveTarget = SharedGraphSaveTarget.SERVER;
            send("share_graph", res.id());
        } else {
            sharedGraphSaveTarget = SharedGraphSaveTarget.NONE;
        }
    }

    // Resolve the share conflict
    private void resolveShareConflict(String name, ControllerManifestStore.SharedGraphSaveMode mode) {
        if (sharedGraphSaveTarget == SharedGraphSaveTarget.LOCAL
                || sharedGraphSaveTarget == SharedGraphSaveTarget.LOCAL_AND_SERVER) {
            saveLocalSharedGraph(name, mode);
            return;
        }
        if (sharedGraphSaveTarget == SharedGraphSaveTarget.SERVER) {
            send(mode == ControllerManifestStore.SharedGraphSaveMode.OVERWRITE
                    ? "share_graph_overwrite" : "share_graph_increment", name);
            return;
        }
        sharedGraphSaveTarget = SharedGraphSaveTarget.SERVER;
        send(mode == ControllerManifestStore.SharedGraphSaveMode.OVERWRITE
                ? "share_graph_overwrite" : "share_graph_increment", name);
    }

    // Check if this can upload shared graph
    private boolean canUploadSharedGraph() {
        return minecraft != null && minecraft.getCurrentServer() != null;
    }

    // Open the shared graphs dir
    private void openSharedGraphsDir() {
        Path dir = ControllerManifestStore.sharedGraphsDirectory();
        try {
            Files.createDirectories(dir);
            Util.getPlatform().openFile(dir.toFile());
        } catch (IOException err) {
            showLinkerStatus("Failed to open the shared graphs directory");
            showGraphToast("Failed to open shared graphs directory", GraphActionToastSeverity.ERROR);
        }
    }

    // Share the current graph
    private void shareCurrentGraph() {
        String name = linkerShareName == null ? "" : linkerShareName.getValue().trim();
        if (name.isBlank()) name = title == null ? "Contraption Graph" : title.getString();
        publicSharePending = true;
        showLinkerStatus("Uploading graph...");
        send("upload_public_share", name);
    }

    // Load the selected shared graph
    private void loadSelectedSharedGraph() {
        if (selectedSharedGraphIndex < 0 || selectedSharedGraphIndex >= sharedGraphEntries.size()) {
            showLinkerStatus("Select a shared graph");
            return;
        }
        send("load_shared_graph", sharedGraphEntries.get(selectedSharedGraphIndex).id());
    }

    // Show the linker status
    private void showLinkerStatus(String msg) {
        linkerStatusMessage = msg == null ? "" : msg;
        linkerStatusTicks = linkerStatusMessage.isBlank() ? 0 : 80;
        if (minecraft != null && minecraft.player != null && !linkerStatusMessage.isBlank()) {
            minecraft.player.displayClientMessage(Component.literal(linkerStatusMessage), true);
        }
    }

    // Show the graph toast
    private void showGraphToast(String msg, GraphActionToastSeverity severity) {
        if (msg == null || msg.isBlank()) return;
        GraphActionToast toast = new GraphActionToast(Component.literal(msg), severity);
        if (graphActionToast == null) displayGraphActionToast(toast);
        else graphActionToastQueue.addLast(toast);
    }

    // Apply the discovery results
    public static void applyDiscoveryResults(BlockPos controllerPos, UUID controllerSubLevelId, List<ControllerDiscoveryNode> nodes) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof AdvancedContraptionControllerScreen screen
                && screen.matchesController(controllerPos, controllerSubLevelId)) {
            screen.applyProjectionDiscoveryResults(nodes);
        }
        AccDisplayGuiProjection.applyDiscoveryResults(controllerPos, controllerSubLevelId, nodes);
    }

    // Apply the graph action result
    public static void applyGraphActionResult(BlockPos controllerPos, UUID controllerSubLevelId, long requestId,
                                              boolean success, String message, int serverRevision,
                                              boolean saveAttempted, boolean graphSaved,
                                              List<AdvancedGraphValidator.Diagnostic> diagnostics) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof AdvancedContraptionControllerScreen screen
                && screen.matchesController(controllerPos, controllerSubLevelId)) {
            screen.applyProjectionGraphActionResult(requestId, success, message,
                    serverRevision, saveAttempted, graphSaved, diagnostics);
        }
        AccDisplayGuiProjection.applyGraphActionResult(controllerPos, controllerSubLevelId,
                requestId, success, message, serverRevision, saveAttempted, graphSaved, diagnostics);
    }

    // Apply the graph history
    public static void applyGraphHistory(BlockPos controllerPos, UUID controllerSubLevelId,
                                         List<AdvancedGraphVersionHistory.Entry> entries,
                                         String message, CompoundTag restoredGraph) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof AdvancedContraptionControllerScreen screen
                && screen.matchesController(controllerPos, controllerSubLevelId)) {
            screen.applyProjectionGraphHistory(entries, message, restoredGraph);
        }
        AccDisplayGuiProjection.applyGraphHistory(controllerPos, controllerSubLevelId,
                entries, message, restoredGraph);
    }

    // Apply the projection graph history
    void applyProjectionGraphHistory(List<AdvancedGraphVersionHistory.Entry> entries,
                                     String message, CompoundTag restoredGraph) {
        graphHistoryEntries.clear();
        if (entries != null) {
            graphHistoryEntries.addAll(entries);
        }
        graphHistoryScroll = Mth.clamp(graphHistoryScroll, 0,
                Math.max(0, graphHistoryEntries.size() - GRAPH_HISTORY_VISIBLE_ROWS));
        if (restoredGraph != null && !restoredGraph.isEmpty()) {
            AdvancedGraphDocument restored = AdvancedGraphDocument.fromTag(restoredGraph);
            checkpoint();
            draft = restored;
            savedDraft = restored.copy();
            draftDirty = false;
            restoreViewport();
            synchronizeComparePorts();
            clearSelection();
            failedNodes.clear();
            failedEdges.clear();
        }
        if (message != null && !message.isBlank() && graphActionToast == null) {
            displayGraphActionToast(new GraphActionToast(Component.literal(message),
                    GraphActionToastSeverity.SUCCESS));
        }
    }

    // Apply the shared graph manifests
    public static void applySharedGraphManifests(BlockPos controllerPos, UUID controllerSubLevelId,
                                                 List<ControllerManifestStore.SharedGraphEntry> entries,
                                                 String message, String conflictingName, CompoundTag graphTag) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof AdvancedContraptionControllerScreen screen
                && screen.matchesController(controllerPos, controllerSubLevelId)) {
            screen.applyProjectionSharedGraphManifests(entries, message, conflictingName, graphTag);
        }
        AccDisplayGuiProjection.applySharedGraphManifests(controllerPos, controllerSubLevelId,
                entries, message, conflictingName, graphTag);
    }

    // Apply the projection shared graph manifests
    void applyProjectionSharedGraphManifests(List<ControllerManifestStore.SharedGraphEntry> entries,
                                             String message, String conflictingName,
                                             CompoundTag graphTag) {
        sharedGraphEntries.clear();
        if (entries != null) {
            sharedGraphEntries.addAll(entries);
        }
        sharedGraphScroll = Mth.clamp(sharedGraphScroll, 0,
                Math.max(0, sharedGraphEntries.size() - 4));
        if (selectedSharedGraphIndex >= sharedGraphEntries.size()) {
            selectedSharedGraphIndex = sharedGraphEntries.isEmpty() ? -1 : sharedGraphEntries.size() - 1;
        }
        if (conflictingName != null && !conflictingName.isBlank()) {
            sharedGraphConflictName = conflictingName.trim();
            if (sharedGraphSaveTarget == SharedGraphSaveTarget.NONE) {
                sharedGraphSaveTarget = SharedGraphSaveTarget.SERVER;
            }
            if (linkerShareName != null) {
                linkerShareName.setFocused(false);
            }
            setFocused(null);
        }
        if (graphTag != null && !graphTag.isEmpty()) {
            if (frequencyModalOpen && !closeFrequencyEditor()) {
                return;
            }
            draft = AdvancedGraphDocument.fromTag(graphTag);
            restoreViewport();
            synchronizeComparePorts();
            savedDraft = draft.copy();
            draftDirty = false;
            syncInspector();
        }
        if (message != null && !message.isBlank()) {
            showLinkerStatus(message);
            if (message.startsWith("Shared graph saved")) {
                sharedGraphSaveTarget = SharedGraphSaveTarget.NONE;
                showGraphToast("Graph saved locally and uploaded",
                        GraphActionToastSeverity.SUCCESS);
            } else if (message.startsWith("Failed to save")) {
                sharedGraphSaveTarget = SharedGraphSaveTarget.NONE;
                showGraphToast(message, GraphActionToastSeverity.ERROR);
            }
        }
    }

    // Apply the public graph share
    public static void applyPublicGraphShare(BlockPos controllerPos, UUID controllerSubLevelId,
                                             boolean available, boolean completed, boolean success,
                                             String message, String url) {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof AdvancedContraptionControllerScreen screen
                && screen.matchesController(controllerPos, controllerSubLevelId)) {
            screen.applyProjectionPublicGraphShare(available, completed, success, message, url);
        }
        AccDisplayGuiProjection.applyPublicGraphShare(controllerPos, controllerSubLevelId,
                available, completed, success, message, url);
    }

    // Apply the projection public graph share
    void applyProjectionPublicGraphShare(boolean available, boolean completed,
                                         boolean success, String message, String url) {
        publicShareAvailable = available;
        publicSharePending = false;
        if (!completed) return;
        if (success && url != null && !url.isBlank()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(url);
            showLinkerStatus("Share link copied: " + url);
            showGraphToast("Graph uploaded; share link copied to clipboard",
                    GraphActionToastSeverity.SUCCESS);
        } else {
            String failure = message == null || message.isBlank()
                    ? "Public graph sharing is unavailable" : message;
            showLinkerStatus(failure);
            showGraphToast(failure, GraphActionToastSeverity.ERROR);
        }
    }

    // Check if this matches controller
    private boolean matchesController(BlockPos controllerPos, UUID controllerSubLevelId) {
        return Objects.equals(menu.getContentPos(), controllerPos)
                && Objects.equals(menu.getContentSubLevelId(), controllerSubLevelId);
    }

    // Apply the projection discovery results
    void applyProjectionDiscoveryResults(List<ControllerDiscoveryNode> nodes) {
        liveGraphTargets.clear();
        liveGraphTargets.addAll(sortedGraphTargets(mergeGraphTargets(currentGraphTargetSeeds(), nodes)));
    }

    // Apply the projection graph action result
    void applyProjectionGraphActionResult(long requestId, boolean success, String message,
                                          int serverRevision, boolean saveAttempted,
                                          boolean graphSaved,
                                          List<AdvancedGraphValidator.Diagnostic> diagnostics) {
        queueGraphActionResult(requestId, success, message,
                serverRevision, saveAttempted, graphSaved, diagnostics);
    }

    // Merge the graph targets
    private static List<ControllerDiscoveryNode> mergeGraphTargets(List<ControllerDiscoveryNode> primary,
                                                                   List<ControllerDiscoveryNode> secondary) {
        Map<String, ControllerDiscoveryNode> merged = new LinkedHashMap<>();
        addGraphTargets(merged, primary);
        addGraphTargets(merged, secondary);
        return List.copyOf(merged.values());
    }

    // Add the graph targets
    private static void addGraphTargets(Map<String, ControllerDiscoveryNode> merged, List<ControllerDiscoveryNode> nodes) {
        if (nodes == null) return;
        for (ControllerDiscoveryNode node : nodes) {
            if (node != null && node.isValid()) {
                merged.put(node.nodeId(), node);
            }
        }
    }

    // Get the sorted graph targets
    private static List<ControllerDiscoveryNode> sortedGraphTargets(List<ControllerDiscoveryNode> nodes) {
        return nodes.stream()
                .filter(node -> node != null && node.isValid())
                .sorted(Comparator.comparing((ControllerDiscoveryNode node) -> node.groupId() == null ? "" : node.groupId())
                        .thenComparing(ControllerDiscoveryNode::label)
                        .thenComparing(ControllerDiscoveryNode::nodeId))
                .toList();
    }

    // Detect the linker slot changes
    private void detectLinkerSlotChanges() {
        ItemStack currentGogglesOutput = copySingle(menu.getGogglesOutputStack());
        if (!ItemStack.isSameItemSameComponents(currentGogglesOutput, lastObservedGogglesOutput)) {
            boolean linked = lastObservedGogglesOutput.isEmpty() && !currentGogglesOutput.isEmpty();
            lastObservedGogglesOutput = currentGogglesOutput;
            if (linked) {
                showGraphToast("Goggles linked to controller", GraphActionToastSeverity.SUCCESS);
            }
        }
        ItemStack currentLinker = copySingle(menu.getCurrentLinkerStack());
        if (ItemStack.isSameItemSameComponents(currentLinker, lastObservedLinkerStack)) {
            return;
        }
        lastObservedLinkerStack = currentLinker;
        refreshGraphTargetsFromMenu();
        requestDiscoveryRefresh();
    }

    // Get the input options
    private List<String> inputOptions(AdvancedGraphDocument.Node node, String port) {
        if (isShipTargetPointInput(node, port)) {
            return AdvancedGraphCatalog.shipTargetPointOptions();
        }
        if (isShipFlightBehaviorInput(node, port)) {
            return AdvancedGraphCatalog.shipFlightBehaviorOptions();
        }
        if (isShipControlModeInput(node, port)) {
            return AdvancedGraphCatalog.shipControlModeOptions();
        }
        if (node != null && "play_sound".equals(node.type()) && "sound".equals(port)) {
            if (soundEventOptions.isEmpty()) {
                soundEventOptions = BuiltInRegistries.SOUND_EVENT.keySet().stream()
                        .map(ResourceLocation::toString)
                        .sorted()
                        .toList();
            }
            return soundEventOptions;
        }
        List<String> opts = new ArrayList<>();
        CompoundTag configuredOptions = node.data().getCompound("InputOptions");
        var values = configuredOptions.getList(port, net.minecraft.nbt.Tag.TAG_STRING);
        if (values.isEmpty()) {
            CompoundTag mapping = node.data().getCompound(AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG)
                    .getCompound(port);
            String sourceKey = mapping.getString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG);
            if (!sourceKey.isBlank()) {
                values = configuredOptions.getList(sourceKey, net.minecraft.nbt.Tag.TAG_STRING);
            }
        }
        for (int idx = 0; idx < values.size(); idx++) opts.add(values.getString(idx));
        return opts;
    }

    // Check if this is ship target point input
    private static boolean isShipTargetPointInput(
            AdvancedGraphDocument.Node node,
            String port
    ) {
        return node != null && "target_point".equals(port)
                && ("ship_dock".equals(node.type())
                || "ship_navigate".equals(node.type()));
    }

    // Check if this is ship flight behavior input
    private static boolean isShipFlightBehaviorInput(
            AdvancedGraphDocument.Node node,
            String port
    ) {
        return node != null && "ship_flight_behavior".equals(node.type())
                && "behavior".equals(port);
    }

    // Check if this is ship control mode input
    private static boolean isShipControlModeInput(
            AdvancedGraphDocument.Node node,
            String port
    ) {
        return node != null && "ship_initialize".equals(node.type())
                && "control_mode".equals(port);
    }

    // Get the ship flight behavior label
    private static String shipFlightBehaviorLabel(String behaviorId) {
        return Component.translatable(
                AdvancedGraphCatalog.shipFlightBehaviorTranslationKey(behaviorId)).getString();
    }

    // Parse the number
    private static double parseNumber(String val) {
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    // Get the numeric range
    private double[] numberRange(AdvancedGraphDocument.Node node, String port) {
        if (isRedstoneValue(node, port)) return new double[]{0, 15};
        if (node.type().startsWith("acc_display_")) {
            return switch (port) {
                case "x", "width" -> new double[]{"width".equals(port) ? 1 : 0, 320};
                case "y", "height" -> new double[]{"height".equals(port) ? 1 : 0, 180};
                case "scale" -> new double[]{0.1D, 4.0D};
                case "rotation" -> new double[]{-180.0D, 180.0D};
                default -> new double[]{-1.0D, 1.0D};
            };
        }
        if ("list_get".equals(node.type()) && "index".equals(port)) {
            int size = listInputSize(node);
            return new double[]{0, Math.max(0, size - 1)};
        }
        if ("substring".equals(node.type())
                && ("start_index".equals(port) || "end_index".equals(port))) {
            return new double[]{0, stringInputLength(node)};
        }
        if ("switch".equals(node.type()) && "selector".equals(port)) {
            return new double[]{0, Math.max(0, executionOutputCount(node) - 1)};
        }
        if (isRotationSpeedControllerSpeed(node, port)) {
            return new double[]{0, Math.max(1, AllConfigs.server().kinetics.maxRotationSpeed.get())};
        }
        if (AdvancedGraphCatalog.isShipSpeedPort(node, port)) {
            return new double[]{0, 10};
        }
        if ("ship_telemetry".equals(node.type())
                && "collision_detection_distance".equals(port)) {
            return new double[]{0, 256};
        }
        if ("ship_telemetry".equals(node.type())
                && "collision_poll_rate".equals(port)) {
            return new double[]{0, AdvancedGraphCatalog.MAX_COLLISION_POLL_RATE};
        }
        if ("amount".equals(port) && node.type().startsWith("ship_")) {
            return new double[]{-1, 1};
        }
        if ("send_named_controller_event".equals(node.type()) && "distance".equals(port)) {
            return new double[]{0, Math.max(1, AllConfigs.server().logistics.linkRange.get())};
        }
        if ("ship_follow".equals(node.type()) && "follow_distance".equals(port)) {
            return new double[]{0, 256};
        }
        if ("random_int".equals(node.type()) && "max".equals(port)) return new double[]{1, 256};
        if ("random_int_in_range".equals(node.type())) return new double[]{-256, 256};
        if ("mouse_input".equals(node.type()) && "timeout".equals(port)) return new double[]{1, 1200};
        if ("lqr_controller".equals(node.type())) {
            return switch (port) {
                case "gain" -> new double[]{0, 100};
                case "minimum", "maximum", "target", "actual", "feed_forward" ->
                        new double[]{-100, 100};
                default -> new double[]{-1, 1};
            };
        }
        if ("adrc".equals(node.type())) {
            return switch (port) {
                case "delta_time" -> new double[]{0.001D, 1.0D};
                case "controller_bandwidth", "observer_bandwidth" -> new double[]{0.01D, 100.0D};
                case "plant_gain", "output_limit" -> new double[]{0.01D, 1000.0D};
                default -> new double[]{-100.0D, 100.0D};
            };
        }
        return switch (port) {
            case "ticks", "period", "duration", "count", "iterations", "index" -> new double[]{1, 256};
            case "pivot_angle", "servo_input_angle", "min_angle", "max_angle" -> new double[]{-180, 180};
            case "p", "i", "d" -> new double[]{0, 10};
            case "amount", "rise", "fall", "step" -> new double[]{0, 1};
            case "speed" -> new double[]{0.001, 1};
            default -> new double[]{-1, 1};
        };
    }

    // Check if this is a redstone value
    private static boolean isRedstoneValue(AdvancedGraphDocument.Node node, String port) {
        return "value".equals(port) && (node.type().startsWith("local_redstone")
                || node.type().startsWith("wireless_frequency")
                || node.type().startsWith("linker_face")
                || "event_redstone_change".equals(node.type()));
    }

    // Check if this is a whole number value
    private static boolean isWholeNumberValue(AdvancedGraphDocument.Node node, String port) {
        return isRedstoneValue(node, port)
                || "list_get".equals(node.type()) && "index".equals(port)
                || "substring".equals(node.type())
                && ("start_index".equals(port) || "end_index".equals(port))
                || "switch".equals(node.type()) && "selector".equals(port)
                || "send_named_controller_event".equals(node.type()) && "distance".equals(port)
                || "random_int".equals(node.type()) && "max".equals(port)
                || "random_int_in_range".equals(node.type())
                && ("min".equals(port) || "max".equals(port))
                || "mouse_input".equals(node.type()) && "timeout".equals(port);
    }

    // Get the list input size
    private int listInputSize(AdvancedGraphDocument.Node node) {
        if (!(menu.getMenuConfigTargetBlockEntity() instanceof AdvancedContraptionControllerBlockEntity controller)) {
            return 0;
        }
        AdvancedGraphDocument.Value preview = controller.previewGraphInput(draft, node, "list");
        int previewSize = GraphRuntime.listSize(preview);
        if (previewSize > 0 || !isInputConnected(node, "list")) return previewSize;
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), "list");
        return liveValue == null || !"list".equals(liveValue.type()) ? 0 : liveValue.entryCount();
    }

    // Get the string input length
    private int stringInputLength(AdvancedGraphDocument.Node node) {
        if (!(menu.getMenuConfigTargetBlockEntity() instanceof AdvancedContraptionControllerBlockEntity controller)) {
            return 0;
        }
        AdvancedGraphDocument.Value preview = controller.previewGraphInput(draft, node, "string");
        int previewLength = "string".equals(preview.type()) || "direction".equals(preview.type())
                ? substringSliderMaximum(preview.asString()) : 0;
        if (previewLength > 0 || !isInputConnected(node, "string")) {
            return previewLength;
        }
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), "string");
        return liveValue == null || !"string".equals(liveValue.type())
                ? previewLength : substringSliderMaximum(liveValue.textValue());
    }

    // Get the substring slider maximum
    static int substringSliderMaximum(String value) {
        return value == null ? 0 : value.length();
    }

    // Get the slider amount
    private static double sliderAmount(double val, double[] range) {
        double span = range[1] - range[0];
        return span <= 0.0D ? 0.0D : Mth.clamp((val - range[0]) / span, 0.0D, 1.0D);
    }

    // Get the input number
    private double inputNumber(AdvancedGraphDocument.Node node, String port) {
        CompoundTag payload = inputPayload(node, port);
        if (payload.contains("Value")) return payload.getDouble("Value");
        if ("ship_telemetry".equals(node.type())
                && "collision_detection_distance".equals(port)) {
            return AdvancedGraphCatalog.DEFAULT_COLLISION_DETECTION_DISTANCE;
        }
        if ("ship_telemetry".equals(node.type())
                && "collision_poll_rate".equals(port)) {
            return AdvancedGraphCatalog.DEFAULT_COLLISION_POLL_RATE;
        }
        String property = Character.toUpperCase(port.charAt(0)) + port.substring(1);
        return node.data().getDouble(property);
    }

    // Get the output number
    private double outputNumber(AdvancedGraphDocument.Node node, String port) {
        return AdvancedGraphPortState.outputDefault(node, port, "number")
                .asNumber();
    }

    // Read the boolean input
    private boolean inputBoolean(AdvancedGraphDocument.Node node, String port) {
        CompoundTag payload = inputPayload(node, port);
        if (payload.contains("Value")) return payload.getBoolean("Value");
        String property = Character.toUpperCase(port.charAt(0)) + port.substring(1);
        return node.data().getBoolean(property);
    }

    // Get the input string
    private String inputString(AdvancedGraphDocument.Node node, String port, String fallback) {
        String val = inputPayload(node, port).getString("Value");
        if (val.isBlank()) {
            String property = Character.toUpperCase(port.charAt(0)) + port.substring(1);
            val = node.data().getString(property);
        }
        return val.isBlank() ? fallback : val;
    }

    // Get the input payload
    private CompoundTag inputPayload(AdvancedGraphDocument.Node node, String port) {
        return node.data().getCompound("Defaults").getCompound(port).getCompound("Payload");
    }

    // Get the graph default
    private static CompoundTag graphDefault(String type, Object val) {
        if (val instanceof AdvancedGraphDocument.Value graphValue) {
            return graphDefault(type, graphValue);
        }
        CompoundTag entry = new CompoundTag();
        CompoundTag payload = new CompoundTag();
        entry.putString("Type", type);
        if (val instanceof Boolean bool) payload.putBoolean("Value", bool);
        else if (val instanceof Number num) payload.putDouble("Value", num.doubleValue());
        else if (val instanceof CompoundTag compound) payload.merge(compound.copy());
        else payload.putString("Value", String.valueOf(val));
        entry.put("Payload", payload);
        return entry;
    }

    // Get the graph default
    private static CompoundTag graphDefault(String type, AdvancedGraphDocument.Value val) {
        if (val == null) return graphDefault(type, "");
        return switch (type) {
            case "boolean" -> graphDefault(type, val.asBoolean());
            case "number" -> graphDefault(type, val.asNumber());
            case "string", "direction" -> graphDefault(type, val.asString());
            default -> {
                CompoundTag entry = new CompoundTag();
                entry.putString("Type", type);
                entry.put("Payload", val.payload().copy());
                yield entry;
            }
        };
    }

    // Put the input default
    private void putInputDefault(AdvancedGraphDocument.Node node, String port, String type, Object val) {
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.put(port, graphDefault(type, val));
        node.data().put("Defaults", defaults);
        if (AdvancedGraphPortState.isPersistent(node, port, false)) {
            AdvancedGraphPortState.updatePersistentValueTag(
                    node, port, false, defaults.getCompound(port));
        }
        if (AdvancedGraphCatalog.isShipSpeedPort(node, port)) {
            node.data().remove(AdvancedGraphCatalog.SHIP_SPEED_PERCENT_TAG);
        }
        CompoundTag prefilledInputs = node.data().getCompound("PrefilledInputs");
        if (prefilledInputs.contains(port)) {
            prefilledInputs.remove(port);
            if (prefilledInputs.isEmpty()) node.data().remove("PrefilledInputs");
            else node.data().put("PrefilledInputs", prefilledInputs);
        }
        if ("variable_set".equals(node.type()) && "default".equals(port)) {
            String variable = node.data().getString("Variable");
            if (validVariableName(variable)) {
                AdvancedGraphDocument.Value graphValue = switch (type) {
                    case "boolean" -> AdvancedGraphDocument.Value.bool(val instanceof Boolean bool && bool);
                    case "number" -> AdvancedGraphDocument.Value.number(
                            val instanceof Number num ? num.doubleValue() : 0.0D);
                    case "direction" -> AdvancedGraphDocument.Value.direction(String.valueOf(val));
                    case "frequency" -> AdvancedGraphDocument.Value.frequency(
                            val instanceof CompoundTag compound ? compound : new CompoundTag());
                    case "target" -> AdvancedGraphDocument.Value.target(
                            val instanceof CompoundTag compound ? compound : new CompoundTag());
                    case "list" -> AdvancedGraphDocument.Value.list(
                            val instanceof CompoundTag compound ? compound : new CompoundTag());
                    case "map" -> AdvancedGraphDocument.Value.map(
                            val instanceof CompoundTag compound ? compound : new CompoundTag());
                    default -> AdvancedGraphDocument.Value.string(String.valueOf(val));
                };
                draft.variables().put(variable, graphValue);
            }
        }
    }

    // Configure the variable type
    private void configureVariableType(AdvancedGraphDocument.Node node, String selectedType) {
        selectedType = normalizeVariableType(selectedType);
        String graphType = variableGraphType(selectedType);
        configureVariablePorts(node, selectedType);
        String variable = node.data().getString("Variable");
        for (AdvancedGraphDocument.Node candidate : activeNodes()) {
            if (candidate.id().equals(node.id()) || !"variable_set".equals(candidate.type())
                    || !variable.equals(candidate.data().getString("Variable"))) continue;
            configureVariablePorts(candidate, selectedType);
        }
        Object defaultValue = variableDefaultObject(graphType);
        putInputDefault(node, "default", graphType, defaultValue);
        if (validVariableName(variable)) {
            draft.variables().put(variable, variableValue(graphType, defaultValue));
        }
        synchronizeVariableNodes();
    }

    // Configure the variable ports
    private static void configureVariablePorts(AdvancedGraphDocument.Node node, String selectedType) {
        selectedType = normalizeVariableType(selectedType);
        String graphType = variableGraphType(selectedType);
        putVariableTypeOptions(node.data());
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        inputs.putString("default", graphType);
        inputs.putString("value", graphType);
        node.data().put("DynamicInputs", inputs);
        CompoundTag outputs = node.data().getCompound("DynamicOutputs");
        outputs.putString("value", graphType);
        node.data().put("DynamicOutputs", outputs);
        node.data().putString("VariableType", selectedType);
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.put("type", graphDefault("string", selectedType));
        CompoundTag defaultEntry = defaults.getCompound("default");
        if (!graphType.equals(defaultEntry.getString("Type"))) {
            Object defaultValue = variableDefaultObject(graphType);
            defaults.put("default", graphDefault(graphType, defaultValue));
        }
        node.data().put("Defaults", defaults);
    }

    // Put the variable type options
    private static void putVariableTypeOptions(CompoundTag data) {
        CompoundTag inputOptions = data.getCompound("InputOptions");
        ListTag values = new ListTag();
        for (String option : VARIABLE_TYPE_OPTIONS) values.add(StringTag.valueOf(option));
        inputOptions.put("type", values);
        data.put("InputOptions", inputOptions);
    }

    // Normalize the variable type
    private static String normalizeVariableType(String selectedType) {
        return VARIABLE_TYPE_OPTIONS.contains(selectedType) ? selectedType : "boolean";
    }

    // Get the variable graph type
    private static String variableGraphType(String selectedType) {
        return switch (normalizeVariableType(selectedType)) {
            case "boolean" -> "boolean";
            case "string" -> "string";
            case "integer", "float" -> "number";
            default -> normalizeVariableType(selectedType);
        };
    }

    // Get the variable type option
    private String variableTypeOption(String variable, AdvancedGraphDocument.Value val) {
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if (!"variable_set".equals(node.type()) || !variable.equals(node.data().getString("Variable"))) continue;
            String selectedType = node.data().getString("VariableType");
            if (VARIABLE_TYPE_OPTIONS.contains(selectedType)) return selectedType;
        }
        if ("boolean".equals(val.type())) return "boolean";
        if ("string".equals(val.type())) return "string";
        if (!"number".equals(val.type()) && VARIABLE_TYPE_OPTIONS.contains(val.type())) {
            return val.type();
        }
        return val.asNumber() == Math.rint(val.asNumber()) ? "integer" : "float";
    }

    // Get the variable input value
    private static Object variableInputValue(AdvancedGraphDocument.Value val, String selectedType) {
        return switch (variableGraphType(selectedType)) {
            case "boolean" -> val.asBoolean();
            case "string", "direction" -> val.asString();
            case "frequency", "target", "list", "map" -> val.payload();
            default -> "integer".equals(selectedType) ? Math.round(val.asNumber()) : val.asNumber();
        };
    }

    // Get the variable default object
    private static Object variableDefaultObject(String graphType) {
        return switch (graphType) {
            case "boolean" -> false;
            case "string", "direction" -> "";
            case "frequency", "target", "list", "map" -> new CompoundTag();
            default -> 0.0D;
        };
    }

    // Get the variable value
    private static AdvancedGraphDocument.Value variableValue(String graphType, Object val) {
        CompoundTag payload = val instanceof CompoundTag compound ? compound : new CompoundTag();
        return switch (graphType) {
            case "boolean" -> AdvancedGraphDocument.Value.bool(val instanceof Boolean bool && bool);
            case "string" -> AdvancedGraphDocument.Value.string(String.valueOf(val));
            case "direction" -> AdvancedGraphDocument.Value.direction(String.valueOf(val));
            case "frequency" -> AdvancedGraphDocument.Value.frequency(payload);
            case "target" -> AdvancedGraphDocument.Value.target(payload);
            case "list" -> AdvancedGraphDocument.Value.list(payload);
            case "map" -> AdvancedGraphDocument.Value.map(payload);
            default -> AdvancedGraphDocument.Value.number(
                    val instanceof Number num ? num.doubleValue() : 0.0D);
        };
    }

    // Check if this is a constructor node
    private static boolean isConstructorNode(AdvancedGraphDocument.Node node) {
        return node != null && AdvancedGraphCatalog.isConstructorType(node.type());
    }

    // Check if the port accepts a constructor value
    private static boolean constructorValuePort(AdvancedGraphDocument.Node node, String port) {
        if (!isConstructorNode(node) || port == null) return false;
        if (AdvancedGraphCatalog.isDynamicConstructor(node)) {
            return AdvancedGraphCatalog.inputs(node).containsKey(port);
        }
        return "value".equals(port);
    }

    // Get the input display label
    private String inputDisplayLabel(AdvancedGraphDocument.Node node, String port) {
        String inlineMapLabel = inlineMapPortLabel(node, port, false);
        if (!inlineMapLabel.isBlank()) return inlineMapLabel;
        if (isHudNode(node)) {
            return hudFieldLabel(node, port);
        }
        if (AdvancedGraphCatalog.isDynamicConstructor(node)) {
            return AdvancedGraphCatalog.constructorInputLabel(node, port);
        }
        return humanPort(port);
    }

    // Get the output display label
    private String outputDisplayLabel(AdvancedGraphDocument.Node node, String port) {
        String label = inlineMapPortLabel(node, port, true);
        if (label.isBlank()) {
            label = node.data().getCompound("OutputLabels").getString(port);
        }
        if (label.isBlank()) {
            label = humanPort(port);
        }
        if (AdvancedGraphPortState.isPersistent(node, port, true)) {
            label += " [P]";
        }
        return label;
    }

    // Get the inline MAP child label
    private String inlineMapPortLabel(AdvancedGraphDocument.Node node, String port, boolean output) {
        if (node == null || port == null || port.isBlank()) return "";
        CompoundTag mappings = node.data().getCompound(output
                ? AdvancedGraphCatalog.INLINE_MAP_OUTPUTS_TAG : AdvancedGraphCatalog.INLINE_MAP_INPUTS_TAG);
        String key = mappings.getCompound(port).getString(AdvancedGraphCatalog.INLINE_MAP_KEY_TAG);
        if (key.isBlank()) return "";
        int separator = key.lastIndexOf('.');
        return humanPort(separator < 0 ? key : key.substring(separator + 1));
    }

    // Get the inline MAP child port indent
    private int inlineMapPortIndent(AdvancedGraphDocument.Node node, String port, boolean output) {
        return inlineMapPortLabel(node, port, output).isBlank() ? 0
                : Math.max(2, (int) Math.round(INLINE_MAP_PORT_INDENT * zoom));
    }

    // Get the inspector inline MAP child port indent
    private int inspectorInlineMapPortIndent(AdvancedGraphDocument.Node node, String port, boolean output) {
        return inlineMapPortLabel(node, port, output).isBlank() ? 0 : INLINE_MAP_PORT_INDENT;
    }

    // Ensure the dynamic constructor
    private void ensureDynamicConstructor(AdvancedGraphDocument.Node node) {
        if (!isConstructorNode(node) || AdvancedGraphCatalog.isDynamicConstructor(node)) return;
        CompoundTag inputs = new CompoundTag();
        CompoundTag labels = new CompoundTag();
        CompoundTag defaults = node.data().getCompound("Defaults");
        inputs.putString("value", "any");
        String label = "list_create".equals(node.type()) ? "Value 1" : inputString(node, "key", "").strip();
        if (label.isBlank()) label = "Field 1";
        labels.putString("value", label);
        if (!defaults.contains("value")) {
            defaults.put("value", graphDefault("string", ""));
        }
        if ("map_create".equals(node.type())) {
            activeEdges().removeIf(edge -> edge.toNode().equals(node.id()) && "key".equals(edge.toPort()));
            defaults.remove("key");
            CompoundTag opts = node.data().getCompound("InputOptions");
            opts.remove("key");
            if (opts.isEmpty()) node.data().remove("InputOptions");
            else node.data().put("InputOptions", opts);
        }
        node.data().putBoolean(AdvancedGraphCatalog.DYNAMIC_CONSTRUCTOR_TAG, true);
        node.data().put("DynamicInputs", inputs);
        node.data().put(AdvancedGraphCatalog.INPUT_LABELS_TAG, labels);
        node.data().put("Defaults", defaults);
    }

    // Add the constructor input
    private void addConstructorInput(AdvancedGraphDocument.Node node) {
        if (!isConstructorNode(node)) return;
        int currentCount = AdvancedGraphCatalog.isDynamicConstructor(node)
                ? AdvancedGraphCatalog.inputs(node).size() : 1;
        if (currentCount >= MAX_CONSTRUCTOR_INPUTS) return;
        checkpoint();
        ensureDynamicConstructor(node);
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        CompoundTag labels = node.data().getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG);
        CompoundTag defaults = node.data().getCompound("Defaults");
        int ordinal = inputs.getAllKeys().size() + 1;
        String prefix = "list_create".equals(node.type()) ? "value_" : "field_";
        String port;
        do {
            port = prefix + ordinal++;
        } while (inputs.contains(port));
        int displayOrdinal = inputs.getAllKeys().size() + 1;
        inputs.putString(port, "any");
        labels.putString(port, ("list_create".equals(node.type()) ? "Value " : "Field ") + displayOrdinal);
        defaults.put(port, graphDefault("string", ""));
        node.data().put("DynamicInputs", inputs);
        node.data().put(AdvancedGraphCatalog.INPUT_LABELS_TAG, labels);
        node.data().put("Defaults", defaults);
        selectedInputPort = CONSTRUCTOR_LABEL_PREFIX + port;
        syncInspector();
    }

    // Remove the constructor input
    private void removeConstructorInput(AdvancedGraphDocument.Node node, String port) {
        if (!constructorValuePort(node, port)) return;
        int currentCount = AdvancedGraphCatalog.isDynamicConstructor(node)
                ? AdvancedGraphCatalog.inputs(node).size() : 1;
        if (currentCount <= 1) return;
        checkpoint();
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        CompoundTag labels = node.data().getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG);
        CompoundTag defaults = node.data().getCompound("Defaults");
        CompoundTag opts = node.data().getCompound("InputOptions");
        inputs.remove(port);
        labels.remove(port);
        defaults.remove(port);
        opts.remove(port);
        node.data().put("DynamicInputs", inputs);
        node.data().put(AdvancedGraphCatalog.INPUT_LABELS_TAG, labels);
        if (defaults.isEmpty()) node.data().remove("Defaults");
        else node.data().put("Defaults", defaults);
        if (opts.isEmpty()) node.data().remove("InputOptions");
        else node.data().put("InputOptions", opts);
        activeEdges().removeIf(edge -> edge.toNode().equals(node.id()) && port.equals(edge.toPort()));
        if ((CONSTRUCTOR_LABEL_PREFIX + port).equals(selectedInputPort) || port.equals(selectedInputPort)) {
            selectedInputPort = null;
        }
        syncInspector();
    }

    // Get the constructor editable label port
    private String constructorEditableLabelPort(AdvancedGraphDocument.Node node, String selectedPort) {
        if (!isConstructorNode(node) || selectedPort == null
                || !selectedPort.startsWith(CONSTRUCTOR_LABEL_PREFIX)) {
            return null;
        }
        String port = selectedPort.substring(CONSTRUCTOR_LABEL_PREFIX.length());
        return constructorValuePort(node, port) ? port : null;
    }

    // Rename the constructor input
    private void renameConstructorInput(AdvancedGraphDocument.Node node, String port, String requested) {
        if (!constructorValuePort(node, port) || requested == null) return;
        String next = requested.strip();
        if (next.isBlank()) return;
        if (next.length() > 64) next = next.substring(0, 64);
        CompoundTag labels = node.data().getCompound(AdvancedGraphCatalog.INPUT_LABELS_TAG);
        labels.putString(port, next);
        node.data().put(AdvancedGraphCatalog.INPUT_LABELS_TAG, labels);
    }

    // Check if this is a HUD type
    private static boolean isHudType(String type) {
        return "hud_element".equals(type) || "advanced_hud_element".equals(type)
                || isAccDisplayWidgetType(type);
    }

    // Check if this is an ACC display widget type
    private static boolean isAccDisplayWidgetType(String type) {
        return "acc_display_widget".equals(type) || "acc_hologram_widget".equals(type);
    }

    // Check if this allows off surface layout
    private static boolean allowsOffSurfaceLayout(AdvancedGraphDocument.Node node) {
        return node != null && "acc_hologram_widget".equals(node.type());
    }

    // Check if this is a HUD node
    private static boolean isHudNode(AdvancedGraphDocument.Node node) {
        return node != null && isHudType(node.type());
    }

    // Check if this is an advanced HUD node
    private static boolean isAdvancedHudNode(AdvancedGraphDocument.Node node) {
        return node != null && ("advanced_hud_element".equals(node.type())
                || isAccDisplayWidgetType(node.type()));
    }

    // Add the advanced HUD element
    private void addAdvancedHudElement(AdvancedGraphDocument.Node node, String type) {
        if (!isAdvancedHudNode(node)) return;
        checkpoint();
        ListTag elements = node.data().getList(WIDGET_ELEMENTS, net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag elm = new CompoundTag();
        int idx = elements.size();
        elm.putString("Type", type);
        elm.putInt("X", 8 + (idx % 4) * 10);
        elm.putInt("Y", 8 + (idx % 5) * 8);
        elm.putInt("W", switch (type) {
            case "image" -> 32;
            case "box" -> 92;
            case "button", "toggle", "text_input" -> 80;
            case "slider", "progress" -> 112;
            default -> 112;
        });
        elm.putInt("H", switch (type) {
            case "image" -> 32;
            case "box" -> 26;
            case "button", "toggle", "slider", "progress", "text_input" -> 20;
            default -> 12;
        });
        elm.putInt("Color", "box".equals(type)
                ? AdvancedHudElementStyle.DEFAULT_BOX_BACKGROUND_COLOR
                : AdvancedHudElementStyle.DEFAULT_TEXT_COLOR);
        elm.putString("Text", switch (type) {
            case "text" -> "Text";
            case "button" -> "Button";
            case "toggle" -> "Toggle";
            case "text_input" -> "Enter text";
            default -> "";
        });
        elm.putString("Port", "value");
        elm.putString("Texture", "minecraft:textures/block/stone.png");
        elm.putDouble("Rotation", 0.0D);
        elm.putDouble("Scale", 1.0D);
        elm.putBoolean("Visible", true);
        if (isInteractiveHudType(type)) {
            elm.putString("InteractionId", UUID.randomUUID().toString());
            elm.putDouble("Min", 0.0D);
            elm.putDouble("Max", 1.0D);
            elm.putDouble("Step", 0.1D);
            elm.putDouble("Value", 0.0D);
            elm.putInt("BorderWidth", 1);
            elm.putInt("BorderColor", AdvancedHudElementStyle.DEFAULT_BOX_BORDER_COLOR);
            elm.putInt("Color", 0xCC315D75);
        }
        if ("progress".equals(type)) {
            elm.putDouble("Min", 0.0D);
            elm.putDouble("Max", 1.0D);
            elm.putDouble("Value", 0.0D);
        }
        AdvancedHudElementStyle.applyDefaults(elm);
        elements.add(elm);
        node.data().put(WIDGET_ELEMENTS, elements);
        syncHudPorts(node);
        syncInspector();
    }

    // Check if this is an interactive HUD type
    private static boolean isInteractiveHudType(String type) {
        return AdvancedHudInteractions.isInteractiveType(type);
    }

    // Sync the HUD ports
    private void syncHudPorts(AdvancedGraphDocument.Node node) {
        syncHudPorts(node, activeEdges());
        clearGraphRenderCache();
    }

    // Sync the HUD ports
    private static void syncHudPorts(
            AdvancedGraphDocument.Node node, List<AdvancedGraphDocument.Edge> edges) {
        AdvancedHudInteractions.synchronize(node, edges);
    }

    // Sync the HUD ports
    private static void syncHudPorts(AdvancedGraphDocument graph) {
        if (graph == null) {
            return;
        }
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            AdvancedHudInteractions.synchronize(node, graph.edges());
        }
        for (AdvancedGraphDocument.FunctionGraph function : graph.functions()) {
            for (AdvancedGraphDocument.Node node : function.nodes()) {
                AdvancedHudInteractions.synchronize(node, function.edges());
            }
        }
    }

    // Add the HUD field
    private void addHudField(AdvancedGraphDocument.Node node) {
        if (!isHudNode(node)) return;
        checkpoint();
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        CompoundTag labels = node.data().getCompound(HUD_FIELD_LABELS);
        CompoundTag defaults = node.data().getCompound("Defaults");
        int idx = 2;
        String port;
        do {
            port = "field_" + idx++;
        } while (inputs.contains(port));
        String label = "Value [" + (idx - 1) + "]";
        inputs.putString(port, "any");
        labels.putString(port, label);
        defaults.put(port, graphDefault("string", ""));
        node.data().put("DynamicInputs", inputs);
        node.data().put(HUD_FIELD_LABELS, labels);
        node.data().put("Defaults", defaults);
        selectedInputPort = "label:" + port;
        syncInspector();
    }

    // Remove the HUD field
    private void removeHudField(AdvancedGraphDocument.Node node, String port) {
        if (!isHudNode(node) || isHudRequiredPort(port)) return;
        checkpoint();
        CompoundTag inputs = node.data().getCompound("DynamicInputs");
        CompoundTag labels = node.data().getCompound(HUD_FIELD_LABELS);
        CompoundTag defaults = node.data().getCompound("Defaults");
        inputs.remove(port);
        labels.remove(port);
        defaults.remove(port);
        if (inputs.isEmpty()) node.data().remove("DynamicInputs");
        else node.data().put("DynamicInputs", inputs);
        if (labels.isEmpty()) node.data().remove(HUD_FIELD_LABELS);
        else node.data().put(HUD_FIELD_LABELS, labels);
        if (defaults.isEmpty()) node.data().remove("Defaults");
        else node.data().put("Defaults", defaults);
        activeEdges().removeIf(edge -> edge.toNode().equals(node.id()) && edge.toPort().equals(port));
        if (isAdvancedHudNode(node)) {
            ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
            for (int idx = 0; idx < elements.size(); idx++) {
                CompoundTag elm = elements.getCompound(idx);
                if (port.equals(elm.getString("Port"))) elm.putString("Port", "value");
                CompoundTag bindings = elm.getCompound(AdvancedHudElementBinding.BINDINGS_TAG);
                for (String property : new LinkedHashSet<>(bindings.getAllKeys())) {
                    if (port.equals(bindings.getString(property))) {
                        AdvancedHudElementBinding.bind(elm, property, "");
                    }
                }
            }
            node.data().put(WIDGET_ELEMENTS, elements);
            syncHudPorts(node);
        }
        if (port.equals(selectedInputPort) || ("label:" + port).equals(selectedInputPort)) {
            selectedInputPort = null;
        }
        syncInspector();
    }

    // Get the HUD body port
    private String hudBodyPortAt(AdvancedGraphDocument.Node node, double mouseX, double mouseY) {
        int x = screenX(node.x());
        int y = screenY(node.y());
        int bodyTop = nodeBodyTop(node, y);
        int rowHeight = Math.max(10, (int) (15 * zoom));
        if ("curve".equals(node.type())) bodyTop += (int) (CURVE_BODY_HEIGHT * zoom);
        if (mouseX < x + 5 || mouseX > x + NODE_WIDTH * zoom - 5 || mouseY < bodyTop
                || mouseY > bodyTop + bodyControlCount(node) * rowHeight) {
            return null;
        }
        int row = (int) ((mouseY - bodyTop) / rowHeight);
        if (usesBinding(node)) row--;
        row -= propertyControlCount(node);
        for (var port : AdvancedGraphCatalog.inputs(node).entrySet()) {
            if ("exec".equals(port.getValue()) || ("curve".equals(node.type()) && "value".equals(port.getKey()))) continue;
            if (!isDataPortVisible(node, port.getKey(), false)) continue;
            if (row-- == 0) return port.getKey();
        }
        return null;
    }

    // Check if this is a HUD reserved port
    private boolean isHudReservedPort(String port) {
        return "label".equals(port) || "visible".equals(port);
    }

    // Check if this is a HUD required port
    private boolean isHudRequiredPort(String port) {
        return isHudReservedPort(port) || "value".equals(port);
    }

    // Get the HUD editable label port
    private String hudEditableLabelPort(AdvancedGraphDocument.Node node, String selectedPort) {
        if (!isHudNode(node) || selectedPort == null || !selectedPort.startsWith("label:")) return null;
        String port = selectedPort.substring(6);
        if (port.isBlank() || isHudReservedPort(port) || !AdvancedGraphCatalog.inputs(node).containsKey(port)) return null;
        return port;
    }

    // Get the editable input label port
    private String editableInputLabelPort(AdvancedGraphDocument.Node node, String selectedPort) {
        String hudPort = hudEditableLabelPort(node, selectedPort);
        return hudPort == null ? constructorEditableLabelPort(node, selectedPort) : hudPort;
    }

    // Get the HUD field label
    private String hudFieldLabel(AdvancedGraphDocument.Node node, String port) {
        if ("label".equals(port)) return "Title";
        if ("visible".equals(port)) return "Visible";
        String label = node.data().getCompound(HUD_FIELD_LABELS).getString(port);
        return label.isBlank() ? humanPort(port) : label;
    }

    // Rename the HUD field label
    private void renameHudFieldLabel(AdvancedGraphDocument.Node node, String port, String requested) {
        if (!isHudNode(node) || port == null || requested == null) return;
        String next = requested.trim();
        if (next.isBlank()) return;
        CompoundTag labels = node.data().getCompound(HUD_FIELD_LABELS);
        labels.putString(port, next);
        node.data().put(HUD_FIELD_LABELS, labels);
    }

    // Migrate the HUD nodes
    private void migrateHudNodes(AdvancedGraphDocument graph) {
        if (graph == null) return;
        // -----------------------------------------------------GRAPH SETS-----------------------------------------------------
        List<List<AdvancedGraphDocument.Node>> nodeSets = new ArrayList<>();
        List<List<AdvancedGraphDocument.Edge>> edgeSets = new ArrayList<>();
        nodeSets.add(graph.nodes());
        edgeSets.add(graph.edges());
        for (AdvancedGraphDocument.FunctionGraph function : graph.functions()) {
            nodeSets.add(function.nodes());
            edgeSets.add(function.edges());
        }
        // -----------------------------------------------------HUD GRAPHS-----------------------------------------------------
        for (int graphIndex = 0; graphIndex < nodeSets.size(); graphIndex++) {
            List<AdvancedGraphDocument.Edge> graphEdges = edgeSets.get(graphIndex);
            for (AdvancedGraphDocument.Node node : nodeSets.get(graphIndex)) {
            if (!isHudNode(node)) continue;
            // -----------------------------------------------------HUD PORTS-----------------------------------------------------
            CompoundTag inputs = node.data().getCompound("DynamicInputs");
            CompoundTag labels = node.data().getCompound(HUD_FIELD_LABELS);
            CompoundTag defaults = node.data().getCompound("Defaults");
            if (!inputs.contains("value")) {
                inputs.putString("value", "any");
            }
            if (labels.getString("value").isBlank()) {
                labels.putString("value", "Value");
            }
            for (String port : inputs.getAllKeys()) {
                if ("value".equals(port)) {
                    if (labels.getString(port).isBlank() || "Field 0".equals(labels.getString(port))) {
                        labels.putString(port, "Value");
                    }
                    continue;
                }
                if (port.startsWith("field_") && (labels.getString(port).isBlank() || labels.getString(port).startsWith("Field "))) {
                    try {
                        int idx = Integer.parseInt(port.substring("field_".length()));
                        labels.putString(port, "Value [" + idx + "]");
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            if (!defaults.contains("value")) {
                defaults.put("value", graphDefault("string", ""));
            }
            if (!defaults.contains("visible")) {
                defaults.put("visible", graphDefault("boolean", true));
            }
            // ------------------------------------ADVANCED ELEMENTS------------------------------------
            if (isAdvancedHudNode(node)) {
                ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
                boolean legacyImagePort = inputs.contains("image")
                        || graphEdges.stream().anyMatch(edge ->
                        node.id().equals(edge.toNode()) && "image".equals(edge.toPort()));
                for (int idx = 0; idx < elements.size(); idx++) {
                    CompoundTag elm = elements.getCompound(idx);
                    AdvancedHudElementStyle.applyDefaults(elm);
                    if (!elm.contains("Rotation", Tag.TAG_DOUBLE)) elm.putDouble("Rotation", 0.0D);
                    if (!elm.contains("Scale", Tag.TAG_DOUBLE)) elm.putDouble("Scale", 1.0D);
                    if (!elm.contains("Visible", Tag.TAG_BYTE)) elm.putBoolean("Visible", true);
                    String elementType = elm.getString("Type");
                    if ("image".equals(elementType) && "image".equals(elm.getString("Port"))) {
                        legacyImagePort = true;
                    }
                    if (("value".equals(elementType) || "image".equals(elementType))
                            && elm.getString("Port").isBlank()) {
                        elm.putString("Port", "value");
                    }
                }
                if (legacyImagePort) {
                    inputs.putString("image", "string");
                    if (labels.getString("image").isBlank()) labels.putString("image", "Image");
                    if (!defaults.contains("image")) {
                        defaults.put("image", graphDefault("string", "minecraft:textures/block/stone.png"));
                    }
                } else {
                    inputs.remove("image");
                    labels.remove("image");
                    defaults.remove("image");
                }
                node.data().put(WIDGET_ELEMENTS, elements);
                syncHudPorts(node, graphEdges);
            }
            // -----------------------------------------------------SAVE NODE-----------------------------------------------------
            node.data().put("DynamicInputs", inputs);
            node.data().put(HUD_FIELD_LABELS, labels);
            node.data().put("Defaults", defaults);
            if (node.data().getString("Label").isBlank()) {
                node.data().putString("Label", isAdvancedHudNode(node) ? "Advanced HUD" : "HUD");
            }
            if (isAdvancedHudNode(node) && !node.data().contains(WIDGET_ELEMENTS)) {
                node.data().put(WIDGET_ELEMENTS, new ListTag());
            }
            }
        }
    }

    // Migrate the split list nodes
    private void migrateSplitListNodes(AdvancedGraphDocument graph) {
        if (graph == null) return;
        for (int i = 0; i < graph.nodes().size(); i++) {
            AdvancedGraphDocument.Node node = graph.nodes().get(i);
            if (!"json_split".equals(node.type())) continue;
            graph.nodes().set(i, new AdvancedGraphDocument.Node(node.id(), "split_list", node.label(),
                    node.x(), node.y(), node.data()));
        }
    }

    // Migrate the comment groups
    private void migrateCommentGroups(AdvancedGraphDocument graph) {
        if (graph == null || font == null) return;
        Set<String> validNodeIds = new LinkedHashSet<>();
        for (AdvancedGraphDocument.Node node : graph.nodes()) {
            validNodeIds.add(node.id());
        }
        Set<String> claimedNodeIds = new LinkedHashSet<>();
        for (CompoundTag group : graph.groups()) {
            if (!group.contains(AdvancedGraphSelection.COMMENT_GROUP_NODE_IDS, Tag.TAG_LIST)) continue;
            Set<String> members = AdvancedGraphSelection.commentGroupNodeIds(group);
            members.removeIf(nodeId -> !validNodeIds.contains(nodeId) || !claimedNodeIds.add(nodeId));
            AdvancedGraphSelection.setCommentGroupNodeIds(group, members);
        }
        for (CompoundTag group : graph.groups()) {
            if (group.contains(AdvancedGraphSelection.COMMENT_GROUP_NODE_IDS, Tag.TAG_LIST)) continue;
            Set<String> members = new LinkedHashSet<>();
            for (AdvancedGraphDocument.Node node : graph.nodes()) {
                if (!claimedNodeIds.contains(node.id()) && groupContainsNode(group, node)) {
                    members.add(node.id());
                    claimedNodeIds.add(node.id());
                }
            }
            AdvancedGraphSelection.setCommentGroupNodeIds(group, members);
        }
    }

    // Check if this is a break out node
    private boolean isBreakOutNode(AdvancedGraphDocument.Node node) {
        return node != null && ("split_list".equals(node.type()) || "break_out".equals(node.type()));
    }

    // Sync the structured data nodes
    private void syncStructuredDataNodes() {
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if (controller == null || draft == null) return;
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if (isBreakOutNode(node)) {
                CompoundTag outputs = structuredInputTypes(controller, node, "value");
                if (outputs.isEmpty() && shouldHoldStructuredOutputs(controller, node, "value")) {
                    continue;
                }
                if (outputs.isEmpty()) node.data().remove("DynamicOutputs");
                else node.data().put("DynamicOutputs", outputs);
                if (selectedNodes.contains(node.id()) && selectedInputPort != null
                        && selectedInputPort.startsWith("out:")) {
                    selectedInputPort = null;
                }
            }
            if ("map_get".equals(node.type())) {
                syncStructureKeyOptions(node, structuredInputTypes(controller, node, "map"));
            }
        }
    }

    // Get the structured input types
    private CompoundTag structuredInputTypes(AdvancedContraptionControllerBlockEntity controller,
                                             AdvancedGraphDocument.Node node, String port) {
        CompoundTag outputs = GraphRuntime.splitListOutputsFor(
                draftSimulationRuntime == null
                        ? controller.previewGraphInput(draft, node, port)
                        : previewDraftInput(node, port));
        if (!outputs.isEmpty()) return outputs;
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), port);
        if (liveValue == null || liveValue.entryTypes().isEmpty()) return outputs;
        liveValue.entryTypes().forEach(outputs::putString);
        return outputs;
    }

    // Check if this should hold structured outputs
    private boolean shouldHoldStructuredOutputs(
            AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node,
            String port
    ) {
        if (node.data().getCompound("DynamicOutputs").isEmpty()) return false;
        AdvancedGraphDocument.Value preview = draftSimulationRuntime == null
                ? controller.previewGraphInput(draft, node, port)
                : previewDraftInput(node, port);
        if (preview != null && ("list".equals(preview.type()) || "map".equals(preview.type()))) {
            return false;
        }
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveInput(node.id(), port);
        if (liveValue != null && ("list".equals(liveValue.type()) || "map".equals(liveValue.type()))) {
            return false;
        }
        AdvancedGraphDocument.Edge sourceEdge = activeEdges().stream()
                .filter(edge -> edge.toNode().equals(node.id()) && edge.toPort().equals(port))
                .findFirst().orElse(null);
        AdvancedGraphDocument.Node src = sourceEdge == null ? null : findNode(sourceEdge.fromNode());
        if (src == null || !"variable_get".equals(src.type())) return false;
        String sourceType = AdvancedGraphCatalog.outputs(src).get(sourceEdge.fromPort());
        return "list".equals(sourceType) || "map".equals(sourceType);
    }

    // Sync the structure key options
    private void syncStructureKeyOptions(AdvancedGraphDocument.Node node, CompoundTag entryTypes) {
        CompoundTag inputOptions = node.data().getCompound("InputOptions");
        ListTag keys = new ListTag();
        entryTypes.getAllKeys().stream().sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(key -> keys.add(StringTag.valueOf(key)));
        if (keys.isEmpty()) {
            inputOptions.remove("key");
            node.data().remove("DynamicOutputs");
        } else {
            inputOptions.put("key", keys);
            String selectedKey = inputString(node, "key", "");
            if (selectedKey.isBlank()) selectedKey = keys.getString(0);
            if (inputString(node, "key", "").isBlank()) putInputDefault(node, "key", "string", selectedKey);
            CompoundTag dynamicOutputs = node.data().getCompound("DynamicOutputs");
            String selectedType = entryTypes.getString(selectedKey);
            if (selectedType.isBlank()) dynamicOutputs.remove("value");
            else dynamicOutputs.putString("value", selectedType);
            if (dynamicOutputs.isEmpty()) node.data().remove("DynamicOutputs");
            else node.data().put("DynamicOutputs", dynamicOutputs);
        }
        if (inputOptions.isEmpty()) node.data().remove("InputOptions");
        else node.data().put("InputOptions", inputOptions);
    }

    // Sync the exec splitters
    private void syncExecSplitters() {
        if (draft == null) return;
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if (isExecutionSplitterNode(node)) {
                if ("switch".equals(node.type())
                        && !node.data().contains(AdvancedGraphCatalog.SWITCH_TYPE_TAG, Tag.TAG_STRING)) {
                    node.data().putString(AdvancedGraphCatalog.SWITCH_TYPE_TAG,
                            AdvancedGraphCatalog.SWITCH_EXECUTION_TYPE);
                }
                int count = executionOutputCount(node);
                CompoundTag ports = node.data().getCompound(
                        AdvancedGraphCatalog.switchDataMode(node)
                                ? "DynamicInputs" : "DynamicOutputs");
                if (!node.data().contains("OutputCount", Tag.TAG_INT)
                        || !executionPortsMatch(node, ports, count)) {
                    setExecutionOutputCount(node, count, true);
                }
            }
            if (isExecutionCombinerNode(node)) {
                int count = executionInputCount(node);
                if (!node.data().contains("InputCount", Tag.TAG_INT)
                        || !executionInputsMatch(node.data().getCompound("DynamicInputs"), count)) {
                    setExecutionInputCount(node, count, true);
                }
            }
        }
    }

    // Check if this is an execution splitter type
    private static boolean isExecutionSplitterType(String type) {
        return "parallel_execution".equals(type) || "sequenced_execution".equals(type)
                || "switch".equals(type);
    }

    // Check if this is an execution splitter node
    private static boolean isExecutionSplitterNode(AdvancedGraphDocument.Node node) {
        return node != null && isExecutionSplitterType(node.type());
    }

    // Get the execution output count
    private static int executionOutputCount(AdvancedGraphDocument.Node node) {
        if (node == null) return MIN_EXECUTION_OUTPUTS;
        return clampExecOutputs(node.data().contains("OutputCount", Tag.TAG_INT)
                ? node.data().getInt("OutputCount")
                : MIN_EXECUTION_OUTPUTS);
    }

    // Clamp the exec outputs
    private static int clampExecOutputs(int count) {
        return Mth.clamp(count, MIN_EXECUTION_OUTPUTS, MAX_EXECUTION_OUTPUTS);
    }

    // Set the execution output count
    private void setExecutionOutputCount(AdvancedGraphDocument.Node node, int count, boolean pruneEdges) {
        if (node == null) return;
        int clamped = clampExecOutputs(count);
        node.data().putInt("OutputCount", clamped);
        putExecutionOutputs(node.type(), node.data(), clamped);
        if (pruneEdges && draft != null) {
            boolean dataSwitch = "switch".equals(node.type())
                    && AdvancedGraphCatalog.switchDataMode(node);
            activeEdges().removeIf(edge -> dataSwitch
                    ? edge.toNode().equals(node.id())
                    && removedExecutionOutput(node.type(), edge.toPort(), clamped)
                    : edge.fromNode().equals(node.id())
                    && removedExecutionOutput(node.type(), edge.fromPort(), clamped));
        }
    }

    // Configure the switch type
    private void configureSwitchType(
            AdvancedGraphDocument.Node node, String requestedType, boolean pruneEdges) {
        if (node == null || !"switch".equals(node.type())) return;
        String previousType = AdvancedGraphCatalog.switchType(node);
        String nextType = AdvancedGraphCatalog.SWITCH_DATA_TYPE.equalsIgnoreCase(requestedType)
                ? AdvancedGraphCatalog.SWITCH_DATA_TYPE
                : AdvancedGraphCatalog.SWITCH_EXECUTION_TYPE;
        node.data().putString(AdvancedGraphCatalog.SWITCH_TYPE_TAG, nextType);

        CompoundTag dynamicInputs = node.data().getCompound("DynamicInputs");
        dynamicInputs.remove("exec");
        dynamicInputs.remove("value");
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.remove("value");
        node.data().put("Defaults", defaults);
        if (dynamicInputs.isEmpty()) node.data().remove("DynamicInputs");
        else node.data().put("DynamicInputs", dynamicInputs);

        setExecutionOutputCount(node, executionOutputCount(node), false);
        if (pruneEdges && draft != null && !previousType.equals(nextType)) {
            boolean dataMode = AdvancedGraphCatalog.SWITCH_DATA_TYPE.equals(nextType);
            activeEdges().removeIf(edge -> dataMode
                    ? edge.fromNode().equals(node.id())
                    && ("default".equals(edge.fromPort())
                    || edge.fromPort().startsWith("case_"))
                    || edge.toNode().equals(node.id()) && "exec".equals(edge.toPort())
                    : edge.fromNode().equals(node.id()) && "value".equals(edge.fromPort())
                    || edge.toNode().equals(node.id())
                    && ("default".equals(edge.toPort())
                    || edge.toPort().startsWith("case_")));
        }
        clearGraphRenderCache();
    }

    // Put the execution outputs
    private static void putExecutionOutputs(String type, CompoundTag data, int count) {
        int clamped = clampExecOutputs(count);
        boolean dataSwitch = "switch".equals(type)
                && AdvancedGraphCatalog.SWITCH_DATA_TYPE.equalsIgnoreCase(
                data.getString(AdvancedGraphCatalog.SWITCH_TYPE_TAG));
        if (dataSwitch) {
            CompoundTag inputs = data.getCompound("DynamicInputs");
            inputs.getAllKeys().stream()
                    .filter(port -> port.startsWith("case_"))
                    .toList().forEach(inputs::remove);
            CompoundTag types = data.getCompound(
                    AdvancedGraphPortState.SWITCH_CASE_TYPES_TAG);
            CompoundTag defaults = data.getCompound("Defaults");
            for (int idx = 0; idx < clamped; idx++) {
                String port = executionOutputPort(type, idx);
                String caseType = AdvancedGraphPortState.switchCaseType(data, port);
                types.putString(port, caseType);
                inputs.putString(port, caseType);
                if (!defaults.contains(port)) {
                    defaults.put(port, graphDefault(caseType,
                            AdvancedGraphPortState.defaultValue(caseType)));
                }
            }
            String defaultType = AdvancedGraphPortState.switchCaseType(data, "default");
            types.putString("default", defaultType);
            if (!defaults.contains("default")) {
                defaults.put("default", graphDefault(defaultType,
                        AdvancedGraphPortState.defaultValue(defaultType)));
            }
            data.put("DynamicInputs", inputs);
            data.put("Defaults", defaults);
            data.put(AdvancedGraphPortState.SWITCH_CASE_TYPES_TAG, types);
            data.remove("DynamicOutputs");
            return;
        }
        CompoundTag inputs = data.getCompound("DynamicInputs");
        inputs.getAllKeys().stream()
                .filter(port -> port.startsWith("case_"))
                .toList().forEach(inputs::remove);
        if (inputs.isEmpty()) data.remove("DynamicInputs");
        else data.put("DynamicInputs", inputs);
        CompoundTag outputs = new CompoundTag();
        for (int idx = 0; idx < clamped; idx++) {
            outputs.putString(executionOutputPort(type, idx), "exec");
        }
        data.put("DynamicOutputs", outputs);
    }

    // Check if the execution ports match
    private static boolean executionPortsMatch(
            AdvancedGraphDocument.Node node, CompoundTag ports, int count) {
        long casePortCount = ports.getAllKeys().stream()
                .filter(port -> port.startsWith(
                        "switch".equals(node.type()) ? "case_" : "exec_"))
                .count();
        if (casePortCount != count) return false;
        for (int idx = 0; idx < count; idx++) {
            String port = executionOutputPort(node.type(), idx);
            String expected = AdvancedGraphCatalog.switchDataMode(node)
                    ? AdvancedGraphPortState.switchCaseType(node, port)
                    : AdvancedGraphCatalog.switchOutputType(node);
            if (!expected.equals(ports.getString(port))) return false;
        }
        return true;
    }

    // Get the execution output port
    private static String executionOutputPort(String type, int zeroBasedIndex) {
        return "switch".equals(type) ? "case_" + zeroBasedIndex : "exec_" + (zeroBasedIndex + 1);
    }

    // Check if the execution output was removed
    private static boolean removedExecutionOutput(String type, String port, int count) {
        String prefix = "switch".equals(type) ? "case_" : "exec_";
        if (port == null || !port.startsWith(prefix)) return false;
        try {
            int idx = Integer.parseInt(port.substring(prefix.length()));
            return "switch".equals(type) ? idx >= count : idx > count;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // Check if this is an execution combiner type
    private static boolean isExecutionCombinerType(String type) {
        return "exec_combine".equals(type);
    }

    // Check if this is an execution combiner node
    private static boolean isExecutionCombinerNode(AdvancedGraphDocument.Node node) {
        return node != null && isExecutionCombinerType(node.type());
    }

    // Get the execution input count
    private static int executionInputCount(AdvancedGraphDocument.Node node) {
        if (node == null) return MIN_EXECUTION_INPUTS;
        int count = node.data().contains("InputCount", Tag.TAG_INT)
                ? node.data().getInt("InputCount")
                : MIN_EXECUTION_INPUTS;
        return Mth.clamp(count, MIN_EXECUTION_INPUTS, MAX_EXECUTION_INPUTS);
    }

    // Set the execution input count
    private void setExecutionInputCount(AdvancedGraphDocument.Node node, int count, boolean pruneEdges) {
        if (node == null) return;
        int clamped = Mth.clamp(count, MIN_EXECUTION_INPUTS, MAX_EXECUTION_INPUTS);
        node.data().putInt("InputCount", clamped);
        putExecutionInputs(node.data(), clamped);
        if (pruneEdges && draft != null) {
            activeEdges().removeIf(edge -> edge.toNode().equals(node.id())
                    && removedExecutionInput(edge.toPort(), clamped));
        }
    }

    // Put the execution inputs
    private static void putExecutionInputs(CompoundTag data, int count) {
        CompoundTag inputs = new CompoundTag();
        int clamped = Mth.clamp(count, MIN_EXECUTION_INPUTS, MAX_EXECUTION_INPUTS);
        for (int idx = 1; idx <= clamped; idx++) {
            inputs.putString("exec_" + idx, "exec");
        }
        data.put("DynamicInputs", inputs);
    }

    // Check if the execution inputs match
    private static boolean executionInputsMatch(CompoundTag inputs, int count) {
        if (inputs == null || inputs.getAllKeys().size() != count) return false;
        for (int idx = 1; idx <= count; idx++) {
            if (!"exec".equals(inputs.getString("exec_" + idx))) return false;
        }
        return true;
    }

    // Check if the execution input was removed
    private static boolean removedExecutionInput(String port, int count) {
        if (port == null || !port.startsWith("exec_")) return false;
        try {
            return Integer.parseInt(port.substring("exec_".length())) > count;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HUD DESIGNER
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the HUD bounds
    private UiRect hudBounds() {
        int width = Math.min(HUD_WIDTH, Math.max(480, this.width - 60));
        int height = Math.min(HUD_HEIGHT, Math.max(300, this.height - 20));
        return new UiRect((this.width - width) / 2, (this.height - height) / 2, width, height);
    }

    // Get the HUD canvas bounds
    private UiRect hudCanvasBounds() {
        UiRect bounds = hudBounds();
        return new UiRect(bounds.x() + 12, bounds.y() + 68, bounds.width() - 250, bounds.height() - 80);
    }

    // Get the HUD sidebar bounds
    private UiRect hudSidebarBounds() {
        UiRect bounds = hudBounds();
        return new UiRect(bounds.right() - 226, bounds.y() + 68, 214, bounds.height() - 80);
    }

    // Get the HUD btn bounds
    private UiRect hudBtnBounds() {
        int x = graphRight() + 10;
        return new UiRect(x, 146, RIGHT_WIDTH - 20, 20);
    }

    // Get the image reference upload button bounds
    private UiRect imageReferenceUploadButtonBounds() {
        int x = graphRight() + 10;
        return new UiRect(x, 146, RIGHT_WIDTH - 20, 20);
    }

    // Check if the pointer is in the HUD
    private boolean inHud(double mouseX, double mouseY) {
        return hudOpen && hudBounds().contains(mouseX, mouseY);
    }

    // Get the HUD node
    private AdvancedGraphDocument.Node hudNode() {
        return findNode(hudNodeId);
    }

    // Get the HUD element
    private CompoundTag hudElement() {
        AdvancedGraphDocument.Node node = hudNode();
        if (!isAdvancedHudNode(node)) return null;
        ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
        if (hudSelectedIdx < 0 || hudSelectedIdx >= elements.size()) return null;
        return elements.getCompound(hudSelectedIdx);
    }

    // Open the HUD
    private void openHud(AdvancedGraphDocument.Node node) {
        if (!isAdvancedHudNode(node)) return;
        clearTextFocus();
        hudOpen = true;
        hudNodeId = node.id();
        hudPortDropdownOpen = false;
        hudPortDropdownScroll = 0;
        draggingHudColorKey = null;
        draggingHudColorControl = null;
        draggingHudPropSlider = null;
        hudFontStyleDropdownOpen = false;
        hudStyleDropdownOpen = false;
        hudImageDropdownOpen = false;
        hudImageDropdownScroll = 0;
        hudBindingDropdownKey = null;
        hudBindingDropdownScroll = 0;
        hudImageStatus = "";
        if (hudSelectedIdx < 0) {
            ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
            hudSelectedIdx = elements.isEmpty() ? -1 : elements.size() - 1;
        }
        AdvancedHudImageClient.requestCatalog();
        layoutHudWidgets();
        syncHudFields();
    }

    // Close the HUD
    private void closeHud() {
        clearHudFieldFocus();
        hudOpen = false;
        hudNodeId = null;
        hudSelectedIdx = -1;
        draggingHudElement = false;
        resizingHudElement = false;
        hudPortDropdownOpen = false;
        hudPortDropdownScroll = 0;
        draggingHudColorKey = null;
        draggingHudColorControl = null;
        draggingHudPropSlider = null;
        hudFontStyleDropdownOpen = false;
        hudStyleDropdownOpen = false;
        hudImageDropdownOpen = false;
        hudImageDropdownScroll = 0;
        hudBindingDropdownKey = null;
        hudBindingDropdownScroll = 0;
        hudImageStatus = "";
        layoutHudWidgets();
    }

    // Lay out the HUD widgets
    private void layoutHudWidgets() {
        CompoundTag elm = hudElement();
        String type = elm == null ? "" : elm.getString("Type");
        boolean textVisible = hudOpen && !hudDataBindings
                && ("text".equals(type) || "button".equals(type) || "toggle".equals(type)
                || "text_input".equals(type));
        boolean boxVisible = hudOpen && "box".equals(type);
        boxVisible = boxVisible && !hudDataBindings;
        boolean textureVisible = hudOpen && !hudDataBindings
                && "image".equals(type) && !hudPortDropdownOpen;
        boolean sizeVisible = hudOpen && !hudDataBindings
                && ("text".equals(type) || "value".equals(type) || "box".equals(type)
                || "image".equals(type) && !hudPortDropdownOpen
                || "button".equals(type) || "toggle".equals(type) || "slider".equals(type)
                || "progress".equals(type)
                || "text_input".equals(type));
        UiRect sidebar = hudSidebarBounds();
        int fieldX = sidebar.x() + 8;
        int fieldWidth = sidebar.width() - 16;
        int propertyTop = hudPropTop(sidebar);

        hudText.setX(fieldX);
        hudText.setY(propertyTop + 14);
        hudText.setWidth(fieldWidth);
        hudText.setVisible(textVisible);

        hudTexture.setX(fieldX);
        hudTexture.setY(propertyTop + 56);
        hudTexture.setWidth(fieldWidth);
        hudTexture.setVisible(textureVisible);

        int compactFieldWidth = (fieldWidth - 12) / 4;
        int sizeFieldY = hudSizeFieldY(sidebar, type);
        hudWidth.setX(fieldX);
        hudWidth.setY(sizeFieldY);
        hudWidth.setWidth(compactFieldWidth);
        hudWidth.setVisible(sizeVisible);

        hudHeight.setX(fieldX + compactFieldWidth + 4);
        hudHeight.setY(sizeFieldY);
        hudHeight.setWidth(compactFieldWidth);
        hudHeight.setVisible(sizeVisible);

        hudRotation.setX(fieldX + (compactFieldWidth + 4) * 2);
        hudRotation.setY(sizeFieldY);
        hudRotation.setWidth(compactFieldWidth);
        hudRotation.setVisible(sizeVisible);

        hudScale.setX(fieldX + (compactFieldWidth + 4) * 3);
        hudScale.setY(sizeFieldY);
        hudScale.setWidth(compactFieldWidth);
        hudScale.setVisible(sizeVisible);

        hudBorderWidth.setX(fieldX + fieldWidth - 64);
        hudBorderWidth.setY(propertyTop + 91);
        hudBorderWidth.setWidth(64);
        hudBorderWidth.setVisible(boxVisible);
    }

    // Sync the HUD fields
    private void syncHudFields() {
        layoutHudWidgets();
        syncingHudFields = true;
        CompoundTag elm = hudElement();
        if (elm == null) {
            for (EditBox field : hudFields()) {
                field.setValue("");
            }
            syncingHudFields = false;
            return;
        }
        AdvancedHudElementStyle.applyDefaults(elm);
        hudText.setValue(elm.getString("Text"));
        hudTexture.setValue(elm.getString("Texture"));
        hudWidth.setValue(Integer.toString(Math.max(1, elm.getInt("W"))));
        hudHeight.setValue(Integer.toString(Math.max(1, elm.getInt("H"))));
        hudRotation.setValue(compactNumber(elm.getDouble("Rotation")));
        hudScale.setValue(compactNumber(elm.getDouble("Scale")));
        hudBorderWidth.setValue(Integer.toString(AdvancedHudElementStyle.borderWidth(elm)));
        syncingHudFields = false;
    }

    // Get the HUD fields
    private List<EditBox> hudFields() {
        return List.of(hudText, hudTexture, hudWidth, hudHeight,
                hudRotation, hudScale, hudBorderWidth);
    }

    // Update the HUD text field
    private void updateHudTextField(String key, String val) {
        if (syncingHudFields) return;
        CompoundTag elm = hudElement();
        if (elm == null) return;
        elm.putString(key, val == null ? "" : val);
        updateManagedAccWidgetDefault(elm, key,
                graphDefault("string", val == null ? "" : val));
    }

    // Update the HUD num field
    private void updateHudNumField(String key, String val) {
        if (syncingHudFields) return;
        CompoundTag elm = hudElement();
        if (elm == null) return;
        try {
            int parsed = Math.max(1, Integer.parseInt(val.trim()));
            elm.putInt(key, parsed);
            updateManagedAccWidgetDefault(elm, key, graphDefault("number", parsed));
        } catch (RuntimeException ignored) {
        }
    }

    // Update the HUD clamped num field
    private void updateHudClampedNumField(String key, String val, int minimum, int maximum) {
        if (syncingHudFields) return;
        CompoundTag elm = hudElement();
        if (elm == null) return;
        try {
            int parsed = Mth.clamp(Integer.parseInt(val.trim()), minimum, maximum);
            elm.putInt(key, parsed);
            updateManagedAccWidgetDefault(elm, key, graphDefault("number", parsed));
        } catch (RuntimeException ignored) {
        }
    }

    // Draw the HUD
    private void drawHud(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect bounds = hudBounds();
        UiRect canvas = hudCanvasBounds();
        UiRect sidebar = hudSidebarBounds();
        graphics.fill(0, 0, width, height, 0x88000000);
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.drawString(font, "Advanced HUD Designer", bounds.x() + 12, bounds.y() + 12, 0xFFFFFFFF, false);

        UiRect visualTab = hudPaletteTab(false);
        UiRect interactiveTab = hudPaletteTab(true);
        renderAdvancedButton(graphics, font, visualTab.x(), visualTab.y(), visualTab.width(), visualTab.height(),
                Component.literal("Visual"), visualTab.contains(mouseX, mouseY), !hudInteractivePalette);
        renderAdvancedButton(graphics, font, interactiveTab.x(), interactiveTab.y(),
                interactiveTab.width(), interactiveTab.height(), Component.literal("Interactive"),
                interactiveTab.contains(mouseX, mouseY), hudInteractivePalette);

        int actionCount = hudInteractivePalette ? 4 : 5;
        for (int i = 0; i < actionCount; i++) {
            UiRect btn = hudActionBtn(i);
            renderAdvancedButton(graphics, font, btn.x(), btn.y(), btn.width(), btn.height(),
                    Component.literal(hudInteractivePalette
                            ? switch (i) {
                        case 0 -> "Button";
                        case 1 -> "Toggle";
                        case 2 -> "Slider";
                        default -> "Text Input";
                    }
                            : switch (i) {
                        case 0 -> "Text";
                        case 1 -> "Value";
                        case 2 -> "Box";
                        case 3 -> "Image";
                        default -> "Progress";
                    }), inside(mouseX, mouseY, btn.x(), btn.y(), btn.width(), btn.height()), true);
        }
        UiRect remove = hudRemoveBtn();
        renderAdvancedButton(graphics, font, remove.x(), remove.y(), remove.width(), remove.height(),
                Component.literal("Remove"), inside(mouseX, mouseY, remove.x(), remove.y(), remove.width(), remove.height()), true);
        UiRect close = hudCloseBtn();
        renderAdvancedButton(graphics, font, close.x(), close.y(), close.width(), close.height(),
                Component.literal("Close"), inside(mouseX, mouseY, close.x(), close.y(), close.width(), close.height()), true);

        graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), 0xFF111923);
        graphics.renderOutline(canvas.x(), canvas.y(), canvas.width(), canvas.height(), 0xFF3D637E);
        renderSidebarPanel(graphics, sidebar.x(), sidebar.y(), sidebar.width(), sidebar.height());
        graphics.renderOutline(sidebar.x(), sidebar.y(), sidebar.width(), sidebar.height(), 0xFF3D637E);
        graphics.drawString(font, "Canvas", canvas.x() + 8, canvas.y() - 12, 0xFF91D9FF, false);
        graphics.drawString(font, "Selected Element", sidebar.x() + 8, sidebar.y() + 8, 0xFF91D9FF, false);

        AdvancedGraphDocument.Node node = hudNode();
        if (!isAdvancedHudNode(node)) return;
        double scale = hudCanvasScale(node, canvas);
        int previewWidth = (int) Math.round(Math.max(1, node.data().getInt("WidgetWidth")) * scale);
        int previewHeight = (int) Math.round(Math.max(1, node.data().getInt("WidgetHeight")) * scale);
        int previewX = canvas.x() + (canvas.width() - previewWidth) / 2;
        int previewY = canvas.y() + (canvas.height() - previewHeight) / 2;
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF0E151D);
        graphics.renderOutline(previewX, previewY, previewWidth, previewHeight, 0xFF64879D);

        // ------------------------------------CANVAS PREVIEW------------------------------------

        ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < elements.size(); i++) {
            CompoundTag elm = AdvancedHudElementBinding.resolvedCopy(
                    elements.getCompound(i), port -> hudDataVal(node, port));
            AdvancedHudElementStyle.applyDefaults(elm);
            if (elm.contains("Visible", Tag.TAG_BYTE) && !elm.getBoolean("Visible")) {
                continue;
            }
            UiRect elementRect = hudElementRect(node, canvas, elm);
            float elementScale = (float) Mth.clamp(elm.getDouble("Scale"), 0.01D, 100.0D);
            graphics.pose().pushPose();
            graphics.pose().translate(elementRect.x() + elementRect.width() * 0.5F,
                    elementRect.y() + elementRect.height() * 0.5F, 0.0F);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                    (float) elm.getDouble("Rotation")));
            graphics.pose().scale(elementScale, elementScale, 1.0F);
            graphics.pose().translate(-elementRect.x() - elementRect.width() * 0.5F,
                    -elementRect.y() - elementRect.height() * 0.5F, 0.0F);
            switch (elm.getString("Type")) {
                case "box" -> AdvancedHudElementRenderer.drawBox(graphics, elm,
                        elementRect.x(), elementRect.y(), elementRect.width(), elementRect.height(), scale);
                case "image" -> {
                    if (!AdvancedHudImageClient.draw(graphics, elm.getString("Texture"),
                            elementRect.x(), elementRect.y(), elementRect.width(), elementRect.height())) {
                        graphics.fill(elementRect.x(), elementRect.y(), elementRect.right(), elementRect.bottom(),
                                0x66374756);
                        graphics.drawString(font, "IMG", elementRect.x() + 4, elementRect.y() + 4,
                                0xFFFFFFFF, false);
                    }
                }
                case "value" -> AdvancedHudElementRenderer.drawText(graphics, font, elm,
                        designerHudValueLabel(node,
                                elm.getString("Port").isBlank() ? "value" : elm.getString("Port")),
                        elementRect.x(), elementRect.y(), elementRect.width(), elementRect.height(), scale);
                case "button", "toggle", "slider", "text_input" -> drawHudInteractive(
                        graphics, elm, elementRect, scale);
                case "progress" -> drawHudProgress(graphics, node, elm, elementRect);
                default -> AdvancedHudElementRenderer.drawText(graphics, font, elm, elm.getString("Text"),
                        elementRect.x(), elementRect.y(), elementRect.width(), elementRect.height(), scale);
            }
            graphics.pose().popPose();
            graphics.renderOutline(elementRect.x(), elementRect.y(), elementRect.width(), elementRect.height(),
                    i == hudSelectedIdx ? 0xFFFFFFFF : 0xFF59C58B);
            if (i == hudSelectedIdx) {
                graphics.fill(elementRect.right() - 5, elementRect.bottom() - 5, elementRect.right(), elementRect.bottom(), 0xFFFFFFFF);
            }
        }

        // -----------------------------------------------------SELECTED WIDGET---------------------------------------------------

        CompoundTag selected = hudElement();
        if (selected == null) {
            graphics.drawString(font, "No widget selected.", sidebar.x() + 8, sidebar.y() + 30, 0xFF91A9B8, false);
            return;
        }
        String type = selected.getString("Type");
        graphics.drawString(font, "Type: " + humanPort(type), sidebar.x() + 8, sidebar.y() + 30,
                0xFFFFFFFF, false);
        graphics.drawString(font, "Drag to move; corner to resize.", sidebar.x() + 8, sidebar.y() + 46,
                0xFF91A9B8, false);
        graphics.drawString(font, "Order: " + (hudSelectedIdx + 1) + " of " + elements.size()
                        + " (front is highest)", sidebar.x() + 8, sidebar.y() + 62, 0xFFC8D7E3, false);
        UiRect lower = hudLayerBtn(-1);
        UiRect raise = hudLayerBtn(1);
        renderAdvancedButton(graphics, font, lower.x(), lower.y(), lower.width(), lower.height(),
                Component.literal("Lower"), lower.contains(mouseX, mouseY), hudSelectedIdx > 0);
        renderAdvancedButton(graphics, font, raise.x(), raise.y(), raise.width(), raise.height(),
                Component.literal("Raise"), raise.contains(mouseX, mouseY),
                hudSelectedIdx < elements.size() - 1);

        UiRect propertiesTab = hudInspectorTab(false);
        UiRect dataTab = hudInspectorTab(true);
        renderAdvancedButton(graphics, font, propertiesTab.x(), propertiesTab.y(),
                propertiesTab.width(), propertiesTab.height(), Component.literal("Properties"),
                propertiesTab.contains(mouseX, mouseY), !hudDataBindings);
        renderAdvancedButton(graphics, font, dataTab.x(), dataTab.y(),
                dataTab.width(), dataTab.height(), Component.literal("Data"),
                dataTab.contains(mouseX, mouseY), hudDataBindings);
        if (hudDataBindings) {
            drawHudBindings(graphics, node, selected, mouseX, mouseY);
            return;
        }

        int propertyTop = hudPropTop(sidebar);
        switch (type) {
            case "value" -> {
                graphics.drawString(font, "Port", sidebar.x() + 8, propertyTop, 0xFFC8D7E3, false);
                drawHudPortBtn(graphics, node, selected, mouseX, mouseY);
                graphics.drawString(font, "Field type: " + humanPort(hudPortType(
                                node, selected.getString("Port"))),
                        sidebar.x() + 8, propertyTop + 38, 0xFF91A9B8, false);
                graphics.drawString(font, "Font size: " + AdvancedHudElementStyle.fontSize(selected),
                        sidebar.x() + 8, propertyTop + 54, 0xFFC8D7E3, false);
                drawHudPropSlider(graphics, hudFontSizeSliderBounds(),
                        AdvancedHudElementStyle.fontSize(selected), AdvancedHudElementStyle.MIN_FONT_SIZE,
                        AdvancedHudElementStyle.MAX_FONT_SIZE, "FontSize", mouseX, mouseY);
                drawHudColorSliders(graphics, selected, "Color", "Text color",
                        propertyTop + 84, mouseX, mouseY);
                graphics.drawString(font, "Font style", sidebar.x() + 8, propertyTop + 132,
                        0xFFC8D7E3, false);
                drawHudFontStyleBtn(graphics, selected, mouseX, mouseY);
                graphics.drawString(font, "W / H / Rotation / Scale",
                        sidebar.x() + 8, propertyTop + 176, 0xFFC8D7E3, false);
            }
            case "box" -> {
                drawHudColorSliders(graphics, selected, "Color", "Background color",
                        propertyTop, mouseX, mouseY);
                drawHudColorSliders(graphics, selected, "BorderColor", "Border color",
                        propertyTop + 46, mouseX, mouseY);
                graphics.drawString(font, "Border width", sidebar.x() + 8, propertyTop + 96,
                        0xFFC8D7E3, false);
                graphics.drawString(font, "Border radius: " + AdvancedHudElementStyle.borderRadius(selected),
                        sidebar.x() + 8, propertyTop + 116,
                        0xFFC8D7E3, false);
                drawHudPropSlider(graphics, hudBorderRadiusSliderBounds(),
                        AdvancedHudElementStyle.borderRadius(selected), 0,
                        AdvancedHudElementStyle.MAX_BORDER_RADIUS, "BorderRadius", mouseX, mouseY);
                graphics.drawString(font, "W / H / Rotation / Scale", sidebar.x() + 8, propertyTop + 148,
                        0xFFC8D7E3, false);
            }
            case "image" -> {
                graphics.drawString(font, "Port", sidebar.x() + 8, propertyTop, 0xFFC8D7E3, false);
                drawHudPortBtn(graphics, node, selected, mouseX, mouseY);
                graphics.drawString(font, "Texture / URL fallback", sidebar.x() + 8, propertyTop + 42,
                        0xFFC8D7E3, false);
                UiRect upload = hudImageUploadBtnBounds();
                UiRect library = hudImageLibraryBtnBounds();
                boolean uploadPending = hudPendingImageNodeId != null;
                renderAdvancedButton(graphics, font, upload.x(), upload.y(), upload.width(), upload.height(),
                        Component.literal(uploadPending ? "Uploading..." : "Upload"),
                        upload.contains(mouseX, mouseY), !uploadPending);
                String libraryLabel = AdvancedHudImageClient.uploadedImages().isEmpty()
                        ? "Uploaded (none)" : "Uploaded";
                renderAdvancedButton(graphics, font, library.x(), library.y(), library.width(), library.height(),
                        Component.literal(libraryLabel + (hudImageDropdownOpen ? " ^" : " v")),
                        library.contains(mouseX, mouseY), !AdvancedHudImageClient.uploadedImages().isEmpty());
                graphics.drawString(font, "W / H / Rotation / Scale", sidebar.x() + 8, propertyTop + 108,
                        0xFFC8D7E3, false);
                graphics.drawString(font, "Fallback is used for an empty port.",
                        sidebar.x() + 8, propertyTop + 146, 0xFF91A9B8, false);
                if (!hudImageStatus.isBlank()) {
                    graphics.drawString(font, trim(hudImageStatus, 31),
                            sidebar.x() + 8, propertyTop + 162, 0xFFC8D7E3, false);
                }
            }
            case "slider", "progress" -> {
                graphics.drawString(font, "progress".equals(type) ? "Port" : "Style",
                        sidebar.x() + 8, propertyTop, 0xFFC8D7E3, false);
                if ("progress".equals(type)) {
                    drawHudPortBtn(graphics, node, selected, mouseX, mouseY);
                } else {
                    drawHudStyleBtn(graphics, selected, mouseX, mouseY);
                }
                graphics.drawString(font, "Range: " + compactNumber(selected.getDouble("Min"))
                                + " to " + compactNumber(selected.getDouble("Max")),
                        sidebar.x() + 8, propertyTop + 42, 0xFFC8D7E3, false);
                graphics.drawString(font, "Step: " + compactNumber(selected.getDouble("Step")),
                        sidebar.x() + 8, propertyTop + 58, 0xFF91A9B8, false);
                graphics.drawString(font, "W / H / Rotation / Scale", sidebar.x() + 8, propertyTop + 80,
                        0xFFC8D7E3, false);
            }
            default -> {
                graphics.drawString(font, "Text", sidebar.x() + 8, propertyTop, 0xFFC8D7E3, false);
                graphics.drawString(font, "Font size: " + AdvancedHudElementStyle.fontSize(selected),
                        sidebar.x() + 8, propertyTop + 38,
                        0xFFC8D7E3, false);
                drawHudPropSlider(graphics, hudFontSizeSliderBounds(),
                        AdvancedHudElementStyle.fontSize(selected), AdvancedHudElementStyle.MIN_FONT_SIZE,
                        AdvancedHudElementStyle.MAX_FONT_SIZE, "FontSize", mouseX, mouseY);
                drawHudColorSliders(graphics, selected, "Color", "Text color",
                        propertyTop + 68, mouseX, mouseY);
                boolean interactive = "button".equals(type) || "toggle".equals(type)
                        || "text_input".equals(type);
                graphics.drawString(font, "Font style", sidebar.x() + 8, propertyTop + 116,
                        0xFFC8D7E3, false);
                if (interactive) {
                    int styleLabelX = sidebar.x() + 16 + (sidebar.width() - 24) / 2;
                    graphics.drawString(font, "Style", styleLabelX, propertyTop + 116,
                            0xFFC8D7E3, false);
                }
                drawHudFontStyleBtn(graphics, selected, mouseX, mouseY);
                if (interactive) {
                    drawHudStyleBtn(graphics, selected, mouseX, mouseY);
                }
                graphics.drawString(font, "W / H / Rotation / Scale", sidebar.x() + 8, propertyTop + 154,
                        0xFFC8D7E3, false);
            }
        }
        if (hudPortDropdownOpen && ("value".equals(type) || "image".equals(type)
                || "progress".equals(type))) {
            drawHudPortDropdown(graphics, node, selected, mouseX, mouseY);
        }
        if (hudFontStyleDropdownOpen && ("text".equals(type) || "value".equals(type)
                || "button".equals(type) || "toggle".equals(type))) {
            drawHudFontStyleDropdown(graphics, selected, mouseX, mouseY);
        }
        if (hudStyleDropdownOpen && isInteractiveHudType(type)) {
            drawHudStyleDropdown(graphics, selected, mouseX, mouseY);
        }
        if (hudImageDropdownOpen && "image".equals(type)) {
            drawHudImageDropdown(graphics, selected, mouseX, mouseY);
        }
    }

    // Draw the HUD interactive
    private void drawHudInteractive(
            GuiGraphics graphics, CompoundTag elm, UiRect rect, double scale) {
        String type = elm.getString("Type");
        boolean toggleActive = "toggle".equals(type) && elm.getBoolean("Value");
        AdvancedHudInteractiveStyleRenderer.draw(
                graphics, font, elm, rect.x(), rect.y(), rect.width(), rect.height(), scale,
                elm.getDouble("Value"), toggleActive, false);
    }

    // Update the HUD double field
    private void updateHudDoubleField(
            String key, String val, double minimum, double maximum) {
        if (syncingHudFields) return;
        CompoundTag elm = hudElement();
        if (elm == null) return;
        try {
            double parsed = Mth.clamp(Double.parseDouble(val.trim()), minimum, maximum);
            elm.putDouble(key, parsed);
            updateManagedAccWidgetDefault(elm, key, graphDefault("number", parsed));
        } catch (RuntimeException ignored) {
        }
    }

    // Update the managed ACC widget default
    private void updateManagedAccWidgetDefault(
            CompoundTag elm, String property, CompoundTag val) {
        if (elm == null || !elm.getBoolean("ManagedWidget")) return;
        String port = switch (property) {
            case "Text" -> "label";
            case "X" -> "x";
            case "Y" -> "y";
            case "W" -> "width";
            case "H" -> "height";
            case "Rotation" -> "rotation";
            case "Scale" -> "scale";
            case "FontSize" -> "font_size";
            case "Color" -> "color";
            case "BackgroundColor" -> "background_color";
            case "AccentColor" -> "accent_color";
            case "TrackColor" -> "track_color";
            case "BorderColor" -> "border_color";
            case "BorderWidth" -> "border_width";
            case "BorderRadius" -> "border_radius";
            case "Min" -> "minimum";
            case "Max" -> "maximum";
            default -> "";
        };
        AdvancedGraphDocument.Node node = hudNode();
        if (node == null || port.isBlank()) return;
        CompoundTag defaults = node.data().getCompound("Defaults");
        defaults.put(port, val);
        node.data().put("Defaults", defaults);
    }

    // Draw the HUD progress
    private void drawHudProgress(
            GuiGraphics graphics, AdvancedGraphDocument.Node node,
            CompoundTag elm, UiRect rect) {
        double minimum = elm.getDouble("Min");
        double maximum = elm.getDouble("Max");
        if (!(maximum > minimum)) maximum = minimum + 1.0D;
        String port = elm.getString("Port");
        AdvancedGraphDocument.Value val = hudDataVal(node,
                port.isBlank() ? "value" : port);
        double pct = Mth.clamp(((val == null ? elm.getDouble("Value") : val.asNumber())
                - minimum) / (maximum - minimum), 0.0D, 1.0D);
        int background = AdvancedHudElementStyle.color(elm, "TrackColor",
                AdvancedHudElementStyle.DEFAULT_WIDGET_TRACK_COLOR);
        int accent = AdvancedHudElementStyle.color(elm, "AccentColor",
                AdvancedHudElementStyle.DEFAULT_WIDGET_ACCENT_COLOR);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), background);
        graphics.fill(rect.x(), rect.y(),
                rect.x() + (int) Math.round(rect.width() * pct), rect.bottom(), accent);
        String label = Math.round(pct * 100.0D) + "%";
        graphics.drawCenteredString(font, label, rect.x() + rect.width() / 2,
                rect.y() + Math.max(0, (rect.height() - font.lineHeight) / 2),
                AdvancedHudElementStyle.color(elm, "Color",
                        AdvancedHudElementStyle.DEFAULT_TEXT_COLOR));
    }

    // Get the HUD data val
    private AdvancedGraphDocument.Value hudDataVal(
            AdvancedGraphDocument.Node node, String port) {
        AdvancedGraphLiveValue live = simulatedLiveInput(node.id(), port);
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if (live == null && controller != null) {
            live = controller.getGraphLiveInput(node.id(), port);
        }
        if (live != null) {
            return switch (live.type()) {
                case "number" -> AdvancedGraphDocument.Value.number(live.numberValue());
                case "boolean" -> AdvancedGraphDocument.Value.bool(live.booleanValue());
                case "direction" -> AdvancedGraphDocument.Value.direction(live.textValue());
                default -> AdvancedGraphDocument.Value.string(live.textValue());
            };
        }
        return controller == null ? null : controller.previewGraphInput(draft, node, port);
    }

    // Get the HUD bindable props
    private List<String> hudBindableProps(CompoundTag elm) {
        if (elm == null) return List.of();
        List<String> properties = new ArrayList<>(List.of(
                "X", "Y", "W", "H", "Rotation", "Scale", "Visible"));
        switch (elm.getString("Type")) {
            case "text" -> properties.addAll(List.of("Text", "Color", "FontSize"));
            case "button", "toggle", "text_input" -> properties.addAll(List.of(
                    "Text", "Color", "BackgroundColor", "AccentColor", "TrackColor",
                    "BorderColor", "BorderWidth", "BorderRadius", "FontSize"));
            case "value" -> properties.addAll(List.of("Color", "FontSize"));
            case "box" -> properties.addAll(List.of(
                    "Color", "BorderColor", "BorderWidth", "BorderRadius"));
            case "image" -> properties.add("Texture");
            case "slider" -> properties.addAll(List.of(
                    "Color", "BackgroundColor", "AccentColor", "TrackColor",
                    "BorderColor", "BorderWidth", "BorderRadius", "FontSize",
                    "Min", "Max", "Step", "Value"));
            case "progress" -> properties.addAll(List.of(
                    "Color", "BackgroundColor", "AccentColor", "TrackColor",
                    "BorderColor", "BorderWidth", "BorderRadius", "FontSize",
                    "Min", "Max", "Value"));
            default -> {
            }
        }
        return properties;
    }

    // Draw the HUD bindings
    private void drawHudBindings(
            GuiGraphics graphics, AdvancedGraphDocument.Node node, CompoundTag elm,
            int mouseX, int mouseY) {
        UiRect sidebar = hudSidebarBounds();
        int y = hudPropTop(sidebar);
        List<String> properties = hudBindableProps(elm);
        int visibleRows = hudBindingVisibleRows();
        hudBindingPropScroll = Mth.clamp(hudBindingPropScroll,
                0, Math.max(0, properties.size() - visibleRows));
        int end = Math.min(properties.size(), hudBindingPropScroll + visibleRows);
        for (int idx = hudBindingPropScroll; idx < end; idx++) {
            String property = properties.get(idx);
            graphics.drawString(font, trim(humanPort(property), 10),
                    sidebar.x() + 8, y + 5, 0xFFC8D7E3, false);
            UiRect btn = hudBindingBtnBounds(property);
            String port = AdvancedHudElementBinding.port(elm, property);
            String label = port.isBlank() ? "Static" : hudFieldLabel(node, port);
            renderAdvancedButton(graphics, font, btn.x(), btn.y(),
                    btn.width(), btn.height(), Component.literal(trim(label, 15)),
                    btn.contains(mouseX, mouseY), true);
            y += 19;
        }
        if (properties.size() > visibleRows) {
            graphics.drawString(font,
                    (hudBindingPropScroll + 1) + "-" + end + " / " + properties.size(),
                    sidebar.right() - 48, sidebar.bottom() - 10, 0xFF91A9B8, false);
        }
        if (hudBindingDropdownKey != null) {
            drawHudBindingDropdown(graphics, node, elm, mouseX, mouseY);
        }
    }

    // Get the HUD binding btn bounds
    private UiRect hudBindingBtnBounds(String property) {
        UiRect sidebar = hudSidebarBounds();
        int idx = hudBindableProps(hudElement()).indexOf(property)
                - hudBindingPropScroll;
        return new UiRect(sidebar.x() + 78,
                hudPropTop(sidebar) + idx * 19,
                sidebar.width() - 86, 17);
    }

    // Get the HUD binding visible rows
    private int hudBindingVisibleRows() {
        UiRect sidebar = hudSidebarBounds();
        return Math.max(1, (sidebar.bottom() - hudPropTop(sidebar) - 12) / 19);
    }

    // Get the HUD binding dropdown bounds
    private UiRect hudBindingDropdownBounds(int visibleRows) {
        UiRect btn = hudBindingBtnBounds(hudBindingDropdownKey);
        UiRect modal = hudBounds();
        int width = 180;
        int height = visibleRows * 18 + 4;
        int x = Math.max(modal.x() + 4, btn.x() - width - 8);
        int y = Mth.clamp(btn.y(), modal.y() + 64, modal.bottom() - height - 4);
        return new UiRect(x, y, width, height);
    }

    // Get the HUD binding ports
    private List<String> hudBindingPorts(AdvancedGraphDocument.Node node) {
        List<String> ports = new ArrayList<>();
        ports.add("");
        if (isAdvancedHudNode(node)) {
            AdvancedGraphCatalog.inputs(node).entrySet().stream()
                    .filter(entry -> !"exec".equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .forEach(ports::add);
        }
        return ports;
    }

    // Draw the HUD binding dropdown
    private void drawHudBindingDropdown(
            GuiGraphics graphics, AdvancedGraphDocument.Node node, CompoundTag elm,
            int mouseX, int mouseY) {
        List<String> ports = hudBindingPorts(node);
        int visibleRows = Math.min(HUD_PORT_VISIBLE_ROWS, ports.size());
        int maximum = Math.max(0, ports.size() - visibleRows);
        hudBindingDropdownScroll = Mth.clamp(
                hudBindingDropdownScroll, 0, maximum);
        UiRect bounds = hudBindingDropdownBounds(visibleRows);
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        String selectedPort = AdvancedHudElementBinding.port(elm, hudBindingDropdownKey);
        int rowY = bounds.y() + 2;
        for (int row = 0; row < visibleRows; row++) {
            String port = ports.get(hudBindingDropdownScroll + row);
            boolean selected = port.equals(selectedPort);
            boolean hovered = inside(mouseX, mouseY, bounds.x() + 2, rowY, bounds.width() - 4, 16);
            if (selected || hovered) {
                renderControllerOption(graphics, bounds.x() + 2, rowY, bounds.width() - 4, 16,
                        port.isBlank() ? AdvancedGraphCatalog.categoryColor("hud")
                                : portColor(hudPortType(node, port)), selected);
            }
            String label = port.isBlank() ? "Static"
                    : hudPortOptionLabel(node, port);
            graphics.drawString(font, trim(label, 24), bounds.x() + 7, rowY + 4,
                    selected ? nodeValueTextColor() : 0xFFE0EDF4, false);
            rowY += 18;
        }
    }

    // Handle the HUD binding click
    private boolean clickHudBinding(double mouseX, double mouseY) {
        CompoundTag elm = hudElement();
        if (elm == null) return false;
        for (String property : hudBindableProps(elm)) {
            if (!hudBindingBtnBounds(property).contains(mouseX, mouseY)) continue;
            hudBindingDropdownKey = property;
            hudBindingDropdownScroll = 0;
            return true;
        }
        return false;
    }

    // Handle the HUD binding dropdown click
    private boolean clickHudBindingDropdown(double mouseX, double mouseY) {
        AdvancedGraphDocument.Node node = hudNode();
        CompoundTag elm = hudElement();
        List<String> ports = hudBindingPorts(node);
        int visibleRows = Math.min(HUD_PORT_VISIBLE_ROWS, ports.size());
        UiRect bounds = hudBindingDropdownBounds(visibleRows);
        if (elm != null && visibleRows > 0 && bounds.contains(mouseX, mouseY)) {
            int row = (int) ((mouseY - bounds.y() - 2) / 18);
            int idx = hudBindingDropdownScroll + row;
            if (row >= 0 && row < visibleRows && idx < ports.size()) {
                checkpoint();
                AdvancedHudElementBinding.bind(
                        elm, hudBindingDropdownKey, ports.get(idx));
            }
        }
        hudBindingDropdownKey = null;
        return true;
    }

    // Draw the HUD prop slider
    private void drawHudPropSlider(GuiGraphics graphics, UiRect bounds, int val,
                                               int minimum, int maximum, String key,
                                               int mouseX, int mouseY) {
        float pct = maximum <= minimum ? 0.0F
                : (val - minimum) / (float) (maximum - minimum);
        boolean active = key.equals(draggingHudPropSlider);
        renderControllerSlider(graphics, bounds.x(), bounds.y() + bounds.height() / 2,
                bounds.width(), pct, portColor("number"),
                active || bounds.contains(mouseX, mouseY));
    }

    // Draw the HUD color sliders
    private void drawHudColorSliders(GuiGraphics graphics, CompoundTag elm, String key,
                                             String label, int labelY, int mouseX, int mouseY) {
        UiRect sidebar = hudSidebarBounds();
        int col = hudColorVal(elm, key);
        HudDesignerHsv hsv = hudHsv(elm, key, col);
        graphics.drawString(font, label, sidebar.x() + 8, labelY, 0xFFC8D7E3, false);
        UiRect swatch = new UiRect(sidebar.right() - 22, labelY, 14, 9);
        drawHudCheckerboard(graphics, swatch);
        graphics.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), col);
        graphics.renderOutline(swatch.x(), swatch.y(), swatch.width(), swatch.height(), 0xFFB9CFDC);
        for (HudDesignerColorControl control : HudDesignerColorControl.values()) {
            UiRect row = hudColorSliderBounds(key, control);
            UiRect track = hudColorSliderTrackBounds(key, control);
            float pct = switch (control) {
                case HUE -> hsv.hue();
                case SATURATION -> hsv.saturation();
                case VALUE -> hsv.value();
                case ALPHA -> ((col >>> 24) & 0xFF) / 255.0F;
            };
            String controlLabel = switch (control) {
                case HUE -> "H";
                case SATURATION -> "S";
                case VALUE -> "V";
                case ALPHA -> "Op";
            };
            graphics.drawString(font, controlLabel, row.x(), row.y() + 3, 0xFF91A9B8, false);
            boolean active = key.equals(draggingHudColorKey)
                    && control == draggingHudColorControl;
            renderControllerSlider(graphics, track.x(), track.y() + track.height() / 2,
                    track.width(), pct, portColor("number"),
                    active || track.contains(mouseX, mouseY));
            String pctLabel = Math.round(pct * 100.0F) + "%";
            graphics.drawString(font, pctLabel, row.right() - font.width(pctLabel), row.y() + 3,
                    0xFF91A9B8, false);
        }
    }

    // Draw the HUD checkerboard
    private static void drawHudCheckerboard(GuiGraphics graphics, UiRect bounds) {
        int cell = 4;
        for (int y = bounds.y(); y < bounds.bottom(); y += cell) {
            for (int x = bounds.x(); x < bounds.right(); x += cell) {
                boolean light = ((x - bounds.x()) / cell + (y - bounds.y()) / cell) % 2 == 0;
                graphics.fill(x, y, Math.min(x + cell, bounds.right()), Math.min(y + cell, bounds.bottom()),
                        light ? 0xFFB8B8B8 : 0xFF686868);
            }
        }
    }

    // Draw the HUD font style btn
    private void drawHudFontStyleBtn(GuiGraphics graphics, CompoundTag elm,
                                                int mouseX, int mouseY) {
        UiRect btn = hudFontStyleBtnBounds();
        String label = AdvancedHudElementStyle.fontStyleLabel(AdvancedHudElementStyle.fontStyle(elm));
        renderAdvancedButton(graphics, font, btn.x(), btn.y(), btn.width(), btn.height(),
                Component.literal(label + (hudFontStyleDropdownOpen ? " ^" : " v")),
                btn.contains(mouseX, mouseY), true);
    }

    // Draw the HUD font style dropdown
    private void drawHudFontStyleDropdown(GuiGraphics graphics, CompoundTag elm,
                                                  int mouseX, int mouseY) {
        UiRect bounds = hudFontStyleDropdownBounds();
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        String selectedStyle = AdvancedHudElementStyle.fontStyle(elm);
        int rowY = bounds.y() + 2;
        for (String style : AdvancedHudElementStyle.FONT_STYLE_OPTIONS) {
            boolean selected = style.equals(selectedStyle);
            boolean hovered = inside(mouseX, mouseY, bounds.x() + 2, rowY, bounds.width() - 4, 16);
            if (selected || hovered) {
                renderControllerOption(graphics, bounds.x() + 2, rowY, bounds.width() - 4, 16,
                        AdvancedGraphCatalog.categoryColor("hud"), selected);
            }
            graphics.drawString(font, AdvancedHudElementStyle.fontStyleLabel(style),
                    bounds.x() + 7, rowY + 4, selected ? nodeValueTextColor() : 0xFFE0EDF4, false);
            rowY += 18;
        }
    }

    // Draw the HUD style btn
    private void drawHudStyleBtn(GuiGraphics graphics, CompoundTag elm,
                                            int mouseX, int mouseY) {
        UiRect btn = hudStyleBtnBounds();
        String type = elm.getString("Type");
        String label = AdvancedHudInteractiveStyles.label(
                type, AdvancedHudInteractiveStyles.style(elm));
        renderAdvancedButton(graphics, font, btn.x(), btn.y(), btn.width(), btn.height(),
                Component.literal(label + (hudStyleDropdownOpen ? " ^" : " v")),
                btn.contains(mouseX, mouseY), true);
    }

    // Draw the HUD style dropdown
    private void drawHudStyleDropdown(GuiGraphics graphics, CompoundTag elm,
                                              int mouseX, int mouseY) {
        List<AdvancedHudInteractiveStyles.Option> opts =
                AdvancedHudInteractiveStyles.options(elm.getString("Type"));
        UiRect bounds = hudStyleDropdownBounds(opts.size());
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        String selectedStyle = AdvancedHudInteractiveStyles.style(elm);
        int rowY = bounds.y() + 2;
        for (AdvancedHudInteractiveStyles.Option option : opts) {
            boolean selected = option.id().equals(selectedStyle);
            boolean hovered = inside(mouseX, mouseY, bounds.x() + 2, rowY, bounds.width() - 4, 16);
            if (selected || hovered) {
                renderControllerOption(graphics, bounds.x() + 2, rowY, bounds.width() - 4, 16,
                        AdvancedGraphCatalog.categoryColor("hud"), selected);
            }
            graphics.drawString(font, option.label(), bounds.x() + 7, rowY + 4,
                    selected ? nodeValueTextColor() : 0xFFE0EDF4, false);
            rowY += 18;
        }
    }

    // Handle the HUD field click
    private boolean clickHudField(double mouseX, double mouseY, int btn) {
        for (EditBox field : hudFields()) {
            if (field.mouseClicked(mouseX, mouseY, btn)) {
                for (EditBox other : hudFields()) {
                    other.setFocused(other == field);
                }
                setFocused(field);
                return true;
            }
        }
        if (getFocused() instanceof EditBox focused && hudFields().contains(focused)) {
            clearHudFieldFocus();
        }
        return true;
    }

    // Clear the HUD field focus
    private void clearHudFieldFocus() {
        for (EditBox field : hudFields()) {
            field.setFocused(false);
        }
        if (getFocused() instanceof EditBox focused && hudFields().contains(focused)) {
            setFocused(null);
        }
    }

    // Handle the HUD color slider click
    private boolean clickHudColorSlider(double mouseX, double mouseY) {
        CompoundTag elm = hudElement();
        if (elm == null) {
            draggingHudColorKey = null;
            draggingHudColorControl = null;
            return false;
        }
        for (String key : hudColorKeys(elm)) {
            for (HudDesignerColorControl control : HudDesignerColorControl.values()) {
                if (hudColorSliderTrackBounds(key, control).contains(mouseX, mouseY)) {
                    checkpoint();
                    draggingHudColorKey = key;
                    draggingHudColorControl = control;
                    hudFontStyleDropdownOpen = false;
                    hudStyleDropdownOpen = false;
                    hudPortDropdownOpen = false;
                    hudImageDropdownOpen = false;
                    updateHudColorSlider(mouseX);
                    return true;
                }
            }
        }
        return false;
    }

    // Handle the HUD font style click
    private boolean clickHudFontStyle(double mouseX, double mouseY) {
        CompoundTag elm = hudElement();
        if (elm == null || !("text".equals(elm.getString("Type"))
                || "value".equals(elm.getString("Type"))
                || "button".equals(elm.getString("Type"))
                || "toggle".equals(elm.getString("Type")))) {
            hudFontStyleDropdownOpen = false;
            return false;
        }
        UiRect btn = hudFontStyleBtnBounds();
        if (hudFontStyleDropdownOpen) {
            UiRect dropdown = hudFontStyleDropdownBounds();
            if (dropdown.contains(mouseX, mouseY)) {
                int row = (int) ((mouseY - dropdown.y() - 2) / 18);
                if (row >= 0 && row < AdvancedHudElementStyle.FONT_STYLE_OPTIONS.size()) {
                    checkpoint();
                    AdvancedHudElementStyle.setFontStyle(elm,
                            AdvancedHudElementStyle.FONT_STYLE_OPTIONS.get(row));
                }
                hudFontStyleDropdownOpen = false;
                return true;
            }
            if (btn.contains(mouseX, mouseY)) {
                hudFontStyleDropdownOpen = false;
                return true;
            }
            hudFontStyleDropdownOpen = false;
            return true;
        }
        if (btn.contains(mouseX, mouseY)) {
            hudFontStyleDropdownOpen = true;
            hudStyleDropdownOpen = false;
            hudPortDropdownOpen = false;
            hudImageDropdownOpen = false;
            return true;
        }
        return false;
    }

    // Handle the HUD style click
    private boolean clickHudStyle(double mouseX, double mouseY) {
        CompoundTag elm = hudElement();
        if (elm == null || !isInteractiveHudType(elm.getString("Type"))) {
            hudStyleDropdownOpen = false;
            return false;
        }
        UiRect btn = hudStyleBtnBounds();
        List<AdvancedHudInteractiveStyles.Option> opts =
                AdvancedHudInteractiveStyles.options(elm.getString("Type"));
        if (hudStyleDropdownOpen) {
            UiRect dropdown = hudStyleDropdownBounds(opts.size());
            if (dropdown.contains(mouseX, mouseY)) {
                int row = (int) ((mouseY - dropdown.y() - 2) / 18);
                if (row >= 0 && row < opts.size()) {
                    checkpoint();
                    AdvancedHudInteractiveStyles.setStyle(elm, opts.get(row).id());
                }
                hudStyleDropdownOpen = false;
                return true;
            }
            if (btn.contains(mouseX, mouseY)) {
                hudStyleDropdownOpen = false;
                return true;
            }
            hudStyleDropdownOpen = false;
            return true;
        }
        if (btn.contains(mouseX, mouseY)) {
            hudStyleDropdownOpen = true;
            hudFontStyleDropdownOpen = false;
            hudPortDropdownOpen = false;
            hudImageDropdownOpen = false;
            return true;
        }
        return false;
    }

    // Handle the HUD prop slider click
    private boolean clickHudPropSlider(double mouseX, double mouseY) {
        CompoundTag elm = hudElement();
        if (elm == null) {
            return false;
        }
        String type = elm.getString("Type");
        if (("text".equals(type) || "value".equals(type)
                || "button".equals(type) || "toggle".equals(type))
                && hudFontSizeSliderBounds().contains(mouseX, mouseY)) {
            checkpoint();
            draggingHudPropSlider = "FontSize";
            updateHudPropSlider(mouseX);
            return true;
        }
        if ("box".equals(type) && hudBorderRadiusSliderBounds().contains(mouseX, mouseY)) {
            checkpoint();
            draggingHudPropSlider = "BorderRadius";
            updateHudPropSlider(mouseX);
            return true;
        }
        return false;
    }

    // Get the HUD color keys
    private List<String> hudColorKeys(CompoundTag elm) {
        return switch (elm.getString("Type")) {
            case "text", "value", "button", "toggle", "slider" -> List.of("Color");
            case "box" -> List.of("Color", "BorderColor");
            default -> List.of();
        };
    }

    // Get the HUD color val
    private int hudColorVal(CompoundTag elm, String key) {
        int fallback = "BorderColor".equals(key)
                ? AdvancedHudElementStyle.DEFAULT_BOX_BORDER_COLOR
                : "box".equals(elm.getString("Type"))
                ? AdvancedHudElementStyle.DEFAULT_BOX_BACKGROUND_COLOR
                : AdvancedHudElementStyle.DEFAULT_TEXT_COLOR;
        return AdvancedHudElementStyle.color(elm, key, fallback);
    }

    // Get the HUD hsv
    private HudDesignerHsv hudHsv(CompoundTag elm, String key, int col) {
        String memoryKey = hudColorMemoryKey(elm, key);
        int rgb = col & 0x00FFFFFF;
        HudDesignerHsv remembered = hudHsvMemory.get(memoryKey);
        if (remembered != null && remembered.rgb() == rgb) {
            return remembered;
        }
        float[] converted = java.awt.Color.RGBtoHSB(
                (col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, null);
        HudDesignerHsv state = new HudDesignerHsv(rgb, converted[0], converted[1], converted[2]);
        hudHsvMemory.put(memoryKey, state);
        return state;
    }

    // Get the HUD color memory key
    private String hudColorMemoryKey(CompoundTag elm, String key) {
        return hudNodeId + ":" + System.identityHashCode(elm) + ":" + key;
    }

    // Update the HUD color slider
    private void updateHudColorSlider(double mouseX) {
        CompoundTag elm = hudElement();
        if (elm == null || draggingHudColorKey == null || draggingHudColorControl == null) {
            return;
        }
        int current = hudColorVal(elm, draggingHudColorKey);
        HudDesignerHsv currentHsv = hudHsv(elm, draggingHudColorKey, current);
        float hue = currentHsv.hue();
        float saturation = currentHsv.saturation();
        float val = currentHsv.value();
        int alpha = (current >>> 24) & 0xFF;
        UiRect track = hudColorSliderTrackBounds(
                draggingHudColorKey, draggingHudColorControl);
        float amount = (float) Mth.clamp((mouseX - track.x() - 4.5D)
                / Math.max(1.0D, track.width() - 9.0D), 0.0D, 1.0D);
        switch (draggingHudColorControl) {
            case HUE -> hue = amount;
            case SATURATION -> saturation = amount;
            case VALUE -> val = amount;
            case ALPHA -> alpha = Math.round(amount * 255.0F);
        }
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, val) & 0x00FFFFFF;
        hudHsvMemory.put(hudColorMemoryKey(elm, draggingHudColorKey),
                new HudDesignerHsv(rgb, hue, saturation, val));
        int col = (alpha << 24) | (rgb & 0x00FFFFFF);
        elm.putInt(draggingHudColorKey, col);
        updateManagedAccWidgetDefault(elm, draggingHudColorKey,
                graphDefault("number", col));
    }

    // Update the HUD prop slider
    private void updateHudPropSlider(double mouseX) {
        CompoundTag elm = hudElement();
        if (elm == null || draggingHudPropSlider == null) {
            return;
        }
        boolean fontSize = "FontSize".equals(draggingHudPropSlider);
        UiRect bounds = fontSize ? hudFontSizeSliderBounds() : hudBorderRadiusSliderBounds();
        int minimum = fontSize ? AdvancedHudElementStyle.MIN_FONT_SIZE : 0;
        int maximum = fontSize ? AdvancedHudElementStyle.MAX_FONT_SIZE
                : AdvancedHudElementStyle.MAX_BORDER_RADIUS;
        double amount = Mth.clamp((mouseX - bounds.x() - 4.5D)
                / Math.max(1.0D, bounds.width() - 9.0D), 0.0D, 1.0D);
        int val = minimum + (int) Math.round(amount * (maximum - minimum));
        elm.putInt(draggingHudPropSlider, val);
        updateManagedAccWidgetDefault(elm, draggingHudPropSlider,
                graphDefault("number", val));
    }

    // Handle the HUD click
    private boolean clickHud(double mouseX, double mouseY, int btn) {
        if (!hudOpen) return false;
        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // ------------------------------------OPEN MENUS------------------------------------
            if (hudBindingDropdownKey != null) {
                return clickHudBindingDropdown(mouseX, mouseY);
            }
            if (hudImageDropdownOpen) {
                return clickHudImageDropdown(mouseX, mouseY);
            }
            if (hudPortDropdownOpen) {
                return clickHudPortDropdown(mouseX, mouseY);
            }
            // ------------------------------------PALETTE / INSPECTOR------------------------------------
            CompoundTag selected = hudElement();
            if (hudPaletteTab(false).contains(mouseX, mouseY)) {
                hudInteractivePalette = false;
                return true;
            }
            if (hudPaletteTab(true).contains(mouseX, mouseY)) {
                hudInteractivePalette = true;
                return true;
            }
            if (selected != null && hudInspectorTab(false).contains(mouseX, mouseY)) {
                hudDataBindings = false;
                hudBindingDropdownKey = null;
                layoutHudWidgets();
                return true;
            }
            if (selected != null && hudInspectorTab(true).contains(mouseX, mouseY)) {
                hudDataBindings = true;
                hudBindingPropScroll = 0;
                clearHudFieldFocus();
                layoutHudWidgets();
                return true;
            }
            if (selected != null && hudDataBindings
                    && clickHudBinding(mouseX, mouseY)) {
                return true;
            }
            if (selected != null && hudDataBindings
                    && hudSidebarBounds().contains(mouseX, mouseY)) {
                return true;
            }
            // ------------------------------------------------PROPERTY CONTROLS-------------------------------------------------
            if (selected != null && "image".equals(selected.getString("Type"))) {
                if (hudPendingImageNodeId == null
                        && hudImageUploadBtnBounds().contains(mouseX, mouseY)) {
                    uploadHudImage();
                    return true;
                }
                if (hudImageLibraryBtnBounds().contains(mouseX, mouseY)
                        && !AdvancedHudImageClient.uploadedImages().isEmpty()) {
                    AdvancedHudImageClient.requestCatalog();
                    hudImageDropdownOpen = true;
                    hudImageDropdownScroll = 0;
                    hudPortDropdownOpen = false;
                    hudFontStyleDropdownOpen = false;
                    hudStyleDropdownOpen = false;
                    return true;
                }
            }
            if (clickHudColorSlider(mouseX, mouseY)) {
                return true;
            }
            if (clickHudFontStyle(mouseX, mouseY)) {
                return true;
            }
            if (clickHudStyle(mouseX, mouseY)) {
                return true;
            }
            if (selected != null && ("value".equals(selected.getString("Type"))
                    || "image".equals(selected.getString("Type"))
                    || "progress".equals(selected.getString("Type")))
                    && !hudValPorts(hudNode()).isEmpty()
                    && hudPortBtnBounds().contains(mouseX, mouseY)) {
                hudPortDropdownOpen = true;
                hudPortDropdownScroll = 0;
                hudImageDropdownOpen = false;
                hudFontStyleDropdownOpen = false;
                hudStyleDropdownOpen = false;
                return true;
            }
            if (clickHudPropSlider(mouseX, mouseY)) {
                return true;
            }
            if (selected != null && hudLayerBtn(-1).contains(mouseX, mouseY)) {
                moveHudElement(-1);
                return true;
            }
            if (selected != null && hudLayerBtn(1).contains(mouseX, mouseY)) {
                moveHudElement(1);
                return true;
            }
            // ------------------------------------ELEMENT ACTIONS------------------------------------
            int actionCount = hudInteractivePalette ? 4 : 5;
            for (int i = 0; i < actionCount; i++) {
                if (hudActionBtn(i).contains(mouseX, mouseY)) {
                    addAdvancedHudElement(hudNode(), hudInteractivePalette
                            ? switch (i) {
                        case 0 -> "button";
                        case 1 -> "toggle";
                        case 2 -> "slider";
                        default -> "text_input";
                    }
                            : switch (i) {
                        case 0 -> "text";
                        case 1 -> "value";
                        case 2 -> "box";
                        case 3 -> "image";
                        default -> "progress";
                    });
                    ListTag elements = hudNode().data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
                    hudSelectedIdx = elements.size() - 1;
                    hudPortDropdownOpen = false;
                    hudImageDropdownOpen = false;
                    draggingHudColorKey = null;
                    draggingHudColorControl = null;
                    hudFontStyleDropdownOpen = false;
                    hudStyleDropdownOpen = false;
                    hudBindingDropdownKey = null;
                    syncHudFields();
                    return true;
                }
            }
            if (hudRemoveBtn().contains(mouseX, mouseY)) {
                removeHudElement();
                return true;
            }
            if (hudCloseBtn().contains(mouseX, mouseY)) {
                closeHud();
                return true;
            }
            // ------------------------------------CANVAS SELECTION------------------------------------
            AdvancedGraphDocument.Node node = hudNode();
            if (isAdvancedHudNode(node)) {
                ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
                for (int i = elements.size() - 1; i >= 0; i--) {
                    CompoundTag elm = AdvancedHudElementBinding.resolvedCopy(
                            elements.getCompound(i), port -> hudDataVal(node, port));
                    UiRect rect = hudElementRect(node, hudCanvasBounds(), elm);
                    if (hudElementContains(elm, rect, mouseX, mouseY)) {
                        hudSelectedIdx = i;
                        hudPortDropdownOpen = false;
                        hudImageDropdownOpen = false;
                        draggingHudColorKey = null;
                        draggingHudColorControl = null;
                        hudFontStyleDropdownOpen = false;
                        hudStyleDropdownOpen = false;
                        hudBindingDropdownKey = null;
                        syncHudFields();
                        checkpoint();
                        if (mouseX >= rect.right() - 8 && mouseY >= rect.bottom() - 8) {
                            resizingHudElement = true;
                        } else {
                            draggingHudElement = true;
                            hudDragOffsetX = (int) mouseX - rect.x();
                            hudDragOffsetY = (int) mouseY - rect.y();
                        }
                        return true;
                    }
                }
                if (hudCanvasBounds().contains(mouseX, mouseY)) {
                    hudSelectedIdx = -1;
                    hudPortDropdownOpen = false;
                    hudImageDropdownOpen = false;
                    draggingHudColorKey = null;
                    draggingHudColorControl = null;
                    hudFontStyleDropdownOpen = false;
                    hudStyleDropdownOpen = false;
                    hudBindingDropdownKey = null;
                    syncHudFields();
                    return true;
                }
            }
        }
        return false;
    }

    // Drag the HUD element
    private boolean dragHud(double mouseX, double mouseY, int btn) {
        if (!hudOpen || btn != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        CompoundTag elm = hudElement();
        AdvancedGraphDocument.Node node = hudNode();
        if (elm == null || !isAdvancedHudNode(node)) return false;
        UiRect canvas = hudCanvasBounds();
        double scale = hudCanvasScale(node, canvas);
        int baseX = hudPreviewX(node, canvas);
        int baseY = hudPreviewY(node, canvas);
        if (draggingHudElement) {
            int localX = (int) Math.round((mouseX - baseX - hudDragOffsetX) / scale);
            int localY = (int) Math.round((mouseY - baseY - hudDragOffsetY) / scale);
            int nextX = allowsOffSurfaceLayout(node)
                    ? Mth.clamp(localX, -4096, 4096)
                    : Mth.clamp(localX, 0, Math.max(0, node.data().getInt("WidgetWidth") - 1));
            int nextY = allowsOffSurfaceLayout(node)
                    ? Mth.clamp(localY, -4096, 4096)
                    : Mth.clamp(localY, 0, Math.max(0, node.data().getInt("WidgetHeight") - 1));
            elm.putInt("X", nextX);
            elm.putInt("Y", nextY);
            updateManagedAccWidgetDefault(elm, "X", graphDefault("number", nextX));
            updateManagedAccWidgetDefault(elm, "Y", graphDefault("number", nextY));
            syncHudFields();
            return true;
        }
        if (resizingHudElement) {
            int localW = (int) Math.round((mouseX - baseX) / scale) - elm.getInt("X");
            int localH = (int) Math.round((mouseY - baseY) / scale) - elm.getInt("Y");
            int nextWidth = allowsOffSurfaceLayout(node)
                    ? Mth.clamp(localW, 1, 4096)
                    : Mth.clamp(localW, 1, Math.max(1,
                    node.data().getInt("WidgetWidth") - elm.getInt("X")));
            int nextHeight = allowsOffSurfaceLayout(node)
                    ? Mth.clamp(localH, 1, 4096)
                    : Mth.clamp(localH, 1, Math.max(1,
                    node.data().getInt("WidgetHeight") - elm.getInt("Y")));
            elm.putInt("W", nextWidth);
            elm.putInt("H", nextHeight);
            updateManagedAccWidgetDefault(elm, "W", graphDefault("number", nextWidth));
            updateManagedAccWidgetDefault(elm, "H", graphDefault("number", nextHeight));
            syncHudFields();
            return true;
        }
        return false;
    }

    // Remove the HUD element
    private void removeHudElement() {
        AdvancedGraphDocument.Node node = hudNode();
        if (!isAdvancedHudNode(node)) return;
        ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
        if (hudSelectedIdx < 0 || hudSelectedIdx >= elements.size()) return;
        checkpoint();
        elements.remove(hudSelectedIdx);
        node.data().put(WIDGET_ELEMENTS, elements);
        syncHudPorts(node);
        hudSelectedIdx = Math.min(hudSelectedIdx, elements.size() - 1);
        hudPortDropdownOpen = false;
        hudImageDropdownOpen = false;
        draggingHudColorKey = null;
        draggingHudColorControl = null;
        hudFontStyleDropdownOpen = false;
        hudStyleDropdownOpen = false;
        syncHudFields();
    }

    // Get the HUD prop top
    private int hudPropTop(UiRect sidebar) {
        return sidebar.y() + 130;
    }

    // Get the HUD size field y
    private int hudSizeFieldY(UiRect sidebar, String type) {
        int propertyTop = hudPropTop(sidebar);
        return switch (type) {
            case "text" -> propertyTop + 168;
            case "value" -> propertyTop + 190;
            case "button", "toggle", "text_input" -> propertyTop + 168;
            case "box" -> propertyTop + 162;
            case "image" -> propertyTop + 122;
            case "slider", "progress" -> propertyTop + 96;
            default -> propertyTop + 14;
        };
    }

    // Get the HUD font size slider bounds
    private UiRect hudFontSizeSliderBounds() {
        UiRect sidebar = hudSidebarBounds();
        CompoundTag elm = hudElement();
        int offset = elm != null && "value".equals(elm.getString("Type")) ? 65 : 49;
        return new UiRect(sidebar.x() + 8, hudPropTop(sidebar) + offset,
                sidebar.width() - 16, 14);
    }

    // Get the HUD border radius slider bounds
    private UiRect hudBorderRadiusSliderBounds() {
        UiRect sidebar = hudSidebarBounds();
        return new UiRect(sidebar.x() + 8, hudPropTop(sidebar) + 127,
                sidebar.width() - 16, 14);
    }

    // Get the HUD font style btn bounds
    private UiRect hudFontStyleBtnBounds() {
        UiRect sidebar = hudSidebarBounds();
        CompoundTag elm = hudElement();
        int offset = elm != null && "value".equals(elm.getString("Type")) ? 144 : 128;
        boolean interactive = elm != null && ("button".equals(elm.getString("Type"))
                || "toggle".equals(elm.getString("Type")));
        int width = interactive ? (sidebar.width() - 24) / 2 : sidebar.width() - 16;
        return new UiRect(sidebar.x() + 8, hudPropTop(sidebar) + offset, width, 20);
    }

    // Get the HUD font style dropdown bounds
    private UiRect hudFontStyleDropdownBounds() {
        UiRect btn = hudFontStyleBtnBounds();
        int height = AdvancedHudElementStyle.FONT_STYLE_OPTIONS.size() * 18 + 4;
        UiRect modal = hudBounds();
        int width = 160;
        int x = Math.max(modal.x() + 4, btn.x() - width - 8);
        int y = Mth.clamp(btn.y(), modal.y() + 36, modal.bottom() - height - 4);
        return new UiRect(x, y, width, height);
    }

    // Get the HUD style btn bounds
    private UiRect hudStyleBtnBounds() {
        UiRect sidebar = hudSidebarBounds();
        CompoundTag elm = hudElement();
        boolean slider = elm != null && "slider".equals(elm.getString("Type"));
        if (slider) {
            return new UiRect(sidebar.x() + 8, hudPropTop(sidebar) + 14,
                    sidebar.width() - 16, 20);
        }
        int width = (sidebar.width() - 24) / 2;
        return new UiRect(sidebar.x() + 16 + width, hudPropTop(sidebar) + 128,
                width, 20);
    }

    // Get the HUD style dropdown bounds
    private UiRect hudStyleDropdownBounds(int optionCount) {
        UiRect btn = hudStyleBtnBounds();
        UiRect modal = hudBounds();
        int height = Math.max(1, optionCount) * 18 + 4;
        int width = 170;
        int x = Math.max(modal.x() + 4, btn.x() - width - 8);
        int y = Mth.clamp(btn.y(), modal.y() + 36, modal.bottom() - height - 4);
        return new UiRect(x, y, width, height);
    }

    // Get the HUD color slider bounds
    private UiRect hudColorSliderBounds(String key, HudDesignerColorControl control) {
        UiRect sidebar = hudSidebarBounds();
        int propertyTop = hudPropTop(sidebar);
        CompoundTag elm = hudElement();
        String type = elm == null ? "" : elm.getString("Type");
        int labelY = "value".equals(type) ? propertyTop + 84
                : "text".equals(type) || "button".equals(type) || "toggle".equals(type)
                ? propertyTop + 68
                : propertyTop + ("BorderColor".equals(key) ? 46 : 0);
        int row = control == HudDesignerColorControl.HUE
                || control == HudDesignerColorControl.SATURATION ? 0 : 1;
        int column = control == HudDesignerColorControl.HUE
                || control == HudDesignerColorControl.VALUE ? 0 : 1;
        int contentWidth = sidebar.width() - 16;
        int columnWidth = (contentWidth - 8) / 2;
        return new UiRect(sidebar.x() + 8 + column * (columnWidth + 8),
                labelY + 14 + row * 15, columnWidth, 14);
    }

    // Get the HUD color slider track bounds
    private UiRect hudColorSliderTrackBounds(String key, HudDesignerColorControl control) {
        UiRect row = hudColorSliderBounds(key, control);
        return new UiRect(row.x() + 14, row.y(), row.width() - 42, row.height());
    }

    // Get the HUD layer btn
    private UiRect hudLayerBtn(int dir) {
        UiRect sidebar = hudSidebarBounds();
        int gap = 8;
        int buttonWidth = (sidebar.width() - 16 - gap) / 2;
        int x = sidebar.x() + 8 + (dir > 0 ? buttonWidth + gap : 0);
        return new UiRect(x, sidebar.y() + 76, buttonWidth, 20);
    }

    // Get the HUD inspector tab
    private UiRect hudInspectorTab(boolean data) {
        UiRect sidebar = hudSidebarBounds();
        int gap = 6;
        int buttonWidth = (sidebar.width() - 16 - gap) / 2;
        return new UiRect(sidebar.x() + 8 + (data ? buttonWidth + gap : 0),
                sidebar.y() + 104, buttonWidth, 20);
    }

    // Get the HUD port btn bounds
    private UiRect hudPortBtnBounds() {
        UiRect sidebar = hudSidebarBounds();
        return new UiRect(sidebar.x() + 8, hudPropTop(sidebar) + 14,
                sidebar.width() - 16, 20);
    }

    // Get the HUD image upload btn bounds
    private UiRect hudImageUploadBtnBounds() {
        UiRect sidebar = hudSidebarBounds();
        int gap = 8;
        int buttonWidth = (sidebar.width() - 16 - gap) / 2;
        return new UiRect(sidebar.x() + 8, hudPropTop(sidebar) + 80, buttonWidth, 20);
    }

    // Get the HUD image library btn bounds
    private UiRect hudImageLibraryBtnBounds() {
        UiRect upload = hudImageUploadBtnBounds();
        return new UiRect(upload.right() + 8, upload.y(), upload.width(), upload.height());
    }

    // Get the HUD image dropdown bounds
    private UiRect hudImageDropdownBounds(int visibleRows) {
        UiRect btn = hudImageLibraryBtnBounds();
        UiRect modal = hudBounds();
        int width = 190;
        int height = visibleRows * 18 + 4;
        int x = Math.max(modal.x() + 4, btn.x() - width - 8);
        int y = Mth.clamp(btn.y(), modal.y() + 36, modal.bottom() - height - 4);
        return new UiRect(x, y, width, height);
    }

    // Get the HUD port dropdown bounds
    private UiRect hudPortDropdownBounds(int visibleRows) {
        UiRect btn = hudPortBtnBounds();
        return new UiRect(btn.x(), btn.bottom() + 2, btn.width(), visibleRows * 18 + 4);
    }

    // Get the HUD val ports
    private List<String> hudValPorts(AdvancedGraphDocument.Node node) {
        if (!isAdvancedHudNode(node)) return List.of();
        return AdvancedGraphCatalog.inputs(node).entrySet().stream()
                .filter(entry -> !"exec".equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .filter(port -> !"label".equals(port) && !"visible".equals(port))
                .toList();
    }

    // Get the HUD port type
    private String hudPortType(AdvancedGraphDocument.Node node, String port) {
        if (!isAdvancedHudNode(node) || port == null || port.isBlank()) return "any";
        String declaredType = AdvancedGraphCatalog.inputs(node).get(port);
        if (declaredType == null || declaredType.isBlank()) return "any";
        if (!"any".equals(declaredType)) return declaredType;
        for (AdvancedGraphDocument.Edge edge : activeEdges()) {
            if (!node.id().equals(edge.toNode()) || !port.equals(edge.toPort())) continue;
            AdvancedGraphDocument.Node src = findNode(edge.fromNode());
            String sourceType = src == null ? null : AdvancedGraphCatalog.outputs(src).get(edge.fromPort());
            if (sourceType != null && !sourceType.isBlank() && !"any".equals(sourceType)) return sourceType;
        }
        AdvancedGraphLiveValue liveValue = simulatedLiveInput(node.id(), port);
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if (liveValue == null && controller != null) {
            liveValue = controller.getGraphLiveInput(node.id(), port);
        }
        if (liveValue != null && !liveValue.type().isBlank() && !"any".equals(liveValue.type())) {
            return liveValue.type();
        }
        String defaultType = node.data().getCompound("Defaults").getCompound(port).getString("Type");
        return defaultType.isBlank() || "any".equals(defaultType) ? declaredType : defaultType;
    }

    // Get the HUD port option label
    private String hudPortOptionLabel(AdvancedGraphDocument.Node node, String port) {
        return hudFieldLabel(node, port) + " (" + humanPort(hudPortType(node, port)) + ")";
    }

    // Draw the HUD port btn
    private void drawHudPortBtn(GuiGraphics graphics, AdvancedGraphDocument.Node node,
                                           CompoundTag elm, int mouseX, int mouseY) {
        UiRect btn = hudPortBtnBounds();
        List<String> ports = hudValPorts(node);
        String port = elm.getString("Port");
        String label = ports.contains(port) ? hudPortOptionLabel(node, port) : "Select a port";
        renderAdvancedButton(graphics, font, btn.x(), btn.y(), btn.width(), btn.height(),
                Component.literal(trim(label, 28) + (hudPortDropdownOpen ? " ^" : " v")),
                btn.contains(mouseX, mouseY), !ports.isEmpty());
    }

    // Draw the HUD port dropdown
    private void drawHudPortDropdown(GuiGraphics graphics, AdvancedGraphDocument.Node node,
                                             CompoundTag elm, int mouseX, int mouseY) {
        List<String> ports = hudValPorts(node);
        int visibleRows = Math.min(HUD_PORT_VISIBLE_ROWS, ports.size());
        if (visibleRows == 0) return;
        int maxScroll = Math.max(0, ports.size() - visibleRows);
        hudPortDropdownScroll = Mth.clamp(hudPortDropdownScroll, 0, maxScroll);
        UiRect bounds = hudPortDropdownBounds(visibleRows);
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        int rowY = bounds.y() + 2;
        for (int row = 0; row < visibleRows; row++) {
            String port = ports.get(hudPortDropdownScroll + row);
            boolean selected = port.equals(elm.getString("Port"));
            boolean hovered = inside(mouseX, mouseY, bounds.x() + 2, rowY, bounds.width() - 4, 16);
            if (selected || hovered) {
                renderControllerOption(graphics, bounds.x() + 2, rowY, bounds.width() - 4, 16,
                        portColor(hudPortType(node, port)), selected);
            }
            graphics.drawString(font, trim(hudPortOptionLabel(node, port), 29),
                    bounds.x() + 7, rowY + 4, selected ? nodeValueTextColor() : 0xFFE0EDF4, false);
            rowY += 18;
        }
    }

    // Handle the HUD port dropdown click
    private boolean clickHudPortDropdown(double mouseX, double mouseY) {
        AdvancedGraphDocument.Node node = hudNode();
        CompoundTag elm = hudElement();
        List<String> ports = hudValPorts(node);
        int visibleRows = Math.min(HUD_PORT_VISIBLE_ROWS, ports.size());
        UiRect bounds = hudPortDropdownBounds(visibleRows);
        if (visibleRows > 0 && bounds.contains(mouseX, mouseY)) {
            int row = (int) ((mouseY - bounds.y() - 2) / 18);
            if (row >= 0 && row < visibleRows && elm != null) {
                int idx = hudPortDropdownScroll + row;
                if (idx < ports.size()) {
                    checkpoint();
                    elm.putString("Port", ports.get(idx));
                }
            }
        }
        hudPortDropdownOpen = false;
        return true;
    }

    // Draw the HUD image dropdown
    private void drawHudImageDropdown(GuiGraphics graphics, CompoundTag elm,
                                              int mouseX, int mouseY) {
        List<String> images = AdvancedHudImageClient.uploadedImages();
        int visibleRows = Math.min(HUD_IMAGE_VISIBLE_ROWS, images.size());
        if (visibleRows == 0) {
            hudImageDropdownOpen = false;
            return;
        }
        int maxScroll = Math.max(0, images.size() - visibleRows);
        hudImageDropdownScroll = Mth.clamp(hudImageDropdownScroll, 0, maxScroll);
        UiRect bounds = hudImageDropdownBounds(visibleRows);
        renderAdvancedPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height());
        String selectedName = AdvancedHudImageStore.referencedName(elm.getString("Texture")).orElse("");
        int rowY = bounds.y() + 2;
        for (int row = 0; row < visibleRows; row++) {
            String image = images.get(hudImageDropdownScroll + row);
            boolean selected = image.equals(selectedName);
            boolean hovered = inside(mouseX, mouseY, bounds.x() + 2, rowY, bounds.width() - 4, 16);
            if (selected || hovered) {
                renderControllerOption(graphics, bounds.x() + 2, rowY, bounds.width() - 4, 16,
                        AdvancedGraphCatalog.categoryColor("hud"), selected);
            }
            graphics.drawString(font, trim(image, 27), bounds.x() + 7, rowY + 4,
                    selected ? nodeValueTextColor() : 0xFFE0EDF4, false);
            rowY += 18;
        }
    }

    // Handle the HUD image dropdown click
    private boolean clickHudImageDropdown(double mouseX, double mouseY) {
        CompoundTag elm = hudElement();
        List<String> images = AdvancedHudImageClient.uploadedImages();
        int visibleRows = Math.min(HUD_IMAGE_VISIBLE_ROWS, images.size());
        UiRect bounds = hudImageDropdownBounds(visibleRows);
        if (elm != null && visibleRows > 0 && bounds.contains(mouseX, mouseY)) {
            int row = (int) ((mouseY - bounds.y() - 2) / 18);
            if (row >= 0 && row < visibleRows) {
                int idx = hudImageDropdownScroll + row;
                if (idx < images.size()) {
                    checkpoint();
                    String image = images.get(idx);
                    elm.putString("Texture", AdvancedHudImageStore.reference(image));
                    hudImageStatus = "Using " + image + ".";
                    syncHudFields();
                }
            }
        }
        hudImageDropdownOpen = false;
        return true;
    }

    // Upload the HUD image
    private void uploadHudImage() {
        CompoundTag elm = hudElement();
        if (elm == null || !"image".equals(elm.getString("Type"))) {
            return;
        }
        String selectedPath = TinyFileDialogs.tinyfd_openFileDialog(
                "Upload HUD image", "", null, null, false);
        if (selectedPath == null || selectedPath.isBlank()) {
            return;
        }
        try {
            Path image = Path.of(selectedPath);
            long size = Files.size(image);
            if (size <= 0) {
                hudImageStatus = "The selected image is empty.";
                return;
            }
            if (size > AdvancedHudImageStore.MAX_IMAGE_BYTES) {
                hudImageStatus = "Images must be 2 MiB or smaller.";
                return;
            }
            byte[] data = Files.readAllBytes(image);
            hudPendingImageNodeId = hudNodeId;
            hudPendingImageElementIdx = hudSelectedIdx;
            hudImageStatus = "Uploading " + image.getFileName() + "...";
            AdvancedHudImageUploadPayload.send(image.getFileName().toString(), data);
        } catch (IOException | RuntimeException err) {
            hudImageStatus = "The selected image could not be read.";
        }
    }

    // Embed the image reference
    private void embedImageReference(AdvancedGraphDocument.Node node) {
        if (!isImageReference(node) || pendingEmbeddedImageNodes.contains(node.id())) {
            return;
        }
        String selectedPath = TinyFileDialogs.tinyfd_openFileDialog(
                "Embed graph reference image", "", null, null, false);
        if (selectedPath == null || selectedPath.isBlank()) {
            return;
        }
        Path image;
        try {
            image = Path.of(selectedPath);
        } catch (RuntimeException err) {
            displayGraphActionToast(new GraphActionToast(
                    Component.literal("The selected image could not be embedded."),
                    GraphActionToastSeverity.ERROR));
            return;
        }
        String nodeId = node.id();
        String functionId = activeFunctionId;
        pendingEmbeddedImageNodes.add(nodeId);
        displayGraphActionToast(new GraphActionToast(
                Component.literal("Converting image in the background..."),
                GraphActionToastSeverity.WARNING));
        CompletableFuture.supplyAsync(() -> {
            try {
                long size = Files.size(image);
                if (size <= 0 || size > IMAGE_REFERENCE_MAX_BYTES) {
                    throw new ImageReferenceSizeException();
                }
                byte[] bytes = Files.readAllBytes(image);
                return new EmbeddedImage(imageMimeType(image),
                        Base64.getEncoder().encodeToString(bytes));
            } catch (IOException err) {
                throw new RuntimeException(err);
            }
        }).whenComplete((embedded, failure) -> Minecraft.getInstance().execute(() -> {
            pendingEmbeddedImageNodes.remove(nodeId);
            if (failure != null) {
                boolean tooLarge = failure.getCause() instanceof ImageReferenceSizeException
                        || failure instanceof ImageReferenceSizeException;
                displayGraphActionToast(new GraphActionToast(
                        Component.literal(tooLarge
                                ? "Reference images must be 256 KiB or smaller."
                                : "The selected image could not be embedded."),
                        GraphActionToastSeverity.ERROR));
                return;
            }
            AdvancedGraphDocument.Node currentNode = findNodeInGraph(functionId, nodeId);
            if (!isImageReference(currentNode)) {
                return;
            }
            checkpoint();
            AdvancedGraphImageAssets.embedBase64(
                    currentNode.data(), embedded.mimeType(), embedded.base64());
            clearGraphRenderCache();
            syncInspector();
            displayGraphActionToast(new GraphActionToast(
                    Component.literal("Image embedded in the graph."),
                    GraphActionToastSeverity.SUCCESS));
        }));
    }

    // Find the node in graph
    private AdvancedGraphDocument.Node findNodeInGraph(String functionId, String nodeId) {
        if (nodeId == null) {
            return null;
        }
        List<AdvancedGraphDocument.Node> nodes;
        if (functionId == null) {
            nodes = draft.nodes();
        } else {
            AdvancedGraphDocument.FunctionGraph function = draft.function(functionId);
            nodes = function == null ? List.of() : function.nodes();
        }
        return nodes.stream().filter(node -> nodeId.equals(node.id())).findFirst().orElse(null);
    }

    // Get the image mime type
    private static String imageMimeType(Path image) {
        String fileName = image.getFileName() == null ? "" : image.getFileName().toString()
                .toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/png";
    }

    // Apply the HUD image catalog
    public static void applyHudImageCatalog(List<String> images, String uploadedName,
                                            String message, boolean uploadResult) {
        AdvancedHudImageClient.applyCatalog(images);
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof AdvancedContraptionControllerScreen screen) {
            screen.applyProjectionHudImageCatalog(uploadedName, message, uploadResult);
        }
        AccDisplayGuiProjection.applyHudImageCatalog(uploadedName, message, uploadResult);
    }

    // Apply the projection HUD image catalog
    void applyProjectionHudImageCatalog(String uploadedName, String message,
                                        boolean uploadResult) {
        if (!uploadResult) {
            return;
        }
        String resultMessage = message == null ? "" : message;
        hudImageStatus = resultMessage;
        if (uploadedName == null || uploadedName.isBlank()) {
            hudPendingImageNodeId = null;
            hudPendingImageElementIdx = -1;
            return;
        }
        AdvancedGraphDocument.Node node = findNode(hudPendingImageNodeId);
        if (isAdvancedHudNode(node)) {
            ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
            int idx = hudPendingImageElementIdx;
            if (idx >= 0 && idx < elements.size()) {
                CompoundTag elm = elements.getCompound(idx);
                if ("image".equals(elm.getString("Type"))) {
                    checkpoint();
                    elm.putString("Texture", AdvancedHudImageStore.reference(uploadedName));
                    node.data().put(WIDGET_ELEMENTS, elements);
                    syncHudFields();
                }
            }
        }
        hudPendingImageNodeId = null;
        hudPendingImageElementIdx = -1;
    }

    // Move the HUD element
    private void moveHudElement(int dir) {
        AdvancedGraphDocument.Node node = hudNode();
        if (!isAdvancedHudNode(node) || dir == 0) return;
        ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
        int destination = hudSelectedIdx + Integer.signum(dir);
        if (hudSelectedIdx < 0 || hudSelectedIdx >= elements.size()
                || destination < 0 || destination >= elements.size()) return;
        checkpoint();
        Tag elm = elements.remove(hudSelectedIdx);
        elements.add(destination, elm);
        node.data().put(WIDGET_ELEMENTS, elements);
        hudSelectedIdx = destination;
        hudPortDropdownOpen = false;
        hudImageDropdownOpen = false;
        syncHudFields();
    }

    // Handle the HUD key press
    private boolean hudKeyPressed(int keyCode) {
        if (hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_C) {
                copyHudElement();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_X) {
                copyHudElement();
                removeHudElement();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V) {
                pasteHudElement();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            removeHudElement();
        }
        return true;
    }

    // Copy the HUD element
    private void copyHudElement() {
        CompoundTag elm = hudElement();
        if (elm != null) hudClipboard = elm.copy();
    }

    // Paste the HUD element
    private void pasteHudElement() {
        AdvancedGraphDocument.Node node = hudNode();
        if (!isAdvancedHudNode(node) || hudClipboard == null) return;
        checkpoint();
        CompoundTag elm = hudClipboard.copy();
        int elementWidth = Math.max(1, elm.getInt("W"));
        int elementHeight = Math.max(1, elm.getInt("H"));
        if ("value".equals(elm.getString("Type")) || "image".equals(elm.getString("Type"))) {
            List<String> ports = hudValPorts(node);
            if (!ports.contains(elm.getString("Port")) && !ports.isEmpty()) {
                elm.putString("Port", ports.getFirst());
            }
        }
        if (allowsOffSurfaceLayout(node)) {
            elm.putInt("X", Mth.clamp(elm.getInt("X") + HUD_PASTE_OFFSET,
                    -4096, 4096));
            elm.putInt("Y", Mth.clamp(elm.getInt("Y") + HUD_PASTE_OFFSET,
                    -4096, 4096));
        } else {
            int maxX = Math.max(0, node.data().getInt("WidgetWidth") - elementWidth);
            int maxY = Math.max(0, node.data().getInt("WidgetHeight") - elementHeight);
            elm.putInt("X", Mth.clamp(elm.getInt("X") + HUD_PASTE_OFFSET, 0, maxX));
            elm.putInt("Y", Mth.clamp(elm.getInt("Y") + HUD_PASTE_OFFSET, 0, maxY));
        }
        ListTag elements = node.data().getList(WIDGET_ELEMENTS, Tag.TAG_COMPOUND);
        if (isInteractiveHudType(elm.getString("Type"))) {
            elm.putString("InteractionId", UUID.randomUUID().toString());
            elm.remove("ExecPort");
            elm.remove("ValuePort");
        }
        elements.add(elm);
        node.data().put(WIDGET_ELEMENTS, elements);
        syncHudPorts(node);
        hudSelectedIdx = elements.size() - 1;
        hudPortDropdownOpen = false;
        hudImageDropdownOpen = false;
        draggingHudColorKey = null;
        draggingHudColorControl = null;
        hudFontStyleDropdownOpen = false;
        hudStyleDropdownOpen = false;
        syncHudFields();
    }

    // Get the HUD action btn
    private UiRect hudActionBtn(int idx) {
        UiRect bounds = hudBounds();
        return new UiRect(bounds.x() + 140 + idx * 68, bounds.y() + 38, 64, 20);
    }

    // Get the HUD palette tab
    private UiRect hudPaletteTab(boolean interactive) {
        UiRect bounds = hudBounds();
        return new UiRect(bounds.x() + 140 + (interactive ? 88 : 0),
                bounds.y() + 10, interactive ? 94 : 82, 20);
    }

    // Get the HUD remove btn
    private UiRect hudRemoveBtn() {
        UiRect bounds = hudBounds();
        return new UiRect(bounds.right() - 160, bounds.y() + 38, 70, 20);
    }

    // Get the HUD close btn
    private UiRect hudCloseBtn() {
        UiRect bounds = hudBounds();
        return new UiRect(bounds.right() - 82, bounds.y() + 38, 70, 20);
    }

    // Get the HUD canvas scale
    private double hudCanvasScale(AdvancedGraphDocument.Node node, UiRect canvas) {
        double widthScale = canvas.width() / (double) Math.max(1, node.data().getInt("WidgetWidth"));
        double heightScale = canvas.height() / (double) Math.max(1, node.data().getInt("WidgetHeight"));
        return Math.max(0.25, Math.min(widthScale, heightScale));
    }

    // Get the HUD preview x
    private int hudPreviewX(AdvancedGraphDocument.Node node, UiRect canvas) {
        int previewWidth = (int) Math.round(Math.max(1, node.data().getInt("WidgetWidth")) * hudCanvasScale(node, canvas));
        return canvas.x() + (canvas.width() - previewWidth) / 2;
    }

    // Get the HUD preview y
    private int hudPreviewY(AdvancedGraphDocument.Node node, UiRect canvas) {
        int previewHeight = (int) Math.round(Math.max(1, node.data().getInt("WidgetHeight")) * hudCanvasScale(node, canvas));
        return canvas.y() + (canvas.height() - previewHeight) / 2;
    }

    // Get the HUD element rect
    private UiRect hudElementRect(AdvancedGraphDocument.Node node, UiRect canvas, CompoundTag elm) {
        double scale = hudCanvasScale(node, canvas);
        int x = hudPreviewX(node, canvas) + (int) Math.round(elm.getInt("X") * scale);
        int y = hudPreviewY(node, canvas) + (int) Math.round(elm.getInt("Y") * scale);
        int width = Math.max(1, (int) Math.round(Math.max(1, elm.getInt("W")) * scale));
        int height = Math.max(1, (int) Math.round(Math.max(1, elm.getInt("H")) * scale));
        return new UiRect(x, y, width, height);
    }

    // Check if the transformed HUD element contains the point
    private static boolean hudElementContains(CompoundTag elm, UiRect rect,
                                              double mouseX, double mouseY) {
        DisplayWidgetProjection.Bounds bounds = new DisplayWidgetProjection.Bounds(
                rect.x(), rect.y(), rect.width(), rect.height());
        return DisplayWidgetProjection.unproject(bounds, mouseX, mouseY,
                elm.getDouble("Scale"), elm.getDouble("Rotation")).isInside(bounds);
    }

    // Get the designer HUD value label
    private String designerHudValueLabel(AdvancedGraphDocument.Node node, String port) {
        return wiredInputValueLabel(node, port, hudPortType(node, port));
    }

    // Sync the variable nodes
    private void synchronizeVariableNodes() {
        draft.removeUnusedVariables();
        Map<String, String> types = new LinkedHashMap<>();
        draft.variables().forEach((name, val) -> types.put(name, val.type()));
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            if (!"variable_set".equals(node.type())) continue;
            putVariableTypeOptions(node.data());
            String selectedType = inputString(node, "type", node.data().getString("VariableType"));
            if (!VARIABLE_TYPE_OPTIONS.contains(selectedType)) {
                selectedType = normalizeVariableType(node.data().getString("VariableType"));
            }
            node.data().putString("VariableType", selectedType);
            CompoundTag defaults = node.data().getCompound("Defaults");
            defaults.put("type", graphDefault("string", selectedType));
            node.data().put("Defaults", defaults);
            String variable = node.data().getString("Variable");
            if (!node.data().contains("RegisteredVariable", Tag.TAG_STRING) && validVariableName(variable)) {
                node.data().putString("RegisteredVariable", variable);
            }
            if (validVariableName(variable) && !draft.variables().containsKey(variable)) {
                draft.variables().put(variable, variableDefaultValue(node, selectedType));
            }
            if (validVariableName(variable)) types.put(variable, variableGraphType(selectedType));
        }
        for (AdvancedGraphDocument.Node node : activeNodes()) {
            String graphType = types.get(node.data().getString("Variable"));
            if ("variable_get".equals(node.type()) || "event_variable_change".equals(node.type())) {
                CompoundTag outputs = node.data().getCompound("DynamicOutputs");
                if (graphType == null) outputs.remove("value");
                else outputs.putString("value", graphType);
                if (outputs.isEmpty()) node.data().remove("DynamicOutputs");
                else node.data().put("DynamicOutputs", outputs);
            } else if ("variable_set".equals(node.type())) {
                if (graphType == null) continue;
                CompoundTag inputs = node.data().getCompound("DynamicInputs");
                inputs.putString("default", graphType);
                inputs.putString("value", graphType);
                node.data().put("DynamicInputs", inputs);
                CompoundTag outputs = node.data().getCompound("DynamicOutputs");
                outputs.putString("value", graphType);
                node.data().put("DynamicOutputs", outputs);
            }
        }
    }

    // Get the compact number
    private static String compactNumber(double val) {
        return Math.abs(val - Math.rint(val)) < 0.0001 ? Long.toString(Math.round(val))
                : String.format(Locale.ROOT, "%.2f", val);
    }

    // Sync the inspector
    private void syncInspector() {
        if (inspectorValue == null) return;
        if (rightSidebarCollapsed) {
            inspectorValue.setVisible(false);
            return;
        }
        inspectorValue.setWidth(RIGHT_WIDTH - 16);
        inspectorValue.setMaxLength(Integer.MAX_VALUE);
        CompoundTag group = selectedGroup();
        if (group != null) {
            inspectorValue.setVisible(true);
            inspectorValue.setX(graphRight() + 8);
            inspectorValue.setY(118);
            setInspectorValue(group.getString("Title"));
            return;
        }
        AdvancedGraphDocument.Node node = selectedNode();
        boolean aliasEditable = node != null
                && NODE_ALIAS_PROPERTY.equals(selectedInputPort);
        String property = editableProperty(node);
        String editableInputLabelPort = editableInputLabelPort(node, selectedInputPort);
        String outputPort = selectedOutputPort(node, selectedInputPort);
        String outputType = outputPort == null ? null
                : AdvancedGraphCatalog.outputs(node).get(outputPort);
        String inputType = node == null || selectedInputPort == null ? null : AdvancedGraphCatalog.inputs(node).get(selectedInputPort);
        boolean inputHasOptions = node != null && selectedInputPort != null && !inputOptions(node, selectedInputPort).isEmpty();
        boolean inputEditable = inputType != null && !isInputConnected(node, selectedInputPort)
                && !inputHasOptions && !List.of("exec", "boolean", "direction", "frequency", "target").contains(inputType);
        boolean propertyEditable = property != null
                && !List.of("OutputType", "CurveType", "MouseInput", "GogglesPair").contains(property)
                && !("event_variable_change".equals(node.type())
                && "Variable".equals(property))
                && (selectedInputPort == null || property.equals(selectedInputPort));
        boolean labelEditable = editableInputLabelPort != null;
        boolean outputEditable = outputType != null;
        inspectorValue.setVisible(aliasEditable || inputEditable || outputEditable
                || propertyEditable || labelEditable);
        inspectorValue.setX(graphRight() + 8);
        inspectorValue.setY(122);
        if (aliasEditable) {
            inspectorValue.setMaxLength(GraphNodeAlias.MAX_LENGTH);
            setInspectorValue(node.data().getString(GraphNodeAlias.DATA_KEY));
            return;
        }
        if (labelEditable) {
            setInspectorValue(inputDisplayLabel(node, editableInputLabelPort));
            return;
        }
        if (inputEditable) {
            setInspectorValue("number".equals(inputType) ? compactNumber(inputNumber(node, selectedInputPort))
                    : inputString(node, selectedInputPort, ""));
            return;
        }
        if (outputEditable) {
            setInspectorValue(outputValueText(
                    AdvancedGraphPortState.outputDefault(node, outputPort, outputType), outputType));
            return;
        }
        if (!propertyEditable) {
            setInspectorValue("");
            return;
        }
        String val = switch (property) {
            case "Value" -> node.type().equals("constant_number") ? Double.toString(node.data().getDouble(property))
                    : node.type().equals("constant_boolean") ? Boolean.toString(node.data().getBoolean(property))
                    : node.data().getString(property);
            case "Ticks", "Period", "OutputCount", "InputCount" -> Integer.toString(node.data().getInt(property));
            default -> node.data().getString(property);
        };
        setInspectorValue(val);
    }

    // Set the inspector value
    private void setInspectorValue(String val) {
        syncingInspectorValue = true;
        try {
            inspectorValue.setValue(val);
        } finally {
            syncingInspectorValue = false;
        }
    }

    // Check if this is valid variable name
    private static boolean validVariableName(String variable) {
        return AdvancedGraphDocument.isValidVariableName(variable);
    }

    // Get the variable default value
    private static AdvancedGraphDocument.Value variableDefaultValue(AdvancedGraphDocument.Node node,
                                                                     String selectedType) {
        CompoundTag entry = node.data().getCompound("Defaults").getCompound("default");
        CompoundTag payload = entry.getCompound("Payload");
        return switch (variableGraphType(selectedType)) {
            case "boolean" -> AdvancedGraphDocument.Value.bool(payload.getBoolean("Value"));
            case "string" -> AdvancedGraphDocument.Value.string(payload.getString("Value"));
            case "direction" -> AdvancedGraphDocument.Value.direction(payload.getString("Value"));
            case "frequency" -> AdvancedGraphDocument.Value.frequency(payload);
            case "target" -> AdvancedGraphDocument.Value.target(payload);
            case "list" -> AdvancedGraphDocument.Value.list(payload);
            case "map" -> AdvancedGraphDocument.Value.map(payload);
            default -> AdvancedGraphDocument.Value.number(payload.getDouble("Value"));
        };
    }

    // Open the body editor
    private void openBodyEditor(AdvancedGraphDocument.Node node, String port, int rowY, int rowHeight) {
        if (inspectorValue == null || node == null || port == null) return;
        editingBodyValue = true;
        selectedInputPort = port;
        syncInspector();
        inspectorValue.setVisible(true);
        inspectorValue.setWidth(Math.max(44, (int) (NODE_WIDTH * zoom * 0.42)));
        inspectorValue.setX(screenX(node.x()) + (int) (NODE_WIDTH * zoom * 0.55));
        inspectorValue.setY(rowY - 2);
        inspectorValue.setHeight(Math.max(12, rowHeight + 2));
        inspectorValue.setFocused(true);
        setFocused(inspectorValue);
    }

    // Update the node property
    private void updateNodeProperty(String val) {
        // -----------------------------------------------------UPDATE CHECK-----------------------------------------------------
        if (syncingInspectorValue) return;
        // ------------------------------------GROUP PROPERTY------------------------------------
        CompoundTag group = selectedGroup();
        if (group != null) {
            group.putString("Title", val);
            return;
        }
        AdvancedGraphDocument.Node node = selectedNode();
        if (node != null && NODE_ALIAS_PROPERTY.equals(selectedInputPort)) {
            String alias = GraphNodeAlias.normalize(val);
            if (GraphNodeAlias.isValid(alias)) {
                if (alias.isBlank()) node.data().remove(GraphNodeAlias.DATA_KEY);
                else node.data().putString(GraphNodeAlias.DATA_KEY, alias);
            }
            return;
        }
        // -----------------------------------------------------INPUT LABEL-----------------------------------------------------
        String editableInputLabelPort = editableInputLabelPort(node, selectedInputPort);
        if (editableInputLabelPort != null) {
            if (isHudNode(node)) {
                renameHudFieldLabel(node, editableInputLabelPort, val);
            } else {
                renameConstructorInput(node, editableInputLabelPort, val);
            }
            return;
        }
        String inputType = node == null || selectedInputPort == null ? null : AdvancedGraphCatalog.inputs(node).get(selectedInputPort);
        // -----------------------------------------------------INPUT DEFAULT-----------------------------------------------------
        if (inputType != null && !isInputConnected(node, selectedInputPort)
                && inputOptions(node, selectedInputPort).isEmpty()) {
            try {
                if ("number".equals(inputType)) {
                    double num = Double.parseDouble(val);
                    putInputDefault(node, selectedInputPort, inputType,
                            isWholeNumberValue(node, selectedInputPort) ? Math.round(num) : num);
                } else {
                    putInputDefault(node, selectedInputPort,
                            "any".equals(inputType) ? "string" : inputType, val);
                }
            } catch (NumberFormatException ignored) {
            }
            return;
        }
        // ------------------------------------OUTPUT PROPERTY------------------------------------
        String outputPort = selectedOutputPort(node, selectedInputPort);
        if (outputPort != null) {
            String outputType = AdvancedGraphCatalog.outputs(node).getOrDefault(outputPort, "any");
            AdvancedGraphDocument.Value parsed = switch (outputType) {
                case "boolean" -> AdvancedGraphDocument.Value.bool(Boolean.parseBoolean(val));
                case "number" -> {
                    try {
                        yield AdvancedGraphDocument.Value.number(Double.parseDouble(val));
                    } catch (NumberFormatException ignored) {
                        yield null;
                    }
                }
                case "direction" -> AdvancedGraphDocument.Value.direction(val);
                case "frequency" -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("Value", val);
                    yield AdvancedGraphDocument.Value.frequency(payload);
                }
                case "target" -> {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("Label", val);
                    yield AdvancedGraphDocument.Value.target(payload);
                }
                default -> AdvancedGraphDocument.Value.string(val);
            };
            if (parsed != null) {
                AdvancedGraphPortState.setOutputDefault(
                        node, outputPort, outputType, parsed);
                clearGraphRenderCache();
            }
            return;
        }
        String property = editableProperty(node);
        if (node == null || property == null) return;
        try {
            if ("Value".equals(property) && "constant_number".equals(node.type())) node.data().putDouble(property, Double.parseDouble(val));
            else if ("Value".equals(property) && "constant_boolean".equals(node.type())) node.data().putBoolean(property, Boolean.parseBoolean(val));
            else if ("Ticks".equals(property) || "Period".equals(property)) node.data().putInt(property, Math.max(1, Integer.parseInt(val)));
            else if ("OutputCount".equals(property) && isExecutionSplitterNode(node)) {
                setExecutionOutputCount(node, Integer.parseInt(val), true);
            }
            else if ("InputCount".equals(property) && isExecutionCombinerNode(node)) {
                setExecutionInputCount(node, Integer.parseInt(val), true);
            }
            else {
                String prev = node.data().getString(property);
                String nextVal = "Variable".equals(property) ? val.strip() : val;
                node.data().putString(property, nextVal);
                if ("Source".equals(property) && isImageReference(node)
                        && !nextVal.startsWith(AdvancedGraphImageAssets.REFERENCE_PREFIX)) {
                    node.data().remove(AdvancedGraphImageAssets.ASSET_ID);
                    node.data().remove(AdvancedGraphImageAssets.MIME_TYPE);
                    node.data().remove(AdvancedGraphImageAssets.BASE64_CHUNKS);
                }
                if ("Variable".equals(property) && "variable_set".equals(node.type())
                        && validVariableName(nextVal)) {
                    String registered = node.data().getString("RegisteredVariable");
                    if (registered.isBlank()) registered = prev;
                    AdvancedGraphDocument.Value initial = draft.variables().get(registered);
                    if (initial == null) initial = draft.variables().get(prev);
                    if (initial == null) {
                        String type = node.data().getString("VariableType");
                        initial = "boolean".equals(type) ? AdvancedGraphDocument.Value.bool(false)
                                : "string".equals(type) ? AdvancedGraphDocument.Value.string("")
                                : AdvancedGraphDocument.Value.number(0);
                    }
                    draft.variables().put(nextVal, initial);
                    boolean definition = !node.data().contains("VariableDefinition", Tag.TAG_BYTE)
                            || node.data().getBoolean("VariableDefinition");
                    if (definition && !registered.equals(nextVal)) {
                        boolean sharedDefinition = varUsedByOtherDef(registered, node.id());
                        if (!sharedDefinition) {
                    relinkVarNodes(registered, nextVal, node.id());
                            draft.variables().remove(registered);
                        }
                    }
                    node.data().putString("RegisteredVariable", nextVal);
                }
                if ("Variable".equals(property)) synchronizeVariableNodes();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                         SAVE / SYNC
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Save the draft
    private void saveDraft() {
        send("save", "");
    }

    // Save and apply the draft
    private void saveAndApplyDraft(String action, long requestId) {
        storeViewport();
        pendingGraphSaves.put(requestId, draft.copy());
        send(action, "", requestId);
    }

    // Check if this has unsaved draft
    private boolean hasUnsavedDraft() {
        return draftDirty;
    }

    // Save the draft on close
    private void saveDraftOnClose() {
        if (!shouldSaveDraftOnClose(saveOnClose, closeSaveSent, hasUnsavedDraft())) {
            return;
        }
        long requestId = GRAPH_ACTION_REQ_IDS.incrementAndGet();
        saveOnCloseReqId = requestId;
        saveAndApplyDraft("save_apply_close", requestId);
        closeSaveSent = true;
    }

    // Check if this should save draft on close
    static boolean shouldSaveDraftOnClose(boolean saveOnClose, boolean closeSaveSent, boolean unsavedDraft) {
        return saveOnClose && !closeSaveSent && unsavedDraft;
    }

    // Send the advanced contraption controller
    private void send(String action, String argument) {
        send(action, argument, 0L);
    }

    // Send the advanced contraption controller
    private void send(String action, String argument, long requestId) {
        storeViewport();
        syncHudPorts(draft);
        clearGraphRenderCache();
        MenuConfigTarget target = MenuConfigTarget.of(menu.getContentPos(), menu.getContentSubLevelId());
        PacketDistributor.sendToServer(new AdvancedContraptionControllerGraphPayload(
                target, action, savedDraft.revision(), draft.toTag(), argument, requestId));
    }

    // Check if this is a pause screen
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Get the slot screen x
    public int slotScreenX(net.minecraft.world.inventory.Slot slot) {
        return leftPos + slot.x;
    }

    // Get the slot screen y
    public int slotScreenY(net.minecraft.world.inventory.Slot slot) {
        return topPos + slot.y;
    }

    // Handle the close event
    @Override
    public void onClose() {
        if (frequencyModalOpen) closeFrequencyEditor(false);
        AccGuiScaleOverride.restoreAfterExit();
        saveDraftOnClose();
        super.onClose();
    }

    // Handle screen removal
    @Override
    public void removed() {
        AnalogueContraptionControllerClientHandler.clearOpenMenuHardwareControllerInput(menu);
        ControllerGraphWebServer.clearOpenGraph();
        for (Set<String> bindings : activeGraphKeyBindings.values()) {
            for (String binding : bindings) {
                PacketDistributor.sendToServer(new AnalogueContraptionControllerKeyPayload(
                        menu.getContentPos(), menu.getContentSubLevelId(), binding, false));
            }
        }
        activeGraphKeyBindings.clear();
        if (frequencyModalOpen) closeFrequencyEditor(false);
        if (openingFunctionPlotter) {
            openingFunctionPlotter = false;
        } else {
            saveDraftOnClose();
        }
        clearGraphActionToast();
        if (initialEmiVisibility != null) RecipeViewerVisibility.setEmiVisible(initialEmiVisibility);
        setLinkerOpen(false, false);
        super.removed();
        AccGuiScaleOverride.restoreAfterExit();
    }

    // Define the graph action toast severity values
    private enum GraphActionToastSeverity {
        SUCCESS(16),
        ERROR(35),
        WARNING(54);

        // Source y
        private final int sourceY;

        // Initialize the graph action toast severity
        GraphActionToastSeverity(int sourceY) {
            this.sourceY = sourceY;
        }

        // Get the source y
        private int sourceY() {
            return sourceY;
        }
    }

    // Define the shared graph save target values
    private enum SharedGraphSaveTarget {
        NONE,
        LOCAL,
        SERVER,
        LOCAL_AND_SERVER
    }

    // Relink the var nodes
    private void relinkVarNodes(String prev, String replacement, String definitionNode) {
        if (!validVariableName(prev) || !validVariableName(replacement)) return;
        for (AdvancedGraphDocument.Node candidate : activeNodes()) {
            if (candidate.id().equals(definitionNode) || !prev.equals(candidate.data().getString("Variable"))) continue;
            boolean accessor = "variable_get".equals(candidate.type())
                    || "event_variable_change".equals(candidate.type())
                    || ("variable_set".equals(candidate.type())
                    && candidate.data().contains("VariableDefinition", Tag.TAG_BYTE)
                    && !candidate.data().getBoolean("VariableDefinition"));
            if (!accessor) continue;
            candidate.data().putString("Variable", replacement);
            if ("variable_set".equals(candidate.type())) {
                candidate.data().putString("RegisteredVariable", replacement);
            }
        }
    }

    // Check if another definition uses the variable
    private boolean varUsedByOtherDef(String variable, String excludedNode) {
        if (!validVariableName(variable)) return false;
        for (AdvancedGraphDocument.Node candidate : activeNodes()) {
            if (candidate.id().equals(excludedNode) || !"variable_set".equals(candidate.type())) continue;
            boolean definition = !candidate.data().contains("VariableDefinition", Tag.TAG_BYTE)
                    || candidate.data().getBoolean("VariableDefinition");
            if (definition && variable.equals(candidate.data().getString("Variable"))) return true;
        }
        return false;
    }

    // Store the graph action toast
    private record GraphActionToast(Component message, GraphActionToastSeverity severity) {
    }

    // Handle the recipe viewer visibility
    private static final class RecipeViewerVisibility {
        // Shared EMI enabled
        private static java.lang.reflect.Field emiEnabled;
        // Shared EMI recalculate
        private static java.lang.reflect.Method emiRecalculate;
        // Tracks whether recipe viewer visibility is initialized
        private static boolean initialized;

        // Check if EMI is visible
        private static Boolean isEmiVisible() {
            initialize();
            if (emiEnabled == null) return null;
            try {
                return emiEnabled.getBoolean(null);
            } catch (Throwable ignored) {
                return null;
            }
        }

        // Set the EMI visible
        private static void setEmiVisible(boolean visible) {
            initialize();
            if (emiEnabled == null) return;
            try {
                if (emiEnabled.getBoolean(null) == visible) return;
                emiEnabled.setBoolean(null, visible);
                if (emiRecalculate != null) emiRecalculate.invoke(null);
            } catch (Throwable ignored) {
            }
        }

        // Recalculate EMI
        private static void recalculateEmi() {
            initialize();
            if (emiRecalculate == null) return;
            try {
                emiRecalculate.invoke(null);
            } catch (Throwable ignored) {
            }
        }

        // Initialize the recipe viewer visibility
        private static void initialize() {
            if (initialized) return;
            initialized = true;
            if (!ModList.get().isLoaded("emi")) return;
            try {
                Class<?> conf = Class.forName("dev.emi.emi.config.EmiConfig");
                Class<?> manager = Class.forName("dev.emi.emi.screen.EmiScreenManager");
                emiEnabled = conf.getField("enabled");
                emiRecalculate = manager.getMethod("forceRecalculate");
            } catch (Throwable ignored) {
                emiEnabled = null;
                emiRecalculate = null;
            }
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                          TOOLTIPS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the human port
    private static String humanPort(String val) {
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

    // Get the node browser tooltip
    private List<Component> nodeBrowserTooltip(double mouseX, double mouseY) {
        if (frequencyModalOpen || hudOpen) {
            return null;
        }
        if (miniBrowser != null) {
            int panelWidth = MINI_BROWSER_WIDTH;
            int panelHeight = MINI_BROWSER_HEIGHT;
            int panelX = Mth.clamp(miniBrowser.x(), graphLeft(), graphRight() - panelWidth);
            int panelY = Mth.clamp(miniBrowser.y(), graphTop(), graphBottom() - panelHeight);
            if (inside(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)) {
                int rowY = panelY + miniBrowserRowsTop() - miniBrowserScroll;
                for (BrowserEntry entry : miniBrowserEntries()) {
                    int rowHeight = entry.category() ? 20 : 16;
                    if (mouseX >= panelX + 4 && mouseX < panelX + panelWidth - 4
                            && mouseY >= rowY - 2 && mouseY < rowY + 13) {
                        return browserEntryTooltip(entry);
                    }
                    rowY += rowHeight;
                }
                return List.of();
            }
        }
        if (blockBrowserOpen || leftSidebarCollapsed
                || mouseX < layoutLeft() || mouseX >= layoutLeft() + activeLeftWidth()
                || mouseY < 82 || mouseY >= height) {
            return null;
        }
        int rowY = 86 - browserScroll;
        String query = nodeSearch == null ? "" : nodeSearch.getValue().trim().toLowerCase(Locale.ROOT);
        for (BrowserEntry entry : nodeBrowserEntries(query)) {
            int rowHeight = browserEntryRowHeight(entry);
            if (mouseY >= rowY - 2 && mouseY < rowY + rowHeight - 2) {
                return browserEntryTooltip(entry);
            }
            rowY += rowHeight;
        }
        return List.of();
    }

    // Get the browser entry tooltip
    private List<Component> browserEntryTooltip(BrowserEntry entry) {
        if (entry.category()) {
            return List.of(Component.literal(v2Ui ? v2CategoryName(entry.id())
                    : AdvancedGraphCatalog.categoryName(entry.id())));
        }
        if (entry.id().startsWith("function:")) {
            return List.of();
        }
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(browserEntryName(entry.id()) + " (" + entry.id() + ")"));
        if (Screen.hasShiftDown()) {
            Component summary = Component.translatable(AdvancedGraphCatalog.summaryTranslationKey(entry.id()));
            if (!summary.getString().isBlank()) {
                tooltip.add(summary.copy().withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable(NODE_SUMMARY_HELPER_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }
        return tooltip;
    }

    // Get the hover tooltip
    private String hoverTooltip(double mouseX, double mouseY) {
        // -----------------------------------------------------OVERLAYS-----------------------------------------------------
        if (frequencyModalOpen || hudOpen) {
            return null;
        }
        if (v2Ui && graphRight() - graphLeft() >= 170 && graphBottom() - graphTop() >= 120
                && (v2MinimapBounds().contains(mouseX, mouseY)
                || v2CanvasStatusBounds().contains(mouseX, mouseY))) {
            return null;
        }
        // -----------------------------------------------------GRAPH PORTS-----------------------------------------------------
        ForceWriteHit forceWriteHit = inGraph(mouseX, mouseY) ? setDataForceWriteAt(mouseX, mouseY) : null;
        if (forceWriteHit != null) {
            return "Force update value";
        }
        PortHit hit = inGraph(mouseX, mouseY) ? portAt(mouseX, mouseY) : null;
        if (hit != null) {
            String type = (hit.output() ? AdvancedGraphCatalog.outputs(hit.node()) : AdvancedGraphCatalog.inputs(hit.node())).get(hit.port());
            String label = hit.output() ? outputDisplayLabel(hit.node(), hit.port())
                    : inputDisplayLabel(hit.node(), hit.port());
            return trim(label + " (" + humanPort(type) + "): "
                    + portValueLabel(hit, type), PORT_TOOLTIP_MAX_CHARACTERS);
        }
        if (inGraph(mouseX, mouseY)) return null;
        // -----------------------------------------------------INSPECTOR-----------------------------------------------------
        if (!rightSidebarCollapsed && mouseX >= graphRight() && mouseX < layoutRight() && mouseY >= TOOLBAR_HEIGHT) {
            AdvancedGraphDocument.Node node = selectedNode();
            if (node == null) return null;
            InspectorSections sections = inspectorSections(node);
            int optionsTop = sections.optionsTop();
            int optionsBottom = sections.optionsBottom();
            int rowY = optionsTop - inspectorOptionsScroll;
            boolean inOptions = !inspectorOptionsCollapsed
                    && mouseY >= optionsTop && mouseY < optionsBottom;
            if (inOptions && mouseY >= rowY - 3 && mouseY < rowY + 13) {
                return "Alias used by CC:Tweaked in place of this node ID";
            }
            rowY += 17;
            if (hasSwitchTypeControl(node)) {
                if (inOptions && mouseY >= rowY - 3 && mouseY < rowY + 13) return "Type";
                rowY += 17;
            }
            if (hasPropertyControl(node)) {
                if (inOptions && mouseY >= rowY - 3 && mouseY < rowY + 13) return editableProperty(node);
                rowY += 17;
            }
            for (var port : AdvancedGraphCatalog.inputs(node).entrySet()) {
                if ("exec".equals(port.getValue())
                        || ("curve".equals(node.type()) && "value".equals(port.getKey()))) continue;
                int rowHeight = "frequency".equals(port.getValue()) ? 22 : 17;
                if (inOptions && mouseY >= rowY - 3 && mouseY < rowY + rowHeight - 4) {
                    String label = inputDisplayLabel(node, port.getKey());
                    String hint = (isHudNode(node) && !isHudReservedPort(port.getKey())
                            || isConstructorNode(node) && constructorValuePort(node, port.getKey()))
                            ? " - right-click to rename" : "";
                    return label + " (" + humanPort(port.getValue()) + ")" + hint;
                }
                rowY += rowHeight;
            }
            if (isConstructorNode(node) && inOptions
                    && mouseY >= rowY - 3 && mouseY < rowY + 13) {
                return "Add another input";
            }
            int targetsTop = sections.targetsTop();
            int targetsBottom = sections.targetsBottom();
            boolean inTargets = !inspectorTargetsCollapsed
                    && mouseY >= targetsTop && mouseY < targetsBottom;
            rowY = targetsTop + 8 - inspectorTargetsScroll;
            // ------------------------------------BINDING TARGETS------------------------------------
            if (usesBinding(node)) {
                rowY += 15;
                for (AdvancedContraptionControllerMenu.GraphBindingOption option : bindingOptions(node)) {
                    if (inTargets && mouseY >= rowY - 2 && mouseY < rowY + 12) return option.label();
                    rowY += 14;
                }
            }
            // ------------------------------------DISCOVERY TARGETS------------------------------------
            if (usesTarget(node)) {
                rowY += 20;
                for (ControllerDiscoveryNode target : graphTargetOptions(node)) {
                    if (inTargets && mouseY >= rowY - 2 && mouseY < rowY + 12) {
                        return target.label().isBlank() ? target.nodeId() : target.label();
                    }
                    rowY += 14;
                    if (target.nodeId().equals(node.data().getString("Target"))) {
                        for (AeroworksControllerCompat.ConsoleSection section :
                                aeroworksSectionsForTarget(node, target)) {
                            if (inTargets && mouseY >= rowY - 2 && mouseY < rowY + 12) {
                                return section.label();
                            }
                            rowY += 14;
                        }
                    }
                }
                List<ControllerDiscoveryNode> scmTargets = graphScmTargetOptions(node);
                if (!scmTargets.isEmpty()) {
                    rowY += 20;
                    for (ControllerDiscoveryNode target : scmTargets) {
                        if (inTargets && mouseY >= rowY - 2 && mouseY < rowY + 12) {
                            return target.label().isBlank() ? target.nodeId() : target.label();
                        }
                        rowY += 14;
                    }
                }
            }
        }
        return null;
    }

    // Get the port value label
    private String portValueLabel(PortHit hit, String type) {
        AdvancedContraptionControllerBlockEntity controller = menu.getMenuConfigTargetBlockEntity();
        if ("exec".equals(type)) {
            if (!hit.output() || minecraft == null || minecraft.level == null) return "flow";
            long latest = Long.MIN_VALUE;
            for (AdvancedGraphDocument.Edge edge : activeEdges()) {
                if (edge.fromNode().equals(hit.node().id()) && edge.fromPort().equals(hit.port())) {
                    latest = Math.max(latest, graphExecutionPulse(
                            controller, GraphRuntime.executionEdgeKey(edge)));
                }
            }
            return latest == Long.MIN_VALUE ? "not pulsed"
                    : "pulsed " + Math.max(0, minecraft.level.getGameTime() - latest) + " ticks ago";
        }
        if (!hit.output()) return wiredInputValueLabel(hit.node(), hit.port(), type);
        if (controller == null) return "...";
        AdvancedGraphLiveValue liveValue = controller.getGraphLiveOutput(hit.node().id(), hit.port());
        if (!hasUnsavedDraft() && liveValue != null) return graphValueLabel(liveValue, type);
        AdvancedGraphLiveValue simulatedValue = simulatedLiveOutput(hit.node().id(), hit.port());
        if (simulatedValue != null) return graphValueLabel(simulatedValue, type);
        if (liveValue != null) return graphValueLabel(liveValue, type);
        return graphValueLabel(controller.previewGraphOutput(draft, hit.node(), hit.port()), type);
    }

    // Get the graph value label
    private String graphValueLabel(AdvancedGraphLiveValue val, String type) {
        if (val == null) return "...";
        String displayType = "any".equals(type) ? val.type() : type;
        return switch (displayType) {
            case "boolean" -> val.booleanValue() ? "true" : "false";
            case "number" -> compactNumber(val.numberValue());
            case "string", "direction", "target" -> valueOrUnset(val.textValue());
            default -> val.textValue().isBlank() ? val.type() : val.textValue();
        };
    }

    // Check if this is rotation speed controller speed
    private static boolean isRotationSpeedControllerSpeed(AdvancedGraphDocument.Node node, String port) {
        return "set_block_data".equals(node.type()) && "speed".equals(port)
                && CreateRotationSpeedControllerGraphCompat.BLOCK_ID.equalsIgnoreCase(
                node.data().getCompound("TargetData").getString("BlockId"));
    }

    // Get the graph value label
    private String graphValueLabel(AdvancedGraphDocument.Value val, String type) {
        if (val == null) return "...";
        String displayType = "any".equals(type) ? val.type() : type;
        return switch (displayType) {
            case "boolean" -> val.asBoolean() ? "true" : "false";
            case "number" -> compactNumber(val.asNumber());
            case "string", "direction" -> valueOrUnset(val.asString());
            case "target" -> valueOrUnset(val.payload().getString("Label"));
            default -> val.payload().isEmpty() ? val.type() : val.payload().toString();
        };
    }

    // Get the output value text
    private String outputValueText(AdvancedGraphDocument.Value val, String declaredType) {
        if (val == null) return "";
        String type = "any".equals(declaredType) ? val.type() : declaredType;
        return switch (type) {
            case "boolean" -> Boolean.toString(val.asBoolean());
            case "number" -> Double.toString(val.asNumber());
            case "string", "direction" -> val.asString();
            case "target" -> val.payload().getString("Label");
            case "frequency" -> val.payload().getString("Value");
            default -> val.payload().toString();
        };
    }

    // Trim the advanced contraption controller
    private static String trim(String val, int max) {
        if (val == null) return "";
        return val.length() <= max ? val : val.substring(0, Math.max(0, max - 3)) + "...";
    }

    // Check if this is inside
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // Get the value or unset
    private static String valueOrUnset(String val) {
        return val == null || val.isBlank() ? "unset" : val;
    }

    // Check if this uses binding
    private static boolean usesBinding(AdvancedGraphDocument.Node node) {
        return node != null && (node.type().startsWith("controller_")
                || "gamepad_input".equals(node.type())
                || node.type().startsWith("local_redstone")
                || "event_channel_change".equals(node.type())
                || "event_redstone_change".equals(node.type()));
    }

    // Get the binding options
    private List<AdvancedContraptionControllerMenu.GraphBindingOption> bindingOptions(
            AdvancedGraphDocument.Node node) {
        return node != null && "gamepad_input".equals(node.type())
                ? menu.getGamepadBindingOptions() : menu.getGraphBindingOptions();
    }

    // Get the binding options title
    private static String bindingOptionsTitle(AdvancedGraphDocument.Node node) {
        return node != null && "gamepad_input".equals(node.type())
                ? "Gamepad Controls" : "Configured Key Bindings";
    }

    // Check if this uses target
    private static boolean usesTarget(AdvancedGraphDocument.Node node) {
        return node != null && (node.type().contains("target") || node.type().startsWith("linker_face")
                || node.type().startsWith("acc_display_")
                || "get_block_data".equals(node.type()) || "set_block_data".equals(node.type()));
    }

    // Check if this supports SCM block target
    private static boolean supportsScmBlockTarget(AdvancedGraphDocument.Node node) {
        return node != null && ("get_block_data".equals(node.type())
                || "set_block_data".equals(node.type())
                || "discovered_target_input".equals(node.type())
                || "direct_target_output".equals(node.type()));
    }

    // Check if this is an ACC display target
    private static boolean isAccDisplayTarget(ControllerDiscoveryNode target) {
        if (target == null) {
            return false;
        }
        return target.kind() == ControllerDiscoveryKind.DISPLAY
                || target.blockId().startsWith("createthrusters:acc_display");
    }

    // Check if this is a display adapter target
    private static boolean isDisplayAdapterTarget(ControllerDiscoveryNode target) {
        return target != null && (target.kind() == ControllerDiscoveryKind.DISPLAY_ADAPTER
                || "createthrusters:universal_display_adapter".equals(target.blockId()));
    }

    // Toggle a set value
    private static <T> void toggle(Set<T> values, T val) {
        if (!values.add(val)) values.remove(val);
    }

    // Handle the V2 toolbar button
    private final class V2ToolbarButton extends Button {
        // Initialize the V2 toolbar button
        private V2ToolbarButton(int x, int y, int width, int height,
                                Component msg, OnPress onPress) {
            super(x, y, width, height, msg, onPress, DEFAULT_NARRATION);
        }

        // Draw the widget
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean primary = "Save".equals(getMessage().getString());
            int border = primary
                    ? isHoveredOrFocused()
                            ? AdvancedControllerV2Theme.ACCENT_LIGHT
                            : AdvancedControllerV2Theme.ACCENT_DARK
                    : isHoveredOrFocused() ? AdvancedControllerV2Theme.ACCENT
                    : AdvancedControllerV2Theme.BORDER;
            int fill = primary
                    ? isHoveredOrFocused()
                            ? AdvancedControllerV2Theme.ACCENT_LIGHT
                            : AdvancedControllerV2Theme.ACCENT
                    : isHoveredOrFocused() ? AdvancedControllerV2Theme.PANEL_HOVERED
                    : AdvancedControllerV2Theme.PANEL_RAISED;
            AdvancedControllerV2Theme.drawRoundedRect(
                    graphics, getX(), getY(), getWidth(), getHeight(), 5, border);
            AdvancedControllerV2Theme.drawRoundedRect(
                    graphics, getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, 4, fill);
            graphics.drawCenteredString(font, getMessage(),
                    getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
                    active
                            ? primary
                                    ? AdvancedControllerV2Theme.PRIMARY_ACTION_TEXT
                                    : AdvancedControllerV2Theme.PRIMARY
                            : AdvancedControllerV2Theme.MUTED);
        }
    }

    // Store the browser entry
    private record BrowserEntry(String id, boolean category) {
    }

    // Store the graph template option
    private record GraphTemplateOption(String id, String label) {
    }

    // Store the registry entry
    private record RegistryEntry(String id, ItemStack stack, boolean header) {
    }

    // Store the port hit
    private record PortHit(AdvancedGraphDocument.Node node, String port, boolean output) {
    }

    // Store the force write hit
    private record ForceWriteHit(AdvancedGraphDocument.Node node, String port) {
    }

    // Store the port position
    private record PortPosition(int x, int y) {
    }

    // Handle the node port layout
    private static final class NodePortLayout {
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
        private NodePortLayout(Map<String, String> inputs, Map<String, String> outputs,
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
        private Map<String, String> inputs() {
            return inputs;
        }

        // Get the outputs
        private Map<String, String> outputs() {
            return outputs;
        }

        // Get the visible inputs
        private Set<String> visibleInputs() {
            return visibleInputs;
        }

        // Get the visible outputs
        private Set<String> visibleOutputs() {
            return visibleOutputs;
        }

        // Get the connected inputs
        private Set<String> connectedInputs() {
            return connectedInputs;
        }

        // Get the output offsets
        private Map<String, Integer> outputOffsets() {
            return outputOffsets;
        }

        // Get the output labels
        private Map<String, List<FormattedCharSequence>> outputLabels() {
            return outputLabels;
        }

        // Check if the node can be collapsed
        private boolean collapsible() {
            return collapsible;
        }

        // Check if the node is collapsed
        private boolean collapsed() {
            return collapsed;
        }

        // Get the output data height
        private int outputDataHeight() {
            return outputDataHeight;
        }

        // Set the output data height
        private void setOutputDataHeight(int outputDataHeight) {
            this.outputDataHeight = Math.max(0, outputDataHeight);
        }
    }

    // Store the sticky edit line
    private record StickyEditLine(String text, int start, int end) {
    }

    // Store the slider track
    private record SliderTrack(int left, int right) {
        // Get the width
        private int width() {
            return Math.max(1, right - left);
        }

        // Check if this contains x
        private boolean containsX(double mouseX, int margin) {
            return mouseX >= left - margin && mouseX <= right + margin;
        }
    }

    // Store the clipboard bounds
    private record ClipboardBounds(double left, double top, double right, double bottom) {
        // Get the center x
        private double centerX() {
            return (left + right) * 0.5D;
        }

        // Get the center y
        private double centerY() {
            return (top + bottom) * 0.5D;
        }
    }

    // Store the inspector sections
    private record InspectorSections(int optionsHeaderTop, int optionsTop, int optionsBottom,
                                     int optionsDividerTop, int targetsHeaderTop, int targetsTop,
                                     int targetsBottom, int targetsDividerTop, int variablesHeaderTop,
                                     int variablesTop, int variablesBottom) {
    }

    // Define the inspector divider values
    private enum InspectorDivider {
        OPTIONS,
        TARGETS
    }

    // Define the HUD designer color control values
    private enum HudDesignerColorControl {
        HUE,
        SATURATION,
        VALUE,
        ALPHA
    }

    // Store the HUD designer hsv
    private record HudDesignerHsv(int rgb, float hue, float saturation, float value) {
    }

    // Store the UI rect
    private record UiRect(int x, int y, int width, int height) {
        // Get the right
        private int right() {
            return x + width;
        }

        // Get the bottom
        private int bottom() {
            return y + height;
        }

        // Check if this contains the value
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }

    // Store the context menu
    private record ContextMenu(int x, int y, List<String> items, String query,
                               PortContext port) {
    }

    // Store port context
    private record PortContext(String nodeId, String port, boolean output) {
    }

    // Store the option dropdown
    private record OptionDropdown(int x, int y, int width, String nodeId, String port, String type,
                                  List<String> options, boolean property, int scroll,
                                  boolean searchable) {
    }

    // Store the dropdown scrollbar
    private record DropdownScrollbar(
            int trackX,
            int trackTop,
            int trackHeight,
            int thumbTop,
            int thumbHeight,
            int thumbTravel,
            int maximumScroll
    ) {
    }

    // Store the embedded image
    private record EmbeddedImage(String mimeType, String base64) {
    }

    // Handle the image reference size exception
    private static final class ImageReferenceSizeException extends RuntimeException {
    }

    // Store the mini browser
    private record MiniBrowser(int x, int y, List<AdvancedGraphCatalog.Definition> definitions,
                               String wireNode, String wirePort, boolean wireOutput) {
    }
}
