package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端的「UUID → 实体」索引。
 *
 * <p><b>为什么需要它</b>：服务端有 {@code ServerLevel#getEntity(UUID)}，
 * 而 {@code Level} 只有 {@code getEntity(int)} —— <b>客户端没有按 UUID 查实体的公共 API</b>
 * （{@code TouhouBossBarRenderer} 的类注释记着同一条）。
 * 凡是客户端需要按 UUID 解析实体的功能，都必须自己维护这张表。
 *
 * <p><b>为什么按 UUID 而不是按网络 id</b>：网络 id 是进程内递增计数，会绕回、会被复用。
 * 灵符的目标引用可以跨越目标的整个生命周期（弹寿命默认 1200 tick），
 * 期间目标完全可能死亡并把 id 让给别的实体 —— 按 id 查的结果是<b>静默</b>地转而追一个
 * 无关实体：没有报错、没有日志、没有任何可观测症状。UUID 不会被复用，查不到就是查不到。
 *
 * <p><b>插入点的覆盖面</b>：客户端所有实体（含本地玩家、远程玩家）都由
 * {@code ClientLevel#addEntity} 加入世界，而它会发 {@link EntityJoinLevelEvent}，
 * 故本表的插入点是全覆盖的。
 *
 * <p><b>为什么清理必须比对实例</b>：{@code ClientLevel#addEntity} 先发 join、
 * 再把同 id 的旧实体踢掉（于是紧接着发一次 leave）。若清理只按 UUID 无条件删除，
 * 这条「后到的 leave」会把<b>刚插入的新实体</b>删掉。用
 * {@link Map#remove(Object, Object)} 限定「映射仍指向这个实例时才删」，
 * id 复用就自动无害了 —— 这也是 {@code danmaku-target-state} 场景「目标死亡后 id 被复用」
 * 在索引层的实现方式。
 *
 * <p><b>为什么还要清空</b>：{@link EntityLeaveLevelEvent} 按实体逐个清，
 * 但退出世界时整个 {@code ClientLevel} 被丢弃，<b>不会</b>为每个实体发一次 leave
 * （同 {@code TouhouBossBarRenderer#onLoggingOut} 的注释）。UUID 跨存档不重复，
 * 所以残留条目指向的是已销毁的对象实例。
 */
@EventBusSubscriber(modid = Gensokyou.MODID, value = Dist.CLIENT)
public final class ClientEntityUuidIndex {

    private static final Map<UUID, Entity> BY_UUID = new HashMap<>();

    private ClientEntityUuidIndex() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        BY_UUID.put(entity.getUUID(), entity);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        BY_UUID.remove(entity.getUUID(), entity);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BY_UUID.clear();
    }

    /** 当前维度中该 UUID 对应的实体；查不到返回 {@code null}（按无目标处理）。 */
    @Nullable
    public static Entity get(UUID uuid) {
        return BY_UUID.get(uuid);
    }

    /** 调试与测试用。 */
    public static int size() {
        return BY_UUID.size();
    }
}