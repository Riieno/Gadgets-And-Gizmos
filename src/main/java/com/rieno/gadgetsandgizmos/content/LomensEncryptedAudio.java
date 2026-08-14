package com.rieno.gadgetsandgizmos.content;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.CreateThrusters;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;

// Decrypt and cache the custom music-disc audio stream at runtime
public final class LomensEncryptedAudio {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Constants
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final byte[] MAGIC = new byte[]{'C', 'T', 'L', 'M', 'A', 'E', 'S', '1'};
    private static final int IV_LENGTH = 16;
    private static final int HEADER_LENGTH = MAGIC.length + IV_LENGTH;
    private static final byte[] KEY_MASK = new byte[]{
            (byte)0x16, (byte)0x8E, (byte)0xA8, (byte)0x7B, (byte)0x7A, (byte)0xFE, (byte)0xDD, (byte)0x1A,
            (byte)0x82, (byte)0xAF, (byte)0xAC, (byte)0x88, (byte)0x1B, (byte)0xB8, (byte)0x8E, (byte)0x4D,
            (byte)0x5E, (byte)0x89, (byte)0x19, (byte)0x59, (byte)0xF4, (byte)0x62, (byte)0x1C, (byte)0x76,
            (byte)0x16, (byte)0xF2, (byte)0x53, (byte)0x94, (byte)0x4A, (byte)0xBA, (byte)0x59, (byte)0x11
    };
    private static final byte[] KEY_SALT = new byte[]{
            (byte)0xB4, (byte)0x2F, (byte)0x9C, (byte)0xD4, (byte)0x12, (byte)0x99, (byte)0x82, (byte)0xF7,
            (byte)0x65, (byte)0x0C, (byte)0x2B, (byte)0x49, (byte)0xAC, (byte)0x6E, (byte)0xF4, (byte)0x7B,
            (byte)0x9F, (byte)0xE5, (byte)0x24, (byte)0xDA, (byte)0xB1, (byte)0x59, (byte)0x77, (byte)0xEC,
            (byte)0x5E, (byte)0x27, (byte)0xEE, (byte)0xA9, (byte)0x1C, (byte)0x56, (byte)0x74, (byte)0xB9
    };
    private static final Set<String> ENCRYPTED_DISC_PATHS = Set.of(
            "sounds/music_disc/kinetic_currency.ogg",
            "sounds/music_disc/twisted_alive.ogg",
            "sounds/music_disc/unplug_the_earth.ogg"
    );

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        PRELOAD / SETUP
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Initialize the lomens encrypted audio
    private LomensEncryptedAudio() {
    }

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Check if this is encrypted disc
    public static boolean isEncryptedDisc(ResourceLocation location) {
        return location != null
                && CreateThrusters.MOD_ID.equals(location.getNamespace())
                && ENCRYPTED_DISC_PATHS.contains(location.getPath());
    }

    // Open the lomens encrypted audio
    public static InputStream open(ResourceProvider provider, ResourceLocation location) throws IOException {
        InputStream input = provider.open(location);
        if (!isEncryptedDisc(location)) {
            return input;
        }

        byte[] header = input.readNBytes(HEADER_LENGTH);
        if (header.length < HEADER_LENGTH || !hasMagic(header)) {
            return new SequenceInputStream(new ByteArrayInputStream(header), input);
        }

        byte[] iv = Arrays.copyOfRange(header, MAGIC.length, HEADER_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keyFor(location), new IvParameterSpec(iv));
            return new CipherInputStream(input, cipher);
        } catch (GeneralSecurityException err) {
            input.close();
            throw new IOException("Could not decrypt Lomens music disc audio " + location, err);
        }
    }

    // Check if this has magic
    private static boolean hasMagic(byte[] header) {
        for (int i = 0; i < MAGIC.length; i++) {
            if (header[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    // Handle key
    private static SecretKeySpec keyFor(ResourceLocation location) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < KEY_MASK.length; i++) {
            digest.update((byte)(KEY_MASK[i] ^ KEY_SALT[KEY_SALT.length - 1 - i]));
        }
        digest.update(location.toString().getBytes(StandardCharsets.UTF_8));
        digest.update(KEY_SALT);
        return new SecretKeySpec(digest.digest(), "AES");
    }
}
