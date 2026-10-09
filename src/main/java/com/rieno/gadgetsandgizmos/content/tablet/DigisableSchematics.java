package com.rieno.gadgetsandgizmos.content.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.config.TabletAppsServerConfig;
import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinEntity;
import com.rieno.gadgetsandgizmos.content.PlayerMannequinVariants;
import com.rieno.gadgetsandgizmos.content.WorkerStorageEndpoint;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.inventory.ContainerAccessRegistry;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicMaterials;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SchematicClientFiles;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematic;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicArchive;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicBuild;
import com.rieno.gadgetsandgizmos.lib.physics.archive.SubLevelSchematicFiles;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.registry.CTEntityTypes;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaterniond;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Connect Digisable gameplay policy and temporary worker presentation to the library schematic API
public final class DigisableSchematics{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Set<String> ACTIONS = Set.of("export", "schematic_preview", "materials", "prepare_build", "build", "pair_network", "cancel_build");
    // Own one material selection and active construction per player/tablet session
    private static final Map<MinecraftServer, Map<Key, Session>> SESSIONS = new WeakHashMap<>();

    private DigisableSchematics(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Identify schematic actions before the archive runtime parses their values
    static boolean handles(String action){ return ACTIONS.contains(action); }

    // Validate app actions and consume the reusable library services
    static String execute(TabletActionContext ctx, TabletAction action) throws Exception{
        if(!TabletAppsServerConfig.SCHEMATICS.get()) throw new IllegalArgumentException("Schematics are disabled by the server");
        Session session = session(ctx);
        String val = action.arguments().getOrDefault("value", "").strip();
        String message = "";
        boolean placement = false;
        switch(action.actionId()){
            case "export" -> {
                String[] args = val.split("\\|", 2);
                if(args.length != 2) throw new IllegalArgumentException("Enter a schematic name");
                throttle(ctx, session);
                var schematic = SubLevelSchematicArchive.capture(ctx.player(), UUID.fromString(args[0]),
                        TabletAppsServerConfig.BODIES.get(), TabletAppsServerConfig.BLOCKS.get());
                String name = SubLevelSchematicFiles.save(ctx.player().server, args[1], schematic);
                message = "Saved schematics/" + name;
            }
            case "pair_network" -> {
                TabletAppTargets.pair(ctx, PaidTabletApps.DIGISABLE.id());
                message = "Worker storage network paired";
            }
            case "schematic_preview", "materials", "prepare_build" -> {
                select(ctx, session, UUID.fromString(val), "schematic_preview".equals(action.actionId()));
                if("prepare_build".equals(action.actionId())){
                    requireReady(ctx, session);
                    placement = true;
                }
            }
            case "build" -> {
                if(session.build != null) throw new IllegalArgumentException("This tablet already has an active construction");
                long active = SESSIONS.get(ctx.player().server).values().stream().filter(row -> row.build != null).count();
                if(active >= 4 || SESSIONS.get(ctx.player().server).entrySet().stream()
                        .anyMatch(row -> row.getKey().player().equals(ctx.player().getUUID()) && row.getValue().build != null))
                    throw new IllegalArgumentException("Wait for an active schematic construction to finish");
                String[] args = val.split("\\|", 8);
                if(args.length != 8) throw new IllegalArgumentException("Choose the construction position in the world first");
                select(ctx, session, UUID.fromString(args[0]), false);
                requireReady(ctx, session);
                Vec3 target = new Vec3(Double.parseDouble(args[1]), Double.parseDouble(args[2]), Double.parseDouble(args[3]));
                Quaterniond turn = new Quaterniond(Double.parseDouble(args[4]), Double.parseDouble(args[5]), Double.parseDouble(args[6]), Double.parseDouble(args[7]));
                double range = Math.max(2, Math.min(64, TabletAppsServerConfig.STORE_RANGE.get()));
                if(!Double.isFinite(target.x + target.y + target.z) || ctx.player().getEyePosition().distanceToSqr(target) > range * range)
                    throw new IllegalArgumentException("Construction point is outside the server range");
                throttle(ctx, session);
                SchematicMaterials.Reservation materials = ctx.player().isCreative() ? null
                        : SchematicMaterials.reserve(ctx.player(), target, session.requirements, endpoints(ctx));
                try{
                    session.build = new SubLevelSchematicBuild(ctx.player(), session.schematic, target, turn, 4);
                    session.materials = materials;
                    session.ctx = ctx;
                    session.origin = target;
                    session.age = 0;
                    session.status = "Building schematic";
                    session.built = 0;
                    session.total = session.build.totalBlocks();
                    spawnWorkers(ctx, session, target);
                }catch(RuntimeException err){
                    abort(session);
                    if(materials != null) materials.refund();
                    throw err;
                }
                message = "Workers started construction";
            }
            case "cancel_build" -> {
                abort(session);
                session.status = "Construction cancelled; materials returned";
                message = session.status;
            }
            default -> throw new IllegalArgumentException("Unknown schematic action");
        }
        CompoundTag data = DigisableApp.snapshot(ctx);
        if(placement) data.putUUID("PlacementId", session.selected);
        if("build".equals(action.actionId())) data.putUUID("PlacementComplete", session.selected);
        PaidTabletApps.snapshot(ctx, action.appId(), data);
        return message;
    }

    // Append file, stock and construction snapshots without exposing inventories or serialized plots
    static void append(TabletActionContext ctx, CompoundTag data) throws IOException{
        data.putBoolean("SchematicsAllowed", TabletAppsServerConfig.SCHEMATICS.get());
        data.putBoolean("Creative", ctx.player().isCreative());
        if(!TabletAppsServerConfig.SCHEMATICS.get()) return;
        ListTag files = new ListTag();
        for(var file : SubLevelSchematicFiles.catalogue(ctx.player().server)){
            CompoundTag row = new CompoundTag(); row.putUUID("Id", file.id()); row.putString("Name", file.name()); files.add(row);
        }
        data.put("Schematics", files);
        data.putString("SharedSchematicFolder", ctx.player().server.isDedicatedServer() ? "server/schematics" : "world/schematics");
        Session session = session(ctx);
        List<WorkerStorageEndpoint> endpoints = List.of();
        try{
            endpoints = endpoints(ctx);
            data.putBoolean("NetworkBound", true);
            data.putInt("NetworkStorage", endpoints.size());
        }catch(IllegalArgumentException err){ data.putString("NetworkHelp", err.getMessage()); }
        if(session.selected != null && session.schematic != null){
            data.putUUID("SchematicSelected", session.selected);
            data.putBoolean("SchematicClient", session.client);
            data.putUUID("Selected", session.selected);
            data.putUUID("PreviewRoot", session.schematic.bodies().getFirst().id());
            data.put("Preview", session.schematic.preview(2048));
            ListTag rows = new ListTag();
            var shortages = SchematicMaterials.shortages(session.requirements, endpoints);
            for(var row : shortages){
                CompoundTag tag = new CompoundTag();
                var req = row.requirement();
                tag.putString("Name", req.stack().getHoverName().getString());
                tag.put("Stack", req.stack().saveOptional(ctx.player().registryAccess()));
                tag.putInt("Required", req.count());
                tag.putLong("Available", ctx.player().isCreative() ? req.count() : row.available());
                tag.putLong("Missing", ctx.player().isCreative() ? 0 : row.missing());
                tag.putBoolean("Tool", req.damage());
                rows.add(tag);
            }
            data.put("Materials", rows);
            if(!session.materialError.isBlank()) data.putString("MaterialError", session.materialError);
            data.putBoolean("BuildReady", session.build == null && (ctx.player().isCreative()
                    || session.materialError.isBlank() && !endpoints.isEmpty() && shortages.stream().noneMatch(row -> row.missing() > 0)));
        }
        data.putBoolean("Building", session.build != null);
        data.putInt("BuildPlaced", session.built); data.putInt("BuildTotal", session.total);
        data.putString("BuildStatus", session.status);
    }

    // Preserve the catalogue and material list when an app action fails
    static CompoundTag errorSnapshot(TabletActionContext ctx){
        try{ return DigisableApp.snapshot(ctx); }
        catch(Exception ignored){ return new CompoundTag(); }
    }

    // Pace server construction and move each worker through its own spatial section
    public static void onServerTick(ServerTickEvent.Post evt){
        Map<Key, Session> sessions = SESSIONS.get(evt.getServer());
        if(sessions == null) return;
        for(var iter = sessions.entrySet().iterator(); iter.hasNext();){
            var entry = iter.next();
            Session session = entry.getValue();
            if(evt.getServer().getPlayerList().getPlayer(entry.getKey().player()) == null){
                try{ abort(session); }
                finally{ iter.remove(); }
                continue;
            }
            if(session.build == null) continue;
            try{
                session.age++;
                int ticks = TabletAppsServerConfig.BUILD_TICKS.get();
                int desired = (int) Math.floor(session.total * Math.min(1D, (double) session.age / ticks));
                var progress = session.build.tickSections(Math.max(0, desired - session.build.placedBlocks()));
                session.built = session.build.placedBlocks();
                if(session.build.complete() && session.materials != null){ session.materials.commit(); session.materials = null; }
                animateWorkers(session, progress);
                if(session.build.complete()){
                    session.build.close(); session.build = null; session.materials = null;
                    removeWorkers(session);
                    session.status = "Schematic built";
                    session.ctx.player().displayClientMessage(Component.literal(session.status), true);
                }
                if(session.age % 20 == 0 || session.build == null)
                    PaidTabletApps.snapshot(session.ctx, PaidTabletApps.DIGISABLE.id(), DigisableApp.snapshot(session.ctx));
            }catch(Exception err){
                abort(session);
                session.status = "Construction stopped: " + (err.getMessage() == null ? "operation failed" : err.getMessage());
                if(session.ctx != null){
                    session.ctx.player().displayClientMessage(Component.literal(session.status), true);
                    PaidTabletApps.snapshot(session.ctx, PaidTabletApps.DIGISABLE.id(), errorSnapshot(session.ctx));
                }
            }
        }
    }

    // Roll back active constructions before the server performs its final world save
    public static void onServerStopping(ServerStoppingEvent evt){
        Map<Key, Session> sessions = SESSIONS.remove(evt.getServer());
        if(sessions != null) sessions.values().forEach(DigisableSchematics::abort);
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep placement tied to the validated preview until the player selects a file again
    private static void select(TabletActionContext ctx, Session session, UUID id, boolean refresh) throws IOException{
        if(session.build != null && !id.equals(session.selected)) throw new IllegalArgumentException("Wait for this construction to finish before selecting another schematic");
        if(!refresh && id.equals(session.selected) && session.schematic != null) return;
        boolean client = SchematicClientFiles.contains(ctx.player().server, ctx.player().getUUID(), id);
        SubLevelSchematic schematic = client
                ? SchematicClientFiles.load(ctx.player().serverLevel(), ctx.player().getUUID(), id,
                        TabletAppsServerConfig.BODIES.get(), TabletAppsServerConfig.BLOCKS.get())
                : SubLevelSchematicFiles.load(ctx.player().serverLevel(), id,
                        TabletAppsServerConfig.BODIES.get(), TabletAppsServerConfig.BLOCKS.get());
        session.client = client;
        session.selected = id; session.schematic = schematic; session.materialError = "";
        try{ session.requirements = SchematicMaterials.requirements(ctx.player().serverLevel(), schematic); }
        catch(IllegalArgumentException err){ session.requirements = List.of(); session.materialError = err.getMessage(); }
    }

    // Resolve only authorized real storage on the explicitly paired ACC network
    private static List<WorkerStorageEndpoint> endpoints(TabletActionContext ctx){
        var binding = DiagnosticTabletAppStorage.selectedBinding(ctx.player().server, ctx.sourceTabletId(), PaidTabletApps.DIGISABLE.id());
        var target = binding == null ? TabletAppTargets.support(ctx)
                : SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), binding.subLevelId(), binding.pos());
        TabletAppTargets.requireAccess(ctx, target);
        if(!(target instanceof AdvancedContraptionControllerBlockEntity acc)) throw new IllegalArgumentException("Pair an ACC worker storage network in Reader mode");
        Map<UUID, WorkerStorageEndpoint> res = new LinkedHashMap<>();
        for(WorkerStorageEndpoint endpoint : WorkerStorageEndpoint.linked(acc, false)){
            if(!endpoint.acceptsDelivery() || !endpoint.isAvailable() || endpoint.level() != ctx.player().serverLevel()) continue;
            if(!WorldAccessPolicy.canAccessLocal(ctx.player(), ctx.player().serverLevel(), endpoint.subLevelId(), endpoint.position())
                    || !ContainerAccessRegistry.canOpen(ctx.player(), endpoint.level(), endpoint.position())) continue;
            res.putIfAbsent(endpoint.storageId(), endpoint);
        }
        if(res.isEmpty()) throw new IllegalArgumentException("The paired ACC has no accessible loaded Worker item storage");
        return List.copyOf(res.values());
    }

    // Require survival stock while allowing creative players to build without a network
    private static void requireReady(TabletActionContext ctx, Session session){
        if(ctx.player().isCreative()) return;
        if(!session.materialError.isBlank()) throw new IllegalArgumentException(session.materialError);
        if(SchematicMaterials.shortages(session.requirements, endpoints(ctx)).stream().anyMatch(row -> row.missing() > 0))
            throw new IllegalArgumentException("Schematic materials are still missing; open Materials");
    }

    // Keep file writes and construction starts within the server operation cooldown
    private static void throttle(TabletActionContext ctx, Session session){
        long now = ctx.player().serverLevel().getGameTime();
        if(session.lastOperation != Long.MIN_VALUE && now - session.lastOperation < TabletAppsServerConfig.COOLDOWN.get()) throw new IllegalArgumentException("Wait before starting another schematic operation");
        session.lastOperation = now;
    }

    // Resolve this player's tablet session without sharing material selections between players
    private static Session session(TabletActionContext ctx){
        return SESSIONS.computeIfAbsent(ctx.player().server, val -> new HashMap<>())
                .computeIfAbsent(new Key(ctx.player().getUUID(), ctx.sourceTabletId()), val -> new Session());
    }

    // Assign a supporter worker to the first block in each independent section
    private static void spawnWorkers(TabletActionContext ctx, Session session, Vec3 target){
        var variants = new ArrayList<>(PlayerMannequinVariants.all());
        Util.shuffle(variants, ctx.player().getRandom());
        var sections = session.build.sectionProgress();
        for(int idx = 0; idx < sections.size(); idx++){
            PlayerMannequinEntity worker = CTEntityTypes.PLAYER_MANNEQUIN.get().create(ctx.player().serverLevel());
            if(worker == null) throw new IllegalArgumentException("Construction workers are unavailable");
            worker.makeConstructionVisual();
            worker.setSteveSkin(false);
            var variant = variants.get(idx % variants.size());
            worker.setVariant(variant);
            worker.setOriginalSupporterVariant(variant);
            worker.setCustomName(Component.literal("Construction Worker"));
            Vec3 point = sections.get(idx).nextBlock();
            Vec3 start = workerTarget(point == null ? target : point, idx);
            worker.setPos(start);
            worker.setWorkerCarryProp(new ItemStack(Items.STONE));
            if(!ctx.player().serverLevel().addFreshEntity(worker)) throw new IllegalArgumentException("Construction worker could not be spawned");
            session.workers.add(worker);
            session.targets.add(worker.position());
        }
    }

    // Follow each section's next block without orbiting a shared construction point
    private static void animateWorkers(Session session, List<SubLevelSchematicBuild.SectionProgress> progress){
        for(int idx = 0; idx < session.workers.size(); idx++){
            PlayerMannequinEntity worker = session.workers.get(idx);
            var section = progress.get(idx);
            Vec3 point = section.points().isEmpty() ? section.nextBlock() : section.points().getLast();
            if(point != null) session.targets.set(idx, workerTarget(point, idx));
            if(!section.points().isEmpty()){
                worker.startWorkerInteraction();
            }
            Vec3 delta = session.targets.get(idx).subtract(worker.position());
            double speed = 0.55 + idx * 0.07;
            Vec3 step = delta.scale(Math.min(0.28, speed / Math.max(0.001, delta.length())));
            worker.setPos(worker.position().add(step));
            Vec3 facing = point == null ? delta : point.subtract(worker.position());
            worker.setYRot((float) Math.toDegrees(Math.atan2(-facing.x, facing.z)));
            worker.setYHeadRot(worker.getYRot());
            worker.setWorkerAnimation(PlayerMannequinEntity.WorkerAnimation.CARRY_WALK);
        }
    }

    // Stand beside a section's active block with a stable approach direction
    private static Vec3 workerTarget(Vec3 point, int idx){
        double angle = idx * Math.PI / 2 + Math.PI / 4;
        return point.add(Math.cos(angle) * 1.2, 0.4, Math.sin(angle) * 1.2);
    }

    // Remove every temporary worker after success or cancellation
    private static void removeWorkers(Session session){
        session.workers.forEach(PlayerMannequinEntity::discard);
        session.workers.clear(); session.targets.clear();
    }

    // Remove partial blocks before returning reserved items
    private static void abort(Session session){
        try{ if(session.build != null) session.build.close(); }
        finally{
            session.build = null;
            try{ if(session.materials != null) session.materials.refund(); }
            finally{ session.materials = null; removeWorkers(session); }
        }
    }

    // Identify the player who owns a tablet session
    private record Key(UUID player, UUID tablet){}

    // Retain the selected template, reserved stock and temporary visual entities
    private static final class Session{
        private UUID selected;
        private boolean client;
        private SubLevelSchematic schematic;
        private List<SchematicMaterials.Requirement> requirements = List.of();
        private String materialError = "";
        private SubLevelSchematicBuild build;
        private SchematicMaterials.Reservation materials;
        private TabletActionContext ctx;
        private Vec3 origin = Vec3.ZERO;
        private final List<PlayerMannequinEntity> workers = new ArrayList<>();
        private final List<Vec3> targets = new ArrayList<>();
        private int age;
        private int built;
        private int total;
        private String status = "";
        private long lastOperation = Long.MIN_VALUE;
    }
}
