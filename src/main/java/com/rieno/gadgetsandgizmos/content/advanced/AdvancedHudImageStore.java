package com.rieno.gadgetsandgizmos.content.advanced;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Save and restore Advanced HUD Image data
public final class AdvancedHudImageStore {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    public static final String REFERENCE_PREFIX = "gizmos-image:";
    public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    public static final int TRANSFER_CHUNK_BYTES = 24 * 1024;
    public static final int MAX_IMAGE_DIMENSION = 4096;
    public static final int MAX_IMAGES_PER_WORLD = 256;
    private static final String DIRECTORY_NAME = "gizmos-images";

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the advanced HUD image store
    private AdvancedHudImageStore() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Save the advanced HUD image store
    public static SaveResult save(MinecraftServer server, String originalName, byte[] data) {
        return save(server.getWorldPath(LevelResource.ROOT), originalName, data);
    }

    // Get the list
    public static List<String> list(MinecraftServer server) {
        return list(server.getWorldPath(LevelResource.ROOT));
    }

    // Read the advanced HUD image store
    public static Optional<byte[]> read(MinecraftServer server, String name) {
        return read(server.getWorldPath(LevelResource.ROOT), name);
    }

    // Get the reference
    public static String reference(String name) {
        return REFERENCE_PREFIX + name;
    }

    // Get the referenced name
    public static Optional<String> referencedName(String val) {
        if (val == null || !val.startsWith(REFERENCE_PREFIX)) {
            return Optional.empty();
        }
        String name = val.substring(REFERENCE_PREFIX.length());
        return isSafeStoredName(name) ? Optional.of(name) : Optional.empty();
    }

    // Save the advanced HUD image store
    static SaveResult save(Path worldRoot, String originalName, byte[] data) {
        if (data == null || data.length == 0) {
            return SaveResult.failure("The selected image is empty.");
        }
        if (data.length > MAX_IMAGE_BYTES) {
            return SaveResult.failure("Images must be 2 MiB or smaller.");
        }
        try {
            ImageMetadata metadata = inspect(data);
            Path dir = imageDirectory(worldRoot);
            Files.createDirectories(dir);
            if (list(worldRoot).size() >= MAX_IMAGES_PER_WORLD) {
                return SaveResult.failure("This world already has the maximum of "
                        + MAX_IMAGES_PER_WORLD + " uploaded images.");
            }
            String requestedName = safeBaseName(originalName, metadata.extension());
            String storedName = uniqueName(dir, requestedName);
            Path target = dir.resolve(storedName).normalize();
            if (!dir.equals(target.getParent())) {
                return SaveResult.failure("The image name is invalid.");
            }
            Path tmp = Files.createTempFile(dir, ".upload-", ".tmp");
            try {
                Files.write(tmp, data);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException ignored) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
            return SaveResult.success(storedName);
        } catch (IOException err) {
            return SaveResult.failure(err.getMessage() == null
                    ? "The image could not be saved." : err.getMessage());
        }
    }

    // Get the list
    static List<String> list(Path worldRoot) {
        Path dir = imageDirectory(worldRoot);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (var paths = Files.list(dir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> isSafeStoredName(path.getFileName().toString()))
                    .filter(path -> {
                        try {
                            long size = Files.size(path);
                            return size > 0 && size <= MAX_IMAGE_BYTES;
                        } catch (IOException ignored) {
                            return false;
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .forEach(names::add);
        } catch (IOException ignored) {
            return List.of();
        }
        names.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return List.copyOf(names);
    }

    // Read the advanced HUD image store
    static Optional<byte[]> read(Path worldRoot, String name) {
        if (!isSafeStoredName(name)) {
            return Optional.empty();
        }
        Path dir = imageDirectory(worldRoot);
        Path target = dir.resolve(name).normalize();
        if (!dir.equals(target.getParent()) || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            long size = Files.size(target);
            if (size <= 0 || size > MAX_IMAGE_BYTES) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    // Get the image directory
    private static Path imageDirectory(Path worldRoot) {
        return worldRoot.toAbsolutePath().normalize().resolve(DIRECTORY_NAME);
    }

    // Get the inspect
    private static ImageMetadata inspect(byte[] data) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            if (input == null) {
                throw new IOException("The selected file is not a supported image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("The selected file is not a supported image.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                    throw new IOException("Images cannot be larger than "
                            + MAX_IMAGE_DIMENSION + " by " + MAX_IMAGE_DIMENSION + " pixels.");
                }
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "png" -> "png";
                    case "jpeg", "jpg" -> "jpg";
                    case "gif" -> "gif";
                    case "bmp" -> "bmp";
                    default -> throw new IOException("Only PNG, JPEG, GIF, and BMP images are supported.");
                };
                return new ImageMetadata(extension);
            } finally {
                reader.dispose();
            }
        }
    }

    // Get the safe base name
    private static String safeBaseName(String originalName, String extension) {
        String filename = originalName == null ? "hud-image" : originalName.replace('\\', '/');
        int separator = filename.lastIndexOf('/');
        if (separator >= 0) {
            filename = filename.substring(separator + 1);
        }
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        base = base.replaceAll("[^A-Za-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "hud-image";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        return base + "." + extension;
    }

    // Get the unique name
    private static String uniqueName(Path dir, String requestedName) {
        int dot = requestedName.lastIndexOf('.');
        String base = requestedName.substring(0, dot);
        String extension = requestedName.substring(dot);
        String candidate = requestedName;
        for (int suffix = 2; Files.exists(dir.resolve(candidate)); suffix++) {
            candidate = base + "-" + suffix + extension;
        }
        return candidate;
    }

    // Check if this is safe stored name
    private static boolean isSafeStoredName(String name) {
        if (name == null || name.isBlank() || name.length() > 96
                || !name.matches("[A-Za-z0-9_-]+\\.(png|jpg|gif|bmp)")) {
            return false;
        }
        return Path.of(name).getNameCount() == 1;
    }

    // Store the image metadata
    private record ImageMetadata(String extension) {
    }

    // Store save results
    public record SaveResult(boolean success, String storedName, String message) {
        // Create a successful save result
        private static SaveResult success(String storedName) {
            return new SaveResult(true, storedName, "Uploaded " + storedName + ".");
        }

        // Create a failed save result
        private static SaveResult failure(String msg) {
            return new SaveResult(false, "", msg);
        }
    }
}
