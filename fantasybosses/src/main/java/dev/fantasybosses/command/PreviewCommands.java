package dev.fantasybosses.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.fantasybosses.entity.PreviewClip;
import dev.fantasybosses.combat.AttackPattern;
import dev.fantasybosses.entity.RadicionEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Admin-only preview. Does not deal damage or alter the world. */
public final class PreviewCommands {
    private PreviewCommands() {}

    public static void register(RegisterCommandsEvent event) {
        var target = Commands.argument("target", EntityArgument.entity());
        for (PreviewClip clip : PreviewClip.values()) {
            target.then(Commands.literal(clip.key()).executes(ctx -> {
                Entity entity = EntityArgument.getEntity(ctx, "target");
                if (!(entity instanceof RadicionEntity boss) || !boss.isAlive()) {
                    ctx.getSource().sendFailure(Component.literal("Target must be a living Radicion."));
                    return 0;
                }
                if (boss.inRoute()) {
                    ctx.getSource().sendFailure(Component.literal("Visual-only previews require a Radicion outside the Worldtree arena."));
                    return 0;
                }
                boss.preview(clip);
                ctx.getSource().sendSuccess(() -> Component.literal("Preview: " + clip.key()
                        + " (visual only; no movement or damage)"), false);
                return 1;
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("fantasybosses")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("preview").then(target));
        var routeTarget = Commands.argument("target", EntityArgument.entity());
        for (String action : new String[]{"start","air","dive","pause","resume","groggy","status"}) {
            routeTarget.then(Commands.literal(action).executes(ctx -> {
                Entity entity=EntityArgument.getEntity(ctx,"target");
                if (!(entity instanceof RadicionEntity boss) || !boss.isAlive()) {
                    ctx.getSource().sendFailure(Component.literal("Target must be a living Radicion."));
                    return 0;
                }
                boolean ok=switch(action) {
                    case "start" -> boss.startRoute();
                    case "air" -> boss.requestAir(false);
                    case "dive" -> boss.requestAir(true);
                    case "pause" -> boss.pauseRoute(true);
                    case "resume" -> boss.pauseRoute(false);
                    case "groggy" -> boss.beginGroggy();
                    case "status" -> true;
                    default -> false;
                };
                if (!ok) {
                    ctx.getSource().sendFailure(Component.literal("Route request denied. Start requires loaded Worldtree arena; air/dive/groggy require unpaused ground patrol without a pending request or active recovery. "+boss.routeStatus()));
                    return 0;
                }
                ctx.getSource().sendSuccess(() -> Component.literal("Radicion "+action+": "+boss.routeStatus()),false);
                return 1;
            }));
        }
        root.then(Commands.literal("route").then(routeTarget));
        var combatTarget = Commands.argument("target", EntityArgument.entity());
        for (String action : new String[]{"bite","drain","tail","dive","brood","descent","auto_on","auto_off","status","phase2","phase3","roots","fissure"}) {
            combatTarget.then(Commands.literal(action).executes(ctx -> {
                Entity entity=EntityArgument.getEntity(ctx,"target");
                if (!(entity instanceof RadicionEntity boss) || !boss.isAlive()) {
                    ctx.getSource().sendFailure(Component.literal("Target must be a living Radicion."));return 0;
                }
                boolean ok=switch(action){
                    case "auto_on" -> {boss.combat().setAuto(true);yield true;}
                    case "auto_off" -> {boss.combat().setAuto(false);yield true;}
                    case "status" -> true;
                    case "roots" -> boss.arena().clearRoots();
                    case "fissure" -> boss.arena().startWave();
                    case "phase2","phase3" -> {
                        if(!boss.inRoute()||boss.combat().busy()||boss.arena().waveActive())yield false;
                        boss.arena().clearRoots();
                        boss.setHealth(Math.min(boss.getHealth(),boss.getMaxHealth()*(action.equals("phase2")?.65F:.32F)));boss.combat().updatePhase();yield true;
                    }
                    default -> boss.combat().start(AttackPattern.byKey(action));
                };
                if(!ok){ctx.getSource().sendFailure(Component.literal("Attack denied: needs living ground patrol, no pending request/recovery, and required phase. "+boss.combat().status()+" "+boss.routeStatus()));return 0;}
                ctx.getSource().sendSuccess(()->Component.literal("Radicion combat "+action+": "+boss.combat().status()),false);return 1;
            }));
        }
        root.then(Commands.literal("combat").then(combatTarget));
        event.getDispatcher().register(root);
    }
}
