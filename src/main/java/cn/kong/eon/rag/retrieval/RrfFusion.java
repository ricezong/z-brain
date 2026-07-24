package cn.kong.eon.rag.retrieval;

import cn.kong.eon.persistence.dto.response.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * RRF (Reciprocal Rank Fusion) 倒数秩融合算法
 *
 * <p>公式：score = 1 / (k + rank)，k 默认 60（原论文推荐值）。</p>
 *
 * @author eon-team
 */
@Slf4j
@Component
public class RrfFusion {

    /**
     * 执行 RRF 融合
     *
     * @param retrievalResults 多路召回结果
     * @param k                平滑常数（默认 60）
     * @param topK             返回 Top K
     */
    public List<RetrievalResult> fuse(List<List<RetrievalResult>> retrievalResults, int k, int topK) {
        if (retrievalResults == null || retrievalResults.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, RetrievalResult> merged = new HashMap<>();
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, List<String>> sourcesMap = new HashMap<>();

        for (List<RetrievalResult> singleList : retrievalResults) {
            if (singleList == null) continue;
            for (int rank = 0; rank < singleList.size(); rank++) {
                RetrievalResult item = singleList.get(rank);
                Long chunkId = item.getChunkId();
                if (chunkId == null) continue;

                double rrfScore = 1.0 / (k + rank + 1);
                scores.merge(chunkId, rrfScore, Double::sum);
                merged.putIfAbsent(chunkId, item);
                sourcesMap.computeIfAbsent(chunkId, x -> new ArrayList<>())
                        .addAll(item.getSources() != null ? item.getSources() : List.of());
            }
        }

        List<RetrievalResult> result = new ArrayList<>();
        for (Map.Entry<Long, RetrievalResult> entry : merged.entrySet()) {
            Long chunkId = entry.getKey();
            RetrievalResult item = entry.getValue();
            item.setScore(scores.get(chunkId));
            item.setSources(sourcesMap.get(chunkId));
            result.add(item);
        }

        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (result.size() > topK) {
            result = new ArrayList<>(result.subList(0, topK));
        }

        log.debug("RRF 融合: 输入路数={}, 输出={}", retrievalResults.size(), result.size());
        return result;
    }
}
