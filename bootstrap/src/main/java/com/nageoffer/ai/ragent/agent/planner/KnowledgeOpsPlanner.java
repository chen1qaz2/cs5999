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

package com.nageoffer.ai.ragent.agent.planner;

import com.nageoffer.ai.ragent.agent.config.KnowledgeOpsAgentProperties;
import com.nageoffer.ai.ragent.agent.domain.AgentPlan;
import com.nageoffer.ai.ragent.agent.domain.AgentPlanStep;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class KnowledgeOpsPlanner {

    private static final String SCENARIO_RETRIEVAL = "retrieval";
    private static final String SCENARIO_RELEASE = "release";
    private static final String SCENARIO_SECURITY = "security";
    private static final String SCENARIO_QUALITY = "quality";
    private static final String SCENARIO_BENCHMARK = "benchmark";

    private final KnowledgeOpsAgentProperties properties;

    public AgentPlan plan(KnowledgeOpsContext context) {
        String scenario = resolveScenario(context);
        List<String> workflow = resolveWorkflow(context, scenario);
        int maxSteps = Math.max(1, properties.getMaxSteps());
        List<AgentPlanStep> steps = new ArrayList<>();
        for (int i = 0; i < workflow.size() && i < maxSteps; i++) {
            String toolName = workflow.get(i);
            steps.add(AgentPlanStep.builder()
                    .order(i + 1)
                    .toolName(toolName)
                    .reason(reason(toolName, scenario))
                    .build());
        }
        return AgentPlan.builder()
                .scenario(scenario)
                .reason("Planner selected " + scenario + " workflow for task: " + context.getTask())
                .steps(steps)
                .build();
    }

    private String resolveScenario(KnowledgeOpsContext context) {
        if (context.getScenario() != null && !context.getScenario().isBlank()) {
            return context.getScenario().trim().toLowerCase(Locale.ROOT);
        }
        if (!properties.isPlannerEnabled()) {
            return SCENARIO_QUALITY;
        }
        String task = context.getTask() == null ? "" : context.getTask().toLowerCase(Locale.ROOT);
        if (containsAny(task, "安全", "敏感", "泄露", "secret", "token", "password", "privacy")) {
            return SCENARIO_SECURITY;
        }
        if (containsAny(task, "上线", "发布", "验收", "production", "release", "go-live")) {
            return SCENARIO_RELEASE;
        }
        if (containsAny(task, "benchmark", "评测", "基准", "问题集", "测试集", "知识缺口", "缺口")) {
            return SCENARIO_BENCHMARK;
        }
        if (containsAny(task, "检索", "召回", "命中", "问答", "答案", "retrieval", "search", "benchmark")) {
            return SCENARIO_RETRIEVAL;
        }
        return SCENARIO_QUALITY;
    }

    private List<String> resolveWorkflow(KnowledgeOpsContext context, String scenario) {
        if (context.getRequestedWorkflow() != null && !context.getRequestedWorkflow().isEmpty()) {
            return context.getRequestedWorkflow();
        }
        return properties.workflow(scenario);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String reason(String toolName, String scenario) {
        return "Run " + toolName + " as part of " + scenario + " knowledge operations workflow.";
    }
}
