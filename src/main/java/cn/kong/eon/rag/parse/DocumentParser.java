package cn.kong.eon.rag.parse;

import cn.kong.eon.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 文档解析器（基于 Apache Tika）
 *
 * <p>支持 PDF / Word / PPT / Excel / TXT / Markdown / HTML 等格式的自动识别解析。
 * 解析后输出纯文本或 Markdown，保留标题层级与表格结构。</p>
 *
 * <p>两条解析路径：</p>
 * <ul>
 *   <li>{@link #parseToMarkdown(InputStream)}：Tika HTML 输出 + JSoup 转换为 Markdown，保留结构</li>
 *   <li>{@link #parse(InputStream)}：Tika 纯文本输出，快速降级方案</li>
 * </ul>
 *
 * @author eon-team
 */
@Slf4j
@Component
public class DocumentParser {

    /** Tika 最大字符限制（-1 表示不限制） */
    private static final int MAX_CHARACTERS = -1;

    /** 多空行合并正则 */
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    /** 行首尾空白裁剪正则 */
    private static final Pattern LINE_TRIM = Pattern.compile("(?m)^\\s+|\\s+$");

    /** 页眉页脚清除正则 */
    private static final Pattern HEADER_FOOTER =
            Pattern.compile("(?m)^第?\\d+页.*$|^Page \\d+.*$|^\\d+\\s*$");

    /** 控制字符清除正则 */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /**
     * 解析文档为纯文本（快速降级方案）
     *
     * @param inputStream 文档输入流
     * @return 清洗后的纯文本
     */
    public String parse(InputStream inputStream) {
        try {
            BodyContentHandler handler = new BodyContentHandler(MAX_CHARACTERS);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            Parser parser = new AutoDetectParser();

            parser.parse(inputStream, handler, metadata, context);
            String rawText = handler.toString();

            return cleanText(rawText);
        } catch (IOException | TikaException | SAXException e) {
            log.error("文档解析失败", e);
            throw new BusinessException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析文档为 Markdown（保留结构化信息）
     *
     * <p>使用 Tika ToHTMLContentHandler 输出 HTML，再通过 JSoup 转换为 Markdown，
     * 保留标题层级（##）、表格（|）等结构化信息。
     * 由 {@code ChunkingEngine} 按结构化 Markdown 切分，提升分块质量。</p>
     *
     * <p>当结构化解析失败时自动降级为纯文本模式。如 PDF 换行连字符修复等
     * 由 Markdown 转换器内部处理，确保分块后语义完整。</p>
     *
     * @param inputStream 文档输入流
     * @return Markdown 格式文本
     */
    public String parseToMarkdown(InputStream inputStream) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ToHTMLContentHandler handler = new ToHTMLContentHandler(baos, StandardCharsets.UTF_8.name());
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            Parser parser = new AutoDetectParser();

            parser.parse(inputStream, handler, metadata, context);
            String html = baos.toString(StandardCharsets.UTF_8);

            // 调用 HtmlToMarkdownConverter 将 HTML 转为 Markdown
            return HtmlToMarkdownConverter.convert(html);
        } catch (IOException | TikaException | SAXException e) {
            log.warn("Markdown 解析失败，降级为纯文本: {}", e.getMessage());
            throw new BusinessException("解析 Markdown 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 文本清洗
     * <ul>
     *   <li>移除控制字符</li>
     *   <li>移除页眉页脚</li>
     *   <li>合并多空行</li>
     *   <li>裁剪行首尾空白</li>
     * </ul>
     */
    public String cleanText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        String text = rawText;
        // 1. 移除控制字符
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        // 2. 移除页眉页脚
        text = HEADER_FOOTER.matcher(text).replaceAll("");
        // 3. 合并多空行
        text = MULTI_BLANK_LINES.matcher(text).replaceAll("\n\n");
        // 4. 裁剪行首尾空白
        text = LINE_TRIM.matcher(text).replaceAll("");
        return text.trim();
    }
}
