package com.ch.quest;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class PlayerData {
    public String current;
    public final Map<Integer, Integer> progress = new HashMap<>(); // 목표 번호(0부터) -> 처치 수
    public final Set<String> done = new LinkedHashSet<>();
}
