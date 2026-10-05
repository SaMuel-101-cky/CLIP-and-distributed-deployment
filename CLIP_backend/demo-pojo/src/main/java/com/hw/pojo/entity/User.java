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
    String passwordHash;
    String phoneNum;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Long id;//主键id

    public String getPassword() {
        return passwordHash;
    }

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    public LocalDateTime getCreateTime() {
        return createdAt;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createdAt = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updatedAt;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updatedAt = updateTime;
    }
}
