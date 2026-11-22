package com.zipinfo.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 대화 하나. 덧붙이기만 한다. 실패한 요청은 같은 기록 그대로 resend()로 다시 보낸다(다시 보낸 요청은 턴으로 세지 않는다). */
public interface ModelSession {
    ModelTurn send(List<UserPart> parts) throws ModelError;

    ModelTurn resend() throws ModelError;

    /**
     * 다른 프로세스에서 이어 돌릴 때(하네스, 01 198~200줄): parts를 사용자 글로 덧붙이고, 기록에 남은 응답 원문(raw, ModelTurn.raw와 같은 꼴)을
     * API에 묻지 않고 받은 것처럼 덧붙인다. 그래서 대화 기록(thinking 블록 포함)이 처음 실행과 같아지고, 다음 send가 멈춘 요청과 같은 요청이 된다.
     * 돌려주는 것은 그 응답으로 만든 ModelTurn이다. millis는 기록에 남은 값을 그대로 쓴다.
     */
    ModelTurn replay(List<UserPart> parts, JsonNode raw, long millis);
}
