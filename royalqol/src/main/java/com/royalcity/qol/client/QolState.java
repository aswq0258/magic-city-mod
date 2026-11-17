package com.royalcity.qol.client;

import com.mojang.logging.LogUtils;
import com.royalcity.qol.nav.DefaultRegions;
import com.royalcity.qol.nav.WaypointBook;
import com.royalcity.qol.nav.WaypointJson;
import com.royalcity.qol.nav.Waypoint;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** 클라이언트 쪽 상태: 웨이포인트 목록, 목적지, HUD 표시 여부, 파일 저장 */
public final class QolState {
    private static final Logger LOG = LogUtils.getLogger();

    public static final WaypointBook BOOK = new WaypointBook(DefaultRegions.all());
    public static boolean hud = true;
    public static Waypoint destination;
    /** 지난 틱에 있던 지역 이름 (진입 알림용) */
    public static String lastRegion;
    /** 가까운 지역 키(Y) 순환 위치 */
    public static int cycle = -1;

    private QolState() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("royalqol").resolve("waypoints.json");
    }

    /** 불러오기. 결과 설명(문제가 있으면 그 내용)을 돌려준다 */
    public static String load() {
        Path f = file();
        if (!Files.exists(f)) {
            BOOK.replaceUser(List.of());
            save();
            return "새 파일을 만들었습니다: " + f;
        }
        try {
            WaypointJson.Data d = WaypointJson.read(Files.readString(f, StandardCharsets.UTF_8));
            hud = d.hud();
            int skipped = BOOK.replaceUser(d.waypoints());
            StringBuilder sb = new StringBuilder("사용자 웨이포인트 " + BOOK.user().size() + "개를 읽었습니다");
            if (skipped > 0) sb.append(", 이름이 겹쳐 건너뜀 ").append(skipped).append("개");
            for (String p : d.problems()) sb.append("\n  ").append(p);
            if (destination != null && BOOK.exact(destination.name()).isEmpty()) destination = null;
            return sb.toString();
        } catch (IOException | RuntimeException e) {
            LOG.warn("[royalqol] {} 읽기 실패", f, e);
            return "파일을 읽지 못했습니다 (" + e.getMessage() + "). 내장 지역만 사용합니다. 파일: " + f;
        }
    }

    public static void save() {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, WaypointJson.write(hud, BOOK.user()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("[royalqol] {} 저장 실패", f, e);
        }
    }

    public static Optional<Waypoint> destination() {
        return Optional.ofNullable(destination);
    }
}
