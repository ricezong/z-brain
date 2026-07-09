package cn.kong.zbrain.chunk.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.kong.zbrain.chunk.ChunkingEngine;
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
 * 默认分块引擎实现（v4：双层递归字符分块）
 *
 * <p>统一使用 {@link RecursiveCharacterSplitter} 进行两轮切分，父层粗切 + 子层细切：</p>
 * <ol>
 *   <li><b>父层切分</b>：对全文递归字符切分，chunk_size≈1000 tokens，overlap≈150 tokens。
 *       分隔符优先级：{@code \n\n → \n → 。→ ！→ ？→ ，→ 空格 → 强制切}。
 *       父块保留完整语义上下文，不向量化。</li>
 *   <li><b>子层切分</b>：对每个父块独立递归字符切分，chunk_size≈300 tokens，overlap≈40 tokens。
 *       分隔符优先级与父层一致，仅阈值更小。子块用于精确向量检索。</li>
 * </ol>
 *
 * <p>两层切分器本质相同，区别仅在 chunk_size 和 chunk_overlap，子块大小约为父块的 1/3。</p>
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

    /** 句末标点：行尾出现这些符号时视为一句话结束，保留换行 */
    private static final String SENTENCE_END_PUNCT = "。！？.!?…";

    /** 独立成行的 URL（PDF 页眉页脚中常见，需移除） */
    private static final Pattern URL_LINE_PATTERN =
            Pattern.compile("(?m)^https?://\\S+\\s*$");

    /** 编号标题模式：2.3.3. 标题（至少两级编号），不应合并换行 */
    private static final Pattern NUMBERED_HEADING_PATTERN =
            Pattern.compile("^\\d+\\.\\d+(\\.\\d+)*\\.\\s+.+");

    private final ZBrainProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<Chunk> chunk(String markdownText, Long docId, Long kbId, Integer chunkSize) {
        if (markdownText == null || markdownText.isBlank()) {
            return new ArrayList<>();
        }

        // 子块 Token 大小：优先使用传入的 chunkSize，未传则回退到全局配置
        int effectiveChildTokenSize = (chunkSize != null && chunkSize > 0)
                ? chunkSize : properties.getChunk().getChildTokenSize();

        // ──────────── 预处理：规范化 PDF 解析产生的虚假换行 ────────────
        markdownText = normalizePdfLineBreaks(markdownText);

        List<Chunk> result = new ArrayList<>();

        // ──────────── 第1步：父层递归字符切分 ────────────
        int parentTokenSize = properties.getChunk().getParentTokenSize();
        int parentOverlap = properties.getChunk().getParentOverlap();

        List<String> parentTexts = RecursiveCharacterSplitter.split(
                markdownText, parentTokenSize, parentOverlap);

        log.info("文档 docId={} 父层切分为 {} 个父块 (chunk_size={}, overlap={})",
                docId, parentTexts.size(), parentTokenSize, parentOverlap);

        // ──────────── 第2步：对每个父块子层递归字符切分 ────────────
        int childOverlap = properties.getChunk().getChildOverlap();
        int globalOffset = 0;

        for (int i = 0; i < parentTexts.size(); i++) {
            String parentText = parentTexts.get(i);

            // 创建父块实体
            Chunk parent = new Chunk();
            parent.setDocId(docId);
            parent.setKbId(kbId);
            parent.setChunkType(ChunkType.PARENT.getCode());
            String cleanedParent = cleanContent(parentText);
            parent.setContent(cleanedParent);
            parent.setTokenCount(TokenUtils.countTokens(cleanedParent));
            parent.setStatus(ChunkStatus.DRAFT.getCode());
            parent.setContentVector(null); // 父块不向量化

            Map<String, Object> parentMeta = new HashMap<>();
            parentMeta.put("parent_index", i);
            parentMeta.put("start_index", globalOffset);
            parentMeta.put("end_index", globalOffset + parentText.length());
            parent.setMetadata(toJson(parentMeta));

            result.add(parent);

            // 子层切分（parentId 暂时为 null，由 Service 层插入后回填）
            List<Chunk> children = splitToChildren(
                    parentText, null, docId, kbId, globalOffset,
                    effectiveChildTokenSize, childOverlap);
            result.addAll(children);

            globalOffset += parentText.length();
        }

        log.info("文档 docId={} 总计 {} 个块（父块: {}，子块: {}）",
                docId, result.size(), parentTexts.size(), result.size() - parentTexts.size());

        return result;
    }

    /**
     * 对父块内容进行递归字符切分生成子块（使用默认 overlap）。
     *
     * @param parentContent 父块纯文本内容
     * @param parentId      父块 ID（可为 null，由 Service 层回填）
     * @param docId         文档 ID
     * @param kbId          知识库 ID
     * @param globalOffset  父块在原文中的起始偏移
     * @return 子块列表
     */
    public List<Chunk> splitToChildren(String parentContent, Long parentId, Long docId, Long kbId, int globalOffset) {
        return splitToChildren(parentContent, parentId, docId, kbId, globalOffset, null, null);
    }

    /**
     * 对父块内容进行递归字符切分生成子块（支持自定义子块大小和 overlap）。
     *
     * @param parentContent    父块纯文本内容
     * @param parentId         父块 ID（可为 null，由 Service 层回填）
     * @param docId            文档 ID
     * @param kbId             知识库 ID
     * @param globalOffset     父块在原文中的起始偏移
     * @param childTokenSize   子块 Token 大小（为 null 时使用默认配置）
     * @param childOverlap     子块 Token 重叠（为 null 时使用默认配置）
     * @return 子块列表
     */
    public List<Chunk> splitToChildren(String parentContent, Long parentId, Long docId, Long kbId,
                                       int globalOffset, Integer childTokenSize, Integer childOverlap) {
        List<Chunk> children = new ArrayList<>();
        if (parentContent == null || parentContent.isBlank()) {
            return children;
        }

        int effectiveChildTokenSize = (childTokenSize != null && childTokenSize > 0)
                ? childTokenSize : properties.getChunk().getChildTokenSize();
        int effectiveOverlap = (childOverlap != null && childOverlap >= 0)
                ? childOverlap : properties.getChunk().getChildOverlap();

        // 子层递归字符切分
        List<String> childTexts = RecursiveCharacterSplitter.split(
                parentContent, effectiveChildTokenSize, effectiveOverlap);

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
     * 规范化 PDF 解析产生的虚假换行。
     *
     * <p>PDF 解析器（Tika/LlamaIndex）常在视觉换行处插入换行符，导致一句话被拆成多行。
     * 本方法通过以下策略修复：</p>
     * <ol>
     *   <li>移除独立成行的 URL（PDF 页眉页脚中的链接）</li>
     *   <li>合并段落内的单换行：若行尾不是句末标点（。！？.!?…）且下一行非结构化块，则拼接</li>
     *   <li>合并虚假段落分隔（\n\n）：若上一段不以句末标点结尾且下一段非结构化块，则拼接</li>
     * </ol>
     *
     * <p>保留的格式：Markdown 标题（## ）、表格（| ）、列表（- / * ）、编号标题（2.3.3. ）</p>
     *
     * @param markdown 原始 Markdown 文本
     * @return 规范化后的 Markdown 文本
     */
    private static String normalizePdfLineBreaks(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return markdown;
        }

        // 1. 移除独立成行的 URL（PDF 页眉页脚中的链接）
        String text = URL_LINE_PATTERN.matcher(markdown).replaceAll("");
        // 清理移除 URL 后可能产生的多余空行
        text = MULTI_BLANK_LINES.matcher(text).replaceAll("\n\n");

        // 2. 按双换行拆分为段落
        String[] paragraphs = text.split("\n\n+");

        // 3. 对每个段落：合并段落内的单换行（PDF 视觉换行）
        List<String> normalized = new ArrayList<>();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            if (isStructuralBlock(trimmed)) {
                // 结构化块（标题/表格/列表/编号标题）保持原样
                normalized.add(trimmed);
            } else {
                // 普通段落：合并段内由 PDF 换行产生的单 \n
                normalized.add(joinWrappedLines(trimmed));
            }
        }

        // 4. 合并虚假段落分隔：上一段不以句末标点结尾 + 下一段非结构化块 → 拼接
        List<String> merged = new ArrayList<>();
        for (String para : normalized) {
            if (merged.isEmpty()) {
                merged.add(para);
                continue;
            }
            String prev = merged.get(merged.size() - 1);
            if (!isStructuralBlock(para) && !endsWithSentencePunctuation(prev)) {
                // 上一段不以句末标点结尾 → 当前段是延续，直接拼接
                merged.set(merged.size() - 1, prev + para);
            } else {
                merged.add(para);
            }
        }

        return String.join("\n\n", merged);
    }

    /**
     * 判断段落是否为结构化块（标题、表格、列表），不应合并换行。
     */
    private static boolean isStructuralBlock(String text) {
        String firstLine = text.split("\n", 2)[0].trim();
        if (firstLine.startsWith("#")) return true;                              // Markdown 标题
        if (firstLine.startsWith("|")) return true;                              // Markdown 表格
        if (firstLine.startsWith("- ") || firstLine.startsWith("* ")) return true; // 无序列表
        if (NUMBERED_HEADING_PATTERN.matcher(firstLine).matches()) return true;    // 编号标题 2.3.3.
        return false;
    }

    /** 英文连字符断词模式：行尾以 "字母-" 结尾，下一行以字母开头，如 "nat-\nive" → "native" */
    private static final Pattern HYPHEN_BREAK_PATTERN =
            Pattern.compile("([a-zA-Z])-\\s*\\n\\s*([a-zA-Z])");

    /**
     * 合并段落内的单换行：行尾非句末标点时与下一行直接拼接。
     *
     * <p>PDF 视觉换行产生的 \n 不代表语义断句，应移除。
     * 仅当行尾是句末标点（。！？.!?…）时保留换行，作为自然断句点。</p>
     *
     * <p>额外处理英文连字符断词：PDF 排版常在行尾用 "-" 连接被拆分的单词
     * （如 "nat-\nive"），拼接时移除连字符恢复为完整单词 "native"。</p>
     */
    private static String joinWrappedLines(String paragraph) {
        // 先修复英文连字符断词：letter-\n letter → letter + letter
        String text = HYPHEN_BREAK_PATTERN.matcher(paragraph).replaceAll("$1$2");

        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (result.length() == 0) {
                result.append(trimmed);
            } else if (endsWithSentencePunctuation(result.toString())) {
                // 上一行以句末标点结尾 → 保留换行（自然断句点）
                result.append("\n").append(trimmed);
            } else {
                // 上一行非句末 → PDF 视觉换行，直接拼接
                result.append(trimmed);
            }
        }
        return result.toString();
    }

    /**
     * 判断文本是否以句末标点结尾。
     */
    private static boolean endsWithSentencePunctuation(String text) {
        if (text == null || text.isEmpty()) return false;
        return SENTENCE_END_PUNCT.indexOf(text.charAt(text.length() - 1)) >= 0;
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
