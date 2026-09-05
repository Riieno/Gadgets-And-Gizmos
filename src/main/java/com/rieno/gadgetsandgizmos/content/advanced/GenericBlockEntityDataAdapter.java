package com.rieno.gadgetsandgizmos.content.advanced;

import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataAdapter;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataPort;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Expose unknown block entities as one structured data port, not a noisy list of NBT fields.
public final class GenericBlockEntityDataAdapter implements BlockEntityDataAdapter<BlockEntity> {
    public static final String DATA_PORT = "nbt";

    private static final Set<String> UNSAFE_WRITE_KEYS = Set.of(
            "id", "x", "y", "z", "pos", "position", "world", "level", "dimension",
            "owner", "uuid", "capabilities", "components", "forgecaps", "neoforgecaps"
    );
    private static final int MAX_COLLECTION_ENTRIES = 256;
    private static final int MAX_COLLECTION_DEPTH = 8;

    @Override
    public Class<BlockEntity> targetType() {
        return BlockEntity.class;
    }

    @Override
    public List<BlockEntityDataPort> ports(BlockEntity target) {
        return List.of(BlockEntityDataPort.readWrite(DATA_PORT, "map"));
    }

    @Override
    public GraphValue read(BlockEntity target, String port) {
        return DATA_PORT.equals(port) ? graphValue(serializedData(target), 0) : null;
    }

    @Override
    public boolean write(BlockEntity target, String port, GraphValue value) {
        if (target == null || !DATA_PORT.equals(port) || value == null
                || !"map".equals(value.type()) || target.getLevel() == null) {
            return false;
        }
        CompoundTag data = serializedData(target);
        boolean changed = false;
        if (!(value.value() instanceof Map<?, ?> values)) {
            return false;
        }
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            Tag current = data.get(key);
            if (current == null || !isSafelyWritable(key, current)) {
                continue;
            }
            Tag replacement = writeTag(current, runtimeValue(entry.getValue()));
            if (replacement != null && !replacement.equals(current)) {
                data.put(key, replacement);
                changed = true;
            }
        }
        if (!changed) {
            return false;
        }
        Level level = target.getLevel();
        target.loadWithComponents(data, level.registryAccess());
        target.setChanged();
        level.sendBlockUpdated(target.getBlockPos(), target.getBlockState(), target.getBlockState(), 3);
        return true;
    }

    private static CompoundTag serializedData(BlockEntity target) {
        if (target == null || target.getLevel() == null) {
            return new CompoundTag();
        }
        return target.saveWithoutMetadata(target.getLevel().registryAccess());
    }

    private static boolean isSafelyWritable(String key, Tag value) {
        if (key == null || value == null || !isScalar(value)) {
            return false;
        }
        String normalized = key.strip().toLowerCase(Locale.ROOT);
        return !UNSAFE_WRITE_KEYS.contains(normalized)
                && !BlockEntityDataAccessPolicy.isItemContentMutation(normalized, graphType(value));
    }

    private static boolean isScalar(Tag value) {
        return value instanceof NumericTag || value instanceof StringTag;
    }

    private static GraphValue graphValue(Tag value, int depth) {
        if (value instanceof NumericTag number) {
            return GraphValue.number(number.getAsDouble());
        }
        if (value instanceof StringTag string) {
            return GraphValue.string(string.getAsString());
        }
        if (depth >= MAX_COLLECTION_DEPTH) {
            return GraphValue.string("<truncated>");
        }
        if (value instanceof CompoundTag compound) {
            Map<String, Object> map = new LinkedHashMap<>();
            int entries = 0;
            for (String key : compound.getAllKeys()) {
                if (entries++ >= MAX_COLLECTION_ENTRIES) {
                    break;
                }
                Tag child = compound.get(key);
                if (child != null) {
                    map.put(key, graphValue(child, depth + 1).value());
                }
            }
            return GraphValue.map(map);
        }
        if (value instanceof ListTag list) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < Math.min(list.size(), MAX_COLLECTION_ENTRIES); index++) {
                values.add(graphValue(list.get(index), depth + 1).value());
            }
            return GraphValue.list(values);
        }
        if (value instanceof ByteArrayTag array) {
            List<Double> values = new ArrayList<>();
            for (byte entry : array.getAsByteArray()) {
                if (values.size() >= MAX_COLLECTION_ENTRIES) break;
                values.add((double) entry);
            }
            return GraphValue.list(values);
        }
        if (value instanceof IntArrayTag array) {
            List<Double> values = new ArrayList<>();
            for (int entry : array.getAsIntArray()) {
                if (values.size() >= MAX_COLLECTION_ENTRIES) break;
                values.add((double) entry);
            }
            return GraphValue.list(values);
        }
        if (value instanceof LongArrayTag array) {
            List<Double> values = new ArrayList<>();
            for (long entry : array.getAsLongArray()) {
                if (values.size() >= MAX_COLLECTION_ENTRIES) break;
                values.add((double) entry);
            }
            return GraphValue.list(values);
        }
        return GraphValue.string(value.getAsString());
    }

    private static String graphType(Tag value) {
        return switch (value.getId()) {
            case Tag.TAG_STRING -> "string";
            case Tag.TAG_LIST, Tag.TAG_BYTE_ARRAY, Tag.TAG_INT_ARRAY, Tag.TAG_LONG_ARRAY -> "list";
            case Tag.TAG_COMPOUND -> "map";
            default -> "number";
        };
    }

    private static AdvancedGraphDocument.Value runtimeValue(Object value) {
        if (value instanceof GraphValue graphValue) {
            return GraphRuntime.fromLibraryValue(graphValue);
        }
        if (value instanceof Boolean bool) {
            return AdvancedGraphDocument.Value.bool(bool);
        }
        if (value instanceof Number number) {
            return AdvancedGraphDocument.Value.number(number.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            CompoundTag values = new CompoundTag();
            map.forEach((key, child) -> values.put(String.valueOf(key), runtimeValue(child).toTag()));
            return AdvancedGraphDocument.Value.map(values);
        }
        if (value instanceof List<?> list) {
            CompoundTag values = new CompoundTag();
            for (int index = 0; index < list.size(); index++) {
                values.put(Integer.toString(index), runtimeValue(list.get(index)).toTag());
            }
            return AdvancedGraphDocument.Value.list(values);
        }
        return AdvancedGraphDocument.Value.string(String.valueOf(value));
    }

    private static Tag writeTag(Tag current, AdvancedGraphDocument.Value value) {
        if (current instanceof ByteTag) return ByteTag.valueOf((byte) Math.round(value.asNumber()));
        if (current instanceof ShortTag) return ShortTag.valueOf((short) Math.round(value.asNumber()));
        if (current instanceof IntTag) return IntTag.valueOf((int) Math.round(value.asNumber()));
        if (current instanceof LongTag) return LongTag.valueOf(Math.round(value.asNumber()));
        if (current instanceof FloatTag) return FloatTag.valueOf((float) value.asNumber());
        if (current instanceof DoubleTag) return DoubleTag.valueOf(value.asNumber());
        if (current instanceof StringTag) return StringTag.valueOf(value.asString());
        return null;
    }
}
