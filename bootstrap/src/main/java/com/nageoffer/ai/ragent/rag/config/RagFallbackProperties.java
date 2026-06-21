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

package com.nageoffer.ai.ragent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 兜底回答策略配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.fallback")
public class RagFallbackProperties {

    /**
     * 是否启用兜底回答策略。空检索仍会被兜底处理，避免后续 Prompt 构建异常。
     */
    private boolean enabled = true;

    /**
     * 对打招呼、自我介绍、能力说明等基础问题，是否直接跳过检索进入兜底回答。
     */
    private boolean directBasicQuestionEnabled = true;

    /**
     * 是否对低相关度检索结果启用兜底回答。
     */
    private boolean lowConfidenceEnabled = true;

    /**
     * 最高命中分数低于该阈值时，认为知识库结果不可靠，转入通用兜底回答。
     */
    private double minRelevantScore = 0.35D;
}
