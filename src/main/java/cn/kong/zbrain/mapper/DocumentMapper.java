package cn.kong.zbrain.mapper;

import cn.kong.zbrain.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档 Mapper
 *
 * @author zbrain-team
 */
@Mapper
public interface DocumentMapper {

    int insert(Document document);

    int update(Document document);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("errorMessage") String errorMessage);

    int updateParseProgress(@Param("id") Long id, @Param("progress") int progress);

    int updateChunkCount(@Param("id") Long id, @Param("chunkCount") int chunkCount);

    int deleteById(@Param("id") Long id);

    Document selectById(@Param("id") Long id);

    List<Document> selectByKbId(@Param("kbId") Long kbId, @Param("status") String status);

    List<Document> selectList(@Param("kbId") Long kbId,
                              @Param("fileName") String fileName,
                              @Param("status") String status,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long countList(@Param("kbId") Long kbId,
                   @Param("fileName") String fileName,
                   @Param("status") String status);

    /**
     * 查询待向量化的文档（status = embedding）
     */
    List<Document> selectPendingEmbedding(@Param("limit") int limit);
}
