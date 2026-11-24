package com.derashanwari.user.ui;

import android.Manifest;
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

import com.derashanwari.user.R;
import com.derashanwari.user.config.ImageKitConfig;
import com.derashanwari.user.firebase.FirebaseConfig;
import com.derashanwari.user.firebase.FirestoreSyncManager;
import com.derashanwari.user.model.Customer;
import com.derashanwari.user.model.GroupChatMember;
import com.derashanwari.user.model.GroupChatMessage;
import com.derashanwari.user.model.RestaurantSettings;
import com.derashanwari.user.repository.RepositoryProvider;
import com.derashanwari.user.ui.adapter.GroupChatAdapter;
import com.derashanwari.user.util.ImageKitUploader;
import com.derashanwari.user.util.ValidationUtil;
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
                else Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show();
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

        adapter = new GroupChatAdapter(new ArrayList<>(), resolveCurrentUid());
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
        RepositoryProvider.chat(this).setGroupOnline(uid, "CUSTOMER", name, "", true);
        RepositoryProvider.chat(this).markGroupRead(uid, "CUSTOMER", name, "");
    }

    @Override
    protected void onStop() {
        super.onStop();
        RepositoryProvider.chat(this).setGroupOnline(
                resolveCurrentUid(), "CUSTOMER", resolveCurrentName(), "", false);
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(this).removeDataChangeListener(this);
        }
    }

    @Override
    public void onDataChanged() {
        refreshMessages();
        RepositoryProvider.chat(this).markGroupRead(
                resolveCurrentUid(), "CUSTOMER", resolveCurrentName(), "");
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
                resolveCurrentUid(), "CUSTOMER", resolveCurrentName(), "", text, "");
        etInput.setText("");
        refreshMessages();
    }

    private void launchCamera() {
        try {
            File file = File.createTempFile("chat_cam_", ".jpg", getCacheDir());
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            takePictureLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
                                resolveCurrentUid(), "CUSTOMER", resolveCurrentName(), "",
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
        Customer c = RepositoryProvider.auth(this).getCurrentCustomer();
        return c != null && c.getId() != null ? c.getId() : "cust_1";
    }

    private String resolveCurrentName() {
        Customer c = RepositoryProvider.auth(this).getCurrentCustomer();
        if (c != null && ValidationUtil.isNonEmpty(c.getName())) return c.getName();
        return "Valued Customer";
    }
}
