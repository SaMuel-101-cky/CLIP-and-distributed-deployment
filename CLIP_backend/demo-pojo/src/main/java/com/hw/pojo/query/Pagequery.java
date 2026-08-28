package com.hw.pojo.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Pagequery {
     private String description;
     private Integer page;//第几页
     private Integer pagesize;//页面大小
}
