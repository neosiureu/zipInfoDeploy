package com.zipinfo.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.zipinfo.ai.model.ModelSettings;
import java.util.Set;

/**
 * 답변 판정의 설정(설정 파일의 judge 칸, 순서 6c 설계 2.4). 판정 모델은 비교하는 판이 아니라서 판 설정(models)과 따로 둔다(01 184·210줄).
 * <ul>
 * <li>model: 단가표에 있는 모델이어야 한다(판정 비용을 셈한다).</li>
 * <li>max_tokens·max_tokens_raised: max_tokens로 끝난 판정은 raised로 다시 낸다(01 225줄). raised는 max_tokens보다 크다.</li>
 * <li>thinking: adaptive·disabled·omit. Opus는 disabled를 받지 않는다(01 181줄).</li>
 * <li>effort: null(보내지 않음)이나 low·medium·high·xhigh·max. thinking이 disabled이면 null·low·medium·high만 받는다(01 181줄).</li>
 * <li>tool_choice: auto나 tool(submit_verdict 강제). tool은 thinking이 disabled일 때만 받는다(강제하면 thinking을 건너뛴다, 01 185줄).
 *     Opus는 auto뿐이다(01 179·186줄).</li>
 * <li>instructions: 지시문 파일(experiment 폴더에서 본 경로). poll_seconds: 배치 상태를 보는 간격(10~3600초).</li>
 * </ul>
 * 배치는 틀린 params를 배치가 다 끝난 뒤에야 알려 주므로, 설정을 읽을 때 미리 막는다.
 */
public record JudgeSettings(String model, long maxTokens, long maxTokensRaised, ModelSettings.Thinking thinking, String effort, String toolChoice,
                            String instructions, int pollSeconds) {
    public static final String AUTO = "auto";
    public static final String TOOL = "tool";
    private static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");
    private static final Set<String> DISABLED_EFFORTS = Set.of("low", "medium", "high");

    static JudgeSettings parse(JsonNode j, Prices prices) {
        String where = "judge";
        Json.fields(j, where, "model", "max_tokens", "max_tokens_raised", "thinking", "effort", "tool_choice", "instructions", "poll_seconds");
        String model = Json.text(j, "model", where);
        prices.tier(model, 0);
        long maxTokens = Json.integer(j, "max_tokens", where, 1, 1_000_000);
        long raised = Json.integer(j, "max_tokens_raised", where, 1, 1_000_000);
        if (raised <= maxTokens) {
            throw new IllegalArgumentException("judge.max_tokens_raised는 max_tokens보다 크다");
        }
        ModelSettings.Thinking thinking = switch (Json.text(j, "thinking", where)) {
            case "adaptive" -> ModelSettings.Thinking.ADAPTIVE;
            case "disabled" -> ModelSettings.Thinking.DISABLED;
            case "omit" -> ModelSettings.Thinking.OMIT;
            default -> throw new IllegalArgumentException("judge.thinking은 adaptive·disabled·omit 가운데 하나다");
        };
        String effort = Json.textOrNull(j, "effort", where);
        if (effort != null && !EFFORTS.contains(effort)) {
            throw new IllegalArgumentException("judge.effort는 null이나 low·medium·high·xhigh·max다");
        }
        if (thinking == ModelSettings.Thinking.DISABLED && effort != null && !DISABLED_EFFORTS.contains(effort)) {
            throw new IllegalArgumentException("judge.thinking이 disabled이면 effort는 null·low·medium·high다(01 181줄)");
        }
        String toolChoice = Json.text(j, "tool_choice", where);
        if (!toolChoice.equals(AUTO) && !toolChoice.equals(TOOL)) {
            throw new IllegalArgumentException("judge.tool_choice는 auto나 tool이다");
        }
        if (model.startsWith("claude-opus")) {
            if (thinking == ModelSettings.Thinking.DISABLED) {
                throw new IllegalArgumentException("Opus 판정은 thinking disabled를 받지 않는다(01 181줄, 400)");
            }
            if (!toolChoice.equals(AUTO)) {
                throw new IllegalArgumentException("Opus 판정은 tool_choice auto뿐이다(01 179·186줄, 400)");
            }
        }
        if (toolChoice.equals(TOOL) && thinking != ModelSettings.Thinking.DISABLED) {
            throw new IllegalArgumentException("judge.tool_choice가 tool(강제)이면 thinking은 disabled다. 강제하면 thinking을 건너뛴다(01 185줄)");
        }
        return new JudgeSettings(model, maxTokens, raised, thinking, effort, toolChoice, Json.text(j, "instructions", where),
                Json.integer(j, "poll_seconds", where, 10, 3600));
    }
}
