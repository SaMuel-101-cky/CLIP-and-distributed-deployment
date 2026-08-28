package com.hw.manage.Controller;
import com.hw.manage.Service.CategoryService;
import com.hw.manage.Service.LoginService;
import com.hw.manage.Service.UserService;
import com.hw.pojo.dto.Descriptiondto;
import com.hw.pojo.dto.Logindto;
import com.hw.pojo.dto.Registerdto;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.Result;
import com.hw.pojo.query.Sequery;
import com.hw.pojo.vo.Loginvo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/user")
public class userController {

    private final LoginService loginService;
    private final UserService userService;
    private final CategoryService categoryService;

    @PostMapping("/login")
    public Result login(@RequestBody Logindto logindto)throws Exception {
        //登录
        Loginvo loginvo = new Loginvo();
      try {
          log.info("用户准备登录，登录信息为:" + "logindto:{}", logindto);
          loginvo = loginService.login(logindto);
      }catch (Exception e){
          log.error("用户登录异常:"+e.getMessage());
          return Result.error("用户登录异常:"+e.getMessage());
      }
      if(loginvo == null){
            return Result.error("用户登录失败");
        }
        return Result.success(loginvo);
    }
    //用户注册
    @PostMapping("/register")
    public Result register(@RequestBody Registerdto registerdto )throws  Exception{
        try {
            log.info("用户准备注册，注册信息为:" + "registerdto:{}", registerdto);
            loginService.register(registerdto);
        }
        catch (Exception e) {
            log.error("用户注册异常:" + e.getMessage());
            return Result.error("用户注册异常:" + e.getMessage());
        }
        return Result.success("用户注册成功");
    }
    @PostMapping("/sendmsg")
    public Result sendmsg(@RequestBody Registerdto registerdto) {
        //发送验证码 该模块待商榷. TODO
        loginService.sendmsg(registerdto.getPhoneNum());
        return Result.success();
    }
    //用户修改信息
    @PutMapping("/change")
    public Result changeuserInfo(@RequestBody User userinfo )
    {
      log.info("用户准备修改信息，修改信息为:" + "userinfo:{}", userinfo);
      userService.changeuserInfo(userinfo);
      return Result.success();
    }
    //用户上传描述
    @PostMapping("/match/upload")
    public Result uploadmatch(@RequestBody Descriptiondto descriptiondto) throws Exception {
        log.info("用户准备上传描述，描述信息为:" + "description:{}", descriptiondto.getDescription());
        return Result.success(userService.uploadmatch(descriptiondto));

    }
    @GetMapping("/match/download")
    public Result downloadmatch(@RequestBody Sequery sequery) throws Exception {
        log.info("用户准备下载描述结果，编号为:{}", sequery);
        List<String> photosList = null;
        try {
           photosList = userService.downloadmatch(sequery);
        }catch (Exception e){
            log.error("用户下载描述结果异常:"+e.getMessage());
            return Result.error("用户下载描述结果异常:"+e.getMessage());
        }
        return Result.success(photosList);
    }

    @PostMapping("/category/upload")
    public Result categoryUpload(
            @RequestParam("username") String username,
            @RequestParam("idNum") Integer idNum,
            @RequestParam("descriptionList") String descriptionList,
            @RequestParam("photoList") MultipartFile[] photoList
    ) {
        try {
            Map<String, Object> data = categoryService.categoryUpload(username, idNum, descriptionList, photoList);
            return Result.success(data);
        } catch (Exception e) {
            log.error("分类上传失败", e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 图片分类结果下载接口
     * URL: /user/category/download
     * 文档要求 GET 请求且带 JSON Body
     */
    @GetMapping("/category/download")
    public Result categoryDownload(@RequestBody Map<String, Object> params) {
        try {
            String username = (String) params.get("username");
            // 兼容处理 idNum 可能是 String 或 Integer
            Object idNumObj = params.get("idNum");
            Integer idNum;
            if (idNumObj instanceof String) {
                idNum = Integer.parseInt((String) idNumObj);
            } else {
                idNum = (Integer) idNumObj;
            }

            Map<String, Object> data = categoryService.categoryDownload(username, idNum);
            return Result.success(data);
        } catch (Exception e) {
            log.error("下载分类结果异常", e);
            return Result.error(e.getMessage()); // 这里会将 "AI正在处理中" 返回给前端
        }
    }
}

