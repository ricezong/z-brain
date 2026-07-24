package cn.kong.eon.rag.context;

import cn.kong.eon.persistence.dto.response.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 上下文组装器（重写：不再负责 citation 编号，编号已在检索层确定）
 *
 * <p>把检索结果组装为 LLM 可读的上下文文本，格式：</p>
 * <pre>
 * [doc_1] 来源：文件名
 * 子块内容...
 * （父块上下文：...）
 *
 * [doc_2] 来源：文件名
 * ...
 * </pre>
 *
 * @author eon-team
 */
@Component
public class ContextAssembler {

    private static final int MAX_PARENT_CONTENT_CHARS = 500;
    private static final int MAX_CHILD_CONTENT_CHARS = 2000;

    /**
     * 组装上下文文本
     *
     * @param results 检索结果列表（已含 citationLabel + parentContent + docName）
     * @return 组装后的上下文文本
     */
    public String assemble(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return "(未检索到相关内容)";
        }

        StringBuilder sb = new StringBuilder();
        for (RetrievalResult r : results) {
            String label = r.getCitationLabel() != null ? r.getCitationLabel() : "doc_?";
            String docName = r.getDocName() != null ? r.getDocName() : "未知文档";

            sb.append("[").append(label).append("] 来源：").append(docName).append("\n");

            // 子块内容
            String content = r.getContent() != null ? r.getContent() : "";
            if (content.length() > MAX_CHILD_CONTENT_CHARS) {
                content = content.substring(0, MAX_CHILD_CONTENT_CHARS) + "...";
            }
            sb.append(content).append("\n");

            // 父块上下文（如有）
            if (r.getParentContent() != null && !r.getParentContent().isBlank()) {
                String parent = r.getParentContent();
                if (parent.length() > MAX_PARENT_CONTENT_CHARS) {
                    parent = parent.substring(0, MAX_PARENT_CONTENT_CHARS) + "...";
                }
                sb.append("（上下文：").append(parent).append("）\n");
            }

            sb.append("\n");
        }

        return sb.toString().trim();
    }

    /**
     * 提取引用列表（供 SSE CITATIONS 事件推送）
     */
    public List<RetrievalResult> extractCitations(List<RetrievalResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        // 同 docId 只保留第一个（去重）
        java.util.Map<Long, RetrievalResult> seen = new java.util.LinkedHashMap<>();
        for (RetrievalResult r : results) {
            if (r.getDocId() != null && !seen.containsKey(r.getDocId())) {
                seen.put(r.getDocId(), r);
            }
        }
        return List.copyOf(seen.values());
    }
}
