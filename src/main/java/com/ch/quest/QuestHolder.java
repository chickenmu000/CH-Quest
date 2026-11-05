package com.ch.quest;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** 퀘스트 GUI 식별용 홀더 */
public class QuestHolder implements InventoryHolder {
    public final String mode;      // offer / progress / adminlist / adminedit / reward
    public final String quest;
    public int page = 1;
    public boolean fromGui;        // reward : 편집 화면에서 열렸는지
    public final Map<Integer, String> slotQuest = new HashMap<>();
    public boolean canAccept;      // offer : 선행 퀘스트 충족 여부
    Inventory inv;

    public QuestHolder(String mode, String quest) {
        this.mode = mode;
        this.quest = quest;
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }
}
