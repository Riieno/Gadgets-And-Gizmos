package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.render.GuiNineSlice;
import com.rieno.gadgetsandgizmos.lib.client.render.GuiTextureRegion;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedGuiStyle;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSliderStyle;
import com.rieno.gadgetsandgizmos.lib.display.ConnectedSurfaceLayout;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.CTContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

// Supply the designer artwork to the shared painted controls
final class HudDesignerAppearance{
    static final GuiTextureRegion FRAME = atlas(0, 0, 576, 272);
    static final GuiTextureRegion VALUE = atlas(91, 339, 22, 8);
    static final GuiTextureRegion SWATCH = atlas(105, 292, 13, 9);
    static final GuiNineSlice RAIL = PaintedGuiStyle.slice(atlas(0, 337, 89, 12), 2, 2, 2, 2);
    static final PaintedSliderStyle SLIDER = new PaintedSliderStyle(atlas(0, 326, 85, 8), atlas(118, 335, 6, 16), 6);
    static final PaintedGuiStyle STYLE = new PaintedGuiStyle(
            CTAnalogueControllerAppearance.STYLE.panel(),
            PaintedGuiStyle.slice(atlas(0, 307, 114, 17), 3, 3, 9, 3),
            PaintedGuiStyle.slice(atlas(0, 285, 98, 16), 2, 2, 2, 2),
            PaintedGuiStyle.slice(atlas(0, 285, 98, 16), 2, 2, 2, 2),
            CTAnalogueControllerAppearance.STYLE.separator(), 0xFFF3EEE4, 0xFFB8B8B8);
    private static final GuiTextureRegion[] DISPLAY = connected("advanced_display_connected", false);
    private static final GuiTextureRegion[] DISPLAY_BORDER = connected("advanced_display_border_connected", false);
    private static final GuiTextureRegion[] SMALL_DISPLAY = connected("advanced_display_small_connected", true);
    private static final GuiTextureRegion[] SMALL_DISPLAY_BORDER = connected("advanced_display_small_border_connected", true);

    private HudDesignerAppearance(){}

    // Assemble the same connected front and bezel tiles used by the display blocks
    static void renderDisplay(GuiGraphics graphics, int x, int y, int width, int height,
                              boolean halfHeight, int columns, int rows){
        GuiTextureRegion[] screen = halfHeight ? SMALL_DISPLAY : DISPLAY;
        GuiTextureRegion[] border = halfHeight ? SMALL_DISPLAY_BORDER : DISPLAY_BORDER;
        for(var cell : new ConnectedSurfaceLayout(columns, rows).cells(x, y, width, height)){
            screen[cell.connections()].draw(graphics, cell.x(), cell.y(), cell.width(), cell.height());
            border[cell.connections()].draw(graphics, cell.x(), cell.y(), cell.width(), cell.height());
        }
    }

    private static GuiTextureRegion atlas(int x, int y, int width, int height){
        return new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters",
                "textures/gui/modals/advanced_hud_designer.png"), 600, 600, x, y, width, height);
    }

    private static GuiTextureRegion[] connected(String name, boolean halfHeight){
        AllCTTypes type = halfHeight ? AllCTTypes.HORIZONTAL_KRYPPERS : AllCTTypes.OMNIDIRECTIONAL;
        int sheet = type.getSheetSize();
        int size = halfHeight ? 32 : 128;
        int tile = size / sheet;
        GuiTextureRegion[] res = new GuiTextureRegion[16];
        for(int mask = 0; mask < res.length; mask++){
            CTContext ctx = new CTContext();
            ctx.left = (mask & ConnectedSurfaceLayout.LEFT) != 0;
            ctx.right = (mask & ConnectedSurfaceLayout.RIGHT) != 0;
            ctx.up = (mask & ConnectedSurfaceLayout.UP) != 0;
            ctx.down = (mask & ConnectedSurfaceLayout.DOWN) != 0;
            ctx.topLeft = ctx.up && ctx.left;
            ctx.topRight = ctx.up && ctx.right;
            ctx.bottomLeft = ctx.down && ctx.left;
            ctx.bottomRight = ctx.down && ctx.right;
            int idx = type.getTextureIndex(ctx);
            res[mask] = new GuiTextureRegion(ResourceLocation.fromNamespaceAndPath("createthrusters",
                    "textures/block/" + name + ".png"), size, size, idx % sheet * tile,
                    idx / sheet * tile, tile, halfHeight ? tile / 2 : tile);
        }
        return res;
    }
}
