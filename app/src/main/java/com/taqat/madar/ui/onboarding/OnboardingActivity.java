package com.taqat.madar.ui.onboarding;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayoutMediator;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.data.local.SessionManager;
import com.taqat.madar.databinding.ActivityOnboardingBinding;
import com.taqat.madar.ui.auth.role.UserTypeActivity;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {
    // تعريف متغير الـ View Binding بناءً على اسم ملف الـ XML (activity_onboarding.xml)
    private ActivityOnboardingBinding binding;
    private OnboardingAdapter onboardingAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // لون شريط الحالة يُضبط من الثيم، وهنا نضيف هامش الحواف فقط
        InsetUtils.applySystemBarPadding(binding.getRoot());
        setupOnboardingItems();
        setupPageTransformer(); // إضافة تأثير الأنيميشن
        setupActions();
    }

    private void setupOnboardingItems() {
        List<OnboardingItem> onboardingItems = new ArrayList<>();

        // الصفحة الأولى
        onboardingItems.add(new OnboardingItem(
                R.drawable.img_onboarding_1,
                getString(R.string.onboarding_title_1),
                getString(R.string.onboarding_desc_1)
        ));

        // الصفحة الثانية
        onboardingItems.add(new OnboardingItem(
                R.drawable.img_onboarding_2,
                getString(R.string.onboarding_title_2),
                getString(R.string.onboarding_desc_2)
        ));

        // الصفحة الثالثة
        onboardingItems.add(new OnboardingItem(
                R.drawable.img_onboarding_3,
                getString(R.string.onboarding_title_3),
                getString(R.string.onboarding_desc_3)
        ));
        onboardingAdapter = new OnboardingAdapter(onboardingItems);
        binding.viewPager.setAdapter(onboardingAdapter);

        // ربط الـ TabLayout مع الـ ViewPager2 باستخدام الـ Binding مباشرة
        new TabLayoutMediator(binding.tabIndicator, binding.viewPager, (tab, position) -> {
            // نقاط المؤشر
        }).attach();

        // تغيير نص الزر إلى "ابدأ الآن" في الصفحة الأخيرة
        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == onboardingItems.size() - 1) {
                    binding.btnNext.setText(R.string.onboarding_start_now);
                } else {
                    binding.btnNext.setText(R.string.onboarding_next);
                }
            }
        });
    }


    // تطبيق أنيميشن الانتقال السلس بين السلايدات
    private void setupPageTransformer() {
        binding.viewPager.setPageTransformer(new ZoomOutPageTransformer());
    }

    private void setupActions() {
        binding.btnNext.setOnClickListener(v -> {
            if (binding.viewPager.getCurrentItem() < onboardingAdapter.getItemCount() - 1) {
                binding.viewPager.setCurrentItem(binding.viewPager.getCurrentItem() + 1);
            } else {
                finishOnboarding();
            }
        });

        binding.tvSkip.setOnClickListener(v -> finishOnboarding());
    }

    /**
     * إنهاء الجولة التعريفية والانتقال لاختيار نوع الحساب،
     * مع حفظ حالة "تم المشاهدة" حتى لا تظهر هذه الشاشات عند كل تشغيل.
     */
    private void finishOnboarding() {
        new SessionManager(this).completeOnboarding();

        startActivity(new Intent(OnboardingActivity.this, UserTypeActivity.class));
        finish();
    }
}