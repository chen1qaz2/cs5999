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
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeChunkDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SensitiveInfoDetectTool implements AgentTool {

    public static final String TOOL_NAME = "sensitive-info-detect";

    private static final Map<String, Pattern> PATTERNS = Map.of(
            "PHONE", Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            "EMAIL", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "ID_CARD", Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)"),
            "SECRET", Pattern.compile("(?i)(api[_-]?key|secret|password|token|access[_-]?key)\\s*[:=]\\s*[^\\s,;]{6,}")
    );

    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeOpsAgentProperties properties;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "SECURITY";
    }

    @Override
    public String description() {
        return "Scans enabled chunks for common sensitive information patterns before production use.";
    }

    @Override
    public AgentToolResult execute(KnowledgeOpsContext context) {
        int scanLimit = Math.max(50, properties.getSensitiveScanLimit());
        List<KnowledgeChunkDO> chunks = chunkMapper.selectList(Wrappers.lambdaQuery(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getKbId, context.getKbId())
                .eq(KnowledgeChunkDO::getEnabled, 1)
                .eq(KnowledgeChunkDO::getDeleted, 0)
                .last("limit " + scanLimit));

        Map<String, Integer> hitByType = new LinkedHashMap<>();
        PATTERNS.keySet().forEach(type -> hitByType.put(type, 0));
        List<Map<String, Object>> samples = new ArrayList<>();

        for (KnowledgeChunkDO chunk : chunks) {
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            scanChunk(chunk, content, hitByType, samples);
        }

        int totalHits = hitByType.values().stream().mapToInt(Integer::intValue).sum();
        String riskLevel = totalHits == 0 ? "LOW" : totalHits < 5 ? "MEDIUM" : "HIGH";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("scannedChunks", chunks.size());
        data.put("scanLimit", scanLimit);
        data.put("riskLevel", riskLevel);
        data.put("totalHits", totalHits);
        data.put("hitByType", hitByType);
        data.put("samples", samples);

        return AgentToolResult.builder()
                .summary("Sensitive scan risk level is " + riskLevel + " with " + totalHits + " hits.")
                .data(data)
                .build();
    }

    private void scanChunk(KnowledgeChunkDO chunk,
                           String content,
                           Map<String, Integer> hitByType,
                           List<Map<String, Object>> samples) {
        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(content);
            if (!matcher.find()) {
                continue;
            }
            String type = entry.getKey();
            hitByType.compute(type, (key, value) -> value == null ? 1 : value + 1);
            if (samples.size() < 8) {
                samples.add(Map.of(
                        "chunkId", chunk.getId(),
                        "docId", nullToEmpty(chunk.getDocId()),
                        "type", type,
                        "excerpt", mask(abbreviate(matcher.group(), 80))
                ));
            }
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private String mask(String text) {
        if (text == null || text.length() <= 4) {
            return "****";
        }
        return text.substring(0, 2) + "****" + text.substring(text.length() - 2);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
