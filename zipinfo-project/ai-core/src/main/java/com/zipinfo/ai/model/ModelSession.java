package com.zipinfo.ai.model;

import java.util.List;

/** 대화 하나. 덧붙이기만 한다. 실패한 요청은 같은 기록 그대로 resend()로 다시 보낸다(다시 보낸 요청은 턴으로 세지 않는다). */
public interface ModelSession {
    ModelTurn send(List<UserPart> parts) throws ModelError;

    ModelTurn resend() throws ModelError;
}
