package cn.kong.zbrain.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 递归字符分块器（Recursive Character Text Splitter）
 *
 * <p>按分隔符优先级递归降级切分，尽量在自然语义边界断开，避免把句子/段落从中间斩断。
 * 切分流程：</p>
 * <ol>
 *   <li>用最高优先级分隔符（如 {@code \n\n}）把文本切成片段；</li>
 *   <li>对每个片段，若 token 数 ≤ 目标大小则保留，否则降级到下一级分隔符继续递归切；</li>
 *   <li>所有分隔符都用尽后仍超长，则按 token 强制硬切作为兜底；</li>
 *   <li>最后把切出的语义片段按目标 token 大小聚合，并用滑动窗口维护 overlap。</li>
 * </ol>
 *
 * <p>分隔符优先级（中文优先）：{@code \n\n → \n → 。→ ！→ ？→ ；→ . → ! → ? → ; → 空格 → 强制切}。
 * 切分时分隔符保留在片段末尾，聚合后语义自然连贯。</p>
 *
 * <p>token 计数基于 jtokkit 的 cl100k_base 编码，与 GPT/Qwen 系列模型接近，比"1 token≈2 字符"
 * 的粗估准确得多。</p>
 *
 * @author zbrain-team
 */
public final class RecursiveCharacterSplitter {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    /**
     * 默认分隔符优先级（中文优先）。
     * 末尾的空字符串表示"已无更细分隔符，兜底强制按 token 切"。
     */
    private static final List<String> DEFAULT_SEPARATORS = Arrays.asList(
            "\n\n", "\n", "。", "！", "？", "；", ".", "!", "?", ";", " ", ""
    );

    /** 标点符号集合（用于 forceSplit 兜底切分时回退到句子边界） */
    private static final String PUNCTUATION = "。！？；，.,!?;:";

    private RecursiveCharacterSplitter() {
    }

    /**
     * 按目标 token 大小递归切分文本，带 overlap。
     *
     * @param text            原始文本
     * @param targetTokenSize 每个分块的目标 token 上限
     * @param overlapTokens   相邻分块的重叠 token 数（滑动窗口）
     * @return 切分后的文本列表
     */
    public static List<String> split(String text, int targetTokenSize, int overlapTokens) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        if (targetTokenSize <= 0) {
            throw new IllegalArgumentException("targetTokenSize 必须大于 0");
        }
        if (overlapTokens < 0 || overlapTokens >= targetTokenSize) {
            throw new IllegalArgumentException("overlapTokens 必须在 [0, targetTokenSize) 范围内");
        }

        // 1. 递归切出语义片段（不含 overlap，保证语义边界干净）
        List<String> splits = recursiveSplit(text, targetTokenSize, DEFAULT_SEPARATORS, 0);
        // 2. 按目标大小聚合并维护 overlap 滑动窗口
        return mergeSplits(splits, targetTokenSize, overlapTokens);
    }

    /**
     * 递归切分：用当前分隔符切，超长片段降级到下一级分隔符。
     */
    private static List<String> recursiveSplit(String text, int targetSize, List<String> separators, int sepIdx) {
        List<String> result = new ArrayList<>();

        // 整段已足够小，直接返回
        if (countTokens(text) <= targetSize) {
            result.add(text);
            return result;
        }

        // 已是最后一级（空字符串分隔符），兜底强制按 token 切
        if (sepIdx >= separators.size()) {
            return forceSplitByTokens(text, targetSize);
        }

        String separator = separators.get(sepIdx);
        List<String> parts = splitPreservingSeparator(text, separator);

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (countTokens(part) <= targetSize) {
                result.add(part);
            } else {
                // 当前片段仍超长，降级到下一级分隔符递归切
                result.addAll(recursiveSplit(part, targetSize, separators, sepIdx + 1));
            }
        }
        return result;
    }

    /**
     * 按分隔符切分，分隔符保留在每段末尾（语义自然，聚合时无需重新拼接）。
     * 空分隔符作为兜底信号，由上层走 forceSplitByTokens，此处不处理。
     */
    private static List<String> splitPreservingSeparator(String text, String separator) {
        if (separator.isEmpty()) {
            // 不应被调用到这里，recursiveSplit 会先走 forceSplitByTokens 兜底
            return Collections.singletonList(text);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        int sepLen = separator.length();
        int idx;
        while ((idx = text.indexOf(separator, start)) != -1) {
            parts.add(text.substring(start, idx + sepLen));
            start = idx + sepLen;
        }
        if (start < text.length()) {
            parts.add(text.substring(start));
        }
        return parts;
    }

    /**
     * 兜底：所有语义分隔符都无法切到目标大小以下时，按 token 强制切分。
     *
     * <p>回退策略：取 targetSize 个 token 对应的文本后，向前查找最近的标点符号，
     * 在标点处截断（包含标点），避免从句子中间截断。只要找到标点符号就在标点处切分，
     * 不做距离限制；仅当整段文本完全无标点符号时，才兜底硬切。</p>
     */
    private static List<String> forceSplitByTokens(String text, int targetSize) {
        List<String> result = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            IntArrayList tokens = ENCODING.encode(remaining);
            if (tokens.size() <= targetSize) {
                result.add(remaining);
                break;
            }
            // 取前 targetSize 个 token 解码为文本（是 remaining 的前缀）
            IntArrayList chunkTokens = new IntArrayList(targetSize);
            for (int j = 0; j < targetSize; j++) {
                chunkTokens.add(tokens.get(j));
            }
            String chunkText = ENCODING.decode(chunkTokens);

            // 向前查找最近的标点符号，在标点处截断
            int cutPos = findLastPunctuation(chunkText);
            if (cutPos > 0) {
                // 找到标点符号 → 在标点处截断（包含标点），不硬切
                result.add(chunkText.substring(0, cutPos + 1));
                remaining = remaining.substring(cutPos + 1);
            } else {
                // 整段无标点符号 → 兜底硬切
                result.add(chunkText);
                remaining = remaining.substring(chunkText.length());
            }
        }
        return result;
    }

    /**
     * 在文本中从后向前查找最近的标点符号位置。
     *
     * @param text 文本
     * @return 最后一个标点符号的索引，无标点返回 -1
     */
    private static int findLastPunctuation(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (PUNCTUATION.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 聚合语义片段到目标 token 大小，并用滑动窗口维护 overlap。
     *
     * <p>overlap 实现：提交当前块后，取其末尾 overlapTokens 个 token 对应的文本作为下一块的起始，
     * 这样相邻块在边界处有重叠，避免检索时因切片边界丢失上下文。</p>
     */
    private static List<String> mergeSplits(List<String> splits, int targetSize, int overlapTokens) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentTokens = 0;

        for (String split : splits) {
            int splitTokens = countTokens(split);

            // 当前块已有内容，且加入新片段会超限 → 提交当前块，开启新块（带 overlap）
            if (currentTokens > 0 && currentTokens + splitTokens > targetSize) {
                String chunk = current.toString();
                result.add(chunk);

                if (overlapTokens > 0) {
                    String tail = tailByTokens(chunk, overlapTokens);
                    current = new StringBuilder(tail);
                    currentTokens = countTokens(tail);
                } else {
                    current = new StringBuilder();
                    currentTokens = 0;
                }
            }

            current.append(split);
            currentTokens += splitTokens;
        }

        if (currentTokens > 0) {
            result.add(current.toString());
        }
        return result;
    }

    /**
     * 取文本末尾 maxTokens 个 token 对应的字符串（用于 overlap 滑动窗口）。
     *
     * <p>向前查找最近的标点符号，从标点之后的字符开始截取作为 overlap，
     * 标点本身保留在前一个块中。这样下一子块的 overlap 从句子开头开始，
     * 避免从句子中间截断，也不会在分块开头出现孤立的标点符号。
     * 向前查找不受距离限制，直到找到标点符号或到达文本起始位置。</p>
     */
    private static String tailByTokens(String text, int maxTokens) {
        IntArrayList tokens = ENCODING.encode(text);
        if (tokens.size() <= maxTokens) {
            return text;
        }
        int from = tokens.size() - maxTokens;
        IntArrayList tail = new IntArrayList(maxTokens);
        for (int j = from; j < tokens.size(); j++) {
            tail.add(tokens.get(j));
        }
        String tailStr = ENCODING.decode(tail);

        // 尝试在原文中定位 tailStr 的起始位置，向前查找最近的标点符号
        int tailPos = text.lastIndexOf(tailStr);
        if (tailPos > 0) {
            for (int i = tailPos - 1; i >= 0; i--) {
                if (PUNCTUATION.indexOf(text.charAt(i)) >= 0) {
                    // 标点保留在前一个块，overlap 从标点之后开始
                    return text.substring(i + 1);
                }
            }
        }

        return tailStr;
    }

    private static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }
}
