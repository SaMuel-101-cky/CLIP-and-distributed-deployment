package com.hw.manage.Mapper;

import com.hw.pojo.entity.Description;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface DescriptionMapper {
    @Insert("INSERT INTO description(content, create_time, update_time, user_id, idNum) " +
            "VALUES(#{content}, #{createTime}, #{updateTime}, #{userId}, #{idNum})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Description description);

    @Select("SELECT * FROM description WHERE user_id = #{userId} AND idNum = #{idNum} ORDER BY id ASC")
    List<Description> findByUserAndIdNum(Integer userId, Integer idNum);
}