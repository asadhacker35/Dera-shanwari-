package com.derashanwari.rider.ui.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.derashanwari.rider.R;
import com.derashanwari.rider.firebase.FirestoreSyncManager;
import com.derashanwari.rider.firebase.GoogleSignInHelper;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.RepositoryProvider;
import com.derashanwari.rider.ui.RiderLoginActivity;
import com.derashanwari.rider.util.CurrencyUtil;
import com.derashanwari.rider.util.ImageLoaderUtil;
import com.derashanwari.rider.util.ValidationUtil;

public class RiderProfileFragment extends Fragment implements FirestoreSyncManager.DataChangeListener {
    private ImageView avatar;
    private TextView tvName, tvPhone, tvVehicle, tvStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_rider_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        avatar = view.findViewById(R.id.iv_rider_avatar);
        tvName = view.findViewById(R.id.tv_profile_name);
        tvPhone = view.findViewById(R.id.tv_profile_phone);
        tvVehicle = view.findViewById(R.id.tv_profile_vehicle);
        tvStatus = view.findViewById(R.id.tv_profile_status);

        view.findViewById(R.id.btn_rider_logout).setOnClickListener(v -> {
            RepositoryProvider.auth(requireContext()).logout();
            if (!RepositoryProvider.isTestMode()) {
                GoogleSignInHelper.signOut(requireContext());
            }
            Intent i = new Intent(requireContext(), RiderLoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        bindProfile();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(requireContext()).addDataChangeListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (!RepositoryProvider.isTestMode()) {
            RepositoryProvider.sync(requireContext()).removeDataChangeListener(this);
        }
    }

    @Override
    public void onDataChanged() {
        if (!isAdded()) return;
        bindProfile();
    }

    private void bindProfile() {
        Rider rider = RepositoryProvider.auth(requireContext()).getCurrentRider();
        if (rider == null && RepositoryProvider.isTestMode()) {
            rider = RepositoryProvider.riders(requireContext()).getRiderById("rider_1");
        }
        if (rider != null) {
            ImageLoaderUtil.loadImage(avatar, rider.getProfileImageUrl(), "ic_dera_emblem");
            tvName.setText(rider.getName());
            String phoneLabel = ValidationUtil.isNonEmpty(rider.getEmail())
                    ? rider.getPhone() + " • " + rider.getEmail()
                    : rider.getPhone();
            tvPhone.setText(phoneLabel);
            tvVehicle.setText(rider.getVehicleInfo() + " • " + rider.getTotalDeliveries()
                    + " deliveries (" + CurrencyUtil.formatPkr(rider.getTotalEarnings()) + ")");
            tvStatus.setText(rider.isActive() ? "ACCOUNT ACTIVE" : "ACCOUNT INACTIVE");
        }
    }
}
