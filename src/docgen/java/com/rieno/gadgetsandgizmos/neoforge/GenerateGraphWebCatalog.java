package com.rieno.gadgetsandgizmos.neoforge;

import com.google.gson.GsonBuilder;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

// Package a complete catalog for web hosts which do not run a Minecraft server
public final class GenerateGraphWebCatalog{
    private GenerateGraphWebCatalog(){}

    public static void main(String[] args) throws Exception{
        if(args.length != 1) throw new IllegalArgumentException("usage: GenerateGraphWebCatalog <web-directory>");
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.neoforged.fml.ModList.of(List.of(), List.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var catalog = ControllerGraphWebServer.nodeCatalogJson();
        catalog.addProperty("sourceBuild", AdvancedGraphCatalog.get("worker_routine") == null ? "stable" : "experimental");
        Path output = Path.of(args[0]);Files.createDirectories(output);
        Files.writeString(output.resolve("nodes.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(catalog) + "\n", StandardCharsets.UTF_8);
        try(var input = GenerateGraphWebCatalog.class.getResourceAsStream("/assets/gadgetsngizmos/web/graph-nodes.js")){
            if(input == null) throw new IllegalStateException("The library web graph API is missing");
            Files.copy(input, output.resolve("graph-nodes.js"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
