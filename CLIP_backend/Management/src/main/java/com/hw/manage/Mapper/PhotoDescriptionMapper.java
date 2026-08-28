package com.hw.manage.Mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface PhotoDescriptionMapper {
    // 根据描述ID查匹配的图片ID
    @Select("SELECT photo_id FROM photo_description WHERE description_id = #{descriptionId}")
    List<Integer> findPhotoIdsByDescriptionId(Integer descriptionId);

    // 检查某批次是否有结果（通过连接查询判断 description 表中该用户的该批次是否在 photo_description 中有记录）
    @Select("SELECT count(*) FROM photo_description pd " +
            "LEFT JOIN description d ON pd.description_id = d.id " +
            "WHERE d.user_id = #{userId} AND d.idNum = #{idNum}")
    Integer countMatchesByBatch(Integer userId, Integer idNum);
}