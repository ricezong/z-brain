package cn.kong.zbrain.chunk.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.kong.zbrain.chunk.ChunkingEngine;
import cn.kong.zbrain.chunk.MarkdownSemanticChunker;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.enums.ChunkStatus;
import cn.kong.zbrain.enums.ChunkType;
import cn.kong.zbrain.util.RecursiveCharacterSplitter;
import cn.kong.zbrain.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 默认分块引擎实现（v3：Markdown 语义边界 + 递归字符分块）
 *
 * <p>统一使用 Markdown 作为中间格式，两步分块策略：</p>
 * <ol>
 *   <li><b>父块切分（Markdown 语义边界）</b>：基于 Markdown ##/### 标题、
 *       |---| 表格语法进行语义切分，父块大小 512-2048 tokens。
 *       绝不跨标题/表格拆分。</li>
 *   <li><b>子块切分（递归字符分块）</b>：对每个父块独立递归字符切分，
 *       chunk_size=256 tokens，overlap=32 tokens。
 *       分隔符优先级：\n\n → \n → 。/！/？ → 空格 → 空字符串（兜底）。
 *       每个子块仅记录所属 parentId。</li>
 * </ol>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultChunkingEngine implements ChunkingEngine {

    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern LINE_TRAILING_SPACES = Pattern.compile("(?m)\\s+$");
    private static final Pattern LINE_LEADING_SPACES = Pattern.compile("(?m)^\\s+");

    private final ZBrainProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<Chunk> chunk(String markdownText, Long docId, Long kbId) {
        if (markdownText == null || markdownText.isBlank()) {
            return new ArrayList<>();
        }

        List<Chunk> result = new ArrayList<>();

        // ──────────── 第1步：Markdown 语义边界父块切分 ────────────
        int minTokens = properties.getChunk().getParentMinTokenSize();
        int maxTokens = properties.getChunk().getParentMaxTokenSize();

        List<MarkdownSemanticChunker.ParentChunk> parents =
                MarkdownSemanticChunker.chunkToParents(markdownText, minTokens, maxTokens);
        if (parents.isEmpty()) {
            log.warn("文档 docId={} Markdown 语义父块切分为空，回退到固定窗口切分", docId);
            parents = MarkdownSemanticChunker.chunkByFixedWindow(markdownText, maxTokens);
        }

        log.info("文档 docId={} 切分为 {} 个父块", docId, parents.size());

        // ──────────── 第2步：对每个父块递归字符切分子块 ────────────
        for (MarkdownSemanticChunker.ParentChunk pc : parents) {
            // 创建父块实体
            Chunk parent = new Chunk();
            parent.setDocId(docId);
            parent.setKbId(kbId);
            parent.setChunkType(ChunkType.PARENT.getCode());
            parent.setContent(cleanContent(pc.content));
            parent.setTokenCount(pc.tokenCount);
            parent.setStatus(ChunkStatus.DRAFT.getCode());
            parent.setContentVector(null); // 父块不向量化

            Map<String, Object> parentMeta = new HashMap<>();
            parentMeta.put("start_index", pc.startOffset);
            parentMeta.put("end_index", pc.endOffset);
            parentMeta.put("parent_index", pc.index);
            if (pc.headingLevel != null) {
                parentMeta.put("heading_level", pc.headingLevel);
            }
            parent.setMetadata(toJson(parentMeta));

            result.add(parent);

            // 切分子块（parentId 暂时为 null，由 Service 层插入后回填）
            List<Chunk> children = splitToChildren(pc.content, null, docId, kbId, pc.startOffset);
            result.addAll(children);
        }

        log.info("文档 docId={} 总计 {} 个块（父块: {}，子块: {})",
                docId, result.size(), parents.size(), result.size() - parents.size());

        return result;
    }

    /**
     * 对父块内容进行递归字符切分生成子块。
     *
     * <p>每个子块仅记录所属 parentId，不跨父块合并或重叠。
     * 分隔符优先级：\n\n → \n → 。/！/？ → " "（空格）→ ""（兜底强制切）。</p>
     *
     * @param parentContent 父块纯文本内容
     * @param parentId      父块 ID（可为 null，由 Service 层回填）
     * @param docId         文档 ID
     * @param kbId          知识库 ID
     * @param globalOffset  父块在原文中的起始偏移
     * @return 子块列表
     */
    @Override
    public List<Chunk> splitToChildren(String parentContent, Long parentId, Long docId, Long kbId, int globalOffset) {
        List<Chunk> children = new ArrayList<>();
        if (parentContent == null || parentContent.isBlank()) {
            return children;
        }

        int childTokenSize = properties.getChunk().getChildTokenSize();
        int childOverlap = properties.getChunk().getTokenOverlap();

        // 使用递归字符分块器，采用中文优先分隔符
        List<String> childTexts = RecursiveCharacterSplitter.split(
                parentContent, childTokenSize, childOverlap);

        int localOffset = 0;
        for (String childText : childTexts) {
            Chunk child = new Chunk();
            child.setDocId(docId);
            child.setKbId(kbId);
            child.setParentId(parentId);
            child.setChunkType(ChunkType.CHILD.getCode());
            String cleaned = cleanContent(childText);
            child.setContent(cleaned);
            child.setTokenCount(TokenUtils.countTokens(cleaned));
            child.setStatus(ChunkStatus.DRAFT.getCode());

            Map<String, Object> meta = new HashMap<>();
            meta.put("start_index", globalOffset + localOffset);
            meta.put("end_index", globalOffset + localOffset + childText.length());
            meta.put("local_start", localOffset);
            meta.put("local_end", localOffset + childText.length());
            child.setMetadata(toJson(meta));

            children.add(child);
            localOffset += childText.length();
        }

        return children;
    }

    @Override
    public String merge(List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Chunk chunk : chunks) {
            if (chunk.getContent() != null) {
                sb.append(chunk.getContent());
                if (!chunk.getContent().endsWith("\n")) {
                    sb.append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    @Override
    public List<Chunk> split(Chunk chunk, int splitPosition) {
        if (chunk == null || chunk.getContent() == null) {
            throw new IllegalArgumentException("分块或内容不能为空");
        }
        String content = chunk.getContent();
        if (splitPosition <= 0 || splitPosition >= content.length()) {
            throw new IllegalArgumentException("拆分位置超出范围");
        }

        String part1 = content.substring(0, splitPosition).trim();
        String part2 = content.substring(splitPosition).trim();

        List<Chunk> result = new ArrayList<>();

        Chunk c1 = new Chunk();
        c1.setDocId(chunk.getDocId());
        c1.setKbId(chunk.getKbId());
        c1.setParentId(chunk.getParentId());
        c1.setChunkType(chunk.getChunkType());
        c1.setContent(part1);
        c1.setTokenCount(TokenUtils.countTokens(part1));
        c1.setStatus(chunk.getStatus());
        c1.setMetadata(chunk.getMetadata());
        result.add(c1);

        Chunk c2 = new Chunk();
        c2.setDocId(chunk.getDocId());
        c2.setKbId(chunk.getKbId());
        c2.setParentId(chunk.getParentId());
        c2.setChunkType(chunk.getChunkType());
        c2.setContent(part2);
        c2.setTokenCount(TokenUtils.countTokens(part2));
        c2.setStatus(chunk.getStatus());
        c2.setMetadata(chunk.getMetadata());
        result.add(c2);

        return result;
    }

    /**
     * 清洗分块内容：去除多余空行、行首尾空白，保留单个段落间隔。
     *
     * <p>处理规则：</p>
     * <ul>
     *   <li>3+ 连续换行 → 压缩为 2 个换行（保留段落间隔）</li>
     *   <li>每行行首尾空白 → 去除</li>
     *   <li>整体首尾空白 → 去除</li>
     * </ul>
     *
     * @param text 原始文本
     * @return 清洗后的文本
     */
    private static String cleanContent(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 1. 压缩 3+ 连续换行为 2 个换行（保留段落间隔）
        String result = MULTI_BLANK_LINES.matcher(text).replaceAll("\n\n");
        // 2. 去除每行行首尾空白
        result = LINE_LEADING_SPACES.matcher(result).replaceAll("");
        result = LINE_TRAILING_SPACES.matcher(result).replaceAll("");
        // 3. 整体 trim
        return result.trim();
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("序列化 metadata 失败: {}", e.getMessage());
            return "{}";
        }
    }
}
