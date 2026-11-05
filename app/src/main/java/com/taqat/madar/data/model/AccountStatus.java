package com.taqat.madar.data.model;

import com.taqat.madar.R;

/**
 * حالة الحساب أو حالة ملف النشاط (متجر/مركبة/خدمة)، وهي التي تقرر هل يدخل المستخدم
 * تطبيقه أم ينتظر موافقة لوحة التحكم. القيم النصية ثابتة لأن لوحة التحكم (الموقع)
 * ستقرأ وتكتب نفس النصوص.
 */
public enum AccountStatus {

    /** بانتظار مراجعة إدارة مدار (تاجر / مهني / مندوب) */
    PENDING("pending"),

    /** مقبول ويمكنه استخدام التطبيق */
    APPROVED("approved"),

    /**
     * مرفوض. الرفض عند إدارة مدار يعني حذف الحساب وبياناته من القاعدة،
     * فلا يبقى لهذه الحالة وثيقة تُقرأ؛ وتبقى معرفة بها لأن قيمة مكتوبة يدوياً
     * أو وثيقة قديمة قد تصل بها التطبيق، فيتعامل معها كأضعف حالة لا كأقواها.
     */
    REJECTED("rejected");

    private final String value;

    AccountStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AccountStatus fromValue(String value) {
        for (AccountStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return PENDING;
    }

    /** التسمية العربية للحالة، تُعرض في الواجهة الأساسية وشاشة المراجعة */
    public int displayNameRes() {
        switch (this) {
            case APPROVED:
                return R.string.status_approved;
            case REJECTED:
                return R.string.status_rejected;
            default:
                return R.string.status_pending;
        }
    }
}
