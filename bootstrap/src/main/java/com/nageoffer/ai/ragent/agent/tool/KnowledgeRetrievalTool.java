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
import com.nageoffer.ai.ragent.agent.domain.AgentToolResult;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsContext;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRetrievalTool implements AgentTool {

    public static final String TOOL_NAME = "knowledge-retrieval";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final RetrieverService retrieverService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "RETRIEVE";
    }

    @Override
    public AgentToolResult execute(KnowledgeOpsContext context) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(context.getKbId());
        int topK = context.getTopK() == null || context.getTopK() <= 0 ? 8 : context.getTopK();

        List<Map<String, Object>> evidence;
        String mode;
        try {
            List<RetrievedChunk> chunks = retrieverService.retrieve(RetrieveRequest.builder()
                    .query(context.getTask())
                    .topK(topK)
                    .collectionName(kb == null ? null : kb.getCollectionName())
                    .build());
            evidence = chunks.stream()
                    .map(chunk -> Map.<String, Object>of(
                            "id", nullToEmpty(chunk.getId()),
                            "text", abbreviate(chunk.getText(), 600),
                            "score", chunk.getScore() == null ? 0 : chunk.getScore()
                    ))
                    .toList();
            mode = "vector";
        } catch (Exception ex) {
            log.warn("Vector retrieval failed, fallback to database sample. kbId={}", context.getKbId(), ex);
            List<KnowledgeChunkDO> chunks = chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                    .eq(KnowledgeChunkDO::getKbId, context.getKbId())
                    .eq(KnowledgeChunkDO::getEnabled, 1)
                    .eq(KnowledgeChunkDO::getDeleted, 0)
                    .last("limit " + topK));
            evidence = chunks.stream()
                    .map(chunk -> Map.<String, Object>of(
                            "id", chunk.getId(),
                            "docId", chunk.getDocId(),
                            "text", abbreviate(chunk.getContent(), 600),
                            "score", 0
                    ))
                    .toList();
            mode = "database-sample";
        }

        return AgentToolResult.builder()
                .summary("Retrieved " + evidence.size() + " evidence chunks by " + mode + ".")
                .data(Map.of("mode", mode, "evidence", evidence))
                .build();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
