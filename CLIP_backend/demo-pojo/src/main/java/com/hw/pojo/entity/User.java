package com.hw.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class User {
    String name;//用户名
    String username;//账户名
    String password;
    String phoneNum;
    LocalDateTime createTime;
    LocalDateTime updateTime;
    Integer id;//主键id
}
