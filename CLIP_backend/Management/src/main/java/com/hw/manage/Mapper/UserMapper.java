package com.hw.manage.Mapper;

import com.hw.pojo.dto.Logindto;
import com.hw.pojo.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Delete("delete from users where username=#{username}")
    public void delete(String name) ;


    @Select("select * from users where username=#{username}")
    public User findByUsername(String username);
    //向数据库中插入注册的信息
    @Insert("insert into users(username,password,phone_num,create_time,update_time,name) values(#{username},#{password},#{phoneNum},#{createTime},#{updateTime},#{name})")
    public void add(User user);
    @Select("select * from users where Id=#{id}")
    public User findById(int id);

}
