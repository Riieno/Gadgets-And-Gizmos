package com.rieno.gadgetsandgizmos.compat.simulated;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterEntries;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;

// Expose every configured Linked Typewriter key as readable graph data
public final class LinkedTypewriterGraphCompat {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String KEYBIND_PORT_PREFIX = "keybind_";
    private static final String ACTIVE_PORT_SUFFIX = "_active";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the linked typewriter graph compat
    private LinkedTypewriterGraphCompat() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Get the readable data
    public static Map<String, String> readableData(BlockEntity blockEntity) {
        if (!(blockEntity instanceof LinkedTypewriterBlockEntity typewriter)) {
            return Map.of();
        }
        Map<String, String> ports = new LinkedHashMap<>();
        typewriter.getTypewriterEntries().getEntries().stream()
                .mapToInt(LinkedTypewriterEntries.KeyboardEntry::getGLFWKeyCode)
                .distinct()
                .sorted()
                .forEach(keyCode -> {
                    ports.put(keyPort(keyCode), "number");
                    ports.put(activePort(keyCode), "boolean");
                });
        return ports;
    }

    // Get the readable labels
    public static Map<String, String> readableLabels(BlockEntity blockEntity) {
        if (!(blockEntity instanceof LinkedTypewriterBlockEntity typewriter)) {
            return Map.of();
        }
        Map<String, String> labels = new LinkedHashMap<>();
        typewriter.getTypewriterEntries().getEntries().stream()
                .mapToInt(LinkedTypewriterEntries.KeyboardEntry::getGLFWKeyCode)
                .distinct()
                .sorted()
                .forEach(keyCode -> {
                    String label = "Key: " + keyLabel(keyCode);
                    labels.put(keyPort(keyCode), label);
                    labels.put(activePort(keyCode), label + " Active");
                });
        return labels;
    }

    // Read the linked typewriter graph compat
    public static AdvancedGraphDocument.Value read(BlockEntity blockEntity, String port) {
        if (!(blockEntity instanceof LinkedTypewriterBlockEntity typewriter) || port == null) {
            return null;
        }
        boolean active = port.endsWith(ACTIVE_PORT_SUFFIX);
        String keyPort = active
                ? port.substring(0, port.length() - ACTIVE_PORT_SUFFIX.length()) : port;
        for (LinkedTypewriterEntries.KeyboardEntry entry : typewriter.getTypewriterEntries().getEntries()) {
            if (!keyPort(entry.getGLFWKeyCode()).equals(keyPort)) {
                continue;
            }
            return active
                    ? AdvancedGraphDocument.Value.bool(entry.getTransmittedStrength() > 0)
                    : AdvancedGraphDocument.Value.number(entry.getGLFWKeyCode());
        }
        return null;
    }

    // Handle key port
    static String keyPort(int keyCode) {
        return KEYBIND_PORT_PREFIX + keyCode;
    }

    // Get the active port
    private static String activePort(int keyCode) {
        return keyPort(keyCode) + ACTIVE_PORT_SUFFIX;
    }

    // Handle key label
    static String keyLabel(int keyCode) {
        if (keyCode >= 65 && keyCode <= 90) {
            return "'" + (char) keyCode + "'";
        }
        if (keyCode >= 48 && keyCode <= 57) {
            return "'" + (char) keyCode + "'";
        }
        if (keyCode >= 290 && keyCode <= 314) {
            return "F" + (keyCode - 289);
        }
        if (keyCode >= 320 && keyCode <= 329) {
            return "Keypad " + (keyCode - 320);
        }
        return switch (keyCode) {
            case 32 -> "Space";
            case 39 -> "Apostrophe";
            case 44 -> "Comma";
            case 45 -> "Minus";
            case 46 -> "Period";
            case 47 -> "Slash";
            case 59 -> "Semicolon";
            case 61 -> "Equals";
            case 91 -> "Left Bracket";
            case 92 -> "Backslash";
            case 93 -> "Right Bracket";
            case 96 -> "Grave Accent";
            case 256 -> "Escape";
            case 257 -> "Enter";
            case 258 -> "Tab";
            case 259 -> "Backspace";
            case 260 -> "Insert";
            case 261 -> "Delete";
            case 262 -> "Right Arrow";
            case 263 -> "Left Arrow";
            case 264 -> "Down Arrow";
            case 265 -> "Up Arrow";
            case 266 -> "Page Up";
            case 267 -> "Page Down";
            case 268 -> "Home";
            case 269 -> "End";
            case 280 -> "Caps Lock";
            case 281 -> "Scroll Lock";
            case 282 -> "Num Lock";
            case 283 -> "Print Screen";
            case 284 -> "Pause";
            case 330 -> "Keypad Decimal";
            case 331 -> "Keypad Divide";
            case 332 -> "Keypad Multiply";
            case 333 -> "Keypad Subtract";
            case 334 -> "Keypad Add";
            case 335 -> "Keypad Enter";
            case 336 -> "Keypad Equals";
            case 340 -> "Left Shift";
            case 341 -> "Left Control";
            case 342 -> "Left Alt";
            case 343 -> "Left Super";
            case 344 -> "Right Shift";
            case 345 -> "Right Control";
            case 346 -> "Right Alt";
            case 347 -> "Right Super";
            case 348 -> "Menu";
            default -> "[" + keyCode + "]";
        };
    }

}
