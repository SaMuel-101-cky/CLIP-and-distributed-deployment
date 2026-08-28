package com.hw.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Registerdto {
    private String username;
    private String password;
    private String phoneNum;//可以绑定手机号码便于找回密码
    private String verificationCode;//后续有时间拓展的短信验证功能TODO
}
