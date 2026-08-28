package com.hw.manage.Service;

import com.hw.pojo.query.PageResult;
import com.hw.pojo.query.Pagequery;

import java.io.IOException;
import java.util.List;

public interface PhotosService {
    public void deletePhotos(String url);

    public PageResult<String> listPhotos(Pagequery pagequery);

    public List<String> listBinPhotos()throws IOException;

    void deleteBinPhotos(String url);
}
