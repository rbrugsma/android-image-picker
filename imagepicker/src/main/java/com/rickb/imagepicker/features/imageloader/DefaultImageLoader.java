package com.rickb.imagepicker.features.imageloader;

import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.rickb.imagepicker.R;
import com.rickb.imagepicker.model.Image;

import static com.rickb.imagepicker.adapter.ImagePickerAdapter.CAMERA_ICON_ID;

public class DefaultImageLoader implements ImageLoader {

    @Override
    public void loadImage(Image image, ImageView imageView, ImageType imageType) {
        if (image.getId() == CAMERA_ICON_ID) {
            int drawableResource = imageView.getResources().getIdentifier("camera_icon", "drawable", imageView.getContext().getPackageName());

            Glide.with(imageView.getContext())
                    .load(drawableResource)
                    .apply(RequestOptions
                            .placeholderOf(imageType == ImageType.FOLDER
                                    ? R.drawable.ef_folder_placeholder
                                    : R.drawable.ef_image_placeholder)
                            .error(imageType == ImageType.FOLDER
                                    ? R.drawable.ef_folder_placeholder
                                    : R.drawable.ef_image_placeholder)
                    )
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView);
        } else {
            Glide.with(imageView.getContext())
                    .load(image.getUri())
                    .apply(RequestOptions
                            .placeholderOf(imageType == ImageType.FOLDER
                                    ? R.drawable.ef_folder_placeholder
                                    : R.drawable.ef_image_placeholder)
                            .error(imageType == ImageType.FOLDER
                                    ? R.drawable.ef_folder_placeholder
                                    : R.drawable.ef_image_placeholder)
                    )
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView);
        }
    }
}
