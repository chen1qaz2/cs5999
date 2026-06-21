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

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.agent.config.KnowledgeOpsAgentProperties;
import com.nageoffer.ai.ragent.agent.domain.AgentToolResult;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsContext;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import com.nageoffer.ai.ragent.rag.dao.entity.SampleQuestionDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.SampleQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class QuestionSetBenchmarkTool implements AgentTool {

    public static final String TOOL_NAME = "question-set-benchmark";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final SampleQuestionMapper sampleQuestionMapper;
    private final RetrieverService retrieverService;
    private final KnowledgeOpsAgentProperties properties;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "BENCHMARK";
    }

    @Override
    public String description() {
        return "Runs a lightweight retrieval benchmark against task-specific or sample questions.";
    }

    @Override
    public Map<String, String> inputSchema() {
        return Map.of(
                "kbId", "Target knowledge base id.",
                "task", "Benchmark objective. Used as a fallback question.",
                "benchmarkQuestions", "Optional explicit benchmark question list.",
                "topK", "Evidence chunks to retrieve for each benchmark question."
        );
    }

    @Override
    public AgentToolResult execute(KnowledgeOpsContext context) {
        List<String> questions = resolveQuestions(context);
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(context.getKbId());
        int topK = context.getTopK() == null || context.getTopK() <= 0 ? 5 : context.getTopK();
        float minScore = properties.getBenchmarkMinScore();

        int hitCount = 0;
        int errorCount = 0;
        double totalTopScore = 0D;
        List<Map<String, Object>> items = new ArrayList<>();

        for (String question : questions) {
            Map<String, Object> item = benchmarkQuestion(question, topK, minScore, kb);
            if (Boolean.TRUE.equals(item.get("hit"))) {
                hitCount++;
            }
            if (item.get("error") != null) {
                errorCount++;
            }
            Object topScore = item.get("topScore");
            if (topScore instanceof Number number) {
                totalTopScore += number.doubleValue();
            }
            items.add(item);
        }

        int questionCount = questions.size();
        int missCount = Math.max(0, questionCount - hitCount);
        double hitRate = questionCount == 0 ? 0D : (double) hitCount / questionCount;
        double averageTopScore = questionCount == 0 ? 0D : totalTopScore / questionCount;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("questionCount", questionCount);
        data.put("hitCount", hitCount);
        data.put("missCount", missCount);
        data.put("errorCount", errorCount);
        data.put("hitRate", round(hitRate));
        data.put("averageTopScore", round(averageTopScore));
        data.put("minScore", minScore);
        data.put("items", items);

        return AgentToolResult.builder()
                .summary("Benchmark completed: " + hitCount + "/" + questionCount + " questions hit relevant evidence.")
                .data(data)
                .build();
    }

    private Map<String, Object> benchmarkQuestion(String question, int topK, float minScore, KnowledgeBaseDO kb) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("question", question);
        try {
            List<RetrievedChunk> chunks = retrieverService.retrieve(RetrieveRequest.builder()
                    .query(question)
                    .topK(topK)
                    .collectionName(kb == null ? null : kb.getCollectionName())
                    .build());
            RetrievedChunk top = chunks.isEmpty() ? null : chunks.get(0);
            float topScore = top == null || top.getScore() == null ? 0F : top.getScore();
            item.put("hit", topScore >= minScore);
            item.put("topScore", topScore);
            item.put("evidenceCount", chunks.size());
            item.put("topChunkId", top == null ? "" : nullToEmpty(top.getId()));
            item.put("topExcerpt", top == null ? "" : abbreviate(top.getText(), 220));
        } catch (Exception ex) {
            item.put("hit", false);
            item.put("topScore", 0F);
            item.put("evidenceCount", 0);
            item.put("topChunkId", "");
            item.put("topExcerpt", "");
            item.put("error", ex.getMessage());
        }
        return item;
    }

    private List<String> resolveQuestions(KnowledgeOpsContext context) {
        int limit = Math.max(1, properties.getBenchmarkQuestionLimit());
        Set<String> questions = new LinkedHashSet<>();
        if (context.getBenchmarkQuestions() != null) {
            context.getBenchmarkQuestions().stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .limit(limit)
                    .forEach(questions::add);
        }
        if (questions.isEmpty() && context.getTask() != null && !context.getTask().isBlank()) {
            questions.add(context.getTask().trim());
        }
        if (questions.size() < limit) {
            List<SampleQuestionDO> samples = sampleQuestionMapper.selectList(Wrappers.lambdaQuery(SampleQuestionDO.class)
                    .orderByDesc(SampleQuestionDO::getCreateTime)
                    .last("limit " + limit));
            for (SampleQuestionDO sample : samples) {
                if (sample.getQuestion() != null && !sample.getQuestion().isBlank()) {
                    questions.add(sample.getQuestion().trim());
                    if (questions.size() >= limit) {
                        break;
                    }
                }
            }
        }
        return new ArrayList<>(questions);
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double round(double value) {
        return Math.round(value * 10000D) / 10000D;
    }
}
