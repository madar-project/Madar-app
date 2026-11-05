package com.taqat.madar.ui.merchant.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.databinding.ActivityMerchantHomeBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;
import com.taqat.madar.ui.merchant.menu.MerchantMenuFragment;
import com.taqat.madar.ui.merchant.notifications.MerchantNotificationFeed;
import com.taqat.madar.ui.merchant.offers.MerchantOffersFragment;
import com.taqat.madar.ui.merchant.orders.MerchantOrdersFragment;
import com.taqat.madar.ui.merchant.settings.MerchantSettingsFragment;

/**
 * مضيفة تبويبات التاجر: لا محتوى فيها، بل حاوية واحدة وشريط أسفلها يقرّر أي صفحة
 * تظهر داخلها.
 *
 * التبويبات صفحات لا شاشات مستقلة: الشريط يبقى مكانه، والتنقّل بينهم يصير تحريك
 * صفحة محل أخرى في نفس الحاوية، فبيانات كل صفحة تُقرأ حين تُعرض لا عند كل ضغطة،
 * وزر الرجوع في الجهاز يخرج من التطبيق كله بدل أن يقلّب التبويبات واحدة واحدة.
 *
 * التوجيه إلى هنا قرار حالة الحساب في users/{uid}، فلا بوابة ثانية على حالة وثيقة
 * المتجر: بوّابتان على وثيقتين ترمي كل واحدة منهما الأخرى حلقة لا تنتهي.
 */
public class MerchantHomeActivity extends AppCompatActivity {

    /** يُستخدم حين تعود شاشة الإضافة إلى تبويب بعينه من شريطها السفلي */
    public static final String EXTRA_TAB = "merchant_home_tab";

    /** التبويب المعروض: يدوّره النظام مع الشاشة، فلا تُبنى صفحة فوق الصفحة المستعادة */
    private static final String STATE_TAB = "shown_tab";

    private ActivityMerchantHomeBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private int shownTab;

    /**
     * مولّد إشعارات التاجر: هنا لا في صفحة التبويب، لأن الصفحة تُهدم مع كل تبديل
     * تبويب ويذهب مستمعها معها، والطلب قد يصل والتاجر في الإعدادات لا في الطلبات.
     */
    private MerchantNotificationFeed notificationFeed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMerchantHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            // لا جلسة: التاجر خارج التطبيق أصلاً، فيعود إلى شاشة الدخول بلا وسيط
            Toast.makeText(this, R.string.error_account_required, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        int tab = getIntent().getIntExtra(EXTRA_TAB, R.id.tab_home);

        // تظبير التبويب قبل تركيب المستمع: setSelectedItemId يستدعي المستمع نفسه،
        // ولو جاء بعده لأعاد عرض الصفحة مرتين عند كل فتح
        MenuItem highlighted = binding.bottomNav.getMenu().findItem(tab);
        if (highlighted != null) {
            highlighted.setChecked(true);
        }

        binding.bottomNav.setOnItemSelectedListener(item -> {
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            showTab(tab);
        } else {
            // إدارة الصفحات تكفّلت بإعادة ما كان ظاهراً: يبقى رقم التبويب عندها لا عندنا
            shownTab = savedInstanceState.getInt(STATE_TAB, R.id.tab_home);

            // والشريط يضيء على التبويب المعاد عرضُه لا على الرئيسية: لو بقي ضاغطاً على
            // غيرها حسّ التاجر أن ضغطة عليه لا تغيّر شيئاً، فالصفحة تحته موجودة أصلاً
            binding.bottomNav.setSelectedItemId(shownTab);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, shownTab);
    }

    @Override
    protected void onStart() {
        super.onStart();

        FirebaseUser account = authRepository.currentUser();
        if (account != null) {
            notificationFeed = new MerchantNotificationFeed(this, account.getUid());
            notificationFeed.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (notificationFeed != null) {
            notificationFeed.stop();
            notificationFeed = null;
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // العودة إلى المضيفة من تطبيق خارجي تصل هنا كنيّة فتح من جديد بلا تبويب مطلوب:
        // ما دام التبويب غير مذكور نُبقي المعروض كما هو، ولا نرجع التاجر للرئيسية سراً
        if (!intent.hasExtra(EXTRA_TAB)) {
            return;
        }

        // العودة من شاشة الإضافة بتبويب مختار: الشريط هو صاحب القرار، فلا نبدّل الصفحة سراً
        int tab = intent.getIntExtra(EXTRA_TAB, R.id.tab_home);
        binding.bottomNav.setSelectedItemId(tab);
    }

    /**
     * عرض صفحة التبويب داخل الحاوية، بحركة دخول واحدة: إعادة الضبط على التبويب
     * الحالي تُكرّر الحركة وتعيد قراءة بيانات من سيرفر بلا سبب.
     */
    private void showTab(int tab) {
        if (tab == shownTab) {
            return;
        }
        shownTab = tab;

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.fragment_container, pageFor(tab))
                .setReorderingAllowed(true)
                .commit();
    }

    @NonNull
    private Fragment pageFor(int tab) {
        if (tab == R.id.tab_menu) {
            return new MerchantMenuFragment();
        }
        if (tab == R.id.tab_orders) {
            return new MerchantOrdersFragment();
        }
        if (tab == R.id.tab_offers) {
            return new MerchantOffersFragment();
        }
        if (tab == R.id.tab_settings) {
            return new MerchantSettingsFragment();
        }
        return new MerchantHomeFragment();
    }
}
