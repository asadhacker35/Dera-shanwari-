package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.example.config.ImageKitConfig;
import com.example.model.Customer;
import com.example.model.Order;
import com.example.model.OrderStatus;
import com.example.model.Product;
import com.example.repository.AuthRepository;
import com.example.repository.RepositoryProvider;
import com.example.repository.demo.CartManager;
import com.example.ui.AuthActivity;
import com.example.ui.CheckoutActivity;
import com.example.util.WhatsAppHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {35})
public class CustomerAppRobolectricTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        RepositoryProvider.enableTestModeForRobolectric();
        RepositoryProvider.init(context);
    }

    @Test
    public void appConfigAndStartup_workCorrectly() {
        assertEquals("Dera Shanwari", context.getString(R.string.app_name));
        assertEquals("https://ik.imagekit.io/s1igzgwfr/", ImageKitConfig.BASE_CDN_ENDPOINT);
        assertFalse(ImageKitConfig.getPresetOnlineImages().isEmpty());
        assertFalse(context.getString(R.string.firestore_database_id).isEmpty());
        assertFalse(context.getString(R.string.default_web_client_id).isEmpty());

        AuthActivity auth = Robolectric.buildActivity(AuthActivity.class).setup().get();
        assertNotNull(auth);
        MainActivity main = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(main);
    }

    @Test
    public void customerLoginAndOrderingFlow_succeeds() {
        AuthRepository authRepo = RepositoryProvider.auth(context);
        final boolean[] loggedIn = {false};
        authRepo.verifyCustomerOtp("03009876543", "1234", "Usman Tariq", "Hayatabad Peshawar",
                new AuthRepository.AuthCallback<Customer>() {
                    @Override public void onSuccess(Customer user) {
                        loggedIn[0] = true;
                        assertEquals("03009876543", user.getPhone());
                    }
                    @Override public void onError(String message) {}
                });
        assertTrue(loggedIn[0]);

        Product karahi = RepositoryProvider.products(context).getAllProducts().get(0);
        CartManager cart = CartManager.getInstance(context);
        cart.clear();
        cart.addProduct(karahi, 2);
        assertEquals(2, cart.getTotalItemCount());
        assertTrue(cart.getSubtotal() > 0);

        Order order = new Order();
        order.setCustomerId("cust_1");
        order.setCustomerName("Usman Tariq");
        order.setCustomerPhone("03009876543");
        order.setDeliveryAddress("Hayatabad Phase 2, Peshawar");
        order.setItems(cart.getItems());
        order.setSubtotal(cart.getSubtotal());
        order.setDeliveryFee(150);
        order.setTotalAmount(cart.getSubtotal() + 150);
        order.setPaymentMethod("Cash on Delivery");

        Order placed = RepositoryProvider.orders(context).placeOrder(order);
        assertNotNull(placed.getOrderId());
        assertEquals(OrderStatus.PENDING, placed.getOrderStatus());

        String waMsg = WhatsAppHelper.buildOrderMessage(placed, RepositoryProvider.settings(context).getRestaurantSettings());
        assertTrue(waMsg.contains("Dera Shanwari"));
        assertTrue(waMsg.contains(placed.getOrderId()));
        assertTrue(waMsg.contains(karahi.getName()));
        assertFalse(waMsg.isEmpty());
    }

    @Test
    public void addToCartAndClickCartTab_rendersCartWithoutCrash() {
        Product karahi = RepositoryProvider.products(context).getAllProducts().get(0);
        CartManager cart = CartManager.getInstance(context);
        cart.clear();
        cart.addProduct(karahi, 1);

        MainActivity main = Robolectric.buildActivity(MainActivity.class).setup().get();
        BottomNavigationView bottomNav = main.findViewById(R.id.bottom_navigation);
        assertNotNull(bottomNav);

        // Simulate tapping the Cart tab after adding an item (triggers CartFragment inflation & refreshCart)
        bottomNav.setSelectedItemId(R.id.nav_cart);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        View summaryCard = main.findViewById(R.id.card_cart_summary);
        RecyclerView rvCart = main.findViewById(R.id.rv_cart_items);
        TextView tvTotal = main.findViewById(R.id.tv_cart_total);
        assertNotNull(summaryCard);
        assertNotNull(rvCart);
        assertNotNull(tvTotal);
        assertEquals(View.VISIBLE, summaryCard.getVisibility());
        assertEquals(View.VISIBLE, rvCart.getVisibility());
        assertNotNull(rvCart.getAdapter());
        assertEquals(1, rvCart.getAdapter().getItemCount());
        assertTrue(tvTotal.getText().toString().contains("Grand Total:"));

        // Increment quantity and verify checkout opens cleanly
        cart.updateQuantity(karahi.getId(), 2);
        assertEquals(2, cart.getTotalItemCount());
        CheckoutActivity checkout = Robolectric.buildActivity(CheckoutActivity.class).setup().get();
        assertNotNull(checkout);
        assertNotNull(checkout.findViewById(R.id.btn_place_order));
    }
}
