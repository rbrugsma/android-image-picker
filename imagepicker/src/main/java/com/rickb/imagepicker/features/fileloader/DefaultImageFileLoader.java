package com.rickb.imagepicker.features.fileloader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.Nullable;

import com.rickb.imagepicker.features.ImagePickerConfig;
import com.rickb.imagepicker.features.common.ImageLoaderListener;
import com.rickb.imagepicker.helper.ImagePickerUtils;
import com.rickb.imagepicker.model.Folder;
import com.rickb.imagepicker.model.Image;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultImageFileLoader implements ImageFileLoader {

    private Context context;
    private ExecutorService executorService;

    private ImagePickerConfig mConfig;
    private ImageLoaderListener mListener;

    public DefaultImageFileLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    private final String[] projection = new String[]{
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
    };

    public void loadNextPage(int page) {

        loadDeviceImagesPage(page);
    }

    @Override
    public void loadDeviceImages(
            final ImagePickerConfig config,
            final ImageLoaderListener listener
    ) {
        mConfig = config;
        mListener = listener;

        loadDeviceImagesPage(0);
    }

    public void loadDeviceImagesPage(int page) {
        if (mConfig == null || mListener == null) return;

        boolean isFolderMode = mConfig.isFolderMode();
        boolean includeVideo = mConfig.isIncludeVideo();
        boolean onlyVideo = mConfig.isOnlyVideo();
        boolean includeAnimation = mConfig.isIncludeAnimation();
        ArrayList<File> excludedImages = mConfig.getExcludedImages();

        getExecutorService().execute(
                new ImageLoadRunnable(
                        isFolderMode,
                        onlyVideo,
                        includeVideo,
                        includeAnimation,
                        excludedImages,
                        mListener,
                        page
                ));
    }

    @Override
    public void abortLoadImages() {
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
    }

    private ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
        }
        return executorService;
    }

    private class ImageLoadRunnable implements Runnable {

        private boolean isFolderMode;
        private boolean includeVideo;
        private boolean onlyVideo;
        private boolean includeAnimation;
        private ArrayList<File> exlucedImages;
        private ImageLoaderListener listener;
        private int page;

        int PAGE_SIZE = 130;

        ImageLoadRunnable(
                boolean isFolderMode,
                boolean onlyVideo,
                boolean includeVideo,
                boolean includeAnimation,
                ArrayList<File> excludedImages,
                ImageLoaderListener listener,
                int page
        ) {
            this.isFolderMode = isFolderMode;
            this.includeVideo = includeVideo;
            this.includeAnimation = includeAnimation;
            this.onlyVideo = onlyVideo;
            this.exlucedImages = excludedImages;
            this.listener = listener;
            this.page = page;
        }

        private String getQuerySelection() {
            if (onlyVideo) {
                return MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                        + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            }
            if (includeVideo) {
                return MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                        + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + " OR "
                        + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                        + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
            }
            return null;
        }

        private Uri getSourceUri() {
            if (onlyVideo || includeVideo) {
                return MediaStore.Files.getContentUri("external");
            }
            return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        @Override
        public void run() {
            int imagesLoaded = 0;
            Cursor cursor = context.getContentResolver().query(getSourceUri(), projection,
                    getQuerySelection(), null, MediaStore.Images.Media.DATE_MODIFIED);

            if (cursor == null) {
                listener.onFailed(new NullPointerException());
                return;
            }

            List<Image> temp = new ArrayList<>();
            Map<String, Folder> folderMap = null;
            if (isFolderMode) {
                folderMap = new HashMap<>();
            }

            if (cursor.moveToLast()) {
                for(int i = 0; i < page * PAGE_SIZE; i++) {
                    if (!cursor.moveToPrevious()) return;
                }
                do {
                    String path = cursor.getString(cursor.getColumnIndex(projection[2]));
                    if (path == null) continue;

                    File file = makeSafeFile(path);
                    if (file == null) {
                        continue;
                    }

                    if (exlucedImages != null && exlucedImages.contains(file))
                        continue;

                    // Exclude GIF when we don't want it
                    if (!includeAnimation) {
                        if (ImagePickerUtils.isGifFormat(path)) {
                            continue;
                        }
                    }

                    long id = cursor.getLong(cursor.getColumnIndex(projection[0]));
                    String name = cursor.getString(cursor.getColumnIndex(projection[1]));
                    String bucket = cursor.getString(cursor.getColumnIndex(projection[3]));

                    boolean isValid = id >= 0 && name != null && path != null && file.lastModified() > 0;
                    if (!isValid) continue;

                    Image image = new Image(id, name, path, null, file.lastModified());

                    temp.add(image);

                    if (folderMap != null) {
                        Folder folder = folderMap.get(bucket);
                        if (folder == null) {
                            folder = new Folder(bucket);
                            folderMap.put(bucket, folder);
                        }
                        folder.getImages().add(image);
                    }

                    imagesLoaded ++;
                    if (imagesLoaded % PAGE_SIZE == 0) {
                        cursor.close();

                        /* Convert HashMap to ArrayList if not null */
                        List<Folder> folders = null;
                        if (folderMap != null) {
                            folders = new ArrayList<>(folderMap.values());
                        }

                        listener.onImagePageLoaded(temp, folders, page);
                        return;
                    }
                } while (cursor.moveToPrevious());
            }
            cursor.close();

            /* Convert HashMap to ArrayList if not null */
            List<Folder> folders = null;
            if (folderMap != null) {
                folders = new ArrayList<>(folderMap.values());
            }

            listener.onImagePageLoaded(temp, folders, page);
        }
    }

    @Nullable
    public static File makeSafeFile(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return new File(path);
        } catch (Exception ignored) {
            return null;
        }
    }
}
