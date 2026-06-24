package cn.kong.zbrain.chunk.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.kong.zbrain.chunk.ChunkingEngine;
import cn.kong.zbrain.config.ZBrainProperties;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.enums.ChunkStatus;
import cn.kong.zbrain.enums.ChunkType;
import cn.kong.zbrain.util.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认分块引擎实现
 *
 * <p>实现父子分块策略：</p>
 * <ol>
 *   <li>将清洗后的文本按 1000 Token 切分为父块</li>
 *   <li>将父块按 200 Token 切分为子块</li>
 *   <li>子块在 metadata 中记录父块 ID、字符偏移量、Token 数</li>
 * </ol>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultChunkingEngine implements ChunkingEngine {

    private final ZBrainProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<Chunk> chunk(String text, Long docId, Long kbId) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        List<Chunk> result = new ArrayList<>();
        int parentTokenSize = properties.getChunk().getParentTokenSize();
        int childTokenSize = properties.getChunk().getChildTokenSize();
        int overlap = properties.getChunk().getTokenOverlap();

        // 1. 切分为父块
        List<String> parentTexts = TokenUtils.splitByTokenSize(text, parentTokenSize, overlap);
        log.info("文档 docId={} 切分为 {} 个父块", docId, parentTexts.size());

        int globalOffset = 0;
        for (String parentText : parentTexts) {
            // 创建父块
            Chunk parent = new Chunk();
            parent.setDocId(docId);
            parent.setKbId(kbId);
            parent.setChunkType(ChunkType.PARENT.getCode());
            parent.setContent(parentText);
            parent.setTokenCount(TokenUtils.countTokens(parentText));
            parent.setStatus(ChunkStatus.DRAFT.getCode());
            parent.setContentVector(null); // 父块不向量化

            Map<String, Object> parentMeta = new HashMap<>();
            parentMeta.put("start_index", globalOffset);
            parentMeta.put("end_index", globalOffset + parentText.length());
            parent.setMetadata(toJson(parentMeta));

            result.add(parent);

            // 2. 切分子块（父块 ID 暂时为 null，由 Service 层插入后回填）
            List<Chunk> children = splitToChildren(parentText, null, docId, kbId, globalOffset);
            result.addAll(children);

            globalOffset += parentText.length();
        }

        return result;
    }

    @Override
    public List<Chunk> splitToChildren(String parentContent, Long parentId, Long docId, Long kbId, int startOffset) {
        List<Chunk> children = new ArrayList<>();
        if (parentContent == null || parentContent.isBlank()) {
            return children;
        }

        int childTokenSize = properties.getChunk().getChildTokenSize();
        int overlap = properties.getChunk().getTokenOverlap();

        List<String> childTexts = TokenUtils.splitByTokenSize(parentContent, childTokenSize, overlap);
        int localOffset = 0;

        for (String childText : childTexts) {
            Chunk child = new Chunk();
            child.setDocId(docId);
            child.setKbId(kbId);
            child.setParentId(parentId);
            child.setChunkType(ChunkType.CHILD.getCode());
            child.setContent(childText);
            child.setTokenCount(TokenUtils.countTokens(childText));
            child.setStatus(ChunkStatus.DRAFT.getCode());

            Map<String, Object> meta = new HashMap<>();
            meta.put("start_index", startOffset + localOffset);
            meta.put("end_index", startOffset + localOffset + childText.length());
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

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("序列化 metadata 失败: {}", e.getMessage());
            return "{}";
        }
    }
}
