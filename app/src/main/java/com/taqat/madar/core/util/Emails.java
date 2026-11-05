package com.taqat.madar.core.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * التحقق من البريد الإلكتروني وتوحيد صيغته قبل إرساله للمصادقة.
 * البريد هو هوية الحساب في مدار، لذلك يجب أن يكون صحيحاً وموحّد الأحرف.
 */
public final class Emails {

    /** صيغة عملية: نص @ نص . امتداد (مثل gmail.com و outlook.co.uk) */
    private static final Pattern PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9.\\-]{1,188}\\.[A-Za-z]{2,}$");

    private Emails() {
    }

    /** هل الصيغة مقبولة للدخول أو التسجيل؟ */
    public static boolean isValid(String email) {
        String normalized = normalize(email);
        return !normalized.isEmpty() && PATTERN.matcher(normalized).matches();
    }

    /**
     * إزالة المسافات وتحويل الأحرف صغيرة، لأن Firebase يفرّق بين الكبيرة والصغيرة
     * فيمنع تكرار نفس البريد بصيغ مختلفة.
     */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.US);
    }
}
