package cn.kong.zbrain.retrieval;

import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.mapper.ChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 全文检索器
 *
 * <p>基于 zhparser 中文分词的全文检索，Top 20。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FullTextRetriever implements Retriever {

    private final ChunkMapper chunkMapper;

    @Override
    public List<RetrievalResult> retrieve(Long kbId, String query, int topK) {
        try {
            List<Chunk> chunks = chunkMapper.fulltextRetrieve(kbId, query, topK);

            List<RetrievalResult> results = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Chunk chunk = chunks.get(i);
                RetrievalResult result = new RetrievalResult();
                result.setChunkId(chunk.getId());
                result.setParentId(chunk.getParentId());
                result.setContent(chunk.getContent());
                result.setDocId(chunk.getDocId());
                result.setMetadata(chunk.getMetadata());
                result.setScore(1.0 / (i + 1));
                result.setSources(List.of(name()));
                results.add(result);
            }
            return results;
        } catch (Exception e) {
            log.error("全文检索失败: kbId={}, query={}", kbId, query, e);
            return new ArrayList<>();
        }
    }

    @Override
    public String name() {
        return "fulltext";
    }
}
