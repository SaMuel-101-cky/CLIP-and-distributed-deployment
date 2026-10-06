package com.hw.manage.Mapper;

import com.hw.pojo.entity.EmbeddingRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EmbeddingRecordMapper {
    @Insert("INSERT INTO embedding_records(user_id, target_type, target_id, embedding_model, vector_db, collection_name, vector_id, dim, status) " +
            "VALUES(#{userId}, #{targetType}, #{targetId}, #{embeddingModel}, #{vectorDb}, #{collectionName}, #{vectorId}, #{dim}, #{status}) " +
            "ON DUPLICATE KEY UPDATE vector_db = VALUES(vector_db), collection_name = VALUES(collection_name), " +
            "vector_id = VALUES(vector_id), dim = VALUES(dim), status = VALUES(status), updated_at = CURRENT_TIMESTAMP")
    void upsert(EmbeddingRecord record);

    @Select("SELECT count(*) FROM embedding_records WHERE user_id = #{userId} AND embedding_model = #{embeddingModel} AND status = 'READY'")
    Integer countReadyByUserAndModel(@Param("userId") Long userId,
                                     @Param("embeddingModel") String embeddingModel);

    @Select("""
            <script>
            SELECT * FROM embedding_records
            WHERE user_id = #{userId}
            <if test='status != null and status != ""'>AND status = #{status}</if>
            <if test='embeddingModel != null and embeddingModel != ""'>AND embedding_model = #{embeddingModel}</if>
            ORDER BY updated_at DESC, id DESC
            </script>
            """)
    List<EmbeddingRecord> listByUserId(@Param("userId") Long userId,
                                        @Param("status") String status,
                                        @Param("embeddingModel") String embeddingModel);

    @Select("""
            <script>
            SELECT count(*) FROM embedding_records
            WHERE user_id = #{userId}
            <if test='status != null and status != ""'>AND status = #{status}</if>
            <if test='embeddingModel != null and embeddingModel != ""'>AND embedding_model = #{embeddingModel}</if>
            </script>
            """)
    Long countByUserId(@Param("userId") Long userId,
                       @Param("status") String status,
                       @Param("embeddingModel") String embeddingModel);
}
