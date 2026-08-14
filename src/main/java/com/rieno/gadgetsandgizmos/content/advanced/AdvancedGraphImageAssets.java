package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// Check and package uploaded graph images for manifests and HUD elements
public final class AdvancedGraphImageAssets {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String REFERENCE_PREFIX = "graph-image:";
    public static final String ASSET_ID = "ImageAssetId";
    public static final String MIME_TYPE = "ImageMimeType";
    public static final String BASE64_CHUNKS = "ImageBase64";
    private static final int NBT_STRING_CHUNK_LENGTH = 30_000;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced graph image assets
    private AdvancedGraphImageAssets() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Embed the Base64 data
    public static void embedBase64(CompoundTag imageData, String mimeType, String base64) {
        if (imageData == null || base64 == null || base64.isBlank()) {
            return;
        }
        String normalizedMime = normalizeMimeType(mimeType);
        String assetId = UUID.randomUUID().toString();
        imageData.putString("Source", REFERENCE_PREFIX + assetId);
        imageData.putString(ASSET_ID, assetId);
        imageData.putString(MIME_TYPE, normalizedMime);
        putChunks(imageData, base64);
    }

    // Check if this is embedded
    public static boolean isEmbedded(CompoundTag imageData) {
        return imageData != null && imageData.getString("Source").startsWith(REFERENCE_PREFIX);
    }

    // Get the asset id
    public static String assetId(CompoundTag imageData) {
        if (imageData == null) {
            return "";
        }
        String stored = imageData.getString(ASSET_ID);
        if (!stored.isBlank()) {
            return stored;
        }
        String src = imageData.getString("Source");
        return src.startsWith(REFERENCE_PREFIX) ? src.substring(REFERENCE_PREFIX.length()) : "";
    }

    // Get the mime type
    public static String mimeType(CompoundTag imageData) {
        return normalizeMimeType(imageData == null ? "" : imageData.getString(MIME_TYPE));
    }

    // Get the Base64 image data
    public static String base64(CompoundTag imageData) {
        if (imageData == null) {
            return "";
        }
        ListTag chunks = imageData.getList(BASE64_CHUNKS, Tag.TAG_STRING);
        if (chunks.isEmpty()) {
            DataUri dataUri = dataUri(imageData.getString("Source"));
            return dataUri == null ? "" : dataUri.base64();
        }
        StringBuilder joined = new StringBuilder(chunks.size() * NBT_STRING_CHUNK_LENGTH);
        for (int idx = 0; idx < chunks.size(); idx++) {
            joined.append(chunks.getString(idx));
        }
        return joined.toString();
    }

    // Get the separate
    public static StoredGraph separate(CompoundTag src) {
        CompoundTag graph = src == null ? new CompoundTag() : src.copy();
        normalizeLegacyDataUris(graph);
        List<ImageAsset> assets = assets(graph);
        visitImageData(graph, data -> data.remove(BASE64_CHUNKS));
        return new StoredGraph(graph, assets);
    }

    // Get the assets
    public static List<ImageAsset> assets(CompoundTag graph) {
        Map<String, ImageAsset> assets = new LinkedHashMap<>();
        visitImageData(graph, data -> {
            String encoded = base64(data);
            if (encoded.isBlank()) {
                return;
            }
            String id = assetId(data);
            String mime = mimeType(data);
            DataUri legacy = dataUri(data.getString("Source"));
            if (legacy != null) {
                mime = legacy.mimeType();
                if (id.isBlank()) {
                    id = legacyAssetId(mime, encoded);
                }
            }
            if (!id.isBlank()) {
                assets.put(id, new ImageAsset(id, mime, encoded));
            }
        });
        return List.copyOf(assets.values());
    }

    // Get the hydrate copy
    public static CompoundTag hydrateCopy(CompoundTag src, Collection<ImageAsset> assets) {
        CompoundTag graph = src == null ? new CompoundTag() : src.copy();
        Map<String, ImageAsset> byId = new LinkedHashMap<>();
        if (assets != null) {
            for (ImageAsset asset : assets) {
                if (asset != null && !asset.id().isBlank() && !asset.base64().isBlank()) {
                    byId.put(asset.id(), asset);
                }
            }
        }
        visitImageData(graph, data -> {
            ImageAsset asset = byId.get(assetId(data));
            if (asset == null) {
                return;
            }
            data.putString(MIME_TYPE, normalizeMimeType(asset.mimeType()));
            putChunks(data, asset.base64());
        });
        return graph;
    }

    // Normalize the legacy data uris
    private static void normalizeLegacyDataUris(CompoundTag graph) {
        visitImageData(graph, data -> {
            DataUri legacy = dataUri(data.getString("Source"));
            if (legacy == null) {
                return;
            }
            String id = legacyAssetId(legacy.mimeType(), legacy.base64());
            data.putString("Source", REFERENCE_PREFIX + id);
            data.putString(ASSET_ID, id);
            data.putString(MIME_TYPE, legacy.mimeType());
            putChunks(data, legacy.base64());
        });
    }

    // Put the chunks
    private static void putChunks(CompoundTag imageData, String base64) {
        ListTag chunks = new ListTag();
        for (int offset = 0; offset < base64.length(); offset += NBT_STRING_CHUNK_LENGTH) {
            chunks.add(StringTag.valueOf(base64.substring(
                    offset, Math.min(base64.length(), offset + NBT_STRING_CHUNK_LENGTH))));
        }
        imageData.put(BASE64_CHUNKS, chunks);
    }

    // Visit the image data
    private static void visitImageData(CompoundTag graph, Consumer<CompoundTag> visitor) {
        if (graph == null || graph.isEmpty()) {
            return;
        }
        visitNodeList(graph.getList("Nodes", Tag.TAG_COMPOUND), visitor);
        ListTag functions = graph.getList("Functions", Tag.TAG_COMPOUND);
        for (int idx = 0; idx < functions.size(); idx++) {
            visitNodeList(functions.getCompound(idx).getList("Nodes", Tag.TAG_COMPOUND), visitor);
        }
    }

    // Visit the node list
    private static void visitNodeList(ListTag nodes, Consumer<CompoundTag> visitor) {
        for (int idx = 0; idx < nodes.size(); idx++) {
            CompoundTag node = nodes.getCompound(idx);
            if ("image_reference".equals(node.getString("Type"))) {
                visitor.accept(node.getCompound("Data"));
            }
        }
    }

    // Get the data URI
    private static DataUri dataUri(String src) {
        if (src == null || !src.regionMatches(true, 0, "data:image/", 0, 11)) {
            return null;
        }
        int separator = src.indexOf(',');
        if (separator <= 0 || separator == src.length() - 1) {
            return null;
        }
        String header = src.substring(5, separator).toLowerCase(Locale.ROOT);
        if (!header.endsWith(";base64")) {
            return null;
        }
        String mime = normalizeMimeType(header.substring(0, header.length() - 7));
        return new DataUri(mime, src.substring(separator + 1));
    }

    // Get the legacy asset id
    private static String legacyAssetId(String mimeType, String base64) {
        return UUID.nameUUIDFromBytes(
                (mimeType + ":" + base64).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // Normalize the mime type
    private static String normalizeMimeType(String mimeType) {
        String normalized = mimeType == null ? "" : mimeType.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/jpeg", "image/jpg" -> "image/jpeg";
            case "image/gif" -> "image/gif";
            case "image/bmp" -> "image/bmp";
            default -> "image/png";
        };
    }

    // Store the image asset
    public record ImageAsset(String id, String mimeType, String base64) {
        // Initialize the image asset
        public ImageAsset {
            id = id == null ? "" : id.strip();
            mimeType = normalizeMimeType(mimeType);
            base64 = base64 == null ? "" : base64;
        }
    }

    // Store the stored graph
    public record StoredGraph(CompoundTag graph, List<ImageAsset> assets) {
        // Initialize the stored graph
        public StoredGraph {
            graph = graph == null ? new CompoundTag() : graph.copy();
            assets = List.copyOf(assets == null ? new ArrayList<>() : assets);
        }
    }

    // Store the data URI
    private record DataUri(String mimeType, String base64) {
    }
}
