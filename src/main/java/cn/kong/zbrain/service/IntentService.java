package cn.kong.zbrain.service;

import cn.kong.zbrain.cache.ChatContextCache;
import cn.kong.zbrain.dto.IntentResult;

import java.util.List;

/**
 * 意图识别服务接口
 *
 * <p>负责判断用户输入的意图类型（闲聊、知识库问答、联网搜索等），
 * 采用「规则预筛 + LLM 结构化分类」双层策略。</p>
 *
 * @author zbrain-team
 */
public interface IntentService {

    /**
     * 意图识别（自动模式）
     *
     * @param query   用户问题
     * @param history 对话历史（用于上下文感知）
     * @param kbId    知识库 ID（有知识库时 RAG 意图置信度可适当提高）
     * @return 意图识别结果
     */
    IntentResult classify(String query, List<ChatContextCache.ChatMessage> history, Long kbId);
}
