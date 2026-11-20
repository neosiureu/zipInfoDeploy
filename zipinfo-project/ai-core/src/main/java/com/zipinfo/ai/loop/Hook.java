package com.zipinfo.ai.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.report.Finding;
import com.zipinfo.ai.report.Report;
import com.zipinfo.ai.tools.RunState;
import java.util.List;

/**
 * 장치 부품 하나(설계안 7.1). 판 설정이 고른 부품만 사슬에 든다. 순서 5에서 ⓪~⑤를 이 꼴로 만든다.
 * 자리: 시작(첫 데이터·계산 도구 전, 제출 전), 도구 전(trade_stats·convert_area), 제출(submit_report), 안내(첫 사용자 글).
 */
public interface Hook {
    enum Point { START, BEFORE_TOOL, SUBMIT, NOTICE }

    /** 통과, 거절과 처방 한 줄, 안내 한 줄. */
    record Decision(Kind kind, String line) {
        public enum Kind { PASS, REJECT, NOTICE }

        public static final Decision PASS = new Decision(Kind.PASS, "");

        public static Decision reject(String line) { return new Decision(Kind.REJECT, line); }

        public static Decision notice(String line) { return new Decision(Kind.NOTICE, line); }
    }

    /** 부품이 보는 것: 실행 상태, 도구와 입력(제출이면 보고서와 대조 결과). */
    record Context(RunState state, String tool, JsonNode input, Report report, List<Finding> findings) {}

    String name();

    boolean at(Point p);

    Decision decide(Point p, Context c);
}
