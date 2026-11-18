package io.github.aswq0258.magiccity.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 퀘스트 NPC (자체 대화, 초안 3절 A안). 바닐라 주민 모습, 움직이지 않음(NoAI), 무적, 사라지지 않음.
 * 표식은 엔티티 태그 {@value #TAG} + {@code magiccity_npc=<id>}. 오른쪽 클릭하면 거래 대신 {@link QuestService#talk} 대화.
 */
public final class QuestNpcs {
    private QuestNpcs() {}

    public static final String TAG = "magiccity_npc";
    private static final String ID_PREFIX = "magiccity_npc=";

    /** 서장 NPC: 왕실 시종장 (왕좌의 전당) */
    public static final String STEWARD = "steward";

    /** 퀘스트 NPC 면 그 id, 아니면 null (서버에서만 정확: 태그는 클라이언트로 보내지 않음) */
    public static String npcId(Entity e) {
        if (!e.getTags().contains(TAG)) return null;
        for (String t : e.getTags()) if (t.startsWith(ID_PREFIX)) return t.substring(ID_PREFIX.length());
        return null;
    }

    /**
     * NPC 소환. 같은 차원에 이미 불러온 같은 id NPC 는 지우고 새로 놓는다 (도시를 다시 지어도 한 명).
     * 자리를 저장해 안내 좌표로 쓴다.
     */
    public static Villager spawn(ServerLevel level, String npcId, double x, double y, double z, float yaw) {
        int removed = remove(level, npcId);
        level.getChunk(BlockPos.containing(x, y, z));
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return null;
        v.moveTo(x, y, z, yaw, 0F);
        v.setYHeadRot(yaw);
        v.setYBodyRot(yaw);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setPersistenceRequired();
        v.setCustomName(Component.literal(QuestManager.npc(npcId).name()));
        v.setCustomNameVisible(true);
        v.addTag(TAG);
        v.addTag(ID_PREFIX + npcId);
        level.addFreshEntity(v);
        QuestData d = QuestData.get(level.getServer());
        d.npcs.put(npcId, BlockPos.containing(x, y, z));
        d.setDirty();
        if (removed > 0) io.github.aswq0258.magiccity.MagicCityMod.LOGGER.info("[퀘스트] NPC {} 다시 소환 (이전 {}명 제거)", npcId, removed);
        return v;
    }

    /** 이 차원에서 불러온 그 id NPC 를 모두 지움 */
    public static int remove(ServerLevel level, String npcId) {
        List<Entity> found = new ArrayList<>();
        for (Entity e : level.getAllEntities()) if (npcId.equals(npcId(e))) found.add(e);
        found.forEach(Entity::discard);
        return found.size();
    }

    /** 마지막으로 소환한 자리 (모르면 null) */
    public static BlockPos position(net.minecraft.server.MinecraftServer server, String npcId) {
        return QuestData.get(server).npcs.get(npcId);
    }

    /** 오른쪽 클릭: 거래 창 대신 대화 */
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        String id = npcId(event.getTarget());
        if (id == null) return;
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (event.getEntity() instanceof ServerPlayer sp) QuestService.talk(sp, event.getTarget(), id);
    }
}
