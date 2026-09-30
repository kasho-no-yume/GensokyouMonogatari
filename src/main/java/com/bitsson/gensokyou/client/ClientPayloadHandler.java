package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.client.ritual.ClientRitualData;
import com.bitsson.gensokyou.jei.GensokyouJeiPlugin;
import com.bitsson.gensokyou.network.DialogSyncPayload;
import com.bitsson.gensokyou.network.RitualConflictPayload;
import com.bitsson.gensokyou.network.RitualInfoPayload;
import com.bitsson.gensokyou.network.RitualPreviewPayload;
import com.bitsson.gensokyou.network.SkillSyncPayload;
import com.bitsson.gensokyou.network.SpiritPowerSyncPayload;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    public static void handleSpiritPowerSync(SpiritPowerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SpiritPowerClientState.update(
                payload.current(), payload.max(), payload.temper(), payload.flightInertia()));
    }

    /**
     * 弹体年龄种子。
     *
     * <p>实体查不到时 MUST 静默忽略——生成包已到但实体已被移除是正常竞态，不是错误，
     * 也无处可报。二次配对（同一客户端实体收到第二个年龄包）由
     * {@code seedPeerAge} 内部计数并在此留一行日志。
     */
    public static void handleDanmakuAge(com.bitsson.gensokyou.network.DanmakuAgePayload payload,
                                        IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            if (level.getEntity(payload.entityId())
                    instanceof com.bitsson.gensokyou.entity.AbstractDanmakuProjectile bullet) {
                boolean reseed = bullet.isPeerAgeSeeded();
                bullet.seedPeerAge(payload.age());
                if (reseed) {
                    com.bitsson.gensokyou.Gensokyou.LOGGER.info(
                            "[danmaku-seed] RESEED id={} age={} localTick={} frame={}",
                            payload.entityId(), payload.age(), bullet.tickCount,
                            bullet.hasFormationFrame());
                }
            }
        });
    }

    /**
     * 完整初始化／恢复快照。
     *
     * <p>实体查不到时 MUST 静默忽略：生成包已到但实体已被移除是正常竞态，不是错误，
     * 也无处可报。快照的原子性由 {@code applyDanmakuSnapshot} 内部保证——位置、速度、
     * 年龄锚点与（按需）运动输入在同一次调用里落定，不存在「改了一半」的中间态。
     */
    public static void handleDanmakuSnapshot(
            com.bitsson.gensokyou.network.DanmakuSnapshotPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            if (level.getEntity(payload.entityId())
                    instanceof com.bitsson.gensokyou.entity.AbstractDanmakuProjectile bullet) {
                bullet.applyDanmakuSnapshot(payload.uuid(), payload.trackingToken(),
                        payload.serverGameTime(), payload.age(), payload.motionRevision(),
                        payload.paramFingerprint(), payload.position(), payload.velocity(),
                        payload.motionParams());
            }
        });
    }

    /**
     * 批量校准样本。
     *
     * <p>逐条就地处理：任何一条升级到失步都<b>不在这里</b>发恢复请求——请求由实体的
     * 每 tick 路径按退避节奏提交，处理器只负责记录事实。
     */
    public static void handleDanmakuCalibration(
            com.bitsson.gensokyou.network.DanmakuCalibrationPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            for (com.bitsson.gensokyou.network.DanmakuCalibrationPayload.Sample sample
                    : payload.samples()) {
                if (level.getEntity(sample.entityId())
                        instanceof com.bitsson.gensokyou.entity.AbstractDanmakuProjectile bullet) {
                    bullet.applyCalibrationSample(payload.serverGameTime(), sample.age(),
                            sample.sequence(),
                            com.bitsson.gensokyou.danmaku.render.DanmakuMotionState
                                    .revisionFor(sample.age()),
                            sample.position());
                }
            }
        });
    }

    public static void handleSkillSync(SkillSyncPayload payload, IPayloadContext context) {        context.enqueueWork(() -> ClientSkillState.update(
                payload.learned(), payload.remainingTicks(), payload.equipped()));
    }

    public static void handleRitualInfo(RitualInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualState.update(payload));
    }

    /** 造化合成演出：登记包围盒粒子程序，客户端本地生成至到期。 */
    public static void handleRitualCraftFx(com.bitsson.gensokyou.network.RitualCraftFxPayload payload,
                                           IPayloadContext context) {
        context.enqueueWork(() -> ClientCraftFxState.add(payload,
                net.minecraft.client.Minecraft.getInstance().level));
    }

    public static void handleRitualConflict(RitualConflictPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualConflictState.update(payload.positions()));
    }

    public static void handleRitualPreview(RitualPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualPreviewState.update(payload.preview()));
    }

    public static void handleEditorPreview(com.bitsson.gensokyou.network.EditorPreviewPayload payload,
                                           IPayloadContext context) {
        context.enqueueWork(() -> ClientRitualEditorState.update(payload));
    }

    /** 对话同步：委托客户端专属类处理（服务端不加载 DialogScreen/Minecraft）。 */
    public static void handleDialogSync(DialogSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDialog.handle(payload));
    }

    /** 无尽藏晶可见页快照：暂存供界面渲染。 */
    public static void handleCrystalPage(
            com.bitsson.gensokyou.network.CrystalStoragePagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCrystalStorageState.update(payload));
    }

    /**
     * 仪式数据快照：解析重建并落盘缓存，并驱动 JEI 页签刷新（客户端专属类，服务端不加载）。
     *
     * <p>本方法是 JEI 侧的两个数据触发点之一（另一个是 {@code onRuntimeAvailable}），取代了
     * 原先每 tick 轮询。解析失败时不刷新——{@code applyJson} 失败会保留既有数据，此时用陈旧
     * 数据刷新优于用空数据刷新。
     */
    public static void handleRitualDataSync(
            com.bitsson.gensokyou.network.RitualDataSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!ClientRitualData.applyJson(payload.json())) {
                return;
            }
            // JEI 为可选软依赖：无 JEI 时不得触碰 GensokyouJeiPlugin（其父接口在缺席期不可解析）
            if (ModList.get().isLoaded("jei")) {
                GensokyouJeiPlugin.resyncAll();
            }
        });
    }

    /**
     * 符卡名下标：记进 {@link ClientSpellCardNames}，供血条下方那一行显示。
     *
     * <p>只存下标不存名字——名字由 {@code BossCards} 的表在渲染时本地反查，
     * 于是 Component 不上线（见 {@code SpellCardNamePayload} 的类注释）。
     *
     * <p>实体查不到时**照收不误**：包先于实体到达是可能的（生成包与本包同序但不同批），
     * 此时记下即可，血条真正画出来时实体早已加载。若在这里因查不到就丢弃，
     * 玩家会看到空白符卡位且此后永不更新——而空白与「无符卡」画面上无法区分。
     */
    public static void handleSpellCardName(
            com.bitsson.gensokyou.network.SpellCardNamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSpellCardNames.put(payload.entityId(), payload.cardIndex()));
    }
}
