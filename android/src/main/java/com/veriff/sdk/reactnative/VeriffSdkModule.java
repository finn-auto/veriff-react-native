package com.veriff.sdk.reactnative;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.veriff.VeriffBranding;
import com.veriff.VeriffConfiguration;
import com.veriff.VeriffResult;
import com.veriff.VeriffResult.Status;
import com.veriff.VeriffSdk;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import mobi.lab.veriff.data.VeriffConstants;
import okhttp3.HttpUrl;

import static android.app.Activity.RESULT_CANCELED;
import static com.veriff.VeriffResult.Status.CANCELED;
import static com.veriff.VeriffResult.Status.ERROR;

public class VeriffSdkModule extends ReactContextBaseJavaModule {
    /**
     * Indicates that the parameters passed to {@link #launchVeriff(ReadableMap, Promise)} were invalid.
     */
    private static final String ERROR_INVALID_ARGS = "E_VERIFF_INVALID_ARGUMENTS";

    /**
     * Indicates that the activity is no longer attached when the
     * {@link #launchVeriff(ReadableMap, Promise)} method was invoked.
     */
    private static final String ERROR_ACTIVITY_NOT_ATTACHED = "E_VERIFF_ACTIVITY_NOT_ATTACHED";

    /**
     * The user canceled left the flow before completing it.
     */
    private static final String STATUS_CANCELED = "STATUS_CANCELED";

    /**
     * The flow was completed, note that this doesn't mean there's a decision yet.
     */
    private static final String STATUS_DONE = "STATUS_DONE";

    /**
     * The flow did not complete successfully.
     */
    private static final String STATUS_ERROR = "STATUS_ERROR";

    private static final String DEFAULT_BASE_URL = "https://magic.veriff.me";
    private static final String TAG = "@veriff/react-native-sdk";
    private static final String JS_NAME = "VeriffSdk";

    private static final int VERIFF_REQUEST_CODE = 47239;

    private static final String KEY_TOKEN = "sessionToken";
    private static final String KEY_BASE_URL = "baseUrl";
    private static final String KEY_SESSION_URL = "sessionUrl";
    private static final String KEY_BRANDING = "branding";
    private static final String KEY_LOCALE = "locale";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ERROR = "error";

    private static final String KEY_THEME_COLOR = "themeColor";
    private static final String KEY_LOGO = "logo";
    private static final String KEY_NAVIGATION_BAR_IMAGE = "navigationBarImage";
    private static final String KEY_ANDROID_NOTIFICATION_ICON = "androidNotificationIcon";
    public static final String KEY_BACKGROUND_COLOR = "backgroundColor";
    public static final String KEY_STATUS_BAR_COLOR = "statusBarColor";
    public static final String KEY_PRIMARY_TEXT_COLOR = "primaryTextColor";
    public static final String KEY_SECONDARY_TEXT_COLOR = "secondaryTextColor";
    public static final String KEY_PRIMARY_BUTTON_BACKGROUND_COLOR = "primaryButtonBackgroundColor";
    public static final String KEY_BUTTON_CORNER_RADIUS = "buttonCornerRadius";
    private static final String KEY_CUSTOM_INTRO_SCREEN = "customIntroScreen";
    private static final String KEY_RN_IMAGE_URI = "uri";

    private static final String ERROR_UNABLE_TO_ACCESS_CAMERA = "UNABLE_TO_ACCESS_CAMERA";
    private static final String ERROR_UNABLE_TO_RECORD_AUDIO = "UNABLE_TO_RECORD_AUDIO";
    private static final String ERROR_UNABLE_TO_START_CAMERA = "UNABLE_TO_START_CAMERA";
    private static final String ERROR_NO_IDENTIFICATION_METHODS_AVAILABLE = "NO_IDENTIFICATION_METHODS_AVAILABLE";
    private static final String ERROR_UNSUPPORTED_SDK_VERSION = "UNSUPPORTED_SDK_VERSION";
    private static final String ERROR_SESSION = "SESSION_ERROR";
    private static final String ERROR_SETUP = "SETUP_ERROR";
    private static final String ERROR_NETWORK = "NETWORK_ERROR";
    private static final String ERROR_UNKNOWN = "UNKNOWN_ERROR";
    private static final String ERROR_NFC_DISABLED = "NFC_DISABLED";
    private static final String ERROR_DEVICE_HAS_NO_NFC = "DEVICE_HAS_NO_NFC";

    private static final Map<String, Object> EXPORTED_CONSTANTS = new HashMap<>();

    static {
        // promise reject errors
        EXPORTED_CONSTANTS.put("errorInvalidArgs", ERROR_INVALID_ARGS);
        EXPORTED_CONSTANTS.put("errorActivityNotAttached", ERROR_ACTIVITY_NOT_ATTACHED);

        // promise resolve statuses
        EXPORTED_CONSTANTS.put("statusCanceled", STATUS_CANCELED);
        EXPORTED_CONSTANTS.put("statusDone", STATUS_DONE);
        EXPORTED_CONSTANTS.put("statusError", STATUS_ERROR);
    }

    private final ReactApplicationContext reactContext;

    public VeriffSdkModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return JS_NAME;
    }

    @Override
    public Map<String, Object> getConstants() {
        return EXPORTED_CONSTANTS;
    }

    public String extractToken(String startUrl) {
        // this is suboptimal but it is what it is.. the SDK returns us a token in the
        // INTENT_EXTRA_SESSION_URL for matching :(
        HttpUrl url = HttpUrl.parse(startUrl);
        if (url == null) {
            return null;
        }
        if (url.encodedPathSegments().size() < 2) {
            return null;
        }
        return url.encodedPathSegments().get(1);
    }

    @ReactMethod
    public void launchVeriff(ReadableMap configuration, final Promise promise) {
        try {
            String sessionToken;
            String startUrl;

            if (configuration.hasKey(KEY_SESSION_URL) && !TextUtils.isEmpty(configuration.getString(KEY_SESSION_URL))) {
                startUrl = configuration.getString(KEY_SESSION_URL);
                sessionToken = extractToken(startUrl);
                if (TextUtils.isEmpty(startUrl) || TextUtils.isEmpty(sessionToken)) {
                    promise.reject(ERROR_INVALID_ARGS, "Invalid session url " + startUrl);
                    return;
                }
            } else {
                sessionToken = configuration.hasKey(KEY_TOKEN) ? configuration.getString(KEY_TOKEN) : null;
                if (TextUtils.isEmpty(sessionToken)) {
                  promise.reject(ERROR_INVALID_ARGS, "No sessionToken in Veriff SDK configuration");
                  return;
                }

                String baseUrl = null;
                if (configuration.hasKey(KEY_BASE_URL)) {
                  baseUrl = configuration.getString(KEY_BASE_URL);
                }
                if (TextUtils.isEmpty(baseUrl)) {
                  baseUrl = DEFAULT_BASE_URL;
                }
                if (baseUrl.endsWith("/")) {
                  baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                startUrl = baseUrl + "/v/" + sessionToken;
            }


            Activity activity = getCurrentActivity();
            if (activity == null) {
                promise.reject(ERROR_ACTIVITY_NOT_ATTACHED, "No activity attached while launching Veriff");
                return;
            }

            ActivityEventListener listener = new BaseActivityEventListener() {
                @Override
                public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
                    if (requestCode == VERIFF_REQUEST_CODE) {
                        if (data == null) {
                            return;
                        }
                        String token = data.getStringExtra(VeriffConstants.INTENT_EXTRA_SESSION_URL);
                        if (!sessionToken.equals(token) && !startUrl.equals(token)) {
                            return;
                        }

                        reactContext.removeActivityEventListener(this);

                        VeriffResult veriffResult = VeriffResult.fromResultIntent(data);
                        WritableMap result = Arguments.createMap();
                        result.putString(KEY_TOKEN, sessionToken);

                        Status status = resultCode == RESULT_CANCELED ? CANCELED : ERROR;
                        if (veriffResult != null) {
                          status = veriffResult.getStatus();
                        }
                        result.putString(KEY_STATUS, statusToString(status));
                        if (veriffResult != null && veriffResult.getError() != null){
                          result.putString(KEY_ERROR, codeToError(veriffResult.getError()));
                        }

                        promise.resolve(result);
                    }
                }
            };

            reactContext.addActivityEventListener(listener);

            VeriffConfiguration.Builder configBuilder = new VeriffConfiguration.Builder();

            if (configuration.hasKey(KEY_CUSTOM_INTRO_SCREEN)) {
                configBuilder.customIntroScreen(configuration.getBoolean(KEY_CUSTOM_INTRO_SCREEN));
            }

            if (configuration.hasKey(KEY_BRANDING)) {
                ReadableMap brandConfig = configuration.getMap(KEY_BRANDING);
                if (brandConfig != null) {
                    VeriffBranding.Builder branding = new VeriffBranding.Builder();
                    if (brandConfig.hasKey(KEY_THEME_COLOR)) {
                        branding.themeColor(parseColor(brandConfig.getString(KEY_THEME_COLOR)));
                    }
                    if (brandConfig.hasKey(KEY_NAVIGATION_BAR_IMAGE)) {
                        handleLogo(activity, KEY_NAVIGATION_BAR_IMAGE, branding, brandConfig);
                    }
                    if (brandConfig.hasKey(KEY_LOGO)) {
                        handleLogo(activity, KEY_LOGO, branding, brandConfig);
                    }
                    if (brandConfig.hasKey(KEY_ANDROID_NOTIFICATION_ICON)) {
                        branding.notificationIcon(getDrawableId(activity, brandConfig.getString(KEY_ANDROID_NOTIFICATION_ICON)));
                    }
                    if (brandConfig.hasKey(KEY_BACKGROUND_COLOR)) {
                        branding.backgroundColor(parseColor(brandConfig.getString(KEY_BACKGROUND_COLOR)));
                    }
                    if (brandConfig.hasKey(KEY_STATUS_BAR_COLOR)) {
                        branding.statusBarColor(parseColor(brandConfig.getString(KEY_STATUS_BAR_COLOR)));
                    }
                    if (brandConfig.hasKey(KEY_PRIMARY_TEXT_COLOR)) {
                        branding.primaryTextColor(parseColor(brandConfig.getString(KEY_PRIMARY_TEXT_COLOR)));
                    }
                    if (brandConfig.hasKey(KEY_SECONDARY_TEXT_COLOR)) {
                        branding.secondaryTextColor(parseColor(brandConfig.getString(KEY_SECONDARY_TEXT_COLOR)));
                    }
                    if (brandConfig.hasKey(KEY_PRIMARY_BUTTON_BACKGROUND_COLOR)) {
                        branding.primaryButtonBackgroundColor(parseColor(brandConfig.getString(KEY_PRIMARY_BUTTON_BACKGROUND_COLOR)));
                    }
                    if (brandConfig.hasKey(KEY_BUTTON_CORNER_RADIUS)) {
                        branding.buttonCornerRadius((float)brandConfig.getDouble(KEY_BUTTON_CORNER_RADIUS));
                    }
                    configBuilder.branding(branding.build());
                }
            }

            if (configuration.hasKey(KEY_LOCALE)) {
                configBuilder.locale(Locale.forLanguageTag(configuration.getString(KEY_LOCALE)));
            }

            Intent intent = VeriffSdk.createLaunchIntent(activity, startUrl, configBuilder.build());
            activity.startActivityForResult(intent, VERIFF_REQUEST_CODE);

        } catch (Throwable t) {
              Log.e(TAG, "starting verification failed", t);
              promise.reject(t);
        }
    }

    private void handleLogo(Context context, String key, VeriffBranding.Builder branding, ReadableMap brandConfig) {
        ReadableType type = brandConfig.getType(key);
        if (ReadableType.String.equals(type)) {
            branding.toolbarIcon(getDrawableId(context, brandConfig.getString(key)));
        } else if (ReadableType.Map.equals(type)) {
            // check if it's a native RN image
            ReadableMap image = brandConfig.getMap(key);
            if (image != null) {
                if (image.hasKey(KEY_RN_IMAGE_URI)) {
                    String url = image.getString(KEY_RN_IMAGE_URI);
                    if (!TextUtils.isEmpty(url)) {
                        // check if url has an async scheme
                        if (isAsyncLogoUrl(url)) {
                            branding.toolbarIconProvider(new ReactNativeImageProvider(url));
                        } else {
                            branding.toolbarIcon(getDrawableId(context, url));
                        }
                    } else {
                        Log.w(TAG, "Image url is empty: " + url);
                    }
                } else {
                    Log.w(TAG, "Provided image does not have " + KEY_RN_IMAGE_URI + " key. Keys: ");
                    for (String imageKey : image.toHashMap().keySet()) {
                        Log.w(TAG, imageKey);
                    }
                }
            } else {
                Log.w(TAG, "Provided image is null");
            }
        } else {
          Log.w(TAG, "Unexpected image type: " + type);
        }
    }

    private boolean isAsyncLogoUrl(String url) {
        return url.startsWith("https://") || url.startsWith("http://") || url.startsWith("file://");
    }

    private int parseColor(String hexcolor) {
        if (hexcolor == null) {
            return 0;
        }
        if (hexcolor.startsWith("#")) {
            return parseColor(hexcolor.substring(1));
        }
        long color = Long.parseLong(hexcolor, 16);
        int a = 255;
        if (hexcolor.length() > 6) {
            a = ((int)color) & 0xff;
            color = color >>> 8;
        }
        int r = ((int)(color >>> 16)) & 0xff;
        int g = ((int)(color >>> 8)) & 0xff;
        int b = ((int)(color >>> 0)) & 0xff;
        return Color.argb(a, r, g, b);
    }

    private static int getDrawableId(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    private static String codeToError(VeriffResult.Error error) {
        switch (error) {
            case UNABLE_TO_ACCESS_CAMERA:
                return ERROR_UNABLE_TO_ACCESS_CAMERA;
            case UNABLE_TO_RECORD_AUDIO:
                return ERROR_UNABLE_TO_RECORD_AUDIO;
            case UNABLE_TO_START_CAMERA:
                return ERROR_UNABLE_TO_START_CAMERA;
            case UNSUPPORTED_SDK_VERSION:
                  return ERROR_UNSUPPORTED_SDK_VERSION;
            case SESSION_ERROR:
                  return ERROR_SESSION;
            case NETWORK_ERROR:
                  return ERROR_NETWORK;
            case SETUP_ERROR:
                  return ERROR_SETUP;
            case NFC_DISABLED:
              return ERROR_NFC_DISABLED;
            case DEVICE_HAS_NO_NFC:
              return ERROR_DEVICE_HAS_NO_NFC;
            case UNKNOWN_ERROR:
            default:
                return ERROR_UNKNOWN;
        }
    }

    private static String statusToString(Status status) {
      switch (status){
        case DONE: return STATUS_DONE;
        case CANCELED: return STATUS_CANCELED;
        default: return STATUS_ERROR;
      }
    }

}
