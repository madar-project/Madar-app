package com.taqat.madar.ui.merchant.settings;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.databinding.ActivityMerchantPoliciesBinding;

/**
 * شاشة سياسات الخدمة: نصّ ثابت يُقرأ ولا يُكتب، فكل ما فيها في ملف النصوص.
 *
 * تفتح من سطر واحد في تبويب الإعدادات، ومخرجها زر الرجوع في رأسها وزر الرجوع في
 * الجهاز — لا قرار يتخذ فيها ولا حفظ يترتب عليها.
 */
public class MerchantPoliciesActivity extends AppCompatActivity {

    private ActivityMerchantPoliciesBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMerchantPoliciesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
