package com.rickb.imagepicker.features;

import android.content.Intent;

import com.rickb.imagepicker.features.common.MvpView;
import com.rickb.imagepicker.model.Folder;
import com.rickb.imagepicker.model.Image;

import java.util.List;

public interface ImagePickerView extends MvpView {
    void showLoading(boolean isLoading);
    void showFetchCompleted(List<Image> images, List<Folder> folders, int page);
    void showError(Throwable throwable);
    void showEmpty();
    void showCapturedImage();
    void finishPickImages(List<Image> images);

    /**
     * Should be called when the external source should open camera to pick an image.
     * @param result The result intent
     */
    void requestExternalCameraImage(List<Image> images);
}
