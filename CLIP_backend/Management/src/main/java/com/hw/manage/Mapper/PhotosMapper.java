package com.hw.manage.Mapper;

import com.hw.pojo.entity.Description;
import com.hw.pojo.entity.Photos;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface PhotosMapper {
     void addAll(List<Description> descriptionList);//链表写入
    @Insert(" insert into description(content,create_time,update_time,user_id,idNum) values(#{content},#{createTime},#{updateTime},#{userId},#{idNum})")
    void addone(Description description);//单个写入
    @Delete(" delete from photos where user_id=#{userId} and image_name=#{imageName}")
    void deletePhotos(Integer userId, String imageName);

    List<String> listPhotos(Integer userId);//通过用户id获取图片

    @Select(" select * from photos where user_id=#{userId} and  image_name=#{imageName}")
    List<Photos> findByImageName(String imageName,Integer userId);

    Integer findMaxIdNum(String username);
    @Select(" select image_name from photos where image= #{url}")
    String findImageName(String url);

    @Insert("INSERT INTO photos(image, create_time, user_id, image_name, image_uuid, idNum) " +
            "VALUES(#{image}, #{createTime}, #{userId}, #{imageName}, #{imageUuid}, #{idNum})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Photos photos);

    @Select("SELECT image FROM photos WHERE id = #{id}")
    String findImagePathById(Integer id);

}
