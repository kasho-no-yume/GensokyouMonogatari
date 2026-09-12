package com.bitsson.gensokyou.ritual.editor;

/**
 * 校验器侧的方块标签/品阶索引抽象（D4）：把 pattern 校验与注册表解耦，
 * 使 {@link RitualPatternValidator} 可在纯 JVM 单元测试中运行。
 * 服务端以注册表实现；测试以 data/&lt;ns&gt;/tags/block/*.json 文件实现。
 */
public interface BlockTagIndex {

    /** 标签成员方块 id 列表（如 {@code gensokyou:ritual_stone_2}）；未知标签返回空列表。 */
    java.util.List<String> members(String tagId);

    /** 方块品阶（0-5），非仪式石/祭品台满块返回 -1。 */
    int tierOf(String blockId);
}
