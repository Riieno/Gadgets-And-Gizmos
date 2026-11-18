package com.rieno.gadgetsandgizmos.compat.computercraft;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.content.AdvancedContraptionControllerBlockEntity;
import com.rieno.gadgetsandgizmos.content.ContraptionNetworkLinkerData;
import com.rieno.gadgetsandgizmos.content.ShipPermissions;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphDocument;
import com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy;
import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftAttachedStorage;
import com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftMountStorage;
import com.rieno.gadgetsandgizmos.lib.discovery.ControllerDiscoveryNode;
import com.rieno.gadgetsandgizmos.lib.physics.SableLevelApi;
import com.rieno.gadgetsandgizmos.lib.scm.ShipPermission;
import com.rieno.gadgetsandgizmos.lib.script.LuaScripts;
import com.rieno.gadgetsandgizmos.lib.script.ScriptEmptyStorage;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFileService;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFileStorage;
import com.rieno.gadgetsandgizmos.lib.script.ScriptFiles;
import com.rieno.gadgetsandgizmos.lib.script.ScriptInput;
import com.rieno.gadgetsandgizmos.lib.script.ScriptMountedStorage;
import com.rieno.gadgetsandgizmos.lib.script.ScriptRequest;
import com.rieno.gadgetsandgizmos.lib.script.ScriptTerminal;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.core.filesystem.MemoryMount;
import dan200.computercraft.shared.computer.blocks.AbstractComputerBlockEntity;
import dan200.computercraft.shared.peripheral.diskdrive.DiskDriveBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

// Supply ACC-authorized CC volumes to the shared file service and isolated script preview
public final class ComputerCraftControllerFiles{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final Map<ServerPlayer, Preview> PREVIEWS = new WeakHashMap<>();
    private static boolean installed;
    private ComputerCraftControllerFiles(){}
    private static final class Preview{
        private final AdvancedContraptionControllerBlockEntity controller;
        private final Object menu;
        private final String root, path, token;
        private final ComputerCraftLuaComputer session;
        private ScriptTerminal terminal;
        private String error = "";
        private int expires = 20;
        private Preview(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player, String root, String path, String token, ComputerCraftLuaComputer session){
            this.controller = controller; this.menu = player.containerMenu; this.root = root; this.path = path; this.token = token; this.session = session;
        }
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static void install(){
        if(installed) return;
        installed = true; NeoForge.EVENT_BUS.addListener(ComputerCraftControllerFiles::tick); NeoForge.EVENT_BUS.addListener(ComputerCraftControllerFiles::stopped);
    }

    public static CompoundTag handle(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player, CompoundTag req){
        if("preview_close".equals(req.getString("Action"))){ closePreview(player, req.getString("PreviewId")); return success(req); }
        List<ComputerCraftAttachedStorage> attached = new ArrayList<>();
        try{
            Map<String, ScriptFileService.Root> roots = roots(controller, player, attached);
            String action = req.getString("Action");
            if(action.equals("catalog")) return ScriptFileService.catalog(roots);
            if(action.equals("reveal")){
                if(player.server.isDedicatedServer() || !player.server.isSingleplayerOwner(player.getGameProfile()))
                    throw new IOException("File explorer is available to the single-player host only");
                return ScriptFileService.reveal(roots, req);
            }
            if(!action.startsWith("preview")) return ScriptFileService.handle(roots, req);
            String root = req.getString("Root"), path = ScriptFiles.path(req.getString("Path"));
            var volume = roots.get(root);
            if(volume == null) throw new IOException("Storage is no longer available");
            if(action.equals("preview")){
                close(player);
                String stored = ScriptFileService.text(volume.storage(), path);
                String source = req.contains("Draft") ? req.getString("Draft") : stored;
                if(source.length() + 14 > LuaScripts.MAX_SOURCE_LENGTH) throw new IOException("The script is too large to preview");
                var mount = new MemoryMount(ScriptFiles.MAX_COPY_BYTES + 1024 * 1024);
                ScriptFiles.snapshot(volume.storage(), new ComputerCraftMountStorage(mount), Set.of("rom", "startup", "startup.lua", ".settings"));
                var id = UUID.randomUUID(); var session = new ComputerCraftLuaComputer(controller, id, mount);
                try{ session.submit(new ScriptRequest(id, 1, "term.attach()\n" + source, Map.of(), Map.of())); }
                catch(RuntimeException ex){ session.close(); throw ex; }
                PREVIEWS.put(player, new Preview(controller, player, root, path, req.getString("PreviewId"), session));
            }
            var preview = PREVIEWS.get(player);
            if(preview == null || preview.controller != controller || preview.menu != player.containerMenu || !preview.root.equals(root)
                    || !preview.path.equals(path) || !preview.token.equals(req.getString("PreviewId")))
                throw new IOException("Choose a file to load in the preview");
            preview.expires = 20;
            if(action.equals("preview_input")) preview.session.input(player, new ScriptInput(req.getString("Input"), req.getDouble("X"), req.getDouble("Y"), req.getInt("Value"), req.getString("Text")));
            var res = success(req); if(preview.terminal != null) res.put("Terminal", preview.terminal.displayFrame()); res.putString("Error", preview.error); return res;
        }catch(IOException | IllegalArgumentException ex){
            var res = req.copy(); res.putBoolean("Success", false); res.putString("Message", ex.getMessage() == null ? "Storage is unavailable" : ex.getMessage()); return res;
        }finally{ for(var storage : attached) storage.close(); }
    }

    private static CompoundTag success(CompoundTag req){ var res = req.copy(); res.putBoolean("Success", true); return res; }
    private static void close(ServerPlayer player){ var prev = PREVIEWS.remove(player); if(prev != null) prev.session.close(); }

    // Closing an owned preview must still work after its target or permissions disappear
    public static void closePreview(ServerPlayer player, String token){
        var preview = PREVIEWS.get(player);
        if(preview != null && preview.token.equals(token)) close(player);
    }

    private static void tick(ServerTickEvent.Post evt){
        var iterator = PREVIEWS.entrySet().iterator();
        while(iterator.hasNext()){
            var entry = iterator.next(); var player = entry.getKey(); var preview = entry.getValue();
            if(player.getServer() != evt.getServer()) continue;
            if(player.hasDisconnected() || preview.controller.isRemoved() || player.containerMenu != preview.menu || --preview.expires <= 0){
                preview.session.close(); iterator.remove(); continue;
            }
            preview.session.tick();
            preview.terminal = preview.session.terminal();
            for(var res = preview.session.poll(); res != null; res = preview.session.poll()){
                if(res.outputs().containsKey(ScriptTerminal.PORT)) preview.terminal = ScriptTerminal.fromValue(res.outputs().get(ScriptTerminal.PORT));
                if(!res.valid()) preview.error = res.error();
            }
        }
    }

    private static void stopped(ServerStoppedEvent evt){
        PREVIEWS.entrySet().removeIf(entry -> {
            if(entry.getKey().getServer() != evt.getServer()) return false;
            entry.getValue().session.close(); return true;
        });
    }

    private static Map<String, ScriptFileService.Root> roots(AdvancedContraptionControllerBlockEntity controller, ServerPlayer player,
                                                            List<ComputerCraftAttachedStorage> attached) throws IOException{
        Map<String, BlockEntity> targets = new LinkedHashMap<>(); var level = controller.getLevel();
        if(level == null) return Map.of();
        for(Direction side : Direction.values()){
            var pos = controller.getBlockPos().relative(side);
            if(level.hasChunkAt(pos)) add(targets, level.getBlockEntity(pos));
        }
        for(var linked : controller.getLinkedTargets()){
            if(linked.scope().usesFaces()) for(var face : linked.faces()) addLinked(targets, controller, linked, face.face());
            else addLinked(targets, controller, linked, null);
        }
        Map<String, ScriptFileService.Root> roots = new LinkedHashMap<>();
        var ordered = new ArrayList<>(targets.entrySet());
        ordered.sort(Comparator.comparingInt(entry -> entry.getValue() instanceof AbstractComputerBlockEntity computer ? computer.getComputerID() : Integer.MAX_VALUE));
        for(var entry : ordered){
            var target = entry.getValue();
            if(!allowed(player, target)) continue;
            if(target instanceof AbstractComputerBlockEntity computer){
                if(computer.isAdminOnly() && !player.hasPermissions(2)) continue;
                var server = computer.createServerComputer();
                var live = com.rieno.gadgetsandgizmos.lib.compat.computercraft.ComputerCraftFileStorage.of(server);
                var storage = new ScriptMountedStorage(live == null ? new ComputerCraftMountStorage(server.createRootMount()) : live);
                var rom = ComputerCraftAPI.createResourceMount(level.getServer(), "computercraft", "lua/rom");
                if(live == null && rom != null) storage.mount("rom", new ComputerCraftMountStorage(rom));
                var folders = new ArrayList<ScriptFileService.Folder>();
                int disks = 0; Set<BlockEntity> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                for(var side : ComputerSide.values()){
                    var peripheral = server.getPeripheral(side);
                    if(peripheral == null || !(peripheral.getTarget() instanceof DiskDriveBlockEntity drive) || !seen.add(drive) || !allowed(player, drive)) continue;
                    String path = disks++ == 0 ? "disk" : "disk" + disks;
                    if(live == null || !live.exists(path)) storage.mount(path, disk(drive, attached));
                    folders.add(new ScriptFileService.Folder("Computer " + server.getID() + " / " + drive.getItem(0).getHoverName().getString(), path));
                }
                roots.put(entry.getKey(), new ScriptFileService.Root("Computer " + server.getID(), storage, folders));
            }else if(target instanceof DiskDriveBlockEntity drive){
                String label = drive.getItem(0).getHoverName().getString();
                var media = dan200.computercraft.shared.platform.PlatformHelper.get().getMedia(drive.getItem(0));
                if(media != null){ String name = media.getLabel(level.registryAccess(), drive.getItem(0)); if(name != null && !name.isBlank()) label = name; }
                roots.put(entry.getKey(), new ScriptFileService.Root(drive.getItem(0).isEmpty() ? "Disk Drive (empty)" : label, disk(drive, attached), List.of(new ScriptFileService.Folder(label, ""))));
            }
        }
        return roots;
    }

    private static ScriptFileStorage disk(DiskDriveBlockEntity drive, List<ComputerCraftAttachedStorage> attached){
        var storage = new ComputerCraftAttachedStorage(drive.peripheral()); attached.add(storage);
        return storage.mounts().values().stream().findFirst().orElseGet(ScriptEmptyStorage::new);
    }
    // Follow the same selected linker face and plane support as ACC data access
    private static void addLinked(Map<String, BlockEntity> targets, AdvancedContraptionControllerBlockEntity controller,
                                  ContraptionNetworkLinkerData.LinkedTarget linked, Direction face){
        var selected = new ControllerDiscoveryNode(ContraptionNetworkLinkerData.nodeIdForTarget(linked), linked.mode().discoveryKind(), "",
                linked.blockId(), linked.label(), linked.subLevelId(), linked.blockPos());
        var data = new CompoundTag();
        if(face != null){ var faces = new CompoundTag(); faces.putString(selected.nodeId(), face.getSerializedName()); data.put(AdvancedGraphCatalog.DATA_TARGET_FACES_TAG, faces); }
        var node = new AdvancedGraphDocument.Node("storage", "", "", 0, 0, data);
        var resolved = AdvancedContraptionControllerBlockEntity.resolveGraphDataTarget(controller.getLevel(), node, selected);
        if(resolved != null) add(targets, resolved.level().getBlockEntity(resolved.pos()));
    }
    private static boolean allowed(ServerPlayer player, BlockEntity target){
        return target != null && !target.isRemoved() && target.getLevel() != null
                && WorldAccessPolicy.canAccessLocal(player, player.serverLevel(), SableLevelApi.containingId(target), target.getBlockPos())
                && ShipPermissions.allows(player, target.getLevel(), target.getBlockPos(), ShipPermission.INTERACT);
    }
    private static void add(Map<String, BlockEntity> targets, BlockEntity target){
        if(target instanceof AbstractComputerBlockEntity || target instanceof DiskDriveBlockEntity) targets.putIfAbsent(ViewReference.of(target).toTag().toString(), target);
    }
}
