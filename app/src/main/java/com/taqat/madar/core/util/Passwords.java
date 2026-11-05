package com.taqat.madar.core.util;

/**
 * قواعد كلمة المرور في مدار: لا تقل عن ٨ أحرف، وتحتوي حروفاً وأرقاماً معاً.
 * التحقق هنا يمنع إرسال كلمة مرور ضعيفة لـ Firebase قبل أن ترفضه.
 */
public final class Passwords {

    /** أقل عدد مسموح من الأحرف */
    public static final int MIN_LENGTH = 8;

    private Passwords() {
    }

    /** هل تحقق كلمة المرور شروط الأمان؟ */
    public static boolean isStrongEnough(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int index = 0; index < password.length(); index++) {
            char symbol = password.charAt(index);
            hasLetter = hasLetter || Character.isLetter(symbol);
            hasDigit = hasDigit || Character.isDigit(symbol);
        }
        return hasLetter && hasDigit;
    }

    /** هل التأكيد مطابق لكلمة المرور؟ */
    public static boolean doMatch(String password, String confirmation) {
        return password != null && password.equals(confirmation);
    }
}
