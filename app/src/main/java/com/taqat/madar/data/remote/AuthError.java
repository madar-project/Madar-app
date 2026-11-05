package com.taqat.madar.data.remote;

import com.taqat.madar.R;

/**
 * أنواع الأخطاء التي ترجع من المصادقة، وكل نوع له نص عربي جاهز للعرض.
 * استخدام النوع بدل النص يوحّد الرسالة بين شاشة التسجيل وشاشة تسجيل الدخول.
 */
public enum AuthError {

    /** صيغة البريد غير صحيحة */
    EMAIL_INVALID(R.string.error_email_invalid),

    /** البريد مستخدم بالفعل في حساب آخر */
    EMAIL_IN_USE(R.string.error_email_in_use),

    /** كلمة المرور لا تحقق شروط الأمان */
    PASSWORD_WEAK(R.string.error_password_weak),

    /** التأكيد لا يطابق كلمة المرور */
    PASSWORD_MISMATCH(R.string.error_password_mismatch),

    /** البريد أو كلمة المرور غير صحيحة */
    LOGIN_FAILED(R.string.error_login_failed),

    /** لا يوجد حساب بهذا البريد */
    ACCOUNT_NOT_FOUND(R.string.error_account_not_found),

    /** محاولات كثيرة متتالية، يُطلب الانتظار */
    TOO_MANY_ATTEMPTS(R.string.error_auth_too_many),

    /** طريقة الدخول بالبريد موقوفة في إعدادات Firebase */
    PROVIDER_DISABLED(R.string.error_auth_provider_disabled),

    /** تعذّر الوصول لسيرفر المصادقة */
    NETWORK(R.string.error_auth_network),

    /** أي خطأ غير متوقع */
    UNKNOWN(R.string.error_auth_unknown);

    private final int messageRes;

    AuthError(int messageRes) {
        this.messageRes = messageRes;
    }

    public int messageRes() {
        return messageRes;
    }
}
