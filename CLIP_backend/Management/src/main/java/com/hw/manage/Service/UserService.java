package com.hw.manage.Service;

import com.hw.pojo.dto.Descriptiondto;
import com.hw.pojo.entity.User;
import com.hw.pojo.query.Sequery;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserService {
    public void changeuserInfo(User user);

    public Integer uploadmatch(Descriptiondto descriptiondto);

    public List<String> downloadmatch(Sequery sequery)throws  Exception;
}
