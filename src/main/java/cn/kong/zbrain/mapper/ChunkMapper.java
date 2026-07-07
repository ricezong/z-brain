package cn.kong.zbrain.mapper;

import cn.kong.zbrain.entity.Chunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分块 Mapper（核心 Mapper）
 *
 * <p>包含向量相似度检索、全文检索等复杂 SQL。</p>
 *
 * @author zbrain-team
 */
@Mapper
public interface ChunkMapper {

    int insert(Chunk chunk);

    int batchInsert(@Param("list") List<Chunk> chunks);

    int update(Chunk chunk);

    int updateContent(@Param("id") Long id, @Param("content") String content, @Param("tokenCount") Integer tokenCount);

    int updateVector(@Param("id") Long id, @Param("vector") String vector);

    int batchUpdateVector(@Param("list") List<Chunk> chunks);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    int updateParentId(@Param("id") Long id, @Param("parentId") Long parentId);

    int deleteById(@Param("id") Long id);

    int deleteByDocId(@Param("docId") Long docId);

    Chunk selectById(@Param("id") Long id);

    List<Chunk> selectByDocId(@Param("docId") Long docId);

    /**
     * 查询文档下指定状态的子块
     */
    List<Chunk> selectChildChunksByDocId(@Param("docId") Long docId, @Param("status") String status);

    /**
     * 查询文档下需要向量化的子块（status=draft 或内容变更）
     */
    List<Chunk> selectChunksForEmbedding(@Param("docId") Long docId);

    /**
     * 查询父块（用于上下文组装）
     */
    Chunk selectParent(@Param("parentId") Long parentId);

    /**
     * 向量召回：基于向量相似度（余弦距离）召回 Top K
     *
     * @param kbId      知识库 ID
     * @param vectorStr 查询向量字符串 "[0.1,0.2,...]"
     * @param topK      召回数量
     * @return 召回的分块列表
     */
    List<Chunk> vectorRetrieve(@Param("kbId") Long kbId,
                               @Param("vectorStr") String vectorStr,
                               @Param("topK") int topK);

    /**
     * 全文召回：基于 zhparser 中文分词的全文检索
     */
    List<Chunk> fulltextRetrieve(@Param("kbId") Long kbId,
                                 @Param("query") String query,
                                 @Param("topK") int topK);

    /**
     * 统计文档的分块数量
     */
    int countByDocId(@Param("docId") Long docId);
}
