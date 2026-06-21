/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.agent.executor;

import com.nageoffer.ai.ragent.agent.core.AgentStepRecorder;
import com.nageoffer.ai.ragent.agent.dao.entity.KnowledgeOpsStepDO;
import com.nageoffer.ai.ragent.agent.domain.AgentPlan;
import com.nageoffer.ai.ragent.agent.domain.AgentPlanStep;
import com.nageoffer.ai.ragent.agent.domain.AgentToolResult;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsContext;
import com.nageoffer.ai.ragent.agent.tool.AgentTool;
import com.nageoffer.ai.ragent.agent.tool.AgentToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KnowledgeOpsAgentExecutor {

    private final AgentToolRegistry toolRegistry;
    private final AgentStepRecorder stepRecorder;

    public void execute(AgentPlan plan, KnowledgeOpsContext context) {
        Map<String, AgentTool> toolMap = toolRegistry.toolMap();
        for (AgentPlanStep planStep : plan.getSteps()) {
            AgentTool tool = toolMap.get(planStep.getToolName());
            if (tool == null) {
                throw new IllegalStateException("Agent tool not found: " + planStep.getToolName());
            }
            executeTool(context.getRunId(), planStep, tool, context);
        }
    }

    private void executeTool(String runId, AgentPlanStep planStep, AgentTool tool, KnowledgeOpsContext context) {
        KnowledgeOpsStepDO step = stepRecorder.start(runId, planStep.getOrder(), tool.type(), tool.name(), input(tool, planStep, context));
        try {
            AgentToolResult result = tool.execute(context);
            context.putResult(tool.name(), result);
            stepRecorder.success(step, result);
        } catch (Exception ex) {
            stepRecorder.failure(step, ex);
            throw ex;
        }
    }

    private Map<String, Object> input(AgentTool tool, AgentPlanStep planStep, KnowledgeOpsContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("kbId", context.getKbId());
        input.put("task", context.getTask());
        input.put("topK", context.getTopK());
        input.put("scenario", context.getPlan() == null ? null : context.getPlan().getScenario());
        input.put("planReason", planStep.getReason());
        input.put("toolDescription", tool.description());
        input.put("toolInputSchema", tool.inputSchema());
        input.put("toolOutputSchema", tool.outputSchema());
        input.put("supportRetry", tool.supportRetry());
        input.put("timeoutSeconds", tool.timeoutSeconds());
        return input;
    }
}
