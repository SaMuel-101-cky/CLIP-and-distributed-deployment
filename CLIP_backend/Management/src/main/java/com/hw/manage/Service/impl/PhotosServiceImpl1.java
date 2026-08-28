package com.hw.manage.Service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.hw.common.utils.PhotosTools;
import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.PhotosService;
import com.hw.pojo.entity.Description;
import com.hw.pojo.entity.Photos;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.PageResult;
import com.hw.pojo.query.Pagequery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
@AllArgsConstructor
@Data
public class PhotosServiceImpl1 implements PhotosService {
  
    private final PhotosMapper photosMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String,String> stringredisTemplate;
    private static final String DELETED_PHOTOS_KEY="deleted_photos";
    private static final int EXPIRATION_DAYS = 30;
    @Transactional(propagation= Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    @Override
    public void deletePhotos(String url) {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();//获取当前用户名
        String username = authentication.getName();
        String imageName= photosMapper.findImageName(url);
       User user = userMapper.findByUsername(username);//读取用户
        List<Photos>photosList= photosMapper.findByImageName(imageName,user.getId());
        String key=DELETED_PHOTOS_KEY+user.getId();
        //先操作数据库再操作Redis因为数据库具有事务性，防止REDIS中有数据，数据库无数据
        photosMapper.deletePhotos(user.getId(),imageName);//删除图片：删除mysql中的图片
        // 使用 有序Set 来存储在Redis中手动创建时间戳之后读取的时候先判断时间戳不符合则删除
        if (photosList != null && !photosList.isEmpty()) {
            // 计算30天后的过期时间戳（毫秒）
            long expirationTimestamp = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(EXPIRATION_DAYS);
            // 使用 ZADD 命令
            // 对于 photosList 中的每一张图片，都以 expirationTimestamp 作为分数存入 Sorted Set
            photosList.forEach(photo -> {
                stringredisTemplate.opsForZSet().add(key, photo.getImage(), expirationTimestamp);
            });
        }

    }
    @Override
    public PageResult<String> listPhotos(Pagequery pagequery)
    {   SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();//获取当前用户名
        if(pagequery.getPage()!=null&&pagequery.getPagesize()!=null)
        {
            PageHelper.startPage(pagequery.getPage(),pagequery.getPagesize());
        }
        else {
            PageHelper.startPage(1,10);//默认从第一页，每页大小为10
        }
        List<String> url=photosMapper.listPhotos(userMapper.findByUsername(authentication.getName()).getId());//获取图片的url链接地址
        Page<String> page= (Page<String>) url;//转换为page对象
        return new PageResult<String>(page.getResult(),page.getTotal());

    }
    @Override
    public List<String>listBinPhotos()throws IOException
    {   SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();//获取当前用户名
        Integer userId=userMapper.findByUsername(authentication.getName()).getId();
        //从redis中获取图片
        String key=DELETED_PHOTOS_KEY+userId;
        List<String>image=new ArrayList<>();
        ZSetOperations<String,String> zSetOps = stringredisTemplate.opsForZSet();//有序集合读取工具类
        // 构建 ScanOptions，count() 指定每次迭代期望获取的元素数量1000
        ScanOptions scanOptions = ScanOptions.scanOptions().count(1000).build();
        // 使用 try-with-resources 确保 Cursor 被关闭
        try (Cursor<ZSetOperations.TypedTuple<String>> cursor = zSetOps.scan(key, scanOptions)) {

            while (cursor.hasNext()) {
                ZSetOperations.TypedTuple<String> tuple = cursor.next();
                String member = tuple.getValue();
                Double score = tuple.getScore();
                if(score!=null&&score>System.currentTimeMillis())
                {
                    image.add(member);//添加图片到链表中
                }
                else
                {
                    log.info("找到过期项目: " + member + "，准备删除...");//即查找时间戳发现过期了
                    // *** 关键：发起一个独立的 ZREM 命令来删除 ***
                    zSetOps.remove(key, member);
                }
            }

        }
        return image;
    }
    @Override
    @Transactional(propagation= Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void deleteBinPhotos(String url) {
       //实现逻辑
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();//获取当前用户名
        Integer userId=userMapper.findByUsername(authentication.getName()).getId();
        //从redis中查找
        String key=DELETED_PHOTOS_KEY+userId;
        ZSetOperations<String,String> zSetOps = stringredisTemplate.opsForZSet();//有序集合读取工具类
        Long removed = zSetOps.remove(key, url);
        //在有序集合中删除 并且从相对路径中删除图片文件
        if (removed != null && removed > 0) {
            log.info("成功删除图片: " + url);
           String relativePath = PhotosTools.parseRelativePath(url);
           PhotosTools.deleteImage(relativePath);
        } else {
            log.info("未找到要删除的图片: " + url);
        }

    }

}
