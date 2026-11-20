package com.zipinfo.ai.tools;

import com.zipinfo.ai.data.AddressBook;
import com.zipinfo.ai.data.History;
import com.zipinfo.ai.data.Listing;
import com.zipinfo.ai.data.ListingMeta;
import com.zipinfo.ai.data.TradeStore;
import com.zipinfo.ai.data.ValidityTable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 시험용 작은 자료: 41591(새 코드)·41590(옛 코드), 아파트 매매와 전월세 몇 건, 주소 하나, 매물 둘. 값은 지어낸 것이다. */
public final class Fixture {
    private Fixture() {}

    static void write(Path p, String... lines) throws IOException {
        Files.writeString(p, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    public static DataSet data(Path dir) throws IOException {
        write(dir.resolve("bundles.csv"), String.join(",", TradeStore.BUNDLE_HEADER),
                "apt_trade,41591,202609,0,0,0,0", "apt_trade,41591,202610,4,4,0,0", "apt_trade,41590,202610,0,0,0,0",
                "apt_rent,41591,202610,3,3,0,0", "apt_rent,41591,202609,0,0,0,0", "rh_trade,41591,202610,1,1,0,0");
        String h = String.join(",", TradeStore.TRADE_HEADER);
        write(dir.resolve("trades.csv"), h,
                "apt_trade,41591,202610,1,0,3,매매,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,84.97,5,2001,30000,,,가동",
                "apt_trade,41591,202610,1,1,9,매매,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,59.5,3,2001,24000,,,가동",
                "apt_trade,41591,202610,1,2,11,매매,apt|41591|나동|1|7|3,4159110200,나동,산7-3,나단지,100,10,2010,52000,,,나동",
                "apt_trade,41591,202610,1,3,20,매매,apt|41591|가동|0|20|0,4159110100,가동,20,다단지,84,7,1999,33600,,,가동",
                "apt_rent,41591,202610,1,0,5,월세,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,84.97,5,2001,,10000,50,가동",
                "apt_rent,41591,202610,1,1,6,전세,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,59.5,2,2001,,30000,0,가동",
                "apt_rent,41591,202610,1,2,7,월세,apt|41591|가동|0|12|0,4159110100,가동,12,가단지,84,8,2001,,5000,100,가동",
                "rh_trade,41591,202610,1,0,4,매매,rh|41591|봉담읍 와우리|0|3|0,4159125021,봉담읍 와우리,3,라빌라,50,2,2015,20000,,,봉담읍 와우리");
        write(dir.resolve("history.csv"), String.join(",", History.HEADER),
                "4159000000,경기도 화성시,,,현존,4100000000,41_590",
                "4159010100,경기도 화성시 가동,,20260131,폐지,4159000000,41_590",
                "4159100000,경기도 화성시 만세구,20260201,,현존,4100000000,41_591",
                "4159110100,경기도 화성시 만세구 가동,20260201,,현존,4159100000,41_591",
                "4159110200,경기도 화성시 만세구 나동,20260201,,현존,4159100000,41_591",
                "4159125000,경기도 화성시 만세구 봉담읍,20260201,,현존,4159100000,41_591",
                "4159125021,경기도 화성시 만세구 봉담읍 와우리,20260201,,현존,4159125000,41_591");
        write(dir.resolve("validity.csv"), String.join(",", ValidityTable.HEADER),
                "41591,apt_trade,202609,41590,0,1,,true_zero", "41591,apt_trade,202609,41591,0,1,,true_zero",
                "41591,apt_trade,202610,41590,0,0,41591,one", "41591,apt_trade,202610,41591,4,1,,one");
        write(dir.resolve("juso.csv"), String.join(",", AddressBook.HEADER),
                "경기도 화성시 만세구 가로 1,1,1,T,1,4159110100,18000,경기도 화성시 만세구 가로 1,경기도,화성시 만세구,가동,,0,12,0,4159110100100120000000001");
        return new DataSet(TradeStore.load(dir.resolve("trades.csv"), dir.resolve("bundles.csv")),
                ValidityTable.load(dir.resolve("validity.csv")), History.load(dir.resolve("history.csv")),
                AddressBook.load(dir.resolve("juso.csv")));
    }

    public static RunState run(String key, String stockType, long price, long fee, String area) {
        Map<String, String> f = new LinkedHashMap<>();
        for (String n : Listing.NINE) {
            f.put(n, "");
        }
        f.put("stockType", stockType);
        f.put("stockSellPrice", Long.toString(price));
        f.put("stockFeeMonth", Long.toString(fee));
        f.put("exclusiveArea", area);
        f.put("currentFloor", "5");
        f.put("stockAddress", "18000^^^경기도 화성시 만세구 가로 1^^^");
        Map<String, Long> ev = new LinkedHashMap<>();
        ev.put("매물.가격", price);
        ev.put("매물.월세", fee);
        ev.put("매물.면적", Long.parseLong(area));
        ev.put("매물.층", 5L);
        String deal = switch (stockType) {
            case "0" -> "매매";
            case "1" -> "전세";
            default -> "월세";
        };
        return new RunState(new Listing(key, f, ev), new ListingMeta(key, "apt|41591|가동|0|12|0", deal, "화성형", "본시험"));
    }

    public static List<String> lines(String... s) {
        return List.of(s);
    }
}
