package com.rieno.gadgetsandgizmos.mixin;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.mojang.blaze3d.audio.SoundBuffer;
import com.rieno.gadgetsandgizmos.content.LomensEncryptedAudio;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.FiniteAudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

// Decrypt custom music disc audio before playback
@Mixin(SoundBufferLibrary.class)
public abstract class EncryptedMusicDiscSoundBufferMixin {
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                            DEFAULTS
                                                       #################
                                                           Variables
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Current resource manager
    @Shadow
    @Final
    private ResourceProvider resourceManager;

    // Tracked cache
    @Shadow
    @Final
    private Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                           Functions
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    // Handle the complete buffer
    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void createthrusters$getCompleteBuffer(ResourceLocation location,
                                                   CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        if (!LomensEncryptedAudio.isEncryptedDisc(location)) {
            return;
        }

        cir.setReturnValue(this.cache.computeIfAbsent(location, key -> CompletableFuture.supplyAsync(() -> {
            try {
                SoundBuffer soundBuffer;
                try (
                        InputStream inputStream = LomensEncryptedAudio.open(this.resourceManager, key);
                        FiniteAudioStream audioStream = new JOrbisAudioStream(inputStream)
                ) {
                    ByteBuffer byteBuffer = audioStream.readAll();
                    soundBuffer = new SoundBuffer(byteBuffer, audioStream.getFormat());
                }

                return soundBuffer;
            } catch (IOException err) {
                throw new CompletionException(err);
            }
        }, Util.nonCriticalIoPool())));
    }

    // Capture the decoded audio stream
    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void createthrusters$getStream(ResourceLocation location, boolean looping,
                                           CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (!LomensEncryptedAudio.isEncryptedDisc(location)) {
            return;
        }

        cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
            try {
                InputStream inputStream = LomensEncryptedAudio.open(this.resourceManager, location);
                return looping
                        ? new LoopingAudioStream(JOrbisAudioStream::new, inputStream)
                        : new JOrbisAudioStream(inputStream);
            } catch (IOException err) {
                throw new CompletionException(err);
            }
        }, Util.nonCriticalIoPool()));
    }
}
