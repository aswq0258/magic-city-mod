package dev.fantasybosses.field;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Test-stage field monster: model + hitbox + basic melee AI so the v1.2 models can be checked in game.
 * Ranged/flying/skill behaviour is not implemented yet (all 30 walk and hit in melee).
 */
public class FieldMonster extends Monster implements GeoEntity {
    private static final String CONTROLLER = "main";
    /** death clips are ~1.15 s; vanilla removes at 20 ticks, so keep the body a little longer. */
    private static final int DEATH_TICKS = 24;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private FieldMonsters.Spec spec;
    private boolean nextSwing;

    public FieldMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public String fieldId() { return BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath(); }

    public FieldMonsters.Spec spec() {
        if (spec == null) spec = FieldMonsters.spec(fieldId());
        return spec;
    }

    public boolean isElite() { return spec().elite(); }

    /** [실험] test values. Real balance belongs to the grade tables later. */
    public static AttributeSupplier.Builder attributes(boolean elite) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, elite ? 60.0 : 20.0)
                .add(Attributes.ATTACK_DAMAGE, elite ? 8.0 : 4.0)
                .add(Attributes.ARMOR, elite ? 4.0 : 0.0)
                .add(Attributes.MOVEMENT_SPEED, elite ? 0.27 : 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, elite ? 0.5 : 0.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim(CONTROLLER, nextSwing ? "attack_2" : "attack_1");
            nextSwing = !nextSwing;
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && isAlive() && !level().isClientSide) triggerAnim(CONTROLLER, "hurt");
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) triggerAnim(CONTROLLER, "death");
    }

    @Override
    protected void tickDeath() {
        ++deathTime;
        if (deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
            level().broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    /** Admin preview of any clip (used by /fantasybosses field anim). */
    public boolean preview(String clip) {
        if (!isAlive() || !FieldClips.TRIGGERED.contains(clip)) return false;
        triggerAnim(CONTROLLER, clip);
        return true;
    }

    private String anim(String clip) { return "animation." + fieldId() + "." + clip; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        FieldMonsters.Spec s = spec();
        RawAnimation idle = RawAnimation.begin().thenLoop(anim(s.idleClip()));
        RawAnimation walk = RawAnimation.begin().thenLoop(anim("walk"));
        RawAnimation run = RawAnimation.begin().thenLoop(anim(s.runClip()));
        AnimationController<FieldMonster> controller = new AnimationController<>(this, CONTROLLER, 3, state -> {
            if (isDeadOrDying()) return PlayState.CONTINUE;
            if (state.isMoving()) return state.setAndContinue(isAggressive() ? run : walk);
            return state.setAndContinue(idle);
        });
        for (String clip : FieldClips.TRIGGERED) {
            controller.triggerableAnim(clip, RawAnimation.begin().then(anim(clip), Animation.LoopType.PLAY_ONCE));
        }
        controllers.add(controller);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
