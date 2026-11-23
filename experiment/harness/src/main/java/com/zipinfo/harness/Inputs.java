package com.zipinfo.harness;

import com.zipinfo.ai.cards.Cards;
import com.zipinfo.ai.data.DataFiles;
import com.zipinfo.ai.data.Hashes;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.reflex.IntentLists;
import com.zipinfo.ai.reflex.Memory;
import com.zipinfo.ai.reflex.Variant;
import com.zipinfo.ai.reflex.Variants;
import com.zipinfo.ai.tools.DataSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 하네스가 읽는 것 모두: 도구 자료, 매물(아홉 칸·근거와 넘기지 않는 정보), 판, 문항, 카드, ⓪의 목록, ⑤의 기억, 지문에 넣을 해시.
 * load는 experiment 폴더에서 읽는다. 시험은 작은 자료로 직접 짠다.
 * memory: 설정의 files.memory가 null이면 Memory.EMPTY(채우기 전: ⑤가 든 판은 안내가 없고 카드는 ID 순), 경로면 그 얼린 파일에서 읽은 것이다(순서 6b).
 */
public record Inputs(DataSet data, Map<String, Listing> listings, Map<String, ListingMeta> metas, List<Variant> variants, List<Question> questions,
                     Cards cards, IntentLists lists, Memory memory, Digests digests) {

    /**
     * code: ai-core main 소스, harnessCode: 하네스 main 소스(지문 밖, 기록에만), cards·lists,
     * memory: 얼린 기억 파일(줄 끝 LF로 맞춤)이나 채우기 전이면 EMPTY,
     * deps: 하네스 잠금 파일의 runtimeClasspath 판들(SDK 포함), data: 도구 자료 파일 이름 → SHA-256.
     */
    public record Digests(String code, String harnessCode, String cards, String lists, String memory, String deps, Map<String, String> data) {}

    static final List<String> PREP = List.of("replay_trades_v1.csv", "replay_bundles_v1.csv", "validity_v1.csv", "listings_input_v1.json");

    public static Inputs load(Path experiment, RunSettings s) throws IOException {
        Path juso = experiment.resolve("frozen/juso_road_v1.csv");
        if (!Files.exists(juso)) {
            throw new IOException("주소 얼린 파일(frozen/juso_road_v1.csv)이 없다. 없으면 주소 찾기가 모두 SNAPSHOT_MISS가 된다");
        }
        DataSet data = DataFiles.load(experiment);
        Map<String, Listing> listings = new LinkedHashMap<>();
        for (Listing l : Listing.load(experiment.resolve("prep/out/listings_input_v1.json"))) {
            listings.put(l.key(), l);
        }
        Map<String, ListingMeta> metas = new LinkedHashMap<>();
        for (ListingMeta m : ListingMeta.load(experiment.resolve("frozen/example_listings_v1.csv"))) {
            metas.put(m.key(), m);
        }
        if (!listings.keySet().equals(metas.keySet())) {
            throw new IOException("매물 입력과 얼린 매물 파일의 키가 다르다");
        }
        Memory memory = s.files().memory() == null ? Memory.EMPTY : MemoryFile.read(experiment.resolve(s.files().memory())).memory();
        return new Inputs(data, listings, metas, Variants.load(experiment.resolve(s.files().variants())),
                Question.load(experiment.resolve(s.files().questions())), Cards.load(experiment.resolve(s.files().cards())),
                IntentLists.load(experiment.resolve(s.files().lists())), memory, digests(experiment, s));
    }

    /** 지문에 넣을 해시. 도구 자료 넷은 prep/expected_v1.txt의 값(DataFiles.load가 대 본다), 이력 표는 못박은 값, 주소·매물 파일은 바이트 그대로. */
    public static Digests digests(Path experiment, RunSettings s) throws IOException {
        Map<String, String> expected = DataFiles.expected(experiment.resolve("prep/expected_v1.txt"));
        Map<String, String> data = new TreeMap<>();
        for (String name : PREP) {
            String h = expected.get(name);
            if (h == null) {
                throw new IOException("expected_v1.txt에 " + name + "이 없다");
            }
            data.put(name, h.toUpperCase());
        }
        data.put("codego_history_v1.csv", DataFiles.HISTORY_SHA256);
        data.put("juso_road_v1.csv", Hashes.sha256(experiment.resolve("frozen/juso_road_v1.csv")));
        data.put("example_listings_v1.csv", Hashes.sha256(experiment.resolve("frozen/example_listings_v1.csv")));
        Path repo = experiment.toAbsolutePath().normalize().getParent();
        String memory = s.files().memory() == null ? "EMPTY" : Fingerprint.file(experiment.resolve(s.files().memory()));
        return new Digests(Fingerprint.sources(repo.resolve("zipinfo-project/ai-core/src/main/java")),
                Fingerprint.sources(experiment.resolve("harness/src/main/java")), Fingerprint.cards(experiment.resolve(s.files().cards())),
                Fingerprint.file(experiment.resolve(s.files().lists())), memory, Fingerprint.deps(experiment.resolve("harness/gradle.lockfile")), data);
    }
}
