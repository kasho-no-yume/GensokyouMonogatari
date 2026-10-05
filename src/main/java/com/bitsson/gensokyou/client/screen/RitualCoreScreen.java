package com.bitsson.gensokyou.client.screen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.client.ClientRitualState;
import com.bitsson.gensokyou.menu.RitualCoreMenu;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.RitualInfoPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 仪式界面（176 宽，与玩家物品栏同宽）：固定头（名称/阶级/状态/灵力）
 * + 头排（电池槽 + 启停按钮）+ 行为自主信息区（InfoLine 逐行渲染）
 * + 底部玩家物品栏。数据来自服务端推送的 RitualInfoPayload（ClientRitualState 暂存）。
 */
public class RitualCoreScreen extends AbstractContainerScreen<RitualCoreMenu> {

    private static final int PANEL_WIDTH = 176;
    /** 信息区高度（含固定头），信息行渲染下缘以此为准。 */
    private static final int INFO_HEIGHT = 150;
    /** 背包区高度：主仓 3 行 + 快捷栏 1 行 + 间隔。 */
    private static final int INVENTORY_HEIGHT = 84;
    private static final int PANEL_HEIGHT = INFO_HEIGHT + INVENTORY_HEIGHT;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_OK = 0xFF2E8B57;
    private static final int COLOR_BAD = 0xFFB22222;
    /** 背景贴图：替换 assets/gensokyou/textures/gui/ritual_core.png 即可整体换肤。 */
    private static final ResourceLocation BACKGROUND =
            Gensokyou.id("textures/gui/ritual_core.png");

    /** 信息行布局：图标行占 18px，纯文本行占 11px。 */
    private static final int INFO_X = 8;
    private static final int INFO_Y_START = 60;
    /**
     * 目标物品槽（星移之仪的增幅核）占据信息区首行时的高度。
     * 槽在 (8,60)、18px 见方，标注画在其右侧，故整条占 {@code INFO_TARGET_ROW_H}。
     */
    private static final int INFO_TARGET_ROW_H = 20;
    private static final int INFO_MAX_Y = INFO_HEIGHT - 22;
    /** 信息盒右钳界：背景贴图分隔线 x=116 留 4px 余量，进度条/高亮/命中区不得越入按钮列。 */
    private static final int INFO_BOX_RIGHT = 112;
    /** 启停按钮（头排右列）。 */
    private static final int TOGGLE_BUTTON_X = 120;
    private static final int TOGGLE_BUTTON_W = 50;
    /**
     * 三态标记（✓/✗）右对齐锚点 = 信息盒内壁。
     *
     * <p>此前固定在 {@code STATE_X = 124}，但右侧按钮列从 {@link #TOGGLE_BUTTON_X}(120) 起——
     * 标记实际画进了按钮列里（表现为"超框"）。改为右对齐到信息盒内壁，绘制与文本让位都以此为准。
     */
    private static final int STATE_RIGHT = INFO_BOX_RIGHT - 1;
    /** 三态标记预留宽（"✓"/"✗" 约 6px，留 2px 余量）。 */
    private static final int STATE_MARK_W = 8;

    @Nullable
    private Button startButton;
    @Nullable
    private Button stopButton;
    private final Button[] actionButtons = new Button[3];
    /** 信息区滚动状态与可交互行命中矩形（绝对屏幕坐标 x,y,w,h,actionId）。 */
    private int infoScroll;
    private int infoContentHeight;
    /** 玩家已显式滚轮翻动过：本次开界面内不再自动对齐，不与玩家抢镜头。 */
    private boolean userScrolled;
    /**
     * 本帧信息区内容的实际起始 y：目标物品槽可见时被压低一行。
     * 悬停命中区、滚动条、裁剪区、绘制起点全部共用它，避免各处各算一套。
     */
    private int infoTop = INFO_Y_START;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private final List<int[]> interactiveRowHits = new ArrayList<>();
    private final List<TipHit> tipRowHits = new ArrayList<>();

    /** 悬浮明细行命中区（绝对坐标）+ 行数据。 */
    private record TipHit(int x, int y, int w, int h, InfoLine line) {
    }

    public RitualCoreScreen(RitualCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        userScrolled = false; // 每次开界面重置"玩家已手动滚动"，恢复自动对齐
        // 启停按钮：头排右列（电池槽右侧）
        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.ritual.start"), b -> send(0))
                .bounds(leftPos + TOGGLE_BUTTON_X, topPos + 40, TOGGLE_BUTTON_W, 20).build());
        stopButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.gensokyou.ritual.stop"), b -> send(1))
                .bounds(leftPos + TOGGLE_BUTTON_X, topPos + 62, TOGGLE_BUTTON_W, 20).build());
        // 首帧即隐藏，显隐唯一由 containerTick 按服务端 payload 收敛（防非 toggleable 仪式按钮闪现）
        startButton.visible = false;
        stopButton.visible = false;
        // 行为注入操作按钮：启停下方竖排
        for (int i = 0; i < actionButtons.length; i++) {
            final int index = i;
            actionButtons[i] = addRenderableWidget(Button.builder(Component.literal("?"),
                            b -> send(RitualCoreMenu.BUTTON_ACTION_BASE + index))
                    .bounds(leftPos + TOGGLE_BUTTON_X, topPos + 88 + i * 22, TOGGLE_BUTTON_W, 20).build());
            actionButtons[i].visible = false;
        }
    }

    private void send(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    /** 该仪式是否声明灵力核心槽：由行为 usesCoreSocket 决定（默认开放，路由/托管豁免），客户端按 payload 图案判定。 */
    private static boolean coreSocket(RitualInfoPayload info) {
        if (info.patternId().isEmpty()) {
            return false;
        }
        return com.bitsson.gensokyou.ritual.RitualBehaviors
                .get(ResourceLocation.parse(info.patternId()))
                .map(com.bitsson.gensokyou.ritual.RitualBehavior::usesCoreSocket)
                .orElse(true);
    }

    /** 该仪式声明了几个额外物品槽（星移的增幅核、少名的炼药试剂等）。 */
    private static int extraSlots(com.bitsson.gensokyou.network.RitualInfoPayload info) {
        if (info.patternId().isEmpty()) {
            return 0;
        }
        // MUST mirror RitualCoreBlockEntity#extraSlotCount(): read RitualExtraSlots#slotCount,
        // NOT RitualBehavior#extraSlotCount (default 0). The old path made syncExtraSlotsVisible(0)
        // every frame -> slot and its frame invisible while server logic stayed fully functional.
        return com.bitsson.gensokyou.ritual.RitualBehaviors
                .get(ResourceLocation.parse(info.patternId()))
                .filter(com.bitsson.gensokyou.ritual.RitualExtraSlots.class::isInstance)
                .map(com.bitsson.gensokyou.ritual.RitualExtraSlots.class::cast)
                .map(com.bitsson.gensokyou.ritual.RitualExtraSlots::slotCount)
                .map(count -> Math.max(0, Math.min(4, count)))
                .orElse(0);
    }

    /** 额外槽的标注 lang 键（行为未声明或返回空串时无标注）。 */
    private String extraSlotLabelKey() {
        RitualInfoPayload info = ClientRitualState.latest();
        if (info == null || info.patternId().isEmpty()) {
            return "";
        }
        return com.bitsson.gensokyou.ritual.RitualBehaviors
                .get(ResourceLocation.parse(info.patternId()))
                .map(com.bitsson.gensokyou.menu.RitualCoreMenu::extraSlotLabelKey)
                .orElse("");
    }

    @Override
    public void containerTick() {
        super.containerTick();
        RitualInfoPayload info = ClientRitualState.latest();
        boolean ours = info != null && info.blockPos().equals(menu.pos());
        boolean showButtons = ours && info.toggleable();
        menu.syncCoreSocketVisible(ours && coreSocket(info));
        menu.syncExtraSlotsVisible(ours ? extraSlots(info) : 0);
        // 额外物品槽占信息区首行 → 整条信息区下移一行（仅声明该槽的仪式付此代价）
        infoTop = INFO_Y_START + (menu.anyExtraSlotShown() ? INFO_TARGET_ROW_H : 0);
        if (startButton != null) {
            startButton.visible = showButtons && !(ours && info.enabled());
        }
        if (stopButton != null) {
            stopButton.visible = showButtons && ours && info.enabled();
        }
        // 行为注入的自定义操作按钮：随 payload 动态显隐
        for (int i = 0; i < actionButtons.length; i++) {
            Button button = actionButtons[i];
            if (button == null) {
                continue;
            }
            RitualInfoPayload.Action action = ours && i < info.actions().size()
                    ? info.actions().get(i) : null;
            if (action != null) {
                button.setMessage(Component.translatable(action.labelKey()));
                button.visible = true;
                // 服务端声明置灰（如造化合成飞行期）：禁用点击并走灰态渲染；服务端仍独立拒绝双保险
                button.active = action.enabled();
            } else {
                button.visible = false;
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // renderLabels 处于面板相对 pose 下拿不到鼠标，先缓存绝对坐标供行悬停/命中比对
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderInfoTips(graphics, mouseX, mouseY);
    }

    /** 信息行悬浮明细：链接行首行自动带对象名；行为按 tipKey 模板内 \n 分行，不自动换行。 */
    private void renderInfoTips(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TipHit hit : tipRowHits) {
            if (mouseX >= hit.x() && mouseX < hit.x() + hit.w()
                    && mouseY >= hit.y() && mouseY < hit.y() + hit.h()) {
                List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
                if (hit.line().controlKind() == InfoLine.CONTROL_LINK) {
                    lines.addAll(font.split(Component.translatable(hit.line().textKey()),
                            Integer.MAX_VALUE));
                }
                // 传极大宽度：split 只在模板内的 \n 断行，不做按宽换行
                lines.addAll(font.split(Component.translatable(hit.line().tipKey(),
                        (Object[]) hit.line().tipArgs()), Integer.MAX_VALUE));
                graphics.renderTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int[] hit : interactiveRowHits) {
                if (mouseX >= hit[0] && mouseX < hit[0] + hit[2]
                        && mouseY >= hit[1] && mouseY < hit[1] + hit[3]) {
                    send(RitualCoreMenu.BUTTON_ACTION_BASE + hit[4]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewHeight = INFO_MAX_Y - infoTop;
        boolean overInfo = mouseX >= leftPos + INFO_X && mouseX < leftPos + INFO_BOX_RIGHT
                && mouseY >= topPos + infoTop && mouseY < topPos + INFO_MAX_Y;
        if (overInfo && infoContentHeight > viewHeight) {
            infoScroll = Mth.clamp((int) (infoScroll - scrollY * 18D),
                    0, infoContentHeight - viewHeight);
            userScrolled = true;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, PANEL_WIDTH, PANEL_HEIGHT,
                PANEL_WIDTH, PANEL_HEIGHT);
        // 额外物品槽落在信息区首行，GUI 贴图里没有对应槽框，
        // 故在此逐个补画与电池槽同款的描边框（注意框在槽坐标 −1 处，见 paintSlotFrame）。
        for (int i = 0; i < menu.extraSlotsShown(); i++) {
            paintSlotFrame(graphics, leftPos + RitualCoreMenu.extraSlotX(i) - 1,
                    topPos + RitualCoreMenu.extraSlotY() - 1);
        }
    }

    /**
     * 18×18 槽框：暗色凹底 + 1px 亮紫描边。
     *
     * <p><b>坐标约定</b>：本 GUI 的槽框画在<b>槽坐标 −1</b> 处（与贴图里电池槽
     * {@code (29,39) 框 / (30,40) 槽} 的关系相同），物品由原版 {@code renderSlot}
     * 画在槽坐标上，故天然内缩 1px 居中。调用方须传 {@code slotX - 1, slotY - 1}。
     *
     * <p><b>为何必须填凹底</b>：电池槽的底是 GUI 贴图里<b>烘焙</b>好的，而额外槽落在
     * 信息区首行——那是贴图里一片空白。原先只描 1px 边，空槽时那圈线在深色面板上几乎
     * 看不见，读作"这里没有槽"，直到玩家把东西放进去才注意到（实机反馈："没放材料时格子
     * 视觉上还是没有，直到放了材料才出现"）。填一层暗底即得 vanilla 槽的空态观感。
     */
    private static void paintSlotFrame(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 1, y + 1, x + 17, y + 17, SLOT_RECESS);
        graphics.fill(x, y, x + 18, y + 1, SLOT_FRAME_BORDER);
        graphics.fill(x, y + 17, x + 18, y + 18, SLOT_FRAME_BORDER);
        graphics.fill(x, y, x + 1, y + 18, SLOT_FRAME_BORDER);
        graphics.fill(x + 17, y, x + 18, y + 18, SLOT_FRAME_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RitualInfoPayload info = ClientRitualState.latest();
        if (info == null || !info.blockPos().equals(menu.pos())) {
            return;
        }
        var font = this.font;
        if (info.patternId().isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.no_pattern"),
                    INFO_X, 4, COLOR_BAD, false);
            return;
        }
        // —— 固定头：名称 / 状态 / 阶级 / 灵力 ——
        ResourceLocation id = ResourceLocation.parse(info.patternId());
        graphics.drawString(font,
                Component.translatableWithFallback("jei." + id.getNamespace() + ".ritual." + id.getPath(),
                        id.getPath().replace('_', ' ')), INFO_X, 4, COLOR_TEXT, false);
        // 启停状态标签只对 toggleable 仪式有意义；被动产能仪式（梦渡等）显示中性"已成型"，
        // 不得渲染"已停止"误导为可启动而未启动
        boolean passive = !info.toggleable();
        Component state = passive
                ? Component.translatable("gui.gensokyou.ritual.formed")
                : Component.translatable(info.enabled()
                        ? "gui.gensokyou.ritual.running" : "gui.gensokyou.ritual.idle");
        graphics.drawString(font, state, PANEL_WIDTH - 8 - font.width(state), 4,
                passive || info.enabled() ? COLOR_OK : COLOR_BAD, false);
        graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.tier", info.tier()),
                INFO_X, 15, COLOR_TEXT, false);
        // 零缓存核心（共鸣塔）不显示"灵力 0/0"行，概要数据由行为信息行替代；
        // 数值 MUST 紧凑化——托管池满配可达 12 位数，raw long 会画穿 176px 面板
        if (info.capacity() > 0) {
            graphics.drawString(font, Component.translatable("gui.gensokyou.ritual.sp",
                    com.bitsson.gensokyou.network.InfoLine.compact(info.stored()),
                    com.bitsson.gensokyou.network.InfoLine.compact(info.capacity())),
                    INFO_X, 26, COLOR_TEXT, false);
        }
        // 灵力核心槽标注（槽体由菜单协议渲染）：随菜单最终可见性绘制
        if (menu.coreSocketShown()) {
            graphics.drawString(font,
                    Component.translatable("gui.gensokyou.ritual.spirit_core_slot"),
                    RitualCoreMenu.BATTERY_SLOT_X + 20, RitualCoreMenu.BATTERY_SLOT_Y + 4,
                    COLOR_TEXT, false);
        }
        // 额外物品槽标注：单槽时画在槽右侧（与迁移前的目标槽同位）；多槽时行内放不下，
        // 改由信息区说明（各仪式自行补 InfoLine），避免标注互相压字。
        int extraShown = menu.extraSlotsShown();
        if (extraShown == 1) {
            String labelKey = extraSlotLabelKey();
            if (!labelKey.isEmpty()) {
                graphics.drawString(font, Component.translatable(labelKey),
                        RitualCoreMenu.extraSlotX(0) + 20, RitualCoreMenu.extraSlotY() + 4,
                        COLOR_TEXT, false);
            }
        }
        // —— 信息区：行为产出的 InfoLine 逐行渲染（电池槽行下方起笔，避免与槽标注叠行） ——
        renderInfoLines(graphics, font, info.infoLines());
        // 一次性状态消息（启停反馈等）：信息区底部
        if (!info.statusKey().isEmpty()) {
            graphics.drawString(font, Component.translatable(info.statusKey()),
                    INFO_X, INFO_HEIGHT - 12, COLOR_TEXT, false);
        }
    }

    /**
     * 一条 InfoLine 的客户端排版结果。
     *
     * <p>文本按信息盒可用宽预换行（{@code wrapped}），行高随换行段数增长，
     * 使"量内容高度"与"实际绘制"共用同一份排版结果——否则换行会画到框外或吃掉下一行。
     */
    private record InfoRow(InfoLine line, @Nullable MutableComponent text, int textX,
                           boolean hasIcon, List<FormattedCharSequence> wrapped, int height) {
    }

    /** 纯文本行高 / 带图标行高（图标 16px + 上下留白）。 */
    private static final int ROW_H_TEXT = 11;
    private static final int ROW_H_ICON = 18;
    /** 槽框描边色：取自贴图里电池槽框的实际像素 #AC98D6。 */
    private static final int SLOT_FRAME_BORDER = 0xFFAC98D6;
    /** 空槽凹底：额外槽所在处贴图没有烘焙槽底，空槽时 MUST 自绘一层暗底才读得出"这是个槽"。 */
    private static final int SLOT_RECESS = 0xFF23232B;
    /** 进度条相对 textX 的固定起点偏移（barX = textX + BAR_OFFSET）。 */
    private static final int BAR_OFFSET = 52;

    /**
     * 文本可用宽度：右缘一律钳到信息盒 {@link #INFO_BOX_RIGHT}。
     *
     * <p>刻意不用 {@code PANEL_WIDTH - 6}：那会让文字横穿背景分隔线画进右侧按钮列。
     * 有三态标记时还要让开 {@link #STATE_X}，有进度条时让开 {@link #BAR_OFFSET} 起笔区。
     */
    private int maxTextWidth(InfoLine line, int textX) {
        int maxW = INFO_BOX_RIGHT - textX;
        if (line.state() != null) {
            // 为右对齐的 ✓/✗ 让位：标记左缘 = STATE_RIGHT - STATE_MARK_W
            maxW = Math.min(maxW, STATE_RIGHT - STATE_MARK_W - textX);
        }
        if (line.progress() >= 0F) {
            maxW = Math.min(maxW, BAR_OFFSET - 2);
        }
        return Math.max(1, maxW);
    }

    /** 构造行内可见文本（与服务端语义无关的纯客户端拼装）。 */
    private @Nullable MutableComponent buildText(InfoLine line) {
        if (line.controlKind() == InfoLine.CONTROL_LINK) {
            // 可见行 = 状态标记 + 名称；坐标/上限等明细走悬浮 tip
            String stateKey = switch (line.linkState()) {
                case InfoLine.LINK_IN -> "gui.gensokyou.ritual.link_in";
                case InfoLine.LINK_OUT -> "gui.gensokyou.ritual.link_out";
                default -> "gui.gensokyou.ritual.link_none";
            };
            return Component.translatable(stateKey)
                    .append(Component.translatable(line.textKey()));
        }
        if (line.controlKind() == InfoLine.CONTROL_ATTR) {
            // 属性名客户端本地化（服务端只有 key），值字符串已由行为格式化
            return Component.translatable(line.textKey())
                    .append(Component.literal(": "
                            + (line.textArgs().length > 0 ? line.textArgs()[0] : "")));
        }
        if (!line.textKey().isEmpty()) {
            return Component.translatable(line.textKey(), (Object[]) line.textArgs());
        }
        return null;
    }

    /**
     * 预排版：解图标、拼文本、按信息盒宽换行、算行高。
     *
     * <p>{@code CONTROL_ITEM} 行的图标即使 id 解析不出物品也保留占位（画空槽），
     * 免得"槽突然消失"导致行高跳变。
     */
    private InfoRow layoutRow(Font font, InfoLine line) {
        int textX = INFO_X;
        boolean framed = line.controlKind() == InfoLine.CONTROL_ITEM;
        boolean hasIcon = false;
        // ⚠️ 外层条件 MUST 同时接受 framed：CONTROL_ITEM 的语义是"画一个空槽框"，
        //    而 iconItemId 为空串恰恰是"槽里还没东西"。若只判 iconItemId 非空，
        //    空槽会整行退化成纯文本行——玩家看不见这里有个能放东西的格子
        //    （实机反馈：试剂槽空着时信息栏什么都不画）。
        if (framed || !line.iconItemId().isEmpty()) {
            ItemStack icon = ClientRitualState.stackFor(line.iconItemId());
            if (!icon.isEmpty() || framed) {
                hasIcon = true;
                textX = INFO_X + 20;
            }
        }
        MutableComponent text = buildText(line);
        List<FormattedCharSequence> wrapped = text == null
                ? List.of()
                : font.split(text, maxTextWidth(line, textX));
        int base = hasIcon ? ROW_H_ICON : ROW_H_TEXT;
        int height = base + Math.max(0, wrapped.size() - 1) * font.lineHeight;
        return new InfoRow(line, text, textX, hasIcon, wrapped, height);
    }

    /**
     * InfoLine 渲染：滚动窗口 + 裁剪；图标(18px) + 自动换行文本 + 进度条 + ✓✗。
     *
     * <p>溢出可发现性（通用，非特定仪式）：内容超出视口时渲染滚动条滑块；且当快照里存在
     * 落在视口外的可交互行时自动把滚动位置对齐到它 —— 否则"决策按钮在第 16 行、视口只有 6 行"
     * 会表现为"面板里啥也没有"。玩家一旦显式滚轮翻动过，本会话内不再自动对齐（不与玩家抢镜头）。
     */
    private void renderInfoLines(GuiGraphics graphics, Font font, List<InfoLine> lines) {
        int viewHeight = INFO_MAX_Y - infoTop;
        List<InfoRow> rows = new ArrayList<>(lines.size());
        int total = 0;
        int firstInteractiveIndex = -1;
        int firstInteractiveTop = 0;
        for (int i = 0; i < lines.size(); i++) {
            InfoLine line = lines.get(i);
            InfoRow row = layoutRow(font, line);
            rows.add(row);
            if (firstInteractiveIndex < 0 && line.interactive()) {
                firstInteractiveIndex = i;
                firstInteractiveTop = total;
            }
            total += row.height();
        }
        this.infoContentHeight = total;
        int maxScroll = Math.max(0, total - viewHeight);
        if (!userScrolled) {
            int target = infoScroll;
            if (firstInteractiveIndex >= 0) {
                int rowTop = firstInteractiveTop;
                int rowBottom = rowTop + rows.get(firstInteractiveIndex).height();
                if (rowTop < infoScroll || rowBottom > infoScroll + viewHeight) {
                    target = rowTop; // 对齐到首个可交互行的顶边
                }
            }
            infoScroll = Mth.clamp(target, 0, maxScroll);
        } else {
            infoScroll = Mth.clamp(infoScroll, 0, maxScroll);
        }
        interactiveRowHits.clear();
        tipRowHits.clear();
        graphics.enableScissor(leftPos + INFO_X - 4, topPos + infoTop - 2,
                leftPos + PANEL_WIDTH - 4, topPos + INFO_MAX_Y + 2);
        int y = infoTop - infoScroll;
        for (InfoRow row : rows) {
            int rowH = row.height();
            renderInfoRow(graphics, font, row, y);
            boolean visible = y + rowH > infoTop && y < INFO_MAX_Y;
            // 命中区右缘钳至信息盒：启停/行为按钮列（x≥116）点击不被行交互吞掉
            int rowW = INFO_BOX_RIGHT - (INFO_X - 4);
            InfoLine line = row.line();
            if (line.interactive() && visible) {
                interactiveRowHits.add(new int[]{leftPos + INFO_X - 4,
                        topPos + Math.max(y, infoTop), rowW,
                        Math.min(y + rowH, INFO_MAX_Y) - Math.max(y, infoTop),
                        line.actionId()});
            }
            if (line.tipped() && visible) {
                tipRowHits.add(new TipHit(leftPos + INFO_X - 4, topPos + Math.max(y, infoTop),
                        rowW,
                        Math.min(y + rowH, INFO_MAX_Y) - Math.max(y, infoTop), line));
            }
            y += rowH;
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            renderScrollbar(graphics, viewHeight, maxScroll);
        }
    }

    /**
     * 滚动条滑块：贴在信息盒右缘内侧（钳在 {@link #INFO_BOX_RIGHT} 之内，不越分隔线）。
     * 滑块高 ∝ 视口/内容，位置随 infoScroll 线性变化；总高不足一屏时（maxScroll 很小）按比例放大。
     */
    private void renderScrollbar(GuiGraphics graphics, int viewHeight, int maxScroll) {
        int trackH = viewHeight;
        int thumbH = Math.max(12, (int) ((long) trackH * viewHeight
                / Math.max(1, viewHeight + maxScroll)));
        thumbH = Math.min(thumbH, trackH);
        int travel = trackH - thumbH;
        int thumbY = infoTop + (maxScroll <= 0 ? 0
                : (int) ((long) travel * infoScroll / maxScroll));
        int x = INFO_BOX_RIGHT - 2;
        graphics.fill(x, infoTop, x + 2, infoTop + trackH, 0x30FFFFFF);
        graphics.fill(x, thumbY, x + 2, thumbY + thumbH, 0xC0FFFFFF);
    }

    /** 单行绘制：换行后的每一段都画（不再只取首段），悬停交互行淡高亮。 */
    private void renderInfoRow(GuiGraphics graphics, Font font, InfoRow row, int y) {
        InfoLine line = row.line();
        int rowH = row.height();
        int textX = row.textX();
        int color = line.color() != 0 ? line.color() : COLOR_TEXT;
        if (line.interactive()) {
            int mx = lastMouseX - leftPos;
            int my = lastMouseY - topPos;
            if (mx >= INFO_X - 4 && mx < INFO_BOX_RIGHT && my >= y && my < y + rowH) {
                graphics.fill(INFO_X - 4, y, INFO_BOX_RIGHT, y + rowH, 0x22FFFFFF);
            }
        }
        if (row.hasIcon()) {
            // CONTROL_ITEM：先补凹槽边框，再画物品（否则物品裸悬在信息框里）
            if (line.controlKind() == InfoLine.CONTROL_ITEM) {
                paintSlotFrame(graphics, INFO_X, y);
            }
            ItemStack icon = ClientRitualState.stackFor(line.iconItemId());
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, INFO_X + 1, y + 1);
            }
        }
        // 注：renderLabels 处于面板相对 pose，坐标 MUST NOT 再叠 leftPos/topPos
        int lineY = y + (row.hasIcon() ? 4 : 0);
        for (FormattedCharSequence segment : row.wrapped()) {
            graphics.drawString(font, segment, textX, lineY, color, false);
            lineY += font.lineHeight;
        }
        if (line.progress() >= 0F) {
            int barX = textX + BAR_OFFSET;
            int barW = Math.min(40, INFO_BOX_RIGHT - barX);
            int barY = y + 5;
            if (barW > 0) {
                graphics.fill(barX, barY, barX + barW, barY + 4, 0xFF202030);
                graphics.fill(barX, barY,
                        barX + (int) (barW * Math.min(1F, line.progress())),
                        barY + 4, 0xFFE8912A);
            }
        }
        if (line.state() != null) {
            String mark = line.state() ? "✓" : "✗";
            int markColor = line.state() ? COLOR_OK : COLOR_BAD;
            graphics.drawString(font, mark, STATE_RIGHT - font.width(mark), y + 4, markColor, true);
        }
    }
}
