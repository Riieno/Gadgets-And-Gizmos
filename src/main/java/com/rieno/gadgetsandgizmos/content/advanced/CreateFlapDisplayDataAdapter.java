package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapter;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataPort;
import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// Expose null safe Create display board text ports
public final class CreateFlapDisplayDataAdapter implements BlockEntityDataAdapter<FlapDisplayBlockEntity> {
    @Override
    public Class<FlapDisplayBlockEntity> targetType() {
        return FlapDisplayBlockEntity.class;
    }

    @Override
    public List<BlockEntityDataPort> ports(FlapDisplayBlockEntity target) {
        FlapDisplayBlockEntity controller = controller(target);
        List<BlockEntityDataPort> ports = new ArrayList<>();
        ports.add(BlockEntityDataPort.readWrite("display_lines", "list"));
        for (int line = 0; line < controller.getLines().size(); line++) {
            ports.add(BlockEntityDataPort.readWrite("display_line_" + line, "string"));
        }
        return List.copyOf(ports);
    }

    @Override
    public GraphValue read(FlapDisplayBlockEntity target, String port) {
        FlapDisplayBlockEntity controller = controller(target);
        if ("display_lines".equals(port)) {
            List<String> lines = new ArrayList<>(controller.getLines().size());
            for (int line = 0; line < controller.getLines().size(); line++) {
                lines.add(lineText(controller, line));
            }
            return GraphValue.list(lines);
        }
        int line = lineIndex(port);
        return line < 0 ? null : GraphValue.string(lineText(controller, line));
    }

    @Override
    public boolean write(FlapDisplayBlockEntity target, String port, GraphValue value) {
        FlapDisplayBlockEntity controller = controller(target);
        if ("display_lines".equals(port)) {
            if (!(value.value() instanceof List<?> values)) {
                return false;
            }
            boolean changed = false;
            int lineCount = Math.min(values.size(), controller.getLines().size());
            for (int line = 0; line < lineCount; line++) {
                String text = String.valueOf(values.get(line));
                if (!lineText(controller, line).equals(text)) {
                    controller.applyTextManually(line, Component.literal(text));
                    changed = true;
                }
            }
            return changed;
        }
        int line = lineIndex(port);
        if (line < 0 || line >= controller.getLines().size()) {
            return false;
        }
        String text = value.asString();
        if (lineText(controller, line).equals(text)) {
            return false;
        }
        controller.applyTextManually(line, Component.literal(text));
        return true;
    }

    private static FlapDisplayBlockEntity controller(FlapDisplayBlockEntity target) {
        FlapDisplayBlockEntity controller = target.getController();
        return controller == null ? target : controller;
    }

    private static String lineText(FlapDisplayBlockEntity target, int line) {
        if (line < 0 || line >= target.getLines().size()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (FlapDisplaySection section : target.getLines().get(line).getSections()) {
            Component component = section.getText();
            if (component != null) {
                text.append(component.getString());
            }
        }
        return text.toString().stripTrailing();
    }

    private static int lineIndex(String port) {
        String prefix = "display_line_";
        if (port == null || !port.startsWith(prefix)) {
            return -1;
        }
        try {
            return Integer.parseInt(port.substring(prefix.length()));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
