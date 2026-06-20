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

package com.nageoffer.ai.ragent.agent.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.agent.controller.request.KnowledgeOpsRunPageRequest;
import com.nageoffer.ai.ragent.agent.controller.request.KnowledgeOpsRunRequest;
import com.nageoffer.ai.ragent.agent.controller.vo.KnowledgeOpsRunVO;
import com.nageoffer.ai.ragent.agent.controller.vo.KnowledgeOpsStepVO;
import com.nageoffer.ai.ragent.agent.service.KnowledgeOpsAgentService;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class KnowledgeOpsAgentController {

    private final KnowledgeOpsAgentService knowledgeOpsAgentService;

    @PostMapping("/agent/knowledge-ops/runs")
    public Result<KnowledgeOpsRunVO> run(@Valid @RequestBody KnowledgeOpsRunRequest request) {
        return Results.success(knowledgeOpsAgentService.run(request));
    }

    @GetMapping("/agent/knowledge-ops/runs")
    public Result<IPage<KnowledgeOpsRunVO>> page(KnowledgeOpsRunPageRequest request) {
        return Results.success(knowledgeOpsAgentService.page(request));
    }

    @GetMapping("/agent/knowledge-ops/runs/{run-id}")
    public Result<KnowledgeOpsRunVO> detail(@PathVariable("run-id") String runId) {
        return Results.success(knowledgeOpsAgentService.detail(runId));
    }

    @GetMapping("/agent/knowledge-ops/runs/{run-id}/steps")
    public Result<List<KnowledgeOpsStepVO>> steps(@PathVariable("run-id") String runId) {
        return Results.success(knowledgeOpsAgentService.steps(runId));
    }
}
