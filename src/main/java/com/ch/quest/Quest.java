package com.ch.quest;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class Quest {
    public final String name;
    public Integer npc;
    public String prerequisite;
    public boolean repeat;
    public final List<Goal> goals = new ArrayList<>();
    public final List<ItemStack> rewards = new ArrayList<>();

    public Quest(String name) {
        this.name = name;
    }
}
