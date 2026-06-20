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
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class KnowledgeBaseProfileTool implements AgentTool {

    public static final String TOOL_NAME = "knowledge-base-profile";

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "PROFILE";
    }

    @Override
    public AgentToolResult execute(KnowledgeOpsContext context) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(context.getKbId());
        if (kb == null) {
            throw new IllegalArgumentException("Knowledge base not found: " + context.getKbId());
        }

        Long documentCount = documentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, context.getKbId())
                .eq(KnowledgeDocumentDO::getDeleted, 0));
        Long enabledDocumentCount = documentMapper.selectCount(Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, context.getKbId())
                .eq(KnowledgeDocumentDO::getEnabled, 1)
                .eq(KnowledgeDocumentDO::getDeleted, 0));
        Long chunkCount = chunkMapper.selectCount(Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getKbId, context.getKbId())
                .eq(KnowledgeChunkDO::getDeleted, 0));
        Long enabledChunkCount = chunkMapper.selectCount(Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getKbId, context.getKbId())
                .eq(KnowledgeChunkDO::getEnabled, 1)
                .eq(KnowledgeChunkDO::getDeleted, 0));

        Map<String, Object> data = Map.of(
                "kbId", kb.getId(),
                "name", kb.getName(),
                "collectionName", kb.getCollectionName(),
                "embeddingModel", kb.getEmbeddingModel(),
                "documentCount", documentCount,
                "enabledDocumentCount", enabledDocumentCount,
                "chunkCount", chunkCount,
                "enabledChunkCount", enabledChunkCount
        );

        return AgentToolResult.builder()
                .summary("Knowledge base contains " + documentCount + " documents and " + chunkCount + " chunks.")
                .data(data)
                .build();
    }
}
