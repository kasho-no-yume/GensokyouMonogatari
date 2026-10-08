package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.network.InfoLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 绑定在特定仪式结构（patternId）上的行为逻辑。
 * 主仪式方块统一为 ritual_core，行为由匹配到的结构决定。
 *
 * 交互契约：成型仪式右键核心（无论空手或持物）一律打开仪式 UI，无例外；
 * 显示内容由各仪式经 {@link #uiActions} 注入自定义操作；
 * 潜行右键保留原物品链（贴放方块 / 旧行为直连）。
 */
public interface RitualBehavior {

    /**
     * UI 内的自定义操作按钮（显示在启停按钮之后）。enabled=false 时客户端置灰（服务端仍独立校验）。
     *
     * <p><b>{@link #id} 是 {@link #uiActions} 返回列表里的下标</b>，服务端原样回传给
     * {@link #onUiAction}：
     * <pre>
     *   客户端发 {@code RitualCoreMenu.BUTTON_ACTION_BASE + i}（BASE = 100）
     *   → 服务端 {@code onUiAction(..., id - BUTTON_ACTION_BASE)}，即 0/1/2
     * </pre>
     * 屏幕侧只有 {@code actionButtons[3]} 三个槽位，按<b>下标</b> i 取 payload 第 i 个 action，
     * 故有效 id 恒为 0/1/2。
     *
     * <p>⚠️ <b>与 {@link InfoLine#actionId} 不是同一条通道</b>：后者是信息行点击，确实要求
     * &ge; 10（0/1 被框架启停按钮占用，故 {@code SeiiService.ACTION_ACCEPT = 10} 那些 10/11
     * 属于它）。按钮通道写成 10 会让服务端回传 0 与之不等，点击被 {@code onUiAction} 的门禁
     * 静默吞掉——症状是"按钮能按但毫无反应"。
     */
    record UiAction(int id, String labelKey, boolean enabled) {

        public UiAction(int id, String labelKey) {
            this(id, labelKey, true);
        }
    }

    /**
     * 本行为定义的 per-core 状态容器工厂；无状态行为返回 {@code null}（默认）。
     *
     * <p>行为是 per-pattern 单例，故 per-core 状态由本工厂创建、由核心持有实例，
     * MUST NOT 存于行为实例自身。
     */
    default @javax.annotation.Nullable com.bitsson.gensokyou.ritual.RitualBehaviorState newState() {
        return null;
    }

    /** 启动成功时发放的成就 id（{@code null} = 无）。默认无。 */
    default @javax.annotation.Nullable String startAchievement(ServerLevel level, BlockPos corePos,
                                                               RitualMatch match,
                                                               SpiritPowerAccess core) {
        return null;
    }

    /**
     * 本仪式要下发的渲染态（客户端 BER 的数据来源）。返回 {@code null} = 本 tick 无渲染态。
     * 默认无——渲染表现由各行为自行组装（"逐帧动画不是所有仪式都有"）。
     */
    default @javax.annotation.Nullable RitualRenderState buildRenderState(RitualMatch match,
                                                                          SpiritPowerAccess core) {
        return null;
    }

    /**
     * 自定义物品接入面（如无尽藏的跨晶块合并箱）。返回 {@code null} = 用默认祭品台代理箱。
     * 核心会按 patternId 缓存返回实例。
     */
    default @javax.annotation.Nullable net.neoforged.neoforge.items.IItemHandler itemHandler(
            SpiritPowerAccess core) {
        return null;
    }

    /** 容量钩子未覆写哨兵。 */
    long CAPACITY_NOT_SET = Long.MIN_VALUE;

    /**
     * 本仪式的灵力缓存上限。返回 {@link #CAPACITY_NOT_SET} 表示未覆写——核心回落
     * {@code DEFAULT_CORE_CAPACITY} 并对该 patternId 打一次显式告警（不再静默回落）。
     *
     * <p>会话型仪式（造化/神恩/百鬼）按会话态返回：空闲 0、会话期 = 锁定配方 spCost。
     */
    default long capacity(int level, SpiritPowerAccess core) {
        return CAPACITY_NOT_SET;
    }

    /**
     * 行为可注入 UI 的自定义操作（返回列表的<b>下标</b>即 {@link UiAction#id}，
     * 有效值 0/1/2，见该记录的说明）。
     */
    default List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return List.of();
    }

    /**
     * 按查看者注入的自定义操作（默认转调无查看者版）。
     * 快照本就按玩家点对点组装，需要"按人显示/置灰"的行为覆写本重载（如神恩惯性开关）。
     */
    default List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core,
                                      @javax.annotation.Nullable ServerPlayer viewer) {
        return uiActions(level, corePos, match, core);
    }

    /**
     * 信息区内容行（随 RitualInfoPayload 推送，客户端照画）。
     * 默认实现 = 通用清单（祭品 ✓✗ + 配方 ✓✗/缺料摘要）；
     * 行为可覆写追加自定义行（燃烧行/产出速率等），可调 {@code defaultUiInfo} 复用通用清单。
     */
    default List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        return defaultUiInfo(level, corePos, match, core);
    }

    /** 按查看者组装信息行（默认转调无查看者版）；"查看者本人属性"类需求覆写本重载。 */
    default List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core,
                                  @javax.annotation.Nullable ServerPlayer viewer) {
        return uiInfo(level, corePos, match, core);
    }

    /** 通用信息行：祭品核对清单 + 当前激活配方标记（配方目录不在 GUI 展示，归 JEI）。 */
    static List<InfoLine> defaultUiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core) {
        List<InfoLine> lines = new ArrayList<>();
        // 祭品核对：按 slot 规范序，图标 + ✓/✗（单条要求恒 1 件，见 ritual-offerings）
        var patternOpt = RitualPatternLoader.byId(match.patternId());
        if (patternOpt.isPresent()) {
            RitualOfferings.Result result = RitualOfferings.check(patternOpt.get(), match, level);
            for (RitualOfferings.SlotStatus status : result.slots()) {
                ItemStack rep = status.requirement().item().representative();
                String itemId = rep.isEmpty()
                        ? "" : BuiltInRegistries.ITEM.getKey(rep.getItem()).toString();
                // CONTROL_ITEM：给图标补 18x18 凹槽边框（否则物品裸悬在信息框里）
                lines.add(new InfoLine("", new String[0], itemId, 0, -1F, status.satisfied(),
                        0, InfoLine.CONTROL_ITEM, InfoLine.LINK_NONE, "", new String[0]));
            }
        }
        // 当前激活配方
        if (core.activeRecipeId() != null) {
            lines.add(new InfoLine("gui.gensokyou.ritual.active_recipe",
                    new String[0], "", 0xFF2E8B57, -1F, null));
        }
        return lines;
    }

    /**
     * 自定义操作的服务端执行入口；返回 FAIL 表示未处理或失败。
     *
     * <p><b>{@code player} 可能为 {@code null}</b>：红石上升沿代管路径（见
     * {@link #redstoneTriggersUiAction}）在玩家不在场时触发，实现
     * {@code handlesStartViaUiAction} 的行为 MUST NOT 无条件解引用它。
     */
    default InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                         SpiritPowerAccess core, ServerPlayer player, int actionId) {
        return InteractionResult.PASS;
    }

    // ---- 灵力端点属性（resonance-relay-routing / ritual-power-attributes）----

    /**
     * 界面是否显示灵力核心槽。默认 true——除路由（万象共鸣）与托管存电（八方归元）显式豁免外，
     * 全部仪式开放该槽作为**供能入口**（扣费从槽内核心抽取，见 SpiritPowerHelper 三段式）。
     * 加具土命为注灵（流入）方向，语义共存：只有配方扣费会从槽抽取。
     */
    default boolean usesCoreSocket() {
        return true;
    }

/**
     * 本仪式是否使用核心 GUI 的额外物品槽（紧邻灵力核心槽右侧，可多格）。
     *
     * <p>与祭品台<b>刻意分开</b>：祭品台一台一件且是配方催化剂的载体，低阶结构台位少
     * （星移 1 阶只有 4 台），额外槽若占台就会挤掉催化剂位。实现 {@link RitualExtraSlots}
     * 的仪式其目标物走这些专用槽，祭品台全部留给催化剂。缺省不实现即 0 格。
     *
     * <p>取代旧的 {@code usesTargetSlot()} 单槽契约——后者强制 1 格且需要每加一个仪式就
     * 改一次 {@code RitualCoreMenu} 与客户端屏幕。
     */
    default int extraSlotCount() {
        return 0;
    }

    /**
 * 本仪式的灵力核心槽能量方向声明。
 *
 * <p><b>true（默认）= 电池 → 缓存</b>：确立"非发电仪式"的统一口径——灵力永远只是使用缓存，
 * 而缓存由槽内灵力核心自动灌注。发电仪式 MUST 显式覆写为 {@code false}（方向相反：缓存 → 电池）。
 *
 * <p><b>注意：本方法当前只是"声明"，没有框架代码读它。</b>真正搬运的是行为自己在
 * {@link #serverPassiveTick} 里调 {@code core.tickBatteryToCacheFill()}（受核的
 * {@code fillRatePerSecond} 限速）。实现为 true 的行为 SHALL 在 tick 内**先**补电、
 * **后**扣费，使同一 tick 内净值不出现负一档。默认取 true 是为了让"非发电 = 电池→缓存"
 * 成为无需逐个 override 的常态，而非常见的例外清单。
 */
    default boolean refillsCacheFromSocket() {
        return true;
    }

    /**
 * 红石上升沿是否由框架代管：触发 {@link #uiActions} 里的第一个可用操作。
 *
 * <p>缺省跟随 {@link #handlesStartViaUiAction()}——"能手动点按钮的仪式也能用红石控制"
 * 是通用诉求，逐个覆写 {@link #onRedstonePulse} 既重复又必漏。
 *
 * <p><b>两条约束</b>：
 * <ul>
 *   <li>行为若已自行覆写 {@link #onRedstonePulse}，框架 SHALL 优先调用其覆写实现，
 *       MUST NOT 同时执行默认触发；</li>
 *   <li>玩家不在场时触发，故框架传入的 {@code player} 为 {@code null}——
 *       实现 {@code handlesStartViaUiAction} 的行为 MUST NOT 无条件解引用它。</li>
 * </ul>
 *
 * <p><b>需要退出时</b>：玩家不在场就自动开打不合理的仪式（起手式召唤、纯会话型）
 * SHALL 显式返回 {@code false}。
 */
    default boolean redstoneTriggersUiAction() {
        return handlesStartViaUiAction();
    }

    /** 行为专属界面菜单 id（null = 通用仪式面板）。由核心右键按图案分派。 */
    default @javax.annotation.Nullable net.minecraft.resources.ResourceLocation screenMenuId() {
        return null;
    }

    /** 作为受灵汇的最大每秒输入速率；0 = 不具备该属性，不可被路由选为输出目标。值为上限，非保证带宽。 */
    default long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       SpiritPowerAccess core) {
        return 0L;
    }

    /** 作为供灵源的最大每秒输出速率；0 = 不具备该属性，不可被路由选为输入来源。值可随阶级变化。 */
    default long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core) {
        return 0L;
    }

    /** 结构存续期间的周期逻辑（核心每 tick 调用，仅 enabled 时；自行按 core.ageTicks() 控频）。 */
    default void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                            SpiritPowerAccess core) {
    }

    /**
     * 结构存续期间的周期逻辑，**不受 enabled 门控**（成型即每 tick 调用，自行按 core.ageTicks() 控频）。
     * 被动产能仪式的常驻搬运在此（如梦渡缓存→灵力核心自发注灵）；启动型仪式照旧用 serverTick。
     */
    default void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                   SpiritPowerAccess core) {
    }

    /** 红石上升沿脉冲回调（0→>0 跳变触发一次）；仅覆写的仪式响应，默认零副作用。 */
    default void onRedstonePulse(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
    }

    /** 结构从有效变为失效时回调一次（用于清理仪式产物，如隙间门）。 */
    default void onStructureLost(ServerLevel level, BlockPos corePos) {
    }

    /**
     * 仪式被停机（{@code enabled} 由 true→false）时回调；覆盖手动停止、结构失效、
     * 周期供给断供等全部停机路径。默认无。
     */
    default void onDisabled(ServerLevel level, BlockPos corePos, RitualMatch match,
                            SpiritPowerAccess core) {
    }

    /**
     * 核心方块被拆除/替换时回调（区块卸载不走此路径）。用于释放跨核资源（强制加载、
     * 孪生门等）——此时 {@code activeMatch} 仍可用。
     */
    default void onCoreRemoved(ServerLevel level, BlockPos corePos, RitualMatch match,
                               SpiritPowerAccess core) {
    }

    /**
     * 核心 BE 被移除（方块破坏或区块卸载）时回调；此时 {@code activeMatch} 仍可用。
     * 用于释放与 BE 生命周期绑定的跨核资源。默认无。
     */
    default void onRemoved(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
    }

    /**
     * 成型替换扩展点：重扫由未命中变为命中的瞬间调用。
     * 当前为空实现占位——将来"替换为大型 tileblock"在此填充，不改调用方。
     */
    default void onFormed(ServerLevel level, BlockPos corePos, RitualMatch match) {
    }

    /**
     * UI 启动按钮触发的行为侧启动逻辑（收费/前置校验等）。
     * 返回 FAIL 将阻止本次启动（enabled 不置位）。
     */
    default InteractionResult onStart(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core, ServerPlayer player) {
        return InteractionResult.SUCCESS;
    }

    /**
     * true = 本仪式启动唯一走自定义 UiAction 会话（造化/神恩）：
     * 通用 start()/stop() 与启停按钮通道对其整体让位，杜绝绕会话直接执行配方。
     */
    default boolean handlesStartViaUiAction() {
        return false;
    }

    /**
     * 启动型配方执行成功后的回调（原料已扣、activeRecipeId 已记录）。
     * effect 字段的解释权完全在行为侧。
     */
    default void onRecipeExecuted(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core, ServerPlayer player, RitualRecipe recipe) {
    }

    /** 玩家持物潜行右键核心（潜行保留的旧直连链路）。 */
    default InteractionResult onUseItem(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        SpiritPowerAccess core, ServerPlayer player, ItemStack stack) {
        return InteractionResult.SUCCESS;
    }

    /** 玩家空手潜行右键核心（潜行保留的旧直连链路）。 */
    default InteractionResult onUseEmptyHand(ServerLevel level, BlockPos corePos, RitualMatch match,
                                             SpiritPowerAccess core, ServerPlayer player) {
        return InteractionResult.SUCCESS;
    }
}
