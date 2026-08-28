package com.hw.manage.Service;

import com.hw.pojo.dto.Logindto;
import com.hw.pojo.dto.Registerdto;
import com.hw.pojo.vo.Loginvo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public interface LoginService  {
    //登录
    public Loginvo login(Logindto logindto)throws Exception;
    //注册
    public void register(Registerdto dto)throws Exception;
    //发送验证码
    public void sendmsg(String phone);
}
