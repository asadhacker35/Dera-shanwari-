package com.derashanwari.user.repository.demo;

import com.derashanwari.user.model.Rider;
import com.derashanwari.user.repository.RiderRepository;

import java.util.ArrayList;
import java.util.List;

public class DemoRiderRepository implements RiderRepository {
    private final DemoDatabaseManager db;

    public DemoRiderRepository(DemoDatabaseManager db) {
        this.db = db;
    }

    @Override
    public List<Rider> getAllRiders() {
        return new ArrayList<>(db.getState().riders);
    }

    @Override
    public List<Rider> getActiveRiders() {
        List<Rider> result = new ArrayList<>();
        for (Rider r : db.getState().riders) {
            if (r.isActive()) result.add(r);
        }
        return result;
    }

    @Override
    public Rider getRiderById(String riderId) {
        for (Rider r : db.getState().riders) {
            if (r.getId().equals(riderId)) return r;
        }
        return null;
    }

    @Override
    public void saveRider(Rider rider) {
        if (rider.getId() == null || rider.getId().trim().isEmpty()) {
            rider.setId("rider_" + System.currentTimeMillis());
        }
        List<Rider> list = db.getState().riders;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(rider.getId())) {
                list.set(i, rider);
                db.save();
                return;
            }
        }
        list.add(rider);
        db.save();
    }

    @Override
    public void setRiderActive(String riderId, boolean active) {
        Rider r = getRiderById(riderId);
        if (r != null) {
            r.setActive(active);
            db.save();
        }
    }

    @Override
    public void incrementRiderDeliveryStats(String riderId, double deliveryEarning) {
        Rider r = getRiderById(riderId);
        if (r != null) {
            r.setTotalDeliveries(r.getTotalDeliveries() + 1);
            r.setTotalEarnings(r.getTotalEarnings() + deliveryEarning);
            db.save();
        }
    }
}
