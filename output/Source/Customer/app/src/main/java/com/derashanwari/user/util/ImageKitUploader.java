package com.derashanwari.user.util;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.derashanwari.user.config.ImageKitConfig;
import com.derashanwari.user.firebase.FirebaseConfig;
import com.derashanwari.user.model.RestaurantSettings;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads compressed camera/gallery images directly to ImageKit using short-lived
 * HMAC-SHA1 signatures fetched from our trusted backend auth service.
 */
public final class ImageKitUploader {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_DIM_PX = 1280;
    private static final int JPEG_QUALITY = 85;

    public static class UploadResult {
        public final String url;
        public final String fileId;

        public UploadResult(String url, String fileId) {
            this.url = url;
            this.fileId = fileId;
        }
    }

    public interface UploadCallback {
        void onProgress(int percent, String stageLabel);
        void onSuccess(UploadResult result);
        void onError(String message);
    }

    private ImageKitUploader() {}

    public static void uploadUri(Context context, Uri imageUri, String folder, String fileNamePrefix,
                                 RestaurantSettings settings, UploadCallback callback) {
        if (context == null || imageUri == null) {
            if (callback != null) callback.onError("No image selected.");
            return;
        }
        String authEndpoint = settings != null ? settings.getImagekitAuthEndpoint().trim() : "";
        String fallbackPublicKey = settings != null ? settings.getImagekitPublicKey().trim() : "";

        if (authEndpoint.isEmpty()) {
            if (callback != null) {
                callback.onError("ImageKit auth server URL is not configured by Admin yet.");
            }
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                postProgress(callback, 10, "Compressing photo...");
                byte[] jpegBytes = compressImageUri(context.getContentResolver(), imageUri);
                if (jpegBytes == null || jpegBytes.length == 0) {
                    postError(callback, "Unable to read or decode selected image.");
                    return;
                }

                postProgress(callback, 25, "Fetching upload signature...");
                FirebaseUser fbUser = FirebaseConfig.getAuth(context).getCurrentUser();
                if (fbUser == null) {
                    postError(callback, "Please sign in before uploading images.");
                    return;
                }
                String idToken = com.google.android.gms.tasks.Tasks.await(fbUser.getIdToken(false)).getToken();
                if (idToken == null || idToken.isEmpty()) {
                    postError(callback, "Failed to obtain Firebase auth token.");
                    return;
                }

                String authUrl = buildAuthUrl(authEndpoint);
                JSONObject authJson = fetchAuthSignature(authUrl, idToken);
                String token = authJson.optString("token", "");
                long expire = authJson.optLong("expire", 0L);
                String signature = authJson.optString("signature", "");
                String publicKey = authJson.optString("publicKey", fallbackPublicKey);

                if (token.isEmpty() || signature.isEmpty() || expire <= 0L || publicKey.isEmpty()) {
                    postError(callback, "Invalid auth payload from ImageKit auth server.");
                    return;
                }

                postProgress(callback, 45, "Uploading to ImageKit...");
                String cleanFolder = normalizeFolder(folder);
                String fileName = (fileNamePrefix != null && !fileNamePrefix.isEmpty()
                        ? fileNamePrefix : "img") + "_" + System.currentTimeMillis() + ".jpg";

                UploadResult result = executeMultipartUpload(
                        jpegBytes, fileName, cleanFolder, publicKey, token, expire, signature,
                        pct -> postProgress(callback, 45 + (int) (pct * 0.55), "Uploading (" + pct + "%)...")
                );
                postProgress(callback, 100, "Upload complete");
                MAIN.post(() -> {
                    if (callback != null) callback.onSuccess(result);
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "Upload failed";
                postError(callback, msg);
            }
        });
    }

    public static byte[] compressImageUri(ContentResolver resolver, Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = resolver.openInputStream(uri)) {
            if (is == null) return null;
            BitmapFactory.decodeStream(is, null, bounds);
        }
        int sample = 1;
        while ((bounds.outWidth / sample) > MAX_DIM_PX || (bounds.outHeight / sample) > MAX_DIM_PX) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream is = resolver.openInputStream(uri)) {
            if (is == null) return null;
            bmp = BitmapFactory.decodeStream(is, null, opts);
        }
        if (bmp == null) return null;

        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w > MAX_DIM_PX || h > MAX_DIM_PX) {
            float ratio = Math.min((float) MAX_DIM_PX / w, (float) MAX_DIM_PX / h);
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * ratio)),
                    Math.max(1, Math.round(h * ratio)), true);
            if (scaled != bmp) bmp.recycle();
            bmp = scaled;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
        bmp.recycle();
        return baos.toByteArray();
    }

    public static String buildAuthUrl(String endpoint) {
        String clean = endpoint.trim();
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        if (clean.endsWith("/auth")) return clean;
        return clean + "/auth";
    }

    private static String normalizeFolder(String folder) {
        if (folder == null || folder.trim().isEmpty()) return "/derashanwari/uploads";
        String f = folder.trim();
        if (f.endsWith("/")) f = f.substring(0, f.length() - 1);
        return f.startsWith("/") ? f : "/derashanwari/" + f;
    }

    private static JSONObject fetchAuthSignature(String authUrl, String idToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(authUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Authorization", "Bearer " + idToken);
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = readAll(stream);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("Auth server error HTTP " + code + ": " + response);
        }
        return new JSONObject(response);
    }

    private interface ByteProgress {
        void onPercent(int percent);
    }

    private static UploadResult executeMultipartUpload(
            byte[] fileBytes, String fileName, String folder,
            String publicKey, String token, long expire, String signature,
            ByteProgress progressListener) throws Exception {

        String boundary = "----DeraShanwariBoundary" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL(ImageKitConfig.UPLOAD_ENDPOINT).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(25000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            writeFormField(out, boundary, "fileName", fileName);
            writeFormField(out, boundary, "folder", folder);
            writeFormField(out, boundary, "useUniqueFileName", "true");
            writeFormField(out, boundary, "publicKey", publicKey);
            writeFormField(out, boundary, "token", token);
            writeFormField(out, boundary, "expire", String.valueOf(expire));
            writeFormField(out, boundary, "signature", signature);

            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
            out.writeBytes("Content-Type: image/jpeg\r\n\r\n");

            int chunkSize = 8192;
            int offset = 0;
            while (offset < fileBytes.length) {
                int len = Math.min(chunkSize, fileBytes.length - offset);
                out.write(fileBytes, offset, len);
                offset += len;
                if (progressListener != null && fileBytes.length > 0) {
                    progressListener.onPercent((int) ((offset * 100L) / fileBytes.length));
                }
            }
            out.writeBytes("\r\n--" + boundary + "--\r\n");
            out.flush();
        }

        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        String response = readAll(stream);
        conn.disconnect();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("ImageKit upload HTTP " + code + ": " + response);
        }
        JSONObject json = new JSONObject(response);
        String url = json.optString("url", "");
        String fileId = json.optString("fileId", "");
        if (url.isEmpty()) {
            throw new IllegalStateException("ImageKit response missing url");
        }
        return new UploadResult(url, fileId);
    }

    private static void writeFormField(DataOutputStream out, String boundary, String name, String value)
            throws Exception {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.writeBytes("\r\n");
    }

    private static String readAll(InputStream is) {
        if (is == null) return "";
        try (Scanner s = new Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static void postProgress(UploadCallback cb, int pct, String label) {
        if (cb != null) MAIN.post(() -> cb.onProgress(pct, label));
    }

    private static void postError(UploadCallback cb, String msg) {
        if (cb != null) MAIN.post(() -> cb.onError(msg));
    }
}
