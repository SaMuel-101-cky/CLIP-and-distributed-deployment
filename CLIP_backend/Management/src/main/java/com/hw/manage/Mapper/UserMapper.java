package com.hw.manage.Mapper;

import com.hw.pojo.dto.Logindto;
import com.hw.pojo.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Delete("delete from users where username=#{username}")
    public void delete(@Param("username") String username) ;


    @Select("SELECT * FROM users WHERE username = #{username}")
    public User findByUsername(String username);
    //向数据库中插入注册的信息
    @Insert("INSERT INTO users(username, password_hash, phone_num, name) " +
            "VALUES(#{username}, #{passwordHash}, #{phoneNum}, #{name})")
    public void add(User user);
    @Select("SELECT * FROM users WHERE id = #{id}")
    public User findById(Long id);

}
