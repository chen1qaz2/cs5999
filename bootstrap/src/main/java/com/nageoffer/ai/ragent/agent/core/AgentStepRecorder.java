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

import cn.hutool.json.JSONUtil;
import com.nageoffer.ai.ragent.agent.dao.entity.KnowledgeOpsStepDO;
import com.nageoffer.ai.ragent.agent.dao.mapper.KnowledgeOpsStepMapper;
import com.nageoffer.ai.ragent.agent.domain.AgentStepStatus;
import com.nageoffer.ai.ragent.agent.domain.AgentToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentStepRecorder {

    private final KnowledgeOpsStepMapper stepMapper;

    public KnowledgeOpsStepDO start(String runId, int order, String stepType, String toolName, Map<String, Object> input) {
        KnowledgeOpsStepDO step = KnowledgeOpsStepDO.builder()
                .runId(runId)
                .stepOrder(order)
                .stepType(stepType)
                .toolName(toolName)
                .status(AgentStepStatus.RUNNING.name())
                .inputJson(JSONUtil.toJsonStr(input))
                .startedAt(new Date())
                .build();
        stepMapper.insert(step);
        return step;
    }

    public void success(KnowledgeOpsStepDO step, AgentToolResult result) {
        step.setStatus(AgentStepStatus.SUCCESS.name());
        step.setOutputJson(JSONUtil.toJsonStr(result));
        step.setFinishedAt(new Date());
        stepMapper.updateById(step);
    }

    public void failure(KnowledgeOpsStepDO step, Exception exception) {
        step.setStatus(AgentStepStatus.FAILED.name());
        step.setErrorMessage(exception.getMessage());
        step.setFinishedAt(new Date());
        stepMapper.updateById(step);
    }
}
