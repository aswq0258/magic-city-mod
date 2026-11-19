package io.github.aswq0258.magiccity.arena;

import io.github.aswq0258.magiccity.MagicCityMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * FTB Teams 파티 (2026-11-19 인스턴스 전장). magiccity 가 FTB Teams 를 필수로 요구하지 않도록 리플렉션으로 부른다.
 * API: {@code FTBTeamsAPI.api().getManager().getTeamForPlayer(ServerPlayer)} → {@code Optional<Team>},
 * {@code Team.isPartyTeam()}, {@code Team.getOnlineMembers()}. FTB Teams 가 없거나 호출이 실패하면 혼자로 본다.
 */
public final class Party {
    private Party() {}

    private static boolean failedOnce;

    /** 그 플레이어를 포함한 접속 중인 파티원 (파티가 없으면 본인만, 본인이 맨 앞) */
    public static List<ServerPlayer> onlineMembers(ServerPlayer p) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(p);
        if (!ModList.get().isLoaded("ftbteams")) return out;
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Object api = apiClass.getMethod("api").invoke(null);
            if (!(Boolean) findMethod(api.getClass(), "isManagerLoaded").invoke(api)) return out;
            Object manager = findMethod(api.getClass(), "getManager").invoke(api);
            Method forPlayer = findMethod(manager.getClass(), "getTeamForPlayer", ServerPlayer.class);
            Optional<?> team = (Optional<?>) forPlayer.invoke(manager, p);
            if (team.isEmpty()) return out;
            Object t = team.get();
            if (!(Boolean) findMethod(t.getClass(), "isPartyTeam").invoke(t)) return out;
            Collection<?> online = (Collection<?>) findMethod(t.getClass(), "getOnlineMembers").invoke(t);
            for (Object o : online) if (o instanceof ServerPlayer sp && !sp.getUUID().equals(p.getUUID())) out.add(sp);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!failedOnce) {
                failedOnce = true;
                MagicCityMod.LOGGER.warn("[전장] FTB Teams 파티를 읽지 못해 혼자로 봅니다: {}", e.toString());
            }
        }
        return out;
    }

    /** 구현 클래스가 공개가 아닐 수 있어, 위로 올라가며 공개 인터페이스에 선언된 메서드를 먼저 찾음 */
    private static Method findMethod(Class<?> c, String name, Class<?>... params) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Class<?> i : allInterfaces(k)) {
                try {
                    return i.getMethod(name, params);
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        Method m = c.getMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private static List<Class<?>> allInterfaces(Class<?> c) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> i : c.getInterfaces()) {
            out.add(i);
            out.addAll(allInterfaces(i));
        }
        return out;
    }
}
