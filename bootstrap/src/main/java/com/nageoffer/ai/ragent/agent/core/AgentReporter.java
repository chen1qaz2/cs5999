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

package com.nageoffer.ai.ragent.agent.core;

import com.nageoffer.ai.ragent.agent.domain.AgentToolResult;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsContext;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsReport;
import com.nageoffer.ai.ragent.agent.tool.ChunkQualityInspectTool;
import com.nageoffer.ai.ragent.agent.tool.CoverageEvaluateTool;
import com.nageoffer.ai.ragent.agent.tool.KnowledgeBaseProfileTool;
import com.nageoffer.ai.ragent.agent.tool.KnowledgeRetrievalTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentReporter {

    public KnowledgeOpsReport buildReport(KnowledgeOpsContext context) {
        AgentToolResult profile = context.getResult(KnowledgeBaseProfileTool.TOOL_NAME);
        AgentToolResult retrieval = context.getResult(KnowledgeRetrievalTool.TOOL_NAME);
        AgentToolResult quality = context.getResult(ChunkQualityInspectTool.TOOL_NAME);
        AgentToolResult coverage = context.getResult(CoverageEvaluateTool.TOOL_NAME);

        String coverageLevel = stringValue(coverage, "coverageLevel", "UNKNOWN");
        int coverageScore = intValue(coverage, "coverageScore", 0);
        String kbName = stringValue(profile, "name", context.getKbId());
        int evidenceCount = intValue(coverage, "evidenceCount", 0);
        int totalChunks = intValue(quality, "totalChunks", 0);
        int disabledChunks = intValue(quality, "disabledChunks", 0);
        int tooShortChunks = intValue(quality, "tooShortChunks", 0);
        int tooLongChunks = intValue(quality, "tooLongChunks", 0);
        int duplicateChunks = intValue(quality, "duplicateHashChunks", 0);

        List<String> findings = new ArrayList<>();
        findings.add("Coverage score: " + coverageScore + " (" + coverageLevel + ").");
        findings.add("Retrieved evidence chunks: " + evidenceCount + ".");
        findings.add("Total chunks inspected: " + totalChunks + ".");
        if (disabledChunks > 0) {
            findings.add(disabledChunks + " chunks are disabled and will not participate in retrieval.");
        }
        if (tooShortChunks > 0) {
            findings.add(tooShortChunks + " chunks are shorter than 80 characters; they may lack context.");
        }
        if (tooLongChunks > 0) {
            findings.add(tooLongChunks + " chunks are longer than 1200 characters; they may reduce retrieval precision.");
        }
        if (duplicateChunks > 0) {
            findings.add(duplicateChunks + " duplicate chunk hashes were found.");
        }

        List<String> recommendations = new ArrayList<>();
        if (evidenceCount < 3) {
            recommendations.add("Add more documents related to the task topic or improve query-term mappings.");
        }
        if (tooShortChunks > 0 || tooLongChunks > 0) {
            recommendations.add("Re-run chunking with a more balanced chunk strategy and overlap setting.");
        }
        if (duplicateChunks > 0) {
            recommendations.add("Deduplicate repeated content before embedding to reduce noise.");
        }
        if (disabledChunks > 0) {
            recommendations.add("Review disabled chunks and enable useful chunks before production use.");
        }
        recommendations.add("Use the returned evidence chunks as a regression set for future RAG evaluation.");

        String summary = "KnowledgeOps Agent evaluated knowledge base \"" + kbName + "\" for task: " + context.getTask();
        String markdown = buildMarkdown(summary, coverageLevel, coverageScore, findings, recommendations, retrieval);

        return KnowledgeOpsReport.builder()
                .coverageLevel(coverageLevel)
                .coverageScore(coverageScore)
                .summary(summary)
                .findings(findings)
                .recommendations(recommendations)
                .markdown(markdown)
                .build();
    }

    private String buildMarkdown(String summary,
                                 String coverageLevel,
                                 int coverageScore,
                                 List<String> findings,
                                 List<String> recommendations,
                                 AgentToolResult retrieval) {
        StringBuilder builder = new StringBuilder();
        builder.append("# KnowledgeOps Agent Report\n\n");
        builder.append("## Summary\n").append(summary).append("\n\n");
        builder.append("## Coverage\n")
                .append("- Level: ").append(coverageLevel).append("\n")
                .append("- Score: ").append(coverageScore).append("\n\n");
        builder.append("## Findings\n");
        findings.forEach(item -> builder.append("- ").append(item).append("\n"));
        builder.append("\n## Recommendations\n");
        recommendations.forEach(item -> builder.append("- ").append(item).append("\n"));
        builder.append("\n## Evidence\n");
        appendEvidence(builder, retrieval);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendEvidence(StringBuilder builder, AgentToolResult retrieval) {
        if (retrieval == null || retrieval.getData() == null) {
            builder.append("- No evidence returned.\n");
            return;
        }
        Object evidenceObj = retrieval.getData().get("evidence");
        if (!(evidenceObj instanceof List<?> evidence) || evidence.isEmpty()) {
            builder.append("- No evidence returned.\n");
            return;
        }
        int index = 1;
        for (Object item : evidence) {
            if (item instanceof Map<?, ?> map) {
                Object text = map.get("text");
                builder.append(index++).append(". ").append(text == null ? "" : text).append("\n");
            }
        }
    }

    private String stringValue(AgentToolResult result, String key, String fallback) {
        if (result == null || result.getData() == null) {
            return fallback;
        }
        Object value = result.getData().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(AgentToolResult result, String key, int fallback) {
        if (result == null || result.getData() == null) {
            return fallback;
        }
        Object value = result.getData().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }
}
