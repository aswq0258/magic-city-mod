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
        NeoForge.EVENT_BUS.addListener(MagicCityMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CityBuildManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(CityBuildManager::onServerStopping);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        MagicCityCommand.register(event.getDispatcher());
    }
}
