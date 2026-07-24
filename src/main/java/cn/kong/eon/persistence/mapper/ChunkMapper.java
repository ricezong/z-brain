package cn.kong.eon.persistence.mapper;

import cn.kong.eon.persistence.entity.Chunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 分块 Mapper（核心 Mapper）
 *
 * <p>包含向量检索、全文检索等复杂 SQL。
 * ★ 新工程瘦查询：检索 SQL 不回传 content_vector/tsv 大字段。</p>
 *
 * @author eon-team
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

    List<Chunk> selectChildChunksByDocId(@Param("docId") Long docId, @Param("status") String status);

    List<Chunk> selectChunksForEmbedding(@Param("docId") Long docId);

    Chunk selectParent(@Param("parentId") Long parentId);

    /**
     * ★ 瘦向量召回：只 SELECT id, parent_id, content, doc_id, metadata（不碰 content_vector/tsv）
     */
    List<Map<String, Object>> vectorRetrieveSkinny(@Param("kbId") Long kbId,
                                                     @Param("vectorStr") String vectorStr,
                                                     @Param("topK") int topK);

    /**
     * ★ 瘦全文召回：只 SELECT id, parent_id, content, doc_id, metadata
     */
    List<Map<String, Object>> fulltextRetrieveSkinny(@Param("kbId") Long kbId,
                                                      @Param("query") String query,
                                                      @Param("topK") int topK);

    /**
     * ★ 批量回填 parent.content + document.file_name（一条 JOIN，消灭 N+1）
     */
    List<Map<String, Object>> batchFillParentAndDoc(@Param("chunkIds") List<Long> chunkIds);

    int countByDocId(@Param("docId") Long docId);
}
