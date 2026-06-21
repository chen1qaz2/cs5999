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
import com.nageoffer.ai.ragent.agent.tool.DocumentFreshnessCheckTool;
import com.nageoffer.ai.ragent.agent.tool.KnowledgeBaseProfileTool;
import com.nageoffer.ai.ragent.agent.tool.KnowledgeRetrievalTool;
import com.nageoffer.ai.ragent.agent.tool.QuestionSetBenchmarkTool;
import com.nageoffer.ai.ragent.agent.tool.RetrievalGapAnalyzeTool;
import com.nageoffer.ai.ragent.agent.tool.SensitiveInfoDetectTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        AgentToolResult freshness = context.getResult(DocumentFreshnessCheckTool.TOOL_NAME);
        AgentToolResult sensitive = context.getResult(SensitiveInfoDetectTool.TOOL_NAME);
        AgentToolResult benchmark = context.getResult(QuestionSetBenchmarkTool.TOOL_NAME);
        AgentToolResult gap = context.getResult(RetrievalGapAnalyzeTool.TOOL_NAME);

        String coverageLevel = stringValue(coverage, "coverageLevel", "UNKNOWN");
        int coverageScore = intValue(coverage, "coverageScore", 0);
        String kbName = stringValue(profile, "name", context.getKbId());
        int evidenceCount = intValue(coverage, "evidenceCount", 0);
        int totalChunks = intValue(quality, "totalChunks", 0);
        int disabledChunks = intValue(quality, "disabledChunks", 0);
        int tooShortChunks = intValue(quality, "tooShortChunks", 0);
        int tooLongChunks = intValue(quality, "tooLongChunks", 0);
        int duplicateChunks = intValue(quality, "duplicateHashChunks", 0);
        int staleDocuments = intValue(freshness, "staleDocuments", 0);
        int failedDocuments = intValue(freshness, "failedDocuments", 0);
        String riskLevel = stringValue(sensitive, "riskLevel", "UNKNOWN");
        int sensitiveHits = intValue(sensitive, "totalHits", 0);
        int benchmarkQuestionCount = intValue(benchmark, "questionCount", 0);
        int benchmarkHitCount = intValue(benchmark, "hitCount", 0);
        String benchmarkHitRate = stringValue(benchmark, "hitRate", "0");
        int weakQuestionCount = intValue(gap, "weakQuestionCount", 0);
        String gapLevel = stringValue(gap, "gapLevel", "UNKNOWN");

        List<String> findings = new ArrayList<>();
        findings.add("Planner scenario: " + context.getScenario() + ".");
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
        if (freshness != null) {
            findings.add("Document freshness check found " + staleDocuments + " stale documents and " + failedDocuments + " failed documents.");
        }
        if (sensitive != null) {
            findings.add("Sensitive information risk level: " + riskLevel + ", hits: " + sensitiveHits + ".");
        }
        if (benchmark != null) {
            findings.add("Benchmark hit rate: " + benchmarkHitRate + " (" + benchmarkHitCount + "/" + benchmarkQuestionCount + ").");
        }
        if (gap != null) {
            findings.add("Retrieval gap level: " + gapLevel + ", weak questions: " + weakQuestionCount + ".");
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
        if (staleDocuments > 0 || failedDocuments > 0) {
            recommendations.add("Refresh stale or failed documents before using this knowledge base for critical tasks.");
        }
        if (sensitiveHits > 0) {
            recommendations.add("Review sensitive scan samples and mask or remove risky content before exposing the knowledge base.");
        }
        if (weakQuestionCount > 0) {
            recommendations.add("Prioritize weak benchmark questions as knowledge-gap backlog items.");
        }
        appendGapSuggestions(recommendations, gap);
        recommendations.add("Use the returned evidence chunks as a regression set for future RAG evaluation.");

        String summary = "KnowledgeOps Agent evaluated knowledge base \"" + kbName + "\" for task: " + context.getTask();
        Map<String, Object> metrics = metrics(context, coverageScore, evidenceCount, totalChunks, staleDocuments, failedDocuments,
                riskLevel, sensitiveHits, benchmark, gap);
        String markdown = buildMarkdown(summary, coverageLevel, coverageScore, findings, recommendations, retrieval, metrics);

        return KnowledgeOpsReport.builder()
                .coverageLevel(coverageLevel)
                .coverageScore(coverageScore)
                .scenario(context.getScenario())
                .planReason(context.getPlan() == null ? null : context.getPlan().getReason())
                .summary(summary)
                .findings(findings)
                .recommendations(recommendations)
                .metrics(metrics)
                .markdown(markdown)
                .build();
    }

    private String buildMarkdown(String summary,
                                 String coverageLevel,
                                 int coverageScore,
                                 List<String> findings,
                                 List<String> recommendations,
                                 AgentToolResult retrieval,
                                 Map<String, Object> metrics) {
        StringBuilder builder = new StringBuilder();
        builder.append("# KnowledgeOps Agent Report\n\n");
        builder.append("## Summary\n").append(summary).append("\n\n");
        builder.append("## Coverage\n")
                .append("- Level: ").append(coverageLevel).append("\n")
                .append("- Score: ").append(coverageScore).append("\n\n");
        builder.append("## Metrics\n");
        metrics.forEach((key, value) -> builder.append("- ").append(key).append(": ").append(value).append("\n"));
        builder.append("## Findings\n");
        findings.forEach(item -> builder.append("- ").append(item).append("\n"));
        builder.append("\n## Recommendations\n");
        recommendations.forEach(item -> builder.append("- ").append(item).append("\n"));
        builder.append("\n## Evidence\n");
        appendEvidence(builder, retrieval);
        return builder.toString();
    }

    private Map<String, Object> metrics(KnowledgeOpsContext context,
                                        int coverageScore,
                                        int evidenceCount,
                                        int totalChunks,
                                        int staleDocuments,
                                        int failedDocuments,
                                        String riskLevel,
                                        int sensitiveHits,
                                        AgentToolResult benchmark,
                                        AgentToolResult gap) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("scenario", context.getScenario());
        metrics.put("coverageScore", coverageScore);
        metrics.put("evidenceCount", evidenceCount);
        metrics.put("totalChunks", totalChunks);
        metrics.put("staleDocuments", staleDocuments);
        metrics.put("failedDocuments", failedDocuments);
        metrics.put("sensitiveRiskLevel", riskLevel);
        metrics.put("sensitiveHits", sensitiveHits);
        metrics.put("benchmarkQuestionCount", intValue(benchmark, "questionCount", 0));
        metrics.put("benchmarkHitRate", stringValue(benchmark, "hitRate", "0"));
        metrics.put("benchmarkAverageTopScore", stringValue(benchmark, "averageTopScore", "0"));
        metrics.put("weakQuestionCount", intValue(gap, "weakQuestionCount", 0));
        metrics.put("gapLevel", stringValue(gap, "gapLevel", "UNKNOWN"));
        metrics.put("toolCount", context.getToolResults() == null ? 0 : context.getToolResults().size());
        return metrics;
    }

    @SuppressWarnings("unchecked")
    private void appendGapSuggestions(List<String> recommendations, AgentToolResult gap) {
        if (gap == null || gap.getData() == null) {
            return;
        }
        Object suggestionsObj = gap.getData().get("suggestions");
        if (!(suggestionsObj instanceof List<?> suggestions)) {
            return;
        }
        suggestions.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .limit(3)
                .forEach(recommendations::add);
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
