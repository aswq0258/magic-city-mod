package dev.fantasybosses.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.fantasybosses.field.FieldClips;
import dev.fantasybosses.field.FieldMonster;
import dev.fantasybosses.field.FieldMonsters;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Admin test commands (permission 2) for the overworld field monster models.
 * /fantasybosses field gallery [elite|normal|all] — line them up, AI off, persistent
 * /fantasybosses field anim <target> <clip>       — play a one-shot clip
 */
public final class FieldCommands {
    private FieldCommands() {}

    public static void register(RegisterCommandsEvent event) {
        var anim = Commands.argument("target", EntityArgument.entity());
        for (String clip : FieldClips.TRIGGERED) {
            anim.then(Commands.literal(clip).executes(ctx -> {
                Entity entity = EntityArgument.getEntity(ctx, "target");
                if (!(entity instanceof FieldMonster monster) || !monster.preview(clip)) {
                    ctx.getSource().sendFailure(Component.literal("Target must be a living field monster."));
                    return 0;
                }
                ctx.getSource().sendSuccess(() -> Component.literal(monster.fieldId() + ": " + clip), false);
                return 1;
            }));
        }
        event.getDispatcher().register(Commands.literal("fantasybosses")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("field")
                        .then(Commands.literal("gallery")
                                .executes(ctx -> gallery(ctx.getSource(), "elite"))
                                .then(Commands.argument("set", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"elite", "normal", "all"}, b))
                                        .executes(ctx -> gallery(ctx.getSource(), StringArgumentType.getString(ctx, "set")))))
                        .then(Commands.literal("anim").then(anim))));
    }

    private static int gallery(CommandSourceStack source, String set) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        int placed = 0;
        double x = 0;
        for (FieldMonsters.Spec spec : FieldMonsters.SPECS) {
            boolean want = switch (set) {
                case "all" -> true;
                case "normal" -> !spec.elite();
                default -> spec.elite();
            };
            if (!want) continue;
            x += Math.max(2.0, spec.width() + 1.5) / 2.0;
            FieldMonster monster = FieldMonsters.TYPES.get(spec.id()).get()
                    .spawn(level, net.minecraft.core.BlockPos.containing(origin.x + x, origin.y, origin.z + 4), MobSpawnType.COMMAND);
            x += Math.max(2.0, spec.width() + 1.5) / 2.0;
            if (monster == null) continue;
            monster.setNoAi(true);
            monster.setPersistenceRequired();
            monster.setYRot(180F);
            monster.setYBodyRot(180F);
            monster.setYHeadRot(180F);
            placed++;
        }
        int count = placed;
        source.sendSuccess(() -> Component.literal("Field gallery: " + count + " placed (AI off). F3+B shows hitboxes."), false);
        return count;
    }
}
