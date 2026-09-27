package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 孪生门必须落在<b>幻想乡</b>维度——离线守卫。
 *
 * <p><b>来源是一次实机反馈</b>："我在仪式开传送门，怎么旁边也同步出现了一个隙间？"
 *
 * <p>根因不是设计而是笔误：{@code twinPortalPos(...)} 内部走 {@code gensokyoLevel(...)}，
 * 返回的是<b>幻想乡里的坐标</b>；但 {@code placePortals} / {@code ensurePortals} /
 * {@code replayShatter} 三处都把这个坐标交给 {@code level}（仪式的维度）去放置与查询。
 * 后果：
 * <ol>
 *   <li>孪生门被放进仪式的维度 ⇒ 同一维度两扇门，靠近 {@code (0,地表,0)} 时读作
 *       "旁边凭空多了一扇同步的门"；</li>
 *   <li>{@code SukimaBlock#entityInside} 把玩家传到<b>幻想乡</b>的 {@code (0,地表,0)}，
 *       那里根本没有门 ⇒ 过不去也回不来，"成对开门好让人原路返回"的意图彻底落空；</li>
 *   <li>"先到先得"守卫查错维度，形同虚设。</li>
 * </ol>
 *
 * <p>同一文件的 {@code removePortals}（关闭路径）<b>一直是对的</b>
 * （{@code requestClose(gensokyo, twin)}），所以这坐实了开门侧是笔误而非设计选择。
 *
 * <p>为什么用源码扫描而不是真建世界：本仓库的离线测试刻意避开 Minecraft 世界与注册表
 * （只测 registry-free 纯函数），没法真的造一个 {@code ServerLevel}。但
 * "孪生门坐标有没有流进仪式的 level"这件事<b>恰好可以静态断言</b>——
 * 这比"下次再靠实机发现一次"划算得多。
 *
 * <p><b>已知局限</b>：源码扫描对方法重命名敏感。若这三个方法被改名，
 * 本测试会失去意义并需要同步更新（届时应改为在方法体上加注解标记）。
 */
class BarrierTwinGateDimensionTest {

    private static final String SOURCE = "src/main/java/com/bitsson/gensokyou/ritual/behavior/BarrierBreakBehavior.java";

    /**
     * 核心断言：孪生门坐标<b>绝不能</b>被交给仪式的 level。
     *
     * <p>同时覆盖"检查"与"放置"两侧——原 bug 正是两侧同时错的。
     */
    @Test
    void twinIsNeverHandedToTheRitualLevel() {
        String src = readSource();
        for (String forbidden : new String[]{
                "placeAt(level, twin)",
                "level.getBlockState(twin)",
                "level.getBlockEntity(twin)"}) {
            assertTrue(!src.contains(forbidden),
                    "孪生门坐标又流进了仪式的 level（`" + forbidden + "`）"
                            + "——孪生门在幻想乡，必须用 gensokyo");
        }
    }

    /** 反向断言：放置孪生门时 MUST 走 gensokyo，否则上面的守卫会"因为代码被删了"而空过。 */
    @Test
    void twinIsPlacedThroughTheGensokyoLevel() {
        String src = readSource();
        assertTrue(src.contains("placeAt(gensokyo, twin)"),
                "没有找到 placeAt(gensokyo, twin)：孪生门的放置路径被改动后本测试需同步更新");
        assertTrue(src.contains("requestClose(gensokyo, twin)"),
                "没有找到 requestClose(gensokyo, twin)：关闭路径一直是本 bug 的正确参照物");
    }

    /** 孪生门在到达点 (0,地表,0) 外扩 4 格。既有设计常量，非本次引入。 */
    @Test
    void twinOffsetIsFourBlocks() {
        assertEquals(4, BarrierBreakBehavior.TWIN_OFFSET);
    }

    /** 主门在核心正上方 2 格（落在 pattern 自带的裂口笼空气井内）。 */
    @Test
    void mainPortalSitsTwoBlocksAboveTheCore() {
        assertEquals(2, BarrierBreakBehavior.PORTAL_UP);
        BlockPos core = new BlockPos(10, 64, -20);
        assertEquals(core.above(2), BarrierBreakBehavior.mainPortalPos(core));
    }

    /** 孪生门位是纯函数派生：DebugCommands 与测试都要用，故必须 public static。 */
    @Test
    void twinPortalPosIsPublicStaticAndTakesTheOverworld() throws Exception {
        Method m = BarrierBreakBehavior.class.getDeclaredMethod("twinPortalPos", ServerLevel.class);
        assertTrue(Modifier.isPublic(m.getModifiers()), "twinPortalPos 必须 public");
        assertTrue(Modifier.isStatic(m.getModifiers()), "twinPortalPos 必须 static");
    }

    private static String readSource() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(SOURCE),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError("读不到 " + SOURCE + "（测试工作目录必须是项目根）", e);
        }
    }
}
