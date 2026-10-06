package com.hw.manage.Mapper;

import com.hw.pojo.entity.Photos;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PhotosMapper {
    @Update("UPDATE photos SET status = 'DELETED', deleted_at = NOW(), " +
            "delete_expire_at = DATE_ADD(NOW(), INTERVAL 30 DAY) " +
            "WHERE user_id = #{userId} AND access_url = #{accessUrl} AND status = 'ACTIVE'")
    void softDeleteByAccessUrl(@Param("userId") Long userId, @Param("accessUrl") String accessUrl);

    @Delete("DELETE FROM photos WHERE user_id = #{userId} AND access_url = #{accessUrl} AND status = 'DELETED'")
    void hardDeleteByAccessUrl(@Param("userId") Long userId, @Param("accessUrl") String accessUrl);

    @Select("SELECT access_url FROM photos WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY created_at DESC")
    List<String> listPhotos(Long userId);

    @Select("SELECT * FROM photos WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY created_at DESC")
    List<Photos> listActivePhotoRecords(Long userId);

    @Select({
            "<script>",
            "SELECT * FROM photos WHERE user_id = #{userId} AND status = 'ACTIVE' AND id IN",
            "<foreach collection='photoIds' item='photoId' open='(' separator=',' close=')'>#{photoId}</foreach>",
            "ORDER BY created_at DESC",
            "</script>"
    })
    List<Photos> listActivePhotoRecordsByIds(@Param("userId") Long userId,
                                             @Param("photoIds") List<Long> photoIds);

    @Select("SELECT access_url FROM photos WHERE user_id = #{userId} AND status = 'DELETED' ORDER BY deleted_at DESC")
    List<String> listDeletedPhotos(Long userId);

    @Select("SELECT * FROM photos WHERE user_id = #{userId} AND access_url = #{accessUrl}")
    Photos findByAccessUrl(@Param("userId") Long userId, @Param("accessUrl") String accessUrl);

    @Insert("INSERT INTO photos(user_id, storage_path, access_url, original_name, stored_name, " +
            "content_hash, mime_type, size_bytes, status) " +
            "VALUES(#{userId}, #{storagePath}, #{accessUrl}, #{originalName}, #{storedName}, " +
            "#{contentHash}, #{mimeType}, #{sizeBytes}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Photos photos);

    @Select("SELECT storage_path FROM photos WHERE id = #{id}")
    String findImagePathById(Long id);

    @Select("SELECT count(*) FROM photos WHERE id = #{photoId} AND user_id = #{userId} AND status = 'ACTIVE'")
    Integer countActiveByUserAndId(@Param("userId") Long userId, @Param("photoId") Long photoId);

}
