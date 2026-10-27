package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.config.CTConfigs;
import com.rieno.gadgetsandgizmos.lib.graph.GraphValue;
import com.rieno.gadgetsandgizmos.lib.probe.BlockEntityDataProvider;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramChunk;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramEntity;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramRegion;
import com.rieno.gadgetsandgizmos.lib.hologram.SurfaceHologramCache;
import com.rieno.gadgetsandgizmos.lib.hologram.HologramSlice;
import com.rieno.gadgetsandgizmos.lib.hologram.SurfaceHologramSampler;
import com.rieno.gadgetsandgizmos.lib.physics.TransientLightBeam;
import com.rieno.gadgetsandgizmos.registry.CTBlocks;
import com.rieno.gadgetsandgizmos.lib.menuconfig.MenuConfigTarget;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.physics.SableTransformApi;
import com.rieno.gadgetsandgizmos.neoforge.network.WorldHologramDataPayload;
import com.rieno.gadgetsandgizmos.registry.CTBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Keep projector settings persistent and stream bounded server-owned terrain to nearby viewers
public final class WorldHologramProjectorBlockEntity extends SmartBlockEntity implements BlockEntityDataProvider{
    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        CONSTANTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private static final SurfaceHologramCache TERRAIN = new SurfaceHologramCache(512, 100, 2);
    private static final int MAX_ENTITIES = 256;
    private static final Map<String, String> DATA_PORTS = Map.of(
            "radius", "number", "height", "number", "size", "number", "y_offset", "number",
            "show_players", "boolean", "show_hostile_mobs", "boolean", "show_passive_mobs", "boolean", "show_sub_levels", "boolean");

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        VARIABLES
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    private WorldHologramSettings settings = WorldHologramSettings.DEFAULT;
    private final Map<UUID, Long> viewers = new HashMap<>();
    private final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
    private HologramRegion region;
    private boolean masked;
    private int revision;
    private HologramSlice slice = HologramSlice.SURFACE;
    private double surfaceY;
    private TransientLightBeam projectionLight;
    private long nextRefresh;

    /*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        FUNCTIONS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

    public WorldHologramProjectorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state){ super(type, pos, state); }
    public WorldHologramProjectorBlockEntity(BlockPos pos, BlockState state){ this(CTBlockEntities.WORLD_HOLOGRAM_PROJECTOR.get(), pos, state); }
    @Override public void addBehaviours(List<BlockEntityBehaviour> behaviours){}
    public WorldHologramSettings settings(){ return settings; }

    // Resolve root coordinates even while the projector is carried by a nested Sable sublevel
    public Vec3 worldCenter(){
        return SableTransformApi.projectOut(level, SableTransformApi.toWorldPosition(this, Vec3.atCenterOf(worldPosition)));
    }

    public boolean canView(Player player, double range){
        return player != null && level != null && !isRemoved() && player.level().dimension().equals(level.dimension())
                && SableTransformApi.distanceSquared(level, player.position(), worldCenter()) <= range * range;
    }

    // Read-only requests expire unless the client is still displaying the projection
    public void watch(ServerPlayer player){
        if(canView(player, 64)) viewers.put(player.getUUID(), level.getGameTime() + 100);
    }

    public void configure(Player player, WorldHologramSettings val){
        if(!canView(player, 8) || !player.mayBuild() || level.isClientSide) return;
        applySettings(val);
    }

    // Notify viewers once for a GUI edit or a batch from the controller's Set Data node
    private boolean applySettings(WorldHologramSettings val){
        if(settings.equals(val)) return false;
        settings = val;
        setChanged();
        sendData();
        return true;
    }

    @Override public Map<String, String> graphReadableData(){ return DATA_PORTS; }
    @Override public Map<String, String> graphWritableData(){ return DATA_PORTS; }

    @Override public GraphValue readGraphValue(String field){
        if(field == null) return null;
        return switch(field){
            case "radius" -> GraphValue.number(settings.radius());
            case "height" -> GraphValue.number(settings.height());
            case "size" -> GraphValue.number(settings.size());
            case "y_offset" -> GraphValue.number(settings.yOffset());
            case "show_players" -> GraphValue.bool(settings.players());
            case "show_hostile_mobs" -> GraphValue.bool(settings.hostile());
            case "show_passive_mobs" -> GraphValue.bool(settings.passive());
            case "show_sub_levels" -> GraphValue.bool(settings.ships());
            default -> null;
        };
    }

    @Override public boolean writeGraphValue(String field, GraphValue val){
        return field != null && val != null && writeGraphValues(Map.of(field, val));
    }

    // Preserve omitted settings and reject mismatched or nonfinite graph values
    @Override public boolean writeGraphValues(Map<String, GraphValue> values){
        if(!(level instanceof ServerLevel) || isRemoved() || values == null || values.isEmpty()) return false;
        WorldHologramSettings next = settings;
        boolean accepted = false;
        for(var entry : values.entrySet()){
            String field = entry.getKey();
            GraphValue val = entry.getValue();
            if(field == null || val == null || !val.type().equals(DATA_PORTS.get(field))) continue;
            if("number".equals(val.type()) && (!(val.value() instanceof Number) || !Double.isFinite(val.asNumber()))) continue;
            if("boolean".equals(val.type()) && !(val.value() instanceof Boolean)) continue;
            accepted = true;
            next = switch(field){
                case "radius" -> new WorldHologramSettings((int)Math.round(Math.clamp(val.asNumber(), 1, 9)), next.height(), next.size(), next.players(), next.hostile(), next.passive(), next.yOffset(), next.ships());
                case "height" -> new WorldHologramSettings(next.radius(), (float)Math.clamp(val.asNumber(), 0, 32), next.size(), next.players(), next.hostile(), next.passive(), next.yOffset(), next.ships());
                case "size" -> new WorldHologramSettings(next.radius(), next.height(), (float)Math.clamp(val.asNumber(), 0.5, 16), next.players(), next.hostile(), next.passive(), next.yOffset(), next.ships());
                case "y_offset" -> new WorldHologramSettings(next.radius(), next.height(), next.size(), next.players(), next.hostile(), next.passive(), (int)Math.round(Math.clamp(val.asNumber(), -512, 512)), next.ships());
                case "show_players" -> new WorldHologramSettings(next.radius(), next.height(), next.size(), val.asBoolean(), next.hostile(), next.passive(), next.yOffset(), next.ships());
                case "show_hostile_mobs" -> new WorldHologramSettings(next.radius(), next.height(), next.size(), next.players(), val.asBoolean(), next.passive(), next.yOffset(), next.ships());
                case "show_passive_mobs" -> new WorldHologramSettings(next.radius(), next.height(), next.size(), next.players(), next.hostile(), val.asBoolean(), next.yOffset(), next.ships());
                case "show_sub_levels" -> new WorldHologramSettings(next.radius(), next.height(), next.size(), next.players(), next.hostile(), next.passive(), next.yOffset(), val.asBoolean());
                default -> next;
            };
        }
        applySettings(next);
        return accepted;
    }

    // Share terrain sampling work across all projectors and keep unloaded chunks unavailable
    @Override
    public void tick(){
        super.tick();
        if(!(level instanceof ServerLevel server)) return;
        if(projectionLight == null) projectionLight = new TransientLightBeam(CTBlocks.PLUME_LIGHT.get().defaultBlockState());
        Vec3 lightPos = SableTransformApi.projectOut(level, SableTransformApi.toWorldPosition(this,
                worldPosition.getCenter().add(0, settings.height() - 0.25, 0)));
        projectionLight.updatePoint(server, lightPos);
        long now = level.getGameTime();
        viewers.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(entry.getKey());
            return entry.getValue() < now || player == null || !canView(player, 64);
        });
        if(viewers.isEmpty()) return;
        Vec3 center = worldCenter();
        HologramRegion current = new HologramRegion(new ChunkPos(BlockPos.containing(center)), settings.radius());
        boolean obfuscate = CTConfigs.SERVER.worldHologramObfuscateOres.get();
        boolean moved = !current.equals(region);
        if(moved || now % 20 == 0) surfaceY = SurfaceHologramSampler.averageHeight(server, current, center.y);
        HologramSlice selected = HologramSlice.at(surfaceY, settings.yOffset());
        boolean changed = !selected.equals(slice) || masked != obfuscate;
        if(moved || changed){
            region = current;
            slice = selected;
            masked = obfuscate;
            if(changed) revision++;
            pending.clear();
            nextRefresh = 0;
        }
        if(pending.isEmpty() && now >= nextRefresh){
            List<ChunkPos> positions = new ArrayList<>();
            for(int x = region.minX(); x <= region.maxX(); x++){
                for(int z = region.minZ(); z <= region.maxZ(); z++) positions.add(new ChunkPos(x, z));
            }
            positions.sort(Comparator.comparingInt(pos -> Math.abs(pos.x - region.center().x) + Math.abs(pos.z - region.center().z)));
            pending.addAll(positions);
            nextRefresh = now + 100;
        }
        HologramChunk chunk = null;
        if(!pending.isEmpty()){
            var sampled = TERRAIN.get(server, pending.peek(), masked, slice);
            if(sampled.isPresent()){ chunk = sampled.get(); pending.remove(); }
        }
        if(chunk == null && now % 10 != 0) return;
        List<HologramEntity> entities = now % 10 == 0 ? entities(server) : null;
        var payload = new WorldHologramDataPayload(MenuConfigTarget.of(worldPosition, SableLevelApi.containingId(this)),
                revision, surfaceY, slice, region, chunk, entities);
        for(UUID id : viewers.keySet()){
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(id);
            if(player != null) PacketDistributor.sendToPlayer(player, payload);
        }
    }

    // Include root-world mobs and players whose actual positions lie inside the selected area
    private List<HologramEntity> entities(ServerLevel server){
        AABB bounds = region.bounds(slice.surface() ? -30_000_000 : slice.minY(), slice.surface() ? 30_000_000 : slice.maxY());
        List<Entity> found = new ArrayList<>();
        if(settings.hostile() || settings.passive()){
            found.addAll(server.getEntities((Entity)null, bounds, entity -> entity instanceof Mob
                    && (entity instanceof Enemy ? settings.hostile() : settings.passive()) && !entity.isInvisible()));
        }
        if(settings.players()){
            for(Player player : server.players()){
                Vec3 pos = SableTransformApi.projectOut(server, player.position());
                if(!player.isSpectator() && !player.isInvisible() && bounds.contains(pos)) found.add(player);
            }
        }
        Vec3 center = worldCenter();
        return found.stream().sorted(Comparator.comparingDouble(entity ->
                        SableTransformApi.projectOut(server, entity.position()).distanceToSqr(center)))
                .limit(MAX_ENTITIES).map(entity -> HologramEntity.of(entity, SableTransformApi.projectOut(server, entity.position()))).toList();
    }

    // Release the real world light when the emitter is removed or unloaded
    @Override
    public void invalidate(){ if(projectionLight != null) projectionLight.clear(); super.invalidate(); }

    @Override
    public void remove(){ if(projectionLight != null) projectionLight.clear(); super.remove(); }

    // Projection bounds include the largest supported width and vertical offset
    @Override
    public AABB getRenderBoundingBox(){ return new AABB(worldPosition).inflate(16, 64, 16); }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.write(tag, provider, clientPacket);
        settings.write(tag);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider provider, boolean clientPacket){
        super.read(tag, provider, clientPacket);
        settings = WorldHologramSettings.read(tag);
    }
}
