package com.rickb.imagepicker.features.fileloader;

import com.rickb.imagepicker.features.ImagePickerConfig;
import com.rickb.imagepicker.features.common.ImageLoaderListener;

import java.io.File;
import java.util.ArrayList;

public interface ImageFileLoader {

    void loadDeviceImages(
            final ImagePickerConfig config,
            final ImageLoaderListener listener
    );

    void abortLoadImages();
}
