package dev.swingingsigns.showcase;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

final class Recorder {
    static final int FPS = 60;
    private static final byte[] END = new byte[0];

    private final String ffmpeg;
    private Process process;
    private Thread writer;
    private BlockingQueue<byte[]> queue;
    private int width;
    private int height;
    private int requested;
    private volatile int received;
    private volatile Throwable failure;

    Recorder(String ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    boolean recording() {
        return process != null;
    }

    void start(Path file) {
        Minecraft mc = Minecraft.getInstance();
        width = Screens.renderTarget(mc).width;
        height = Screens.renderTarget(mc).height;
        requested = 0;
        received = 0;
        try {
            Files.createDirectories(file.getParent());
            process = new ProcessBuilder(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-r", String.valueOf(FPS),
                    "-i", "-",
                    "-c:v", "h264_nvenc", "-preset", "p7", "-rc", "vbr", "-cq", "14", "-b:v", "0",
                    "-pix_fmt", "yuv420p", file.toString()))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        queue = new ArrayBlockingQueue<>(6);
        OutputStream out = process.getOutputStream();
        BlockingQueue<byte[]> frames = queue;
        writer = new Thread(() -> {
            try (out) {
                while (true) {
                    byte[] frame = frames.take();
                    if (frame == END) {
                        return;
                    }
                    out.write(frame);
                }
            } catch (Throwable e) {
                failure = e;
            }
        }, "swingingsigns-recorder");
        writer.start();
    }

    void capture(Path still) {
        check();
        requested++;
        BlockingQueue<byte[]> frames = queue;
        Screenshot.takeScreenshot(Screens.renderTarget(Minecraft.getInstance()), image -> {
            try (image) {
                if (still != null) {
                    saveStill(image, still);
                }
                byte[] bytes = new byte[image.getWidth() * image.getHeight() * 4];
                MemoryUtil.memByteBuffer(image.getPointer(), bytes.length).get(bytes);
                frames.put(bytes);
                received++;
            } catch (Throwable e) {
                failure = e;
            }
        });
    }

    static void still(Path file) {
        Screenshot.takeScreenshot(Screens.renderTarget(Minecraft.getInstance()), image -> {
            try (image) {
                saveStill(image, file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void saveStill(NativeImage image, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        image.writeToFile(file);
    }

    boolean drained() {
        return received >= requested;
    }

    void stop() {
        try {
            queue.put(END);
            writer.join();
            process.getOutputStream().close();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("ffmpeg did not finish");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg failed with " + process.exitValue());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            process = null;
        }
        check();
    }

    private void check() {
        if (failure != null) {
            throw new IllegalStateException("recording failed", failure);
        }
    }
}
