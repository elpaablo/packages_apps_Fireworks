package com.spark.settings.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceGroup;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import com.android.internal.lineage.hardware.LineageHardwareManager;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.hwkeys.ActionConstants;
import com.android.internal.util.hwkeys.ActionUtils;
import com.android.internal.util.spark.SparkUtils;
import com.android.settingslib.search.SearchIndexable;
import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.Utils;
import com.spark.settings.preferences.ActionFragment;
import com.spark.support.preferences.CustomSeekBarPreference;
import java.net.InetAddress;
import java.util.List;

@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class MiscSettings extends ActionFragment implements
        OnPreferenceChangeListener {

    private static final String PREF_ADBLOCK = "persist.spark.hosts_block";


    // category keys
    private static final String CATEGORY_HWKEY = "hardware_keys";
    private static final String CATEGORY_BACK = "back_key";
    private static final String CATEGORY_HOME = "home_key";
    private static final String CATEGORY_MENU = "menu_key";
    private static final String CATEGORY_ASSIST = "assist_key";
    private static final String CATEGORY_APPSWITCH = "app_switch_key";
    private static final String CATEGORY_VOLUME = "volume_keys";
    private static final String CATEGORY_POWER = "power_key";

    private static final String KEY_BUTTON_MANUAL_BRIGHTNESS_NEW = "button_manual_brightness_new";
    private static final String KEY_BUTTON_TIMEOUT = "button_timeout";
    private static final String KEY_BUTON_BACKLIGHT_OPTIONS = "button_backlight_options_category";
    private static final String HWKEY_DISABLE = "hardware_keys_disable";
    private static final String NAVBAR_VISIBILITY = "navbar_visibility";

    // Masks for checking presence of hardware keys.
    // Must match values in frameworks/base/core/res/res/values/config.xml
    public static final int KEY_MASK_HOME = 0x01;
    public static final int KEY_MASK_BACK = 0x02;
    public static final int KEY_MASK_MENU = 0x04;
    public static final int KEY_MASK_ASSIST = 0x08;
    public static final int KEY_MASK_APP_SWITCH = 0x10;
    public static final int KEY_MASK_CAMERA = 0x20;
    public static final int KEY_MASK_VOLUME = 0x40;

    private CustomSeekBarPreference mButtonTimoutBar;
    private CustomSeekBarPreference mManualButtonBrightness;
    private PreferenceCategory mButtonBackLightCategory;
    private SwitchPreference mHwKeyDisable;
    private SwitchPreference mNavbarVisibility;

    private boolean mIsNavSwitchingMode = false;

    private Handler mHandler = new Handler();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.spark_settings_misc);

        final ContentResolver resolver = getActivity().getContentResolver();
        final Context mContext = getActivity().getApplicationContext();
        final PreferenceScreen prefSet = getPreferenceScreen();
        final Resources res = mContext.getResources();

        findPreference(PREF_ADBLOCK).setOnPreferenceChangeListener(this);

        final boolean needsNavbar = ActionUtils.hasNavbarByDefault(getActivity());
        final PreferenceCategory hwkeyCat = (PreferenceCategory) prefSet
                .findPreference(CATEGORY_HWKEY);
        int keysDisabled = 0;
        if (!needsNavbar) {
            mHwKeyDisable = (SwitchPreference) findPreference(HWKEY_DISABLE);
            keysDisabled = Settings.System.getIntForUser(getContentResolver(),
                    Settings.System.HARDWARE_KEYS_DISABLE, 0,
                    UserHandle.USER_CURRENT);
            mHwKeyDisable.setChecked(keysDisabled != 0);
            mHwKeyDisable.setOnPreferenceChangeListener(this);
        } else {
            prefSet.removePreference(hwkeyCat);
        }

        // bits for hardware keys present on device
        final int deviceKeys = getResources().getInteger(
                com.android.internal.R.integer.config_deviceHardwareKeys);
        // read bits for present hardware keys
        final boolean hasHomeKey = (deviceKeys & KEY_MASK_HOME) != 0;
        final boolean hasBackKey = (deviceKeys & KEY_MASK_BACK) != 0;
        final boolean hasMenuKey = (deviceKeys & KEY_MASK_MENU) != 0;
        final boolean hasAssistKey = (deviceKeys & KEY_MASK_ASSIST) != 0;
        final boolean hasAppSwitchKey = (deviceKeys & KEY_MASK_APP_SWITCH) != 0;
        // load categories and init/remove preferences based on device
        // configuration
        final PreferenceCategory backCategory = (PreferenceCategory) prefSet
                .findPreference(CATEGORY_BACK);
        final PreferenceCategory homeCategory = (PreferenceCategory) prefSet
                .findPreference(CATEGORY_HOME);
        final PreferenceCategory menuCategory = (PreferenceCategory) prefSet
                .findPreference(CATEGORY_MENU);
        final PreferenceCategory assistCategory = (PreferenceCategory) prefSet
                .findPreference(CATEGORY_ASSIST);
        final PreferenceCategory appSwitchCategory = (PreferenceCategory) prefSet
                .findPreference(CATEGORY_APPSWITCH);
        // back key
        if (!hasBackKey) {
            prefSet.removePreference(backCategory);
        }
        // home key
        if (!hasHomeKey) {
            prefSet.removePreference(homeCategory);
        }
        // App switch key (recents)
        if (!hasAppSwitchKey) {
            prefSet.removePreference(appSwitchCategory);
        }
        // menu key
        if (!hasMenuKey) {
            prefSet.removePreference(menuCategory);
        }
        // search/assist key
        if (!hasAssistKey) {
            prefSet.removePreference(assistCategory);
        }
        // let super know we can load ActionPreferences
        onPreferenceScreenLoaded(ActionConstants.getDefaults(ActionConstants.HWKEYS));

        // load preferences first
        setActionPreferencesEnabled(keysDisabled == 0);

        mManualButtonBrightness = (CustomSeekBarPreference) findPreference(
                KEY_BUTTON_MANUAL_BRIGHTNESS_NEW);
        final int customButtonBrightness = getResources().getInteger(
                com.android.internal.R.integer.config_button_brightness_default);
        final int currentBrightness = Settings.System.getInt(resolver,
                Settings.System.CUSTOM_BUTTON_BRIGHTNESS, customButtonBrightness);
        mManualButtonBrightness.setValue(currentBrightness);
        mManualButtonBrightness.setOnPreferenceChangeListener(this);

        mButtonTimoutBar = (CustomSeekBarPreference) findPreference(KEY_BUTTON_TIMEOUT);
        int currentTimeout = Settings.System.getInt(resolver,
                Settings.System.BUTTON_BACKLIGHT_TIMEOUT, 0);
        mButtonTimoutBar.setValue(currentTimeout);
        mButtonTimoutBar.setOnPreferenceChangeListener(this);

        final boolean enableBacklightOptions = getResources().getBoolean(
                com.android.internal.R.bool.config_button_brightness_support);

        mButtonBackLightCategory = (PreferenceCategory) findPreference(KEY_BUTON_BACKLIGHT_OPTIONS);

        if (!enableBacklightOptions) {
            prefSet.removePreference(mButtonBackLightCategory);
        }

        mNavbarVisibility = (SwitchPreference) findPreference(NAVBAR_VISIBILITY);

        boolean showing = Settings.System.getIntForUser(resolver,
                Settings.System.FORCE_SHOW_NAVBAR,
                SparkUtils.hasNavbarByDefault(getActivity()) ? 1 : 0, UserHandle.USER_CURRENT) != 0;
        mNavbarVisibility.setChecked(showing);
        mNavbarVisibility.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver resolver = getActivity().getContentResolver();
        if (PREF_ADBLOCK.equals(preference.getKey())) {
            // Flush the java VM DNS cache to re-read the hosts file.
            // Delay to ensure the value is persisted before we refresh
            mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        InetAddress.clearDnsCache();
                    }
            }, 1000);
            return true;
        } else if (preference == mHwKeyDisable) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getContentResolver(), Settings.System.HARDWARE_KEYS_DISABLE,
                    value ? 1 : 0);
            setActionPreferencesEnabled(!value);
            return true;
        } else if (preference == mButtonTimoutBar) {
            int buttonTimeout = (Integer) newValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.BUTTON_BACKLIGHT_TIMEOUT, buttonTimeout);
            return true;
        } else if (preference == mManualButtonBrightness) {
            int buttonBrightness = (Integer) newValue;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.CUSTOM_BUTTON_BRIGHTNESS, buttonBrightness);
            return true;
        } else if (preference == mNavbarVisibility) {
            if (mIsNavSwitchingMode) {
                return false;
            }
            mIsNavSwitchingMode = true;
            boolean showing = ((Boolean)newValue);
            Settings.System.putIntForUser(resolver, Settings.System.FORCE_SHOW_NAVBAR,
                    showing ? 1 : 0, UserHandle.USER_CURRENT);
            mNavbarVisibility.setChecked(showing);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mIsNavSwitchingMode = false;
                }
            }, 1500);
            return true;
        } else {
            return false;
        }
    }


    @Override
    public int getMetricsCategory() {
        return MetricsEvent.SPARK_SETTINGS;
    }

    private static boolean isKeyDisablerSupported(Context context) {
        final LineageHardwareManager hardware = LineageHardwareManager.getInstance(context);
        return hardware.isSupported(LineageHardwareManager.FEATURE_KEY_DISABLE);
    }

    /**
     * For Search.
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.spark_settings_misc) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    LineageHardwareManager mLineageHardware = LineageHardwareManager.getInstance(context);

                    if (!isKeyDisablerSupported(context)) {
                        keys.add(HWKEY_DISABLE);
                        keys.add(CATEGORY_HWKEY);
                        keys.add(CATEGORY_BACK);
                        keys.add(CATEGORY_HOME);
                        keys.add(CATEGORY_MENU);
                        keys.add(CATEGORY_ASSIST);
                        keys.add(CATEGORY_APPSWITCH);
                    }

                    return keys;
                }
            };
}
