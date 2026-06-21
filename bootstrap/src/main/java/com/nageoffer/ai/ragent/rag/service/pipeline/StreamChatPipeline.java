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

package com.nageoffer.ai.ragent.rag.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import com.nageoffer.ai.ragent.rag.config.RagFallbackProperties;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.guidance.GuidanceDecision;
import com.nageoffer.ai.ragent.rag.core.guidance.IntentGuidanceService;
import com.nageoffer.ai.ragent.rag.core.intent.IntentResolver;
import com.nageoffer.ai.ragent.rag.core.memory.ConversationMemoryService;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptContext;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.core.prompt.RAGPromptService;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrievalEngine;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.dto.IntentGroup;
import com.nageoffer.ai.ragent.rag.dto.RetrievalContext;
import com.nageoffer.ai.ragent.rag.dto.SubQuestionIntent;
import com.nageoffer.ai.ragent.rag.service.handler.StreamTaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CHAT_FALLBACK_PROMPT_PATH;
import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.CHAT_SYSTEM_PROMPT_PATH;

/**
 * 流式对话流水线
 * <p>
 * 承载从 RAGChatServiceImpl 提取的业务编排逻辑：
 * 记忆加载 -> 改写拆分 -> 意图解析 -> 歧义引导 -> 系统响应 / 检索 -> Prompt 组装 -> 流式输出
 * <p>
 * 流水线模式：通过私有方法 + boolean 返回值（handleXxx 返回 true 表示已处理并短路）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamChatPipeline {

    private final SearchChannelProperties searchProperties;
    private final ConversationMemoryService memoryService;
    private final QueryRewriteService queryRewriteService;
    private final IntentResolver intentResolver;
    private final IntentGuidanceService guidanceService;
    private final RetrievalEngine retrievalEngine;
    private final RagFallbackProperties fallbackProperties;
    private final LLMService llmService;
    private final RAGPromptService promptBuilder;
    private final PromptTemplateLoader promptTemplateLoader;
    private final StreamTaskManager taskManager;

    /**
     * 执行流式对话管道
     */
    public void execute(StreamChatContext ctx) {
        loadMemory(ctx);
        rewriteQuery(ctx);
        resolveIntents(ctx);

        if (handleGuidance(ctx)) {
            return;
        }
        if (handleSystemOnly(ctx)) {
            return;
        }
        if (handleDirectFallback(ctx)) {
            return;
        }

        RetrievalContext retrievalCtx = retrieve(ctx);
        if (handleFallbackRetrieval(ctx, retrievalCtx)) {
            return;
        }

        streamRagResponse(ctx, retrievalCtx);
    }

    // ==================== 流水线阶段 ====================

    private void loadMemory(StreamChatContext ctx) {
        List<ChatMessage> history = memoryService.loadAndAppend(
                ctx.getConversationId(),
                ctx.getUserId(),
                ChatMessage.user(ctx.getQuestion())
        );
        ctx.setHistory(history);
    }

    private void rewriteQuery(StreamChatContext ctx) {
        RewriteResult rewriteResult = queryRewriteService.rewriteWithSplit(ctx.getQuestion(), ctx.getHistory());
        ctx.setRewriteResult(rewriteResult);
    }

    private void resolveIntents(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = intentResolver.resolve(ctx.getRewriteResult());
        ctx.setSubIntents(subIntents);
    }

    private boolean handleGuidance(StreamChatContext ctx) {
        GuidanceDecision decision = guidanceService.detectAmbiguity(
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getSubIntents()
        );
        if (!decision.isPrompt()) {
            return false;
        }
        StreamCallback callback = ctx.getCallback();
        callback.onContent(decision.getPrompt());
        callback.onComplete();
        return true;
    }

    private boolean handleSystemOnly(StreamChatContext ctx) {
        List<SubQuestionIntent> subIntents = ctx.getSubIntents();
        if (CollUtil.isEmpty(subIntents)) {
            return false;
        }
        boolean allSystemOnly = subIntents.stream()
                .allMatch(si -> intentResolver.isSystemOnly(si.nodeScores()));
        if (!allSystemOnly) {
            return false;
        }
        String customPrompt = subIntents.stream()
                .flatMap(si -> si.nodeScores().stream())
                .map(ns -> ns.getNode().getPromptTemplate())
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
        StreamCancellationHandle handle = streamSystemResponse(
                ctx.getRewriteResult().rewrittenQuestion(),
                ctx.getHistory(),
                customPrompt,
                ctx.getCallback()
        );
        taskManager.bindHandle(ctx.getTaskId(), handle);
        return true;
    }

    private RetrievalContext retrieve(StreamChatContext ctx) {
        return retrievalEngine.retrieve(ctx.getSubIntents(), searchProperties.getDefaultTopK());
    }

    private boolean handleDirectFallback(StreamChatContext ctx) {
        if (!fallbackProperties.isEnabled()
                || !fallbackProperties.isDirectBasicQuestionEnabled()
                || !isBasicFallbackQuestion(ctx.getQuestion())) {
            return false;
        }
        streamFallbackResponse(ctx, "basic-question");
        return true;
    }

    private boolean handleFallbackRetrieval(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        if (retrievalCtx.isEmpty()) {
            streamFallbackResponse(ctx, "empty-retrieval");
            return true;
        }
        if (isLowConfidenceRetrieval(retrievalCtx)) {
            streamFallbackResponse(ctx, "low-confidence-retrieval");
            return true;
        }
        return false;
    }

    private void streamFallbackResponse(StreamChatContext ctx, String reason) {
        try {
            log.info("RAG 进入兜底回答, reason={}, question={}", reason, ctx.getQuestion());
            StreamCancellationHandle handle = streamSystemResponse(
                    ctx.getRewriteResult().rewrittenQuestion(),
                    ctx.getHistory(),
                    promptTemplateLoader.load(CHAT_FALLBACK_PROMPT_PATH),
                    ctx.getCallback()
            );
            taskManager.bindHandle(ctx.getTaskId(), handle);
        } catch (Exception e) {
            log.warn("兜底回答调用大模型失败，使用本地降级响应, reason={}, question={}", reason, ctx.getQuestion(), e);
            StreamCallback callback = ctx.getCallback();
            callback.onContent(buildLocalFallbackAnswer(ctx.getQuestion()));
            callback.onComplete();
        }
    }

    private boolean isLowConfidenceRetrieval(RetrievalContext retrievalCtx) {
        if (!fallbackProperties.isEnabled()
                || !fallbackProperties.isLowConfidenceEnabled()
                || retrievalCtx.hasMcp()) {
            return false;
        }
        Map<String, List<RetrievedChunk>> intentChunks = retrievalCtx.getIntentChunks();
        if (CollUtil.isEmpty(intentChunks)) {
            return false;
        }
        double maxScore = intentChunks.values().stream()
                .filter(CollUtil::isNotEmpty)
                .flatMap(List::stream)
                .map(RetrievedChunk::getScore)
                .filter(score -> score != null)
                .mapToDouble(Float::doubleValue)
                .max()
                .orElse(Double.NaN);
        return !Double.isNaN(maxScore) && maxScore < fallbackProperties.getMinRelevantScore();
    }

    private boolean isBasicFallbackQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return true;
        }
        String normalized = question.trim().toLowerCase();
        String compact = normalized.replaceAll("\\s+", "");
        return compact.matches("^(你好|您好|嗨|哈喽|hello|hi|hey|在吗|早上好|上午好|中午好|下午好|晚上好)[!！。？?]*$")
                || compact.contains("你是谁")
                || compact.contains("你能做什么")
                || compact.contains("你可以做什么")
                || compact.contains("你是什么")
                || compact.contains("介绍一下你自己")
                || compact.equals("help")
                || compact.equals("帮助");
    }

    private String buildLocalFallbackAnswer(String question) {
        if (StrUtil.isBlank(question)) {
            return "我目前没有收到明确的问题。你可以继续输入需要查询或咨询的内容。";
        }
        String normalized = question.trim().toLowerCase();
        if (normalized.contains("你可以做什么")
                || normalized.contains("你能做什么")
                || normalized.contains("你是谁")
                || normalized.contains("能做什么")
                || normalized.contains("可以做什么")) {
            return "我是企业知识助手小码，可以帮你查询知识库内容、梳理企业内部流程、回答系统使用问题；当知识库暂时没有相关内容时，也可以回答基础概念、日常说明和一般性技术问题。";
        }
        if (normalized.contains("rag")) {
            return "RAG 是检索增强生成：先从外部知识源检索相关资料，再把资料交给大模型生成回答。它适合企业知识库问答，因为可以让回答尽量贴近公司已有文档。";
        }
        if (normalized.contains("agent") || normalized.contains("智能体")) {
            return "AI Agent 通常指能够理解目标、规划步骤、调用工具并根据结果继续行动的智能体。相比普通问答，它更强调任务执行和闭环。";
        }
        if (normalized.contains("java")) {
            return "Java 是一种面向对象的编程语言，常用于后端服务、企业应用、Android 生态和大数据组件开发，特点是跨平台、生态成熟、工程化能力强。";
        }
        if (normalized.contains("天气")) {
            return "天气属于实时信息，当前没有可用的实时工具结果。如果系统接入了 MCP 天气工具，我可以根据城市和日期继续查询。";
        }
        return "当前没有可用的大模型兜底响应。对于企业内部制度、流程或业务数据问题，请补充相关知识库文档后再问；对于一般基础问题，可以稍后在模型服务恢复后继续提问。";
    }

    private void streamRagResponse(StreamChatContext ctx, RetrievalContext retrievalCtx) {
        // 聚合所有意图用于 prompt 规划
        IntentGroup mergedGroup = intentResolver.mergeIntentGroup(ctx.getSubIntents());

        StreamCancellationHandle handle = streamLLMResponse(
                ctx.getRewriteResult(),
                retrievalCtx,
                mergedGroup,
                ctx.getHistory(),
                ctx.isDeepThinking(),
                ctx.getCallback()
        );
        taskManager.bindHandle(ctx.getTaskId(), handle);
    }

    // ==================== LLM 响应 ====================

    private StreamCancellationHandle streamSystemResponse(String question, List<ChatMessage> history,
                                                          String customPrompt, StreamCallback callback) {
        String systemPrompt = StrUtil.isNotBlank(customPrompt)
                ? customPrompt
                : promptTemplateLoader.load(CHAT_SYSTEM_PROMPT_PATH);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        if (CollUtil.isNotEmpty(history)) {
            messages.addAll(history);
        }
        messages.add(ChatMessage.user(question));

        ChatRequest req = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7D)
                .thinking(false)
                .build();
        return llmService.streamChat(req, callback);
    }

    private StreamCancellationHandle streamLLMResponse(RewriteResult rewriteResult, RetrievalContext ctx,
                                                       IntentGroup intentGroup, List<ChatMessage> history,
                                                       boolean deepThinking, StreamCallback callback) {
        PromptContext promptContext = PromptContext.builder()
                .question(rewriteResult.rewrittenQuestion())
                .mcpContext(ctx.getMcpContext())
                .kbContext(ctx.getKbContext())
                .mcpIntents(intentGroup.mcpIntents())
                .kbIntents(intentGroup.kbIntents())
                .intentChunks(ctx.getIntentChunks())
                .build();

        List<ChatMessage> messages = promptBuilder.buildStructuredMessages(
                promptContext,
                history,
                rewriteResult.rewrittenQuestion(),
                rewriteResult.subQuestions()  // 传入子问题列表
        );
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .thinking(deepThinking)
                .temperature(ctx.hasMcp() ? 0.3D : 0D)  // MCP 场景稍微放宽温度
                .topP(ctx.hasMcp() ? 0.8D : 1D)
                .build();

        return llmService.streamChat(chatRequest, callback);
    }
}
