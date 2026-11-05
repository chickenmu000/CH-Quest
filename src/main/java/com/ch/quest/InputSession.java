package com.ch.quest;

import org.bukkit.inventory.ItemStack;

/** 모루 입력 세션 */
public class InputSession {
    public final String mode;   // create / pre / kill / itemamt
    public final String quest;  // 대상 퀘스트 ("" = 목록으로 돌아감)
    public ItemStack pending;   // itemamt : 목표 아이템

    public InputSession(String mode, String quest) {
        this.mode = mode;
        this.quest = quest;
    }
}
