package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSelectionGrid;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedSelectionModal;
import com.rieno.gadgetsandgizmos.lib.client.ui.PaintedListModal.Region;

// Share the song selector's artwork while reserving larger texture thumbnails
final class CTTexturePickerAppearance{
    private CTTexturePickerAppearance(){}

    static final PaintedSelectionModal.Appearance DEFAULT = create();

    // Keep texture search, cards and action buttons aligned with the existing selector
    private static PaintedSelectionModal.Appearance create(){
        var sound = CTSoundPickerAppearance.DEFAULT;
        var layout = sound.layout();
        var card = new PaintedSelectionGrid.CardAppearance(sound.entry(), sound.tick(), layout.entry().checkbox(),
                new Region(24, 4, 40, 28), layout.entry().label());
        return new PaintedSelectionModal.Appearance(sound.panel(),
                new PaintedSelectionGrid.Appearance(card, layout.grid(), layout.search(), layout.scrollbar()),
                layout.title(), layout.footer(), layout.accept(), layout.cancel());
    }
}
