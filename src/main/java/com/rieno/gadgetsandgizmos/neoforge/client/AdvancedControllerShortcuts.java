package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.lib.client.ui.ShortcutsModal.Shortcut;
import net.minecraft.network.chat.Component;

import java.util.List;

// Describe the controls implemented by each ACC viewport
final class AdvancedControllerShortcuts{
    enum Profile{ NODES, SCM, WORKERS, SCHEDULE }

    private AdvancedControllerShortcuts(){}

    static List<Shortcut> entries(Profile profile, boolean mac){
        String ctrl = mac ? "Cmd" : "Ctrl";
        if(profile == Profile.SCHEDULE) return List.of(
                hint(ctrl + "+S", "save_schedule"), hint("F2", "configure_block"),
                hint("Alt+Up / Alt+Down", "move_condition"), hint(ctrl + "+A", "select_all"),
                hint(ctrl + "+C", "copy"), hint(ctrl + "+X", "cut"), hint(ctrl + "+V", "paste"), hint(ctrl + "+D", "duplicate"),
                hint("Del / Backspace", "delete"), hint("Arrows", "nudge"), hint(ctrl + "+Arrows", "nudge_grid"),
                hint(ctrl + "+LMB / Shift+LMB", "toggle_selection"), hint("LMB drag canvas", "box_select"),
                hint("Shift+drag block", "snap"), hint("MMB / RMB drag", "pan"), hint("Wheel", "zoom"), hint("Esc", "close"));
        if(profile == Profile.SCM) return List.of(
                hint(ctrl + "+S", "save_apply"), hint("LMB drag", "rotate_preview"),
                hint("MMB / RMB drag", "pan"), hint("Wheel", "zoom"),
                hint("LMB", "select_block"), hint(ctrl + "+LMB", "toggle_selection"),
                hint("Shift+LMB", "select_range"), hint("Esc", "close"));
        if(profile == Profile.WORKERS) return List.of(
                hint(ctrl + "+S", "save_worker"), hint("LMB", "select_worker"), hint("Esc", "close"));
        return List.of(
                hint(ctrl + "+S", "save_apply"), hint(ctrl + "+Z", "undo"),
                hint(ctrl + "+Y / " + ctrl + "+Shift+Z", "redo"),
                hint(ctrl + "+C", "copy"), hint(ctrl + "+V", "paste"), hint(ctrl + "+D", "duplicate"),
                hint(ctrl + "+G", "comment_group"), hint("Del / Backspace", "delete"),
                hint("Arrows", "nudge"), hint(ctrl + "+Arrows", "nudge_grid"),
                hint(ctrl + "+LMB node", "toggle_selection"), hint(ctrl + "+LMB port", "disconnect"),
                hint("LMB drag port", "connect"),
                hint("LMB drag canvas", "box_select"), hint("MMB / RMB drag", "pan"),
                hint("Wheel", "zoom"), hint("RMB", "node_menu"),
                hint("Double LMB canvas", "sticky_note"), hint("Double LMB wire", "reroute"),
                hint("Double LMB function", "open_function"),
                hint("Shift+drag node", "snap"), hint("Enter / Shift+Enter", "search_results"), hint("Esc", "close"));
    }

    private static Shortcut hint(String keys, String action){
        return new Shortcut(keys, Component.translatable("createthrusters.advanced_controller.shortcuts." + action));
    }
}
