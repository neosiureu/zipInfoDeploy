package com.zipinfo.ai.model;

import com.zipinfo.ai.tools.ToolSpecs;
import java.util.List;

/** 모델 부르기 경계(설계안 4.4). 고리·장치·하네스는 이것만 본다. 공개 모델 구현은 필수 판 본실험 뒤로 미룬다. */
public interface ModelClient {
    ModelSession open(String system, List<ToolSpecs.Spec> tools);
}
