package com.rieno.gadgetsandgizmos.ponder;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

// Provide the shared scene helpers used by the addon's Ponder demonstrations
public class PonderTemplate {

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int CT_TOOLTIP_DURATION = 120;
    private static final int CT_TOOLTIP_PAUSE = 180;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ponder template
    private PonderTemplate() {}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the CT tooltip
    private static TextElementBuilder ctTooltip(CreateSceneBuilder scene) {
        return scene.overlay().showText(CT_TOOLTIP_DURATION);
    }

    // Handle the CT pause
    private static void ctPause(CreateSceneBuilder scene) {
        scene.idle(CT_TOOLTIP_PAUSE);
    }

    // Handle the template scene
    public static void templateScene(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);

        scene.title("ponder_template", "Template Scene Title");

        scene.configureBasePlate(0, 0, 5);

        scene.world().showSection(util.select().layer(0), Direction.UP);
        BlockPos gaugePos = util.grid().at(0, 1, 3);
        Selection gauge = util.select().position(gaugePos);
        scene.world().showSection(gauge, Direction.UP);
        scene.world().setKineticSpeed(gauge, 0.0f);
        scene.idle(5);

        scene.world().showSection(util.select().fromTo(5, 1, 2, 4, 1, 2), Direction.DOWN);
        scene.idle(10);
        for (int i = 0; i < 3; ++i) {
            scene.idle(5);
            scene.world().showSection(util.select().position(3, 1, 2 - i), Direction.DOWN);
            if (i == 0) continue;
            scene.world().showSection(util.select().position(3, 1, 2 + i), Direction.DOWN);
        }
        scene.idle(10);
        scene.world().showSection(util.select().position(gaugePos.east(2)), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(gaugePos.east()), Direction.DOWN);
        scene.idle(5);

        scene.world().setKineticSpeed(gauge, 64.0f);
        scene.effects().indicateSuccess(gaugePos);
        scene.idle(20);

        ctTooltip(scene)
                .text("TEMPLATE: Replace this with your own description text")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(3, 1, 4), Direction.WEST));
        ctPause(scene);

        Selection shafts = util.select().fromTo(2, 1, 0, 2, 1, 1);
        scene.world().showSection(shafts, Direction.EAST);
        scene.idle(10);
        scene.effects().rotationDirectionIndicator(util.grid().at(2, 1, 0));
        scene.effects().rotationDirectionIndicator(util.grid().at(2, 1, 1));
        scene.idle(20);
        ctTooltip(scene)
                .text("TEMPLATE: Describe another behaviour here")
                .attachKeyFrame()
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(util.grid().at(2, 1, 1), Direction.WEST));
        ctPause(scene);

        scene.markAsFinished();
    }
}
