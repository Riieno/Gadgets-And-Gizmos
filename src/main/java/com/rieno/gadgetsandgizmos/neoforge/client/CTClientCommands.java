package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.ClawMarkerRenderMode;
import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

// Provide client commands for graph, render and physics diagnostics
public final class CTClientCommands {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the CT client commands
    private CTClientCommands() {
    }

    // Register the client commands
    public static void registerClientCommands(RegisterClientCommandsEvent evt) {
        CommandDispatcher<CommandSourceStack> dispatcher = evt.getDispatcher();
        dispatcher.register(Commands.literal("thrusters")
                .then(Commands.literal("clawmarker")
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(Component.literal("Claw marker is disabled"));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("depth").executes(ctx -> setMode(ctx.getSource(), ClawMarkerRenderMode.OFF)))
                        .then(Commands.literal("decal").executes(ctx -> setMode(ctx.getSource(), ClawMarkerRenderMode.OFF)))
                        .then(Commands.literal("particle").executes(ctx -> setMode(ctx.getSource(), ClawMarkerRenderMode.OFF)))
                        .then(Commands.literal("off").executes(ctx -> setMode(ctx.getSource(), ClawMarkerRenderMode.OFF))))
                .then(Commands.literal("debug")
                        .executes(ctx -> {
                            ctx.getSource().sendSystemMessage(Component.literal("Depth debug HUD is disabled"));
                            return Command.SINGLE_SUCCESS;
                        })));

            dispatcher.register(Commands.literal("gantry")
                .then(Commands.literal("debug")
                    .executes(ctx -> setGantryDebug(ctx.getSource(), null))
                    .then(Commands.literal("on").executes(ctx -> setGantryDebug(ctx.getSource(), Boolean.TRUE)))
                    .then(Commands.literal("off").executes(ctx -> setGantryDebug(ctx.getSource(), Boolean.FALSE)))));
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Set the mode
    private static int setMode(CommandSourceStack src, ClawMarkerRenderMode mode) {
        CTConfigs.CLIENT.clawMarkerRenderMode.set(ClawMarkerRenderMode.OFF);
        src.sendSystemMessage(Component.literal("Claw marker is disabled"));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.levelRenderer != null) {
            minecraft.levelRenderer.allChanged();
        }
        return Command.SINGLE_SUCCESS;
    }

    // Set the gantry debug
    private static int setGantryDebug(CommandSourceStack src, Boolean enabled) {
        boolean state;
        if (enabled == null) {
            state = CTGantryAnchorDebugRenderer.toggle();
        } else {
            CTGantryAnchorDebugRenderer.setEnabled(enabled);
            state = enabled;
        }

        String msg = state
                ? "Gantry anchor debug enabled"
                : "Gantry anchor debug disabled";
        src.sendSystemMessage(Component.literal(msg + " (anchors: " + CTGantryAnchorDebugRenderer.getLastAnchorCount() + ")"));
        return Command.SINGLE_SUCCESS;
    }
}
