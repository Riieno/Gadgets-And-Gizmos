package com.rieno.gadgetsandgizmos.compat.recipe;

import com.rieno.gadgetsandgizmos.content.ThrusterBlock;
import com.rieno.gadgetsandgizmos.lib.client.recipe.BulkProcessingLayout;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;

// Supply the thruster appearance to the shared recipe-viewer layout
public final class ThrusterProcessingPreview{
    public static final int WIDTH = BulkProcessingLayout.WIDTH;
    public static final int HEIGHT = BulkProcessingLayout.HEIGHT;
    public static final int INPUT_X = BulkProcessingLayout.INPUT_X;
    public static final int INPUT_Y = BulkProcessingLayout.INPUT_Y;

    private ThrusterProcessingPreview(){}

    public static int outputX(int count, int idx){ return BulkProcessingLayout.outputX(count, idx); }
    public static int outputY(int count, int idx){ return BulkProcessingLayout.outputY(count, idx); }

    // Keep the preview independent of live thruster block entities and particles
    public static void render(GuiGraphics graphics, int left, int top){
        BulkProcessingLayout.render(graphics, left, top, CTBlocks.THRUSTER.get().defaultBlockState()
                .setValue(ThrusterBlock.POWERED, true).setValue(ThrusterBlock.FACING, Direction.EAST));
    }
}
