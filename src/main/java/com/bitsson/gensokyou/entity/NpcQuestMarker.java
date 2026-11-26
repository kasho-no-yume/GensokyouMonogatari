package com.bitsson.gensokyou.entity;

/** NPC 头顶任务标记视图；由服务端向每名观察者单独同步，不存于实体。 */
public enum NpcQuestMarker {
    NONE("none", ""),
    EXCLAMATION("exclamation", "!"),
    QUESTION("question", "?");

    private final String id;
    private final String symbol;

    NpcQuestMarker(String id, String symbol) {
        this.id = id;
        this.symbol = symbol;
    }

    public String id() {
        return id;
    }

    public String symbol() {
        return symbol;
    }

    public static NpcQuestMarker fromId(String id) {
        return switch (id) {
            case "exclamation" -> EXCLAMATION;
            case "question" -> QUESTION;
            default -> NONE;
        };
    }
}
