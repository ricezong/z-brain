package cn.kong.zbrain.llm.impl;

import cn.kong.zbrain.common.BusinessException;
import cn.kong.zbrain.llm.LLMService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLM 服务实现（基于 Spring AI ChatClient）
 *
 * <p>统一封装大模型交互，支持同步与流式调用。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMServiceImpl implements LLMService {

    private final ChatModel chatModel;

    private ChatClient chatClient;

    private ChatClient getChatClient() {
        if (chatClient == null) {
            chatClient = ChatClient.builder(chatModel).build();
        }
        return chatClient;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, List<ChatMessage> history) {
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            if (history != null) {
                for (ChatMessage msg : history) {
                    if ("user".equals(msg.role())) {
                        messages.add(new UserMessage(msg.content()));
                    } else if ("assistant".equals(msg.role())) {
                        messages.add(new AssistantMessage(msg.content()));
                    }
                }
            }
            messages.add(new UserMessage(userPrompt));

            Prompt prompt = new Prompt(messages);
            return chatModel.call(prompt).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("LLM 同步调用失败", e);
            throw new BusinessException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatStream(String systemPrompt, String userPrompt, List<ChatMessage> history,
                           Consumer<String> onChunk) {
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            if (history != null) {
                for (ChatMessage msg : history) {
                    if ("user".equals(msg.role())) {
                        messages.add(new UserMessage(msg.content()));
                    } else if ("assistant".equals(msg.role())) {
                        messages.add(new AssistantMessage(msg.content()));
                    }
                }
            }
            messages.add(new UserMessage(userPrompt));

            Prompt prompt = new Prompt(messages);
            chatModel.stream(prompt).toIterable().forEach(chatResponse -> {
                String text = chatResponse.getResult().getOutput().getText();
                if (text != null && !text.isEmpty()) {
                    onChunk.accept(text);
                }
            });
        } catch (Exception e) {
            log.error("LLM 流式调用失败", e);
            throw new BusinessException("LLM 流式调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String simpleChat(String prompt) {
        try {
            return getChatClient().prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("LLM 简单调用失败", e);
            throw new BusinessException("LLM 调用失败: " + e.getMessage(), e);
        }
    }
}
