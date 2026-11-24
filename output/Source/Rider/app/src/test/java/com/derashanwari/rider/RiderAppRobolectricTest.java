package com.derashanwari.rider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.derashanwari.rider.config.ImageKitConfig;
import com.derashanwari.rider.model.Order;
import com.derashanwari.rider.model.OrderStatus;
import com.derashanwari.rider.model.Rider;
import com.derashanwari.rider.repository.AuthRepository;
import com.derashanwari.rider.repository.RepositoryProvider;
import com.derashanwari.rider.ui.RiderLoginActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class RiderAppRobolectricTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        RepositoryProvider.enableTestModeForRobolectric();
        RepositoryProvider.init(context);
    }

    @Test
    public void riderStartupConfigAndLogin_succeed() {
        assertEquals("https://ik.imagekit.io/s1igzgwfr/", ImageKitConfig.BASE_CDN_ENDPOINT);
        assertFalse(ImageKitConfig.getPresetOnlineImages().isEmpty());
        assertFalse(context.getString(R.string.firestore_database_id).isEmpty());
        assertFalse(context.getString(R.string.default_web_client_id).isEmpty());

        RiderLoginActivity login = Robolectric.buildActivity(RiderLoginActivity.class).setup().get();
        assertNotNull(login);
        MainActivity main = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(main);

        final boolean[] ok = {false};
        RepositoryProvider.auth(context).loginRider("03125556677", "1234",
                new AuthRepository.AuthCallback<Rider>() {
                    @Override public void onSuccess(Rider user) {
                        ok[0] = true;
                        assertEquals("Bilal Khan", user.getName());
                    }
                    @Override public void onError(String message) {}
                });
        assertTrue(ok[0]);
    }

    @Test
    public void riderOrderStatusProgression_updatesSharedRepositoryAndEarnings() {
        Rider before = RepositoryProvider.riders(context).getRiderById("rider_1");
        int deliveriesBefore = before.getTotalDeliveries();

        List<Order> assigned = RepositoryProvider.orders(context).getOrdersByRider("rider_1");
        Order activeOrder = null;
        for (Order o : assigned) {
            if (!OrderStatus.isCompletedOrCancelled(o.getOrderStatus())) {
                activeOrder = o;
                break;
            }
        }
        assertNotNull(activeOrder);
        assertTrue(RepositoryProvider.orders(context).updateOrderStatus(activeOrder.getOrderId(), OrderStatus.OUT_FOR_DELIVERY));
        assertEquals(OrderStatus.OUT_FOR_DELIVERY,
                RepositoryProvider.orders(context).getOrderById(activeOrder.getOrderId()).getOrderStatus());

        assertTrue(RepositoryProvider.orders(context).updateOrderStatus(activeOrder.getOrderId(), OrderStatus.DELIVERED));
        Rider after = RepositoryProvider.riders(context).getRiderById("rider_1");
        assertEquals(deliveriesBefore + 1, after.getTotalDeliveries());
    }
}
