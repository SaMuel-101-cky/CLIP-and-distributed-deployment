package com.hw.manage.Controller;

import com.hw.manage.Service.PhotosService;
import com.hw.pojo.query.Pagequery;
import com.hw.pojo.query.Result;
import jakarta.websocket.server.PathParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;


@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/user/photos")
public class PhotosController {

    private final PhotosService photosService;


    @DeleteMapping({"/{url}"})
    public Result deletePhotos(@PathVariable String url) {
        // 删除图片
        log.info("删除图片: {}",url);
        photosService.deletePhotos(url);
        return Result.success();
    }
    @GetMapping("/list")
    public Result listPhotos(@RequestBody Pagequery pagequery) {
        // 分页获取图片列表
        log.info("获取图片列表");
        return Result.success(photosService.listPhotos(pagequery));
    }
    @GetMapping("/binlist")
    public Result listBinPhotos()throws IOException {
        // 分页获取图片列表
        log.info("获取回收站图片列表");
        return Result.success(photosService.listBinPhotos());
    }
    @DeleteMapping("/bin/{url}")
    public Result deleteBinPhotos(@PathVariable String url) {
        // 删除图片
        log.info("删除图片: {}", url);
        photosService.deleteBinPhotos(url);
        return Result.success();
    }
}
