package com.hw.manage.Service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.hw.manage.Mapper.PhotosMapper;
import com.hw.manage.Mapper.UserMapper;
import com.hw.manage.Service.PhotosService;
import com.hw.manage.Service.storage.FileStorageService;
import com.hw.pojo.entity.Photos;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.PageResult;
import com.hw.pojo.query.Pagequery;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;


@Slf4j
@Service
@AllArgsConstructor
@Data
public class PhotosServiceImpl1 implements PhotosService {
  
    private final PhotosMapper photosMapper;
    private final UserMapper userMapper;
    private final FileStorageService fileStorageService;

    @Transactional(propagation= Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    @Override
    public void deletePhotos(String url) {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();//获取当前用户名
        String username = authentication.getName();
        User user = userMapper.findByUsername(username);//读取用户
        photosMapper.softDeleteByAccessUrl(user.getId(), url);

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
        Long userId=userMapper.findByUsername(authentication.getName()).getId();
        return photosMapper.listDeletedPhotos(userId);
    }
    @Override
    @Transactional(propagation= Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void deleteBinPhotos(String url) {
       //实现逻辑
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();//获取当前用户名
        Long userId=userMapper.findByUsername(authentication.getName()).getId();
        Photos photo = photosMapper.findByAccessUrl(userId, url);
        if (photo != null && "DELETED".equals(photo.getStatus())) {
            fileStorageService.delete(photo.getStoragePath());
            photosMapper.hardDeleteByAccessUrl(userId, url);
        }

    }

}
