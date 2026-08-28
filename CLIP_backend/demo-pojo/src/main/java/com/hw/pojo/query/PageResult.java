package com.hw.pojo.query;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResult<T> {
    List<T>list;//数据列表
    Long total;//总的页数

}
