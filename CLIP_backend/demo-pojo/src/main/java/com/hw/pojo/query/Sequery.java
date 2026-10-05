package com.hw.pojo.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Sequery {
    //封装username和taskId
    private String username;
    private Long taskId;

}
