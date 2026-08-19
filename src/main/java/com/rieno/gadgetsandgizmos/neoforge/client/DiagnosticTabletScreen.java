package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletApps;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletData;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletItem;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAppRegistry;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletTabDefinition;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletInteractionMode;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientContext;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRegistry;
import com.rieno.gadgetsandgizmos.lib.client.tablet.TabletAppClientRenderer;
import com.rieno.gadgetsandgizmos.neoforge.network.DiagnosticTabletActionPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// Run the tablet desktop and keep every app inside its own saved workspace
public final class DiagnosticTabletScreen extends AbstractContainerScreen<DiagnosticTabletScreen.TabletOverlayMenu> {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int PANEL_WIDTH = 492;
    private static final int PANEL_HEIGHT = 286;
    private static final int CONTENT_MARGIN = 18;
    private static final int HOME_ICON_SIZE = 42;
    private static final int HOME_CELL_WIDTH = 82;
    private static final int HOME_CELL_HEIGHT = 64;
    private static final int REDSTONE_MODAL_FIRST_SLOT_X = 42;
    private static final int REDSTONE_MODAL_SECOND_SLOT_X = 42;
    private static final int REDSTONE_MODAL_FIRST_SLOT_Y = 116;
    private static final int REDSTONE_MODAL_SECOND_SLOT_Y = 150;
    private static final int REDSTONE_MODAL_INVENTORY_X = 246;
    private static final int REDSTONE_MODAL_INVENTORY_Y = 116;
    private static final int REDSTONE_MODAL_INVENTORY_SLOT_SIZE = 18;
    private static final int[] REDSTONE_CONTROL_COLORS = {
            0xF44336, 0xFF9800, 0xFFEB3B, 0x4CAF50,
            0x03A9F4, 0x3F51B5, 0x9C27B0, 0xE91E63
    };
    private static final int SETTINGS_APP_PAGE_SIZE = 5;
    private static final Set<String> READER_ACTIONS = Set.of(
            "landing_zone", "map_target", "test_target", "assign_items", "assign_fluids",
            "assign_energy", "assign_fuel", "configure_network", "configure_fuel",
            "configure_run", "add_waypoint", "bind_channel", "nfc_scan");
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Tracks whether placed is set
    private final boolean placed;
    // Tracks whether projected is set
    private final boolean projected;
    // Hand
    private final InteractionHand hand;
    // Tablet pos
    private final BlockPos tabletPos;
    // Tablet sub-level id
    private final UUID tabletSubLevelId;
    // Source tablet id
    private UUID sourceTabletId;
    // Current diagnostic tablet state
    private DiagnosticTabletData.State state;
    // Current action input
    private EditBox actionInput;
    // Current secondary input
    private EditBox secondaryInput;
    // Current editing action
    private String editingAction = "";
    // Current editing prompt
    private String editingPrompt = "";
    // Current editing value prefix
    private String editingValuePrefix = "";
    // Snapshot refresh tick count
    private int snapshotRefreshTicks;
    // Current home page
    private int homePage;
    // Current redstone page
    private int redstonePage;
    // Current App Store page
    private int appStorePage;
    // Current redstone editing id
    private String redstoneEditingId = "";
    // Redstone first index
    private int redstoneFirstIndex;
    // Redstone second index
    private int redstoneSecondIndex;
    // Current redstone first ghost
    private ItemStack redstoneFirstGhost = ItemStack.EMPTY;
    // Current redstone second ghost
    private ItemStack redstoneSecondGhost = ItemStack.EMPTY;
    // Tracks whether redstone frequency modal is set
    private boolean redstoneFrequencyModal;
    // Current redstone frequency target
    private int redstoneFrequencyTarget;
    // Current redstone mode
    private String redstoneMode = "button";
    // Redstone strength
    private int redstoneStrength = 15;
    // Journey start
    private String journeyFrom = "";
    // Journey destination
    private String journeyTo = "";
    // Current journey picker
    private String journeyPicker = "";
    // Current journey picker scroll
    private int journeyPickerScroll;
    // Current settings app page
    private int settingsAppPage;
    // Tracks whether SCM stock modal is set
    private boolean scmStockModal;
    // Tracks whether SCM run type picker is set
    private boolean scmRunTypePicker;
    // Current SCM run scroll
    private int scmRunScroll;
    // Current nfc property scroll
    private int nfcPropertyScroll;
    // Current observed registry revision
    private long observedRegistryRevision = -1L;
    // Current observed selected definition
    private TabletAppDefinition observedSelectedDefinition;
    // Current left
    private int left;
    // Current top
    private int top;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the diagnostic tablet
    private DiagnosticTabletScreen(boolean placed, boolean projected, InteractionHand hand,
                                   BlockPos tabletPos, UUID tabletSubLevelId,
                                   DiagnosticTabletData.State state) {
        super(new TabletOverlayMenu(), clientInventory(),
                Component.translatable("screen.createthrusters.diagnostic_tablet"));
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        this.placed = placed;
        this.projected = projected;
        this.hand = hand;
        this.tabletPos = tabletPos;
        this.tabletSubLevelId = tabletSubLevelId;
        DiagnosticTabletData.State resolved = state == null ? DiagnosticTabletData.State.DEFAULT : state;
        this.sourceTabletId = resolved.tabletId() == null ? UUID.randomUUID() : resolved.tabletId();
        this.state = resolved.tabletId() == null ? resolved.withTabletId(sourceTabletId) : resolved;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the client inventory
    private static Inventory clientInventory() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("A player is required to open the Smart Tablet");
        }
        return player.getInventory();
    }

    // Open the item
    public static void openItem(InteractionHand hand, ItemStack stack) {
        DiagnosticTabletApps.register();
        DiagnosticTabletData.ensureTabletId(stack);
        Minecraft.getInstance().setScreen(new DiagnosticTabletScreen(false, false, hand,
                BlockPos.ZERO, null, DiagnosticTabletData.read(stack)));
    }

    // Open the block
    public static void openBlock(DiagnosticTabletBlockEntity tablet) {
        DiagnosticTabletApps.register();
        Minecraft.getInstance().setScreen(new DiagnosticTabletScreen(true, false,
                InteractionHand.MAIN_HAND, tablet.getBlockPos(),
                SimulatedHelper.getContainingSubLevelId(tablet), tablet.state()));
    }

    // Get the projection width
    static int projectionWidth() {
        return PANEL_WIDTH;
    }

    // Get the projection height
    static int projectionHeight() {
        return PANEL_HEIGHT;
    }

    // Get the projection item
    static DiagnosticTabletScreen projectionItem(InteractionHand hand, ItemStack stack) {
        DiagnosticTabletApps.register();
        DiagnosticTabletData.ensureTabletId(stack);
        return new DiagnosticTabletScreen(false, true, hand, BlockPos.ZERO, null,
                DiagnosticTabletData.read(stack));
    }

    // Get the projection block
    static DiagnosticTabletScreen projectionBlock(DiagnosticTabletBlockEntity tablet) {
        DiagnosticTabletApps.register();
        return new DiagnosticTabletScreen(true, true, InteractionHand.MAIN_HAND,
                tablet.getBlockPos(), SimulatedHelper.getContainingSubLevelId(tablet), tablet.state());
    }

    // Initialize the diagnostic tablet
    @Override
    protected void init() {
        super.init();
        DiagnosticTabletApps.register();
        left = leftPos;
        top = topPos;
        if (!isHome() && selectedApp() == null) {
            state = state.withApp(DiagnosticTabletData.appId("home"), "");
        }
        actionInput = new EditBox(font, left + CONTENT_MARGIN + 10,
                top + PANEL_HEIGHT - 66, PANEL_WIDTH - CONTENT_MARGIN * 2 - 104, 18,
                Component.literal("Tablet action value"));
        actionInput.setMaxLength(192);
        actionInput.setVisible(!editingAction.isBlank());
        if (!editingAction.isBlank()) {
            actionInput.setHint(Component.literal(placeholder(editingAction)));
        }
        addRenderableWidget(actionInput);
        secondaryInput = new EditBox(font, left + CONTENT_MARGIN + 10,
                top + PANEL_HEIGHT - 43, PANEL_WIDTH - CONTENT_MARGIN * 2 - 104, 18,
                Component.literal("Secondary tablet value"));
        secondaryInput.setMaxLength(192);
        secondaryInput.setVisible(false);
        addRenderableWidget(secondaryInput);
        if (!editingAction.isBlank()) setInitialFocus(actionInput);
        reqActiveAppSnapshot();
    }

    // Update the container
    @Override
    protected void containerTick() {
        super.containerTick();
        reconcileSourceTablet();
        if (!isHome() && ++snapshotRefreshTicks >= 20) {
            snapshotRefreshTicks = 0;
            reqActiveAppSnapshot();
        }
    }

    // Draw the background
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    // Draw the bg
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    }

    // Draw the labels
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the diagnostic tablet
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        reconcileRuntimeApps();
        graphics.fillGradient(0, 0, width, height, 0xE0050910, 0xE00D1420);
        AdvancedControllerV2Theme.drawRoundedRect(graphics, left, top, PANEL_WIDTH, PANEL_HEIGHT,
                13, AdvancedControllerV2Theme.BORDER_STRONG);
        CompoundTag tabletSettings = settingsData();
        int[] wallpaper = wallpaperColors(tabletSettings.getString("Wallpaper"));
        graphics.fillGradient(left + 3, top + 3, left + PANEL_WIDTH - 3, top + PANEL_HEIGHT - 3,
                wallpaper[0], wallpaper[1]);
        renderStatusBar(graphics);
        if (isHome()) renderHome(graphics, mouseX, mouseY);
        else renderApp(graphics, mouseX, mouseY);
        renderNavigationBar(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRedstoneInventoryTooltip(graphics, mouseX, mouseY);
    }

    // Draw the status bar
    private void renderStatusBar(GuiGraphics graphics) {
        String clock = minecraftTime();
        int statusLeft = left + 15 + font.width(clock) + 15;
        String worldStatus = worldStatus();
        graphics.drawString(font, clock, left + 15, top + 13,
                0xFFF3F7FC, false);
        graphics.drawString(font, worldStatus, statusLeft, top + 13, 0xFFB7C7DA, false);
        String mode = state.mode().name();
        int modeWidth = font.width(mode) + 12;
        int col = state.mode() == TabletInteractionMode.STANDARD
                ? 0xFFB7C7DA
                : state.mode() == TabletInteractionMode.READER ? 0xFF4FC3C8 : 0xFFFFB74D;
        AdvancedControllerV2Theme.drawRoundedRect(graphics,
                left + PANEL_WIDTH - modeWidth - 54, top + 10, modeWidth, 14, 7, col);
        graphics.drawCenteredString(font, mode, left + PANEL_WIDTH - 54 - modeWidth / 2,
                top + 13, 0xFF071017);
        int notifications = notificationCount();
        if (notifications > 0) {
            String label = "! " + notifications;
            int notificationWidth = font.width(label) + 10;
            int notificationLeft = left + PANEL_WIDTH - modeWidth - notificationWidth - 61;
            AdvancedControllerV2Theme.drawRoundedRect(graphics, notificationLeft, top + 10,
                    notificationWidth, 14, 7, 0xFFE3A64B);
            graphics.drawCenteredString(font, label, notificationLeft + notificationWidth / 2,
                    top + 13, 0xFF071017);
        }
        int promptLeft = statusLeft + font.width(worldStatus) + 14;
        int promptRight = left + PANEL_WIDTH - modeWidth - (notifications > 0 ? 74 : 61);
        if (promptRight > promptLeft + 30) {
            graphics.drawString(font, font.plainSubstrByWidth("Press Alt + Use to switch modes.",
                    promptRight - promptLeft), promptLeft, top + 13, 0xFFB7C7DA, false);
        }
    }

    // Draw the home
    private void renderHome(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentLeft = contentLeft();
        graphics.pose().pushPose();
        graphics.pose().translate(contentLeft, top + 40, 0.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawString(font, minecraftTime(), 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popPose();
        graphics.drawString(font, worldStatus(), contentLeft, top + 62,
                0xFFD2DEEC, false);
        CompoundTag settings = settingsData();
        String tabletName = settings.getString("Name");
        if (!tabletName.isBlank()) {
            graphics.drawString(font, tabletName, contentLeft + 122, top + 48,
                    0xFFE8F1FA, false);
        }
        List<TabletAppDefinition> apps = installedApps();
        int pageCount = Math.max(1, (apps.size() + 9) / 10);
        homePage = Math.max(0, Math.min(homePage, pageCount - 1));
        if (pageCount > 1) {
            graphics.drawString(font, "‹", left + PANEL_WIDTH - 91, top + 60,
                    0xFFDCE7F4, false);
            graphics.drawCenteredString(font, (homePage + 1) + " / " + pageCount,
                    left + PANEL_WIDTH - 60, top + 61, 0xFFB8C8DA);
            graphics.drawString(font, "›", left + PANEL_WIDTH - 30, top + 60,
                    0xFFDCE7F4, false);
        }
        if (apps.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal("No tablet apps are registered."),
                    contentLeft, top + 96, contentWidth(),
                    AdvancedControllerV2Theme.DANGER);
        }
        int firstApp = homePage * 10;
        for (int visible = 0; visible < 10 && firstApp + visible < apps.size(); visible++) {
            TabletAppDefinition app = apps.get(firstApp + visible);
            int x = contentLeft + 20 + visible % 5 * HOME_CELL_WIDTH;
            int y = top + 91 + visible / 5 * HOME_CELL_HEIGHT;
            boolean hovered = inside(mouseX, mouseY, x - 7, y - 6,
                    HOME_ICON_SIZE + 14, HOME_ICON_SIZE + 24);
            if (hovered) {
                AdvancedControllerV2Theme.drawRoundedRect(graphics, x - 7, y - 6,
                        HOME_ICON_SIZE + 14, HOME_ICON_SIZE + 24, 10, 0x553D5A78);
            }
            drawAppIcon(graphics, app, x, y, HOME_ICON_SIZE);
            int notifications = notificationCount(app.id());
            if (notifications > 0) {
                String label = Integer.toString(Math.min(99, notifications));
                int badgeWidth = Math.max(13, font.width(label) + 6);
                AdvancedControllerV2Theme.drawRoundedRect(graphics,
                        x + HOME_ICON_SIZE - badgeWidth + 3, y - 4, badgeWidth, 12, 6,
                        0xFFE3A64B);
                graphics.drawCenteredString(font, label,
                        x + HOME_ICON_SIZE - badgeWidth / 2 + 3, y - 2, 0xFF071017);
            }
            String title = font.plainSubstrByWidth(app.title().getString(), HOME_CELL_WIDTH - 8);
            graphics.drawCenteredString(font, title, x + HOME_ICON_SIZE / 2, y + HOME_ICON_SIZE + 5,
                    0xFFF5F8FC);
        }
    }

    // Draw the app
    private void renderApp(GuiGraphics graphics, int mouseX, int mouseY) {
        // -----------------------------------------------------APP HEADER-----------------------------------------------------
        TabletAppDefinition app = selectedApp();
        if (app == null) return;
        int contentLeft = contentLeft();
        int contentWidth = contentWidth();
        drawAppIcon(graphics, app, contentLeft, top + 34, 20);
        graphics.drawString(font, app.title(), contentLeft + 27, top + 40,
                AdvancedControllerV2Theme.PRIMARY, false);
        int notifications = notificationCount(app.id());
        if (notifications > 0) {
            String label = notifications + " notification" + (notifications == 1 ? "" : "s");
            graphics.drawString(font, label, contentLeft + 27, top + 52,
                    0xFFE3A64B, false);
        }
        if (usesReaderMode(app)) {
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 92,
                    top + 34, 92, 18, state.mode() == TabletInteractionMode.READER
                            ? "Reader active" : "Reader mode", app.accentColor());
        }

        renderTabs(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
        // -----------------------------------------------------BUILT IN APPS-----------------------------------------------------
        if (isBuiltInApp(app, "rdp")) {
            renderRdpNativeApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            if (!editingAction.isBlank()) renderInlineEditor(graphics, mouseX, mouseY, contentLeft, contentWidth);
            return;
        }
        if (isBuiltInApp(app, "journey")) {
            renderJourneyNativeApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            if (!editingAction.isBlank()) renderInlineEditor(graphics, mouseX, mouseY, contentLeft, contentWidth);
            return;
        }
        if (isBuiltInApp(app, "block360")) {
            renderBlock360NativeApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            if (!editingAction.isBlank()) {
                renderInlineEditor(graphics, mouseX, mouseY, contentLeft, contentWidth);
            }
            return;
        }
        if (isBuiltInApp(app, "redstone_link")) {
            renderRedstoneNativeApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            if (!editingAction.isBlank() && !"redstone_editor".equals(editingAction)) {
                renderInlineEditor(graphics, mouseX, mouseY, contentLeft, contentWidth);
            }
            return;
        }
        if (isBuiltInApp(app, "scm")) {
            renderScmApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            if (!editingAction.isBlank()) renderInlineEditor(graphics, mouseX, mouseY, contentLeft, contentWidth);
            return;
        }
        if (isBuiltInApp(app, "app_store")) {
            renderAppStoreNativeApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            return;
        }
        if (isBuiltInApp(app, "settings")) {
            renderSettingsApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            if (!editingAction.isBlank()) renderInlineEditor(graphics, mouseX, mouseY, contentLeft, contentWidth);
            return;
        }
        if (isBuiltInApp(app, "gg_auto")) {
            renderAutoApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            if (!editingAction.isBlank()) renderInlineEditor(graphics, mouseX, mouseY, contentLeft, contentWidth);
            return;
        }
        if (isBuiltInApp(app, "nfc")) {
            renderNfcApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            return;
        }
        TabletAppClientRenderer renderer = TabletAppClientRegistry.renderer(app.id());
        if (renderer != null) {
            renderer.render(clientAppContext(app, graphics, mouseX, mouseY));
            return;
        }
        TabletTabDefinition tab = activeTab(app);
        List<String> visibleActions = visibleActions(tab);
        int actionTop = top + 88;
        int actionColumns = visibleActions.size() > 8 ? 3 : 2;
        int actionGap = 5;
        int actionWidth = (contentWidth - actionGap * (actionColumns - 1)) / actionColumns;
        int actionIndex = 0;
        for (String action : visibleActions) {
            int x = contentLeft + actionIndex % actionColumns * (actionWidth + actionGap);
            int y = actionTop + actionIndex / actionColumns * 29;
            if (y > top + PANEL_HEIGHT - 91) break;
            drawActionButton(graphics, mouseX, mouseY, x, y, actionWidth,
                    label(action), app.accentColor());
            actionIndex++;
        }

        int footerY = top + PANEL_HEIGHT - 88;
        if (surfaceProjection() && visibleActions.size() != tab.actions().size()) {
            graphics.drawString(font, "Sneak-use the tablet to edit text actions.",
                    contentLeft, footerY, AdvancedControllerV2Theme.MUTED, false);
        } else if (tab.actions().stream().anyMatch(this::readerAction)) {
            graphics.drawString(font, "Reader actions close the tablet; use it on the target block.",
                    contentLeft, footerY, AdvancedControllerV2Theme.MUTED, false);
        } else {
            graphics.drawString(font, app.description(), contentLeft, footerY,
                    AdvancedControllerV2Theme.MUTED, false);
        }
        if (!placed && (isBuiltInApp(app, "scm") || isBuiltInApp(app, "rdp"))
                && !state.selections().isEmpty()) {
            drawSmallButton(graphics, mouseX, mouseY, contentLeft, footerY + 15,
                    144, 20, "Push configured blocks", 0xFFFFB74D);
        }
        if (!editingAction.isBlank()) renderInlineEditor(graphics, mouseX, mouseY, contentLeft, contentWidth);
    }

    // Draw the rdp native app
    private void renderRdpNativeApp(GuiGraphics graphics, int mouseX, int mouseY,
                                    TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        ListTag devices = data.getList("Devices", Tag.TAG_COMPOUND);
        String tab = activeTab(app).id();
        if ("settings".equals(tab)) {
            renderPerAppSettings(graphics, mouseX, mouseY, app, contentLeft, contentWidth,
                    "Session quality", "Confirm remote input");
            return;
        }
        AdvancedControllerV2Theme.drawRoundedRect(graphics, contentLeft, top + 69,
                contentWidth, 32, 15, 0xCC082B3A);
        graphics.drawString(font, "Your remote computers", contentLeft + 13, top + 78,
                0xFFF5FBFF, false);
        graphics.drawString(font, devices.size() + " paired", contentLeft + 13, top + 89,
                0xFF83DDF0, false);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 78,
                top + 76, 66, 19, "Refresh", app.accentColor());
        if (devices.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal(
                            "No controllers paired. Switch this app to Reader mode, then interact with an Advanced Controller."),
                    contentLeft + 16, top + 120, contentWidth - 32, 0xFFB6C8D8);
            return;
        }
        for (int idx = 0; idx < Math.min(3, devices.size()); idx++) {
            CompoundTag device = devices.getCompound(idx);
            int y = top + 108 + idx * 42;
            drawNativeCard(graphics, contentLeft, y, contentWidth, 37,
                    0xFF35505E,
                    inside(mouseX, mouseY, contentLeft, y, contentWidth, 37));
            AdvancedControllerV2Theme.drawRoundedRect(graphics, contentLeft + 9, y + 7,
                    23, 23, 6, device.getBoolean("Online") ? 0xFF154C58 : 0xFF343A43);
            boolean computer = "computer".equals(device.getString("Type"));
            graphics.drawCenteredString(font, computer ? "CC" : "AC", contentLeft + 20, y + 15,
                    device.getBoolean("Online") ? 0xFF6DE1F0 : 0xFF8995A2);
            graphics.drawString(font, font.plainSubstrByWidth(device.getString("Name"), 122),
                    contentLeft + 39, y + 7, 0xFFF2F7FB, false);
            graphics.drawString(font, device.getBoolean("Online") ? "Ready to connect" : "Offline",
                    contentLeft + 39, y + 20,
                    device.getBoolean("Online") ? 0xFF67D7B2 : 0xFF8998A8, false);
            int right = contentLeft + contentWidth;
            if (device.getBoolean("Online")) {
                int controlX = computer ? right - 132 : right - 230;
                drawSmallButton(graphics, mouseX, mouseY, controlX, y + 8,
                        56, 21, "Control", app.accentColor());
                if (!computer) {
                    drawSmallButton(graphics, mouseX, mouseY, right - 170, y + 8,
                            48, 21, "Graph", 0xFF536DFE);
                    drawSmallButton(graphics, mouseX, mouseY, right - 118, y + 8,
                            42, 21, "Plot", 0xFF7E57C2);
                }
            }
            drawSmallButton(graphics, mouseX, mouseY, right - 72, y + 8,
                    34, 21, "Edit", 0xFF536DFE);
            drawSmallButton(graphics, mouseX, mouseY, right - 34, y + 8,
                    24, 21, "X", 0xFFE57373);
        }
    }

    // Draw the SCM ships
    private void renderScmShips(GuiGraphics graphics, int mouseX, int mouseY,
                                TabletAppDefinition app, int contentLeft, int contentWidth,
                                ListTag targets, CompoundTag selected) {
        graphics.drawString(font, "Bound ships and controllers", contentLeft, top + 70,
                0xFFF2F5F8, false);
        int listWidth = 176;
        for (int idx = 0; idx < Math.min(5, targets.size()); idx++) {
            CompoundTag row = targets.getCompound(idx);
            int y = top + 86 + idx * 29;
            drawNativeCard(graphics, contentLeft, y, listWidth, 25,
                    row.getBoolean("Selected") ? app.accentColor() : 0xFF455465,
                    inside(mouseX, mouseY, contentLeft, y, listWidth, 25));
            graphics.drawString(font, font.plainSubstrByWidth(row.getString("Name"), 130),
                    contentLeft + 9, y + 8, 0xFFF3F6F9, false);
        }
        int panelX = contentLeft + 187;
        if (selected == null) {
            graphics.drawWordWrap(font, Component.literal(
                            "Select a bound ship or use Reader mode on its controller."),
                    panelX, top + 91, contentWidth - 187, 0xFFAFBBC8);
            return;
        }
        graphics.drawString(font, selected.getString("Name"), panelX, top + 86,
                0xFFFFFFFF, false);
        String[] controls = {"Manual control", "Navigate", "Follow", "Climb to Y",
                "Hover", "Initialize", "Dock"};
        for (int idx = 0; idx < controls.length; idx++) {
            int width = 124;
            int x = panelX + idx % 2 * (width + 6);
            int y = top + 105 + idx / 2 * 29;
            drawSmallButton(graphics, mouseX, mouseY, x, y, width, 23,
                    controls[idx], idx == 0 ? 0xFF1565C0 : app.accentColor());
        }
    }

    // Draw the journey native app
    private void renderJourneyNativeApp(GuiGraphics graphics, int mouseX, int mouseY,
                                        TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        String tab = activeTab(app).id();
        if ("settings".equals(tab)) {
            renderPerAppSettings(graphics, mouseX, mouseY, app, contentLeft, contentWidth,
                    "Live notifications", "Show ship journeys");
            return;
        }
        if ("saved".equals(tab)) {
            graphics.drawString(font, "Saved journeys", contentLeft, top + 73, 0xFFFFFFFF, false);
            ListTag saved = data.getList("Saved", Tag.TAG_COMPOUND);
            if (saved.isEmpty()) {
                graphics.drawWordWrap(font, Component.literal(
                                "Saved routes appear here for one-tap live updates."),
                        contentLeft, top + 96, contentWidth, 0xFFB7C3CE);
            }
            for (int idx = 0; idx < Math.min(5, saved.size()); idx++) {
                CompoundTag row = saved.getCompound(idx);
                int y = top + 91 + idx * 29;
                drawNativeCard(graphics, contentLeft, y, contentWidth, 25,
                        app.accentColor(), inside(mouseX, mouseY, contentLeft, y, contentWidth, 25));
                graphics.drawString(font, font.plainSubstrByWidth(row.getString("Route"), contentWidth - 55),
                        contentLeft + 10, y + 8, 0xFFF2F7F7, false);
                graphics.drawString(font, "×", contentLeft + contentWidth - 18, y + 8,
                        0xFFFF8A80, false);
            }
            return;
        }
        if ("search".equals(tab)) {
            AdvancedControllerV2Theme.drawRoundedRect(graphics, contentLeft, top + 68,
                    contentWidth, 66, 13, 0xFF241078);
            String from = data.getString("From");
            String to = data.getString("To");
            if (journeyFrom.isBlank()) journeyFrom = from;
            if (journeyTo.isBlank()) journeyTo = to;
            drawJourneyField(graphics, contentLeft + 10, top + 76, 180, "FROM",
                    journeyFrom.isBlank() ? "Choose origin" : journeyFrom);
            drawJourneyField(graphics, contentLeft + 200, top + 76, 180, "TO",
                    journeyTo.isBlank() ? "Choose destination" : journeyTo);
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + 390, top + 84,
                    56, 34, "Search", 0xFF00C7A5);
            if (!journeyFrom.isBlank() && !journeyTo.isBlank()) {
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + 390, top + 119,
                        56, 18, "Save", 0xFF6650A4);
            }
            if (!journeyPicker.isBlank()) {
                renderJourneyPicker(graphics, mouseX, mouseY, data, contentLeft);
                return;
            }
        }
        ListTag itinerary = data.getList("Itinerary", Tag.TAG_COMPOUND);
        ListTag departures = "search".equals(tab) && !itinerary.isEmpty()
                ? itinerary : data.getList("Departures", Tag.TAG_COMPOUND);
        int listY = "search".equals(tab) ? top + 141 : top + 72;
        graphics.drawString(font, "Live times", contentLeft, listY,
                0xFFF2F6FA, false);
        listY += 13;
        if (departures.isEmpty()) {
            graphics.drawString(font, "No matching journeys are reporting right now",
                    contentLeft, listY + 8, 0xFFABB9C7, false);
            return;
        }
        int rows = "search".equals(tab) ? 3 : 5;
        for (int idx = 0; idx < Math.min(rows, departures.size()); idx++) {
            CompoundTag row = departures.getCompound(idx);
            int y = listY + idx * 27;
            graphics.fill(contentLeft, y, contentLeft + contentWidth, y + 24, 0xEDF7F8FA);
            graphics.drawString(font, formatEta(row.getLong("Eta")), contentLeft + 9, y + 5,
                    0xFF151A21, false);
            String route = row.getString("CurrentStop") + "  →  " + row.getString("Destination");
            graphics.drawString(font, font.plainSubstrByWidth(route, 235), contentLeft + 76, y + 5,
                    0xFF20252C, false);
            graphics.drawString(font, row.getString("Status"), contentLeft + 76, y + 15,
                    0xFF5F6973, false);
            graphics.drawString(font, "›", contentLeft + contentWidth - 15, y + 8,
                    0xFF00A88F, false);
        }
    }

    // Draw the journey picker
    private void renderJourneyPicker(GuiGraphics graphics, int mouseX, int mouseY,
                                     CompoundTag data, int contentLeft) {
        int x = "from".equals(journeyPicker) ? contentLeft + 10 : contentLeft + 200;
        AdvancedControllerV2Theme.drawRoundedRect(graphics, x, top + 121, 180, 106,
                8, 0xFFFAFAFC);
        String filter = actionInput == null ? "" : actionInput.getValue().strip().toLowerCase(Locale.ROOT);
        List<String> choices = new ArrayList<>();
        appendJourneyChoices(choices, data.getList("Docks", Tag.TAG_COMPOUND), filter);
        appendJourneyChoices(choices, data.getList("TrainStations", Tag.TAG_COMPOUND), filter);
        journeyPickerScroll = Mth.clamp(journeyPickerScroll, 0, Math.max(0, choices.size() - 4));
        for (int visible = 0; visible < Math.min(4, choices.size()); visible++) {
            int idx = journeyPickerScroll + visible;
            int y = top + 151 + visible * 18;
            boolean hovered = inside(mouseX, mouseY, x + 4, y - 3, 172, 17);
            if (hovered) graphics.fill(x + 4, y - 3, x + 176, y + 14, 0xFFE4F2EF);
            graphics.drawString(font, font.plainSubstrByWidth(choices.get(idx), 162),
                    x + 9, y, 0xFF182027, false);
        }
        if (choices.size() > 4) {
            int trackY = top + 149;
            int thumbHeight = Math.max(12, 68 * 4 / choices.size());
            int thumbY = trackY + (68 - thumbHeight) * journeyPickerScroll
                    / Math.max(1, choices.size() - 4);
            graphics.fill(x + 174, trackY, x + 177, trackY + 68, 0xFFD7DEE2);
            graphics.fill(x + 174, thumbY, x + 177, thumbY + thumbHeight, 0xFF6D7D86);
        }
    }

    // Add the journey choices
    private static void appendJourneyChoices(List<String> choices, ListTag rows, String filter) {
        for (int idx = 0; idx < rows.size(); idx++) {
            String name = rows.getCompound(idx).getString("Name");
            if (!name.isBlank() && (filter.isBlank()
                    || name.toLowerCase(Locale.ROOT).contains(filter))) choices.add(name);
        }
    }

    // Draw the journey field
    private void drawJourneyField(GuiGraphics graphics, int x, int y, int width,
                                  String caption, String val) {
        AdvancedControllerV2Theme.drawRoundedRect(graphics, x, y, width, 42, 8, 0xFF3D218C);
        graphics.drawString(font, caption, x + 9, y + 7, 0xFFB9A9E6, false);
        graphics.drawString(font, font.plainSubstrByWidth(val, width - 18), x + 9, y + 22,
                0xFFFFFFFF, false);
    }

    // Draw the block360 native app
    private void renderBlock360NativeApp(GuiGraphics graphics, int mouseX, int mouseY,
                                         TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        String tab = activeTab(app).id();
        if ("settings".equals(tab) || "places".equals(tab)) {
            renderPerAppSettings(graphics, mouseX, mouseY, app, contentLeft, contentWidth,
                    "Location sharing", "Arrival alerts");
            return;
        }
        ListTag friends = data.getList("Friends", Tag.TAG_COMPOUND);
        ListTag pending = data.getList("Pending", Tag.TAG_COMPOUND);
        AdvancedControllerV2Theme.drawRoundedRect(graphics, contentLeft, top + 68,
                286, 166, 14, 0xFF172B2C);
        graphics.fillGradient(contentLeft + 5, top + 73, contentLeft + 281, top + 229,
                0xFF254A42, 0xFF182E3D);
        for (int line = 0; line < 5; line++) {
            int y = top + 84 + line * 27;
            graphics.fill(contentLeft + 9, y, contentLeft + 277, y + 1, 0x334ED4A8);
        }
        for (int idx = 0; idx < Math.min(6, friends.size()); idx++) {
            CompoundTag friend = friends.getCompound(idx);
            int x = contentLeft + 30 + idx % 3 * 86;
            int y = top + 92 + idx / 3 * 70;
            int col = friend.getBoolean("Online") ? app.accentColor() : 0xFF697780;
            AdvancedControllerV2Theme.drawRoundedRect(graphics, x, y, 34, 34, 17, col);
            String initial = friend.getString("Name").isBlank() ? "?"
                    : friend.getString("Name").substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawCenteredString(font, initial, x + 17, y + 13, 0xFF10201B);
            graphics.drawCenteredString(font, font.plainSubstrByWidth(friend.getString("Name"), 66),
                    x + 17, y + 39, 0xFFF4FAF7);
            if (friend.getBoolean("Online")) {
                drawSmallButton(graphics, mouseX, mouseY, x - 10, y + 50,
                        54, 16, "Navigate", 0xFF2E7D32);
            }
        }
        int panelX = contentLeft + 296;
        graphics.drawString(font, "Your circle", panelX, top + 72, 0xFFF4F8F6, false);
        graphics.drawString(font, friends.size() + " members", panelX, top + 84,
                0xFF9FB6AE, false);
        if (!surfaceProjection()) {
            drawSmallButton(graphics, mouseX, mouseY, panelX, top + 99,
                    150, 22, "Add a person", app.accentColor());
        }
        if (!pending.isEmpty()) {
            CompoundTag req = pending.getCompound(0);
            drawNativeCard(graphics, panelX, top + 128, 150, 44,
                    app.accentColor(), inside(mouseX, mouseY, panelX, top + 128, 150, 44));
            graphics.drawString(font, font.plainSubstrByWidth(req.getString("Name"), 88),
                    panelX + 8, top + 136, 0xFFF5FAF8, false);
            graphics.drawString(font, "wants to join", panelX + 8, top + 148, 0xFFAFC0BA, false);
            drawSmallButton(graphics, mouseX, mouseY, panelX + 94, top + 139,
                    49, 20, "Accept", app.accentColor());
        }
        drawSmallButton(graphics, mouseX, mouseY, panelX, top + 181,
                72, 20, "Refresh", 0xFF45635A);
    }

    // Draw the redstone native app
    private void renderRedstoneNativeApp(GuiGraphics graphics, int mouseX, int mouseY,
                                         TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        String tab = activeTab(app).id();
        if ("settings".equals(tab)) {
            renderPerAppSettings(graphics, mouseX, mouseY, app, contentLeft, contentWidth,
                    "Haptic feedback", "Confirm destructive actions");
            return;
        }
        if (!editingAction.isBlank() && "redstone_editor".equals(editingAction)) {
            renderRedstoneEditor(graphics, mouseX, mouseY, app, contentLeft, contentWidth, data);
            return;
        }
        ListTag channels = data.getList("Channels", Tag.TAG_COMPOUND);
        graphics.drawString(font, "My home", contentLeft, top + 72, 0xFFF7F4FF, false);
        graphics.drawString(font, channels.size() + " wireless devices", contentLeft, top + 84,
                0xFFB6AACD, false);
        if ("devices".equals(tab) && !surfaceProjection()) {
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 145,
                    top + 70, 92, 23, "New control", app.accentColor());
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 48,
                    top + 70, 48, 23, "Scan", 0xFF546E7A);
        }
        int first = redstonePage * 4;
        int pageCount = Math.max(1, (channels.size() + 3) / 4);
        redstonePage = Math.max(0, Math.min(redstonePage, pageCount - 1));
        for (int visible = 0; visible < 4 && first + visible < channels.size(); visible++) {
            CompoundTag channel = channels.getCompound(first + visible);
            int cardWidth = (contentWidth - 8) / 2;
            int x = contentLeft + visible % 2 * (cardWidth + 8);
            int y = top + 101 + visible / 2 * 64;
            String mode = channel.getString("Mode");
            boolean active = channel.getInt("Output") > 0;
            int accent = active ? app.accentColor() : 0xFF405064;
            drawNativeCard(graphics, x, y, cardWidth, 57, accent,
                    inside(mouseX, mouseY, x, y, cardWidth, 57));
            AdvancedControllerV2Theme.drawRoundedRect(graphics, x + 9, y + 8,
                    25, 25, 12, active ? app.accentColor() : 0xFF2B3646);
            graphics.drawCenteredString(font, "R", x + 21, y + 17,
                    active ? 0xFFFFFFFF : 0xFFAAB5C2);
            graphics.drawString(font, font.plainSubstrByWidth(channel.getString("Label"), cardWidth - 86),
                    x + 42, y + 8, 0xFFF4F7FC, false);
            graphics.drawString(font, label(mode), x + 42, y + 20,
                    0xFF9EACC0, false);
            String actionLabel = "button".equals(mode) ? "PRESS"
                    : "toggle".equals(mode) ? (channel.getBoolean("Active") ? "ON" : "OFF")
                    : Integer.toString(channel.getInt("Strength"));
            drawSmallButton(graphics, mouseX, mouseY, x + cardWidth - 58, y + 8,
                    49, 21, actionLabel, accent);
            int strength = channel.getInt("Strength");
            AdvancedControllerV2Theme.drawSlider(graphics, x + 12, y + 45,
                    cardWidth - 24, strength / 15.0D, app.accentColor(), true);
            if ("devices".equals(tab) && !surfaceProjection()) {
                graphics.drawString(font, "Edit", x + cardWidth - 32, y + 37,
                        0xFFCABFFF, false);
            }
        }
        if (channels.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal(
                            "Create a control by choosing two frequency items, its behavior, and output strength."),
                    contentLeft + 16, top + 116, contentWidth - 32, 0xFFB7AEC9);
        }
        if (pageCount > 1) {
            graphics.drawCenteredString(font, (redstonePage + 1) + " / " + pageCount,
                    contentLeft + contentWidth / 2, top + 231, 0xFFC6BDD6);
        }
    }

    // Draw the redstone editor
    private void renderRedstoneEditor(GuiGraphics graphics, int mouseX, int mouseY,
                                      TabletAppDefinition app, int contentLeft, int contentWidth,
                                      CompoundTag data) {
        if (redstoneFrequencyModal) {
            renderRedstoneFreqModal(graphics, mouseX, mouseY, app, contentLeft, contentWidth);
            return;
        }
        graphics.drawString(font, redstoneEditingId.isBlank() ? "Create smart control" : "Edit smart control",
                contentLeft, top + 70, 0xFFF8F5FF, false);
        graphics.drawString(font, "Frequency pair", contentLeft, top + 88, 0xFFB9AECF, false);
        drawGhostFrequency(graphics, contentLeft, top + 99, redstoneFirstGhost, 0xFFE05252);
        drawGhostFrequency(graphics, contentLeft + 24, top + 99, redstoneSecondGhost, 0xFF4C8DFF);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 54, top + 98,
                132, 23, "Set frequency", app.accentColor());
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 194, top + 98,
                120, 23, "First colour", redstoneButtonColor(redstoneFirstGhost, 0xFFE05252));
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 322, top + 98,
                124, 23, "Second colour", redstoneButtonColor(redstoneSecondGhost, 0xFF4C8DFF));
        graphics.drawString(font, "Behavior", contentLeft, top + 139, 0xFFB9AECF, false);
        String[] modes = {"button", "toggle", "slider"};
        for (int idx = 0; idx < modes.length; idx++) {
            int x = contentLeft + idx * 100;
            drawSmallButton(graphics, mouseX, mouseY, x, top + 151, 92, 22,
                    label(modes[idx]), modes[idx].equals(redstoneMode)
                            ? app.accentColor() : 0xFF455064);
        }
        graphics.drawString(font, "Output strength  " + redstoneStrength, contentLeft, top + 184,
                0xFFF4F0FB, false);
        AdvancedControllerV2Theme.drawSlider(graphics, contentLeft, top + 204,
                300, redstoneStrength / 15.0D, app.accentColor(), true);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 320, top + 190,
                126, 25, "Save control", app.accentColor());
        graphics.drawString(font, "Name", contentLeft, top + 220, 0xFFB9AECF, false);
    }

    // Draw the redstone freq modal
    private void renderRedstoneFreqModal(GuiGraphics graphics, int mouseX, int mouseY,
                                              TabletAppDefinition app, int contentLeft, int contentWidth) {
        AdvancedControllerV2Theme.drawRoundedRect(graphics, contentLeft + 18, top + 68,
                contentWidth - 36, 166, 12, 0xFF141C27);
        graphics.drawString(font, "Set Redstone Link frequency", contentLeft + 32, top + 79,
                0xFFF7F9FC, false);
        graphics.drawString(font, "Click inventory items or drag from JEI or EMI",
                contentLeft + 32, top + 92, 0xFF9EACBA, false);
        drawGhostFrequency(graphics, contentLeft + REDSTONE_MODAL_FIRST_SLOT_X,
                top + REDSTONE_MODAL_FIRST_SLOT_Y,
                redstoneFirstGhost, 0xFFE05252, redstoneFrequencyTarget == 0);
        drawGhostFrequency(graphics, contentLeft + REDSTONE_MODAL_SECOND_SLOT_X,
                top + REDSTONE_MODAL_SECOND_SLOT_Y,
                redstoneSecondGhost, 0xFF4C8DFF, redstoneFrequencyTarget == 1);
        graphics.drawString(font, "RED", contentLeft + 70, top + 118, 0xFFE97979, false);
        graphics.drawString(font, "First frequency item", contentLeft + 70, top + 131,
                0xFFD6DEE8, false);
        graphics.drawString(font, "BLUE", contentLeft + 70, top + 152, 0xFF78A8FF, false);
        graphics.drawString(font, "Second frequency item", contentLeft + 70, top + 165,
                0xFFD6DEE8, false);
        graphics.drawString(font, "Right-click a frequency to clear it",
                contentLeft + 32, top + 187, 0xFF92A1B1, false);
        renderRedstonePlayerInventory(graphics, mouseX, mouseY, contentLeft);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 92,
                top + 204, 70, 21, "Done", app.accentColor());
    }

    // Draw the ghost frequency
    private void drawGhostFrequency(GuiGraphics graphics, int x, int y, ItemStack stack, int col) {
        drawGhostFrequency(graphics, x, y, stack, col, false);
    }

    // Draw the ghost frequency
    private void drawGhostFrequency(GuiGraphics graphics, int x, int y, ItemStack stack,
                                    int col, boolean selected) {
        if (selected) graphics.renderOutline(x - 2, y - 2, 24, 24, 0xFFF7F9FC);
        graphics.fill(x, y, x + 20, y + 20, 0xFF202936);
        graphics.fill(x + 1, y + 1, x + 19, y + 19, 0xFF151C26);
        graphics.renderOutline(x, y, 20, 20, col);
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 2, y + 2);
    }

    // Draw the redstone player inventory
    private void renderRedstonePlayerInventory(GuiGraphics graphics, int mouseX, int mouseY,
                                               int contentLeft) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        int x = contentLeft + REDSTONE_MODAL_INVENTORY_X;
        int y = top + REDSTONE_MODAL_INVENTORY_Y;
        graphics.drawString(font, "Player inventory", x, y - 12, 0xFFD6DEE8, false);
        for (int visualSlot = 0; visualSlot < 36; visualSlot++) {
            int inventorySlot = visualSlot < 27 ? visualSlot + 9 : visualSlot - 27;
            int column = visualSlot % 9;
            int row = visualSlot / 9;
            int slotX = x + column * REDSTONE_MODAL_INVENTORY_SLOT_SIZE;
            int slotY = y + row * REDSTONE_MODAL_INVENTORY_SLOT_SIZE;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF33404E);
            graphics.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF18212C);
            ItemStack stack = player.getInventory().getItem(inventorySlot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, slotX + 1, slotY + 1);
                graphics.renderItemDecorations(font, stack, slotX + 1, slotY + 1);
            }
            if (inside(mouseX, mouseY, slotX, slotY, 18, 18)) {
                graphics.renderOutline(slotX, slotY, 18, 18, 0xFFFFFFFF);
            }
        }
    }

    // Draw the redstone inventory tooltip
    private void renderRedstoneInventoryTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!"redstone_editor".equals(editingAction) || !redstoneFrequencyModal) return;
        int slot = redstoneInventorySlotAt(mouseX, mouseY);
        Player player = Minecraft.getInstance().player;
        if (slot < 0 || player == null) return;
        ItemStack stack = player.getInventory().getItem(slot);
        if (!stack.isEmpty()) graphics.renderTooltip(font, stack, mouseX, mouseY);
    }

    // Get the redstone inventory slot
    private int redstoneInventorySlotAt(double mouseX, double mouseY) {
        int x = contentLeft() + REDSTONE_MODAL_INVENTORY_X;
        int y = top + REDSTONE_MODAL_INVENTORY_Y;
        if (!inside(mouseX, mouseY, x, y,
                9 * REDSTONE_MODAL_INVENTORY_SLOT_SIZE,
                4 * REDSTONE_MODAL_INVENTORY_SLOT_SIZE)) return -1;
        int column = (int) (mouseX - x) / REDSTONE_MODAL_INVENTORY_SLOT_SIZE;
        int row = (int) (mouseY - y) / REDSTONE_MODAL_INVENTORY_SLOT_SIZE;
        int visualSlot = row * 9 + column;
        return visualSlot < 27 ? visualSlot + 9 : visualSlot - 27;
    }

    // Check if this accepts redstone ghost
    public boolean acceptsRedstoneGhost(int mouseX, int mouseY) {
        return "redstone_editor".equals(editingAction) && redstoneFrequencyModal
                && (inside(mouseX, mouseY, contentLeft() + REDSTONE_MODAL_FIRST_SLOT_X,
                top + REDSTONE_MODAL_FIRST_SLOT_Y, 20, 20)
                || inside(mouseX, mouseY, contentLeft() + REDSTONE_MODAL_SECOND_SLOT_X,
                top + REDSTONE_MODAL_SECOND_SLOT_Y, 20, 20));
    }

    // Accept the redstone ghost
    public void acceptRedstoneGhost(int mouseX, int mouseY, ItemStack stack) {
        if (!acceptsRedstoneGhost(mouseX, mouseY) || stack == null || stack.isEmpty()) return;
        if (inside(mouseX, mouseY, contentLeft() + REDSTONE_MODAL_FIRST_SLOT_X,
                top + REDSTONE_MODAL_FIRST_SLOT_Y, 20, 20)) {
            redstoneFrequencyTarget = 0;
            redstoneFirstGhost = stack.copyWithCount(1);
        } else {
            redstoneFrequencyTarget = 1;
            redstoneSecondGhost = stack.copyWithCount(1);
        }
    }

    // Get the redstone ghost areas
    public List<Rect2i> redstoneGhostAreas() {
        if (!"redstone_editor".equals(editingAction) || !redstoneFrequencyModal) return List.of();
        return List.of(new Rect2i(contentLeft() + REDSTONE_MODAL_FIRST_SLOT_X,
                        top + REDSTONE_MODAL_FIRST_SLOT_Y, 20, 20),
                new Rect2i(contentLeft() + REDSTONE_MODAL_SECOND_SLOT_X,
                        top + REDSTONE_MODAL_SECOND_SLOT_Y, 20, 20));
    }

    // Get the tablet gui area
    public Rect2i tabletGuiArea() {
        return new Rect2i(left, top, PANEL_WIDTH, PANEL_HEIGHT);
    }

    // Accept the redstone ghost
    public void acceptRedstoneGhost(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty() || redstoneGhostAreas().isEmpty()) return;
        if (slot == 0) {
            redstoneFrequencyTarget = 0;
            redstoneFirstGhost = stack.copyWithCount(1);
        } else if (slot == 1) {
            redstoneFrequencyTarget = 1;
            redstoneSecondGhost = stack.copyWithCount(1);
        }
    }

    // Draw the frequency picker
    private void drawFrequencyPicker(GuiGraphics graphics, int mouseX, int mouseY,
                                     int x, int y, int width, ListTag items, int idx, int accent) {
        drawNativeCard(graphics, x, y, width, 31, accent,
                inside(mouseX, mouseY, x, y, width, 31));
        if (items.isEmpty()) {
            graphics.drawString(font, "No inventory items", x + 10, y + 11, 0xFF9BA7B5, false);
            return;
        }
        CompoundTag item = items.getCompound(Math.floorMod(idx, items.size()));
        ResourceLocation id = ResourceLocation.tryParse(item.getString("Id"));
        ItemStack stack = id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id)
                .map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 8, y + 7);
        graphics.drawString(font, font.plainSubstrByWidth(item.getString("Name"), width - 58),
                x + 30, y + 11, 0xFFF5F7FB, false);
        graphics.drawString(font, "‹", x + width - 25, y + 10, 0xFFD7CEEF, false);
        graphics.drawString(font, "›", x + width - 12, y + 10, 0xFFD7CEEF, false);
    }

    // Draw the SCM app
    private void renderScmApp(GuiGraphics graphics, int mouseX, int mouseY,
                              TabletAppDefinition app, int contentLeft, int contentWidth) {
        // -----------------------------------------------------TAB CONTENT-----------------------------------------------------
        CompoundTag data = appData(app.id());
        ListTag targets = data.getList("Targets", Tag.TAG_COMPOUND);
        CompoundTag selected = selectedRow(targets);
        String tab = activeTab(app).id();
        if ("settings".equals(tab)) {
            renderPerAppSettings(graphics, mouseX, mouseY, app, contentLeft, contentWidth,
                    "Safety confirmations", "Show engineering values");
            return;
        }
        if ("ships".equals(tab)) {
            renderScmShips(graphics, mouseX, mouseY, app, contentLeft, contentWidth,
                    targets, selected);
            return;
        }
        // ------------------------------------SELECTED TARGET------------------------------------
        graphics.drawString(font, "Selected ship or dock", contentLeft, top + 70,
                0xFFAEBBCA, false);
        drawNativeCard(graphics, contentLeft, top + 82, contentWidth, 32,
                app.accentColor(), inside(mouseX, mouseY, contentLeft, top + 82, contentWidth, 32));
        if (selected == null) {
            graphics.drawString(font, "No target selected", contentLeft + 12, top + 94,
                    0xFFE0E5EB, false);
            graphics.drawString(font, "Use Reader mode on a dock or Advanced Controller",
                    contentLeft + 158, top + 94, 0xFF9EABB8, false);
        } else {
            graphics.drawString(font, font.plainSubstrByWidth(selected.getString("Name"), 205),
                    contentLeft + 12, top + 89, 0xFFF8F9FB, false);
            graphics.drawString(font, selected.getString("Kind").equals("dock") ? "Ship dock" : "Ship controller",
                    contentLeft + 12, top + 101, 0xFFB6C2CD, false);
            graphics.drawString(font, selected.getBoolean("Online") ? "ONLINE" : "OFFLINE",
                    contentLeft + contentWidth - 58, top + 94,
                    selected.getBoolean("Online") ? 0xFF69D8A8 : 0xFFFF8A80, false);
        }
        if (selected == null) {
            int y = top + 129;
            for (int idx = 0; idx < Math.min(4, targets.size()); idx++) {
                CompoundTag target = targets.getCompound(idx);
                drawActionButton(graphics, mouseX, mouseY, contentLeft, y + idx * 27,
                        contentWidth, "Select " + target.getString("Name"), app.accentColor());
            }
            return;
        }
        // -----------------------------------------------------LANDING-----------------------------------------------------
        if ("landing".equals(tab)) {
            renderLandingZoneApp(graphics, mouseX, mouseY, app, contentLeft, contentWidth, data, selected);
            return;
        }
        // -----------------------------------------------------LOGISTICS-----------------------------------------------------
        if ("logistics".equals(tab)) {
            if (scmStockModal) {
                renderScmStockModal(graphics, mouseX, mouseY, app, contentLeft, contentWidth, selected);
                return;
            }
            graphics.drawString(font, "Logistics runs", contentLeft, top + 126,
                    0xFFF2F4F7, false);
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 194,
                    top + 120, 54, 20, "Stock", app.accentColor());
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 134,
                    top + 120, 68, 20, "Schedule",
                    selected.getBoolean("Pilot") ? app.accentColor() : 0xFF59636E);
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 60,
                    top + 120, 60, 20, "+ Run", 0xFF43A047);
            if (!"ship".equals(selected.getString("Kind"))) {
                graphics.drawWordWrap(font, Component.literal(
                                "Logistics runs belong to a ship controller. Select a ship to edit them."),
                        contentLeft, top + 158, contentWidth, 0xFFFFB0A8);
                return;
            }
            if (scmRunTypePicker) {
                String[] types = {"Item", "Fluid", "FE", "Fuel"};
                int[] colors = {0xFF55D6FF, 0xFF4A8DFF, 0xFFFFD54F, 0xFFFF8A3D};
                for (int idx = 0; idx < types.length; idx++) {
                    int width = (contentWidth - 8) / 2;
                    int x = contentLeft + idx % 2 * (width + 8);
                    int y = top + 148 + idx / 2 * 38;
                    drawNativeCard(graphics, x, y, width, 31, colors[idx],
                            inside(mouseX, mouseY, x, y, width, 31));
                    graphics.drawString(font, types[idx] + " run", x + 12, y + 11,
                            0xFFF7F9FB, false);
                }
                graphics.drawString(font, "Choose a channel type, then select its blocks and connector",
                        contentLeft, top + 224, 0xFFAEBAC6, false);
                return;
            }
            ListTag runs = selected.getList("Runs", Tag.TAG_COMPOUND);
            scmRunScroll = Mth.clamp(scmRunScroll, 0, Math.max(0, runs.size() - 3));
            if (runs.isEmpty()) {
                graphics.drawString(font, "No runs yet. Create one to map storage and a connector.",
                        contentLeft + 8, top + 164, 0xFFAEBAC6, false);
                return;
            }
            for (int visible = 0; visible < 3 && scmRunScroll + visible < runs.size(); visible++) {
                CompoundTag run = runs.getCompound(scmRunScroll + visible);
                int y = top + 147 + visible * 28;
                int col = run.getInt("Color");
                drawNativeCard(graphics, contentLeft, y, contentWidth, 24, col,
                        run.getBoolean("Selected"));
                graphics.fill(contentLeft, y, contentLeft + 4, y + 24, col);
                String detail = run.getString("Name") + "  |  "
                        + run.getString("ResourceLabel") + "  |  "
                        + run.getList("Endpoints", Tag.TAG_COMPOUND).size() + " blocks";
                graphics.drawString(font, font.plainSubstrByWidth(detail, contentWidth - 166),
                        contentLeft + 10, y + 8, 0xFFF2F5F8, false);
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 150,
                        y + 3, 42, 18, "Edit", col);
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 103,
                        y + 3, 49, 18, "Rename", app.accentColor());
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 49,
                        y + 3, 46, 18, "Delete", 0xFFE57373);
            }
            return;
        }
        // -----------------------------------------------------SHIP CONTROLS-----------------------------------------------------
        String status = selected.getString("Status");
        if (!selected.getBoolean("Ready")) {
            if (status.isBlank()) status = "Waiting for initialization";
            graphics.drawString(font, status, contentLeft, top + 123, 0xFFBAC5CF, false);
        }
        String[] controls = {"Initialize", "Hover", "Stop", "Landing zone"};
        int[] colors = {0xFF42A5F5, 0xFF66BB6A, 0xFFEF5350, 0xFFFFB74D};
        for (int idx = 0; idx < controls.length; idx++) {
            int cardWidth = (contentWidth - 8) / 2;
            int x = contentLeft + idx % 2 * (cardWidth + 8);
            int y = top + 139 + idx / 2 * 47;
            drawNativeCard(graphics, x, y, cardWidth, 40, colors[idx],
                    inside(mouseX, mouseY, x, y, cardWidth, 40));
            AdvancedControllerV2Theme.drawRoundedRect(graphics, x + 10, y + 9,
                    22, 22, 11, colors[idx]);
            graphics.drawString(font, controls[idx], x + 41, y + 15, 0xFFF6F7F9, false);
        }
    }

    // Draw the SCM stock modal
    private void renderScmStockModal(GuiGraphics graphics, int mouseX, int mouseY,
                                     TabletAppDefinition app, int contentLeft, int contentWidth,
                                     CompoundTag selected) {
        graphics.drawString(font, "Ship stock ticker", contentLeft, top + 72,
                0xFFF4F7FA, false);
        ListTag stock = selected.getList("Stock", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < Math.min(6, stock.size()); idx++) {
            CompoundTag row = stock.getCompound(idx);
            int y = top + 91 + idx * 20;
            drawNativeCard(graphics, contentLeft, y, 276, 17, app.accentColor(),
                    inside(mouseX, mouseY, contentLeft, y, 276, 17));
            graphics.drawString(font, font.plainSubstrByWidth(row.getString("Name"), 180),
                    contentLeft + 8, y + 5, 0xFFF2F5F8, false);
            graphics.drawString(font, Long.toString(row.getLong("Amount")),
                    contentLeft + 210, y + 5, 0xFFC2CDD8, false);
        }
        graphics.drawString(font, "Deliver to player or ship dock", contentLeft + 290, top + 94,
                0xFFBAC5CF, false);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 290, top + 145,
                74, 22, "Send", app.accentColor());
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 371, top + 145,
                74, 22, "Close", 0xFF596675);
    }

    // Draw the landing zone app
    private void renderLandingZoneApp(GuiGraphics graphics, int mouseX, int mouseY,
                                      TabletAppDefinition app, int contentLeft, int contentWidth,
                                      CompoundTag data, CompoundTag selected) {
        boolean dock = "dock".equals(selected.getString("Kind"));
        ListTag zones = data.getList("LandingZones", Tag.TAG_COMPOUND);
        boolean zone = !zones.isEmpty();
        boolean draft = data.contains("LandingDraftStart");
        graphics.drawString(font, "Persistent landing zone", contentLeft, top + 127,
                0xFFF4F5F7, false);
        if (!dock) {
            graphics.drawWordWrap(font, Component.literal(
                            "Landing zones belong to a Ship Dock. Select a dock before editing."),
                    contentLeft, top + 146, contentWidth, 0xFFFFB0A8);
            return;
        }
        String status = zone ? "Zone active and visible while this tablet is held"
                : draft ? "Start corner set — interact again for the end corner"
                : "No landing zone has been drawn";
        drawNativeCard(graphics, contentLeft, top + 143, contentWidth, 44,
                zone ? 0xFF4DB6AC : app.accentColor(), false);
        graphics.drawString(font, status, contentLeft + 12, top + 157,
                0xFFF2F5F6, false);
        for (int idx = 0; idx < Math.min(3, zones.size()); idx++) {
            CompoundTag row = zones.getCompound(idx);
            int y = top + 190 + idx * 18;
            graphics.drawString(font, (row.getInt("QueueOrder") + 1) + ". " + row.getString("Name"),
                    contentLeft + 8, y + 4, row.getBoolean("Selected") ? 0xFFFFFFFF : 0xFFAFBAC5, false);
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + 235, y,
                    70, 17, row.getBoolean("Selected") ? "Selected" : "Select", app.accentColor());
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + 312, y,
                    54, 17, "Remove", 0xFFE57373);
        }
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 374, top + 190,
                72, 22, draft ? "Set end" : "Add zone", app.accentColor());
        graphics.drawString(font, "Sneak + scroll a selected face to push or pull it",
                contentLeft + 235, top + 221, 0xFFAEBAC6, false);
    }

    // Draw the settings app
    private void renderSettingsApp(GuiGraphics graphics, int mouseX, int mouseY,
                                   TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag settings = settingsData();
        String tab = activeTab(app).id();
        if ("tablet".equals(tab)) {
            graphics.drawString(font, "Tablet identity", contentLeft, top + 73, 0xFFF3F5F7, false);
            drawNativeCard(graphics, contentLeft, top + 89, contentWidth, 42,
                    app.accentColor(), inside(mouseX, mouseY, contentLeft, top + 89, contentWidth, 42));
            graphics.drawString(font, settings.getString("Name"), contentLeft + 12, top + 101,
                    0xFFF4F6F8, false);
            graphics.drawString(font, sourceTabletId.toString(), contentLeft + 12, top + 115,
                    0xFF9FAAB5, false);
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 82,
                    top + 99, 70, 22, "Rename", app.accentColor());
            graphics.drawString(font, "Installed themes", contentLeft, top + 141, 0xFFF3F5F7, false);
            ListTag wallpapers = settings.getList("Wallpapers", Tag.TAG_COMPOUND);
            for (int idx = 0; idx < wallpapers.size(); idx++) {
                String id = wallpapers.getCompound(idx).getString("Id");
                int x = contentLeft + idx * 87;
                int[] colors = wallpaperColors(id);
                graphics.fillGradient(x, top + 154, x + 78, top + 186, colors[0], colors[1]);
                if (id.equals(settings.getString("Wallpaper"))) {
                    graphics.renderOutline(x, top + 154, 78, 32, 0xFFFFFFFF);
                }
                graphics.drawCenteredString(font, label(id), x + 39, top + 190, 0xFFDCE3E9);
            }
            return;
        }
        if ("apps".equals(tab)) {
            graphics.drawString(font, "Application manager", contentLeft, top + 73, 0xFFF3F5F7, false);
            ListTag apps = settings.getList("Apps", Tag.TAG_COMPOUND);
            int pageCount = Math.max(1, (apps.size() + SETTINGS_APP_PAGE_SIZE - 1)
                    / SETTINGS_APP_PAGE_SIZE);
            settingsAppPage = Mth.clamp(settingsAppPage, 0, pageCount - 1);
            if (pageCount > 1) {
                drawSmallButton(graphics, mouseX, mouseY,
                        contentLeft + contentWidth - 112, top + 68, 22, 18,
                        "<", app.accentColor());
                graphics.drawCenteredString(font, (settingsAppPage + 1) + " / " + pageCount,
                        contentLeft + contentWidth - 57, top + 73, 0xFFC4CFDA);
                drawSmallButton(graphics, mouseX, mouseY,
                        contentLeft + contentWidth - 22, top + 68, 22, 18,
                        ">", app.accentColor());
            }
            int firstApp = settingsAppPage * SETTINGS_APP_PAGE_SIZE;
            for (int visible = 0; visible < SETTINGS_APP_PAGE_SIZE
                    && firstApp + visible < apps.size(); visible++) {
                CompoundTag row = apps.getCompound(firstApp + visible);
                int y = top + 90 + visible * 29;
                drawNativeCard(graphics, contentLeft, y, contentWidth, 25,
                        app.accentColor(), inside(mouseX, mouseY, contentLeft, y, contentWidth, 25));
                graphics.drawString(font, row.getString("Name"), contentLeft + 11, y + 8,
                        0xFFF1F4F6, false);
                String status = row.getBoolean("Installed") ? "Installed" : "Available in App Store";
                graphics.drawString(font, status, contentLeft + 190, y + 8,
                        row.getBoolean("Installed") ? 0xFF80CBC4 : 0xFF9EA7B0, false);
                if (!row.getBoolean("Required")) {
                    drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 79,
                            y + 3, 70, 19, row.getBoolean("Installed") ? "Remove" : "Store",
                            row.getBoolean("Installed") ? 0xFFE57373 : 0xFF66BB6A);
                } else {
                    graphics.drawCenteredString(font, "Built in",
                            contentLeft + contentWidth - 44, y + 8, 0xFF9EAAB7);
                }
            }
            return;
        }
        graphics.drawString(font, "Create: Gadgets and Gizmos", contentLeft, top + 84,
                0xFFF3F5F7, false);
        graphics.drawWordWrap(font, Component.literal(
                        "Version " + settings.getString("ModVersion") + "  |  Library "
                                + settings.getString("LibraryVersion")
                                + "\nNative ship, automation and logistics tools for Create and Sable."),
                contentLeft, top + 108, contentWidth, 0xFFB1BCC6);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft, top + 174,
                176, 24, "Open G&G Wiki", app.accentColor());
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 186, top + 174,
                176, 24, "Join Discord", 0xFF5865F2);
    }

    // Draw the App Store
    private void renderAppStoreNativeApp(GuiGraphics graphics, int mouseX, int mouseY,
                                         TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        ListTag apps = data.getList("Apps", Tag.TAG_COMPOUND);
        int pageCount = Math.max(1, (apps.size() + SETTINGS_APP_PAGE_SIZE - 1)
                / SETTINGS_APP_PAGE_SIZE);
        appStorePage = Mth.clamp(appStorePage, 0, pageCount - 1);
        graphics.drawString(font, "Applications", contentLeft, top + 73, 0xFFF3F5F7, false);
        if (pageCount > 1) {
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 112,
                    top + 68, 22, 18, "<", app.accentColor());
            graphics.drawCenteredString(font, (appStorePage + 1) + " / " + pageCount,
                    contentLeft + contentWidth - 57, top + 73, 0xFFC4CFDA);
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 22,
                    top + 68, 22, 18, ">", app.accentColor());
        }
        int firstApp = appStorePage * SETTINGS_APP_PAGE_SIZE;
        for (int visible = 0; visible < SETTINGS_APP_PAGE_SIZE && firstApp + visible < apps.size(); visible++) {
            CompoundTag row = apps.getCompound(firstApp + visible);
            int y = top + 90 + visible * 29;
            boolean owned = row.getBoolean("Owned");
            boolean installed = row.getBoolean("Installed");
            int priceCount = row.getInt("PriceCount");
            String price = priceCount == 0 ? "Free"
                    : priceCount + " " + label(ResourceLocation.tryParse(row.getString("PriceItem"))
                    == null ? "item" : ResourceLocation.parse(row.getString("PriceItem")).getPath());
            drawNativeCard(graphics, contentLeft, y, contentWidth, 25, app.accentColor(),
                    inside(mouseX, mouseY, contentLeft, y, contentWidth, 25));
            graphics.drawString(font, row.getString("Name"), contentLeft + 11, y + 4, 0xFFF1F4F6, false);
            graphics.drawString(font, installed ? "Installed" : owned ? "Owned" : price,
                    contentLeft + 11, y + 14, installed ? 0xFF80CBC4 : owned ? 0xFF8DD4FF : 0xFF9EA7B0,
                    false);
            if (!installed) {
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 79,
                        y + 3, 70, 19, owned ? "Install" : priceCount == 0 ? "Get" : "Buy",
                        owned ? 0xFF42A5F5 : app.accentColor());
            }
        }
        if (apps.isEmpty()) {
            graphics.drawString(font, "No purchasable applications are registered.", contentLeft, top + 98,
                    0xFF9EA7B0, false);
        }
    }

    // Draw the auto app
    private void renderAutoApp(GuiGraphics graphics, int mouseX, int mouseY,
                               TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        if ("settings".equals(activeTab(app).id())) {
            renderPerAppSettings(graphics, mouseX, mouseY, app, contentLeft, contentWidth,
                    "Metric telemetry", "Arrival notifications");
            return;
        }
        if (!data.getBoolean("Available")) {
            graphics.drawString(font, "G&G Auto", contentLeft, top + 78,
                    0xFFF5F7FA, false);
            drawNativeCard(graphics, contentLeft, top + 98, contentWidth, 91,
                    app.accentColor(), false);
            graphics.drawString(font, "Ship connection required", contentLeft + 14,
                    top + 114, 0xFFF1F5F9, false);
            graphics.drawWordWrap(font, Component.literal(
                            "Place this tablet on an initialized ship with an Advanced Contraption Controller to view telemetry and automation controls."),
                    contentLeft + 14, top + 134, contentWidth - 28, 0xFFB5C2CF);
            return;
        }
        graphics.drawString(font, data.getString("Ship"), contentLeft, top + 72,
                0xFFF5F7FA, false);
        graphics.drawString(font, data.getString("Destination").isBlank()
                        ? "No destination" : "To " + data.getString("Destination"),
                contentLeft, top + 84, 0xFF9FB4CA, false);
        String[] labels = {"Speed", "Weight", "Fuel", "Cargo", "Fluids", "Energy"};
        String[] values = {String.format(Locale.ROOT, "%.1f m/s", data.getDouble("Speed")),
                String.format(Locale.ROOT, "%.0f kg", data.getDouble("Weight")),
                Math.round(data.getDouble("Fuel") * 100.0D) + "%",
                Long.toString(data.getLong("Cargo")), Long.toString(data.getLong("Fluids")),
                data.getLong("Energy") + " FE"};
        for (int idx = 0; idx < labels.length; idx++) {
            int cardWidth = (contentWidth - 12) / 3;
            int x = contentLeft + idx % 3 * (cardWidth + 6);
            int y = top + 101 + idx / 3 * 47;
            drawNativeCard(graphics, x, y, cardWidth, 41, app.accentColor(),
                    inside(mouseX, mouseY, x, y, cardWidth, 41));
            graphics.drawString(font, labels[idx], x + 10, y + 8, 0xFF9FB0C3, false);
            graphics.drawString(font, font.plainSubstrByWidth(values[idx], cardWidth - 20),
                    x + 10, y + 22, 0xFFF8FAFC, false);
        }
        drawSmallButton(graphics, mouseX, mouseY, contentLeft, top + 204,
                104, 23, "Navigate", app.accentColor());
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 111, top + 204,
                92, 23, "Hover", 0xFF43A047);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + 210, top + 204,
                92, 23, "Dock", 0xFF3949AB);
        graphics.drawString(font, data.getBoolean("Pilot") ? "Pilot schedule active" : "Manual trip",
                contentLeft + 315, top + 211, 0xFFB8C5D2, false);
    }

    // Draw the nfc app
    private void renderNfcApp(GuiGraphics graphics, int mouseX, int mouseY,
                              TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        if ("settings".equals(activeTab(app).id())) {
            renderPerAppSettings(graphics, mouseX, mouseY, app, contentLeft, contentWidth,
                    "Show block properties", "Allow direct signals");
            return;
        }
        if (data.isEmpty() || !data.getBoolean("Available")) {
            graphics.drawWordWrap(font, Component.literal(placed
                            ? "This tablet is not attached to a readable block."
                            : "Tap Scan, close the tablet, then interact with any loaded block."),
                    contentLeft, top + 100, contentWidth - 130, 0xFFB9C5D1);
            if (!placed) drawSmallButton(graphics, mouseX, mouseY,
                    contentLeft + contentWidth - 112, top + 91, 112, 27,
                    "Scan block", app.accentColor());
            return;
        }
        drawNativeCard(graphics, contentLeft, top + 74, contentWidth, 48,
                app.accentColor(), false);
        graphics.drawString(font, data.getString("Name"), contentLeft + 12, top + 87,
                0xFFF5F8FB, false);
        graphics.drawString(font, data.getString("Block"), contentLeft + 12, top + 101,
                0xFFA8B7C7, false);
        ListTag properties = data.getList("Properties", Tag.TAG_COMPOUND);
        int visibleProperties = data.getBoolean("Controllable") ? 5 : 6;
        nfcPropertyScroll = Mth.clamp(nfcPropertyScroll, 0,
                Math.max(0, properties.size() - visibleProperties));
        int propertyWidth = data.getBoolean("Controllable") ? 270 : contentWidth;
        for (int visible = 0; visible < visibleProperties
                && nfcPropertyScroll + visible < properties.size(); visible++) {
            CompoundTag row = properties.getCompound(nfcPropertyScroll + visible);
            int y = top + 133 + visible * 18;
            graphics.drawString(font, font.plainSubstrByWidth(row.getString("Name"), 132),
                    contentLeft + 8, y,
                    0xFF9BAABC, false);
            graphics.drawString(font, font.plainSubstrByWidth(row.getString("Value"),
                            propertyWidth - 158), contentLeft + 150, y,
                    0xFFF0F4F8, false);
        }
        if (properties.size() > visibleProperties) {
            graphics.drawString(font, (nfcPropertyScroll + 1) + "-"
                            + Math.min(properties.size(), nfcPropertyScroll + visibleProperties)
                            + " / " + properties.size(), contentLeft + 8, top + 225,
                    0xFF8F9EAD, false);
        }
        if (data.getBoolean("Controllable")) {
            graphics.drawString(font, "Redstone  " + data.getInt("Signal"),
                    contentLeft + 285, top + 137, 0xFFF1F4F8, false);
            AdvancedControllerV2Theme.drawSlider(graphics, contentLeft + 285, top + 158,
                    150, data.getInt("Signal") / 15.0D, app.accentColor(), true);
        }
        if (!placed) drawSmallButton(graphics, mouseX, mouseY,
                contentLeft + contentWidth - 94, top + 202, 94, 23,
                "Scan again", app.accentColor());
    }

    // Draw the per app settings
    private void renderPerAppSettings(GuiGraphics graphics, int mouseX, int mouseY,
                                      TabletAppDefinition app, int contentLeft, int contentWidth,
                                      String first, String second) {
        CompoundTag settings = appData(app.id()).getCompound("Settings");
        graphics.drawString(font, app.title().getString() + " settings", contentLeft, top + 74,
                0xFFF2F4F7, false);
        String[] names = {first, second};
        String[] keys = {"primary", "secondary"};
        for (int idx = 0; idx < names.length; idx++) {
            int y = top + 96 + idx * 48;
            boolean enabled = !"false".equalsIgnoreCase(settings.getString(keys[idx]));
            drawNativeCard(graphics, contentLeft, y, contentWidth, 40,
                    app.accentColor(), inside(mouseX, mouseY, contentLeft, y, contentWidth, 40));
            graphics.drawString(font, names[idx], contentLeft + 13, y + 15,
                    0xFFF3F5F8, false);
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 67,
                    y + 9, 55, 22, enabled ? "ON" : "OFF",
                    enabled ? app.accentColor() : 0xFF53606E);
        }
        graphics.drawString(font, "These preferences are stored only for this app on this tablet ID.",
                contentLeft, top + 207, 0xFFAEB8C3, false);
    }

    // Draw the rdp app
    private void renderRdpApp(GuiGraphics graphics, int mouseX, int mouseY,
                              TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        boolean connected = data.getBoolean("Connected");
        String status = connected
                ? data.getString("Controller") + "  |  graph " + data.getInt("GraphRevision")
                : data.isEmpty() ? "Connecting to controller..." : "No controller linked";
        graphics.drawString(font, font.plainSubstrByWidth(status, contentWidth - 76),
                contentLeft, top + 84, connected ? 0xFF4FC3C8 : AdvancedControllerV2Theme.MUTED, false);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 70,
                top + 81, 70, 18, "Refresh", app.accentColor());

        List<CompoundTag> controls = rdpControls(data);
        if (connected && controls.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal(
                            "The active graph has no RDP display elements. Add an ACC Display Widget or Advanced HUD Element."),
                    contentLeft, top + 112, contentWidth, AdvancedControllerV2Theme.MUTED);
        }
        for (int idx = 0; idx < Math.min(6, controls.size()); idx++) {
            CompoundTag control = controls.get(idx);
            int cardGap = 8;
            int cardWidth = (contentWidth - cardGap) / 2;
            int x = contentLeft + idx % 2 * (cardWidth + cardGap);
            int y = top + 106 + idx / 2 * 34;
            boolean needsKeyboard = "text_input".equals(control.getString("Type"));
            boolean interactive = control.getBoolean("Interactive")
                    && !(surfaceProjection() && needsKeyboard);
            boolean hovered = interactive && inside(mouseX, mouseY, x, y, cardWidth, 28);
            drawNativeCard(graphics, x, y, cardWidth, 28, app.accentColor(), hovered);
            String title = control.getString("Text");
            graphics.drawString(font, font.plainSubstrByWidth(title, cardWidth - 62), x + 10, y + 6,
                    interactive ? AdvancedControllerV2Theme.PRIMARY : AdvancedControllerV2Theme.SECONDARY, false);
            String detail = surfaceProjection() && needsKeyboard
                    ? "Sneak-use to edit" : rdpControlDetail(control);
            graphics.drawString(font, font.plainSubstrByWidth(detail, cardWidth - 62), x + 10, y + 17,
                    AdvancedControllerV2Theme.MUTED, false);
            String type = control.getString("Type").toUpperCase(Locale.ROOT);
            graphics.drawString(font, font.plainSubstrByWidth(type, 45), x + cardWidth - 50, y + 11,
                    app.accentColor(), false);
        }
        int footerY = top + PANEL_HEIGHT - 88;
        boolean hiddenTextInput = surfaceProjection() && controls.stream()
                .anyMatch(control -> "text_input".equals(control.getString("Type")));
        graphics.drawString(font, hiddenTextInput
                        ? "Sneak-use the tablet to edit text controls"
                        : controls.size() > 6
                        ? "Showing 6 of " + controls.size() + " display elements"
                        : "Live values refresh automatically",
                contentLeft, footerY, AdvancedControllerV2Theme.MUTED, false);
        if (!placed && !state.selections().isEmpty()) {
            drawSmallButton(graphics, mouseX, mouseY, contentLeft, footerY + 15,
                    144, 20, "Push configured blocks", 0xFFFFB74D);
        }
    }

    // Draw the journey app
    private void renderJourneyApp(GuiGraphics graphics, int mouseX, int mouseY,
                                  TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        ListTag docks = data.getList("Docks", Tag.TAG_COMPOUND);
        ListTag departures = data.getList("Departures", Tag.TAG_COMPOUND);
        ListTag stations = data.getList("TrainStations", Tag.TAG_COMPOUND);
        String status = data.isEmpty() ? "Loading live journey information..."
                : docks.size() + " dock(s)  |  " + stations.size() + " station(s)  |  "
                + departures.size() + " journey(s)";
        graphics.drawString(font, status, contentLeft, top + 84,
                AdvancedControllerV2Theme.SECONDARY, false);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 70,
                top + 81, 70, 18, "Refresh", app.accentColor());
        if ("stations".equals(activeTab(app).id())) {
            renderJourneyStations(graphics, stations, contentLeft, contentWidth, app.accentColor());
        } else if ("docks".equals(activeTab(app).id())) {
            renderJourneyDocks(graphics, docks, contentLeft, contentWidth, app.accentColor());
        } else {
            renderJourneyDepartures(graphics, departures, contentLeft, contentWidth, app.accentColor());
        }
    }

    // Draw the block360 app
    private void renderBlock360App(GuiGraphics graphics, int mouseX, int mouseY,
                                   TabletAppDefinition app, int contentLeft, int contentWidth) {
        CompoundTag data = appData(app.id());
        ListTag pending = data.getList("Pending", Tag.TAG_COMPOUND);
        ListTag friends = data.getList("Friends", Tag.TAG_COMPOUND);
        String status = data.contains("Error") ? data.getString("Error")
                : friends.size() + " friend(s)  |  " + pending.size() + " pending";
        int statusWidth = surfaceProjection() ? contentWidth - 76 : contentWidth - 152;
        graphics.drawString(font, font.plainSubstrByWidth(status, statusWidth),
                contentLeft, top + 84, data.contains("Error")
                        ? AdvancedControllerV2Theme.DANGER : AdvancedControllerV2Theme.SECONDARY, false);
        if (surfaceProjection()) {
            graphics.drawString(font, "Sneak to add", contentLeft + contentWidth - 145,
                    top + 86, AdvancedControllerV2Theme.MUTED, false);
        } else {
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 146,
                    top + 81, 72, 18, "Add friend", app.accentColor());
        }
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 70,
                top + 81, 70, 18, "Refresh", app.accentColor());

        int y = top + 106;
        if (!pending.isEmpty()) {
            graphics.drawString(font, "Pending requests", contentLeft, y,
                    AdvancedControllerV2Theme.PRIMARY, false);
            y += 12;
            for (int idx = 0; idx < Math.min(2, pending.size()); idx++) {
                CompoundTag row = pending.getCompound(idx);
                graphics.fill(contentLeft, y, contentLeft + contentWidth, y + 22,
                        AdvancedControllerV2Theme.PANEL_RAISED);
                graphics.drawString(font, font.plainSubstrByWidth(row.getString("Name"), 225),
                        contentLeft + 7, y + 7, AdvancedControllerV2Theme.PRIMARY, false);
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 67,
                        y + 2, 62, 18, "Accept", app.accentColor());
                y += 25;
            }
        }
        graphics.drawString(font, "Friends", contentLeft, y,
                AdvancedControllerV2Theme.PRIMARY, false);
        y += 12;
        if (friends.isEmpty()) {
            graphics.drawString(font, "No friends yet. Add an online player or use Reader mode on them.",
                    contentLeft, y + 6, AdvancedControllerV2Theme.MUTED, false);
            return;
        }
        int availableRows = Math.max(1, (top + PANEL_HEIGHT - 36 - y) / 27);
        for (int idx = 0; idx < Math.min(availableRows, friends.size()); idx++) {
            CompoundTag row = friends.getCompound(idx);
            graphics.fill(contentLeft, y, contentLeft + contentWidth, y + 24,
                    idx % 2 == 0 ? AdvancedControllerV2Theme.PANEL_RAISED
                            : AdvancedControllerV2Theme.PANEL_BACKGROUND);
            int presence = row.getBoolean("Online") ? app.accentColor() : AdvancedControllerV2Theme.MUTED;
            graphics.fill(contentLeft + 5, y + 6, contentLeft + 9, y + 18, presence);
            graphics.drawString(font, font.plainSubstrByWidth(row.getString("Name"), 130),
                    contentLeft + 15, y + 4, AdvancedControllerV2Theme.PRIMARY, false);
            String location = row.getBoolean("Online")
                    ? row.getInt("X") + ", " + row.getInt("Y") + ", " + row.getInt("Z")
                    + "  " + row.getString("Dimension") : "Offline";
            graphics.drawString(font, font.plainSubstrByWidth(location, 215),
                    contentLeft + 15, y + 14, AdvancedControllerV2Theme.MUTED, false);
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 67,
                    y + 3, 62, 18, "Remove", 0xFFE57373);
            y += 27;
        }
    }

    // Draw the redstone link app
    private void renderRedstoneLinkApp(GuiGraphics graphics, int mouseX, int mouseY,
                                       TabletAppDefinition app, int contentLeft, int contentWidth) {
        boolean surfaceProjection = surfaceProjection();
        CompoundTag data = appData(app.id());
        ListTag channels = data.getList("Channels", Tag.TAG_COMPOUND);
        int pageCount = Math.max(1, (channels.size() + 3) / 4);
        redstonePage = Math.max(0, Math.min(redstonePage, pageCount - 1));
        int maxChannels = data.getInt("MaxChannels");
        if (maxChannels <= 0) maxChannels = DiagnosticTabletData.MAX_REDSTONE_LINK_CHANNELS;
        graphics.drawString(font, channels.size() + " / " + maxChannels
                        + " wireless channels", contentLeft, top + 85,
                0xFFB8C8DA, false);
        if (pageCount > 1) {
            graphics.drawString(font, "‹", contentLeft + contentWidth - 154, top + 84,
                    0xFFDCE7F4, false);
            graphics.drawCenteredString(font, (redstonePage + 1) + " / " + pageCount,
                    contentLeft + contentWidth - 128, top + 85, 0xFFB8C8DA);
            graphics.drawString(font, "›", contentLeft + contentWidth - 102, top + 84,
                    0xFFDCE7F4, false);
        }
        if (!surfaceProjection) {
            drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 86,
                    top + 80, 86, 20, "Add channel", app.accentColor());
        } else {
            graphics.drawString(font, "Sneak to edit", contentLeft + contentWidth - 86,
                    top + 86, AdvancedControllerV2Theme.MUTED, false);
        }

        int first = redstonePage * 4;
        for (int visible = 0; visible < 4 && first + visible < channels.size(); visible++) {
            CompoundTag channel = channels.getCompound(first + visible);
            int y = top + 105 + visible * 36;
            drawNativeCard(graphics, contentLeft, y, contentWidth, 32,
                    app.accentColor(), inside(mouseX, mouseY, contentLeft, y, contentWidth, 32));
            graphics.drawString(font, font.plainSubstrByWidth(channel.getString("Label"), 172),
                    contentLeft + 10, y + 6, 0xFFF2F6FB, false);
            String frequency = channel.getString("FirstName") + " + " + channel.getString("SecondName");
            graphics.drawString(font, font.plainSubstrByWidth(frequency, 172),
                    contentLeft + 10, y + 18, 0xFF90A5BC, false);

            String mode = channel.getString("Mode");
            int modeX = contentLeft + 190;
            AdvancedControllerV2Theme.drawRoundedRect(graphics, modeX, y + 7, 58, 18,
                    9, 0xFF283D52);
            graphics.drawCenteredString(font, label(mode), modeX + 29, y + 13,
                    app.accentColor());

            int controlX = contentLeft + 256;
            if ("slider".equals(mode)) {
                int strength = channel.getInt("Strength");
                AdvancedControllerV2Theme.drawSlider(graphics, controlX + 8, y + 16,
                        91, strength / 15.0D, app.accentColor(), true);
                graphics.drawString(font, Integer.toString(strength), controlX + 104, y + 12,
                        0xFFE6EEF7, false);
            } else {
                String control = "toggle".equals(mode)
                        ? (channel.getInt("Strength") > 0 ? "ON" : "OFF") : "SEND";
                drawSmallButton(graphics, mouseX, mouseY, controlX, y + 7,
                        112, 18, control, app.accentColor());
            }
            if (surfaceProjection) {
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + 376, y + 7,
                        70, 18, "Remove", 0xFFE57373);
            } else {
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + 376, y + 7,
                        37, 18, "Edit", 0xFF607D8B);
                drawSmallButton(graphics, mouseX, mouseY, contentLeft + 418, y + 7,
                        28, 18, "×", 0xFFE57373);
            }
        }
        if (channels.isEmpty()) {
            String emptyMessage = surfaceProjection
                    ? "Sneak-use the tablet to open its full screen and add a channel."
                    : "Tap Add channel, then use the tablet on a configured Redstone Link.";
            graphics.drawWordWrap(font, Component.literal(
                            emptyMessage),
                    contentLeft + 12, top + 124, contentWidth - 24, 0xFFAFC0D3);
        }
    }

    // Draw the journey departures
    private void renderJourneyDepartures(GuiGraphics graphics, ListTag departures,
                                         int contentLeft, int contentWidth, int accent) {
        if (departures.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal(
                            "No scheduled ships are currently reporting to docks in this dimension."),
                    contentLeft, top + 113, contentWidth, AdvancedControllerV2Theme.MUTED);
            return;
        }
        for (int idx = 0; idx < Math.min(5, departures.size()); idx++) {
            CompoundTag row = departures.getCompound(idx);
            int y = top + 106 + idx * 24;
            graphics.fill(contentLeft, y, contentLeft + contentWidth, y + 21,
                    idx % 2 == 0 ? AdvancedControllerV2Theme.PANEL_RAISED
                            : AdvancedControllerV2Theme.PANEL_BACKGROUND);
            graphics.fill(contentLeft, y, contentLeft + 3, y + 21, accent);
            String route = row.getString("Ship") + "  ->  " + row.getString("Destination");
            graphics.drawString(font, font.plainSubstrByWidth(route, 205),
                    contentLeft + 9, y + 4, AdvancedControllerV2Theme.PRIMARY, false);
            graphics.drawString(font, formatEta(row.getLong("Eta")),
                    contentLeft + 224, y + 4, accent, false);
            String detail = row.getString("Status") + "  |  Fuel "
                    + Math.round(row.getDouble("Fuel") * 100.0D) + "%";
            graphics.drawString(font, font.plainSubstrByWidth(detail, contentWidth - 18),
                    contentLeft + 9, y + 13, AdvancedControllerV2Theme.MUTED, false);
        }
    }

    // Draw the journey docks
    private void renderJourneyDocks(GuiGraphics graphics, ListTag docks,
                                    int contentLeft, int contentWidth, int accent) {
        if (docks.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal("No ship docks are available in this dimension."),
                    contentLeft, top + 113, contentWidth, AdvancedControllerV2Theme.MUTED);
            return;
        }
        for (int idx = 0; idx < Math.min(5, docks.size()); idx++) {
            CompoundTag row = docks.getCompound(idx);
            int y = top + 106 + idx * 24;
            graphics.fill(contentLeft, y, contentLeft + contentWidth, y + 21,
                    idx % 2 == 0 ? AdvancedControllerV2Theme.PANEL_RAISED
                            : AdvancedControllerV2Theme.PANEL_BACKGROUND);
            graphics.fill(contentLeft, y, contentLeft + 3, y + 21, accent);
            graphics.drawString(font, font.plainSubstrByWidth(row.getString("Name"), 190),
                    contentLeft + 9, y + 4, AdvancedControllerV2Theme.PRIMARY, false);
            graphics.drawString(font, Math.round(row.getDouble("Distance")) + "m",
                    contentLeft + 224, y + 4, accent, false);
            String services = dockServices(row) + "  |  " + row.getInt("Ships") + " ship(s)";
            graphics.drawString(font, font.plainSubstrByWidth(services, contentWidth - 18),
                    contentLeft + 9, y + 13, AdvancedControllerV2Theme.MUTED, false);
        }
    }

    // Draw the journey stations
    private void renderJourneyStations(GuiGraphics graphics, ListTag stations,
                                       int contentLeft, int contentWidth, int accent) {
        if (stations.isEmpty()) {
            graphics.drawWordWrap(font, Component.literal(
                            "No Create train stations are registered on the railway network."),
                    contentLeft, top + 113, contentWidth, AdvancedControllerV2Theme.MUTED);
            return;
        }
        for (int idx = 0; idx < Math.min(6, stations.size()); idx++) {
            CompoundTag row = stations.getCompound(idx);
            int y = top + 106 + idx * 21;
            graphics.fill(contentLeft, y, contentLeft + contentWidth, y + 18,
                    idx % 2 == 0 ? AdvancedControllerV2Theme.PANEL_RAISED
                            : AdvancedControllerV2Theme.PANEL_BACKGROUND);
            graphics.fill(contentLeft, y, contentLeft + 3, y + 18, accent);
            graphics.drawString(font, font.plainSubstrByWidth(row.getString("Name"), contentWidth - 24),
                    contentLeft + 9, y + 6, AdvancedControllerV2Theme.PRIMARY, false);
        }
    }

    // Draw the tabs
    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY, TabletAppDefinition app,
                            int contentLeft, int contentWidth) {
        if (app.tabs().isEmpty()) return;
        int gap = 3;
        int tabWidth = Math.max(44, (contentWidth - gap * (app.tabs().size() - 1)) / app.tabs().size());
        int x = contentLeft;
        TabletTabDefinition active = activeTab(app);
        for (TabletTabDefinition tab : app.tabs()) {
            boolean selected = tab.id().equals(active.id());
            int col = selected ? (0xEE000000 | app.accentColor() & 0x00FFFFFF)
                    : 0x99152538;
            AdvancedControllerV2Theme.drawRoundedRect(graphics, x, top + PANEL_HEIGHT - 42,
                    tabWidth, 24, 12, col);
            String label = font.plainSubstrByWidth(tab.title().getString(), tabWidth - 8);
            graphics.drawCenteredString(font, label, x + tabWidth / 2, top + PANEL_HEIGHT - 34,
                    selected ? 0xFF071017 : 0xFFD6E0EC);
            x += tabWidth + gap;
        }
    }

    // Draw the inline editor
    private void renderInlineEditor(GuiGraphics graphics, int mouseX, int mouseY,
                                    int contentLeft, int contentWidth) {
        int y = top + PANEL_HEIGHT - 99;
        graphics.fill(contentLeft - 5, y, contentLeft + contentWidth + 5,
                top + PANEL_HEIGHT - 32, 0xFF101821);
        boolean journey = "journey_search".equals(editingAction);
        graphics.drawString(font, journey ? "From / To"
                        : editingPrompt.isBlank() ? prompt(editingAction) : editingPrompt,
                contentLeft, journey ? y + 2 : y + 8,
                AdvancedControllerV2Theme.PRIMARY, false);
        drawSmallButton(graphics, mouseX, mouseY, contentLeft + contentWidth - 82,
                journey ? top + 220 : y + 30, 76, 18, "Apply", 0xFF4FC3C8);
    }

    // Draw the navigation bar
    private void renderNavigationBar(GuiGraphics graphics) {
        int barWidth = isHome() ? 58 : 82;
        int barLeft = left + (PANEL_WIDTH - barWidth) / 2;
        AdvancedControllerV2Theme.drawRoundedRect(graphics, barLeft,
                top + PANEL_HEIGHT - 12, barWidth, 3, 2, 0xFFB6C4D5);
        if (!isHome()) {
            graphics.drawString(font, "‹", left + 15, top + PANEL_HEIGHT - 19,
                    0xFFDCE7F4, false);
        }
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        if (btn == 1 && "redstone_editor".equals(editingAction) && redstoneFrequencyModal) {
            if (inside(mouseX, mouseY, contentLeft() + REDSTONE_MODAL_FIRST_SLOT_X,
                    top + REDSTONE_MODAL_FIRST_SLOT_Y, 20, 20)) {
                redstoneFrequencyTarget = 0;
                redstoneFirstGhost = ItemStack.EMPTY;
                return true;
            }
            if (inside(mouseX, mouseY, contentLeft() + REDSTONE_MODAL_SECOND_SLOT_X,
                    top + REDSTONE_MODAL_SECOND_SLOT_Y, 20, 20)) {
                redstoneFrequencyTarget = 1;
                redstoneSecondGhost = ItemStack.EMPTY;
                return true;
            }
        }
        if (btn != 0) return super.mouseClicked(mouseX, mouseY, btn);
        if (!editingAction.isBlank() && !"redstone_editor".equals(editingAction)) {
            int contentLeft = contentLeft();
            int contentWidth = contentWidth();
            int y = top + PANEL_HEIGHT - 99;
            int applyY = "journey_search".equals(editingAction) ? top + 220 : y + 30;
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 82, applyY, 76, 18)) {
                String val = "journey_search".equals(editingAction)
                        ? actionInput.getValue().trim() + "|" + secondaryInput.getValue().trim()
                        : editingValuePrefix + actionInput.getValue().trim();
                send(editingAction, val);
                stopEditing();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, btn);
        }

        List<TabletAppDefinition> apps = installedApps();
        int navigationWidth = 82;
        int navigationLeft = left + (PANEL_WIDTH - navigationWidth) / 2;
        if (!isHome() && (inside(mouseX, mouseY, navigationLeft,
                top + PANEL_HEIGHT - 18, navigationWidth, 13) || inside(mouseX, mouseY,
                left + 8, top + PANEL_HEIGHT - 27, 30, 22))) {
            select(DiagnosticTabletData.appId("home"), "");
            return true;
        }
        if (isHome()) return clickHome(mouseX, mouseY, apps);
        return clickApp(mouseX, mouseY);
    }

    // Handle the home click
    private boolean clickHome(double mouseX, double mouseY, List<TabletAppDefinition> apps) {
        int contentLeft = contentLeft();
        int pageCount = Math.max(1, (apps.size() + 9) / 10);
        if (pageCount > 1 && inside(mouseX, mouseY,
                left + PANEL_WIDTH - 99, top + 54, 25, 22)) {
            homePage = (homePage + pageCount - 1) % pageCount;
            return true;
        }
        if (pageCount > 1 && inside(mouseX, mouseY,
                left + PANEL_WIDTH - 39, top + 54, 25, 22)) {
            homePage = (homePage + 1) % pageCount;
            return true;
        }
        int firstApp = homePage * 10;
        for (int visible = 0; visible < 10 && firstApp + visible < apps.size(); visible++) {
            int x = contentLeft + 20 + visible % 5 * HOME_CELL_WIDTH;
            int y = top + 91 + visible / 5 * HOME_CELL_HEIGHT;
            if (inside(mouseX, mouseY, x - 7, y - 6,
                    HOME_ICON_SIZE + 14, HOME_ICON_SIZE + 24)) {
                TabletAppDefinition app = apps.get(firstApp + visible);
                select(app.id(), app.tabs().isEmpty() ? "" : app.tabs().getFirst().id());
                return true;
            }
        }
        return false;
    }

    // Handle the app click
    private boolean clickApp(double mouseX, double mouseY) {
        // -----------------------------------------------------APP CONTROLS-----------------------------------------------------
        TabletAppDefinition app = selectedApp();
        if (app == null) return false;
        int contentLeft = contentLeft();
        int contentWidth = contentWidth();
        if (usesReaderMode(app) && inside(mouseX, mouseY, contentLeft + contentWidth - 92,
                top + 34, 92, 18)) {
            if (state.mode() == TabletInteractionMode.READER) {
                state = state.withMode(TabletInteractionMode.STANDARD, "");
                send("mode_cycle", TabletInteractionMode.STANDARD.id());
            } else {
                beginReaderMode(readerActionFor(app));
            }
            return true;
        }
        int gap = 3;
        int tabWidth = app.tabs().isEmpty() ? contentWidth
                : Math.max(44, (contentWidth - gap * (app.tabs().size() - 1)) / app.tabs().size());
        int tabX = contentLeft;
        for (TabletTabDefinition tab : app.tabs()) {
            if (inside(mouseX, mouseY, tabX, top + PANEL_HEIGHT - 43, tabWidth, 26)) {
                select(app.id(), tab.id());
                return true;
            }
            tabX += tabWidth + gap;
        }

        // -----------------------------------------------------BUILT IN APPS-----------------------------------------------------
        if (isBuiltInApp(app, "rdp")) {
            return clickRdpNativeApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (isBuiltInApp(app, "journey")) {
            return clickJourneyNativeApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (isBuiltInApp(app, "block360")) {
            return clickBlock360NativeApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (isBuiltInApp(app, "redstone_link")) {
            return clickRedstoneNativeApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (isBuiltInApp(app, "scm")) {
            return clickScmNativeApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (isBuiltInApp(app, "app_store")) {
            return clickAppStoreNativeApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (isBuiltInApp(app, "settings")) {
            return clickSettingsApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (isBuiltInApp(app, "gg_auto")) {
            return clickAutoApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (isBuiltInApp(app, "nfc")) {
            return clickNfcApp(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        TabletAppClientRenderer renderer = TabletAppClientRegistry.renderer(app.id());
        if (renderer != null) {
            return renderer.mouseClicked(clientAppContext(app, null, (int) mouseX, (int) mouseY),
                    mouseX, mouseY, 0);
        }

        int actionIndex = 0;
        TabletTabDefinition active = activeTab(app);
        List<String> visibleActions = visibleActions(active);
        int actionColumns = visibleActions.size() > 8 ? 3 : 2;
        int actionGap = 5;
        int actionWidth = (contentWidth - actionGap * (actionColumns - 1)) / actionColumns;
        for (String action : visibleActions) {
            int x = contentLeft + actionIndex % actionColumns * (actionWidth + actionGap);
            int y = top + 88 + actionIndex / actionColumns * 29;
            if (y > top + PANEL_HEIGHT - 91) break;
            if (inside(mouseX, mouseY, x, y, actionWidth, 23)) {
                beginAction(action);
                return true;
            }
            actionIndex++;
        }
        int footerY = top + PANEL_HEIGHT - 73;
        if (!placed && (isBuiltInApp(app, "scm") || isBuiltInApp(app, "rdp"))
                && !state.selections().isEmpty()
                && inside(mouseX, mouseY, contentLeft, footerY, 144, 20)) {
            state = state.withMode(TabletInteractionMode.PUSH, "push_configured");
            send("begin_push", "push_configured");
            onClose();
            return true;
        }
        return false;
    }

    // Handle the rdp app click
    private boolean clickRdpApp(double mouseX, double mouseY, TabletAppDefinition app,
                                int contentLeft, int contentWidth) {
        if (inside(mouseX, mouseY, contentLeft + contentWidth - 70, top + 81, 70, 18)) {
            send("refresh", "");
            return true;
        }
        List<CompoundTag> controls = rdpControls(appData(app.id()));
        for (int idx = 0; idx < Math.min(6, controls.size()); idx++) {
            CompoundTag control = controls.get(idx);
            int cardGap = 8;
            int cardWidth = (contentWidth - cardGap) / 2;
            int x = contentLeft + idx % 2 * (cardWidth + cardGap);
            int y = top + 106 + idx / 2 * 34;
            if (!control.getBoolean("Interactive")
                    || surfaceProjection() && "text_input".equals(control.getString("Type"))
                    || !inside(mouseX, mouseY, x, y, cardWidth, 28)) {
                continue;
            }
            String action = control.getString("Action");
            switch (control.getString("Type")) {
                case "slider" -> {
                    double minimum = control.getDouble("Min");
                    double maximum = control.getDouble("Max");
                    if (!(maximum > minimum)) maximum = minimum + 1.0D;
                    double fraction = Math.max(0.0D, Math.min(1.0D, (mouseX - x) / cardWidth));
                    send(action, Double.toString(minimum + (maximum - minimum) * fraction));
                }
                case "text_input" -> beginRdpTextInput(action, control);
                default -> send(action, control.getString("CurrentValue"));
            }
            return true;
        }
        int footerY = top + PANEL_HEIGHT - 73;
        if (!placed && !state.selections().isEmpty()
                && inside(mouseX, mouseY, contentLeft, footerY, 144, 20)) {
            state = state.withMode(TabletInteractionMode.PUSH, "push_configured");
            send("begin_push", "push_configured");
            onClose();
            return true;
        }
        return false;
    }

    // Handle the rdp native app click
    private boolean clickRdpNativeApp(double mouseX, double mouseY, TabletAppDefinition app,
                                      int contentLeft, int contentWidth) {
        if ("settings".equals(activeTab(app).id())) {
            return clickPerAppSettings(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (inside(mouseX, mouseY, contentLeft + contentWidth - 78, top + 76, 66, 19)) {
            send("refresh", "");
            return true;
        }
        ListTag devices = appData(app.id()).getList("Devices", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < Math.min(3, devices.size()); idx++) {
            CompoundTag device = devices.getCompound(idx);
            int y = top + 108 + idx * 42;
            String key = device.getString("Key");
            boolean computer = "computer".equals(device.getString("Type"));
            int right = contentLeft + contentWidth;
            if (device.getBoolean("Online")) {
                int controlX = computer ? right - 132 : right - 230;
                if (inside(mouseX, mouseY, controlX, y + 8, 56, 21)) {
                    send("remote_control", key);
                    return true;
                }
                if (!computer && inside(mouseX, mouseY, right - 170, y + 8, 48, 21)) {
                    send("open_graph", key);
                    return true;
                }
                if (!computer && inside(mouseX, mouseY, right - 118, y + 8, 42, 21)) {
                    send("open_plotter", key);
                    return true;
                }
            }
            if (inside(mouseX, mouseY, right - 72, y + 8, 34, 21)) {
                beginTextEdit("rename_controller", "Controller name", key + "|" + device.getString("Name"));
                return true;
            }
            if (inside(mouseX, mouseY, right - 34, y + 8, 24, 21)) {
                send("remove_controller", key);
                return true;
            }
        }
        return false;
    }

    // Handle the journey native app click
    private boolean clickJourneyNativeApp(double mouseX, double mouseY, TabletAppDefinition app,
                                          int contentLeft, int contentWidth) {
        String tab = activeTab(app).id();
        if ("settings".equals(tab)) {
            return clickPerAppSettings(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        CompoundTag data = appData(app.id());
        if ("search".equals(tab) && !journeyPicker.isBlank()) {
            int pickerX = "from".equals(journeyPicker) ? contentLeft + 10 : contentLeft + 200;
            String filter = actionInput.getValue().strip().toLowerCase(Locale.ROOT);
            List<String> choices = new ArrayList<>();
            appendJourneyChoices(choices, data.getList("Docks", Tag.TAG_COMPOUND), filter);
            appendJourneyChoices(choices, data.getList("TrainStations", Tag.TAG_COMPOUND), filter);
            journeyPickerScroll = Mth.clamp(journeyPickerScroll, 0, Math.max(0, choices.size() - 4));
            for (int visible = 0; visible < Math.min(4, choices.size()); visible++) {
                int idx = journeyPickerScroll + visible;
                if (inside(mouseX, mouseY, pickerX + 4, top + 148 + visible * 18, 172, 17)) {
                    if ("from".equals(journeyPicker)) journeyFrom = choices.get(idx);
                    else journeyTo = choices.get(idx);
                    journeyPicker = "";
                    actionInput.setVisible(false);
                    setFocused(null);
                    return true;
                }
            }
        }
        if ("search".equals(tab) && inside(mouseX, mouseY,
                contentLeft + 10, top + 76, 180, 42)) {
            beginJourneyPicker("from", contentLeft + 10);
            return true;
        }
        if ("search".equals(tab) && inside(mouseX, mouseY,
                contentLeft + 200, top + 76, 180, 42)) {
            beginJourneyPicker("to", contentLeft + 200);
            return true;
        }
        if ("search".equals(tab) && inside(mouseX, mouseY,
                contentLeft + 390, top + 84, 56, 34)) {
            if (!journeyFrom.isBlank() && !journeyTo.isBlank()) {
                send("journey_search", journeyFrom + "|" + journeyTo);
            }
            return true;
        }
        if ("search".equals(tab) && inside(mouseX, mouseY,
                contentLeft + 390, top + 119, 56, 18)
                && !journeyFrom.isBlank() && !journeyTo.isBlank()) {
            send("journey_save", journeyFrom + " -> " + journeyTo);
            return true;
        }
        if ("saved".equals(tab)) {
            ListTag saved = data.getList("Saved", Tag.TAG_COMPOUND);
            for (int idx = 0; idx < Math.min(5, saved.size()); idx++) {
                int y = top + 91 + idx * 29;
                if (inside(mouseX, mouseY, contentLeft + contentWidth - 31, y, 31, 25)) {
                    send("journey_remove", saved.getCompound(idx).getString("Route"));
                    return true;
                }
            }
        }
        return false;
    }

    // Begin the journey picker
    private void beginJourneyPicker(String side, int x) {
        journeyPicker = side;
        journeyPickerScroll = 0;
        resetEditorInputLayout();
        actionInput.setValue("");
        actionInput.setHint(Component.literal("Search docks and stations"));
        actionInput.setX(x + 6);
        actionInput.setY(top + 125);
        actionInput.setWidth(168);
        actionInput.setVisible(true);
        setInitialFocus(actionInput);
    }

    // Handle the block360 native app click
    private boolean clickBlock360NativeApp(double mouseX, double mouseY,
                                           TabletAppDefinition app, int contentLeft, int contentWidth) {
        String tab = activeTab(app).id();
        if ("settings".equals(tab) || "places".equals(tab)) {
            return clickPerAppSettings(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        int panelX = contentLeft + 296;
        if (!surfaceProjection() && inside(mouseX, mouseY, panelX, top + 99, 150, 22)) {
            beginAction("add_friend");
            return true;
        }
        ListTag pending = appData(app.id()).getList("Pending", Tag.TAG_COMPOUND);
        ListTag friends = appData(app.id()).getList("Friends", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < Math.min(6, friends.size()); idx++) {
            CompoundTag friend = friends.getCompound(idx);
            int x = contentLeft + 30 + idx % 3 * 86;
            int y = top + 92 + idx / 3 * 70;
            if (friend.getBoolean("Online") && inside(mouseX, mouseY,
                    x - 10, y + 50, 54, 16)) {
                send("navigate_friend", friend.getString("Uuid"));
                return true;
            }
        }
        if (!pending.isEmpty() && inside(mouseX, mouseY,
                panelX + 94, top + 139, 49, 20)) {
            send("accept_friend", pending.getCompound(0).getString("Uuid"));
            return true;
        }
        if (inside(mouseX, mouseY, panelX, top + 181, 72, 20)) {
            send("refresh", "");
            return true;
        }
        return false;
    }

    // Handle the redstone native app click
    private boolean clickRedstoneNativeApp(double mouseX, double mouseY,
                                           TabletAppDefinition app, int contentLeft, int contentWidth) {
        // -----------------------------------------------------TAB CHECK-----------------------------------------------------
        String tab = activeTab(app).id();
        if ("settings".equals(tab)) {
            return clickPerAppSettings(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        CompoundTag data = appData(app.id());
        ListTag channels = data.getList("Channels", Tag.TAG_COMPOUND);
        ListTag items = data.getList("FrequencyItems", Tag.TAG_COMPOUND);
        // ------------------------------------CHANNEL EDITOR------------------------------------
        if ("redstone_editor".equals(editingAction)) {
            if (redstoneFrequencyModal) {
                if (inside(mouseX, mouseY, contentLeft + REDSTONE_MODAL_FIRST_SLOT_X,
                        top + REDSTONE_MODAL_FIRST_SLOT_Y, 20, 20)) {
                    redstoneFrequencyTarget = 0;
                    return true;
                }
                if (inside(mouseX, mouseY, contentLeft + REDSTONE_MODAL_SECOND_SLOT_X,
                        top + REDSTONE_MODAL_SECOND_SLOT_Y, 20, 20)) {
                    redstoneFrequencyTarget = 1;
                    return true;
                }
                int inventorySlot = redstoneInventorySlotAt(mouseX, mouseY);
                if (inventorySlot >= 0) {
                    Player player = Minecraft.getInstance().player;
                    ItemStack stack = player == null
                            ? ItemStack.EMPTY : player.getInventory().getItem(inventorySlot);
                    if (!stack.isEmpty()) acceptRedstoneGhost(redstoneFrequencyTarget, stack);
                    return true;
                }
                if (inside(mouseX, mouseY, contentLeft + contentWidth - 92,
                        top + 204, 70, 21)) {
                    redstoneFrequencyModal = false;
                    actionInput.setVisible(true);
                    return true;
                }
                return false;
            }
            if (inside(mouseX, mouseY, contentLeft + 54, top + 98, 132, 23)) {
                redstoneFrequencyModal = true;
                redstoneFrequencyTarget = redstoneFirstGhost.isEmpty() ? 0 : 1;
                actionInput.setVisible(false);
                setFocused(null);
                return true;
            }
            if (inside(mouseX, mouseY, contentLeft + 194, top + 98, 120, 23)) {
                redstoneFirstGhost = withNextRedstoneColor(redstoneFirstGhost);
                return true;
            }
            if (inside(mouseX, mouseY, contentLeft + 322, top + 98, 124, 23)) {
                redstoneSecondGhost = withNextRedstoneColor(redstoneSecondGhost);
                return true;
            }
            String[] modes = {"button", "toggle", "slider"};
            for (int idx = 0; idx < modes.length; idx++) {
                if (inside(mouseX, mouseY, contentLeft + idx * 100, top + 151, 92, 22)) {
                    redstoneMode = modes[idx];
                    return true;
                }
            }
            if (inside(mouseX, mouseY, contentLeft, top + 194, 300, 20)) {
                redstoneStrength = (int) Math.round(Math.max(0.0D, Math.min(1.0D,
                        (mouseX - contentLeft) / 300.0D)) * 15.0D);
                return true;
            }
            if (inside(mouseX, mouseY, contentLeft + 320, top + 190, 126, 25)
                    && !redstoneFirstGhost.isEmpty() && !redstoneSecondGhost.isEmpty()) {
                String name = actionInput.getValue().isBlank() ? "Redstone control" : actionInput.getValue().strip();
                String encoded = BuiltInRegistries.ITEM.getKey(redstoneFirstGhost.getItem()) + "|"
                        + redstoneColor(redstoneFirstGhost) + "|"
                        + BuiltInRegistries.ITEM.getKey(redstoneSecondGhost.getItem()) + "|"
                        + redstoneColor(redstoneSecondGhost) + "|"
                        + redstoneMode + "|" + redstoneStrength + "|" + name;
                if (redstoneEditingId.isBlank()) send("channel_create", encoded);
                else send("channel_update", redstoneEditingId + "|" + encoded);
                stopEditing();
                redstoneEditingId = "";
                return true;
            }
            return false;
        }
        // -----------------------------------------------------DEVICE LIST-----------------------------------------------------
        if ("devices".equals(tab) && !surfaceProjection()) {
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 145, top + 70, 92, 23)) {
                beginRedstoneEditor(null, data);
                return true;
            }
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 48, top + 70, 48, 23)) {
                beginAction("bind_channel");
                return true;
            }
        }
        // -----------------------------------------------------CHANNEL LIST-----------------------------------------------------
        int firstIndex = redstonePage * 4;
        for (int visible = 0; visible < 4 && firstIndex + visible < channels.size(); visible++) {
            CompoundTag channel = channels.getCompound(firstIndex + visible);
            int cardWidth = (contentWidth - 8) / 2;
            int x = contentLeft + visible % 2 * (cardWidth + 8);
            int y = top + 101 + visible / 2 * 64;
            String id = channel.getUUID("Id").toString();
            String mode = channel.getString("Mode");
            if (inside(mouseX, mouseY, x + cardWidth - 58, y + 8, 49, 21)) {
                if ("button".equals(mode)) send("channel_button", id);
                else if ("toggle".equals(mode)) send("channel_toggle", id);
                return true;
            }
            if (inside(mouseX, mouseY, x + 12, y + 37, cardWidth - 24, 20)) {
                int strength = (int) Math.round(Math.max(0.0D, Math.min(1.0D,
                        (mouseX - x - 12) / (cardWidth - 24.0D))) * 15.0D);
                send("slider".equals(mode) ? "channel_slider" : "channel_strength",
                        id + "|" + strength);
                return true;
            }
            if ("devices".equals(tab) && !surfaceProjection()
                    && inside(mouseX, mouseY, x + cardWidth - 43, y + 31, 43, 24)) {
                beginRedstoneEditor(channel, data);
                return true;
            }
        }
        return false;
    }

    // Handle the SCM native app click
    private boolean clickScmNativeApp(double mouseX, double mouseY, TabletAppDefinition app,
                                      int contentLeft, int contentWidth) {
        String tab = activeTab(app).id();
        if ("settings".equals(tab)) {
            return clickPerAppSettings(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        CompoundTag data = appData(app.id());
        ListTag targets = data.getList("Targets", Tag.TAG_COMPOUND);
        CompoundTag selected = selectedRow(targets);
        if ("ships".equals(tab)) {
            for (int idx = 0; idx < Math.min(5, targets.size()); idx++) {
                int y = top + 86 + idx * 29;
                if (inside(mouseX, mouseY, contentLeft, y, 176, 25)) {
                    send("select_target", targets.getCompound(idx).getString("Key"));
                    return true;
                }
            }
            if (selected == null) return false;
            String[] actions = {"manual_control", "navigate", "follow", "climb",
                    "hover", "initialize", "dock"};
            int panelX = contentLeft + 187;
            for (int idx = 0; idx < actions.length; idx++) {
                int x = panelX + idx % 2 * 130;
                int y = top + 105 + idx / 2 * 29;
                if (inside(mouseX, mouseY, x, y, 124, 23)) {
                    beginAction(actions[idx]);
                    return true;
                }
            }
            return false;
        }
        if (selected == null) {
            for (int idx = 0; idx < Math.min(4, targets.size()); idx++) {
                if (inside(mouseX, mouseY, contentLeft, top + 129 + idx * 27,
                        contentWidth, 23)) {
                    send("select_target", targets.getCompound(idx).getString("Key"));
                    return true;
                }
            }
            return false;
        }
        if ("landing".equals(tab) && "dock".equals(selected.getString("Kind"))) {
            ListTag zones = data.getList("LandingZones", Tag.TAG_COMPOUND);
            for (int idx = 0; idx < Math.min(3, zones.size()); idx++) {
                CompoundTag row = zones.getCompound(idx);
                int y = top + 190 + idx * 18;
                if (inside(mouseX, mouseY, contentLeft + 235, y, 70, 17)) {
                    send("select_landing_zone", row.getUUID("Id").toString());
                    return true;
                }
                if (inside(mouseX, mouseY, contentLeft + 312, y, 54, 17)) {
                    send("remove_landing_zone", row.getUUID("Id").toString());
                    return true;
                }
            }
            if (inside(mouseX, mouseY, contentLeft + 374, top + 190, 72, 22)) {
                beginAction("landing_zone");
                return true;
            }
            return false;
        }
        if ("logistics".equals(tab)) {
            // ------------------------------------STOCK MODAL------------------------------------
            if (scmStockModal) {
                if (inside(mouseX, mouseY, contentLeft + 371, top + 145, 74, 22)) {
                    scmStockModal = false;
                    actionInput.setVisible(false);
                    return true;
                }
                if (inside(mouseX, mouseY, contentLeft + 290, top + 145, 74, 22)) {
                    send("request_items", actionInput.getValue().strip());
                    return true;
                }
                return false;
            }
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 194,
                    top + 120, 54, 20)) {
                scmStockModal = true;
                actionInput.setValue("");
                actionInput.setHint(Component.literal("Player or ship dock address"));
                actionInput.setX(contentLeft + 290);
                actionInput.setY(top + 117);
                actionInput.setWidth(155);
                actionInput.setVisible(true);
                setInitialFocus(actionInput);
                return true;
            }
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 134,
                    top + 120, 68, 20)) {
                if (selected.getBoolean("Pilot")) beginAction("schedule");
                return true;
            }
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 60,
                    top + 120, 60, 20)) {
                scmRunTypePicker = !scmRunTypePicker;
                return true;
            }
            if (!"ship".equals(selected.getString("Kind"))) return false;
            // ------------------------------------RUN TYPE PICKER------------------------------------
            if (scmRunTypePicker) {
                String[] types = {"item", "fluid", "energy", "fuel"};
                for (int idx = 0; idx < types.length; idx++) {
                    int width = (contentWidth - 8) / 2;
                    int x = contentLeft + idx % 2 * (width + 8);
                    int y = top + 148 + idx / 2 * 38;
                    if (inside(mouseX, mouseY, x, y, width, 31)) {
                        beginLogisticsReader("begin_logistics_run", types[idx]);
                        return true;
                    }
                }
                return false;
            }
            ListTag runs = selected.getList("Runs", Tag.TAG_COMPOUND);
            for (int visible = 0; visible < 3 && scmRunScroll + visible < runs.size(); visible++) {
                CompoundTag run = runs.getCompound(scmRunScroll + visible);
                int y = top + 147 + visible * 28;
                String id = run.getUUID("Id").toString();
                if (inside(mouseX, mouseY, contentLeft + contentWidth - 150,
                        y + 3, 42, 18)) {
                    beginLogisticsReader("edit_logistics_run", id);
                    return true;
                }
                if (inside(mouseX, mouseY, contentLeft + contentWidth - 103,
                        y + 3, 49, 18)) {
                    resetEditorInputLayout();
                    editingAction = "rename_logistics_run";
                    editingPrompt = "Logistics run name";
                    editingValuePrefix = id + "|";
                    actionInput.setValue(run.getString("Name"));
                    actionInput.setHint(Component.literal("Run name"));
                    actionInput.setVisible(true);
                    setInitialFocus(actionInput);
                    return true;
                }
                if (inside(mouseX, mouseY, contentLeft + contentWidth - 49,
                        y + 3, 46, 18)) {
                    send("delete_logistics_run", id);
                    return true;
                }
                if (inside(mouseX, mouseY, contentLeft, y,
                        contentWidth - 155, 24)) {
                    send("select_logistics_run", id);
                    return true;
                }
            }
            return false;
        }
        String[] actions = {"initialize", "hover", "stop", "landing_zone"};
        for (int idx = 0; idx < actions.length; idx++) {
            int cardWidth = (contentWidth - 8) / 2;
            int x = contentLeft + idx % 2 * (cardWidth + 8);
            int y = top + 139 + idx / 2 * 47;
            if (inside(mouseX, mouseY, x, y, cardWidth, 40)) {
                if ("landing_zone".equals(actions[idx])) select(app.id(), "landing");
                else send(actions[idx], "");
                return true;
            }
        }
        return false;
    }

    // Handle the settings app click
    private boolean clickSettingsApp(double mouseX, double mouseY, TabletAppDefinition app,
                                     int contentLeft, int contentWidth) {
        CompoundTag settings = settingsData();
        String tab = activeTab(app).id();
        if ("tablet".equals(tab)) {
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 82, top + 99, 70, 22)) {
                beginTextEdit("tablet_rename", "Tablet name", settings.getString("Name"));
                return true;
            }
            ListTag wallpapers = settings.getList("Wallpapers", Tag.TAG_COMPOUND);
            for (int idx = 0; idx < wallpapers.size(); idx++) {
                if (inside(mouseX, mouseY, contentLeft + idx * 87, top + 154, 78, 44)) {
                    send("wallpaper_set", wallpapers.getCompound(idx).getString("Id"));
                    return true;
                }
            }
        } else if ("apps".equals(tab)) {
            ListTag apps = settings.getList("Apps", Tag.TAG_COMPOUND);
            int pageCount = Math.max(1, (apps.size() + SETTINGS_APP_PAGE_SIZE - 1)
                    / SETTINGS_APP_PAGE_SIZE);
            settingsAppPage = Mth.clamp(settingsAppPage, 0, pageCount - 1);
            if (pageCount > 1 && inside(mouseX, mouseY,
                    contentLeft + contentWidth - 112, top + 68, 22, 18)) {
                settingsAppPage = Math.max(0, settingsAppPage - 1);
                return true;
            }
            if (pageCount > 1 && inside(mouseX, mouseY,
                    contentLeft + contentWidth - 22, top + 68, 22, 18)) {
                settingsAppPage = Math.min(pageCount - 1, settingsAppPage + 1);
                return true;
            }
            int firstApp = settingsAppPage * SETTINGS_APP_PAGE_SIZE;
            for (int visible = 0; visible < SETTINGS_APP_PAGE_SIZE
                    && firstApp + visible < apps.size(); visible++) {
                CompoundTag row = apps.getCompound(firstApp + visible);
                int y = top + 90 + visible * 29;
                if (!row.getBoolean("Required") && inside(mouseX, mouseY,
                        contentLeft + contentWidth - 79, y + 3, 70, 19)) {
                    if (row.getBoolean("Installed")) {
                        send("app_uninstall", row.getString("Id"));
                    } else {
                        select(DiagnosticTabletData.appId("app_store"), "store");
                    }
                    return true;
                }
            }
        } else if ("about".equals(tab)) {
            if (inside(mouseX, mouseY, contentLeft, top + 174, 176, 24)) {
                Util.getPlatform().openUri("https://gadgetsngizmos-hub.com/wiki");
                return true;
            }
            if (inside(mouseX, mouseY, contentLeft + 186, top + 174, 176, 24)) {
                Util.getPlatform().openUri("https://discord.gg/zhvuEMEpZR");
                return true;
            }
        }
        return false;
    }

    // Handle the App Store click
    private boolean clickAppStoreNativeApp(double mouseX, double mouseY, TabletAppDefinition app,
                                           int contentLeft, int contentWidth) {
        ListTag apps = appData(app.id()).getList("Apps", Tag.TAG_COMPOUND);
        int pageCount = Math.max(1, (apps.size() + SETTINGS_APP_PAGE_SIZE - 1)
                / SETTINGS_APP_PAGE_SIZE);
        appStorePage = Mth.clamp(appStorePage, 0, pageCount - 1);
        if (pageCount > 1 && inside(mouseX, mouseY,
                contentLeft + contentWidth - 112, top + 68, 22, 18)) {
            appStorePage = Math.max(0, appStorePage - 1);
            return true;
        }
        if (pageCount > 1 && inside(mouseX, mouseY,
                contentLeft + contentWidth - 22, top + 68, 22, 18)) {
            appStorePage = Math.min(pageCount - 1, appStorePage + 1);
            return true;
        }
        int firstApp = appStorePage * SETTINGS_APP_PAGE_SIZE;
        for (int visible = 0; visible < SETTINGS_APP_PAGE_SIZE && firstApp + visible < apps.size(); visible++) {
            CompoundTag row = apps.getCompound(firstApp + visible);
            int y = top + 90 + visible * 29;
            if (!row.getBoolean("Installed") && inside(mouseX, mouseY,
                    contentLeft + contentWidth - 79, y + 3, 70, 19)) {
                send("purchase_app", row.getString("Id"));
                return true;
            }
        }
        return false;
    }

    // Handle the auto app click
    private boolean clickAutoApp(double mouseX, double mouseY, TabletAppDefinition app,
                                 int contentLeft, int contentWidth) {
        if ("settings".equals(activeTab(app).id())) {
            return clickPerAppSettings(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        if (!appData(app.id()).getBoolean("Available")) return false;
        if (inside(mouseX, mouseY, contentLeft, top + 204, 104, 23)) {
            beginTextEdit("navigate", "X, Y, Z coordinates", "");
            return true;
        }
        if (inside(mouseX, mouseY, contentLeft + 111, top + 204, 92, 23)) {
            send("hover", "");
            return true;
        }
        if (inside(mouseX, mouseY, contentLeft + 210, top + 204, 92, 23)) {
            send("dock", "");
            return true;
        }
        return false;
    }

    // Handle the nfc app click
    private boolean clickNfcApp(double mouseX, double mouseY, TabletAppDefinition app,
                                int contentLeft, int contentWidth) {
        if ("settings".equals(activeTab(app).id())) {
            return clickPerAppSettings(mouseX, mouseY, app, contentLeft, contentWidth);
        }
        CompoundTag data = appData(app.id());
        if (!placed && inside(mouseX, mouseY, contentLeft + contentWidth - 112,
                data.getBoolean("Available") ? top + 202 : top + 91,
                data.getBoolean("Available") ? 94 : 112, data.getBoolean("Available") ? 23 : 27)) {
            beginAction("nfc_scan");
            return true;
        }
        if (data.getBoolean("Controllable") && inside(mouseX, mouseY,
                contentLeft + 285, top + 150, 150, 22)) {
            int signal = (int) Math.round(Math.max(0.0D, Math.min(1.0D,
                    (mouseX - contentLeft - 285) / 150.0D)) * 15.0D);
            send("nfc_signal", Integer.toString(signal));
            return true;
        }
        return false;
    }

    // Handle the per app settings click
    private boolean clickPerAppSettings(double mouseX, double mouseY, TabletAppDefinition app,
                                        int contentLeft, int contentWidth) {
        CompoundTag settings = appData(app.id()).getCompound("Settings");
        String[] keys = {"primary", "secondary"};
        for (int idx = 0; idx < keys.length; idx++) {
            int y = top + 96 + idx * 48;
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 67, y + 9, 55, 22)) {
                boolean enabled = !"false".equalsIgnoreCase(settings.getString(keys[idx]));
                send("app_setting", keys[idx] + "|" + !enabled);
                return true;
            }
        }
        return false;
    }

    // Handle the block360 app click
    private boolean clickBlock360App(double mouseX, double mouseY, TabletAppDefinition app,
                                     int contentLeft, int contentWidth) {
        if (!surfaceProjection() && inside(mouseX, mouseY,
                contentLeft + contentWidth - 146, top + 81, 72, 18)) {
            beginAction("add_friend");
            return true;
        }
        if (inside(mouseX, mouseY, contentLeft + contentWidth - 70, top + 81, 70, 18)) {
            send("refresh", "");
            return true;
        }
        CompoundTag data = appData(app.id());
        ListTag pending = data.getList("Pending", Tag.TAG_COMPOUND);
        ListTag friends = data.getList("Friends", Tag.TAG_COMPOUND);
        int y = top + 106;
        if (!pending.isEmpty()) {
            y += 12;
            for (int idx = 0; idx < Math.min(2, pending.size()); idx++) {
                CompoundTag row = pending.getCompound(idx);
                if (inside(mouseX, mouseY, contentLeft + contentWidth - 67, y + 2, 62, 18)) {
                    send("accept_friend", row.getString("Uuid"));
                    return true;
                }
                y += 25;
            }
        }
        y += 12;
        int availableRows = Math.max(1, (top + PANEL_HEIGHT - 36 - y) / 27);
        for (int idx = 0; idx < Math.min(availableRows, friends.size()); idx++) {
            CompoundTag row = friends.getCompound(idx);
            if (inside(mouseX, mouseY, contentLeft + contentWidth - 67, y + 3, 62, 18)) {
                send("remove_friend", row.getString("Uuid"));
                return true;
            }
            y += 27;
        }
        return false;
    }

    // Handle the redstone link app click
    private boolean clickRedstoneLinkApp(double mouseX, double mouseY, TabletAppDefinition app,
                                         int contentLeft, int contentWidth) {
        boolean surfaceProjection = surfaceProjection();
        CompoundTag data = appData(app.id());
        ListTag channels = data.getList("Channels", Tag.TAG_COMPOUND);
        int pageCount = Math.max(1, (channels.size() + 3) / 4);
        if (!surfaceProjection && inside(mouseX, mouseY,
                contentLeft + contentWidth - 86, top + 80, 86, 20)) {
            beginAction("bind_channel");
            return true;
        }
        if (pageCount > 1 && inside(mouseX, mouseY,
                contentLeft + contentWidth - 160, top + 80, 28, 20)) {
            redstonePage = (redstonePage + pageCount - 1) % pageCount;
            return true;
        }
        if (pageCount > 1 && inside(mouseX, mouseY,
                contentLeft + contentWidth - 116, top + 80, 28, 20)) {
            redstonePage = (redstonePage + 1) % pageCount;
            return true;
        }
        int first = redstonePage * 4;
        for (int visible = 0; visible < 4 && first + visible < channels.size(); visible++) {
            CompoundTag channel = channels.getCompound(first + visible);
            int y = top + 105 + visible * 36;
            String id = channel.getUUID("Id").toString();
            String mode = channel.getString("Mode");
            if (inside(mouseX, mouseY, contentLeft + 190, y + 7, 58, 18)) {
                String nextMode = "button".equals(mode) ? "toggle"
                        : "toggle".equals(mode) ? "slider" : "button";
                send("channel_mode", id + "|" + nextMode);
                return true;
            }
            boolean controlHit = "slider".equals(mode)
                    ? inside(mouseX, mouseY, contentLeft + 264, y + 9, 91, 14)
                    : inside(mouseX, mouseY, contentLeft + 256, y + 5, 112, 22);
            if (controlHit) {
                if ("slider".equals(mode)) {
                    double fraction = Math.max(0.0D, Math.min(1.0D,
                            (mouseX - contentLeft - 264) / 91.0D));
                    send("channel_slider", id + "|" + Math.round(fraction * 15.0D));
                } else {
                    send("toggle".equals(mode) ? "channel_toggle" : "channel_button", id);
                }
                return true;
            }
            if (!surfaceProjection
                    && inside(mouseX, mouseY, contentLeft + 376, y + 7, 37, 18)) {
                beginRedstoneRename(id, channel.getString("Label"));
                return true;
            }
            int removeX = surfaceProjection ? contentLeft + 376 : contentLeft + 418;
            int removeWidth = surfaceProjection ? 70 : 28;
            if (inside(mouseX, mouseY, removeX, y + 7, removeWidth, 18)) {
                send("channel_remove", id);
                return true;
            }
        }
        return false;
    }

    // Begin the rdp text input
    private void beginRdpTextInput(String action, CompoundTag control) {
        if (surfaceProjection()) {
            openProjectedTextInput(action, control.getString("Text"),
                    control.getString("CurrentValue"), "");
            return;
        }
        resetEditorInputLayout();
        editingAction = action;
        editingPrompt = control.getString("Text");
        editingValuePrefix = "";
        actionInput.setValue(control.getString("CurrentValue"));
        actionInput.setHint(Component.literal("Enter remote text"));
        actionInput.setVisible(true);
        setInitialFocus(actionInput);
    }

    // Begin the action
    private void beginAction(String action) {
        if (surfaceProjection() && keyboardAction(action)) {
            openProjectedTextInput(action, prompt(action), "", "");
            return;
        }
        if (readerAction(action) && !placed) {
            state = state.withMode(TabletInteractionMode.READER, action);
            send("begin_reader", action);
            onClose();
            return;
        }
        if (keyboardAction(action)) {
            resetEditorInputLayout();
            editingAction = action;
            editingPrompt = prompt(action);
            editingValuePrefix = "";
            actionInput.setValue("");
            actionInput.setHint(Component.literal(placeholder(action)));
            actionInput.setVisible(true);
            setInitialFocus(actionInput);
            return;
        }
        send(action, "");
    }

    // Begin the logistics reader
    private void beginLogisticsReader(String action, String val) {
        state = state.withMode(TabletInteractionMode.READER, "configure_run");
        send(action, val);
        onClose();
    }

    // Begin the redstone rename
    private void beginRedstoneRename(String id, String label) {
        if (surfaceProjection()) {
            openProjectedTextInput("channel_rename", "Channel name", label, id + "|");
            return;
        }
        resetEditorInputLayout();
        editingAction = "channel_rename";
        editingPrompt = "Channel name";
        editingValuePrefix = id + "|";
        actionInput.setValue(label);
        actionInput.setHint(Component.literal("Channel name"));
        actionInput.setVisible(true);
        setInitialFocus(actionInput);
    }

    // Begin the redstone editor
    private void beginRedstoneEditor(CompoundTag channel, CompoundTag data) {
        if (surfaceProjection()) return;
        editingAction = "redstone_editor";
        redstoneEditingId = channel == null ? "" : channel.getUUID("Id").toString();
        redstoneMode = channel == null ? "button" : channel.getString("Mode");
        redstoneStrength = channel == null ? 15 : channel.getInt("Strength");
        redstoneFrequencyModal = false;
        redstoneFrequencyTarget = 0;
        redstoneFirstGhost = itemStack(channel == null ? "" : channel.getString("FirstItem"),
                channel == null ? -1 : channel.getInt("FirstColor"));
        redstoneSecondGhost = itemStack(channel == null ? "" : channel.getString("SecondItem"),
                channel == null ? -1 : channel.getInt("SecondColor"));
        actionInput.setValue(channel == null ? "" : channel.getString("Label"));
        actionInput.setHint(Component.literal("Control name"));
        actionInput.setVisible(true);
        actionInput.setX(contentLeft() + 38);
        actionInput.setY(top + 214);
        actionInput.setWidth(250);
        secondaryInput.setVisible(false);
        ListTag items = data.getList("FrequencyItems", Tag.TAG_COMPOUND);
        redstoneFirstIndex = frequencyIndex(items, channel == null ? "" : channel.getString("FirstItem"));
        redstoneSecondIndex = frequencyIndex(items, channel == null ? "" : channel.getString("SecondItem"));
        setInitialFocus(actionInput);
    }

    // Get the item stack
    private static ItemStack itemStack(String id) {
        return itemStack(id, -1);
    }

    // Get the item stack
    private static ItemStack itemStack(String id, int col) {
        ResourceLocation resource = ResourceLocation.tryParse(id);
        ItemStack stack = resource == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(resource)
                .map(item -> new ItemStack(item, 1)).orElse(ItemStack.EMPTY);
        if (!stack.isEmpty() && col >= 0) {
            stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                    new net.minecraft.world.item.component.DyedItemColor(col, false));
        }
        return stack;
    }

    // Get the redstone color
    private static int redstoneColor(ItemStack stack) {
        net.minecraft.world.item.component.DyedItemColor col = stack.get(
                net.minecraft.core.component.DataComponents.DYED_COLOR);
        return col == null ? -1 : col.rgb();
    }

    // Get the redstone button color
    private static int redstoneButtonColor(ItemStack stack, int fallback) {
        int col = redstoneColor(stack);
        return col < 0 ? fallback : 0xFF000000 | col;
    }

    // Copy the diagnostic tablet with the next redstone color
    private static ItemStack withNextRedstoneColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return stack;
        int current = redstoneColor(stack);
        int next = REDSTONE_CONTROL_COLORS[0];
        for (int idx = 0; idx < REDSTONE_CONTROL_COLORS.length; idx++) {
            if (REDSTONE_CONTROL_COLORS[idx] == current) {
                next = REDSTONE_CONTROL_COLORS[(idx + 1) % REDSTONE_CONTROL_COLORS.length];
                break;
            }
        }
        ItemStack coloured = stack.copyWithCount(1);
        coloured.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
                new net.minecraft.world.item.component.DyedItemColor(next, false));
        return coloured;
    }

    // Begin the text edit
    private void beginTextEdit(String action, String prompt, String val) {
        if (surfaceProjection()) {
            openProjectedTextInput(action, prompt, val, "");
            return;
        }
        resetEditorInputLayout();
        editingAction = action;
        editingPrompt = prompt;
        editingValuePrefix = "";
        actionInput.setValue(val == null ? "" : val);
        actionInput.setHint(Component.literal(prompt));
        actionInput.setVisible(true);
        secondaryInput.setVisible(false);
        setInitialFocus(actionInput);
    }

    // Open the projected text input
    private void openProjectedTextInput(String action, String prompt, String initial,
                                        String prefix) {
        Minecraft.getInstance().setScreen(new ControllerTextInputScreen(null,
                Component.literal("Smart Tablet"), Component.literal(prompt), initial,
                val -> send(action, prefix + val), 192));
    }

    // Reset the editor input layout
    private void resetEditorInputLayout() {
        actionInput.setX(contentLeft() + 10);
        actionInput.setY(top + PANEL_HEIGHT - 66);
        actionInput.setWidth(contentWidth() - 104);
        secondaryInput.setX(contentLeft() + 10);
        secondaryInput.setY(top + PANEL_HEIGHT - 43);
        secondaryInput.setWidth(contentWidth() - 104);
    }

    // Stop the editing
    private void stopEditing() {
        editingAction = "";
        editingPrompt = "";
        editingValuePrefix = "";
        actionInput.setVisible(false);
        secondaryInput.setVisible(false);
        setFocused(null);
    }

    // Select the diagnostic tablet
    private void select(ResourceLocation app, String tab) {
        stopEditing();
        state = state.withApp(app, tab);
        observedSelectedDefinition = TabletAppRegistry.definition(app);
        send("select", "");
        reqActiveAppSnapshot();
    }

    // Request the active app snapshot
    private void reqActiveAppSnapshot() {
        TabletAppDefinition app = selectedApp();
        if (app != null) send("refresh", "");
        requestSettingsSnapshot();
    }

    // Request the settings snapshot
    private void requestSettingsSnapshot() {
        PacketDistributor.sendToServer(new DiagnosticTabletActionPayload(placed, hand,
                tabletPos, tabletSubLevelId, sourceTabletId,
                DiagnosticTabletData.appId("settings"), "tablet", "background_refresh", ""));
    }

    // Send the diagnostic tablet
    private void send(String action, String val) {
        PacketDistributor.sendToServer(new DiagnosticTabletActionPayload(placed, hand,
                tabletPos, tabletSubLevelId, sourceTabletId, state.app(), state.tab(), action,
                val == null ? "" : val));
    }

    // Get the client app context
    private TabletAppClientContext clientAppContext(TabletAppDefinition app,
                                                    GuiGraphics graphics, int mouseX, int mouseY) {
        return new TabletAppClientContext(app, activeTab(app), appData(app.id()), graphics, font,
                contentLeft(), top + 69, contentWidth(), PANEL_HEIGHT - 112, mouseX, mouseY,
                this::send, this::reqActiveAppSnapshot);
    }

    // Draw the action button
    private void drawActionButton(GuiGraphics graphics, int mouseX, int mouseY,
                                  int x, int y, String text, int accent) {
        drawActionButton(graphics, mouseX, mouseY, x, y, 166, text, accent);
    }

    // Draw the action button
    private void drawActionButton(GuiGraphics graphics, int mouseX, int mouseY,
                                  int x, int y, int width, String text, int accent) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, 23);
        drawNativeCard(graphics, x, y, width, 23, accent, hovered);
        AdvancedControllerV2Theme.drawRoundedRect(graphics, x + 8, y + 8, 7, 7, 4, accent);
        graphics.drawString(font, font.plainSubstrByWidth(text, width - 37), x + 21, y + 8,
                0xFFF0F5FA, false);
        graphics.drawString(font, "›", x + width - 13, y + 7, 0xFF9FB2C8, false);
    }

    // Draw the small button
    private void drawSmallButton(GuiGraphics graphics, int mouseX, int mouseY,
                                 int x, int y, int width, int height, String text, int accent) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        int col = hovered ? accent : (0xCC000000 | accent & 0x00FFFFFF);
        AdvancedControllerV2Theme.drawRoundedRect(graphics, x, y, width, height,
                Math.min(9, height / 2), col);
        graphics.drawCenteredString(font, text, x + width / 2, y + 6,
                0xFFF7FAFD);
    }

    // Draw the native card
    private void drawNativeCard(GuiGraphics graphics, int x, int y, int width, int height,
                                int accent, boolean hovered) {
        AdvancedControllerV2Theme.drawRoundedRect(graphics, x, y, width, height, 8,
                hovered ? (0x77000000 | accent & 0x00FFFFFF) : 0xD916273B);
        AdvancedControllerV2Theme.drawRoundedRect(graphics, x + 1, y + 1,
                width - 2, height - 2, 7, hovered ? 0xFF253C54 : 0xFF17283B);
    }

    // Draw the app icon
    private void drawAppIcon(GuiGraphics graphics, TabletAppDefinition app,
                             int x, int y, int size) {
        graphics.blit(app.icon(), x, y, size, size, 0.0F, 0.0F,
                64, 64, 64, 64);
    }

    // Get the minimum ecraft time
    private String minecraftTime() {
        long time = Math.floorMod(minecraftDayTime(), 24000L);
        int hours = (int) ((time / 1000L + 6L) % 24L);
        int minutes = (int) ((time % 1000L) * 60L / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    // Get the world status
    private String worldStatus() {
        Level level = Minecraft.getInstance().level;
        long day = Math.floorDiv(minecraftDayTime(), 24000L) + 1L;
        String weather = level == null ? "Unknown weather"
                : level.isThundering() ? "Thunder"
                : level.isRaining() ? "Rain"
                : "Clear";
        String season = seasonName(level);
        return season.isBlank() ? "Day " + day + " · " + weather
                : "Day " + day + " · " + season + " · " + weather;
    }

    // Get the minimum ecraft day time
    private long minecraftDayTime() {
        Level level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getDayTime();
    }

    // Get the season name
    private static String seasonName(Level level) {
        if (level == null || !ModList.get().isLoaded("sereneseasons")) return "";
        try {
            Object state = Class.forName("sereneseasons.api.season.SeasonHelper")
                    .getMethod("getSeasonState", Level.class).invoke(null, level);
            if (state == null) return "";
            Object season = state.getClass().getMethod("getSeason").invoke(state);
            return season == null ? "" : season.toString().toLowerCase(Locale.ROOT)
                    .replace('_', ' ');
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    // Get the notification count
    private int notificationCount() {
        return notificationCount(null);
    }

    // Get the notification count
    private int notificationCount(ResourceLocation appId) {
        int total = 0;
        for (int idx = 0; idx < settingsData().getList("NotificationCounts", Tag.TAG_COMPOUND).size(); idx++) {
            CompoundTag notification = settingsData().getList("NotificationCounts", Tag.TAG_COMPOUND)
                    .getCompound(idx);
            if (appId == null || appId.toString().equals(notification.getString("Id"))) {
                total += Math.max(0, notification.getInt("Count"));
            }
        }
        return total;
    }

    // Get the content left
    private int contentLeft() {
        return left + CONTENT_MARGIN;
    }

    // Get the content width
    private int contentWidth() {
        return PANEL_WIDTH - CONTENT_MARGIN * 2;
    }

    // Reconcile the runtime apps
    private void reconcileRuntimeApps() {
        TabletAppRegistry.Snapshot snapshot = TabletAppRegistry.snapshot();
        if (snapshot.revision() == observedRegistryRevision) return;
        observedRegistryRevision = snapshot.revision();
        if (isHome()) {
            observedSelectedDefinition = null;
            return;
        }
        TabletAppDefinition selected = snapshot.apps().stream()
                .filter(app -> app.id().equals(state.app())).findFirst().orElse(null);
        if (selected == null) {
            if (actionInput != null && !editingAction.isBlank()) stopEditing();
            state = state.withApp(DiagnosticTabletData.appId("home"), "");
            observedSelectedDefinition = null;
            send("select", "");
            return;
        }
        boolean replaced = observedSelectedDefinition != null
                && observedSelectedDefinition != selected;
        if (replaced && actionInput != null && !editingAction.isBlank()) stopEditing();
        String resolvedTab = selected.tabs().stream().anyMatch(tab -> tab.id().equals(state.tab()))
                ? state.tab() : selected.tabs().isEmpty() ? "" : selected.tabs().getFirst().id();
        if (!resolvedTab.equals(state.tab())) {
            state = state.withApp(selected.id(), resolvedTab);
            send("select", "");
        }
        observedSelectedDefinition = selected;
    }

    // Check if this is home
    private boolean isHome() {
        return DiagnosticTabletData.appId("home").equals(state.app());
    }

    // Check if the built is in the app
    private static boolean isBuiltInApp(TabletAppDefinition app, String id) {
        return DiagnosticTabletApps.isCanonicalDefinition(app, id);
    }

    // Get the selected app
    private TabletAppDefinition selectedApp() {
        return TabletAppRegistry.apps().stream().filter(app -> app.id().equals(state.app()))
                .findFirst().orElse(null);
    }

    // Get the active tab
    private TabletTabDefinition activeTab(TabletAppDefinition app) {
        return app.tabs().stream().filter(tab -> tab.id().equals(state.tab())).findFirst()
                .orElse(app.tabs().isEmpty()
                        ? new TabletTabDefinition("", Component.empty(), List.of()) : app.tabs().getFirst());
    }

    // Get the app data
    private CompoundTag appData(ResourceLocation appId) {
        return DiagnosticTabletClientAppData.get(appId, placed, sourceTabletId,
                tabletSubLevelId, placed ? tabletPos : null);
    }

    // Get the settings data
    private CompoundTag settingsData() {
        return appData(DiagnosticTabletData.appId("settings"));
    }

    // Get the installed apps
    private List<TabletAppDefinition> installedApps() {
        ListTag rows = settingsData().getList("Apps", Tag.TAG_COMPOUND);
        if (rows.isEmpty()) {
            TabletAppDefinition settings = TabletAppRegistry.definition(
                    DiagnosticTabletData.appId("settings"));
            return settings == null ? List.of() : List.of(settings);
        }
        Set<ResourceLocation> installed = new java.util.HashSet<>();
        for (int idx = 0; idx < rows.size(); idx++) {
            CompoundTag row = rows.getCompound(idx);
            ResourceLocation id = ResourceLocation.tryParse(row.getString("Id"));
            if (id != null && row.getBoolean("Installed")) installed.add(id);
        }
        return TabletAppRegistry.apps().stream()
                .filter(app -> installed.contains(app.id()))
                .toList();
    }

    // Get the selected row
    private static CompoundTag selectedRow(ListTag rows) {
        for (int idx = 0; idx < rows.size(); idx++) {
            CompoundTag row = rows.getCompound(idx);
            if (row.getBoolean("Selected")) return row;
        }
        return null;
    }

    // Get the frequency index
    private static int frequencyIndex(ListTag items, String itemId) {
        for (int idx = 0; idx < items.size(); idx++) {
            if (itemId.equals(items.getCompound(idx).getString("Id"))) return idx;
        }
        return 0;
    }

    // Get the wallpaper colors
    private static int[] wallpaperColors(String id) {
        return switch (id == null ? "" : id) {
            case "midnight" -> new int[]{0xFF111827, 0xFF020617};
            case "sunset" -> new int[]{0xFF7C2D5A, 0xFF1E3A5F};
            case "meadow" -> new int[]{0xFF245B52, 0xFF102B30};
            case "graphite" -> new int[]{0xFF343A40, 0xFF111315};
            default -> new int[]{0xFF183F63, 0xFF18102F};
        };
    }

    // Check if surface projection is enabled
    private boolean surfaceProjection() {
        return placed && projected;
    }

    // Get the visible actions
    private List<String> visibleActions(TabletTabDefinition tab) {
        return tab.actions();
    }

    // Handle the reader action
    private boolean readerAction(String action) {
        TabletAppDefinition app = selectedApp();
        return READER_ACTIONS.contains(action)
                && (isBuiltInApp(app, "scm") || isBuiltInApp(app, "redstone_link"));
    }

    // Check if this uses reader mode
    private static boolean usesReaderMode(TabletAppDefinition app) {
        return isBuiltInApp(app, "rdp") || isBuiltInApp(app, "scm")
                || isBuiltInApp(app, "redstone_link") || isBuiltInApp(app, "nfc");
    }

    // Get the reader action
    private static String readerActionFor(TabletAppDefinition app) {
        if (isBuiltInApp(app, "nfc")) return "nfc_scan";
        if (isBuiltInApp(app, "redstone_link")) return "bind_channel";
        return "";
    }

    // Begin the reader mode
    private void beginReaderMode(String action) {
        state = state.withMode(TabletInteractionMode.READER, action);
        send("begin_reader", action);
        onClose();
    }

    // Handle keyboard action
    private boolean keyboardAction(String action) {
        TabletAppDefinition app = selectedApp();
        return app != null && activeTab(app).requiresKeyboard(action);
    }

    // Reconcile the source tablet
    private void reconcileSourceTablet() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        DiagnosticTabletData.State physicalState;
        if (placed) {
            if (!(SimulatedHelper.findLoadedBlockEntityExact(minecraft.level,
                    tabletSubLevelId, tabletPos) instanceof DiagnosticTabletBlockEntity tablet)) {
                return;
            }
            physicalState = tablet.state();
        } else {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (!(stack.getItem() instanceof DiagnosticTabletItem)) return;
            physicalState = DiagnosticTabletData.read(stack);
        }
        UUID physicalId = physicalState.tabletId();
        if (physicalId == null || physicalId.equals(sourceTabletId)) return;
        sourceTabletId = physicalId;
        state = physicalState;
        if (actionInput != null && !editingAction.isBlank()) stopEditing();
        observedSelectedDefinition = selectedApp();
        snapshotRefreshTicks = 0;
        reqActiveAppSnapshot();
    }

    // Get the rdp controls
    private static List<CompoundTag> rdpControls(CompoundTag data) {
        List<CompoundTag> interactiveControls = new ArrayList<>();
        List<CompoundTag> displayElements = new ArrayList<>();
        ListTag widgets = data.getList("Widgets", Tag.TAG_COMPOUND);
        for (int widgetIndex = 0; widgetIndex < widgets.size(); widgetIndex++) {
            CompoundTag widget = widgets.getCompound(widgetIndex);
            ListTag elements = widget.getList("Elements", Tag.TAG_COMPOUND);
            if (elements.isEmpty()) {
                CompoundTag empty = new CompoundTag();
                empty.putString("Text", widget.getString("Label"));
                empty.putString("Type", "display");
                displayElements.add(empty);
                continue;
            }
            for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
                CompoundTag elm = elements.getCompound(elementIndex).copy();
                String text = elm.getString("Text");
                elm.putString("Text", text.isBlank() ? widget.getString("Label") : text);
                String type = elm.getString("Type");
                boolean interactive = Set.of("button", "toggle", "slider", "text_input").contains(type)
                        && !elm.getString("InteractionId").isBlank();
                elm.putBoolean("Interactive", interactive);
                elm.putString("Action", "widget:" + widget.getString("Id") + ":"
                        + elm.getString("InteractionId"));
                (interactive ? interactiveControls : displayElements).add(elm);
            }
        }
        interactiveControls.addAll(displayElements);
        return List.copyOf(interactiveControls);
    }

    // Get the rdp control detail
    private static String rdpControlDetail(CompoundTag control) {
        return switch (control.getString("Type")) {
            case "toggle" -> control.getBoolean("CurrentBoolean") ? "On" : "Off";
            case "slider" -> compactNumber(control.getDouble("CurrentNumber")) + "  ["
                    + compactNumber(control.getDouble("Min")) + " - "
                    + compactNumber(control.getDouble("Max")) + "]";
            case "text_input" -> control.getString("CurrentValue").isBlank()
                    ? "Tap to enter text" : control.getString("CurrentValue");
            case "progress" -> Math.round(control.getDouble("Value") * 100.0D) + "%";
            case "button" -> "Tap to send";
            default -> control.getString("CurrentValue");
        };
    }

    // Get the dock services
    private static String dockServices(CompoundTag dock) {
        List<String> services = new ArrayList<>();
        if (dock.getBoolean("Refuel")) services.add("Fuel");
        if (dock.getBoolean("Restock")) services.add("Stock");
        if (dock.getBoolean("Packages")) services.add("Packages");
        return services.isEmpty() ? "No advertised services" : String.join(" / ", services);
    }

    // Format the eta
    private static String formatEta(long seconds) {
        if (seconds < 0L) return "ETA --:--";
        if (seconds == 0L) return "Arrived";
        long hours = seconds / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        long remainder = seconds % 60L;
        return hours > 0L ? "%d:%02d:%02d".formatted(hours, minutes, remainder)
                : "%02d:%02d".formatted(minutes, remainder);
    }

    // Get the compact number
    private static String compactNumber(double val) {
        return val == Math.rint(val) ? Long.toString(Math.round(val))
                : String.format(Locale.ROOT, "%.2f", val);
    }

    // Get the label
    private static String label(String id) {
        String[] words = id.split("_");
        StringBuilder res = new StringBuilder();
        for (String word : words) {
            if (!res.isEmpty()) res.append(' ');
            if (!word.isEmpty()) res.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return res.toString();
    }

    // Get the prompt
    private static String prompt(String action) {
        return switch (action) {
            case "rename_dock" -> "Dock name";
            case "dock_capabilities" -> "Advertised capabilities";
            case "add_friend", "accept_friend", "remove_friend" -> "Player name or UUID";
            case "request_items" -> "Requested item and amount";
            case "route", "navigate" -> "Destination dock or waypoint";
            case "schedule" -> "Schedule name";
            case "remove_waypoint" -> "Waypoint name";
            case "remove_landing_zone" -> "Landing zone name";
            case "enable_connector", "disable_connector", "reserve_connector", "release_connector" ->
                    "Connector name or coordinates";
            default -> label(action);
        };
    }

    // Get the placeholder
    private static String placeholder(String action) {
        return switch (action) {
            case "dock_capabilities" -> "refuel, restock, packages";
            case "request_items" -> "minecraft:iron_ingot 64";
            case "route", "navigate" -> "Dock or waypoint name";
            default -> prompt(action);
        };
    }

    // Check if the point is inside the bounds
    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    // Handle mouse scrolled
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        TabletAppDefinition app = selectedApp();
        if (isBuiltInApp(app, "settings") && "apps".equals(activeTab(app).id())
                && inside(mouseX, mouseY, contentLeft(), top + 68, contentWidth(), 167)) {
            ListTag apps = settingsData().getList("Apps", Tag.TAG_COMPOUND);
            int pageCount = Math.max(1, (apps.size() + SETTINGS_APP_PAGE_SIZE - 1)
                    / SETTINGS_APP_PAGE_SIZE);
            int dir = scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
            if (dir != 0) {
                settingsAppPage = Mth.clamp(settingsAppPage + dir, 0, pageCount - 1);
                return true;
            }
        }
        if (isBuiltInApp(app, "journey") && "search".equals(activeTab(app).id())
                && !journeyPicker.isBlank()) {
            int pickerX = "from".equals(journeyPicker) ? contentLeft() + 10 : contentLeft() + 200;
            if (inside(mouseX, mouseY, pickerX, top + 121, 180, 106)) {
                CompoundTag data = appData(app.id());
                String filter = actionInput == null
                        ? "" : actionInput.getValue().strip().toLowerCase(Locale.ROOT);
                List<String> choices = new ArrayList<>();
                appendJourneyChoices(choices, data.getList("Docks", Tag.TAG_COMPOUND), filter);
                appendJourneyChoices(choices, data.getList("TrainStations", Tag.TAG_COMPOUND), filter);
                int maximum = Math.max(0, choices.size() - 4);
                int dir = scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
                journeyPickerScroll = Mth.clamp(journeyPickerScroll + dir, 0, maximum);
                return true;
            }
        }
        if (isBuiltInApp(app, "nfc") && "inspect".equals(activeTab(app).id())
                && inside(mouseX, mouseY, contentLeft(), top + 126, contentWidth(), 112)) {
            ListTag properties = appData(app.id()).getList("Properties", Tag.TAG_COMPOUND);
            int visible = appData(app.id()).getBoolean("Controllable") ? 5 : 6;
            int dir = scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
            if (dir != 0) {
                nfcPropertyScroll = Mth.clamp(nfcPropertyScroll + dir,
                        0, Math.max(0, properties.size() - visible));
                return true;
            }
        }
        if (isBuiltInApp(app, "scm") && "logistics".equals(activeTab(app).id())
                && !scmRunTypePicker && inside(mouseX, mouseY,
                contentLeft(), top + 145, contentWidth(), 82)) {
            CompoundTag selected = selectedRow(appData(app.id()).getList(
                    "Targets", Tag.TAG_COMPOUND));
            ListTag runs = selected == null ? new ListTag()
                    : selected.getList("Runs", Tag.TAG_COMPOUND);
            int dir = scrollY > 0.0D ? -1 : scrollY < 0.0D ? 1 : 0;
            if (dir != 0) {
                scmRunScroll = Mth.clamp(scmRunScroll + dir,
                        0, Math.max(0, runs.size() - 3));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Handle key pressed
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editingAction.isBlank() && (keyCode == 257 || keyCode == 335)) {
            send(editingAction, editingValuePrefix + actionInput.getValue().trim());
            stopEditing();
            return true;
        }
        if (!editingAction.isBlank() && keyCode == 256) {
            stopEditing();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Check if this is a pause screen
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Handle the tablet overlay menu
    static final class TabletOverlayMenu extends AbstractContainerMenu {
        // Initialize the tablet overlay menu
        private TabletOverlayMenu() {
            super(null, -1);
            addSlot(new Slot(new SimpleContainer(1), 0, -10_000, -10_000) {
                // Check if this is active
                @Override
                public boolean isActive() {
                    return false;
                }

                // Check if this may pickup
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                // Check if this may place
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // Move the stack quickly
        @Override
        public ItemStack quickMoveStack(Player player, int idx) {
            return ItemStack.EMPTY;
        }

        // Check if the still is valid
        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
