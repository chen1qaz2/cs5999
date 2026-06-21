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

package com.nageoffer.ai.ragent.agent.config;

import com.nageoffer.ai.ragent.agent.tool.ChunkQualityInspectTool;
import com.nageoffer.ai.ragent.agent.tool.CoverageEvaluateTool;
import com.nageoffer.ai.ragent.agent.tool.DocumentFreshnessCheckTool;
import com.nageoffer.ai.ragent.agent.tool.KnowledgeBaseProfileTool;
import com.nageoffer.ai.ragent.agent.tool.KnowledgeRetrievalTool;
import com.nageoffer.ai.ragent.agent.tool.QuestionSetBenchmarkTool;
import com.nageoffer.ai.ragent.agent.tool.RetrievalGapAnalyzeTool;
import com.nageoffer.ai.ragent.agent.tool.SensitiveInfoDetectTool;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "agent.knowledge-ops")
public class KnowledgeOpsAgentProperties {

    private boolean plannerEnabled = true;

    private int maxSteps = 8;

    private int staleDocumentDays = 90;

    private int sensitiveScanLimit = 500;

    private int benchmarkQuestionLimit = 8;

    private float benchmarkMinScore = 0.45F;

    private int gapSampleLimit = 5;

    private List<String> defaultWorkflow = new ArrayList<>(List.of(
            KnowledgeBaseProfileTool.TOOL_NAME,
            DocumentFreshnessCheckTool.TOOL_NAME,
            KnowledgeRetrievalTool.TOOL_NAME,
            QuestionSetBenchmarkTool.TOOL_NAME,
            RetrievalGapAnalyzeTool.TOOL_NAME,
            ChunkQualityInspectTool.TOOL_NAME,
            CoverageEvaluateTool.TOOL_NAME
    ));

    private Map<String, List<String>> workflows = new LinkedHashMap<>();

    public KnowledgeOpsAgentProperties() {
        workflows.put("quality", defaultWorkflow);
        workflows.put("retrieval", List.of(
                KnowledgeBaseProfileTool.TOOL_NAME,
                KnowledgeRetrievalTool.TOOL_NAME,
                QuestionSetBenchmarkTool.TOOL_NAME,
                RetrievalGapAnalyzeTool.TOOL_NAME,
                CoverageEvaluateTool.TOOL_NAME
        ));
        workflows.put("security", List.of(
                KnowledgeBaseProfileTool.TOOL_NAME,
                SensitiveInfoDetectTool.TOOL_NAME,
                DocumentFreshnessCheckTool.TOOL_NAME,
                ChunkQualityInspectTool.TOOL_NAME,
                CoverageEvaluateTool.TOOL_NAME
        ));
        workflows.put("release", List.of(
                KnowledgeBaseProfileTool.TOOL_NAME,
                DocumentFreshnessCheckTool.TOOL_NAME,
                SensitiveInfoDetectTool.TOOL_NAME,
                KnowledgeRetrievalTool.TOOL_NAME,
                QuestionSetBenchmarkTool.TOOL_NAME,
                RetrievalGapAnalyzeTool.TOOL_NAME,
                ChunkQualityInspectTool.TOOL_NAME,
                CoverageEvaluateTool.TOOL_NAME
        ));
        workflows.put("benchmark", List.of(
                KnowledgeBaseProfileTool.TOOL_NAME,
                QuestionSetBenchmarkTool.TOOL_NAME,
                RetrievalGapAnalyzeTool.TOOL_NAME,
                CoverageEvaluateTool.TOOL_NAME
        ));
    }

    public List<String> workflow(String name) {
        List<String> workflow = workflows.get(name);
        if (workflow == null || workflow.isEmpty()) {
            return defaultWorkflow;
        }
        return workflow;
    }
}
