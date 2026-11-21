package dev.fantasybosses.field;

import java.util.List;

/** One-shot clips every field model has (manifest v1.2). Loops: idle, walk, run (+ float/skitter/fly). */
public final class FieldClips {
    private FieldClips() {}
    public static final List<String> TRIGGERED = List.of("alert", "attack_1", "attack_2", "skill", "hurt", "death");
}
