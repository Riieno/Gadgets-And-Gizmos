package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapter;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataPort;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Expose safe front and back sign text without exposing edit session state
public final class VanillaSignDataAdapter implements BlockEntityDataAdapter<SignBlockEntity> {
    private static final List<BlockEntityDataPort> PORTS = createPorts();

    @Override
    public Class<SignBlockEntity> targetType() {
        return SignBlockEntity.class;
    }

    @Override
    public List<BlockEntityDataPort> ports(SignBlockEntity target) {
        return PORTS;
    }

    @Override
    public GraphValue read(SignBlockEntity target, String port) {
        SignLine line = parse(port);
        if (line == null) {
            return null;
        }
        SignText text = line.front() ? target.getFrontText() : target.getBackText();
        Component message = text.getMessage(line.line(), false);
        return GraphValue.string(message == null ? "" : message.getString());
    }

    @Override
    public boolean write(SignBlockEntity target, String port, GraphValue value) {
        SignLine line = parse(port);
        if (line == null || target.isWaxed()) {
            return false;
        }
        SignText current = line.front() ? target.getFrontText() : target.getBackText();
        Component message = Component.literal(limit(value.asString(), 384));
        if (Objects.equals(current.getMessage(line.line(), false), message)) {
            return false;
        }
        boolean changed = target.setText(current.setMessage(line.line(), message), line.front());
        Level level = target.getLevel();
        if (changed && level != null) {
            level.sendBlockUpdated(target.getBlockPos(), target.getBlockState(), target.getBlockState(), 3);
        }
        return changed;
    }

    private static List<BlockEntityDataPort> createPorts() {
        List<BlockEntityDataPort> ports = new ArrayList<>(8);
        for (int line = 1; line <= 4; line++) {
            ports.add(BlockEntityDataPort.readWrite("front_line_" + line, "string"));
            ports.add(BlockEntityDataPort.readWrite("back_line_" + line, "string"));
        }
        return List.copyOf(ports);
    }

    private static SignLine parse(String port) {
        if (port == null) {
            return null;
        }
        boolean front = port.startsWith("front_line_");
        String prefix = front ? "front_line_" : "back_line_";
        if (!front && !port.startsWith(prefix)) {
            return null;
        }
        try {
            int line = Integer.parseInt(port.substring(prefix.length())) - 1;
            return line >= 0 && line < 4 ? new SignLine(front, line) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String limit(String value, int maxLength) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= maxLength
                ? normalized : normalized.substring(0, maxLength);
    }

    private record SignLine(boolean front, int line) {
    }
}
