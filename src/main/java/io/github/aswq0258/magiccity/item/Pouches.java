package io.github.aswq0258.magiccity.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 꾸러미 지급 (2026-11-19 사용자 결정): 인벤토리에 넣고, 가득 차 있으면 그 방(필드보스 결투장·보스 전장)을 나간 뒤 발밑에 떨어뜨린다.
 * 기다리는 꾸러미는 서버가 켜져 있는 동안만 기억하며, 접속을 끊으면 그 자리에 바로 떨어뜨린다.
 */
public final class Pouches {
    private Pouches() {}

    /** 나갈 때까지 기다리는 꾸러미: 이 차원의 이 중심에서 반지름 밖으로 나가면(또는 다른 차원으로 가면) 떨어뜨림 */
    private record Pending(ItemStack stack, ResourceKey<Level> dim, Vec3 center, double radius) {}

    private static final Map<UUID, List<Pending>> PENDING = new HashMap<>();

    /** 꾸러미 주기. 가득 차 있으면 방을 나간 뒤 떨어뜨림 */
    public static void give(ServerPlayer p, ItemStack pouch, ResourceKey<Level> roomDim, Vec3 roomCenter, double roomRadius) {
        if (p.getInventory().add(pouch.copy())) {
            p.sendSystemMessage(Component.literal("§6[전리품] ").append(pouch.getHoverName()).append(Component.literal("§6을(를) 받았다.")));
            return;
        }
        PENDING.computeIfAbsent(p.getUUID(), u -> new ArrayList<>()).add(new Pending(pouch.copy(), roomDim, roomCenter, roomRadius));
        p.sendSystemMessage(Component.literal("§e[전리품] 인벤토리가 가득 차 있다. 꾸러미는 이곳을 나간 뒤 발밑에 떨어진다."));
    }

    /** 1초마다: 방을 나간 사람의 꾸러미를 인벤토리로(그래도 가득이면 발밑) */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty() || event.getServer().getTickCount() % 20 != 0) return;
        for (Iterator<Map.Entry<UUID, List<Pending>>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            e.getValue().removeIf(pd -> {
                boolean left = p.level().dimension() != pd.dim() || p.position().distanceToSqr(pd.center()) > pd.radius() * pd.radius();
                if (!left) return false;
                release(p, pd.stack());
                return true;
            });
            if (e.getValue().isEmpty()) it.remove();
        }
    }

    /** 접속을 끊으면 기다리던 꾸러미를 그 자리에 */
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        List<Pending> list = PENDING.remove(p.getUUID());
        if (list != null) for (Pending pd : list) p.drop(pd.stack(), false);
    }

    private static void release(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) {
            p.drop(s, false);
            p.sendSystemMessage(Component.literal("§e[전리품] 꾸러미를 발밑에 떨어뜨렸다."));
        } else {
            p.sendSystemMessage(Component.literal("§6[전리품] 꾸러미를 인벤토리에 넣었다."));
        }
    }
}
