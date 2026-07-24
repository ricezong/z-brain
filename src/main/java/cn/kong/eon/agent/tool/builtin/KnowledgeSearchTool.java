package cn.kong.eon.agent.tool.builtin;

import cn.kong.eon.agent.tool.InnerTool;
import cn.kong.eon.persistence.dto.response.RetrievalResult;
import cn.kong.eon.rag.retrieval.HybridRetriever;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知识库检索工具（改调新 rag.retrieval.HybridRetriever）
 *
 * <p>citation 编号在检索层确定（doc_1/doc_2...），工具直接透传。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool implements InnerTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HybridRetriever hybridRetriever;

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "搜索知识库中的文档内容。当问题涉及已入库的文档资料、专业知识、私有数据，"
                + "或你不确定答案时，必须先调用本工具查询，禁止凭记忆编造。"
                + "返回带 [doc_x] 引用编号的知识片段及其完整上下文。";
    }

    @Override
    public String jsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "检索查询，应是独立完整、消解了指代的问句或关键词组合"
                    },
                    "kb_id": {
                      "type": "integer",
                      "description": "限定检索的知识库 ID（可选，不填则全局检索）"
                    }
                  },
                  "required": ["query"]
                }
                """;
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode args = MAPPER.readTree(argumentsJson);
            String query = args.path("query").asText();
            if (query == null || query.isBlank()) {
                return errorJson("query 参数不能为空");
            }
            Long kbId = args.hasNonNull("kb_id") ? args.get("kb_id").asLong() : null;

            List<RetrievalResult> results = hybridRetriever.retrieve(kbId, query, query);
            return toResultJson(results);
        } catch (Exception e) {
            log.warn("[KnowledgeSearchTool] 检索失败", e);
            throw new IllegalStateException("知识库检索失败: " + e.getMessage(), e);
        }
    }

    private String toResultJson(List<RetrievalResult> results) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        if (results == null || results.isEmpty()) {
            root.put("hit", false);
            root.put("message", "知识库中未找到相关内容，请如实告知用户并建议补充知识库。");
            return MAPPER.writeValueAsString(root);
        }

        root.put("hit", true);
        root.put("citation_guide", "回答中引用以下知识时，必须在句末内联标注对应的 [doc_x] 编号");
        ArrayNode array = root.putArray("results");
        for (RetrievalResult r : results) {
            ObjectNode item = array.addObject();
            item.put("citation", r.getCitationLabel());
            item.put("content", r.getContent());
            item.put("parent_content", r.getParentContent());
            item.put("doc_name", r.getDocName());
            item.put("score", r.getScore());
            item.put("doc_id", r.getDocId());
            if (r.getSources() != null && !r.getSources().isEmpty()) {
                item.putPOJO("sources", r.getSources());
            }
        }
        return MAPPER.writeValueAsString(root);
    }

    private String errorJson(String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("hit", false);
        node.put("error", message);
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"hit\":false,\"error\":\"" + message + "\"}";
        }
    }
}
