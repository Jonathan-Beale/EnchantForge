package com.example.enchantforge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StackBehaviorTest {

    @Test
    void sum() {
        assertEquals(4, StackBehavior.SUM.compute(List.of(1, 2, 1)));
    }

    @Test
    void highest() {
        assertEquals(3, StackBehavior.HIGHEST.compute(List.of(1, 3, 2)));
    }

    @Test
    void exclusive() {
        assertEquals(2, StackBehavior.EXCLUSIVE.compute(List.of(2, 1)));
    }

    @Test
    void sumSingle() {
        assertEquals(5, StackBehavior.SUM.compute(List.of(5)));
    }

    @Test
    void highestSingle() {
        assertEquals(5, StackBehavior.HIGHEST.compute(List.of(5)));
    }

    @Test
    void exclusiveSingle() {
        assertEquals(5, StackBehavior.EXCLUSIVE.compute(List.of(5)));
    }
}
