package com.hw.manage.Controller;

import com.hw.pojo.dto.Imagedto;
import com.hw.pojo.query.Pagequery;
import com.hw.pojo.query.Result;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/user/album")
public class albumController {
    //对相册进行操作
    @PostMapping("/add")
    public Result add(@RequestBody Imagedto imagedto) {    //todo 添加图片
        return Result.success();
    }
    @GetMapping("/get")
    public Result get(@RequestParam Pagequery pagequery) {    //todo 获取图片
        return Result.success();
    }
    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable String id ) {    //todo 删除图片
        return Result.success();
    }


}
