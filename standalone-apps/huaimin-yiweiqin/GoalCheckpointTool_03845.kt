package com.cleo.cleos.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray

/** Model reports intentions and evidence; ONLY GoalStore may mutate authoritative state. */
object GoalCheckpointTool {
    val spec = ToolSpec(
        name="goal_checkpoint",
        groups=emptySet(),
        action="保存目标检查点",
        description="目标模式专用：汇报真实计划、当前步骤、验收和阻塞信息。不能用自然语言宣布目标已完成来结束调度。先提供plan字符串数组作为可编辑计划；执行后用step_index、step_state=ACTIVE/DONE/BLOCKED及result说明进展；验收用criterion_index、criterion_state=PASSED/FAILED/UNVERIFIED和来自真实工具调用的evidence_id。PASSED若没有本目标本版本的已验证工具收据，程序会强制记作未验证。needs_input=true会等待用户。blocking_reason非空会受阻。除非程序验证全部必需验收通过，目标不能标记完成。",
        parameters=buildJsonObject {
            put("type","object")
            putJsonObject("properties") {
                putJsonObject("plan") {
                    put("type","array")
                    putJsonObject("items"){put("type","string")}
                }
                putJsonObject("replace_plan"){put("type","boolean")}
                putJsonObject("step_index"){put("type","integer")}
                putJsonObject("step_state"){put("type","string");putJsonArray("enum"){addString("PENDING");addString("ACTIVE");addString("DONE");addString("BLOCKED")}}
                putJsonObject("criterion_index"){put("type","integer")}
                putJsonObject("criterion_state"){put("type","string");putJsonArray("enum"){addString("PASSED");addString("FAILED");addString("UNVERIFIED")}}
                putJsonObject("result"){put("type","string")}
                putJsonObject("evidence_id"){put("type","string")}
                putJsonObject("needs_input"){put("type","boolean")}
                putJsonObject("blocking_reason"){put("type","string")}
                putJsonObject("verification_phase"){put("type","boolean")}
            }
        },
    )
    private fun kotlinx.serialization.json.JsonArrayBuilder.addString(v:String) { add(kotlinx.serialization.json.JsonPrimitive(v)) }
}
