package cn.kong.zbrain.parser;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 JSoup 的 HTML → Markdown 格式归一化转换器。
 *
 * <p>将 Tika {@code ToHTMLContentHandler} 输出的 HTML 按标签语义主动转写为
 * 标准 Markdown，统一整个下游流水线的输入格式。</p>
 *
 * <p>转换策略（按文档顺序遍历 DOM）：</p>
 * <ul>
 *   <li>{@code <h1>～<h6>} → {@code # ～ ######} 标题</li>
 *   <li>{@code <p>} → 检测编号标题模式（如 "2.1. 线程"）转为 Markdown 标题，
 *       否则作为普通段落；同时过滤 PDF 页眉页脚</li>
 *   <li>{@code <table>} → {@code | col | col |} 表格（含表头分隔线）</li>
 *   <li>{@code <ul>/<ol>} → {@code - } 无序 / 有序列表</li>
 *   <li>{@code <div>} → 递归处理子元素</li>
 *   <li>其余内联标签（{@code <b>/<i>/<a>}）保留纯文本内容</li>
 * </ul>
 *
 * <p>PDF 特殊处理：</p>
 * <ul>
 *   <li>过滤页眉页脚（如 "13/04/2018 Page 20 of 283"）</li>
 *   <li>跳过 PDF 书签目录树（{@code <body>} 直接子级的 {@code <ul>}）</li>
 *   <li>识别 {@code <p>} 中的编号标题（如 "2.1. 线程"）并转为 Markdown 标题</li>
 * </ul>
 *
 * @author zbrain-team
 */
@Slf4j
public final class HtmlToMarkdownConverter {

    /**
     * 编号标题模式：匹配 "2.1. 线程"、"2.2.1. 程序计数器" 等。
     * <p>要求至少两级编号（如 2.1），避免误匹配 "1. 列表项" 等编号列表。
     * <p>分组1=编号部分（如 "2.1"），分组2=标题文本。
     */
    private static final Pattern HEADING_NUM_PATTERN =
            Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)*)\\.\\s*(.+)");

    /**
     * 页眉页脚模式：匹配 "13/04/2018  Page 20 of 283"、"Page X of Y"、
     * "第X页" 等。
     */
    private static final Pattern PAGE_HEADER_FOOTER_PATTERN =
            Pattern.compile(
                    "\\d{2}/\\d{2}/\\d{4}\\s+Page\\s+\\d+\\s+of\\s+\\d+" // 13/04/2018 Page 20 of 283
                    + "|^Page\\s+\\d+\\s+of\\s+\\d+"                    // Page 20 of 283
                    + "|^第[\\d一二三四五六七八九十百千]+页"               // 第20页
            );

    /** 多余空行正则 */
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    private HtmlToMarkdownConverter() {}

    /**
     * 将 HTML 文本转换为 Markdown（按文档顺序遍历）。
     *
     * @param html 原始 HTML 字符串
     * @return 标准 Markdown 字符串
     */
    public static String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parse(html);
        Element body = doc.body();
        if (body == null) {
            return "";
        }

        StringBuilder md = new StringBuilder();
        walkChildren(body, md);

        // 合并多余空行
        String result = MULTI_BLANK_LINES.matcher(md.toString()).replaceAll("\n\n");
        return result.trim();
    }

    // ======================== DOM 遍历 ========================

    /**
     * 按文档顺序遍历父元素的子元素并逐个转换。
     *
     * @param parent 父元素
     * @param md     输出缓冲区
     */
    private static void walkChildren(Element parent, StringBuilder md) {
        for (Element child : parent.children()) {
            processElement(child, md);
        }
    }

    /**
     * 根据标签类型处理单个元素。
     */
    private static void processElement(Element element, StringBuilder md) {
        String tagName = element.tagName().toLowerCase();

        switch (tagName) {
            case "h1": case "h2": case "h3":
            case "h4": case "h5": case "h6":
                appendHeading(element.text().trim(),
                        Integer.parseInt(tagName.substring(1)), md);
                break;

            case "p":
                processParagraph(element, md);
                break;

            case "table":
                String tableMd = convertTableToMarkdown(element);
                if (!tableMd.isBlank()) {
                    md.append(tableMd).append("\n\n");
                }
                break;

            case "ul":
                // 跳过 PDF 书签目录树（<body> 的直接子级 <ul>）
                if (!isPdfBookmarkList(element)) {
                    convertList(element, md, false);
                }
                break;

            case "ol":
                convertList(element, md, true);
                break;

            case "div":
                // 递归处理 div 内的子元素（如 <div class="page">）
                walkChildren(element, md);
                break;

            case "br":
            case "hr":
                md.append("\n");
                break;

            default:
                // 其他标签：有子元素则递归，否则提取文本
                if (element.children().isEmpty()) {
                    String text = element.text().trim();
                    if (!text.isEmpty()) {
                        md.append(text).append("\n\n");
                    }
                } else {
                    walkChildren(element, md);
                }
        }
    }

    // ======================== 段落处理 ========================

    /**
     * 处理 {@code <p>} 标签：过滤页眉页脚、检测编号标题、输出普通段落。
     */
    private static void processParagraph(Element p, StringBuilder md) {
        String text = p.text().trim();
        if (text.isEmpty()) {
            return;
        }

        // 1. 过滤页眉页脚
        if (isPageHeaderFooter(text)) {
            return;
        }

        // 2. 检测编号标题（如 "2.1. 线程"、"2.2.1. 程序计数器"）
        int headingLevel = detectHeadingLevel(text);
        if (headingLevel > 0) {
            appendHeading(text, headingLevel, md);
        } else {
            // 3. 普通段落
            md.append(text).append("\n\n");
        }
    }

    // ======================== 标题处理 ========================

    /**
     * 输出 Markdown 标题。
     *
     * @param text  标题文本（含编号，如 "2.1. 线程"）
     * @param level 标题层级（1-6）
     * @param md    输出缓冲区
     */
    private static void appendHeading(String text, int level, StringBuilder md) {
        if (text.isEmpty()) {
            return;
        }
        int clampedLevel = Math.min(Math.max(level, 1), 6);
        md.append("#".repeat(clampedLevel)).append(" ").append(text).append("\n\n");
    }

    /**
     * 检测文本是否为编号标题，返回 Markdown 标题层级（2-6），非标题返回 0。
     *
     * <p>编号深度与标题层级映射：</p>
     * <ul>
     *   <li>"2.1." → 2 级编号 → Markdown 层级 3 ({@code ###})</li>
     *   <li>"2.2.1." → 3 级编号 → Markdown 层级 4 ({@code ####})</li>
     *   <li>"2.3.1.1." → 4 级编号 → Markdown 层级 5 ({@code #####})</li>
     * </ul>
     *
     * @param text 待检测文本
     * @return Markdown 标题层级（2-6），非标题返回 0
     */
    private static int detectHeadingLevel(String text) {
        Matcher m = HEADING_NUM_PATTERN.matcher(text);
        if (m.matches()) {
            String numbers = m.group(1);          // "2.1" or "2.2.1"
            int depth = numbers.split("\\.").length; // 2 or 3
            int level = depth + 1;                   // 3 or 4
            return level <= 6 ? level : 0;
        }
        return 0;
    }

    // ======================== 页眉页脚过滤 ========================

    /**
     * 判断文本是否为 PDF 页眉页脚。
     */
    private static boolean isPageHeaderFooter(String text) {
        return PAGE_HEADER_FOOTER_PATTERN.matcher(text).find();
    }

    // ======================== PDF 书签检测 ========================

    /**
     * 判断 {@code <ul>} 是否为 PDF 书签目录树。
     *
     * <p>Tika 解析 PDF 时，会在 {@code <body>} 末尾追加一个包含所有书签的
     * {@code <ul>} 元素。该元素是 {@code <body>} 的直接子级，
     * 不在任何 {@code <div class="page">} 内部。</p>
     *
     * @param ul 待检测的 {@code <ul>} 元素
     * @return true 表示是 PDF 书签，应跳过
     */
    private static boolean isPdfBookmarkList(Element ul) {
        Element parent = ul.parent();
        return parent != null && "body".equalsIgnoreCase(parent.tagName());
    }

    // ======================== 列表转换 ========================

    /**
     * 将 {@code <ul>/<ol>} 转换为 Markdown 列表。
     *
     * @param list    列表元素
     * @param md      输出缓冲区
     * @param ordered 是否为有序列表
     */
    private static void convertList(Element list, StringBuilder md, boolean ordered) {
        Elements items = list.select("li");
        int idx = 1;
        for (Element li : items) {
            String text = li.text().trim();
            if (!text.isEmpty()) {
                String prefix = ordered ? (idx++ + ". ") : "- ";
                md.append(prefix).append(text).append("\n");
            }
        }
        md.append("\n");
    }

    // ======================== 表格转换 ========================

    /**
     * 将 HTML {@code <table>} 元素转为 Markdown 表格。
     *
     * <p>简化版：不处理 colspan/rowspan 合并单元格。Tika 输出的 HTML 表格
     * 通常已展开所有单元格，直接按行列提取即可。</p>
     */
    private static String convertTableToMarkdown(Element table) {
        StringBuilder sb = new StringBuilder();
        Elements rows = table.select("tr");
        boolean isFirstRow = true;

        for (Element row : rows) {
            Elements cells = row.select("td, th");
            if (cells.isEmpty()) {
                continue;
            }

            sb.append("| ");
            for (Element cell : cells) {
                // 转义 Markdown 表格中的竖线，避免破坏列边界
                String text = cell.text().replace("|", "\\|").trim();
                sb.append(text).append(" | ");
            }
            sb.append("\n");

            // 第一行后添加表头分隔线
            if (isFirstRow) {
                for (int i = 0; i < cells.size(); i++) {
                    sb.append("| --- ");
                }
                sb.append("|\n");
                isFirstRow = false;
            }
        }

        return sb.toString();
    }
}
