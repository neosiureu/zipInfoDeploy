package com.zipinfo.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.ai.data.DataFiles;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.num.Q;
import com.zipinfo.ai.tools.DataSet;
import com.zipinfo.ai.tools.Evidence;
import com.zipinfo.ai.tools.RunState;
import com.zipinfo.ai.tools.ToolResult;
import com.zipinfo.ai.tools.Tools;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * 설계안 9.3 "같은 식을 두 곳에": 파이썬 정답(prep answers가 쓴 answers_check_v1.json)의 맞는 부르기를
 * Java 도구에 그대로 넣어 표본 수·비교 기준·기준값·환산·차이가 같은지 본다. 순서 2 build·answers 결과가 있을 때만 돈다.
 */
class AnswersCrossCheckTest {
    static final ObjectMapper M = new ObjectMapper();

    @Test
    void javaToolsMatchPythonAnswers() throws Exception {
        Path exp = Path.of("..").toAbsolutePath().normalize();
        Path out = exp.resolve("prep/out");
        Path answers = out.resolve("answers_check_v1.json");
        Path listingsCsv = exp.resolve("frozen/example_listings_v1.csv");
        assumeTrue(Files.exists(answers) && Files.exists(listingsCsv), "prep build·answers 결과와 얼린 매물 파일이 있을 때만 돈다");
        DataSet data = DataFiles.load(exp);
        Tools tools = new Tools(data);
        Map<String, Listing> listing = new HashMap<>();
        for (Listing l : Listing.load(out.resolve("listings_input_v1.json"))) {
            listing.put(l.key(), l);
        }
        Map<String, ListingMeta> meta = new HashMap<>();
        for (ListingMeta m : ListingMeta.load(listingsCsv)) {
            meta.put(m.key(), m);
        }
        List<String> bad = new ArrayList<>();
        Map<String, Integer> seen = new TreeMap<>();
        for (JsonNode c : M.readTree(Files.readString(answers))) {
            String key = c.get("key").asText();
            String tag = key + " " + c.get("qtype").asText() + " " + c.get("months").asInt() + "달";
            seen.merge(c.get("qtype").asText(), 1, Integer::sum);
            RunState st = new RunState(listing.get(key), meta.get(key));
            ToolResult r = tools.call("trade_stats", c.get("call"), st);
            if (r.isError()) {
                bad.add(tag + ": 오류 " + r.errorCode());
                continue;
            }
            JsonNode res = M.readTree(r.text());
            check(bad, tag + " 비교기준", c.get("scope").asText(), res.get("조건").get("비교기준").asText());
            check(bad, tag + " 표본수", Q.of(c.get("sample").asLong()), st.find("S1.표본수").value());
            boolean rent = meta.get(key).deal().equals("월세");
            if (rent) {
                check(bad, tag + " 보증금기준", q(c.get("deposit")), st.find("S1.보증금기준").value());
                check(bad, tag + " 월세기준", q(c.get("monthly")), st.find("S1.월세기준").value());
                check(bad, tag + " 환산기준", q(c.get("converted")), st.find("S1.환산기준").value());
            } else {
                check(bad, tag + " 기준", q(c.get("basis")), st.find("S1.기준").value());
            }
            if (c.get("sample").asLong() > 0) {
                String listingSide = "매물.가격";
                String basisSide = "S1.기준";
                if (rent) {
                    tools.call("convert_rent", M.createObjectNode().put("deposit_evidence", "매물.가격").put("monthly_evidence", "매물.월세"), st);
                    check(bad, tag + " 환산보증금", q(c.get("listing_value")), st.find("R1.환산보증금").value());
                    listingSide = "R1.환산보증금";
                    basisSide = "S1.환산기준";
                }
                ToolResult cr = tools.call("compare_prices", M.createObjectNode().put("listing_evidence", listingSide).put("basis_evidence", basisSide), st);
                if (cr.isError()) {
                    bad.add(tag + ": 비교 오류 " + cr.errorCode());
                } else {
                    check(bad, tag + " 차이", q(c.get("diff")), st.find("C1.차이").value());
                }
            }
            if (c.has("t5_count")) {
                // T5 건수는 비교 범위와 상관없이 단지·최근 1년(12달)이다(6판 9.3). 단지ID에서 법정동과 지번을 만든다.
                String[] id = meta.get(key).complexId().split("\\|");
                String jibun = (id[3].equals("1") ? "산" : "") + id[4] + (id[5].equals("0") ? "" : "-" + id[5]);
                JsonNode call = c.get("call").deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) call).put("start_month", "202511").put("end_month", "202610")
                        .put("dong", id[2]).put("jibun", jibun);
                tools.call("trade_stats", call, st);
                Evidence e = st.find("S2.표본수");
                check(bad, tag + " T5 건수", Q.of(c.get("t5_count").asLong()), e == null ? null : e.value());
                seen.merge("T5 건수", 1, Integer::sum);
            }
        }
        System.out.println("대조한 문항: " + seen + ", 어긋난 것 " + bad.size());
        bad.stream().limit(20).forEach(System.out::println);
        assertEquals(List.of(), bad);
    }

    static Q q(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        String s = n.asText();
        int slash = s.indexOf('/');
        return slash < 0 ? Q.of(Long.parseLong(s)) : Q.of(Long.parseLong(s.substring(0, slash)), Long.parseLong(s.substring(slash + 1)));
    }

    static void check(List<String> bad, String what, Object want, Object got) {
        if (want == null ? got != null : !want.equals(got)) {
            bad.add(what + ": 파이썬 " + want + ", Java " + got);
        }
    }
}
