package com.example.enchantforge;

import java.util.List;

public enum StackBehavior {
    SUM {
        @Override public int compute(List<Integer> levels) {
            return levels.stream().mapToInt(Integer::intValue).sum();
        }
    },
    HIGHEST {
        @Override public int compute(List<Integer> levels) {
            return levels.stream().mapToInt(Integer::intValue).max().orElse(0);
        }
    },
    EXCLUSIVE {
        @Override public int compute(List<Integer> levels) {
            // Only the first source applies; enforcement at apply-command level
            return levels.isEmpty() ? 0 : levels.get(0);
        }
    };

    public abstract int compute(List<Integer> levels);

    public static StackBehavior fromString(String s) {
        return valueOf(s.toUpperCase());
    }
}
