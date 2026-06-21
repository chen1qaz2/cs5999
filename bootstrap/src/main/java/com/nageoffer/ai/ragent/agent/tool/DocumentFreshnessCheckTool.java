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
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DocumentFreshnessCheckTool implements AgentTool {

    public static final String TOOL_NAME = "document-freshness-check";

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeOpsAgentProperties properties;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "FRESHNESS";
    }

    @Override
    public String description() {
        return "Checks document update freshness, failed ingestion states, and stale knowledge risks.";
    }

    @Override
    public AgentToolResult execute(KnowledgeOpsContext context) {
        List<KnowledgeDocumentDO> documents = documentMapper.selectList(Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, context.getKbId())
                .eq(KnowledgeDocumentDO::getDeleted, 0));

        Date cutoff = Date.from(Instant.now().minus(Duration.ofDays(Math.max(1, properties.getStaleDocumentDays()))));
        int stale = 0;
        int failed = 0;
        int pending = 0;
        int disabled = 0;
        Date latestUpdate = null;
        List<Map<String, Object>> staleSamples = new ArrayList<>();

        for (KnowledgeDocumentDO document : documents) {
            if (document.getEnabled() == null || document.getEnabled() == 0) {
                disabled++;
            }
            String status = document.getStatus() == null ? "" : document.getStatus();
            if ("failed".equalsIgnoreCase(status)) {
                failed++;
            }
            if ("pending".equalsIgnoreCase(status) || "running".equalsIgnoreCase(status)) {
                pending++;
            }
            Date updatedAt = firstNonNull(document.getUpdateTime(), document.getCreateTime());
            if (updatedAt != null && (latestUpdate == null || updatedAt.after(latestUpdate))) {
                latestUpdate = updatedAt;
            }
            if (updatedAt != null && updatedAt.before(cutoff)) {
                stale++;
                if (staleSamples.size() < 5) {
                    staleSamples.add(Map.of(
                            "docId", document.getId(),
                            "docName", nullToEmpty(document.getDocName()),
                            "updatedAt", updatedAt
                    ));
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalDocuments", documents.size());
        data.put("staleDocumentDays", properties.getStaleDocumentDays());
        data.put("staleDocuments", stale);
        data.put("failedDocuments", failed);
        data.put("pendingDocuments", pending);
        data.put("disabledDocuments", disabled);
        data.put("latestUpdateTime", latestUpdate);
        data.put("staleSamples", staleSamples);

        return AgentToolResult.builder()
                .summary("Checked " + documents.size() + " documents. Stale: " + stale + ", failed: " + failed + ".")
                .data(data)
                .build();
    }

    private Date firstNonNull(Date first, Date second) {
        return first == null ? second : first;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
