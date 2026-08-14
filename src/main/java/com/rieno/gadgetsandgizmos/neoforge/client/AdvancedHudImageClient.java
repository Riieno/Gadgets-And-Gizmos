package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.platform.NativeImage;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedGraphImageAssets;
import com.rieno.gadgetsandgizmos.content.advanced.AdvancedHudImageStore;
import com.rieno.gadgetsandgizmos.neoforge.network.AdvancedHudImageRequestPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// Decode bounded graph HUD images once and reuse their client textures until the payload changes
public final class AdvancedHudImageClient {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final int MAX_REDIRECTS = 4;
    private static final int MAX_REMOTE_SOURCE_LENGTH = 2_048;
    private static final int MAX_PENDING_IMAGES = 36;
    private static final int MAX_CONCURRENT_DOWNLOADS = 4;
    private static final int MAX_QUEUED_DOWNLOADS = 32;
    private static final int MAX_TEXTURES = 64;
    private static final int MAX_DOWNLOADS = 16;
    private static final int MAX_TRANSFER_CHUNKS = (AdvancedHudImageStore.MAX_IMAGE_BYTES
            + AdvancedHudImageStore.TRANSFER_CHUNK_BYTES - 1)
            / AdvancedHudImageStore.TRANSFER_CHUNK_BYTES;
    private static final long MAX_CACHED_PIXELS = 32L * 1024L * 1024L;
    private static final long TRANSFER_TIMEOUT_MILLIS = 30_000L;
    private static final Map<String, CachedTexture> TEXTURES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<String> REQUESTED = new HashSet<>();
    private static final Map<UUID, DownloadAssembly> DOWNLOADS = new HashMap<>();
    private static final Set<HttpURLConnection> ACTIVE_CONNECTIONS = ConcurrentHashMap.newKeySet();
    private static final AtomicLong CACHE_GENERATION = new AtomicLong();
    private static final AtomicInteger DOWNLOAD_THREAD = new AtomicInteger();
    private static final ThreadPoolExecutor DOWNLOAD_EXECUTOR = createDownloadExecutor();
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Cached pixels
    private static long cachedPixels;
    // Tracked uploaded images
    private static volatile List<String> uploadedImages = List.of();

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD image client
    private AdvancedHudImageClient() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Draw the advanced HUD image client
    public static boolean draw(GuiGraphics graphics, String src,
                               int x, int y, int width, int height) {
        ResourceLocation texture = resolve(src);
        if (texture == null) {
            return false;
        }
        graphics.blit(texture, x, y, 0, 0, width, height, width, height);
        return true;
    }

    // Resolve the texture
    public static ResourceLocation resolveTexture(String src) {
        return resolve(src);
    }

    // Check if this has loaded texture
    public static boolean hasLoadedTexture(String src) {
        return src != null && loadedTexture(src.trim()) != null;
    }

    // Draw the advanced HUD image client
    public static boolean draw(GuiGraphics graphics, CompoundTag imageData,
                               int x, int y, int width, int height) {
        ResourceLocation texture = resolve(imageData);
        if (texture == null) {
            return false;
        }
        graphics.blit(texture, x, y, 0, 0, width, height, width, height);
        return true;
    }

    // Get the uploaded images
    public static List<String> uploadedImages() {
        return uploadedImages;
    }

    // Request the catalog
    public static void requestCatalog() {
        PacketDistributor.sendToServer(new AdvancedHudImageRequestPayload(""));
    }

    // Apply the catalog
    public static void applyCatalog(List<String> images) {
        List<String> sorted = new ArrayList<>(images == null ? List.of() : images);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        uploadedImages = List.copyOf(sorted);
    }

    // Clear the server images
    public static void clearServerImages() {
        CACHE_GENERATION.incrementAndGet();
        DOWNLOAD_EXECUTOR.getQueue().clear();
        for (HttpURLConnection connection : ACTIVE_CONNECTIONS) {
            connection.disconnect();
        }
        Minecraft minecraft = Minecraft.getInstance();
        List<ResourceLocation> textures;
        synchronized (TEXTURES) {
            textures = TEXTURES.values().stream().map(CachedTexture::location).toList();
            TEXTURES.clear();
            cachedPixels = 0L;
        }
        for (ResourceLocation texture : textures) {
            minecraft.getTextureManager().release(texture);
        }
        synchronized (REQUESTED) {
            REQUESTED.clear();
        }
        synchronized (DOWNLOADS) {
            DOWNLOADS.clear();
        }
        uploadedImages = List.of();
    }

    // Accept the image chunk
    public static void acceptImageChunk(String filename, UUID transferId,
                                        int chunkIndex, int chunkCount, byte[] data) {
        acceptSourceImageChunk(AdvancedHudImageStore.reference(filename), transferId,
                chunkIndex, chunkCount, data);
    }

    // Accept the source image chunk
    private static void acceptSourceImageChunk(String src, UUID transferId,
                                               int chunkIndex, int chunkCount, byte[] data) {
        if (src == null || src.isBlank() || transferId == null
                || chunkCount <= 0 || chunkCount > MAX_TRANSFER_CHUNKS
                || chunkIndex < 0 || chunkIndex >= chunkCount
                || data == null || data.length == 0
                || data.length > AdvancedHudImageStore.TRANSFER_CHUNK_BYTES) {
            return;
        }
        byte[] imageData = null;
        synchronized (DOWNLOADS) {
            cleanupExpiredTransfers();
            DownloadAssembly assembly = DOWNLOADS.get(transferId);
            if (assembly == null) {
                if (DOWNLOADS.size() >= MAX_DOWNLOADS) {
                    return;
                }
                assembly = new DownloadAssembly(src, chunkCount);
                DOWNLOADS.put(transferId, assembly);
            }
            if (!assembly.accept(src, chunkIndex, chunkCount, data)) {
                DOWNLOADS.remove(transferId);
                return;
            }
            if (assembly.complete()) {
                DOWNLOADS.remove(transferId);
                imageData = assembly.join();
            }
        }
        if (imageData != null) {
            loadBytes(src, imageData, CACHE_GENERATION.get());
        }
    }

    // Resolve the advanced HUD image client
    private static ResourceLocation resolve(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        String normalized = src.trim();
        ResourceLocation loaded = loadedTexture(normalized);
        if (loaded != null) {
            return loaded;
        }
        if (AdvancedHudImageStore.referencedName(normalized).isPresent()) {
            if (request(normalized)) {
                PacketDistributor.sendToServer(new AdvancedHudImageRequestPayload(
                        AdvancedHudImageStore.referencedName(normalized).orElseThrow()));
            }
            return null;
        }
        if (normalized.regionMatches(true, 0, "data:image/", 0, 11)) {
            long maxLength = (AdvancedHudImageStore.MAX_IMAGE_BYTES * 4L / 3L) + 80L;
            if (normalized.length() > maxLength) {
                return null;
            }
            long generation = CACHE_GENERATION.get();
            if (request(normalized)) {
                submitDownload(normalized, generation, () -> decodeDataUri(normalized, generation));
            }
            return null;
        }
        if (normalized.regionMatches(true, 0, "http://", 0, 7)
                || normalized.regionMatches(true, 0, "https://", 0, 8)) {
            if (normalized.length() > MAX_REMOTE_SOURCE_LENGTH) {
                return null;
            }
            long generation = CACHE_GENERATION.get();
            if (request(normalized)) {
                submitDownload(normalized, generation, () -> downloadUrl(normalized, generation));
            }
            return null;
        }
        return ResourceLocation.tryParse(normalized);
    }

    // Resolve the advanced HUD image client
    private static ResourceLocation resolve(CompoundTag imageData) {
        if (imageData == null || !AdvancedGraphImageAssets.isEmbedded(imageData)) {
            return resolve(imageData == null ? "" : imageData.getString("Source"));
        }
        String src = imageData.getString("Source");
        ResourceLocation loaded = loadedTexture(src);
        if (loaded != null) {
            return loaded;
        }
        long generation = CACHE_GENERATION.get();
        if (request(src)) {
            CompoundTag payload = imageData.copy();
            submitDownload(src, generation, () -> decodeGraphImage(src, payload, generation));
        }
        return null;
    }

    // Decode the graph image
    private static void decodeGraphImage(String src, CompoundTag imageData, long generation) {
        try {
            String encoded = AdvancedGraphImageAssets.base64(imageData);
            if (encoded.isBlank()
                    || encoded.length() > (AdvancedHudImageStore.MAX_IMAGE_BYTES * 4L / 3L) + 16L) {
                throw new IOException("Embedded image is too large.");
            }
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length == 0 || data.length > AdvancedHudImageStore.MAX_IMAGE_BYTES) {
                throw new IOException("Embedded image is too large.");
            }
            decodeBytes(src, data, generation);
        } catch (Exception ignored) {
            removeRequest(src, generation);
        }
    }

    // Decode the data URI
    private static void decodeDataUri(String src, long generation) {
        try {
            int separator = src.indexOf(',');
            if (separator <= 0 || separator > 64
                    || !src.substring(0, separator).toLowerCase(Locale.ROOT)
                    .matches("data:image/(png|jpeg|jpg|gif|bmp);base64")) {
                throw new IOException("Unsupported embedded image.");
            }
            String encoded = src.substring(separator + 1);
            if (encoded.length() > (AdvancedHudImageStore.MAX_IMAGE_BYTES * 4L / 3L) + 16L) {
                throw new IOException("Embedded image is too large.");
            }
            byte[] data = Base64.getDecoder().decode(encoded);
            if (data.length == 0 || data.length > AdvancedHudImageStore.MAX_IMAGE_BYTES) {
                throw new IOException("Embedded image is too large.");
            }
            decodeBytes(src, data, generation);
        } catch (Exception ignored) {
            removeRequest(src, generation);
        }
    }

    // Download the URL
    private static void downloadUrl(String src, long generation) {
        try {
            URI uri = URI.create(src);
            for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
                ensureCurrentGeneration(generation);
                validateRemoteUri(uri);
                HttpURLConnection connection = openConnection(uri, generation);
                try {
                    int status = connection.getResponseCode();
                    if (isRedirect(status)) {
                        if (redirects == MAX_REDIRECTS) {
                            throw new IOException("Image URL redirected too many times.");
                        }
                        String location = connection.getHeaderField("Location");
                        if (location == null || location.isBlank()) {
                            throw new IOException("Image URL redirect is missing a location.");
                        }
                        URI redirected = uri.resolve(location);
                        if ("https".equalsIgnoreCase(uri.getScheme())
                                && !"https".equalsIgnoreCase(redirected.getScheme())) {
                            throw new IOException("Image URL cannot redirect to an insecure connection.");
                        }
                        uri = redirected;
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        throw new IOException("Image server returned HTTP " + status + ".");
                    }
                    long declaredLength = connection.getContentLengthLong();
                    if (declaredLength > AdvancedHudImageStore.MAX_IMAGE_BYTES) {
                        throw new IOException("Remote image is too large.");
                    }
                    try (InputStream input = connection.getInputStream()) {
                        byte[] data = input.readNBytes(AdvancedHudImageStore.MAX_IMAGE_BYTES + 1);
                        if (data.length == 0 || data.length > AdvancedHudImageStore.MAX_IMAGE_BYTES) {
                            throw new IOException("Remote image is too large.");
                        }
                        decodeBytes(src, data, generation);
                    }
                    return;
                } finally {
                    ACTIVE_CONNECTIONS.remove(connection);
                    connection.disconnect();
                }
            }
        } catch (Exception ignored) {
            removeRequest(src, generation);
        }
    }

    // Open the connection
    private static HttpURLConnection openConnection(URI uri, long generation) throws IOException {
        URLConnection rawConnection = uri.toURL().openConnection();
        if (!(rawConnection instanceof HttpURLConnection connection)) {
            throw new IOException("Unsupported image URL.");
        }
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "Create-Thrusters-HUD/1");
        connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif,image/bmp,*/*;q=0.1");
        connection.setRequestProperty("Accept-Encoding", "identity");
        ACTIVE_CONNECTIONS.add(connection);
        if (generation != CACHE_GENERATION.get()) {
            ACTIVE_CONNECTIONS.remove(connection);
            connection.disconnect();
            throw new IOException("Image request was cancelled.");
        }
        return connection;
    }

    // Validate the remote URI
    private static void validateRemoteUri(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getRawUserInfo() != null) {
            throw new IOException("Unsupported image URL.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || uri.getPort() == 0 || uri.getPort() > 65_535) {
            throw new IOException("Invalid image URL.");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) {
            throw new IOException("Image URL has no address.");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IOException("Image URL points to a private address.");
            }
        }
    }

    // Check if this is a public address
    private static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte[] val = address.getAddress();
        if (isIpv4Mapped(val)) {
            return isPublicIpv4(Arrays.copyOfRange(val, 12, 16));
        }
        if ((val[0] & 0xE0) != 0x20) {
            return false;
        }
        int first = val[0] & 0xFF;
        int second = val[1] & 0xFF;
        int third = val[2] & 0xFF;
        int fourth = val[3] & 0xFF;
        if (first == 0x20 && second == 0x01
                && ((third & 0xFE) == 0 || (third == 0x0D && fourth == 0xB8))) {
            return false;
        }
        if (first == 0x20 && second == 0x02) {
            return false;
        }
        return !(first == 0x3F && second == 0xFF && (third & 0xF0) == 0);
    }

    // Check if this is a public IPv4 address
    private static boolean isPublicIpv4(byte[] val) {
        int first = val[0] & 0xFF;
        int second = val[1] & 0xFF;
        int third = val[2] & 0xFF;
        if (first == 0 || first == 10 || first == 127 || first >= 224) {
            return false;
        }
        if (first == 100 && (second & 0xC0) == 0x40) {
            return false;
        }
        if (first == 169 && second == 254) {
            return false;
        }
        if (first == 172 && (second & 0xF0) == 16) {
            return false;
        }
        if (first == 192 && (second == 168 || (second == 0 && third == 0)
                || (second == 0 && third == 2) || (second == 88 && third == 99))) {
            return false;
        }
        if (first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100))) {
            return false;
        }
        return !(first == 203 && second == 0 && third == 113);
    }

    // Check if the IPv4 address is mapped
    private static boolean isIpv4Mapped(byte[] val) {
        if (val.length != 16 || val[10] != (byte) 0xFF || val[11] != (byte) 0xFF) {
            return false;
        }
        for (int idx = 0; idx < 10; idx++) {
            if (val[idx] != 0) {
                return false;
            }
        }
        return true;
    }

    // Check if this is redirect
    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307 || status == 308;
    }

    // Ensure the current generation
    private static void ensureCurrentGeneration(long generation) throws IOException {
        if (generation != CACHE_GENERATION.get()) {
            throw new IOException("Image request was cancelled.");
        }
    }

    // Load the bytes
    private static void loadBytes(String src, byte[] data, long generation) {
        submitDownload(src, generation, () -> decodeBytes(src, data, generation));
    }

    // Decode the bytes
    private static void decodeBytes(String src, byte[] data, long generation) {
        NativeImage image = null;
        try {
            ensureCurrentGeneration(generation);
            validateImage(data);
            image = NativeImage.read(new ByteArrayInputStream(data));
            ensureCurrentGeneration(generation);
            NativeImage decoded = image;
            Minecraft.getInstance().execute(() -> registerTexture(src, decoded, generation));
            image = null;
        } catch (Exception ignored) {
            removeRequest(src, generation);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    // Validate the image
    private static void validateImage(byte[] data) throws IOException {
        if (data == null || data.length == 0 || data.length > AdvancedHudImageStore.MAX_IMAGE_BYTES) {
            throw new IOException("Image is too large.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) {
                throw new IOException("Unsupported image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals("png") || format.equals("jpeg") || format.equals("jpg")
                        || format.equals("gif") || format.equals("bmp"))) {
                    throw new IOException("Unsupported image.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > AdvancedHudImageStore.MAX_IMAGE_DIMENSION
                        || height > AdvancedHudImageStore.MAX_IMAGE_DIMENSION
                        || (long) width * height > MAX_CACHED_PIXELS) {
                    throw new IOException("Image dimensions are too large.");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    // Register the texture
    private static void registerTexture(String src, NativeImage image, long generation) {
        if (generation != CACHE_GENERATION.get()
                || image.getWidth() <= 0 || image.getHeight() <= 0
                || image.getWidth() > AdvancedHudImageStore.MAX_IMAGE_DIMENSION
                || image.getHeight() > AdvancedHudImageStore.MAX_IMAGE_DIMENSION) {
            image.close();
            removeRequest(src, generation);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        try {
            String id = UUID.nameUUIDFromBytes(src.getBytes(StandardCharsets.UTF_8))
                    .toString().replace("-", "");
            ResourceLocation location = minecraft.getTextureManager()
                    .register("advanced_hud_image_" + id, new DynamicTexture(image));
            long pixels = (long) image.getWidth() * image.getHeight();
            List<ResourceLocation> evicted = new ArrayList<>();
            synchronized (TEXTURES) {
                CachedTexture prev = TEXTURES.put(src, new CachedTexture(location, pixels));
                if (prev != null) {
                    cachedPixels -= prev.pixels();
                    if (!prev.location().equals(location)) {
                        evicted.add(prev.location());
                    }
                }
                cachedPixels += pixels;
                Iterator<Map.Entry<String, CachedTexture>> textures = TEXTURES.entrySet().iterator();
                while ((TEXTURES.size() > MAX_TEXTURES || cachedPixels > MAX_CACHED_PIXELS)
                        && textures.hasNext()) {
                    CachedTexture removed = textures.next().getValue();
                    textures.remove();
                    cachedPixels -= removed.pixels();
                    if (!removed.location().equals(location)) {
                        evicted.add(removed.location());
                    }
                }
            }
            for (ResourceLocation removed : evicted) {
                minecraft.getTextureManager().release(removed);
            }
            removeRequest(src, generation);
        } catch (Exception ignored) {
            image.close();
            removeRequest(src, generation);
        }
    }

    // Get the loaded texture
    private static ResourceLocation loadedTexture(String src) {
        synchronized (TEXTURES) {
            CachedTexture texture = TEXTURES.get(src);
            return texture == null ? null : texture.location();
        }
    }

    // Request the advanced HUD image client
    private static boolean request(String src) {
        synchronized (REQUESTED) {
            if (REQUESTED.contains(src) || REQUESTED.size() >= MAX_PENDING_IMAGES) {
                return false;
            }
            return REQUESTED.add(src);
        }
    }

    // Remove the request
    private static void removeRequest(String src, long generation) {
        if (generation != CACHE_GENERATION.get()) {
            return;
        }
        synchronized (REQUESTED) {
            REQUESTED.remove(src);
        }
    }

    // Submit the download
    private static void submitDownload(String src, long generation, Runnable task) {
        if (generation != CACHE_GENERATION.get()) {
            removeRequest(src, generation);
            return;
        }
        try {
            DOWNLOAD_EXECUTOR.execute(task);
        } catch (RejectedExecutionException ignored) {
            removeRequest(src, generation);
        }
    }

    // Create the download executor
    private static ThreadPoolExecutor createDownloadExecutor() {
        return new ThreadPoolExecutor(MAX_CONCURRENT_DOWNLOADS, MAX_CONCURRENT_DOWNLOADS,
                30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(MAX_QUEUED_DOWNLOADS), runnable -> {
                    Thread thread = new Thread(runnable,
                            "Create Thrusters HUD Image " + DOWNLOAD_THREAD.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    // Clean up the expired transfers
    private static void cleanupExpiredTransfers() {
        long oldest = Util.getMillis() - TRANSFER_TIMEOUT_MILLIS;
        DOWNLOADS.entrySet().removeIf(entry -> entry.getValue().updatedAt < oldest);
    }

    // Store the cached texture
    private record CachedTexture(ResourceLocation location, long pixels) {
    }

    // Handle the download assembly
    private static final class DownloadAssembly {
        // Download assembly source
        private final String source;
        // Chunks
        private final byte[][] chunks;
        // Current received
        private int received;
        // Current total bytes
        private int totalBytes;
        // Last update time
        private long updatedAt = Util.getMillis();

        // Initialize the download assembly
        private DownloadAssembly(String src, int chunkCount) {
            this.source = src;
            this.chunks = new byte[chunkCount][];
        }

        // Accept the download assembly
        private boolean accept(String incomingSource, int chunkIndex, int chunkCount, byte[] data) {
            if (!source.equals(incomingSource) || chunks.length != chunkCount) {
                return false;
            }
            if (chunks[chunkIndex] != null) {
                return Arrays.equals(chunks[chunkIndex], data);
            }
            if (totalBytes + data.length > AdvancedHudImageStore.MAX_IMAGE_BYTES) {
                return false;
            }
            chunks[chunkIndex] = data.clone();
            received++;
            totalBytes += data.length;
            updatedAt = Util.getMillis();
            return true;
        }

        // Check if this is complete
        private boolean complete() {
            return received == chunks.length;
        }

        // Join the download assembly
        private byte[] join() {
            ByteArrayOutputStream output = new ByteArrayOutputStream(totalBytes);
            for (byte[] chunk : chunks) {
                output.writeBytes(chunk);
            }
            return output.toByteArray();
        }
    }

}
