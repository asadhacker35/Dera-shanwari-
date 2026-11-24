package com.derashanwari.admin.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.derashanwari.admin.R;
import com.derashanwari.admin.config.ImageKitConfig;
import com.derashanwari.admin.firebase.FirebaseConfig;
import com.derashanwari.admin.firebase.FirestoreSyncManager;
import com.derashanwari.admin.model.AdminUser;
import com.derashanwari.admin.model.GroupChatMember;
import com.derashanwari.admin.model.GroupChatMessage;
import com.derashanwari.admin.model.RestaurantSettings;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.ui.adapter.GroupChatAdapter;
import com.derashanwari.admin.util.ImageKitUploader;
import com.derashanwari.admin.util.ValidationUtil;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class GroupChatActivity extends AppCompatActivity implements FirestoreSyncManager.DataChangeListener {
    private RecyclerView rvChat;
    private GroupChatAdapter adapter;
    private TextView tvPresence, tvUploadStatus;
    private View layoutUploadProgress;
    private EditText etInput;
    private Uri pendingCameraUri;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) uploadAndSendPhoto(uri);
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraUri != null) {
                    uploadAndSendPhoto(pendingCameraUri);
                }
            });

    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) launchCamera();
                else Toast.makeText(this, "Camera permission is needed to take photos", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        tvPresence = findViewById(R.id.tv_group_presence);
        tvUploadStatus = findViewById(R.id.tv_chat_upload_status);
        layoutUploadProgress = findViewById(R.id.layout_chat_upload_progress);
        etInput = findViewById(R.id.et_group_chat_input);
        rvChat = findViewById(R.id.rv_group_chat);

        findViewById(R.id.btn_group_chat_back).setOnClickListener(v -> finish());

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvChat.setLayoutManager(lm);

        String uid = resolveCurrentUid();
        adapter = new GroupChatAdapter(new ArrayList<>(), uid, true, message ->
                new AlertDialog.Builder(this)
                        .setTitle("Delete Message")
                        .setMessage("Remove this message for all group members?")
                        .setPositiveButton("Delete", (d, w) -> {
                            RepositoryProvider.chat(this).deleteGroupMessage(message.getId());
                            refreshMessages();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
        rvChat.setAdapter(adapter);

        findViewById(R.id.btn_group_chat_send).setOnClickListener(v -> sendTextMessage());
        findViewById(R.id.btn_chat_gallery).setOnClickListener(v ->
                pickPhotoLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        findViewById(R.id.btn_chat_camera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        refreshMessages();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).addDataChangeListener(this);
        }
        String uid = resolveCurrentUid();
        String name = resolveCurrentName();
        String avatar = resolveAvatarUrl();
        RepositoryProvider.chat(this).setGroupOnline(uid, "ADMIN", name, avatar, true);
        RepositoryProvider.chat(this).markGroupRead(uid, "ADMIN", name, avatar);
    }

    @Override
    protected void onStop() {
        super.onStop();
        String uid = resolveCurrentUid();
        String name = resolveCurrentName();
        String avatar = resolveAvatarUrl();
        RepositoryProvider.chat(this).setGroupOnline(uid, "ADMIN", name, avatar, false);
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).removeDataChangeListener(this);
        }
    }

    @Override
    public void onDataChanged() {
        refreshMessages();
        RepositoryProvider.chat(this).markGroupRead(
                resolveCurrentUid(), "ADMIN", resolveCurrentName(), resolveAvatarUrl());
    }

    private void refreshMessages() {
        List<GroupChatMessage> list = RepositoryProvider.chat(this).getGroupMessages();
        adapter.updateMessages(list);
        if (!list.isEmpty()) {
            rvChat.scrollToPosition(list.size() - 1);
        }

        List<GroupChatMember> members = RepositoryProvider.chat(this).getGroupMembers();
        int onlineCount = 0;
        for (GroupChatMember m : members) {
            if (m != null && m.isOnline()) onlineCount++;
        }
        tvPresence.setText(onlineCount + " online (" + members.size() + " members) • Admin, Riders & Customers");
    }

    private void sendTextMessage() {
        String text = etInput.getText().toString().trim();
        if (!ValidationUtil.isNonEmpty(text)) return;
        RepositoryProvider.chat(this).sendGroupMessage(
                resolveCurrentUid(), "ADMIN", resolveCurrentName(), resolveAvatarUrl(), text, "");
        etInput.setText("");
        refreshMessages();
    }

    private void launchCamera() {
        try {
            File file = File.createTempFile("chat_cam_", ".jpg", getCacheDir());
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            takePictureLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to launch camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadAndSendPhoto(Uri uri) {
        RestaurantSettings settings = RepositoryProvider.settings(this).getRestaurantSettings();
        layoutUploadProgress.setVisibility(View.VISIBLE);
        tvUploadStatus.setText("Uploading photo...");

        ImageKitUploader.uploadUri(this, uri, ImageKitConfig.FOLDER_CHAT, "group", settings,
                new ImageKitUploader.UploadCallback() {
                    @Override
                    public void onProgress(int percent, String stageLabel) {
                        tvUploadStatus.setText(stageLabel + " (" + percent + "%)");
                    }

                    @Override
                    public void onSuccess(ImageKitUploader.UploadResult result) {
                        layoutUploadProgress.setVisibility(View.GONE);
                        String caption = etInput.getText().toString().trim();
                        etInput.setText("");
                        RepositoryProvider.chat(GroupChatActivity.this).sendGroupMessage(
                                resolveCurrentUid(), "ADMIN", resolveCurrentName(), resolveAvatarUrl(),
                                caption, result.url);
                        refreshMessages();
                    }

                    @Override
                    public void onError(String message) {
                        layoutUploadProgress.setVisibility(View.GONE);
                        Toast.makeText(GroupChatActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String resolveCurrentUid() {
        if (!RepositoryProvider.isTestMode()) {
            FirebaseUser u = FirebaseConfig.getAuth(this).getCurrentUser();
            if (u != null) return u.getUid();
        }
        AdminUser admin = RepositoryProvider.auth(this).getCurrentAdmin();
        return admin != null && admin.getId() != null ? admin.getId() : "admin_main";
    }

    private String resolveCurrentName() {
        AdminUser admin = RepositoryProvider.auth(this).getCurrentAdmin();
        if (admin != null && ValidationUtil.isNonEmpty(admin.getName())) return admin.getName();
        return "Dera Shanwari Admin";
    }

    private String resolveAvatarUrl() {
        RestaurantSettings s = RepositoryProvider.settings(this).getRestaurantSettings();
        return s != null && s.getLogoUrl() != null ? s.getLogoUrl() : "";
    }
}
