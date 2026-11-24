package com.derashanwari.admin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.derashanwari.admin.config.ImageKitConfig;
import com.derashanwari.admin.model.AdminUser;
import com.derashanwari.admin.model.Order;
import com.derashanwari.admin.model.OrderStatus;
import com.derashanwari.admin.model.Product;
import com.derashanwari.admin.repository.AuthRepository;
import com.derashanwari.admin.repository.RepositoryProvider;
import com.derashanwari.admin.ui.AdminLoginActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class AdminAppRobolectricTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        RepositoryProvider.enableTestModeForRobolectric();
        RepositoryProvider.init(context);
    }

    @Test
    public void adminStartupConfigAndLogin_succeed() {
        assertEquals("https://ik.imagekit.io/s1igzgwfr/", ImageKitConfig.BASE_CDN_ENDPOINT);
        assertFalse(ImageKitConfig.getPresetOnlineImages().isEmpty());
        assertFalse(context.getString(R.string.firestore_database_id).isEmpty());
        assertFalse(context.getString(R.string.default_web_client_id).isEmpty());

        AdminLoginActivity login = Robolectric.buildActivity(AdminLoginActivity.class).setup().get();
        assertNotNull(login);
        MainActivity main = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(main);

        final boolean[] ok = {false};
        RepositoryProvider.auth(context).loginAdmin("admin@derashanwari.pk", "123456",
                new AuthRepository.AuthCallback<AdminUser>() {
                    @Override public void onSuccess(AdminUser user) { ok[0] = true; }
                    @Override public void onError(String message) {}
                });
        assertTrue(ok[0]);
    }

    @Test
    public void adminProductManagementAndOrderAssignment_workCorrectly() {
        int before = RepositoryProvider.products(context).getAllProducts().size();
        Product p = new Product("", "Test Special Handi", "Rich creamy handi",
                1600, 1400, ImageKitConfig.IMG_MUTTON_KARAHI, "img_banner_shanwari",
                "cat_karahi", "Karahi", true, true, true, System.currentTimeMillis());
        RepositoryProvider.products(context).saveProduct(p);
        assertEquals(before + 1, RepositoryProvider.products(context).getAllProducts().size());

        Order order = RepositoryProvider.orders(context).getAllOrders().get(0);
        assertTrue(RepositoryProvider.orders(context).assignRider(order.getOrderId(), "rider_1", "Bilal Khan"));
        Order updated = RepositoryProvider.orders(context).getOrderById(order.getOrderId());
        assertEquals(OrderStatus.ASSIGNED, updated.getOrderStatus());
        assertEquals("Bilal Khan", updated.getRiderName());
    }
}
