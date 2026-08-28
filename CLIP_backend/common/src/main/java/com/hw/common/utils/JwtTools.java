package com.hw.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.beans.BeanProperty;
import java.util.Map;


public class JwtTools {

    public static Claims parseToken(String token) throws Exception {

        // 使用传入的token参数而不是硬编码的令牌
        return  Jwts.parser()
                .setSigningKey("kjson108241")
                .parseClaimsJws(token)
                .getBody();

    }
    public static String create(Map<String,Object> claims)
    {
        return Jwts.builder().signWith(SignatureAlgorithm.HS256,"kjson108241").setClaims(claims)
                .setExpiration(new java.util.Date(System.currentTimeMillis()+12*60*60*1000))
                .compact();
    }

}
