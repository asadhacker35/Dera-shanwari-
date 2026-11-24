package com.derashanwari.rider.repository;

import com.derashanwari.rider.model.Rider;

import java.util.List;

public interface RiderRepository {
    List<Rider> getAllRiders();
    List<Rider> getActiveRiders();
    Rider getRiderById(String riderId);
    void saveRider(Rider rider);
    void setRiderActive(String riderId, boolean active);
    void incrementRiderDeliveryStats(String riderId, double deliveryEarning);
}
