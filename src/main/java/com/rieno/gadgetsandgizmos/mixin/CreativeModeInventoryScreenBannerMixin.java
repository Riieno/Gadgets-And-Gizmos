package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.registry.CTCreativeTabSection;
import com.rieno.gadgetsandgizmos.registry.CTCreativeTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Draw the addon banner above the creative inventory
@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeModeInventoryScreenBannerMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Selected tab
    @Shadow private static CreativeModeTab selectedTab;
    // Current scroll offs
    @Shadow private float scrollOffs;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the section banners
    @Inject(method = "renderBg", at = @At("TAIL"))
    private void ct$renderSectionBanners(GuiGraphics graphics, float partialTick,
                                         int mouseX, int mouseY, CallbackInfo callback) {
        CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
        if (selectedTab != CTCreativeTabs.MAIN.get()) return;
        int rows = Math.max(0, (screen.getMenu().items.size() + 8) / 9 - 5);
        int firstVisibleRow = Math.round(scrollOffs * rows);
        for (CTCreativeTabSection section : CTCreativeTabs.sections()) {
            int row = CTCreativeTabs.getSectionRow(section);
            int visibleRow = row - firstVisibleRow;
            if (visibleRow < 0 || visibleRow >= 5) continue;
            graphics.blitSprite(section.bannerTexture(), screen.getGuiLeft() + 9,
                    screen.getGuiTop() + 18 + visibleRow * 18, 162, 18);
        }
    }
}
