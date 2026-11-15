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
 * 흰색(석영·방해석)을 메인으로, 민트색(밀랍칠한 산화 구리·하늘색 유리)을 포인트로 쓰는
 * 반지름 300칸 원형 성채 도시를 코드로 자동 생성합니다.
 *
 * 사용법: 도시 중심이 될 땅 위에 서서  /magiccity build  [반지름 200~400]
 */
@Mod(MagicCityMod.MODID)
public class MagicCityMod {
    public static final String MODID = "magiccity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MagicCityMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(MagicCityMod::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(CityBuildManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(CityBuildManager::onServerStopping);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        MagicCityCommand.register(event.getDispatcher());
    }
}
