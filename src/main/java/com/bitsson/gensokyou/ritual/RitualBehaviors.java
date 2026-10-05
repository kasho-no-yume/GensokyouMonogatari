package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.ritual.behavior.BousenBehavior;
import com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior;
import com.bitsson.gensokyou.ritual.behavior.BafangGuiyuanBehavior;
import com.bitsson.gensokyou.ritual.behavior.KagutsuchiFlameBehavior;
import com.bitsson.gensokyou.ritual.behavior.KanayamahikoBehavior;
import com.bitsson.gensokyou.ritual.behavior.KayaNoHimeBehavior;
import com.bitsson.gensokyou.ritual.behavior.KukunochiBehavior;
import com.bitsson.gensokyou.ritual.behavior.HaniyasuBehavior;
import com.bitsson.gensokyou.ritual.behavior.HoujounoTeihouBehavior;
import com.bitsson.gensokyou.ritual.behavior.HyakkiYagyoBehavior;
import com.bitsson.gensokyou.ritual.behavior.NichirinBehavior;
import com.bitsson.gensokyou.ritual.behavior.OyamatsumiBehavior;
import com.bitsson.gensokyou.ritual.behavior.ReiyokuBehavior;
import com.bitsson.gensokyou.ritual.behavior.ResonanceRelayBehavior;
import com.bitsson.gensokyou.ritual.behavior.SairEnergyBehavior;
import com.bitsson.gensokyou.ritual.behavior.SeiiBehavior;
import com.bitsson.gensokyou.ritual.behavior.ShujouYorokuBehavior;
import com.bitsson.gensokyou.ritual.behavior.SunakoBehavior;
import com.bitsson.gensokyou.ritual.behavior.TsukikageBehavior;
import com.bitsson.gensokyou.ritual.behavior.WatatsumiBehavior;
import com.bitsson.gensokyou.ritual.behavior.WujinzangBehavior;
import com.bitsson.gensokyou.ritual.behavior.YaoyorozuGraceBehavior;
import com.bitsson.gensokyou.ritual.behavior.YumewatariBehavior;
import com.bitsson.gensokyou.ritual.behavior.ZaohuaCraftingBehavior;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class RitualBehaviors {

    private static final Map<ResourceLocation, RitualBehavior> REGISTRY = new HashMap<>();

    public static final ResourceLocation RESONANCE = Gensokyou.id("resonance_relay");
    public static final ResourceLocation BARRIER_BREAK = Gensokyou.id("barrier_break_circle");
    public static final ResourceLocation KAGUTSUICHI = Gensokyou.id("kagutsuchi_flame_circle");
    public static final ResourceLocation BAFANG_GUIYUAN = Gensokyou.id("bafang_guiyuan_circle");
    public static final ResourceLocation ZAOHUA = Gensokyou.id("zaohua_circle");
    public static final ResourceLocation KAMI_NO_MEGUMI = Gensokyou.id("kami_no_megumi_circle");
    public static final ResourceLocation SEII = Gensokyou.id("seii_circle");
    public static final ResourceLocation YUMEWATARI = Gensokyou.id("yumewatari_circle");
    public static final ResourceLocation NICHIRIN = Gensokyou.id("nichirin_circle");
    public static final ResourceLocation TSUKIKAGE = Gensokyou.id("tsukikage_circle");
    public static final ResourceLocation OYAMATSUMI = Gensokyou.id("oyamatsumi_circle");
    public static final ResourceLocation KUKUNOCHI = Gensokyou.id("kukunochi_circle");
    public static final ResourceLocation HANIYASU = Gensokyou.id("haniyasu_circle");
    public static final ResourceLocation KAYA_NO_HIME = Gensokyou.id("kaya_no_hime_circle");
    public static final ResourceLocation WATATSUMI = Gensokyou.id("watatsumi_circle");
    public static final ResourceLocation SHUJOU = Gensokyou.id("shujou_yoroku_circle");
    public static final ResourceLocation WUJINZANG = Gensokyou.id("wujinzang_circle");
    public static final ResourceLocation KANAYAMAHIKO = Gensokyou.id("kanayamahiko_circle");
    public static final ResourceLocation HOUJOUNO_TEIHOU = Gensokyou.id("houjouno_teihou_circle");
    public static final ResourceLocation SAIR_ENERGY = Gensokyou.id("sair_energy_circle");
    /**
     * 百鬼夜行：祭品化召唤仪式。召唤类仪式由 {@code startsSessionViaUiAction} 独占启停
     * （无门票，故 MUST NOT 走会预扣 {@code spCost} 的通用 {@code start()}）。
     */
    public static final ResourceLocation HYAKKI_YAGYO = Gensokyou.id("hyakki_yagyo_circle");
    /**
     * 灵浴：浴亭内的玩家注灵仪式。启停门控、非会话型（不绑定启动者），
     * 充灵速率按<b>结构等级</b>对应的玩家阶级标准池取值（不按玩家自身阶级）。
     */
    public static final ResourceLocation REIYOKU = Gensokyou.id("reiyoku_circle");
    /**
     * 忘川灯坛：全亮门控产灵。启停门控（pattern {@code toggleable:true}）、非会话型。
     * 产灵判据 = {@code enabled} ∧ 结构内全部蜡烛点亮；熄灭<b>不</b>停机（补灯即自动恢复），
     * 熄灭后<b>自我冻结</b>（不再产灵也不再继续随机熄灭）。蜡烛是结构的一部分——打掉即散坛。
     */
    public static final ResourceLocation BOUSEN = Gensokyou.id("bousen_circle");
    /**
     * 少名：自定义炼药仪式。祭品台摆瓶装三途川水、核心额外槽放炼药试剂，
     * 点一次按钮跑一个批次（{@code min(有效台数, 缓存 ÷ 单价)} 瓶，原位替换台面）。
     * 一次性而非启停型；缓存常驻故可被万象共鸣选为下游目标。
     */
    public static final ResourceLocation SUNAKO = Gensokyou.id("sunako_circle");

    static {
        register(RESONANCE, new ResonanceRelayBehavior());
        register(BARRIER_BREAK, new BarrierBreakBehavior());
        register(KAGUTSUICHI, new KagutsuchiFlameBehavior());
        register(BAFANG_GUIYUAN, new BafangGuiyuanBehavior());
        register(ZAOHUA, new ZaohuaCraftingBehavior());
        register(KAMI_NO_MEGUMI, new YaoyorozuGraceBehavior());
        register(SEII, new SeiiBehavior());
        register(YUMEWATARI, new YumewatariBehavior());
        register(NICHIRIN, new NichirinBehavior());
        register(TSUKIKAGE, new TsukikageBehavior());
        register(OYAMATSUMI, new OyamatsumiBehavior());
        register(KUKUNOCHI, new KukunochiBehavior());
        register(HANIYASU, new HaniyasuBehavior());
        register(KAYA_NO_HIME, new KayaNoHimeBehavior());
        register(WATATSUMI, new WatatsumiBehavior());
        register(SHUJOU, new ShujouYorokuBehavior());
        register(WUJINZANG, new WujinzangBehavior());
        register(KANAYAMAHIKO, new KanayamahikoBehavior());
        register(HOUJOUNO_TEIHOU, new HoujounoTeihouBehavior());
        register(SAIR_ENERGY, new SairEnergyBehavior());
        register(HYAKKI_YAGYO, new HyakkiYagyoBehavior());
        register(REIYOKU, new ReiyokuBehavior());
        register(BOUSEN, new BousenBehavior());
        register(SUNAKO, new SunakoBehavior());
    }

    private RitualBehaviors() {
    }

    public static void register(ResourceLocation patternId, RitualBehavior behavior) {
        REGISTRY.put(patternId, behavior);
    }

    /** 献祭工具/竿仪式（共享缓存分派与产出光柱语义；绵津见献祭钓鱼竿）。 */
    public static boolean isToolSacrifice(ResourceLocation patternId) {
        return patternId.equals(OYAMATSUMI) || patternId.equals(KUKUNOCHI)
                || patternId.equals(HANIYASU) || patternId.equals(KAYA_NO_HIME)
                || patternId.equals(WATATSUMI);
    }

    /** 献祭光柱色索引（服务端写入渲染态；客户端映射 RGB）：石0/木1/土2/草3/绵津见4水蓝/众生余录5紫/丰穰神6金穗/少名7汤青。 */
    public static int sacrificeColorIndex(ResourceLocation patternId) {
        if (patternId.equals(KUKUNOCHI)) {
            return 1;
        }
        if (patternId.equals(HANIYASU)) {
            return 2;
        }
        if (patternId.equals(KAYA_NO_HIME)) {
            return 3;
        }
        if (patternId.equals(WATATSUMI)) {
            return 4;
        }
        if (patternId.equals(SHUJOU)) {
            return 5;
        }
        if (patternId.equals(HOUJOUNO_TEIHOU)) {
            return 6;
        }
        if (patternId.equals(SUNAKO)) {
            return 7;
        }
        return 0;
    }

    public static Optional<RitualBehavior> get(ResourceLocation patternId) {
        return Optional.ofNullable(REGISTRY.get(patternId));
    }
}
