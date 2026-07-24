package cn.kong.eon.agent.memory;

import cn.kong.eon.persistence.entity.ChatMessage;
import cn.kong.eon.persistence.mapper.ChatMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 PostgreSQL chat_message 表的 ChatMemoryRepository 实现。
 *
 * <p>替代 Spring AI 官方 JdbcChatMemoryRepository（默认表名 SPRING_AI_CHAT_MEMORY），
 * 统一使用 {@code chat_message} 表，保持全库命名一致。</p>
 *
 * <p>消息序列化格式（content 字段）：
 * <pre>{"type":"USER","text":"消息文本","metadata":{"msg_seq":123,...}}</pre>
 * {@code msg_seq} 取自 {@code chat_message.id}（BIGSERIAL 主键，会话内单调），
 * 注入到 Message.metadata 供压缩 Advisor 做决策缓存稳定 key
 *（方案铁律 #2：决策按 msg_seq 持久化，保 Prompt Cache 前缀稳定）。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgChatMemoryRepository implements ChatMemoryRepository {

    /** metadata 中存放消息序号的 key（值 = chat_message.id） */
    public static final String MSG_SEQ_KEY = "msg_seq";

    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<String> findConversationIds() {
        return chatMessageMapper.selectDistinctConversationIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<ChatMessage> rows = chatMessageMapper.selectByConversationId(conversationId);
        List<Message> messages = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            // 把 chat_message.id 作为 msg_seq 注入 metadata，供压缩 Advisor 做决策 key
            Message msg = deserialize(row.getType(), row.getContent(), row.getId());
            if (msg != null) {
                messages.add(msg);
            }
        }
        return messages;
    }

    /**
     * 保存会话消息（diff-based upsert，保留 id/msg_seq 稳定）。
     *
     * <p>遵循 Spring AI {@link ChatMemoryRepository} 契约：最终状态等于给定消息列表
     * （replace 语义），但实现为 diff 而非 delete+insert 全量。</p>
     * <ol>
     *   <li>查现有消息 id 集合</li>
     *   <li>给定消息中带 msg_seq(=id) 且存在的 → {@link ChatMessageMapper#updateContentById}
     *       定向更新（保留 id，文本可能因压缩而变）</li>
     *   <li>无 msg_seq 的（新消息）→ batchInsert（BIGSERIAL 生成新 id）</li>
     *   <li>现有但不在给定列表的 → {@link ChatMessageMapper#deleteByIds} 删除</li>
     * </ol>
     *
     * <p><b>为何不用 delete+insert 全量</b>：{@code MessageChatMemoryAdvisor} 每轮
     * before/after 都调 saveAll，全量 replace 会让 id 每次重新生成，msg_seq 失稳，
     * 压缩决策缓存（按 msg_seq）跨轮失效、Prompt Cache 前缀被破坏。diff-based
     * 保留 id 稳定，决策缓存真正有效（方案铁律 #1/#2）。</p>
     */
    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        List<ChatMessage> existing = chatMessageMapper.selectByConversationId(conversationId);
        Map<Long, ChatMessage> existingMap = new HashMap<>(existing.size());
        for (ChatMessage r : existing) {
            existingMap.put(r.getId(), r);
        }

        Set<Long> keepIds = new HashSet<>();
        List<ChatMessage> toInsert = new ArrayList<>();
        if (messages != null && !messages.isEmpty()) {
            for (Message msg : messages) {
                Long id = extractMsgSeq(msg);
                if (id != null && existingMap.containsKey(id)) {
                    keepIds.add(id);
                    // content 可能因压缩而变，定向 update 保留 id/msg_seq
                    chatMessageMapper.updateContentById(id, serialize(msg));
                } else {
                    ChatMessage row = new ChatMessage();
                    row.setConversationId(conversationId);
                    row.setType(msg.getMessageType().name());
                    row.setContent(serialize(msg));
                    row.setMsgTime(LocalDateTime.now());
                    toInsert.add(row);
                }
            }
        }
        if (!toInsert.isEmpty()) {
            chatMessageMapper.batchInsert(toInsert);
        }
        // 删除多余的（现有但不在保留集合的）
        Set<Long> toDelete = new HashSet<>(existingMap.keySet());
        toDelete.removeAll(keepIds);
        if (!toDelete.isEmpty()) {
            chatMessageMapper.deleteByIds(toDelete);
        }
    }

    /**
     * 从消息 metadata 提取 msg_seq（= chat_message.id）。
     */
    private Long extractMsgSeq(Message msg) {
        if (msg.getMetadata() == null) {
            return null;
        }
        Object v = msg.getMetadata().get(MSG_SEQ_KEY);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        chatMessageMapper.deleteByConversationId(conversationId);
    }

    // ==================== 序列化 / 反序列化 ====================

    private String serialize(Message msg) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", msg.getMessageType().name());
            data.put("text", msg.getText() != null ? msg.getText() : "");
            Map<String, Object> metadata = msg.getMetadata();
            if (metadata != null && !metadata.isEmpty()) {
                // 拷贝一份，避免持有 Message 内部可变 Map 引用
                data.put("metadata", new HashMap<>(metadata));
            }
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("消息序列化失败: type={}", msg.getMessageType(), e);
            return "{\"type\":\"" + msg.getMessageType().name() + "\",\"text\":\"\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private Message deserialize(String type, String content, Long msgSeq) {
        try {
            Map<String, Object> data = objectMapper.readValue(content, Map.class);
            String text = (String) data.getOrDefault("text", "");
            Map<String, Object> metadata = new HashMap<>();
            // msg_seq 优先取持久化的 chat_message.id（稳定 key）
            if (msgSeq != null) {
                metadata.put(MSG_SEQ_KEY, msgSeq);
            }
            Object savedMeta = data.get("metadata");
            if (savedMeta instanceof Map) {
                metadata.putAll((Map<String, Object>) savedMeta);
            }
            // msg_seq 以 db id 为准（覆盖 metadata 里可能过期的旧值）
            if (msgSeq != null) {
                metadata.put(MSG_SEQ_KEY, msgSeq);
            }
            MessageType msgType = MessageType.valueOf(type);
            Message msg = switch (msgType) {
                case USER -> new UserMessage(text);
                case ASSISTANT -> new AssistantMessage(text);
                case SYSTEM -> new SystemMessage(text);
                case TOOL -> new UserMessage(text); // S1 降级，S3 沿用
            };
            // 回填 metadata（含 msg_seq）：构造后 put（Spring AI 消息 (String,Map) 构造不可用）
            if (!metadata.isEmpty()) {
                msg.getMetadata().putAll(metadata);
            }
            return msg;
        } catch (Exception e) {
            log.warn("消息反序列化失败，降级为 UserMessage: type={}, content={}", type, content, e);
            return new UserMessage(content);
        }
    }
}
