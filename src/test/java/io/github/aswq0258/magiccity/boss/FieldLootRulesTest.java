package io.github.aswq0258.magiccity.boss;

import com.google.gson.JsonParser;
import io.github.aswq0258.magiccity.boss.FieldLootRules.Loot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 필드보스 꾸러미 규칙·배포 데이터 (Minecraft 없이 실행).
 * 기준: 프로젝트 문서 claude/인스턴스전장_구현초안.md v3 9절, 8절 D(다이아·철은 반복에도 소량).
 */
class FieldLootRulesTest {
    private static Loot load(String key) throws Exception {
        Path p = Path.of("src/main/resources/data/magiccity/field_boss_loot/" + key + ".json");
        return FieldLootRules.parse(JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    @Test
    @DisplayName("첫 처치 기록: 길마다 한 비트, 기록 뒤에는 반복 보상")
    void firstKill() {
        int m = 0;
        assertTrue(FieldLootRules.firstKill(m, 3));
        m = FieldLootRules.markKill(m, 3);
        assertFalse(FieldLootRules.firstKill(m, 3));
        assertTrue(FieldLootRules.firstKill(m, 4));
        assertEquals("frost_bear", FieldLootRules.key(0));
        assertEquals("rift_watcher", FieldLootRules.key(9));
        assertEquals(null, FieldLootRules.key(10));
    }

    @Test
    @DisplayName("배포 데이터 10개: 첫 처치 다이아 3·철 10·에메랄드 12, 반복 다이아 1·철 3·에메랄드 3, 반복은 첫 처치보다 적음")
    void shipped() throws Exception {
        for (String k : FieldLootRules.KEYS) {
            Loot l = load(k);
            assertFalse(l.name().isBlank(), k);
            assertEquals(3, FieldLootRules.count(l.first(), "minecraft:diamond"), k);
            assertEquals(10, FieldLootRules.count(l.first(), "minecraft:iron_ingot"), k);
            assertEquals(12, FieldLootRules.count(l.first(), "minecraft:emerald"), k);
            assertEquals(1, FieldLootRules.count(l.repeat(), "minecraft:diamond"), k);
            assertEquals(3, FieldLootRules.count(l.repeat(), "minecraft:iron_ingot"), k);
            assertEquals(3, FieldLootRules.count(l.repeat(), "minecraft:emerald"), k);
            int firstTotal = l.first().stream().mapToInt(FieldLootRules.Entry::count).sum();
            int repeatTotal = l.repeat().stream().mapToInt(FieldLootRules.Entry::count).sum();
            assertTrue(repeatTotal < firstTotal, k);
        }
        assertEquals(1, FieldLootRules.count(load("raider_evoker").first(), "minecraft:totem_of_undying"));
        assertEquals(0, FieldLootRules.count(load("raider_evoker").repeat(), "minecraft:totem_of_undying"));
    }

    @Test
    @DisplayName("잘못된 데이터 거부: 비었음, 네임스페이스 없음, 수량 0·65")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> FieldLootRules.parse(JsonParser.parseString("{\"first\":[],\"repeat\":[]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> FieldLootRules.parse(JsonParser.parseString(
                "{\"first\":[{\"item\":\"diamond\"}],\"repeat\":[{\"item\":\"minecraft:dirt\"}]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> FieldLootRules.parse(JsonParser.parseString(
                "{\"first\":[{\"item\":\"minecraft:dirt\",\"count\":0}],\"repeat\":[{\"item\":\"minecraft:dirt\"}]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> FieldLootRules.parse(JsonParser.parseString(
                "{\"first\":[{\"item\":\"minecraft:dirt\",\"count\":65}],\"repeat\":[{\"item\":\"minecraft:dirt\"}]}").getAsJsonObject()));
    }
}
