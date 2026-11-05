package com.taqat.madar.core.ui;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * أدوات مشتركة للتعامل مع حواف الشاشة (شريط الحالة فوق والشريط التنقلي أسفل).
 */
public final class InsetUtils {
    private InsetUtils() {
    }

    /**
     * يضيف هامش النظام إلى الـ padding الموجود أصلاً في التصميم بدل استبداله،
     * حتى لا تضيع المسافات التي حددها المصمم في ملف الـ XML.
     */
    public static void applySystemBarPadding(View root) {
        if (root == null) {
            return;
        }

        // نتذكر الـ padding الأصلي مرة واحدة، لأن النظام قد يعيد إرسال الـ insets أكثر من مرة
        final int[] basePadding = {
                root.getPaddingLeft(),
                root.getPaddingTop(),
                root.getPaddingRight(),
                root.getPaddingBottom()
        };

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    basePadding[0] + systemBars.left,
                    basePadding[1] + systemBars.top,
                    basePadding[2] + systemBars.right,
                    basePadding[3] + systemBars.bottom);
            return insets;
        });
    }
}
