package com.bitsson.gensokyou.client.resident;

import com.bitsson.gensokyou.Gensokyou;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Asset reload only: never installed as a server data-pack listener. */
public final class ResidentResources extends SimpleJsonResourceReloadListener {
    public ResidentResources() { super(new Gson(), "village_residents"); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        List<ResidentScene> scenes = new ArrayList<>();
        int count = 0;
        for (var entry : entries.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString())).toList()) {
            try {
                ResidentScene scene = ResidentScene.parse(entry.getKey().toString(), entry.getValue().getAsJsonObject());
                if (count + scene.residents().size() > ResidentScene.MAX_RESIDENTS) {
                    throw new IllegalArgumentException("total resident limit is " + ResidentScene.MAX_RESIDENTS);
                }
                scenes.add(scene);
                count += scene.residents().size();
            } catch (RuntimeException error) {
                Gensokyou.LOGGER.error("Invalid client resident scene {}: {}", entry.getKey(), error.getMessage());
            }
        }
        ClientResidents.install(List.copyOf(scenes));
        ResidentRenderer.invalidateModel();
    }
}
