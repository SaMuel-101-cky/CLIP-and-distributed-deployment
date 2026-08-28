package com.hw.manage.Service.impl;

import cn.hutool.jwt.Claims;
import com.hw.common.utils.JwtTools;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.LoginService;
import com.hw.pojo.dto.Logindto;
import com.hw.pojo.dto.Registerdto;
import com.hw.pojo.entity.User;
import com.hw.pojo.vo.Loginvo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Data
//登录注册服务集成在loginservice
public class LoginServiceimpl1 implements LoginService {
    private final UserMapper userMapper;
    //验证用户信息
    @Autowired
    private final AuthenticationManager authenticationManager;
    //对密码进行加密存储
    private final PasswordEncoder passwordEncoder;
    //调用用户信息服务
    @Autowired
    private UserDetailsService userDetailsService;

   // todo

   public Loginvo login(Logindto logindto) throws Exception{
       final UserDetails userDetails = userDetailsService.loadUserByUsername(logindto.getUsername());
       try {
           // 使用 AuthenticationManager 进行用户认证
           authenticationManager.authenticate(
                   new UsernamePasswordAuthenticationToken(logindto.getUsername(), logindto.getPassword())
           );
       } catch (BadCredentialsException e) {
           log.error("用户名或密码错误: {}", e.getMessage());
          throw new Exception("用户名或密码错误");
       }
       log.info("用户登录成功");
       Map<String, Object> claims = new HashMap<>();
       claims.put("sub", userDetails.getUsername());//将用户名封装到claim的sub字段中
       String token = JwtTools.create(claims);//创建token返回给前端
       Loginvo loginvo = new Loginvo();
       loginvo.setToken(token);
       loginvo.setUsername(userDetails.getUsername());
       return loginvo;
   }
   public void register(Registerdto registerdto) throws Exception{
       while( userMapper.findByUsername(registerdto.getUsername()) != null)
       {
           throw new Exception("用户已存在");
       }
       User user=new User();
       user.setUsername(registerdto.getUsername());
       user.setPhoneNum(registerdto.getPhoneNum());
       user.setCreateTime(LocalDateTime.now());
       user.setUpdateTime(LocalDateTime.now());
       //对密码进行加密存储
       String encodedPassword = passwordEncoder.encode(registerdto.getPassword());
       user.setPassword(encodedPassword);
       userMapper.add(user);
       log.info("用户注册成功");
   }
   public void sendmsg(String phone)
   {

   }
}
