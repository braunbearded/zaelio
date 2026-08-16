package com.zaelio.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.zaelio.app.theme.ThemeStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ThemeStoreTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void sessionFieldsCollapsedPersists() {
        ThemeStore theme = new ThemeStore(context);

        assertFalse(theme.sessionFieldsCollapsed());
        theme.setSessionFieldsCollapsed(true);

        assertTrue(new ThemeStore(context).sessionFieldsCollapsed());
    }
}
