package com.rieno.gadgetsandgizmos.neoforge.client;

import com.rieno.gadgetsandgizmos.content.ControllerManifestStore;
import com.rieno.gadgetsandgizmos.lib.storage.SharedDocumentMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.nio.file.Path;
import java.util.List;

// Resolve private graph files from the client instance regardless of the connected server.
public final class ClientSharedGraphs{
    private ClientSharedGraphs(){}

    public static Path directory(){ return Minecraft.getInstance().gameDirectory.toPath().resolve("shared_graphs").toAbsolutePath().normalize(); }

    public static SharedDocumentMetadata metadata(){
        var user = Minecraft.getInstance().getUser();
        return ControllerManifestStore.sharingMetadata(user.getName(), user.getProfileId());
    }

    public static List<ControllerManifestStore.SharedGraphEntry> catalogue(){ return ControllerManifestStore.listSharedGraphs(directory()); }

    public static ControllerManifestStore.SharedGraphSaveResult save(String name, CompoundTag data, ControllerManifestStore.SharedGraphSaveMode mode){
        return ControllerManifestStore.saveSharedGraph(directory(), name, data, mode, metadata());
    }

    public static ControllerManifestStore.ManifestSnapshot load(String id){ return ControllerManifestStore.loadSharedGraph(directory(), id); }
    public static boolean rename(String id, String name){ return ControllerManifestStore.renameSharedGraph(directory(), id, name); }
    public static boolean delete(String id){ return ControllerManifestStore.deleteSharedGraph(directory(), id); }
}
