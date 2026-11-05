package com.taqat.madar.data.local;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * يحفظ حالات الجهاز البسيطة على الهاتف نفسه (SharedPreferences)،
 * مثل: هل شاهد المستخدم شريحة الـ Onboarding لأول مرة؟
 *
 * ملاحظة: بيانات المستخدم الرسمية (الدور وحالة الموافقة) ستكون في Firebase،
 * وهذا الكلاس فقط للتفضيلات المحلية السريعة.
 */
public class SessionManager {

    private static final String PREFS_NAME = "madar_session";
    private static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** هل أنهى المستخدم الجولة التعريفية من قبل؟ */
    public boolean isOnboardingCompleted() {
        return preferences.getBoolean(KEY_ONBOARDING_COMPLETED, false);
    }

    /** تُستدعى عند مغادرة الـ Onboarding حتى لا تظهر مرة أخرى */
    public void completeOnboarding() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply();
    }
}
