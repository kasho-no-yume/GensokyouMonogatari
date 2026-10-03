package com.bitsson.gensokyou.danmaku;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code TalismanDanmaku} 的源码级断言。
 *
 * <p><b>为什么是源码级而不是行为级</b>：本仓库没有构造实体与 {@code Level} 的测试脚手架
 * （唯一的 Minecraft 引导只负责注册表，见 {@code MinecraftTestBootstrap}），
 * 而「客户端不写 {@code DATA_TARGET_LOST}」是一条<b>代码位置</b>的性质：
 * 它要求那个 {@code entityData.set} 位于 {@code !level().isClientSide} 之内，
 * 靠行为测试只能间接观察。因此这里直接读源码断言结构。
 *
 * <p>代价是<b>重构会让这些断言失效</b>（改方法名、拆方法、改缩进都可能触发）。
 * 这是刻意接受的：它们盯的是一条纪律 —— 「客户端不得自行判定服务端决策」，
 * 而纪律被重命名掉的那一天，人也会重新审视它是否还成立。
 */
class TalismanDanmakuSourceTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "com", "bitsson",
            "gensokyou", "entity", "TalismanDanmaku.java");

    private static String source;

    @BeforeAll
    static void loadSource() throws IOException {
        assertTrue(Files.exists(SOURCE), "找不到源码：" + SOURCE.toAbsolutePath()
                + "（测试的工作目录应为项目根目录）");
        source = Files.readString(SOURCE, StandardCharsets.UTF_8);
    }

    /** 取方法体（从方法签名到下一个同缩进的成员声明为止）。 */
    private static String methodBody(String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "源码中找不到：" + signatureStart);
        // 从签名行的缩进推断方法体的结束位置
        int lineStart = source.lastIndexOf('\n', start) + 1;
        String indent = source.substring(lineStart, start);
        int end = source.indexOf("\n" + indent + "}", start);
        assertTrue(end > start, "无法界定方法体范围：" + signatureStart);
        return source.substring(start, end);
    }

    /** 去掉所有空白与注释，只留代码骨架。 */
    private static String codeOnly(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            int comment = line.indexOf("//");
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.replaceAll("/\\*.*?\\*/", "").trim();
            if (!line.isEmpty()) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    @Test
    @DisplayName("客户端代码路径中不存在对 DATA_TARGET_LOST 的写调用")
    void clientNeverWritesTargetLost() {
        String homing = codeOnly(methodBody("private void tickHoming("));

        List<String> writes = new ArrayList<>();
        for (String line : homing.split("\n")) {
            if (line.contains("DATA_TARGET_LOST") && line.contains(".set(")) {
                writes.add(line);
            }
        }
        assertEquals(1, writes.size(),
                "tickHoming 里应当只有一处对 DATA_TARGET_LOST 的写入，实际：" + writes);

        // 那唯一一处必须落在服务端分支内。
        // 注意要定位**写**的那次出现，而不是第一次出现 —— 第一次是开头的读取短路，
        // 它在守卫之前出现是对的（双端同构），拿它比位置会得到假失败。
        int guard = homing.indexOf("!this.level().isClientSide");
        int write = homing.indexOf(".set(DATA_TARGET_LOST");
        assertTrue(guard >= 0, "tickHoming 里必须有服务端分支");
        assertTrue(write > guard, "唯一的写入必须位于 !isClientSide 分支之后，实际顺序：\n" + homing);
        assertTrue(homing.contains("if (!this.level().isClientSide) {"),
                "服务端判定必须是块级分支，代码骨架：\n" + homing);
    }

    @Test
    @DisplayName("目标身份全程只用 UUID，不出现按网络 id 的查找或回退")
    void targetIdentityNeverUsesNetworkId() {
        String body = codeOnly(methodBody("public Entity getTarget()"));
        assertTrue(body.contains("DanmakuTargetRef.resolve"),
                "getTarget 必须走 DanmakuTargetRef.resolve（单一解析入口）");
        assertTrue(!body.contains(".getEntity("),
                "getTarget 内不得直接调用按 id 的 getEntity：" + body);

        // 整个类里都不该再有 entityData 的 INT 型目标字段
        assertTrue(!source.contains("EntityDataSerializers.INT"),
                "TalismanDanmaku 不应再有 INT 型同步字段（目标身份已改 UUID）");
        assertTrue(!source.contains("getEntity(int"), "不应出现按网络 id 的实体查找");
    }

    @Test
    @DisplayName("存档键成对出现：写与读的条件同宽")
    void saveKeysArePaired() {
        String write = codeOnly(methodBody("protected void addAdditionalSaveData("));
        String read = codeOnly(methodBody("protected void readAdditionalSaveData("));

        // TargetUUID 的写入条件是「present 才写」，读取条件必须同宽
        assertTrue(write.contains("targetUuid.isPresent()") && write.contains("putUUID(\"TargetUUID\""),
                "写入侧：TargetUUID 只在有目标时写");
        assertTrue(read.contains("hasUUID(\"TargetUUID\""),
                "读取侧：必须用 hasUUID 判存在，与写入条件同宽");
        assertTrue(read.contains("Optional.empty()"),
                "缺键时退化为无目标，而不是异常或残留旧值");

        // TargetLost 无条件写，故读取侧也无条件读
        assertTrue(write.contains("putBoolean(\"TargetLost\""), "写入侧：TargetLost");
        assertTrue(read.contains("getBoolean(\"TargetLost\""), "读取侧：TargetLost");

        // 三个键都必须两侧成对
        for (String key : List.of("Sensitivity", "TargetLost", "TargetUUID")) {
            assertTrue(write.contains("\"" + key + "\""), "写入侧缺键：" + key);
            assertTrue(read.contains("\"" + key + "\""), "读取侧缺键：" + key);
        }
    }

    @Test
    @DisplayName("javadoc 不复述可配置阈值，改为引用配置键")
    void javadocReferencesConfigKeyNotLiteral() {
        String javadoc = methodBody("private void tickHoming(");
        assertTrue(javadoc.contains("TALISMAN_TARGET_LOSS_ANGLE_DEG"),
                "javadoc 应引用配置键名");
        assertTrue(!javadoc.contains("150°") && !javadoc.contains("默认 150"),
                "javadoc 不应复述阈值数值（该数值已经漂过一次：实际默认 120）");
    }
}