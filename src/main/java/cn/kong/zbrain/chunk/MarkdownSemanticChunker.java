package cn.kong.zbrain.chunk;

import cn.kong.zbrain.util.TokenUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 Markdown 语法的语义边界父块切分器。
 *
 * <p>第1步：将 Markdown 文档按语义边界切分为父块（默认 512-2048 tokens），
 * 保证不在标题内部、表格内部拆分，维持语义完整性。</p>
 *
 * <p>切分策略优先级：</p>
 * <ol>
 *   <li>按 Markdown 标题（{@code ## / ###}）切分 → 每个标题段落视为候选父块</li>
 *   <li>无标题时 → 按段落边界 + 滑动窗口合并为目标大小</li>
 *   <li>兜底 → 固定 token 窗口硬切（仅在上述方法均失败时）</li>
 * </ol>
 *
 * <h3>约束规则</h3>
 * <ul>
 *   <li>绝不在标题内部拆分</li>
 *   <li>绝不在 Markdown 表格内部拆分</li>
 *   <li>某段落自身超过最大 token 数 → 单独作为父块</li>
 *   <li>两个标题之间内容太短（&lt; minTokens）→ 合并到上一个父块</li>
 * </ul>
 *
 * <p>父块 token 约束可通过 {@link #chunkToParents(String, int, int)} 传入，
 * 也可使用默认值 {@link #MIN_PARENT_TOKENS} / {@link #MAX_PARENT_TOKENS}。</p>
 *
 * @author zbrain-team
 */
@Slf4j
public final class MarkdownSemanticChunker {

    /** 父块默认最小 token 数 */
    private static final int MIN_PARENT_TOKENS = 512;
    /** 父块默认最大 token 数 */
    private static final int MAX_PARENT_TOKENS = 2048;

    // ──────── Markdown 语法正则 ────────

    /** 匹配 Markdown 标题行：## / ### ... 标题文本 */
    private static final Pattern MD_HEADING_PATTERN =
            Pattern.compile("^(#{2,6})\\s+(.+)$", Pattern.MULTILINE);

    /** 匹配 Markdown 表格：以 | 开头和结尾的行属于表格 */
    private static final Pattern MD_TABLE_ROW_PATTERN =
            Pattern.compile("^\\|.+\\|\\s*$", Pattern.MULTILINE);

    /** 匹配表头分隔行：|---|...---| */
    private static final Pattern MD_TABLE_SEPARATOR_PATTERN =
            Pattern.compile("^\\|\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$", Pattern.MULTILINE);

    // ──────── 内部数据结构 ────────

    /**
     * Markdown 语义片段
     */
    static class Segment {
        enum Type { HEADING, TABLE, PARAGRAPH }
        final Type type;
        final String text;         // 纯文本内容（Markdown 语法已剥离）
        final int startIndex;      // 在原文中的字符起始位置
        final int endIndex;        // 在原文中的字符结束位置
        final int tokenCount;
        final String rawLine;      // 原始行（保留 Markdown 标记用于 heading 层级判断）

        Segment(Type type, String text, int startIndex, int endIndex, String rawLine) {
            this.type = type;
            this.text = text;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.tokenCount = TokenUtils.countTokens(text);
            this.rawLine = rawLine;
        }
    }

    /**
     * 父块切分结果
     */
    public static class ParentChunk {
        /** 父块唯一标识（在文档内的序号，1-based） */
        public final int index;
        /** 父块纯文本内容 */
        public final String content;
        /** 原始 Markdown 中起始位置 */
        public final int startOffset;
        /** 原始 Markdown 中结束位置 */
        public final int endOffset;
        /** 实际 token 数 */
        public final int tokenCount;
        /** 该父块包含的语义片段 */
        public final List<Segment> segments;
        /** 标题层级（## / ###），无则为 null */
        public final String headingLevel;

        ParentChunk(int index, String content, int startOffset, int endOffset,
                    List<Segment> segments, String headingLevel) {
            this.index = index;
            this.content = content;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.tokenCount = TokenUtils.countTokens(content);
            this.segments = Collections.unmodifiableList(segments);
            this.headingLevel = headingLevel;
        }
    }

    private MarkdownSemanticChunker() {}

    /**
     * 对 Markdown 文本执行语义父块切分（使用默认 token 约束）。
     *
     * @param markdown Markdown 格式文本
     * @return 父块列表（按原文顺序）
     */
    public static List<ParentChunk> chunkToParents(String markdown) {
        return chunkToParents(markdown, MIN_PARENT_TOKENS, MAX_PARENT_TOKENS);
    }

    /**
     * 对 Markdown 文本执行语义父块切分（可指定 token 约束）。
     *
     * @param markdown  Markdown 格式文本
     * @param minTokens 父块最小 token 数
     * @param maxTokens 父块最大 token 数
     * @return 父块列表（按原文顺序）
     */
    public static List<ParentChunk> chunkToParents(String markdown, int minTokens, int maxTokens) {
        if (markdown == null || markdown.isBlank()) {
            return Collections.emptyList();
        }

        // 1. 提取所有语义片段
        List<Segment> segments = extractSegments(markdown);

        // 2. 检测是否有标题
        boolean hasHeadings = segments.stream().anyMatch(s -> s.type == Segment.Type.HEADING);

        if (hasHeadings) {
            // 策略1：按 Markdown 标题层级切分
            return chunkByHeadings(segments, minTokens, maxTokens);
        } else {
            // 策略2：按段落边界 + 滑动窗口合并
            return chunkByParagraphs(segments, minTokens, maxTokens);
        }
    }

    // ======================== 片段提取 ========================

    /**
     * 从 Markdown 文本中按顺序提取语义片段。
     *
     * <p>处理顺序：标题 → 表格 → 段落。</p>
     */
    private static List<Segment> extractSegments(String md) {
        List<Segment> segments = new ArrayList<>();

        // 按空行拆分为段落块
        String[] blocks = md.split("\n\n+");
        int pos = 0;

        for (String block : blocks) {
            if (block.isBlank()) {
                // 跳过纯空白块（pos 推进：空行分隔符长度计入偏移）
                // 实际用 indexOf 精确跟踪
                continue;
            }

            int blockStart = md.indexOf(block, pos);
            if (blockStart < 0) blockStart = pos;
            int blockEnd = blockStart + block.length();

            String trimmed = block.trim();

            // 检测标题
            Matcher headingMatcher = MD_HEADING_PATTERN.matcher(trimmed);
            if (headingMatcher.find()) {
                String level = headingMatcher.group(1); // "##" / "###" …
                String headingText = headingMatcher.group(2).trim();
                segments.add(new Segment(Segment.Type.HEADING, headingText,
                        blockStart, blockEnd, level + " " + headingText));
                pos = blockEnd;
                continue;
            }

            // 检测表格（以 |---| 分隔行或 |col|col| 行开始）
            if (isTableBlock(trimmed)) {
                // 剥离 Markdown 表格语法，保留纯文本
                String plainText = stripTableMarkdown(trimmed);
                segments.add(new Segment(Segment.Type.TABLE, plainText,
                        blockStart, blockEnd, trimmed));
                pos = blockEnd;
                continue;
            }

            // 普通段落
            segments.add(new Segment(Segment.Type.PARAGRAPH, trimmed,
                    blockStart, blockEnd, trimmed));
            pos = blockEnd;
        }

        return segments;
    }

    /**
     * 判断一个文本块是否为 Markdown 表格。
     */
    private static boolean isTableBlock(String block) {
        // 表格至少有2行，且含 |---| 分隔行
        String[] lines = block.split("\n");
        boolean hasSeparator = false;
        boolean hasDataRow = false;
        for (String line : lines) {
            String t = line.trim();
            if (MD_TABLE_SEPARATOR_PATTERN.matcher(t).matches()) {
                hasSeparator = true;
            } else if (MD_TABLE_ROW_PATTERN.matcher(t).matches()) {
                hasDataRow = true;
            }
        }
        return hasSeparator && hasDataRow;
    }

    /**
     * 剥离 Markdown 表格语法，提取纯文本（保留列对齐的空白）。
     */
    private static String stripTableMarkdown(String block) {
        StringBuilder sb = new StringBuilder();
        String[] lines = block.split("\n");
        for (String line : lines) {
            String t = line.trim();
            // 跳过分隔行 |---|
            if (MD_TABLE_SEPARATOR_PATTERN.matcher(t).matches()) {
                continue;
            }
            if (MD_TABLE_ROW_PATTERN.matcher(t).matches()) {
                // 提取 | col1 | col2 | 中的纯文本
                String clean = t.replaceAll("^\\|\\s*", "")
                               .replaceAll("\\s*\\|\\s*$", "")
                               .replaceAll("\\s*\\|\\s*", " | ");
                sb.append(clean).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // ======================== 策略1：按标题切分 ========================

    /**
     * 策略1：以 Markdown 标题（##/###等）为边界切分父块。
     */
    private static List<ParentChunk> chunkByHeadings(List<Segment> segments,
                                                      int minTokens, int maxTokens) {
        // 先按标题边界分组
        List<List<Segment>> sections = new ArrayList<>();
        List<Segment> currentSection = new ArrayList<>();

        for (Segment seg : segments) {
            if (seg.type == Segment.Type.HEADING) {
                // 当前段内容太少（< minTokens），合并到上一个段
                if (!currentSection.isEmpty()) {
                    int sectionTokens = currentSection.stream().mapToInt(s -> s.tokenCount).sum();
                    if (sectionTokens < minTokens && !sections.isEmpty()) {
                        // 合并到上一个 section
                        sections.get(sections.size() - 1).addAll(currentSection);
                    } else {
                        sections.add(currentSection);
                    }
                }
                currentSection = new ArrayList<>();
            }
            currentSection.add(seg);
        }
        // 最后一段：如果太小，合并到上一个 section
        if (!currentSection.isEmpty()) {
            int sectionTokens = currentSection.stream().mapToInt(s -> s.tokenCount).sum();
            if (sectionTokens < minTokens && !sections.isEmpty()) {
                sections.get(sections.size() - 1).addAll(currentSection);
            } else {
                sections.add(currentSection);
            }
        }

        // 按父块大小约束构建父块
        List<ParentChunk> parents = new ArrayList<>();
        int parentIndex = 0;

        for (List<Segment> section : sections) {
            // 提取 section 的标题层级
            String headingLevel = null;
            for (Segment seg : section) {
                if (seg.type == Segment.Type.HEADING) {
                    headingLevel = seg.rawLine != null
                            ? seg.rawLine.split("\\s+")[0] // "##" / "###"
                            : null;
                    break;
                }
            }

            parentIndex++;
            parents.addAll(buildParentsFromSegments(section, parentIndex, headingLevel, minTokens, maxTokens));
        }

        return parents;
    }

    // ======================== 策略2：按段落合并 ========================

    /**
     * 策略2：无标题时按段落边界 + 滑动窗口合并。
     */
    private static List<ParentChunk> chunkByParagraphs(List<Segment> segments,
                                                        int minTokens, int maxTokens) {
        List<ParentChunk> parents = new ArrayList<>();
        List<Segment> currentSegments = new ArrayList<>();
        int currentTokens = 0;
        int parentIndex = 0;

        for (Segment seg : segments) {
            // 大段落特殊处理
            if (seg.tokenCount > maxTokens) {
                if (!currentSegments.isEmpty()) {
                    parentIndex++;
                    parents.add(buildParent(currentSegments, parentIndex, null));
                    currentSegments.clear();
                    currentTokens = 0;
                }
                parentIndex++;
                parents.add(buildParent(Collections.singletonList(seg), parentIndex, null));
                continue;
            }

            // 表格不可拆分：要么全在当前块，要么全在下一块
            boolean isTable = (seg.type == Segment.Type.TABLE);
            if (isTable && currentTokens + seg.tokenCount > maxTokens) {
                if (!currentSegments.isEmpty()) {
                    parentIndex++;
                    parents.add(buildParent(currentSegments, parentIndex, null));
                    currentSegments.clear();
                    currentTokens = 0;
                }
                parentIndex++;
                parents.add(buildParent(Collections.singletonList(seg), parentIndex, null));
                continue;
            }

            // 超过上限且已有内容 → 提交当前块，开启新块
            if (currentTokens > 0 && currentTokens + seg.tokenCount > maxTokens) {
                parentIndex++;
                parents.add(buildParent(currentSegments, parentIndex, null));
                currentSegments.clear();
                currentTokens = 0;
            }

            currentSegments.add(seg);
            currentTokens += seg.tokenCount;
        }

        // 最后一段：如果太小，合并到上一个父块
        if (!currentSegments.isEmpty()) {
            if (currentTokens < minTokens && !parents.isEmpty()) {
                ParentChunk prev = parents.remove(parents.size() - 1);
                List<Segment> mergedSegs = new ArrayList<>(prev.segments);
                mergedSegs.addAll(currentSegments);
                parents.add(buildParent(mergedSegs, prev.index, prev.headingLevel));
            } else {
                parentIndex++;
                parents.add(buildParent(currentSegments, parentIndex, null));
            }
        }

        return parents;
    }

    // ======================== 策略3：兜底固定 token 窗口 ========================

    /**
     * 兜底：固定 token 窗口切分（使用默认最大 token 数）。
     */
    public static List<ParentChunk> chunkByFixedWindow(String text) {
        return chunkByFixedWindow(text, MAX_PARENT_TOKENS);
    }

    /**
     * 兜底：固定 token 窗口切分（可指定最大 token 数）。
     *
     * @param text      文本
     * @param maxTokens 每个父块最大 token 数
     * @return 父块列表
     */
    public static List<ParentChunk> chunkByFixedWindow(String text, int maxTokens) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<ParentChunk> parents = new ArrayList<>();
        List<String> chunks = TokenUtils.splitByTokenSize(text, maxTokens, 0);
        int offset = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            int endOff = offset + chunk.length();
            Segment seg = new Segment(Segment.Type.PARAGRAPH, chunk, offset, endOff, chunk);
            parents.add(buildParent(Collections.singletonList(seg), i + 1, null));
            offset = endOff;
        }
        return parents;
    }

    // ======================== 父块构建辅助 ========================

    private static List<ParentChunk> buildParentsFromSegments(List<Segment> segments,
                                                               int startIndex, String headingLevel,
                                                               int minTokens, int maxTokens) {
        List<ParentChunk> result = new ArrayList<>();
        int totalTokens = segments.stream().mapToInt(s -> s.tokenCount).sum();

        if (totalTokens <= maxTokens || segments.size() == 1) {
            result.add(buildParent(segments, startIndex, headingLevel));
        } else {
            result.addAll(splitOversizedSegments(segments, startIndex, headingLevel, minTokens, maxTokens));
        }
        return result;
    }

    private static List<ParentChunk> splitOversizedSegments(List<Segment> segments,
                                                              int startIndex, String headingLevel,
                                                              int minTokens, int maxTokens) {
        List<ParentChunk> result = new ArrayList<>();
        List<Segment> batch = new ArrayList<>();
        int batchTokens = 0;
        int idx = startIndex;

        for (Segment seg : segments) {
            if (seg.type == Segment.Type.HEADING && !batch.isEmpty()) {
                // 仅当 batch 达到最小 token 数，或加入标题后会超限时才提交
                // 否则保留 batch，让标题加入当前 batch（避免产生过小的父块）
                if (batchTokens >= minTokens || batchTokens + seg.tokenCount > maxTokens) {
                    result.add(buildParent(batch, idx++, headingLevel));
                    batch = new ArrayList<>();
                    batchTokens = 0;
                }
            }
            if (seg.type == Segment.Type.TABLE) {
                if (batchTokens + seg.tokenCount > maxTokens && !batch.isEmpty()) {
                    result.add(buildParent(batch, idx++, headingLevel));
                    batch = new ArrayList<>();
                    batchTokens = 0;
                }
            }
            if (seg.type == Segment.Type.PARAGRAPH && seg.tokenCount > maxTokens
                    && batch.isEmpty()) {
                result.add(buildParent(Collections.singletonList(seg), idx++, headingLevel));
                continue;
            }
            if (batchTokens + seg.tokenCount > maxTokens && !batch.isEmpty()) {
                result.add(buildParent(batch, idx++, headingLevel));
                batch = new ArrayList<>();
                batchTokens = 0;
            }
            batch.add(seg);
            batchTokens += seg.tokenCount;
        }
        if (!batch.isEmpty()) {
            // 末尾 batch 太小 → 合并到上一个父块
            if (batchTokens < minTokens && !result.isEmpty()) {
                ParentChunk prev = result.remove(result.size() - 1);
                List<Segment> mergedSegs = new ArrayList<>(prev.segments);
                mergedSegs.addAll(batch);
                result.add(buildParent(mergedSegs, prev.index, headingLevel));
            } else {
                result.add(buildParent(batch, idx, headingLevel));
            }
        }
        return result;
    }

    private static ParentChunk buildParent(List<Segment> segments, int index, String headingLevel) {
        int start = segments.get(0).startIndex;
        int end = segments.get(segments.size() - 1).endIndex;
        String content = segments.stream().map(s -> s.text).collect(Collectors.joining("\n\n"));
        return new ParentChunk(index, content, start, end, new ArrayList<>(segments), headingLevel);
    }
}
