package com.ch.quest;

import org.bukkit.inventory.ItemStack;

public class Goal {
    public enum Type { ITEM, KILL }

    public Type type;
    public ItemStack item;   // ITEM : 목표 아이템 (수량 1)
    public String mob;       // KILL : 몹 이름 ("아무" = 아무 몬스터)
    public int amount;
    public boolean recall;   // ITEM : 클리어 시 아이템 회수
}
