package com.ch.quest;

/** NPC 이름 조회 (Citizens 가 없으면 번호) */
final class NpcName {
    private NpcName() {
    }

    static String get(int id) {
        return NpcHook.name(id);
    }
}
