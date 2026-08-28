package com.hw.pojo.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {
    private  int code;//1成功0失败
    private String message;//错误信息
    private Object data;//传输的数据
    public static Result success(){
        Result result = new Result();
        result.code = 1;
        result.message = "操作成功";
        return result;
    }//无数据传输成功
    public static Result success(Object data){
        Result result = new Result();
        result.code=1;
        result.message="success";
        result.data = data;
        return result;
    }//有数据传输成功
    public static Result error(String message){
        Result result = new Result();
        result.code=0;
        result.message=message;
        return result;
    }//报错

}
