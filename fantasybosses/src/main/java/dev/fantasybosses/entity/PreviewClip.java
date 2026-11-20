package dev.fantasybosses.entity;

/** Visual-only clips. The route clips do not move the server hitbox. */
public enum PreviewClip {
    IDLE("idle"), BITE("bite"), DRAIN("drain"), TAIL_SWEEP("tail_sweep"), DEATH("death"),
    GROUND_CRAWL("ground_crawl"), WALL_CEILING_ROUTE("wall_ceiling_route"),
    CEILING_DIVE("ceiling_dive");

    private final String key;
    PreviewClip(String key) { this.key = key; }
    public String key() { return key; }
    public String animationId() { return "animation.radicion." + key; }
}
