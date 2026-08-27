package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.NotationDraftStore;
import com.rieno.gadgetsandgizmos.content.NotationScmModel;
import com.rieno.gadgetsandgizmos.content.advanced.NotationExpression;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedContraptionControllerGraphPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

// Edit function notation and preview the resulting graph before it is saved to the controller
public final class FunctionPlotterScreen extends Screen {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int TOOLBAR_HEIGHT = 28;
    private static final int MIN_EXPRESSIONS = 1;
    private static final int MAX_EXPRESSIONS = 30;
    private static final int EXPRESSION_ROW_HEIGHT = 34;
    private static final int EDIT_NONE = -2;
    private static final int EDIT_DRAFT_NAME = -1;
    private static final int[] SERIES_COLORS = {
            0xFF25C6D8, 0xFFF3A72F, 0xFF66D17A, 0xFFE06BDD,
            0xFFFF6B6B, 0xFF8EA7FF, 0xFFE2D66B, 0xFFB985F4
    };
    private static final DateTimeFormatter DRAFT_TIME =
            DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.systemDefault());

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Parent function plotter
    private final AdvancedContraptionControllerScreen parent;
    // Function plotter target
    private final MenuConfigTarget target;
    // Tracked expression values
    private final List<String> expressionValues = new ArrayList<>();
    // Tracked stored drafts
    private final List<NotationDraftStore.Summary> storedDrafts = new ArrayList<>();

    // Current inline editor
    private EditBox inlineEditor;
    // Current program
    private NotationExpression.Program program;
    // Current SCM model
    private NotationScmModel scmModel = NotationScmModel.empty();
    // Output mode
    private NotationScmModel.OutputMode outputMode = NotationScmModel.OutputMode.FORCE_MAGNITUDE;
    // Draft id
    private String draftId = "";
    // Draft name
    private String draftName = "";
    // Current function plotter status
    private String status = "";
    // Current value before edit
    private String valueBeforeEdit = "";
    // Tracks whether status error is set
    private boolean statusError;
    // Controls whether to use SCM
    private boolean useScm;
    // Tracks whether function plotter is dirty
    private boolean dirty;
    // Tracks whether before edit is dirty
    private boolean dirtyBeforeEdit;
    // Tracks whether server data is requested
    private boolean requestedServerData;
    // Tracks whether load is open
    private boolean loadOpen;
    // Tracks whether view is initialized
    private boolean viewInitialized;
    // Tracks whether inline editor is being synced
    private boolean syncingInlineEditor;
    // Current editing field
    private int editingField = EDIT_NONE;
    // Current expression scroll
    private int expressionScroll;
    // Current load scroll
    private int loadScroll;
    // Current center x
    private double centerX;
    // Current center y
    private double centerY;
    // Current units per pixel x
    private double unitsPerPixelX = 1.0D;
    // Current units per pixel y
    private double unitsPerPixelY = 1.0D;
    // Tracks whether function plotter is panning
    private boolean panning;
    // Last mouse x
    private double lastMouseX;
    // Last mouse y
    private double lastMouseY;
    // Current cursor world x
    private double cursorWorldX;
    // Current cursor world y
    private double cursorWorldY;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the function plotter
    public FunctionPlotterScreen(AdvancedContraptionControllerScreen parent, MenuConfigTarget target) {
        super(Component.literal("Function Plotter"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.target = Objects.requireNonNull(target, "target");
        expressionValues.add("");
        rebuildProgram();
    }

    // Initialize the function plotter
    @Override
    protected void init() {
        if (!viewInitialized) initView();
        inlineEditor = new EditBox(font, 0, 0, 32, 16, Component.literal("Function expression"));
        inlineEditor.setBordered(false);
        inlineEditor.setTextColor(primaryTextColor());
        inlineEditor.setTextColorUneditable(mutedTextColor());
        inlineEditor.setVisible(false);
        inlineEditor.setResponder(this::updateInlineValue);
        addRenderableWidget(inlineEditor);
        if (editingField != EDIT_NONE) layoutInlineEditor(true);

        if (!requestedServerData) {
            requestedServerData = true;
            request("notation_open", new CompoundTag(), "");
            setStatus("Loading saved drafts and SCM calibration...", false);
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the background
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the function plotter
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        cursorWorldX = worldX(mouseX);
        cursorWorldY = worldY(mouseY);
        graphics.fill(0, 0, width, height, canvasColor());
        drawPlot(graphics, mouseX, mouseY);
        drawSidebar(graphics, mouseX, mouseY);
        drawToolbar(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (loadOpen) drawLoadBrowser(graphics, mouseX, mouseY);
    }

    // Draw the toolbar
    private void drawToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        parent.renderTitleBar(graphics, 0, 0, width, TOOLBAR_HEIGHT);
        for (ActionButton btn : toolbarButtons()) {
            parent.renderAdvancedButton(graphics, font,
                    btn.bounds.x, btn.bounds.y, btn.bounds.width, btn.bounds.height,
                    btn.label, btn.bounds.contains(mouseX, mouseY), btn.active);
        }
        int occupied = toolbarButtons().stream().mapToInt(btn -> btn.bounds.right()).max().orElse(0);
        String heading = "Function Plotter";
        if (width - occupied > font.width(heading) + 24) {
            graphics.drawString(font, heading, occupied + 12, 10, secondaryTextColor(), false);
        }
    }

    // Draw the sidebar
    private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        // -----------------------------------------------------DRAFT HEADER-----------------------------------------------------
        int sidebar = sidebarWidth();
        parent.renderSidebarPanel(graphics, 0, TOOLBAR_HEIGHT, sidebar, height - TOOLBAR_HEIGHT);
        graphics.fill(sidebar - 1, TOOLBAR_HEIGHT, sidebar, height, borderColor());
        graphics.drawString(font, "FUNCTIONS", 8, TOOLBAR_HEIGHT + 8, mutedTextColor(), false);

        Rect draftBounds = draftNameBounds();
        boolean draftHovered = draftBounds.contains(mouseX, mouseY);
        parent.renderControllerOption(graphics, draftBounds.x, draftBounds.y,
                draftBounds.width, draftBounds.height, SERIES_COLORS[0],
                editingField == EDIT_DRAFT_NAME || draftHovered);
        if (editingField != EDIT_DRAFT_NAME) {
            boolean unnamed = draftName.isBlank();
            String shownName = unnamed ? "Function name..." : draftName;
            graphics.drawString(font, trim(shownName, Math.max(8, (draftBounds.width - 14) / 6)),
                    draftBounds.x + 8, draftBounds.y + 6,
                    unnamed ? mutedTextColor() : primaryTextColor(), false);
        }

        // -----------------------------------------------------EXPRESSIONS-----------------------------------------------------
        Rect listBounds = expressionListBounds();
        int visibleRows = expressionVisibleRows();
        expressionScroll = Mth.clamp(expressionScroll, 0, maxExpressionScroll());
        graphics.enableScissor(listBounds.x, listBounds.y, listBounds.right(), listBounds.bottom());
        NotationExpression.LineResult hoveredError = null;
        int lastVisible = Math.min(expressionValues.size(), expressionScroll + visibleRows);
        for (int idx = expressionScroll; idx < lastVisible; idx++) {
            Rect bounds = expressionBounds(idx);
            boolean hovered = bounds.contains(mouseX, mouseY);
            parent.renderControllerOption(graphics, bounds.x, bounds.y,
                    bounds.width, bounds.height, SERIES_COLORS[idx % SERIES_COLORS.length],
                    editingField == idx || hovered);
            graphics.drawCenteredString(font, Integer.toString(idx + 1),
                    bounds.x + 16, bounds.y + (bounds.height - 8) / 2, secondaryTextColor());
            NotationExpression.LineResult res = program.lines().get(idx);
            if (editingField != idx) {
                String src = expressionValues.get(idx);
                String shown = src.isBlank() ? "Click to add expression..." : src;
                int textColor = src.isBlank() ? mutedTextColor() : primaryTextColor();
                int sourceX = bounds.x + 34;
                int sourceY = bounds.y + 6;
                int sourceWidth = Math.max(1, bounds.width - 68);
                String visibleSource = font.plainSubstrByWidth(shown, sourceWidth);
                graphics.drawString(font, visibleSource, sourceX, sourceY, textColor, false);
                if (!res.valid() && res.errorColumn() >= 0) {
                    int start = Math.min(res.safeErrorColumn(), visibleSource.length());
                    int end = Math.min(visibleSource.length(), start + res.safeErrorLength());
                    if (start < visibleSource.length()) {
                        int underlineX = sourceX + font.width(visibleSource.substring(0, start));
                        int underlineWidth = Math.max(1, font.width(visibleSource.substring(
                                start, Math.max(start + 1, end))));
                        graphics.fill(underlineX, sourceY + font.lineHeight,
                                underlineX + underlineWidth, sourceY + font.lineHeight + 2, errorColor());
                    } else {
                        graphics.fill(bounds.right() - 28, bounds.y + 3,
                                bounds.right() - 26, bounds.bottom() - 3, errorColor());
                    }
                }
                if (res.valid() && !src.isBlank() && !program.plotted(idx)) {
                    try {
                        String val = "= " + compact(program.evaluateLine(idx, 0.0D));
                        graphics.drawString(font, trim(val, Math.max(8, (bounds.width - 78) / 6)),
                                bounds.x + 34, bounds.y + bounds.height - 10, mutedTextColor(), false);
                    } catch (NotationExpression.EvaluationException ignored) {
                    }
                }
            }
            if (!res.valid()) {
                graphics.fill(bounds.right() - 28, bounds.y + 3,
                        bounds.right() - 26, bounds.bottom() - 3, errorColor());
                if (hovered) hoveredError = res;
            }
            Rect remove = removeExpressionBounds(idx);
            parent.renderAdvancedButton(graphics, font, remove.x, remove.y, remove.width, remove.height,
                    Component.literal("-"), remove.contains(mouseX, mouseY), expressionValues.size() > MIN_EXPRESSIONS);
        }
        graphics.disableScissor();

        // -----------------------------------------------------SYNTAX HELP-----------------------------------------------------
        int helpTop = expressionBounds(expressionValues.size() - 1).bottom() + 10;
        if (expressionScroll == 0 && helpTop < listBounds.bottom() - 38) {
            graphics.drawString(font, "Implicit multiply: 2x, xy, 2(x + 1)", 8, helpTop,
                    mutedTextColor(), false);
            graphics.drawString(font, "Parametric: x = cos(t), y = sin(t)", 8, helpTop + 12,
                    mutedTextColor(), false);
            graphics.drawString(font, "Range: t_min = 0, t_max = tau", 8, helpTop + 24,
                    mutedTextColor(), false);
        }
        // -----------------------------------------------------LIST CONTROLS-----------------------------------------------------
        Rect add = addExpressionBounds();
        parent.renderAdvancedButton(graphics, font, add.x, add.y, add.width, add.height,
                Component.literal("+ Add expression"), add.contains(mouseX, mouseY),
                expressionValues.size() < MAX_EXPRESSIONS);
        String count = expressionValues.size() + " / " + MAX_EXPRESSIONS;
        graphics.drawString(font, count, add.right() + 8, add.y + 6, mutedTextColor(), false);
        drawExpressionScrollbar(graphics, listBounds, visibleRows);
        // -----------------------------------------------------STATUS-----------------------------------------------------
        String footer = statusLine();
        boolean expressionError = program.lines().stream().anyMatch(line -> !line.valid());
        int footerColor = statusError || expressionError ? errorColor() : accentTextColor();
        graphics.drawString(font, trim(footer, Math.max(20, (sidebar - 16) / 6)),
                8, height - 14, footerColor, false);
        if (hoveredError != null) {
            List<FormattedCharSequence> tooltip = new ArrayList<>();
            String location = "Line " + (hoveredError.lineIndex() + 1);
            if (hoveredError.errorColumn() >= 0) {
                location += ", column " + (hoveredError.safeErrorColumn() + 1);
            }
            tooltip.add(Component.literal(location).getVisualOrderText());
            tooltip.add(Component.literal(hoveredError.error()).getVisualOrderText());
            graphics.renderTooltip(font, tooltip, mouseX, mouseY);
        } else if (new Rect(6, height - 18, Math.max(1, sidebar - 12), 16).contains(mouseX, mouseY)
                && !footer.isBlank()) {
            graphics.renderTooltip(font, Component.literal(footer), mouseX, mouseY);
        }
    }

    // Draw the expression scrollbar
    private void drawExpressionScrollbar(GuiGraphics graphics, Rect listBounds, int visibleRows) {
        int maximum = maxExpressionScroll();
        if (maximum <= 0) return;
        int trackX = sidebarWidth() - 5;
        graphics.fill(trackX, listBounds.y, trackX + 3, listBounds.bottom(), borderColor());
        int thumbHeight = Math.max(16, listBounds.height * visibleRows / expressionValues.size());
        int travel = Math.max(0, listBounds.height - thumbHeight);
        int thumbY = listBounds.y + travel * expressionScroll / maximum;
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, accentTextColor());
    }

    // Draw the plot
    private void drawPlot(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = sidebarWidth();
        if (left >= width || TOOLBAR_HEIGHT >= height) return;
        graphics.enableScissor(left, TOOLBAR_HEIGHT, width, height);
        graphics.fill(left, TOOLBAR_HEIGHT, width, height, canvasColor());
        drawGrid(graphics, left);
        drawSeries(graphics, left);
        graphics.disableScissor();

        if (mouseX >= left && mouseY >= TOOLBAR_HEIGHT) {
            String coordinates = "x=" + compact(cursorWorldX) + "  y=" + compact(cursorWorldY);
            int panel = v2Ui() ? AdvancedControllerV2Theme.PANEL_BACKGROUND : 0xDD101820;
            graphics.fill(left + 6, height - 22, left + 12 + font.width(coordinates), height - 6, panel);
            graphics.drawString(font, coordinates, left + 9, height - 18, secondaryTextColor(), false);
        }
        if (useScm) {
            String model = scmModel.available()
                    ? "SCM / Sable: " + outputMode.label() + "  |  " + scmModel.unitCount()
                    + " units, " + scmModel.bearingCount() + " bearings, " + scmModel.surfaceCount() + " surfaces"
                    : "SCM / Sable: unavailable";
            graphics.drawString(font, model, left + 8, TOOLBAR_HEIGHT + 7,
                    scmModel.available() ? accentTextColor() : errorColor(), false);
        }
        for (ActionButton btn : plotButtons()) {
            parent.renderAdvancedButton(graphics, font,
                    btn.bounds.x, btn.bounds.y, btn.bounds.width, btn.bounds.height,
                    btn.label, btn.bounds.contains(mouseX, mouseY), btn.active);
        }
    }

    // Draw the grid
    private void drawGrid(GuiGraphics graphics, int left) {
        double visibleWidth = Math.max(1, width - left);
        double visibleHeight = Math.max(1, height - TOOLBAR_HEIGHT);
        double xMinimum = centerX - visibleWidth * 0.5D * unitsPerPixelX;
        double xMaximum = centerX + visibleWidth * 0.5D * unitsPerPixelX;
        double yMinimum = centerY - visibleHeight * 0.5D * unitsPerPixelY;
        double yMaximum = centerY + visibleHeight * 0.5D * unitsPerPixelY;
        double xStep = niceStep(unitsPerPixelX * 72.0D);
        double yStep = niceStep(unitsPerPixelY * 54.0D);
        int grid = v2Ui() ? AdvancedControllerV2Theme.BORDER_SOFT : 0xFF26303A;
        int axisColor = v2Ui() ? AdvancedControllerV2Theme.BORDER_STRONG : 0xFFB8C2C9;

        for (double x = Math.ceil(xMinimum / xStep) * xStep; x <= xMaximum + xStep * 0.01D; x += xStep) {
            int screenX = screenX(x);
            boolean axis = Math.abs(x) < xStep * 1.0E-6D;
            graphics.fill(screenX, TOOLBAR_HEIGHT, screenX + (axis ? 2 : 1), height,
                    axis ? axisColor : grid);
            if (!axis || screenX > left + 18) {
                graphics.drawCenteredString(font, compact(x), screenX, screenY(0.0D) + 4,
                        mutedTextColor());
            }
        }
        for (double y = Math.ceil(yMinimum / yStep) * yStep; y <= yMaximum + yStep * 0.01D; y += yStep) {
            int screenY = screenY(y);
            boolean axis = Math.abs(y) < yStep * 1.0E-6D;
            graphics.fill(left, screenY, width, screenY + (axis ? 2 : 1),
                    axis ? axisColor : grid);
            if (!axis || screenY > TOOLBAR_HEIGHT + 14) {
                int axisX = Mth.clamp(screenX(0.0D) + 4, left + 4, width - 48);
                graphics.drawString(font, compact(y), axisX, screenY + 3, mutedTextColor(), false);
            }
        }
    }

    // Draw the series
    private void drawSeries(GuiGraphics graphics, int left) {
        for (NotationExpression.Series series : program.series()) {
            int col = SERIES_COLORS[series.lineIndex() % SERIES_COLORS.length];
            if (series.parametric()) drawParametricSeries(graphics, series, col, left);
            else if (series.implicit()) drawImplicitSeries(graphics, series, col, left);
            else drawCartesianSeries(graphics, series, col, left);
        }
    }

    // Draw the cartesian series
    private void drawCartesianSeries(GuiGraphics graphics, NotationExpression.Series series,
                                     int col, int left) {
        boolean previousValid = false;
        int previousX = 0;
        int previousY = 0;
        for (int sampleX = left; sampleX < width; sampleX++) {
            NotationExpression.Point point;
            try {
                point = program.evaluatePoint(series, worldX(sampleX));
                if (useScm && scmModel.available()) {
                    point = new NotationExpression.Point(point.x(), scmModel.evaluate(outputMode, point.y()));
                }
            } catch (NotationExpression.EvaluationException err) {
                previousValid = false;
                continue;
            }
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                previousValid = false;
                continue;
            }
            int plottedX = screenX(point.x());
            int plottedY = screenY(point.y());
            boolean visible = visibleSeriesPoint(plottedX, plottedY, left);
            if (previousValid && visible
                    && Math.abs(plottedY - previousY) < Math.max(64, height / 2)) {
                drawLine(graphics, previousX, previousY, plottedX, plottedY, col);
            }
            previousValid = visible;
            previousX = plottedX;
            previousY = plottedY;
        }
    }

    // Draw the parametric series
    private void drawParametricSeries(GuiGraphics graphics, NotationExpression.Series series,
                                      int col, int left) {
        double minimum = program.parameterMinimum(series);
        double maximum = program.parameterMaximum(series);
        int samples = Math.max(256, Math.max(1, width - left) * 2);
        boolean previousValid = false;
        int previousX = 0;
        int previousY = 0;
        for (int idx = 0; idx <= samples; idx++) {
            double parameter = minimum + (maximum - minimum) * idx / samples;
            NotationExpression.Point point;
            try {
                point = program.evaluatePoint(series, parameter);
                if (useScm && scmModel.available()) {
                    point = new NotationExpression.Point(
                            scmModel.evaluate(outputMode, point.x()),
                            scmModel.evaluate(outputMode, point.y()));
                }
            } catch (NotationExpression.EvaluationException err) {
                previousValid = false;
                continue;
            }
            if (!Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                previousValid = false;
                continue;
            }
            int plottedX = screenX(point.x());
            int plottedY = screenY(point.y());
            boolean visible = visibleSeriesPoint(plottedX, plottedY, left);
            int jumpLimit = Math.max(96, Math.max(width - left, height) / 2);
            if (previousValid && visible
                    && Math.abs(plottedX - previousX) < jumpLimit
                    && Math.abs(plottedY - previousY) < jumpLimit) {
                drawLine(graphics, previousX, previousY, plottedX, plottedY, col);
            }
            previousValid = visible;
            previousX = plottedX;
            previousY = plottedY;
        }
    }

    // Draw the implicit series
    private void drawImplicitSeries(GuiGraphics graphics, NotationExpression.Series series,
                                    int col, int left) {
        int step = 8;
        int columns = Math.max(1, (width - left + step - 1) / step);
        int rows = Math.max(1, (height - TOOLBAR_HEIGHT + step - 1) / step);
        double[][] values = new double[columns + 1][rows + 1];
        for (int column = 0; column <= columns; column++) {
            int sampleX = Math.min(width, left + column * step);
            for (int row = 0; row <= rows; row++) {
                int sampleY = Math.min(height, TOOLBAR_HEIGHT + row * step);
                try {
                    double val = program.evaluateImplicit(series, worldX(sampleX), worldY(sampleY));
                    values[column][row] = useScm && scmModel.available()
                            ? scmModel.evaluate(outputMode, val) : val;
                } catch (NotationExpression.EvaluationException err) {
                    values[column][row] = Double.NaN;
                }
            }
        }
        int[] crossingX = new int[4];
        int[] crossingY = new int[4];
        for (int column = 0; column < columns; column++) {
            int x0 = left + column * step;
            int x1 = Math.min(width, x0 + step);
            for (int row = 0; row < rows; row++) {
                int y0 = TOOLBAR_HEIGHT + row * step;
                int y1 = Math.min(height, y0 + step);
                double topLeft = values[column][row];
                double topRight = values[column + 1][row];
                double bottomLeft = values[column][row + 1];
                double bottomRight = values[column + 1][row + 1];
                int crossingCount = 0;
                crossingCount = addContourCrossing(crossingX, crossingY, crossingCount,
                        x0, y0, topLeft, x1, y0, topRight);
                crossingCount = addContourCrossing(crossingX, crossingY, crossingCount,
                        x1, y0, topRight, x1, y1, bottomRight);
                crossingCount = addContourCrossing(crossingX, crossingY, crossingCount,
                        x0, y1, bottomLeft, x1, y1, bottomRight);
                crossingCount = addContourCrossing(crossingX, crossingY, crossingCount,
                        x0, y0, topLeft, x0, y1, bottomLeft);
                if (crossingCount >= 2) {
                    drawLine(graphics, crossingX[0], crossingY[0], crossingX[1], crossingY[1], col);
                }
                if (crossingCount >= 4) {
                    drawLine(graphics, crossingX[2], crossingY[2], crossingX[3], crossingY[3], col);
                }
            }
        }
    }

    // Add the contour crossing
    private static int addContourCrossing(int[] crossingX, int[] crossingY, int count,
                                          int x0, int y0, double value0,
                                          int x1, int y1, double value1) {
        if (!Double.isFinite(value0) || !Double.isFinite(value1)) return count;
        if (value0 != 0.0D && value1 != 0.0D && Math.signum(value0) == Math.signum(value1)) return count;
        double denominator = Math.abs(value0) + Math.abs(value1);
        double amount = denominator <= 1.0E-12D ? 0.5D : Math.abs(value0) / denominator;
        int x = (int) Math.round(x0 + (x1 - x0) * amount);
        int y = (int) Math.round(y0 + (y1 - y0) * amount);
        for (int idx = 0; idx < count; idx++) {
            if (crossingX[idx] == x && crossingY[idx] == y) return count;
        }
        if (count < crossingX.length) {
            crossingX[count] = x;
            crossingY[count] = y;
            count++;
        }
        return count;
    }

    // Check if this is visible series point
    private boolean visibleSeriesPoint(int plottedX, int plottedY, int left) {
        int plotWidth = Math.max(1, width - left);
        return plottedX > left - plotWidth * 2 && plottedX < width + plotWidth * 2
                && plottedY > TOOLBAR_HEIGHT - height * 2 && plottedY < height * 3;
    }

    // Draw the load browser
    private void drawLoadBrowser(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect browser = loadBrowserBounds();
        parent.renderAdvancedPanel(graphics, browser.x, browser.y, browser.width, browser.height);
        graphics.drawString(font, "Saved function drafts", browser.x + 8, browser.y + 8,
                primaryTextColor(), false);
        graphics.drawString(font, "Left-click to load, right-click to delete",
                browser.x + 8, browser.y + 20, mutedTextColor(), false);
        int visible = loadVisibleRows();
        int maximum = Math.max(0, storedDrafts.size() - visible);
        loadScroll = Mth.clamp(loadScroll, 0, maximum);
        for (int row = 0; row < visible; row++) {
            int idx = loadScroll + row;
            if (idx >= storedDrafts.size()) break;
            NotationDraftStore.Summary summary = storedDrafts.get(idx);
            Rect rowBounds = loadRowBounds(row);
            boolean hovered = rowBounds.contains(mouseX, mouseY);
            parent.renderControllerOption(graphics, rowBounds.x, rowBounds.y,
                    rowBounds.width, rowBounds.height, SERIES_COLORS[idx % SERIES_COLORS.length], hovered);
            graphics.drawString(font, trim(summary.name(), 34), rowBounds.x + 8, rowBounds.y + 4,
                    primaryTextColor(), false);
            graphics.drawString(font, DRAFT_TIME.format(Instant.ofEpochMilli(summary.updatedAt())),
                    rowBounds.x + 8, rowBounds.y + 15, mutedTextColor(), false);
        }
        if (storedDrafts.isEmpty()) {
            graphics.drawCenteredString(font, "No saved function drafts",
                    browser.x + browser.width / 2, browser.y + 58, mutedTextColor());
        }
    }

    // Handle mouse clicked
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int btn) {
        if (loadOpen) return clickLoadBrowser(mouseX, mouseY, btn);

        if (editingField != EDIT_NONE && inlineEditor != null
                && inlineEditor.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, btn);
        }
        if (editingField != EDIT_NONE) finishInlineEdit(false);

        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (ActionButton action : toolbarButtons()) {
                if (action.bounds.contains(mouseX, mouseY)) {
                    handleAction(action);
                    return true;
                }
            }
            for (ActionButton action : plotButtons()) {
                if (action.bounds.contains(mouseX, mouseY)) {
                    handleAction(action);
                    return true;
                }
            }
            if (draftNameBounds().contains(mouseX, mouseY)) {
                beginInlineEdit(EDIT_DRAFT_NAME);
                return true;
            }
            if (addExpressionBounds().contains(mouseX, mouseY)) {
                addExpression();
                return true;
            }
            int lastVisible = Math.min(expressionValues.size(), expressionScroll + expressionVisibleRows());
            for (int idx = expressionScroll; idx < lastVisible; idx++) {
                if (removeExpressionBounds(idx).contains(mouseX, mouseY)) {
                    removeExpression(idx);
                    return true;
                }
                if (expressionBounds(idx).contains(mouseX, mouseY)) {
                    beginInlineEdit(idx);
                    return true;
                }
            }
        }
        if (mouseX >= sidebarWidth() && mouseY >= TOOLBAR_HEIGHT
                && (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT || btn == GLFW.GLFW_MOUSE_BUTTON_MIDDLE)) {
            panning = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, btn);
    }

    // Handle the load browser click
    private boolean clickLoadBrowser(double mouseX, double mouseY, int btn) {
        Rect browser = loadBrowserBounds();
        if (!browser.contains(mouseX, mouseY)) {
            loadOpen = false;
            return true;
        }
        for (int row = 0; row < loadVisibleRows(); row++) {
            if (!loadRowBounds(row).contains(mouseX, mouseY)) continue;
            int idx = loadScroll + row;
            if (idx >= storedDrafts.size()) return true;
            NotationDraftStore.Summary summary = storedDrafts.get(idx);
            if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                request("notation_delete", new CompoundTag(), summary.id());
            } else if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                request("notation_load", new CompoundTag(), summary.id());
                setStatus("Loading '" + summary.name() + "'...", false);
            }
            return true;
        }
        return true;
    }

    // Handle mouse dragged
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int btn, double dragX, double dragY) {
        if (panning) {
            centerX -= (mouseX - lastMouseX) * unitsPerPixelX;
            centerY += (mouseY - lastMouseY) * unitsPerPixelY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            dirty = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, btn, dragX, dragY);
    }

    // Handle mouse released
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int btn) {
        if (panning) {
            panning = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, btn);
    }

    // Handle mouse scrolled
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (loadOpen) {
            loadScroll = Mth.clamp(loadScroll - (int) Math.signum(scrollY),
                    0, Math.max(0, storedDrafts.size() - loadVisibleRows()));
            return true;
        }
        if (mouseX >= sidebarWidth() && mouseY >= TOOLBAR_HEIGHT) {
            zoomAt(Math.pow(1.15D, -scrollY), mouseX, mouseY);
            return true;
        }
        if (expressionListBounds().contains(mouseX, mouseY)) {
            finishInlineEdit(false);
            int amount = Math.max(1, (int) Math.round(Math.abs(scrollY)));
            expressionScroll = Mth.clamp(expressionScroll - (int) Math.signum(scrollY) * amount,
                    0, maxExpressionScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // Handle key pressed
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && loadOpen) {
            loadOpen = false;
            return true;
        }
        if (editingField != EDIT_NONE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishInlineEdit(true);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                finishInlineEdit(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                int next = editingField == EDIT_DRAFT_NAME ? 0
                        : editingField >= expressionValues.size() - 1 ? EDIT_DRAFT_NAME : editingField + 1;
                finishInlineEdit(false);
                beginInlineEdit(next);
                return true;
            }
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_S) {
            saveDraft();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // Handle the close event
    @Override
    public void onClose() {
        finishInlineEdit(false);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // Check if this is a pause screen
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Apply the server data
    public static void applyServerData(BlockPos pos, UUID subLevelId, String action,
                                       boolean success, String msg,
                                       List<NotationDraftStore.Summary> drafts,
                                       CompoundTag draft, CompoundTag scmModel) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof FunctionPlotterScreen screen
                && screen.target.pos().equals(pos)
                && Objects.equals(screen.target.subLevelId(), subLevelId)) {
            screen.applyServerData(action, success, msg, drafts, draft, scmModel);
        }
        AccDisplayGuiProjection.applyFunctionPlotterData(
                pos, subLevelId, action, success, msg, drafts, draft, scmModel);
    }

    // Apply the server data
    void applyServerData(String action, boolean success, String msg,
                         List<NotationDraftStore.Summary> drafts,
                         CompoundTag draft, CompoundTag scmModel) {
        storedDrafts.clear();
        if (drafts != null) storedDrafts.addAll(drafts);
        if (scmModel != null && !scmModel.isEmpty()) {
            this.scmModel = NotationScmModel.fromTag(scmModel);
            if (!this.scmModel.available()) useScm = false;
        }
        if (draft != null && !draft.isEmpty() && ("load".equals(action) || "save".equals(action))) {
            applyDraft(draft);
            loadOpen = false;
        }
        if (msg != null && !msg.isBlank()) setStatus(msg, !success);
        else if ("open".equals(action)) {
            setStatus(this.scmModel.available()
                    ? "SCM calibration ready" : "Normal plotting ready; no stored SCM calibration", false);
        }
    }

    // Draw the projection
    void renderProjection(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics, mouseX, mouseY, partialTick);
    }

    // Get the projection target
    MenuConfigTarget projectionTarget() {
        return target;
    }

    // Run the selected plotter action
    private void handleAction(ActionButton btn) {
        if (!btn.active) return;
        switch (btn.action) {
            case BACK -> onClose();
            case SAVE -> saveDraft();
            case LOAD -> {
                loadOpen = !loadOpen;
                loadScroll = 0;
            }
            case CONVERT -> convert();
            case SCM -> {
                if (!scmModel.available()) {
                    setStatus("No stored SCM calibration is available for this controller", true);
                    return;
                }
                useScm = !useScm;
                dirty = true;
            }
            case OUTPUT -> {
                outputMode = outputMode.next();
                dirty = true;
            }
            case HOME -> resetView();
            case ZOOM_IN -> zoomAt(0.75D,
                    (sidebarWidth() + width) * 0.5D, (TOOLBAR_HEIGHT + height) * 0.5D);
            case ZOOM_OUT -> zoomAt(1.0D / 0.75D,
                    (sidebarWidth() + width) * 0.5D, (TOOLBAR_HEIGHT + height) * 0.5D);
        }
    }

    // Save the draft
    private void saveDraft() {
        captureInlineEditor();
        request("notation_save", serializeDraft(), draftId);
        setStatus("Saving function draft...", false);
    }

    // Convert the function plotter
    private void convert() {
        captureInlineEditor();
        NotationExpression.LineResult invalidLine = program.lines().stream()
                .filter(line -> !line.valid()).findFirst().orElse(null);
        if (invalidLine != null) {
            setStatus("Line " + (invalidLine.lineIndex() + 1) + ": " + invalidLine.error(), true);
            return;
        }
        NotationExpression.Series targetSeries = program.finalSeries();
        if (targetSeries == null) {
            setStatus("Enter a valid expression before converting", true);
            return;
        }
        if (useScm && !scmModel.available()) {
            setStatus("SCM conversion requires a stored calibration map", true);
            return;
        }
        String functionName = draftName.isBlank() ? targetSeries.label() : draftName;
        List<NotationExpression.CurveSample> curve = useScm ? scmModel.curve(outputMode) : List.of();
        NotationExpression.CompileResult res = program.compile(targetSeries, functionName, curve);
        if (!res.successful()) {
            setStatus(res.error(), true);
            return;
        }
        String importResult = parent.importPlottedFunction(res.function());
        if (!importResult.isBlank()) {
            setStatus(importResult, true);
            return;
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    // Request the function plotter
    private void request(String action, CompoundTag data, String argument) {
        PacketDistributor.sendToServer(new AdvancedContraptionControllerGraphPayload(
                target, action, 0, data == null ? new CompoundTag() : data,
                argument == null ? "" : argument, 0L));
    }

    // Get the serialize draft
    private CompoundTag serializeDraft() {
        captureInlineEditor();
        CompoundTag tag = new CompoundTag();
        if (!draftId.isBlank()) tag.putString("Id", draftId);
        tag.putString("Name", draftName == null ? "" : draftName.strip());
        ListTag expressions = new ListTag();
        expressionValues.forEach(val -> expressions.add(StringTag.valueOf(val == null ? "" : val)));
        tag.put("Expressions", expressions);
        tag.putInt("ExpressionCount", expressionValues.size());
        tag.putBoolean("UseScm", useScm);
        tag.putString("OutputMode", outputMode.id());
        tag.putDouble("CenterX", centerX);
        tag.putDouble("CenterY", centerY);
        tag.putDouble("UnitsPerPixelX", unitsPerPixelX);
        tag.putDouble("UnitsPerPixelY", unitsPerPixelY);
        return tag;
    }

    // Apply the draft
    private void applyDraft(CompoundTag tag) {
        finishInlineEdit(false);
        draftId = tag.getString("Id");
        draftName = tag.getString("Name");
        if (draftName.isBlank()) draftName = "Untitled function";
        ListTag expressions = tag.getList("Expressions", Tag.TAG_STRING);
        int expressionCount = tag.contains("ExpressionCount", Tag.TAG_ANY_NUMERIC)
                ? Mth.clamp(tag.getInt("ExpressionCount"), MIN_EXPRESSIONS, MAX_EXPRESSIONS)
                : legacyExpressionCount(expressions);
        expressionValues.clear();
        for (int idx = 0; idx < expressionCount; idx++) {
            expressionValues.add(idx < expressions.size() ? expressions.getString(idx) : "");
        }
        expressionScroll = 0;
        useScm = tag.getBoolean("UseScm") && scmModel.available();
        outputMode = NotationScmModel.OutputMode.fromId(tag.getString("OutputMode"));
        centerX = tag.contains("CenterX", Tag.TAG_ANY_NUMERIC)
                ? finite(tag.getDouble("CenterX"), 0.0D) : 0.0D;
        centerY = tag.contains("CenterY", Tag.TAG_ANY_NUMERIC)
                ? finite(tag.getDouble("CenterY"), 0.0D) : 0.0D;
        double storedHorizontalScale = tag.contains("UnitsPerPixelX", Tag.TAG_ANY_NUMERIC)
                ? clampScale(tag.getDouble("UnitsPerPixelX")) : defaultScale();
        double storedVerticalScale = tag.contains("UnitsPerPixelY", Tag.TAG_ANY_NUMERIC)
                ? clampScale(tag.getDouble("UnitsPerPixelY")) : defaultScale();
        unitsPerPixelX = Math.max(storedHorizontalScale, storedVerticalScale);
        unitsPerPixelY = unitsPerPixelX;
        viewInitialized = true;
        dirty = false;
        rebuildProgram();
    }

    // Add the expression
    private void addExpression() {
        if (expressionValues.size() >= MAX_EXPRESSIONS) return;
        finishInlineEdit(false);
        expressionValues.add("");
        dirty = true;
        rebuildProgram();
        int added = expressionValues.size() - 1;
        ensureExpressionVisible(added);
        beginInlineEdit(added);
    }

    // Remove the expression
    private void removeExpression(int idx) {
        if (expressionValues.size() <= MIN_EXPRESSIONS
                || idx < 0 || idx >= expressionValues.size()) return;
        finishInlineEdit(false);
        expressionValues.remove(idx);
        expressionScroll = Mth.clamp(expressionScroll, 0, maxExpressionScroll());
        dirty = true;
        rebuildProgram();
    }

    // Get the legacy expression count
    private static int legacyExpressionCount(ListTag expressions) {
        int count = Math.min(expressions.size(), MAX_EXPRESSIONS);
        while (count > MIN_EXPRESSIONS && expressions.getString(count - 1).isBlank()) count--;
        return Math.max(MIN_EXPRESSIONS, count);
    }

    // Ensure the expression visible
    private void ensureExpressionVisible(int idx) {
        int visibleRows = expressionVisibleRows();
        if (idx < expressionScroll) expressionScroll = idx;
        if (idx >= expressionScroll + visibleRows) expressionScroll = idx - visibleRows + 1;
        expressionScroll = Mth.clamp(expressionScroll, 0, maxExpressionScroll());
    }

    // Begin the inline edit
    private void beginInlineEdit(int field) {
        if (field < EDIT_DRAFT_NAME || field >= expressionValues.size()) return;
        finishInlineEdit(false);
        if (field >= 0) ensureExpressionVisible(field);
        editingField = field;
        valueBeforeEdit = field == EDIT_DRAFT_NAME ? draftName : expressionValues.get(field);
        dirtyBeforeEdit = dirty;
        layoutInlineEditor(true);
        if (field >= 0 && inlineEditor != null) {
            NotationExpression.LineResult result = program.lines().get(field);
            if (!result.valid() && result.errorColumn() >= 0) {
                inlineEditor.setCursorPosition(Math.min(result.safeErrorColumn(), inlineEditor.getValue().length()));
            }
        }
    }

    // Lay out the inline editor
    private void layoutInlineEditor(boolean focus) {
        if (inlineEditor == null || editingField == EDIT_NONE) return;
        Rect bounds = inlineEditorBounds(editingField);
        inlineEditor.setX(bounds.x);
        inlineEditor.setY(bounds.y);
        inlineEditor.setWidth(bounds.width);
        inlineEditor.setHeight(bounds.height);
        inlineEditor.setMaxLength(editingField == EDIT_DRAFT_NAME ? 64 : 512);
        inlineEditor.setHint(editingField == EDIT_DRAFT_NAME
                ? Component.literal("Function name...") : Component.empty());
        inlineEditor.setTextColor(primaryTextColor());
        inlineEditor.setTextColorUneditable(mutedTextColor());
        syncingInlineEditor = true;
        inlineEditor.setValue(editingField == EDIT_DRAFT_NAME
                ? draftName : expressionValues.get(editingField));
        syncingInlineEditor = false;
        inlineEditor.setVisible(true);
        if (focus) {
            inlineEditor.setFocused(true);
            inlineEditor.moveCursorToEnd(false);
            setFocused(inlineEditor);
        }
    }

    // Update the inline value
    private void updateInlineValue(String val) {
        if (syncingInlineEditor || editingField == EDIT_NONE) return;
        String prev = editingField == EDIT_DRAFT_NAME ? draftName : expressionValues.get(editingField);
        if (Objects.equals(prev, val)) return;
        if (editingField == EDIT_DRAFT_NAME) draftName = val;
        else expressionValues.set(editingField, val);
        dirty = true;
        rebuildProgram();
    }

    // Capture the inline editor
    private void captureInlineEditor() {
        if (editingField == EDIT_NONE || inlineEditor == null) return;
        updateInlineValue(inlineEditor.getValue());
    }

    // Finish the inline edit
    private void finishInlineEdit(boolean revert) {
        if (editingField == EDIT_NONE) return;
        int field = editingField;
        if (revert) {
            if (field == EDIT_DRAFT_NAME) draftName = valueBeforeEdit;
            else expressionValues.set(field, valueBeforeEdit);
            dirty = dirtyBeforeEdit;
            rebuildProgram();
        } else {
            captureInlineEditor();
        }
        editingField = EDIT_NONE;
        valueBeforeEdit = "";
        if (inlineEditor != null) {
            inlineEditor.setVisible(false);
            inlineEditor.setFocused(false);
        }
        if (getFocused() == inlineEditor) setFocused(null);
    }

    // Rebuild the program
    private void rebuildProgram() {
        program = NotationExpression.parse(expressionValues);
    }

    // Reset the view
    private void resetView() {
        initView();
        dirty = true;
    }

    // Initialize the view
    private void initView() {
        centerX = 0.0D;
        centerY = 0.0D;
        unitsPerPixelX = defaultScale();
        unitsPerPixelY = unitsPerPixelX;
        viewInitialized = true;
    }

    // Create the default scale
    private double defaultScale() {
        double horizontalScale = 20.0D / Math.max(320, width - sidebarWidth());
        double verticalScale = 14.0D / Math.max(240, height - TOOLBAR_HEIGHT);
        return clampScale(Math.max(horizontalScale, verticalScale));
    }

    // Zoom around the cursor
    private void zoomAt(double factor, double mouseX, double mouseY) {
        double beforeX = worldX(mouseX);
        double beforeY = worldY(mouseY);
        unitsPerPixelX = clampScale(unitsPerPixelX * factor);
        unitsPerPixelY = clampScale(unitsPerPixelY * factor);
        double afterX = worldX(mouseX);
        double afterY = worldY(mouseY);
        centerX += beforeX - afterX;
        centerY += beforeY - afterY;
        dirty = true;
    }

    // Build the function plotter toolbar buttons
    private List<ActionButton> toolbarButtons() {
        boolean compact = width < 780;
        int[] widths = compact
                ? new int[]{62, 55, 55, 112, 92, 94}
                : new int[]{94, 82, 82, 174, 150, 142};
        String[] labels = compact
                ? new String[]{"Graph", "Save", "Load", "Convert Function",
                "SCM: " + (useScm ? "On" : "Off"), shortOutputLabel()}
                : new String[]{"Back to Graph", "Save Draft", "Load Draft", "Convert to Graph Function",
                "Use SCM simulation: " + (useScm ? "On" : "Off"), outputMode.label()};
        Action[] actions = {Action.BACK, Action.SAVE, Action.LOAD, Action.CONVERT, Action.SCM, Action.OUTPUT};
        List<ActionButton> buttons = new ArrayList<>(actions.length);
        int x = 5;
        int gap = compact ? 3 : 5;
        for (int idx = 0; idx < actions.length; idx++) {
            boolean active = actions[idx] != Action.OUTPUT || useScm && scmModel.available();
            buttons.add(new ActionButton(actions[idx], new Rect(x, 5, widths[idx], 18),
                    Component.literal(labels[idx]), active));
            x += widths[idx] + gap;
        }
        return List.copyOf(buttons);
    }

    // Get the plot buttons
    private List<ActionButton> plotButtons() {
        int right = width - 5;
        return List.of(
                new ActionButton(Action.HOME, new Rect(right - 126, height - 26, 56, 20),
                        Component.literal("Home"), true),
                new ActionButton(Action.ZOOM_IN, new Rect(right - 66, height - 26, 28, 20),
                        Component.literal("+"), true),
                new ActionButton(Action.ZOOM_OUT, new Rect(right - 34, height - 26, 28, 20),
                        Component.literal("-"), true));
    }

    // Get the short output label
    private String shortOutputLabel() {
        return switch (outputMode) {
            case FORCE_MAGNITUDE -> "Force";
            case FORCE_X -> "Force X";
            case FORCE_Y -> "Force Y";
            case FORCE_Z -> "Force Z";
            case TORQUE_MAGNITUDE -> "Torque";
            case TORQUE_X -> "Torque X";
            case TORQUE_Y -> "Torque Y";
            case TORQUE_Z -> "Torque Z";
            case SPEED -> "Speed";
        };
    }

    // Get the sidebar width
    private int sidebarWidth() {
        return Math.min(340, Math.max(250, width / 4));
    }

    // Get the draft name bounds
    private Rect draftNameBounds() {
        return new Rect(6, TOOLBAR_HEIGHT + 22, sidebarWidth() - 12, 22);
    }

    // Get the expression list bounds
    private Rect expressionListBounds() {
        int top = TOOLBAR_HEIGHT + 52;
        int bottom = Math.max(top + EXPRESSION_ROW_HEIGHT, addExpressionBounds().y - 5);
        return new Rect(0, top, sidebarWidth(), bottom - top);
    }

    // Get the expression visible rows
    private int expressionVisibleRows() {
        return Math.max(1, expressionListBounds().height / EXPRESSION_ROW_HEIGHT);
    }

    // Get the maximum expression scroll
    private int maxExpressionScroll() {
        return Math.max(0, expressionValues.size() - expressionVisibleRows());
    }

    // Get the expression bounds
    private Rect expressionBounds(int idx) {
        int top = expressionListBounds().y + (idx - expressionScroll) * EXPRESSION_ROW_HEIGHT;
        return new Rect(6, top, sidebarWidth() - 12, EXPRESSION_ROW_HEIGHT - 3);
    }

    // Remove the expression bounds
    private Rect removeExpressionBounds(int idx) {
        Rect row = expressionBounds(idx);
        return new Rect(row.right() - 23, row.y + 4, 18, row.height - 8);
    }

    // Add the expression bounds
    private Rect addExpressionBounds() {
        return new Rect(6, height - 50, 118, 20);
    }

    // Get the inline editor bounds
    private Rect inlineEditorBounds(int field) {
        Rect row = field == EDIT_DRAFT_NAME ? draftNameBounds() : expressionBounds(field);
        int inset = field == EDIT_DRAFT_NAME ? 8 : 34;
        int trailing = field == EDIT_DRAFT_NAME ? 8 : 32;
        return new Rect(row.x + inset, row.y + 3,
                Math.max(24, row.width - inset - trailing), Math.min(18, row.height - 6));
    }

    // Load the browser bounds
    private Rect loadBrowserBounds() {
        int x = 8;
        int y = TOOLBAR_HEIGHT + 34;
        int browserHeight = Math.min(260, Math.max(80, height - y - 20));
        return new Rect(x, y, sidebarWidth() - 16, browserHeight);
    }

    // Load the visible rows
    private int loadVisibleRows() {
        return Math.max(1, (loadBrowserBounds().height - 38) / 28);
    }

    // Load the row bounds
    private Rect loadRowBounds(int row) {
        Rect browser = loadBrowserBounds();
        return new Rect(browser.x + 4, browser.y + 36 + row * 28,
                browser.width - 8, 26);
    }

    // Check if the V2 UI is enabled
    private boolean v2Ui() {
        return parent.usesV2Ui();
    }

    // Get the canvas color
    private int canvasColor() {
        return v2Ui() ? AdvancedControllerV2Theme.CANVAS_BACKGROUND : 0xFF10141C;
    }

    // Get the primary text color
    private int primaryTextColor() {
        return v2Ui() ? AdvancedControllerV2Theme.PRIMARY : 0xFFE7F4FF;
    }

    // Get the secondary text color
    private int secondaryTextColor() {
        return v2Ui() ? AdvancedControllerV2Theme.SECONDARY : 0xFFC8D7E3;
    }

    // Get the muted text color
    private int mutedTextColor() {
        return v2Ui() ? AdvancedControllerV2Theme.MUTED : 0xFF91A9B8;
    }

    // Get the accent text color
    private int accentTextColor() {
        return v2Ui() ? AdvancedControllerV2Theme.ACCENT_LIGHT : 0xFF91D9FF;
    }

    // Get the border color
    private int borderColor() {
        return v2Ui() ? AdvancedControllerV2Theme.BORDER : 0xFF314657;
    }

    // Get the error color
    private int errorColor() {
        return v2Ui() ? AdvancedControllerV2Theme.DANGER : 0xFFFF7777;
    }

    // Get the world x
    private double worldX(double screenX) {
        double center = (sidebarWidth() + width) * 0.5D;
        return centerX + (screenX - center) * unitsPerPixelX;
    }

    // Get the world y
    private double worldY(double screenY) {
        double center = (TOOLBAR_HEIGHT + height) * 0.5D;
        return centerY - (screenY - center) * unitsPerPixelY;
    }

    // Get the screen x
    private int screenX(double worldX) {
        double center = (sidebarWidth() + width) * 0.5D;
        return (int) Math.round(center + (worldX - centerX) / unitsPerPixelX);
    }

    // Get the screen y
    private int screenY(double worldY) {
        double center = (TOOLBAR_HEIGHT + height) * 0.5D;
        return (int) Math.round(center - (worldY - centerY) / unitsPerPixelY);
    }

    // Get the status line
    private String statusLine() {
        for (NotationExpression.LineResult line : program.lines()) {
            if (!line.valid()) return "Line " + (line.lineIndex() + 1) + ": " + line.error();
        }
        if (!status.isBlank()) return status;
        return dirty ? "Unsaved changes" : "Ready";
    }

    // Set the status
    private void setStatus(String msg, boolean error) {
        status = msg == null ? "" : msg;
        statusError = error;
    }

    // Get the nice step
    private static double niceStep(double target) {
        if (!Double.isFinite(target) || target <= 0.0D) return 1.0D;
        double power = Math.pow(10.0D, Math.floor(Math.log10(target)));
        double scaled = target / power;
        double nice = scaled <= 1.0D ? 1.0D : scaled <= 2.0D ? 2.0D : scaled <= 5.0D ? 5.0D : 10.0D;
        return nice * power;
    }

    // Clamp the scale
    private static double clampScale(double requested) {
        return Mth.clamp(finite(requested, 1.0D), 1.0E-5D, 1.0E6D);
    }

    // Normalize the value to a finite result
    private static double finite(double val, double fallback) {
        return Double.isFinite(val) ? val : fallback;
    }

    // Get the compact
    private static String compact(double val) {
        if (!Double.isFinite(val)) return "--";
        double absolute = Math.abs(val);
        if (absolute >= 100000.0D || absolute > 0.0D && absolute < 0.001D) {
            return String.format(Locale.ROOT, "%.2e", val);
        }
        String formatted = String.format(Locale.ROOT, "%.4f", val);
        return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // Trim the function plotter
    private static String trim(String val, int maximum) {
        if (val == null || maximum <= 0) return "";
        return val.length() <= maximum ? val : val.substring(0, Math.max(0, maximum - 3)) + "...";
    }

    // Draw the line
    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int col) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int stepX = x1 < x2 ? 1 : -1;
        int stepY = y1 < y2 ? 1 : -1;
        int error = dx - dy;
        while (true) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, col);
            if (x1 == x2 && y1 == y2) {
                return;
            }
            int doubledError = error * 2;
            if (doubledError > -dy) {
                error -= dy;
                x1 += stepX;
            }
            if (doubledError < dx) {
                error += dx;
                y1 += stepY;
            }
        }
    }

    // Define the action values
    private enum Action {
        BACK,
        SAVE,
        LOAD,
        CONVERT,
        SCM,
        OUTPUT,
        HOME,
        ZOOM_IN,
        ZOOM_OUT
    }

    // Store the rect
    private record Rect(int x, int y, int width, int height) {
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

    // Store the action button
    private record ActionButton(Action action, Rect bounds, Component label, boolean active) {
    }

}
