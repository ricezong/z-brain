package cn.kong.zbrain.parser;

import cn.kong.zbrain.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * 文档解析器（基于 Apache Tika）
 *
 * <p>支持 PDF / Word / PPT / Excel / TXT / Markdown / HTML 等多种格式，
 * 解析为纯文本并进行基础清洗。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
public class DocumentParser {

    /** Tika 最大字符数限制（-1 表示无限制） */
    private static final int MAX_CHARACTERS = -1;

    /** 多余空行正则 */
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    /** 行首尾空白正则 */
    private static final Pattern LINE_TRIM = Pattern.compile("(?m)^\\s+|\\s+$");

    /** 常见页眉页脚模式 */
    private static final Pattern HEADER_FOOTER =
            Pattern.compile("(?m)^第[\\d一二三四五六七八九十百千]+页.*$|^Page \\d+.*$|^\\d+\\s*$");

    /** 乱码控制字符 */
    private static final Pattern CONTROL_CHARS =
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    /**
     * 解析文档为纯文本
     *
     * @param inputStream 文件输入流
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
     * 文本清洗
     * <ul>
     *   <li>去除乱码控制字符</li>
     *   <li>去除常见页眉页脚</li>
     *   <li>合并多余空行</li>
     *   <li>去除行首尾多余空白</li>
     * </ul>
     */
    public String cleanText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }
        String text = rawText;
        // 1. 去除控制字符
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        // 2. 去除页眉页脚
        text = HEADER_FOOTER.matcher(text).replaceAll("");
        // 3. 合并多余空行
        text = MULTI_BLANK_LINES.matcher(text).replaceAll("\n\n");
        // 4. 去除行首尾空白
        text = LINE_TRIM.matcher(text).replaceAll("");
        return text.trim();
    }
}
