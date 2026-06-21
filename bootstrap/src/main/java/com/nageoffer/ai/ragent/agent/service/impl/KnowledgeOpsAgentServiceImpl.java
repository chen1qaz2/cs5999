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

package com.nageoffer.ai.ragent.agent.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.agent.controller.request.KnowledgeOpsRunPageRequest;
import com.nageoffer.ai.ragent.agent.controller.request.KnowledgeOpsRunRequest;
import com.nageoffer.ai.ragent.agent.controller.vo.KnowledgeOpsReportVO;
import com.nageoffer.ai.ragent.agent.controller.vo.KnowledgeOpsRunVO;
import com.nageoffer.ai.ragent.agent.controller.vo.KnowledgeOpsStepVO;
import com.nageoffer.ai.ragent.agent.core.AgentReporter;
import com.nageoffer.ai.ragent.agent.dao.entity.KnowledgeOpsRunDO;
import com.nageoffer.ai.ragent.agent.dao.entity.KnowledgeOpsStepDO;
import com.nageoffer.ai.ragent.agent.dao.mapper.KnowledgeOpsRunMapper;
import com.nageoffer.ai.ragent.agent.dao.mapper.KnowledgeOpsStepMapper;
import com.nageoffer.ai.ragent.agent.domain.AgentPlan;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsContext;
import com.nageoffer.ai.ragent.agent.domain.KnowledgeOpsReport;
import com.nageoffer.ai.ragent.agent.enums.KnowledgeOpsRunStatus;
import com.nageoffer.ai.ragent.agent.executor.KnowledgeOpsAgentExecutor;
import com.nageoffer.ai.ragent.agent.planner.KnowledgeOpsPlanner;
import com.nageoffer.ai.ragent.agent.service.KnowledgeOpsAgentService;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeOpsAgentServiceImpl implements KnowledgeOpsAgentService {

    private final KnowledgeOpsRunMapper runMapper;
    private final KnowledgeOpsStepMapper stepMapper;
    private final KnowledgeOpsPlanner planner;
    private final KnowledgeOpsAgentExecutor executor;
    private final AgentReporter reporter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeOpsRunVO run(KnowledgeOpsRunRequest request) {
        Date now = new Date();
        KnowledgeOpsRunDO run = KnowledgeOpsRunDO.builder()
                .userId(UserContext.getUserId())
                .kbId(request.getKbId())
                .task(request.getTask())
                .status(KnowledgeOpsRunStatus.RUNNING.name())
                .startedAt(now)
                .build();
        runMapper.insert(run);

        KnowledgeOpsContext context = KnowledgeOpsContext.builder()
                .runId(run.getId())
                .kbId(request.getKbId())
                .task(request.getTask())
                .topK(request.getTopK())
                .scenario(request.getScenario())
                .requestedWorkflow(request.getWorkflow())
                .benchmarkQuestions(request.getBenchmarkQuestions())
                .build();

        try {
            AgentPlan plan = planner.plan(context);
            context.setPlan(plan);
            context.setScenario(plan.getScenario());
            executor.execute(plan, context);
            KnowledgeOpsReport report = reporter.buildReport(context);
            run.setStatus(KnowledgeOpsRunStatus.SUCCESS.name());
            run.setSummary(report.getSummary());
            run.setReportJson(JSONUtil.toJsonStr(report));
            run.setFinishedAt(new Date());
            runMapper.updateById(run);
            return detail(run.getId());
        } catch (Exception ex) {
            run.setStatus(KnowledgeOpsRunStatus.FAILED.name());
            run.setErrorMessage(ex.getMessage());
            run.setFinishedAt(new Date());
            runMapper.updateById(run);
            throw ex;
        }
    }

    @Override
    public IPage<KnowledgeOpsRunVO> page(KnowledgeOpsRunPageRequest request) {
        LambdaQueryWrapper<KnowledgeOpsRunDO> query = new LambdaQueryWrapper<KnowledgeOpsRunDO>()
                .eq(request.getKbId() != null && !request.getKbId().isBlank(), KnowledgeOpsRunDO::getKbId, request.getKbId())
                .eq(request.getStatus() != null && !request.getStatus().isBlank(), KnowledgeOpsRunDO::getStatus, request.getStatus())
                .orderByDesc(KnowledgeOpsRunDO::getCreateTime);
        IPage<KnowledgeOpsRunDO> page = runMapper.selectPage(new Page<>(request.getCurrent(), request.getSize()), query);
        return page.convert(this::toRunVOWithoutSteps);
    }

    @Override
    public KnowledgeOpsRunVO detail(String runId) {
        KnowledgeOpsRunDO run = runMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("KnowledgeOps run not found: " + runId);
        }
        KnowledgeOpsRunVO vo = toRunVOWithoutSteps(run);
        vo.setSteps(steps(runId));
        return vo;
    }

    @Override
    public List<KnowledgeOpsStepVO> steps(String runId) {
        List<KnowledgeOpsStepDO> stepList = stepMapper.selectList(new LambdaQueryWrapper<KnowledgeOpsStepDO>()
                .eq(KnowledgeOpsStepDO::getRunId, runId)
                .orderByAsc(KnowledgeOpsStepDO::getStepOrder));
        return stepList.stream().map(this::toStepVO).toList();
    }

    private KnowledgeOpsRunVO toRunVOWithoutSteps(KnowledgeOpsRunDO run) {
        return KnowledgeOpsRunVO.builder()
                .id(run.getId())
                .kbId(run.getKbId())
                .task(run.getTask())
                .status(run.getStatus())
                .summary(run.getSummary())
                .errorMessage(run.getErrorMessage())
                .report(parseReport(run.getReportJson()))
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .createTime(run.getCreateTime())
                .build();
    }

    private KnowledgeOpsStepVO toStepVO(KnowledgeOpsStepDO step) {
        return KnowledgeOpsStepVO.builder()
                .id(step.getId())
                .runId(step.getRunId())
                .stepOrder(step.getStepOrder())
                .stepType(step.getStepType())
                .toolName(step.getToolName())
                .status(step.getStatus())
                .inputJson(step.getInputJson())
                .outputJson(step.getOutputJson())
                .errorMessage(step.getErrorMessage())
                .startedAt(step.getStartedAt())
                .finishedAt(step.getFinishedAt())
                .build();
    }

    private KnowledgeOpsReportVO parseReport(String reportJson) {
        if (reportJson == null || reportJson.isBlank()) {
            return null;
        }
        KnowledgeOpsReport report = JSONUtil.toBean(reportJson, KnowledgeOpsReport.class);
        return KnowledgeOpsReportVO.builder()
                .coverageLevel(report.getCoverageLevel())
                .coverageScore(report.getCoverageScore())
                .scenario(report.getScenario())
                .planReason(report.getPlanReason())
                .summary(report.getSummary())
                .findings(report.getFindings())
                .recommendations(report.getRecommendations())
                .metrics(report.getMetrics())
                .markdown(report.getMarkdown())
                .build();
    }
}
