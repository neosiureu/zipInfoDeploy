package com.zipinfo.ai.data;

import com.zipinfo.ai.tools.DataSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * experiment 폴더에서 도구 자료를 읽는다. 순서 2 결과는 prep/expected_v1.txt의 SHA-256과, 이력 표는 못박은 값과 대 보고 다르면 멈춘다.
 * 주소 얼린 파일(frozen/juso_road_v1.csv)은 순서 2-4 뒤에 생긴다. 없으면 빈 주소 표를 쓴다(모든 검색이 SNAPSHOT_MISS).
 */
public final class DataFiles {
    public static final String HISTORY_SHA256 = "9DD686B01C0E93D6BE366A43653B0BA527739C7CA2EEAA0D380F5A56A35A9BFF";

    private DataFiles() {}

    public static Map<String, String> expected(Path expectedTxt) throws IOException {
        Map<String, String> out = new HashMap<>();
        for (String line : Files.readAllLines(expectedTxt, StandardCharsets.UTF_8)) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            String[] f = t.split("\\s+");
            out.put(f[1], f[0]);
        }
        return out;
    }

    public static DataSet load(Path experiment) throws IOException {
        Path out = experiment.resolve("prep/out");
        Map<String, String> want = expected(experiment.resolve("prep/expected_v1.txt"));
        for (String name : List.of("replay_trades_v1.csv", "replay_bundles_v1.csv", "validity_v1.csv", "listings_input_v1.json")) {
            String h = want.get(name);
            if (h == null) {
                throw new IOException("expected_v1.txt에 " + name + "이 없다");
            }
            Hashes.require(out.resolve(name), h);
        }
        Path history = experiment.resolve("frozen/codego/codego_history_v1.csv");
        Hashes.require(history, HISTORY_SHA256);
        Path juso = experiment.resolve("frozen/juso_road_v1.csv");
        return new DataSet(TradeStore.load(out.resolve("replay_trades_v1.csv"), out.resolve("replay_bundles_v1.csv")),
                ValidityTable.load(out.resolve("validity_v1.csv")), History.load(history),
                Files.exists(juso) ? AddressBook.load(juso) : AddressBook.empty());
    }
}
