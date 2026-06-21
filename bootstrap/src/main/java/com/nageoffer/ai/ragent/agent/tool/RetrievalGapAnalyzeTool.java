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

package com.nageoffer.ai.ragent.agent.tool;

import com.nageoffer.ai.ragent.agent.config.KnowledgeOpsAgentProperties;
import com.nageoffer.ai.ragent.agent.domain.AgentToolResult;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RetrievalGapAnalyzeTool implements AgentTool {

    public static final String TOOL_NAME = "retrieval-gap-analyze";

    private final KnowledgeOpsAgentProperties properties;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "GAP_ANALYSIS";
    }

    @Override
    public String description() {
        return "Analyzes weak benchmark questions and turns low-recall cases into actionable knowledge-gap suggestions.";
    }

    @Override
    public AgentToolResult execute(KnowledgeOpsContext context) {
        AgentToolResult benchmark = context.getResult(QuestionSetBenchmarkTool.TOOL_NAME);
        AgentToolResult retrieval = context.getResult(KnowledgeRetrievalTool.TOOL_NAME);
        List<Map<String, Object>> weakQuestions = weakQuestions(benchmark);
        int retrievalEvidenceCount = retrievalEvidenceCount(retrieval);
        List<String> suggestions = suggestions(weakQuestions, retrievalEvidenceCount);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weakQuestionCount", weakQuestions.size());
        data.put("retrievalEvidenceCount", retrievalEvidenceCount);
        data.put("gapLevel", gapLevel(weakQuestions.size(), retrievalEvidenceCount));
        data.put("weakQuestions", weakQuestions);
        data.put("suggestions", suggestions);

        return AgentToolResult.builder()
                .summary("Gap analysis found " + weakQuestions.size() + " weak benchmark questions.")
                .data(data)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> weakQuestions(AgentToolResult benchmark) {
        int limit = Math.max(1, properties.getGapSampleLimit());
        List<Map<String, Object>> weakQuestions = new ArrayList<>();
        if (benchmark == null || benchmark.getData() == null) {
            return weakQuestions;
        }
        Object itemsObj = benchmark.getData().get("items");
        if (!(itemsObj instanceof List<?> items)) {
            return weakQuestions;
        }
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }
            boolean hit = Boolean.TRUE.equals(item.get("hit"));
            Number topScore = item.get("topScore") instanceof Number number ? number : 0;
            if (hit && topScore.floatValue() >= properties.getBenchmarkMinScore()) {
                continue;
            }
            Map<String, Object> weak = new LinkedHashMap<>();
            weak.put("question", stringValue(item.get("question")));
            weak.put("topScore", topScore);
            Object evidenceCount = item.get("evidenceCount");
            weak.put("evidenceCount", evidenceCount == null ? 0 : evidenceCount);
            weak.put("topExcerpt", stringValue(item.get("topExcerpt")));
            weak.put("reason", item.get("error") == null
                    ? "Top evidence score is below benchmark threshold."
                    : "Retrieval failed: " + item.get("error"));
            weakQuestions.add(weak);
            if (weakQuestions.size() >= limit) {
                break;
            }
        }
        return weakQuestions;
    }

    @SuppressWarnings("unchecked")
    private int retrievalEvidenceCount(AgentToolResult retrieval) {
        if (retrieval == null || retrieval.getData() == null) {
            return 0;
        }
        Object evidence = retrieval.getData().get("evidence");
        if (evidence instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private List<String> suggestions(List<Map<String, Object>> weakQuestions, int retrievalEvidenceCount) {
        List<String> suggestions = new ArrayList<>();
        if (weakQuestions.isEmpty() && retrievalEvidenceCount >= 3) {
            suggestions.add("Benchmark did not expose obvious retrieval gaps. Keep current question set as regression coverage.");
            return suggestions;
        }
        if (retrievalEvidenceCount < 3) {
            suggestions.add("Add or re-index documents related to the task topic; current task-level retrieval returned too few evidence chunks.");
        }
        for (Map<String, Object> weak : weakQuestions) {
            String question = stringValue(weak.get("question"));
            if (!question.isBlank()) {
                suggestions.add("Create or improve source material for weak question: " + question);
            }
        }
        suggestions.add("After document updates, rerun the benchmark workflow and compare hitRate and weakQuestionCount.");
        return suggestions;
    }

    private String gapLevel(int weakQuestionCount, int retrievalEvidenceCount) {
        if (weakQuestionCount >= 3 || retrievalEvidenceCount == 0) {
            return "HIGH";
        }
        if (weakQuestionCount > 0 || retrievalEvidenceCount < 3) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
