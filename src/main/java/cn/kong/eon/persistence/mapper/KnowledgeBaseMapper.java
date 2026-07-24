package cn.kong.eon.persistence.mapper;

import cn.kong.eon.persistence.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 鐭ヨ瘑搴?Mapper
 *
 * @author eon-team
 */
@Mapper
public interface KnowledgeBaseMapper {

    int insert(KnowledgeBase kb);

    int update(KnowledgeBase kb);

    int deleteById(@Param("id") Long id);

    KnowledgeBase selectById(@Param("id") Long id);

    List<KnowledgeBase> selectList(@Param("name") String name,
                                   @Param("category") String category,
                                   @Param("status") String status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countList(@Param("name") String name,
                   @Param("category") String category,
                   @Param("status") String status);

    int updateDocCount(@Param("id") Long id, @Param("delta") int delta);

    int updateChunkCount(@Param("id") Long id, @Param("delta") int delta);
}

