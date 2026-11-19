package io.github.aswq0258.magiccity.arena;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** 플레이어용 {@code /arena vote accept|decline} (권한 0, 투표 버튼이 부름). 서버가 투표 대상인지 다시 확인한다 */
public final class ArenaCommand {
    private ArenaCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("arena")
                .then(Commands.literal("vote")
                        .then(Commands.literal("accept").executes(c -> vote(c, true)))
                        .then(Commands.literal("decline").executes(c -> vote(c, false)))));
    }

    private static int vote(CommandContext<CommandSourceStack> c, boolean accept) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String err = ArenaManager.vote(c.getSource().getPlayerOrException(), accept);
        if (err != null) {
            c.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }
}
