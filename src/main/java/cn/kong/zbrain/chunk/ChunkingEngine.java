package cn.kong.zbrain.chunk;

import cn.kong.zbrain.entity.Chunk;

import java.util.List;

/**
 * 分块引擎接口
 *
 * <p>支持父子分块（Parent-Child）策略：
 * <ul>
 *   <li>父层递归字符切分（1000 Token，重叠 150）</li>
 *   <li>子层递归字符切分（300 Token，重叠 40）</li>
 *   <li>子块记录父块 ID 及字符偏移量</li>
 * </ul>
 * </p>
 *
 * @author zbrain-team
 */
public interface ChunkingEngine {

    /**
     * 对文本进行父子分块
     *
     * @param text      清洗后的文本
     * @param docId     文档 ID
     * @param kbId      知识库 ID
     * @param chunkSize 子块 Token 大小（为 null 时使用默认配置）
     * @return 分块列表（父块在前，子块在后）
     */
    List<Chunk> chunk(String text, Long docId, Long kbId, Integer chunkSize);

    /**
     * 合并多个分块内容
     *
     * @param chunks 待合并的分块列表
     * @return 合并后的内容
     */
    String merge(List<Chunk> chunks);

    /**
     * 拆分单个分块
     *
     * @param chunk         原分块
     * @param splitPosition 拆分位置（字符偏移）
     * @return 拆分后的两个分块
     */
    List<Chunk> split(Chunk chunk, int splitPosition);
}
