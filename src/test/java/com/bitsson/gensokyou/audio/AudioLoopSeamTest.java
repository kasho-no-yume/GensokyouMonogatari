package com.bitsson.gensokyou.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 持续音素材的循环接缝检查（离线，不启动游戏）。
 *
 * <p>接缝只有一处：文件末尾样本回到文件开头样本。循环播放 60 秒不会让接缝变差，
 * 所以断言一次「末→首」跳变即可代表任意长度的循环。
 *
 * <p><b>需要 PATH 上的 {@code ffmpeg}</b>（Java 没有内置 Vorbis 解码器）。
 * 缺失时跳过而不是失败 —— 这是外部工具依赖，不应让无 ffmpeg 的机器红。
 */
class AudioLoopSeamTest {

    private static final Path LOOP = Path.of("src", "main", "resources", "assets",
            "gensokyou", "sounds", "laser_loop.ogg");

    @Test
    void loopSeamIsNoWorseThanTheWorstInternalStep() throws Exception {
        assumeTrue(Files.isRegularFile(LOOP), "缺少素材: " + LOOP);
        assumeTrue(ffmpegAvailable(), "需要 PATH 上的 ffmpeg 解码 ogg");

        short[] samples = decode(LOOP);
        assertTrue(samples.length > 4410, "素材过短，无法判断接缝");

        int[] steps = new int[samples.length - 1];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = Math.abs(samples[i + 1] - samples[i]);
        }
        int[] sorted = steps.clone();
        Arrays.sort(sorted);
        int index = (int) Math.min(sorted.length - 1L,
                Math.max(0L, (long) Math.ceil(0.999D * sorted.length) - 1L));
        int p999 = sorted[index];

        int seam = Math.abs(samples[0] - samples[samples.length - 1]);
        assertTrue(seam <= p999,
                "循环接缝跳变 " + seam + " 超过整段 99.9% 分位 " + p999
                        + "（循环会听到「咔」）");
    }

    private static short[] decode(Path file) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "ffmpeg", "-v", "error", "-i", file.toString(),
                "-f", "s16le", "-ac", "1", "-ar", "44100", "pipe:1")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        byte[] bytes = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        assertTrue(code == 0, "ffmpeg 解码失败，退出码 " + code);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        short[] samples = new short[bytes.length / 2];
        buffer.asShortBuffer().get(samples);
        return samples;
    }

    private static boolean ffmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException failure) {
            return false;
        }
    }
}
