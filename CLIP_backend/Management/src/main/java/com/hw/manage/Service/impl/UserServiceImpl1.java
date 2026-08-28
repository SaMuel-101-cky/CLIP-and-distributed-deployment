package com.hw.manage.Service.impl;

import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.UserService;
import com.hw.pojo.dto.Descriptiondto;
import com.hw.pojo.entity.Description;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.Sequery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
@Slf4j
@Data
public class UserServiceImpl1 implements UserService {
    //对密码进行加密存储
    private final PasswordEncoder passwordEncoder;
    private UserMapper userMapper;
    private RedisTemplate<String,Object> redisTemplate;
    private PhotosMapper photosMapper;
    // 定义Redis中Key的前缀，未匹配的描述 方便管理
    private static final String MATCH_CACHE_KEY_PREFIX = "match:";
    //定义Redis中Key的前缀，获得匹配好的结果
    private static final String MATCHRESULT_CACHE_KEY_PREFIX = "match_result:";
    @Transactional(propagation= Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    @Override
    public void changeuserInfo(User userInfo) {
        //逻辑是先删除数据库原本的数据然后将修改后的数据添加上去
        String name = userInfo.getUsername();
        log.info("修改用户的账户名：{}", name);
        LocalDateTime createTime = userMapper.findByUsername(name).getCreateTime();//保留创造时间属性
        userMapper.delete(name);
        log.info("删除用户账户名：{}", name);
        userInfo.setUpdateTime(LocalDateTime.now());
        userInfo.setCreateTime(createTime);
        String encodedPassword = passwordEncoder.encode(userInfo.getPassword());//对密码进行加密存储
        userInfo.setPassword(encodedPassword);
        userMapper.add(userInfo);
        log.info("添加用户账户名：{}", name);
        log.info("修改用户账户名成功");
    }
    @Override
    public Integer uploadmatch(Descriptiondto descriptiondto)
    {   log.info("上传匹配信息");
        log.info("查找最大的idNum");
        Integer idNum=photosMapper.findMaxIdNum(descriptiondto.getUsername())+1;
        //将匹配信息存储到redis中
        String cacheKey = MATCH_CACHE_KEY_PREFIX + descriptiondto.getUsername()+ ":" + idNum;//KEY值
        redisTemplate.opsForValue().set(cacheKey, descriptiondto.getDescription());
        //将描述写入数据库
        Integer userId = userMapper.findByUsername(descriptiondto.getUsername()).getId();
        Description description =new Description(descriptiondto.getDescription(), LocalDateTime.now(), LocalDateTime.now(), userId,null,idNum);
        photosMapper.addone(description);
       log.info("上传匹配信息成功");
       return idNum;
    }
    @Override
    public List<String> downloadmatch(Sequery sequery)throws Exception
    {   log.info("开始下载匹配信息");
        String cacheKey = MATCHRESULT_CACHE_KEY_PREFIX + sequery.getUsername()+ ":" + sequery.getIdNum();
        Object obj = redisTemplate.opsForValue().get(cacheKey);
        //判断是否是字符串类型
        if (!(obj instanceof List<?> rawList)) {
            throw new Exception("结果还没处理好");
        }
        // 检查是否所有元素都能被当作字符串
        if (rawList.stream().anyMatch(item -> !(item == null || item instanceof String))) {
            System.err.println("转换失败：List 中包含非 String 类型的元素。");
            return null;
        }
        // 进行转换
        return  rawList.stream()
                .map(item -> (String) item)
                .toList();
    }

}
