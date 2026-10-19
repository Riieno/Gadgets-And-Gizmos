package com.rieno.gadgetsandgizmos.content;

import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.content.advanced.GraphRuntime;
import com.rieno.gadgetsandgizmos.lib.view.EntityViewSource;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaySampler;
import com.rieno.gadgetsandgizmos.lib.view.ViewRaycast;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

// Authorize wearable camera selection against the controller's current pair list
public final class BoundGlassesCamera{
    public static final String PREFIX = "bound_glasses:";
    private static final Map<AdvancedContraptionControllerBlockEntity, Sensors> SAMPLERS = new WeakHashMap<>();
    private BoundGlassesCamera(){}

    public static @Nullable UUID pairId(CompoundTag data){
        String id = data.getString("NodeId");
        if(id.isBlank()) id = data.getString("Value");
        if(!id.startsWith(PREFIX)) return null;
        try{ return UUID.fromString(id.substring(PREFIX.length())); }
        catch(IllegalArgumentException err){ return null; }
    }

    public static CompoundTag selection(AdvancedGraphDocument.Node node, AdvancedGraphDocument.Value target){
        if(target != null && (target.payload().contains("BlockPos") || !target.payload().getString("NodeId").isBlank()
                || !target.payload().getString("Value").isBlank())) return target.payload();
        return node.data().getCompound("TargetData");
    }

    public static @Nullable EntityViewSource source(AdvancedContraptionControllerBlockEntity controller, CompoundTag data){
        UUID pair = pairId(data);
        if(controller == null || pair == null || !controller.getGogglesTrackerPairLabels().containsKey(pair)
                || controller.getLevel() == null || controller.getLevel().getServer() == null) return null;
        var wearer = ControllerRuntimeObserver.findGogglesWearer(controller.getLevel().getServer(), pair);
        if(!(wearer instanceof Player player) || !player.isAlive()
                || !player.level().dimension().equals(controller.getLevel().dimension())
                || !pair.equals(ControllerRuntimeObserver.gogglesPairId(player))) return null;
        return new EntityViewSource(player);
    }

    public static CompoundTag frame(AdvancedContraptionControllerBlockEntity controller, CompoundTag data){
        var source = source(controller, data);
        CompoundTag frame = new CompoundTag();
        if(source != null){
            frame.putString("Format", "camera");
            frame.put("ViewSource", ViewReference.of(source.viewEntity()).toTag());
        }
        return frame;
    }

    public static AdvancedGraphDocument.Value value(AdvancedContraptionControllerBlockEntity controller,
            AdvancedGraphDocument.Node node, String port, Function<String, AdvancedGraphDocument.Value> input){
        var source = source(controller, selection(node, input.apply("target")));
        if(source == null) return com.rieno.gadgetsandgizmos.content.advanced.CameraGraphNodes.unavailable(port);
        int revision = controller.getActiveGraphRevision();
        Sensors sensors = SAMPLERS.get(controller);
        if(sensors == null || sensors.revision() != revision){
            sensors = new Sensors(revision, new HashMap<>());
            SAMPLERS.put(controller, sensors);
        }
        ViewRaySampler sampler = sensors.values().computeIfAbsent(node.id(), ignored -> new ViewRaySampler());
        sampler.sample(controller.getLevel(), source.viewPose(1), input.apply("max_ray_length").asNumber(),
                input.apply("rays_per_second").asNumber(),
                ViewRaycast.Filter.fromValue(GraphRuntime.toLibraryValue(input.apply("filter")),
                        "allowlist".equalsIgnoreCase(input.apply("filter_type").asString())), source.viewEntity().getUUID());
        var value = sampler.values(controller.getLevel()).get(port);
        return value == null ? com.rieno.gadgetsandgizmos.content.advanced.CameraGraphNodes.unavailable(port)
                : GraphRuntime.fromLibraryValue(value);
    }
    private record Sensors(int revision, Map<String, ViewRaySampler> values){}
}
