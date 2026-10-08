package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.client.ritual.ClientRitualData;
import com.bitsson.gensokyou.ritual.TsukumogamiFuelLoader;

import java.util.List;

/**
 * 付丧之冢 JEI 卡的数据载体（一等级一张，共 0/1/2 三张）。
 *
 * <p>燃料表由服务端数据快照下发（专用客户端上 loader 恒空），经 {@link ClientRitualData}
 * 重建；单件 L0 总量直接展示，等级 1/2 阶段的点位/时长由客户端据系数算出。
 */
public record TsukumogamiFuelCardWrapper(int level,
                                         List<TsukumogamiFuelLoader.Entry> entries) {

    public static TsukumogamiFuelCardWrapper of(int level) {
        return new TsukumogamiFuelCardWrapper(level,
                List.copyOf(ClientRitualData.tsukumogamiFuel()));
    }
}
