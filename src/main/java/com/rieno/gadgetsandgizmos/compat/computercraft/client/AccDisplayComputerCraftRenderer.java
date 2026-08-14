package com.rieno.gadgetsandgizmos.compat.computercraft.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.vertex.PoseStack;
import dan200.computercraft.client.render.text.FixedWidthFontRenderer;
import dan200.computercraft.shared.computer.terminal.NetworkedTerminal;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Iterator;

// Draw a ComputerCraft terminal across the complete connected ACC display surface
public final class AccDisplayComputerCraftRenderer {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final float TERMINAL_BASE_Z = -0.20F;
    private static final float TERMINAL_DEPTH_SCALE = -960.0F;
    private static final int MAX_CACHED_TERMINALS = 32;
    private static final ArrayDeque<CachedTerminal> TERMINAL_CACHE = new ArrayDeque<>();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the ACC display computer craft
    private AccDisplayComputerCraftRenderer() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                              MAIN
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the ACC display ComputerCraft
    public static boolean render(
            CompoundTag frame, int width, int height,
            PoseStack poseStack, MultiBufferSource buffers) {
        CompoundTag terminalTag = frame.getCompound("Terminal");
        int columns = frame.getInt("Width");
        int rows = frame.getInt("Height");
        if (terminalTag.isEmpty() || columns <= 0 || rows <= 0) return false;
        NetworkedTerminal terminal = terminal(frame, terminalTag, columns, rows);
        float scaleX = width / (float) Math.max(1, terminal.getWidth() * 6);
        float scaleY = height / (float) Math.max(1, terminal.getHeight() * 9);
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, TERMINAL_BASE_Z);
        poseStack.scale(scaleX, scaleY, TERMINAL_DEPTH_SCALE);
        FixedWidthFontRenderer.drawTerminal(
                FixedWidthFontRenderer.toVertexConsumer(poseStack,
                        buffers.getBuffer(RenderType.text(FixedWidthFontRenderer.FONT))),
                0.0F, 0.0F, terminal, 0.0F, 0.0F, 0.0F, 0.0F);
        poseStack.popPose();
        return true;
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the terminal
    private static synchronized NetworkedTerminal terminal(
            CompoundTag frame, CompoundTag terminalTag, int columns, int rows
    ) {
        int fingerprint = terminalTag.hashCode();
        Iterator<CachedTerminal> iterator = TERMINAL_CACHE.iterator();
        while (iterator.hasNext()) {
            CachedTerminal cached = iterator.next();
            CompoundTag cachedFrame = cached.frame().get();
            if (cachedFrame == null) {
                iterator.remove();
                continue;
            }
            if (cachedFrame == frame && cached.columns() == columns && cached.rows() == rows) {
                if (cached.fingerprint() == fingerprint) {
                    return cached.terminal();
                }
                iterator.remove();
                break;
            }
        }
        NetworkedTerminal terminal = new NetworkedTerminal(columns, rows, true);
        terminal.readFromNBT(terminalTag);
        TERMINAL_CACHE.addFirst(new CachedTerminal(
                new WeakReference<>(frame), columns, rows, fingerprint, terminal));
        while (TERMINAL_CACHE.size() > MAX_CACHED_TERMINALS) {
            TERMINAL_CACHE.removeLast();
        }
        return terminal;
    }

    // Store the cached terminal
    private record CachedTerminal(
            WeakReference<CompoundTag> frame,
            int columns,
            int rows,
            int fingerprint,
            NetworkedTerminal terminal
    ) {
    }
}
