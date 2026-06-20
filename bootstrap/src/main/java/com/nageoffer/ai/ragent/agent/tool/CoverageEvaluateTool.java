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

import com.nageoffer.ai.ragent.agent.domain.AgentToolResult;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CoverageEvaluateTool implements AgentTool {

    public static final String TOOL_NAME = "coverage-evaluate";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "EVALUATE";
    }

    @Override
    @SuppressWarnings("unchecked")
    public AgentToolResult execute(KnowledgeOpsContext context) {
        AgentToolResult profile = context.getResult(KnowledgeBaseProfileTool.TOOL_NAME);
        AgentToolResult retrieval = context.getResult(KnowledgeRetrievalTool.TOOL_NAME);
        AgentToolResult quality = context.getResult(ChunkQualityInspectTool.TOOL_NAME);

        long enabledChunks = number(profile, "enabledChunkCount");
        int evidenceCount = retrieval == null ? 0 : ((List<Object>) retrieval.getData().getOrDefault("evidence", List.of())).size();
        long emptyChunks = number(quality, "emptyChunks");
        long tooShortChunks = number(quality, "tooShortChunks");
        long tooLongChunks = number(quality, "tooLongChunks");

        int score = 30;
        score += Math.min(35, evidenceCount * 7);
        score += Math.min(20, (int) (enabledChunks / 10));
        score -= Math.min(25, (int) (emptyChunks * 5 + tooShortChunks * 2 + tooLongChunks));
        score = Math.max(0, Math.min(100, score));

        String level = score >= 75 ? "GOOD" : score >= 50 ? "PARTIAL" : "WEAK";
        Map<String, Object> data = Map.of(
                "coverageScore", score,
                "coverageLevel", level,
                "evidenceCount", evidenceCount,
                "enabledChunks", enabledChunks
        );

        return AgentToolResult.builder()
                .summary("Coverage level is " + level + " with score " + score + ".")
                .data(data)
                .build();
    }

    private long number(AgentToolResult result, String key) {
        if (result == null || result.getData() == null) {
            return 0;
        }
        Object value = result.getData().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0;
    }
}
