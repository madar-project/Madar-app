package com.taqat.madar.data.model;

import com.taqat.madar.R;

/**
 * أنواع الحسابات في تطبيق مدار.
 * القيم النصية هي نفسها التي تُخزَّن في Firestore، لذلك يجب إبقاؤها ثابتة
 * لأن لوحة التحكم (الموقع) ستقرأ نفس النص.
 */
public enum Role {

    CUSTOMER("customer"),
    MERCHANT("merchant"),
    PROFESSIONAL("professional"),
    DELIVERY("delivery");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    /** القيمة المخزنة في قاعدة البيانات وفي نواقل Intent */
    public String value() {
        return value;
    }

    /**
     * تحويل قيمة مخزنة إلى الدور المناسب، مع الزبون كقيمة افتراضية آمنة.
     */
    public static Role fromValue(String value) {
        for (Role role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        return CUSTOMER;
    }

    /**
     * الزبون يدخل مباشرة، أما باقي الأدوار فتحتاج موافقة من لوحة التحكم
     * قبل تفعيل الحساب.
     */
    public boolean requiresApproval() {
        return this != CUSTOMER;
    }

    /** التسمية العربية للدور، تُعرض في شاشة المراجعة والواجهة الأساسية */
    public int displayNameRes() {
        switch (this) {
            case MERCHANT:
                return R.string.role_merchant;
            case PROFESSIONAL:
                return R.string.role_professional;
            case DELIVERY:
                return R.string.role_delivery;
            default:
                return R.string.role_customer;
        }
    }
}
