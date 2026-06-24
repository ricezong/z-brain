package cn.kong.zbrain.retrieval;

import cn.kong.zbrain.dto.response.RetrievalResult;
import cn.kong.zbrain.entity.Chunk;
import cn.kong.zbrain.mapper.ChunkMapper;
import cn.kong.zbrain.config.ZBrainProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模糊检索器
 *
 * <p>基于 pg_trgm 的模糊匹配，Top 10。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuzzyRetriever implements Retriever {

    private final ChunkMapper chunkMapper;
    private final ZBrainProperties properties;

    @Override
    public List<RetrievalResult> retrieve(Long kbId, String query, int topK) {
        try {
            double threshold = properties.getRetrieval().getFuzzyThreshold();
            List<Chunk> chunks = chunkMapper.fuzzyRetrieve(kbId, query, threshold, topK);

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
            log.error("模糊检索失败: kbId={}, query={}", kbId, query, e);
            return new ArrayList<>();
        }
    }

    @Override
    public String name() {
        return "fuzzy";
    }
}
