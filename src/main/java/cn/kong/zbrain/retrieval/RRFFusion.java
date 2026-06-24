package cn.kong.zbrain.retrieval;

import cn.kong.zbrain.dto.response.RetrievalResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF (Reciprocal Rank Fusion) 倒数秩融合算法
 *
 * <p>对多路召回结果去重并加权融合，公式：score = 1 / (k + rank)</p>
 * <p>其中 k 为平滑常数（默认 60），rank 为该结果在单路召回中的排名（从 1 开始）。</p>
 *
 * @author zbrain-team
 */
@Slf4j
@Component
public class RRFFusion {

    /**
     * 执行 RRF 融合
     *
     * @param retrievalResults 多路召回结果（每一路一个列表）
     * @param k                平滑常数（默认 60）
     * @param topK             返回 Top K
     * @return 融合后的结果列表（按综合得分降序）
     */
    public List<RetrievalResult> fuse(List<List<RetrievalResult>> retrievalResults, int k, int topK) {
        if (retrievalResults == null || retrievalResults.isEmpty()) {
            return new ArrayList<>();
        }

        // 以 chunkId 为 Key 聚合
        Map<Long, RetrievalResult> merged = new HashMap<>();
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, List<String>> sourcesMap = new HashMap<>();

        for (List<RetrievalResult> singleList : retrievalResults) {
            if (singleList == null) {
                continue;
            }
            for (int rank = 0; rank < singleList.size(); rank++) {
                RetrievalResult item = singleList.get(rank);
                Long chunkId = item.getChunkId();
                if (chunkId == null) {
                    continue;
                }

                // RRF 得分：1 / (k + rank)，rank 从 1 开始
                double rrfScore = 1.0 / (k + rank + 1);
                scores.merge(chunkId, rrfScore, Double::sum);

                // 保留首次出现的结果作为基础
                merged.putIfAbsent(chunkId, item);

                // 合并来源
                sourcesMap.computeIfAbsent(chunkId, x -> new ArrayList<>()).add(item.getSources().get(0));
            }
        }

        // 组装最终结果
        List<RetrievalResult> result = new ArrayList<>();
        for (Map.Entry<Long, RetrievalResult> entry : merged.entrySet()) {
            Long chunkId = entry.getKey();
            RetrievalResult item = entry.getValue();
            item.setScore(scores.get(chunkId));
            item.setSources(sourcesMap.get(chunkId));
            result.add(item);
        }

        // 按得分降序排序，取 Top K
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (result.size() > topK) {
            result = new ArrayList<>(result.subList(0, topK));
        }

        log.debug("RRF 融合完成: 输入路数={}, 输出数量={}", retrievalResults.size(), result.size());
        return result;
    }
}
