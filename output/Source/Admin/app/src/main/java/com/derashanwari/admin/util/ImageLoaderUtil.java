package com.derashanwari.admin.util;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.derashanwari.admin.R;
import com.derashanwari.admin.config.ImageKitConfig;

/**
 * Loads ImageKit CDN URLs and public online HTTPS dish images via Glide with bundled drawable fallbacks.
 */
public final class ImageLoaderUtil {
    private ImageLoaderUtil() {}

    public static void loadImage(ImageView imageView, String imageUrl, String localDrawableName) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        int fallbackRes = resolveLocalDrawable(context, localDrawableName);

        if (imageUrl != null && !imageUrl.trim().isEmpty() && !imageUrl.startsWith("local://")) {
            String transformed = ImageKitConfig.buildTransformedUrl(imageUrl, 720, 540);
            Glide.with(context)
                    .load(transformed)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(fallbackRes)
                    .error(fallbackRes)
                    .centerCrop()
                    .into(imageView);
        } else {
            Glide.with(context)
                    .load(fallbackRes)
                    .centerCrop()
                    .into(imageView);
        }
    }

    public static int resolveLocalDrawable(Context context, String localDrawableName) {
        if (localDrawableName != null && !localDrawableName.trim().isEmpty()) {
            int resId = context.getResources().getIdentifier(
                    localDrawableName.trim(), "drawable", context.getPackageName());
            if (resId != 0) {
                return resId;
            }
        }
        return R.drawable.img_banner_shanwari;
    }
}
