package com.taqat.madar.data.local;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * تفضيلات التنبيه الصوتي على جهاز التاجر نفسه.
 *
 * الاختيار يبقى على الجهاز لا في Firestore: نغمة الطلب تخصّ السماعة التي يحملها
 * التاجر، ولو حُفظت في وثيقته لأجبرت كل أجهزته على قرار واحد — ولاحتاجت قراءة
 * من السيرفر قبل أن يعرف التاجر هل يرنّ هاتفه.
 *
 * مسار الإشعارات لم يُبنَ بعد، فتقرأ هذه الفئة الاختيار وتحفظه فقط، وهو أوّل ما
 * يقرأه ذلك المسار حين يُفعَّل.
 */
public class NotificationPrefs {

    private static final String PREFS_NAME = "madar_notifications";

    /** هل يرنّ الهاتف عند وصول طلب جديد؟ */
    private static final String KEY_ORDER_SOUND = "order_sound_enabled";

    private final SharedPreferences preferences;

    public NotificationPrefs(Context context) {
        this.preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * الصوت يشتغل افتراضياً: التاجر الذي لم يفتح الإعدادات أصلاً لم يطلب إسكات
     * التنبيه، والخيار السلبي كان سيُفوت عليه طلبات أول ما تُفعَّل الإشعارات.
     */
    public boolean isOrderSoundEnabled() {
        return preferences.getBoolean(KEY_ORDER_SOUND, true);
    }

    public void setOrderSoundEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ORDER_SOUND, enabled).apply();
    }
}
