package com.taqat.madar.core.constants;

/**
 * مفاتيح البيانات التي تُمرَّر بين الشاشات عبر Intent.
 * وضعها في مكان موحّد يمنع اختلاف المفتاح بين شاشة أرسل وأخرى استقبلت.
 */
public final class Extras {

    private Extras() {
    }

    /** نوع الحساب المختار في شاشة «اختر نوع حسابك»، ويصل إلى شاشة التسجيل */
    public static final String USER_ROLE = "user_role";
}
