package dev.opencloud.region;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

/**
 * Static landing points for the call sites rewritten by the build tool.
 * Every method falls back to the real framework method when the user has
 * turned the profile off, so the patch remains reversible at runtime.
 */
public final class Hooks {
    private Hooks() {
    }

    public static String country(TelephonyManager phone) {
        Profile profile = Config.currentProfile();
        return profile == null ? phone.getSimCountryIso() : profile.code();
    }

    public static String networkCountry(TelephonyManager phone) {
        Profile profile = Config.currentProfile();
        return profile == null ? phone.getNetworkCountryIso() : profile.code();
    }

    public static String simOperator(TelephonyManager phone) {
        Profile profile = Config.currentProfile();
        return profile == null ? phone.getSimOperator() : profile.numeric();
    }

    public static String networkOperator(TelephonyManager phone) {
        Profile profile = Config.currentProfile();
        return profile == null ? phone.getNetworkOperator() : profile.numeric();
    }

    public static String simOperatorName(TelephonyManager phone) {
        Profile profile = Config.currentProfile();
        return profile == null ? phone.getSimOperatorName() : profile.operator();
    }

    public static String networkOperatorName(TelephonyManager phone) {
        Profile profile = Config.currentProfile();
        return profile == null ? phone.getNetworkOperatorName() : profile.operator();
    }

    public static int simState(TelephonyManager phone) {
        return Config.currentProfile() == null
                ? phone.getSimState()
                : TelephonyManager.SIM_STATE_READY;
    }

    public static int simStateForSlot(TelephonyManager phone, int slot) {
        if (Config.currentProfile() == null) {
            return phone.getSimState(slot);
        }
        return slot == 0 ? TelephonyManager.SIM_STATE_READY : TelephonyManager.SIM_STATE_ABSENT;
    }

    public static boolean iccPresent(TelephonyManager phone) {
        return Config.currentProfile() == null ? phone.hasIccCard() : true;
    }

    public static boolean roaming(TelephonyManager phone) {
        return Config.currentProfile() == null ? phone.isNetworkRoaming() : false;
    }

    public static int carrierId(TelephonyManager phone) {
        return Config.currentProfile() == null
                ? phone.getSimCarrierId()
                : TelephonyManager.UNKNOWN_CARRIER_ID;
    }

    public static int phoneCount(TelephonyManager phone) {
        return Config.currentProfile() == null ? phone.getPhoneCount() : 1;
    }

    public static int modemCount(TelephonyManager phone) {
        return Config.currentProfile() == null ? phone.getActiveModemCount() : 1;
    }

    public static int phoneType(TelephonyManager phone) {
        return Config.currentProfile() == null
                ? phone.getPhoneType()
                : TelephonyManager.PHONE_TYPE_GSM;
    }

    public static int activeSubscriptions(SubscriptionManager subscriptions) {
        return Config.currentProfile() == null
                ? subscriptions.getActiveSubscriptionInfoCount()
                : 1;
    }

    public static String subscriptionCountry(SubscriptionInfo info) {
        Profile profile = Config.currentProfile();
        return profile == null ? info.getCountryIso() : profile.code();
    }

    public static int defaultDataId() {
        return Config.currentProfile() == null
                ? SubscriptionManager.getDefaultDataSubscriptionId()
                : 1;
    }

    public static int defaultVoiceId() {
        return Config.currentProfile() == null
                ? SubscriptionManager.getDefaultVoiceSubscriptionId()
                : 1;
    }

    public static int defaultSmsId() {
        return Config.currentProfile() == null
                ? SubscriptionManager.getDefaultSmsSubscriptionId()
                : 1;
    }

    public static int activeDataId() {
        return Config.currentProfile() == null
                ? SubscriptionManager.getActiveDataSubscriptionId()
                : 1;
    }

    public static Typeface typeface(String family, int style) {
        return Fonts.create(family, style);
    }

    public static Typeface typeface(Typeface base, int style) {
        return Fonts.create(base, style);
    }

    public static Typeface defaultTypeface(int style) {
        return Fonts.defaultStyle(style);
    }

    public static Typeface typefaceFromAsset(AssetManager assets, String path) {
        return Fonts.fromAsset(assets, path);
    }

    public static Typeface typefaceFromFile(String path) {
        return Fonts.fromFile(path);
    }
}
