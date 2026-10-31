package com.bayurmdn.catatemas;

import static org.junit.Assert.assertNotNull;

import android.webkit.WebView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** Guards against crashes during app start-up on old and new Android versions. */
@RunWith(RobolectricTestRunner.class)
public class MainActivityLaunchTest {

    private void launch() {
        try (ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class)) {
            c.setup(); // onCreate -> onStart -> onResume -> visible
            c.pause().resume(); // onResume path that refreshes prices
            assertNotNull(c.get().getWindow().getDecorView());
            c.pause().stop().destroy();
        }
    }

    @Test @Config(sdk = 35)
    public void launchesOnAndroid15() { launch(); }

    @Test @Config(sdk = 30)
    public void launchesOnAndroid11() { launch(); }

    @Test @Config(sdk = 26)
    public void launchesOnAndroid8() { launch(); }
}
