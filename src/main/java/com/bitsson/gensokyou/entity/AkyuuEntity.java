package com.bitsson.gensokyou.entity;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.dialogue.DialogueGraph;
import com.bitsson.gensokyou.dialogue.DialogueNode;
import com.bitsson.gensokyou.dialogue.DialogueOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistent, stationary test commissioner; the common dialogue adapter adds private quest operations. */
public final class AkyuuEntity extends TouhouNpcEntity {
    private int residenceId = -1;
    private static final DialogueGraph DIALOGUE = new DialogueGraph(Gensokyou.id("akyuu"), Map.of("greet",
            new DialogueNode(Component.translatable("gui.gensokyou.quest.akyuu_greeting"), List.of(
                    new DialogueOption(Component.translatable("gui.gensokyou.quest.farewell"), Optional.empty(), Optional.empty())))), "greet");
    public AkyuuEntity(EntityType<? extends AkyuuEntity> type, Level level) { super(type, level); }
    public int residenceId() { return residenceId; }
    public void setResidenceId(int id) { residenceId = id; }
    @Override public DialogueGraph dialogueGraph() { return DIALOGUE; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("ResidenceId", residenceId);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); residenceId = tag.contains("ResidenceId") ? tag.getInt("ResidenceId") : -1;
    }
}
