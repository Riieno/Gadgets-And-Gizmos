package com.rieno.gadgetsandgizmos.content.tablet;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.compat.simulated.SimulatedHelper;
import com.rieno.gadgetsandgizmos.content.CameraBlockEntity;
import com.rieno.gadgetsandgizmos.content.DiagnosticTabletAppStorage;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletAction;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletActionContext;
import com.rieno.gadgetsandgizmos.lib.tablet.TabletStorageApi;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlInput;
import com.rieno.gadgetsandgizmos.lib.view.ViewControlSessions;
import com.rieno.gadgetsandgizmos.lib.view.ViewReference;
import com.rieno.gadgetsandgizmos.lib.view.ViewSourceBindings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

// Bind loaded cameras to a tablet and authorize their individual controls
final class CctvApp{
    private CctvApp(){}

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle each action through the existing authenticated tablet channel
    static String execute(TabletActionContext ctx, TabletAction action) throws Exception{
        CompoundTag data = DiagnosticTabletAppStorage.data(ctx.player().server, ctx.sourceTabletId(), action.appId());
        ViewSourceBindings bindings = ViewSourceBindings.fromTag(data.getList("CameraSources", Tag.TAG_COMPOUND));
        String val = action.arguments().getOrDefault("value", "");
        String selected = data.getString("SelectedCamera");
        switch(action.actionId()){
            case "pair" -> {
                var selections = DiagnosticTabletAppStorage.selections(ctx.player().server, ctx.sourceTabletId(), action.appId());
                if(selections.isEmpty()) throw new IllegalArgumentException("Use Reader mode on a camera first");
                var binding = selections.getLast();
                var be = SimulatedHelper.findLoadedBlockEntityExact(ctx.player().level(), binding.subLevelId(), binding.pos());
                if(!(be instanceof CameraBlockEntity camera)) throw new IllegalArgumentException("CCTV can only bind cameras");
                TabletAppTargets.requireAccess(ctx, camera);
                if(ctx.player().distanceToSqr(camera.viewPose(0).position()) > 256){
                    throw new IllegalArgumentException("Bind the camera from within 16 blocks");
                }
                ViewReference ref = ViewReference.of(camera);
                bindings = bindings.add(ref, binding.label());
                selected = bindings.entries().getLast().key();
                DiagnosticTabletAppStorage.clearSelections(ctx.player().server, ctx.sourceTabletId(), action.appId());
            }
            case "select_camera" -> { requireBinding(bindings, val); selected = val; }
            case "forget" -> { requireBinding(bindings, val); bindings = bindings.remove(val); }
            case "rename" -> {
                String[] parts = val.split("\\|", 2);
                if(parts.length != 2) throw new IllegalArgumentException("Choose a camera and its name");
                requireBinding(bindings, parts[0]);
                bindings = bindings.rename(parts[0], parts[1]);
            }
            case "control" -> {
                String[] parts = val.split("\\|", 2);
                if(parts.length != 2 || parts[1].length() > 512) throw new IllegalArgumentException("Invalid camera controls");
                var entry = requireBinding(bindings, parts[0]);
                var src = entry.source().resolve(ctx.player().level());
                if(!(src instanceof CameraBlockEntity camera)) throw new IllegalArgumentException("The camera is unloaded or in another dimension");
                TabletAppTargets.requireAccess(ctx, camera);
                ViewControlInput input = ViewControlInput.fromTag(TagParser.parseTag(parts[1]));
                if(!ViewControlSessions.apply(ctx.player(), entry.source(), input)){
                    throw new IllegalArgumentException("The camera is being controlled by another player");
                }
                return "";
            }
            case "refresh" -> {}
            default -> throw new IllegalArgumentException("Unknown CCTV action");
        }
        boolean found = false;
        for(var entry : bindings.entries()) if(entry.key().equals(selected)) found = true;
        if(!found) selected = bindings.entries().isEmpty() ? "" : bindings.entries().getFirst().key();
        ViewSourceBindings next = bindings;
        String nextSelected = selected;
        if(!"refresh".equals(action.actionId())){
            TabletStorageApi.storage().updateApp(ctx.sourceTabletId(), action.appId(), tag -> {
                tag.put("CameraSources", next.toTag());
                tag.putString("SelectedCamera", nextSelected);
                return tag;
            });
        }
        PaidTabletApps.snapshot(ctx, action.appId(), snapshot(ctx, bindings, selected));
        return "";
    }
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            HELPERS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Keep camera bindings visible if an individual control command is denied
    static CompoundTag snapshot(TabletActionContext ctx){
        CompoundTag data = DiagnosticTabletAppStorage.data(ctx.player().server, ctx.sourceTabletId(), PaidTabletApps.CCTV.id());
        return snapshot(ctx, ViewSourceBindings.fromTag(data.getList("CameraSources", Tag.TAG_COMPOUND)),
                data.getString("SelectedCamera"));
    }
    private static CompoundTag snapshot(TabletActionContext ctx, ViewSourceBindings bindings, String selected){
        CompoundTag snapshot = new CompoundTag();
        ListTag cameras = new ListTag();
        for(var entry : bindings.entries()){
            CompoundTag tag = entry.source().toTag();
            tag.putString("Key", entry.key());
            tag.putString("Name", entry.name());
            var src = entry.source().resolve(ctx.player().level());
            if(src instanceof CameraBlockEntity camera && com.rieno.gadgetsandgizmos.lib.access.WorldAccessPolicy.canAccessLocal(
                    ctx.player(), ctx.player().serverLevel(), entry.source().subLevelId(), entry.source().blockPos())){
                tag.putBoolean("Available", true);
                tag.put("Controls", camera.viewControlState().toTag());
            }
            cameras.add(tag);
        }
        snapshot.put("Cameras", cameras);
        snapshot.putString("SelectedCamera", selected);
        snapshot.putString("Help", "Use Reader mode to bind cameras. Select a feed to control it.");
        return snapshot;
    }
    // Resolve controls only against sources already bound to this tablet
    private static ViewSourceBindings.Entry requireBinding(ViewSourceBindings bindings, String key){
        return bindings.entries().stream().filter(entry -> entry.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This camera is not bound to the tablet"));
    }
}
