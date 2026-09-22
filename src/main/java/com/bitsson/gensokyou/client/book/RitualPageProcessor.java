package com.bitsson.gensokyou.client.book;

import net.minecraft.world.level.Level;
import vazkii.patchouli.api.IComponentProcessor;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.api.IVariableProvider;

/**
 * 仪式页通用 processor：把页面/模板里声明的变量（ritual / tier / recipe_index …）
 * 原样透传给自定义组件。仅客户端加载。
 */
public class RitualPageProcessor implements IComponentProcessor {

    private IVariableProvider provider;

    @Override
    public void setup(Level level, IVariableProvider vars) {
        this.provider = vars;
    }

    @Override
    public IVariable process(Level level, String key) {
        if (provider != null && provider.has(key)) {
            return provider.get(key, level.registryAccess());
        }
        return null;
    }
}
