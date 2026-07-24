package cn.kong.eon.agent.advisor;

import cn.kong.eon.agent.evolution.PromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Workspace md 资产注入 Advisor
 *
 * <p>在 {@code TieredCompressionAdvisor}(order=200) 之后执行（order=300），
 * 将 workspace 目录下的 SOUL / USER / MEMORY md 资产经 {@link PromptBuilder} 组装后
 * 注入到系统提示词尾部（agent_system 硬约束层 + md 资产动态层合并）。</p>
 *
 * <p>注入策略：找到第一条 SystemMessage 并追加注入内容；若无 SystemMessage 则
 * 在列表头部插入新 SystemMessage。保证注入内容始终在上下文最前部，
 * 不受压缩 Advisor 裁剪影响（保护区内的消息不会被压缩）。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceInjectAdvisor implements BaseAdvisor {

    public static final int ORDER = 300;

    private final PromptBuilder promptBuilder;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String injection = promptBuilder.buildInjection();
        if (injection == null || injection.isBlank()) {
            return request;
        }

        List<Message> messages = request.prompt().getInstructions();
        List<Message> processed = new ArrayList<>(messages.size());
        boolean injected = false;
        for (Message m : messages) {
            if (!injected && m.getMessageType() == MessageType.SYSTEM) {
                String merged = (m.getText() != null ? m.getText() : "") + injection;
                processed.add(new SystemMessage(merged));
                injected = true;
            } else {
                processed.add(m);
            }
        }
        if (!injected) {
            processed.add(0, new SystemMessage(injection.strip()));
        }

        log.debug("[WorkspaceInject] 注入 md 资产 {} 字符", injection.length());
        return request.mutate()
                .prompt(request.prompt().mutate().messages(processed).build())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }
}
