package com.bitsson.gensokyou.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 核心渲染器的<b>缓冲获取结构</b>守卫。
 *
 * <p><b>为什么需要它</b>：球形核心的渲染器曾把两个 {@code getBuffer} 调用提到方法开头
 * （先取"加法辉光"与"半透明本体"两个 consumer，再依次往里写顶点），
 * 结果第二个 {@code getBuffer} 把第一个 consumer 顶掉了——
 * 渲染本体正常、渲染外晕时炸 {@code IllegalStateException: Not building!}，
 * 物品栏里带着这把武器就必崩。
 *
 * <p><b>契约</b>（1.21.1 {@code MultiBufferSource.BufferSource#getBuffer}）：
 * 某个 RenderType <b>首次</b>占用共享缓冲时，会把上一个占用者 {@code endBatch} 掉
 * ——那个 builder 的 {@code building} 置 false，再往它的 consumer 写顶点就抛
 * {@code Not building!}。故<b>取缓冲与写入 MUST 绑在一起</b>（取 → 写 → 取 → 写）。
 *
 * <p><b>这条契约本身无法写成单测</b>：验证它需要真实构造 {@code RenderType}，
 * 而 {@code RenderType} 的静态初始化会连带初始化原版注册表
 * （{@code BuiltInRegistries → Blocks → SoundType → SoundEvents}），
 * 单测环境没有原版引导就抛 "Not bootstrapped"；而 NeoForge 的
 * {@code Bootstrap.bootStrap()} 又需要 FML 的 mod 列表（{@code LoadingModList.get()} 为 null），
 * 同样不可用。**因此真正的验证在实机**（物品栏 / 快捷栏渲染）。
 *
 * <p>能写成单测的是<b>结构</b>：只要"取缓冲"与"写入"在语言层面绑死，
 * 这个坑就无从复现。本测试守的就是这条结构。
 */
class CoreOrbBufferStructureTest {

    /** 取缓冲与写入绑在一起的入口。 */
    private static final List<String> ATOMIC_ENTRY_POINTS = List.of("emitTo", "emitFlameTo");

    /**
     * 渲染器 MUST NOT 把 {@link VertexConsumer} 存成字段。
     *
     * <p>存成字段 = 跨帧持有 = 下一帧的 {@code getBuffer} 必然把它顶掉。
     */
    @Test
    void rendererHoldsNoVertexConsumerField() {
        List<String> offenders = new ArrayList<>();
        for (Field field : CoreOrbRenderer.class.getDeclaredFields()) {
            if (VertexConsumer.class.isAssignableFrom(field.getType())) {
                offenders.add(field.getName());
            }
        }
        assertTrue(offenders.isEmpty(),
                "渲染器不得持有 VertexConsumer 字段（跨帧持有必被 getBuffer 顶掉）：" + offenders);
    }

    /**
     * 任何接收 {@link VertexConsumer} 的方法 MUST 是 private。
     *
     * <p>public/protected 的方法会把 consumer 交到调用方手里，
     * 调用方就可能跨 {@code getBuffer} 继续用它——那正是本变更踩过的坑。
     */
    @Test
    void onlyPrivateMethodsMayAcceptAVertexConsumer() {
        List<String> offenders = new ArrayList<>();
        for (Method method : CoreOrbRenderer.class.getDeclaredMethods()) {
            boolean takesConsumer = false;
            for (Class<?> param : method.getParameterTypes()) {
                if (VertexConsumer.class.isAssignableFrom(param)) {
                    takesConsumer = true;
                    break;
                }
            }
            if (takesConsumer && !Modifier.isPrivate(method.getModifiers())) {
                offenders.add(method.getName());
            }
        }
        assertTrue(offenders.isEmpty(),
                "接收 VertexConsumer 的方法 MUST 是 private：" + offenders);
    }

    /**
     * "取缓冲 + 写入" MUST 绑在一个方法里，且该方法 MUST 自己取缓冲。
     *
     * <p>这是把契约变成结构：调用方拿不到 consumer，也就没有跨 {@code getBuffer}
     * 传递它的机会。
     */
    @Test
    void acquireAndWriteAreBoundTogether() throws Exception {
        for (String name : ATOMIC_ENTRY_POINTS) {
            Method method = null;
            for (Method candidate : CoreOrbRenderer.class.getDeclaredMethods()) {
                if (candidate.getName().equals(name)) {
                    method = candidate;
                    break;
                }
            }
            assertTrue(method != null, "缺少绑定入口：" + name);
            assertTrue(Modifier.isPrivate(method.getModifiers()), name + " MUST 是 private");
            assertTrue(MultiBufferSource.class.isAssignableFrom(method.getParameterTypes()[method.getParameterCount() - 1]),
                    name + " 的最后一个参数 MUST 是 MultiBufferSource（自己取缓冲）");
            boolean takesConsumer = false;
            for (Class<?> param : method.getParameterTypes()) {
                if (VertexConsumer.class.isAssignableFrom(param)) {
                    takesConsumer = true;
                    break;
                }
            }
            assertTrue(!takesConsumer, name + " MUST NOT 接收 VertexConsumer（它自己取）");
        }
    }

    /**
     * 渲染器 MUST NOT 在方法体里出现"先取好 consumer、隔一段再写"的形状。
     *
     * <p>本测试用反射看不到源码，故退一步断言：{@code renderByItem} 之外没有别的方法
     * 会拿到 consumer（见上面两条）。这里只断言 {@code renderByItem} 本身存在且可调用。
     */
    @Test
    void renderByItemStillTakesTheStandardArguments() throws Exception {
        Method method = CoreOrbRenderer.class.getMethod("renderByItem", net.minecraft.world.item.ItemStack.class,
                net.minecraft.world.item.ItemDisplayContext.class,
                com.mojang.blaze3d.vertex.PoseStack.class,
                MultiBufferSource.class, int.class, int.class);
        assertEquals(CoreOrbRenderer.class, method.getDeclaringClass());
        assertTrue(RenderType.class != null, "RenderType 只是为了让本测试显式依赖渲染类型契约");
    }
}
