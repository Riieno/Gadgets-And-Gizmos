package com.rieno.gadgetsandgizmos.neoforge.client;

/*--------------------------------------------------------##---------------------------------------------------------

=======================================================================================================================
                                                        IMPORTS
=======================================================================================================================

------------------------------------------------------------##-----------------------------------------------------*/

import com.rieno.gadgetsandgizmos.lib.client.sound.MediaPlaybackEngine;
import com.rieno.gadgetsandgizmos.lib.client.sound.PcmAudioStream;
import com.rieno.gadgetsandgizmos.lib.media.MediaPlaybackState;
import com.rieno.gadgetsandgizmos.lib.media.MusicDiscCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

// Compare decoded music samples and exercise the real streaming sound channels
@EventBusSubscriber(modid = "createthrusters", value = Dist.CLIENT)
public final class MediaAudioClientValidation{
    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        CONSTANTS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    private static final String OUTPUT = System.getProperty("gadgetsngizmos.mediaAudioValidation");
    private static final MediaPlaybackEngine ENGINE = new MediaPlaybackEngine();
    private static final UUID ID = UUID.randomUUID();
    private static final AtomicInteger STARTS = new AtomicInteger();
    private static final AtomicInteger POSITIONAL = new AtomicInteger();
    private static volatile TickableSoundInstance last;
    private static volatile String failure;
    private static MediaPlaybackState playing;
    private static MediaPlaybackState paused;
    private static MediaPlaybackState resumed;
    private static CompletableFuture<Void> decoded;
    private static int ticks;
    private static boolean complete;

    /*--------------------------------------------------------##---------------------------------------------------------

    =======================================================================================================================
                                                        FUNCTIONS
    =======================================================================================================================

    ------------------------------------------------------------##-----------------------------------------------------*/

    @SubscribeEvent public static void streaming(PlayStreamingSourceEvent evt){
        if(OUTPUT == null || complete || !evt.getSound().getClass().getName().contains("MediaPlaybackEngine$TrackSound")) return;
        if(!evt.getChannel().playing()) failure = "OpenAL did not start the music channel";
        STARTS.incrementAndGet();
        if(!evt.getSound().isRelative()) POSITIONAL.incrementAndGet();
        last = (TickableSoundInstance)evt.getSound();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post evt){
        if(OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) mc.screen.onClose();
        if(mc.level == null || mc.player == null || mc.getOverlay() != null || mc.player.tickCount < 40) return;
        mc.options.pauseOnLostFocus = false;
        try{
            if(playing == null){
                mc.setScreen(null);
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.getSoundSourceOptionInstance(SoundSource.RECORDS).set(.02D);
                var track = MusicDiscCatalog.resolve(mc.level.registryAccess(), ResourceLocation.withDefaultNamespace("music_disc_cat"));
                require(track != null, "Music disc Cat did not resolve");
                playing = new MediaPlaybackState(track.itemId(), true, 0, mc.level.getGameTime(), track.durationTicks(), 10);
                var event = mc.getSoundManager().getSoundEvent(track.sound().getLocation());
                require(event != null, "Music sound event did not resolve");
                var sound = event.getSound(RandomSource.create(1));
                decoded = CompletableFuture.runAsync(() -> {
                    try{ validatePcm(mc.getResourceManager(), sound.getPath()); }
                    catch(IOException err){ throw new java.io.UncheckedIOException(err); }
                });
            }
            if(failure != null) throw new AssertionError(failure);
            if(decoded.isCompletedExceptionally()) decoded.join();
            Vec3 near = mc.player.position().add(1, 0, 0);
            if(ticks < 80){
                // Creative placement leaves a hotbar copy with the same tablet id
                if(ticks % 2 == 0){
                    ENGINE.observe(ID, playing, near, false);
                    ENGINE.observe(ID, playing, Vec3.ZERO, true);
                }else{
                    ENGINE.observe(ID, playing, Vec3.ZERO, true);
                    ENGINE.observe(ID, playing, near, false);
                }
            }else if(ticks < 100){
                if(paused == null) paused = playing.paused(mc.level.getGameTime());
                ENGINE.observe(ID, playing, Vec3.ZERO, true);
                ENGINE.observe(ID, paused, near, false);
            }else if(ticks < 180){
                if(resumed == null) resumed = new MediaPlaybackState(playing.discId(), true, paused.offsetTicks(),
                        mc.level.getGameTime(), playing.durationTicks(), paused.revision() + 1);
                ENGINE.observe(ID, playing, Vec3.ZERO, true);
                ENGINE.observe(ID, resumed, near, false);
            }else if(ticks < 260) ENGINE.observe(ID, resumed, near, false);
            else if(ticks < 280) ENGINE.observe(ID, resumed, near.add(200, 0, 0), false);
            else if(ticks < 340) ENGINE.observe(ID, resumed, near, false);
            ENGINE.tick();
            switch(ticks++){
                case 70 -> require(STARTS.get() == 1, "Duplicate observations restarted portable playback: " + STARTS.get());
                case 95 -> require(last != null && last.isStopped(), "The latest pause did not stop an old playing copy");
                case 170 -> require(STARTS.get() == 2, "Resume did not retain one stable stream: " + STARTS.get());
                case 250 -> require(STARTS.get() == 3 && POSITIONAL.get() == 1, "Placed playback did not open one positional stream");
                case 275 -> require(last.isStopped(), "A source outside hearing range continued playing");
                case 330 -> require(STARTS.get() == 4 && POSITIONAL.get() == 2, "Returning to hearing range did not resume the timeline");
                case 350 -> {
                    require(last.isStopped(), "Unobserved source did not release its stream");
                    require(decoded.isDone(), "PCM decoding validation did not complete");
                    decoded.join();
                    finish("PASS: PCM samples exact; seek tails preserved; portable/placed OpenAL streams stable; pause/range/unload cleanup passed");
                }
            }
        }catch(Throwable err){ finish("FAIL " + err); }
    }

    private static void validatePcm(ResourceManager resources, ResourceLocation path) throws IOException{
        byte[] reference;
        int rate, channels;
        try(var raw = new JOrbisAudioStream(resources.open(path))){
            rate = (int)raw.getFormat().getSampleRate();
            channels = raw.getFormat().getChannels();
            int bytes = rate * channels * 2 * 8;
            var data = raw.read(bytes);
            require(data.remaining() >= bytes, "Music reference had no complete decoded buffers");
            reference = new byte[bytes];
            data.get(reference);
        }
        try(var stream = new PcmAudioStream(new JOrbisAudioStream(resources.open(path)), false)){
            var data = stream.read(reference.length);
            require(data != null && data.remaining() == reference.length, "Portable PCM was truncated");
            byte[] actual = new byte[data.remaining()];
            data.get(actual);
            require(Arrays.equals(reference, actual), "Portable PCM samples changed during streaming");
        }
        try(var stream = new PcmAudioStream(new JOrbisAudioStream(resources.open(path)), true)){
            var mono = stream.read(rate * 2 * 8).order(ByteOrder.nativeOrder());
            var raw = ByteBuffer.wrap(reference).order(ByteOrder.nativeOrder());
            long energy = 0;
            while(mono.hasRemaining()){
                int sum = 0;
                for(int idx = 0; idx < channels; idx++) sum += raw.getShort();
                short sample = mono.getShort();
                require(sample == (short)(sum / channels), "Placed PCM was distorted by channel conversion");
                energy += (long)sample * sample;
            }
            require(energy > 0, "The decoded track was silent");
        }
        try(var stream = new PcmAudioStream(new JOrbisAudioStream(resources.open(path)), false)){
            stream.seekTicks(81, () -> false);
            int offset = rate * 81 / 20 * channels * 2;
            int size = rate * channels * 2;
            var data = stream.read(size);
            require(data != null && data.remaining() == size, "Seeking produced a short initial OpenAL buffer");
            byte[] actual = new byte[size];
            data.get(actual);
            require(Arrays.equals(Arrays.copyOfRange(reference, offset, offset + size), actual), "Seeking lost decoded samples");
        }
    }

    private static void require(boolean val, String message){ if(!val) throw new AssertionError(message); }
    private static void finish(String result){
        complete = true;
        ENGINE.clear();
        try{ Files.writeString(Path.of(OUTPUT), result); }catch(IOException err){ throw new java.io.UncheckedIOException(err); }
        Minecraft.getInstance().stop();
    }
}
