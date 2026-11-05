package com.taqat.madar.ui.auth.role;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.taqat.madar.R;
import com.taqat.madar.core.constants.Extras;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.data.model.Role;
import com.taqat.madar.databinding.ActivityUserTypeBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;
import com.taqat.madar.ui.auth.register.RegisterActivity;
import com.taqat.madar.ui.merchant.register.MerchantRegisterActivity;

/**
 * شاشة اختيار نوع الحساب (الزبون / التاجر / المهني / مندوب التوصيل).
 * الزبون يدخل مباشرة بعد التسجيل، وباقي الأدوار تحتاج موافقة لوحة التحكم.
 *
 * لكل دور شاشة تسجيل بمحتواها: التاجر يملأ بيانات متجره في شاشته الخاصة
 * ويصل طلبه إلى المراجعة فور إرساله، بينما تمر الأدوار الأخرى على التسجيل العام.
 */
public class UserTypeActivity extends AppCompatActivity {

    private ActivityUserTypeBinding binding;
    private Role selectedRole = Role.CUSTOMER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityUserTypeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ضبط حواف الشاشة مع الحفاظ على المسافات الموجودة في التصميم
        InsetUtils.applySystemBarPadding(binding.getRoot());

        // تعيين الكارد الأول (زبون) كمختار افتراضياً عند فتح الشاشة
        selectRole(Role.CUSTOMER);

        // تفعيل النقر على الكاردات
        binding.cardCustomer.setOnClickListener(v -> selectRole(Role.CUSTOMER));
        binding.cardMerchant.setOnClickListener(v -> selectRole(Role.MERCHANT));
        binding.cardProfessional.setOnClickListener(v -> selectRole(Role.PROFESSIONAL));
        binding.cardDelivery.setOnClickListener(v -> selectRole(Role.DELIVERY));

        // زر المتابعة: التاجر له شاشة تسجيل مستقلة ببيانات متجره
        binding.btnContinue.setOnClickListener(v -> {
            Intent intent = new Intent(UserTypeActivity.this,
                    selectedRole == Role.MERCHANT
                            ? MerchantRegisterActivity.class
                            : RegisterActivity.class);
            intent.putExtra(Extras.USER_ROLE, selectedRole.value());
            startActivity(intent);
        });

        // رابط تسجيل الدخول
        binding.tvLoginLink.setOnClickListener(v ->
                startActivity(new Intent(UserTypeActivity.this, LoginActivity.class)));
    }

    private void selectRole(Role role) {
        selectedRole = role;

        // 1. تحديد الكارد المطلوب وإرجاع باقي الكاردات لحالتها العادية
        highlightCard(binding.cardCustomer, binding.ivCustomerIcon, role == Role.CUSTOMER);
        highlightCard(binding.cardMerchant, binding.ivMerchantIcon, role == Role.MERCHANT);
        highlightCard(binding.cardProfessional, binding.ivProfessionalIcon, role == Role.PROFESSIONAL);
        highlightCard(binding.cardDelivery, binding.ivDeliveryIcon, role == Role.DELIVERY);

        // 2. تحديث نص زر المتابعة حسب الدور المختار
        binding.btnContinue.setText(getString(labelFor(role)));
    }

    private int labelFor(Role role) {
        switch (role) {
            case MERCHANT:
                return R.string.btn_continue_merchant;
            case PROFESSIONAL:
                return R.string.btn_continue_professional;
            case DELIVERY:
                return R.string.btn_continue_delivery;
            default:
                return R.string.btn_continue_customer;
        }
    }

    /**
     * تظليل الكارد: إطار برتقالي حول الكارد، وأيقونة بيضاء داخل خلفية برتقالية.
     * حالة التحديد تُضبط على الكارد وعلى الأيقونة معاً، لأن خلفيتهما selector
     * (bg_selectable_card و bg_icon_selector) تقرأ state_selected من نفس الـ View.
     */
    private void highlightCard(View card, ImageView icon, boolean isSelected) {
        card.setSelected(isSelected);
        icon.setSelected(isSelected);

        int iconColor = ContextCompat.getColor(this, isSelected ? R.color.white : R.color.secondary_navy);
        icon.setImageTintList(ColorStateList.valueOf(iconColor));
    }
}
