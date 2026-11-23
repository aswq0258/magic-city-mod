package io.github.aswq0258.magiccity;

import com.mojang.logging.LogUtils;
import io.github.aswq0258.magiccity.build.CityBuildManager;
import io.github.aswq0258.magiccity.command.MagicCityCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * 마법 도시 (Magic City)
 *
 * 방사형 이중 성곽 왕성도시를 코드로 자동 생성하고 (/magiccity build),
 * 그 왕국이 자리 잡을 대륙 차원(magiccity:kingdom)을 제공합니다 (/magiccity kingdom).
 */
@Mod(MagicCityMod.MODID)
public class MagicCityMod {
    public static final String MODID = "magiccity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MagicCityMod(IEventBus modBus) {
        io.github.aswq0258.magiccity.world.ModWorldgen.register(modBus);
        io.github.aswq0258.magiccity.item.ModItems.register(modBus);
        // 챕터 보스 인스턴스 전장·보스 꾸러미 (2026-11-19)
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, io.github.aswq0258.magiccity.arena.ArenaManager::onDeath);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onLogin);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onLogout);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onChangeDimension);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onBreak);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onPlace);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onExplode);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onUseItem);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onJoin);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.world.NoCreepers::onJoin);   // 크리퍼 없음 (2026-11-20)
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onPearl);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onChorus);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.ArenaManager::onGrief);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.SealAltar::onUse);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.item.Pouches::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.item.Pouches::onLogout);
        NeoForge.EVENT_BUS.addListener(MagicCityMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CityBuildManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.monument.MonumentRunner::onServerTick);      // 웅장화 (2026-11-23)
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.monument.MonumentRunner::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CityBuildManager::onServerStopping);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.GateKeeper::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.GateKeeper::onTravel);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.FieldBosses::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.FieldBosses::onDeath);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.RoadEncounters::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.world.SafeZones::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.ManagedBoss::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.ManagedBoss::onDeath);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.Progression::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.BossPatterns::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.FieldLoot::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.BossMaterials::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.BossData::onAddReloadListeners);        // 보스 수치·외부 보스 연결 (2026-11-20)
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.arena.BossRooms::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.boss.BossPatterns::onServerTick);
        // 개인 퀘스트·퀘스트 NPC (2026-11-19)
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.quest.QuestManager::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.quest.QuestNpcs::onInteract);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.quest.QuestService::onUseBlock);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.quest.QuestService::onServerTick);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.quest.QuestService::onLogin);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.quest.QuestService::onChangeDimension);
        NeoForge.EVENT_BUS.addListener(io.github.aswq0258.magiccity.quest.QuestService::onChapterClear);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartingEvent e) -> io.github.aswq0258.magiccity.boss.Progression.load());
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        MagicCityCommand.register(event.getDispatcher());
        io.github.aswq0258.magiccity.quest.QuestCommand.register(event.getDispatcher());
        io.github.aswq0258.magiccity.arena.ArenaCommand.register(event.getDispatcher());
        io.github.aswq0258.magiccity.monument.MonumentCommand.register(event.getDispatcher());
    }
}
