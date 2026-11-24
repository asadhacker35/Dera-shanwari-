package com.derashanwari.rider.repository.cloud;

import com.derashanwari.rider.firebase.FirestoreSyncManager;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.RiderRepository;

import java.util.ArrayList;
import java.util.List;

public class CloudRiderRepository implements RiderRepository {
    private final FirestoreSyncManager sync;

    public CloudRiderRepository(FirestoreSyncManager sync) {
        this.sync = sync;
    }

    @Override
    public List<Rider> getAllRiders() {
        return sync.getRiders();
    }

    @Override
    public List<Rider> getActiveRiders() {
        List<Rider> res = new ArrayList<>();
        for (Rider r : sync.getRiders()) {
            if (r.isActive()) res.add(r);
        }
        return res;
    }

    @Override
    public Rider getRiderById(String riderId) {
        if (riderId == null) return null;
        Rider current = sync.getCurrentRider();
        if (current != null && riderId.equals(current.getId())) return current;
        for (Rider r : sync.getRiders()) {
            if (riderId.equals(r.getId())) return r;
        }
        return null;
    }

    @Override
    public void saveRider(Rider rider) {
        sync.saveRider(rider);
    }

    @Override
    public void setRiderActive(String riderId, boolean active) {
        sync.setRiderActive(riderId, active);
    }

    @Override
    public void incrementRiderDeliveryStats(String riderId, double deliveryEarning) {
        sync.incrementRiderDeliveryStats(riderId, deliveryEarning);
    }
}
