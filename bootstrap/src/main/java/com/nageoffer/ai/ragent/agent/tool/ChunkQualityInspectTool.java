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
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChunkQualityInspectTool implements AgentTool {

    public static final String TOOL_NAME = "chunk-quality-inspect";

    private final KnowledgeChunkMapper chunkMapper;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "INSPECT";
    }

    @Override
    public AgentToolResult execute(KnowledgeOpsContext context) {
        List<KnowledgeChunkDO> chunks = chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getKbId, context.getKbId())
                .eq(KnowledgeChunkDO::getDeleted, 0));

        int total = chunks.size();
        int disabled = 0;
        int empty = 0;
        int tooShort = 0;
        int tooLong = 0;
        int duplicate = 0;
        long totalChars = 0L;
        Set<String> hashes = new HashSet<>();

        for (KnowledgeChunkDO chunk : chunks) {
            if (chunk.getEnabled() == null || chunk.getEnabled() == 0) {
                disabled++;
            }
            String content = chunk.getContent();
            int length = content == null ? 0 : content.length();
            totalChars += length;
            if (length == 0) {
                empty++;
            }
            if (length > 0 && length < 80) {
                tooShort++;
            }
            if (length > 1200) {
                tooLong++;
            }
            String hash = chunk.getContentHash();
            if (hash != null && !hash.isBlank() && !hashes.add(hash)) {
                duplicate++;
            }
        }

        long averageChars = total == 0 ? 0 : totalChars / total;
        Map<String, Object> data = Map.of(
                "totalChunks", total,
                "disabledChunks", disabled,
                "emptyChunks", empty,
                "tooShortChunks", tooShort,
                "tooLongChunks", tooLong,
                "duplicateHashChunks", duplicate,
                "averageChars", averageChars
        );

        return AgentToolResult.builder()
                .summary("Inspected " + total + " chunks. Avg chars: " + averageChars + ".")
                .data(data)
                .build();
    }
}
