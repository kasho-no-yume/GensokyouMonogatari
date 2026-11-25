package com.bitsson.gensokyou.entity;

/** NPC 头顶任务标记；任务系统可在服务端切换，默认不显示。 */
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
