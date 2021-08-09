package com.rickb.imagepicker.features.common;

import com.rickb.imagepicker.model.Folder;
import com.rickb.imagepicker.model.Image;

import java.util.List;

public interface ImageLoaderListener {
    void onImagePageLoaded(List<Image> images, List<Folder> folders, int page);
    void onFailed(Throwable throwable);
}
