package com.taqat.madar.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.databinding.ActivityCustomerHomeBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;
import com.taqat.madar.ui.customer.browse.CustomerSectionFragment;
import com.taqat.madar.ui.customer.home.CustomerHomeFragment;
import com.taqat.madar.ui.customer.home.CustomerSoonFragment;
import com.taqat.madar.ui.customer.menu.CustomerMenuFragment;

/**
 * مضيفة تبويبات الزبون: حاوية واحدة وشريط أسفلها يقرّر أي صفحة تظهر داخلها، وهي
 * أول شاشة يراها الزبون بعد فتح التطبيق لأن حساب الزبون لا يمرّ على المراجعة.
 *
 * مثل مضيفة التاجر تماماً: التبويبات صفحات لا شاشات مستقلة، فزر الرجوع في الجهاز
 * يخرج من التطبيق كله بدل أن يقلّب التبويبات واحدة واحدة.
 *
 * شاشة القسم استثناء واحد: تُفتح طبقة فوق التبويب وتُضاف إلى مكدّس الرجوع، لأن
 * الرسمة تبقي الشريط السفلي ظاهراً ومضيئاً على «الرئيسية» وهو يعرض المطاعم والمحلات —
 * فهي امتداد للرئيسية لا تبويب خامس. فالرجوع في الجهاز ينزل عنها إلى الرئيسية،
 * وأي ضغطة على الشريط تسقطها قبل أن تعرض صفحة التبويب.
 *
 * صفحة المتجر طبقة فوق طبقة القسم بنفس السبب: الزبون يدخل المنيو من لائحة المطاعم
 * ويرجع منها إليها، والشريط السفلي يبقى على «الرئيسية» مضيئاً في الاثنتين.
 */
public class CustomerHomeActivity extends AppCompatActivity {

    /** يُستخدم حين تُطلب رئيسية الزبون على تبويب بعينه، كما تعود بطاقة الطلب النشط */
    public static final String EXTRA_TAB = "customer_home_tab";

    /** التبويب المعروض: يدوّره النظام مع الشاشة، فلا تُبنى صفحة فوق الصفحة المستعادة */
    private static final String STATE_TAB = "shown_tab";

    private ActivityCustomerHomeBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private int shownTab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            // لا جلسة: الزبون خارج التطبيق أصلاً، فيعود إلى شاشة الدخول بلا وسيط
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
            closeOverlays();
            showTab(item.getItemId());
            return true;
        });

        if (savedInstanceState == null) {
            showTab(tab);
        } else {
            shownTab = savedInstanceState.getInt(STATE_TAB, R.id.tab_home);

            // والشريط يضيء على التبويب المعاد عرضُه لا على الرئيسية
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                binding.bottomNav.setSelectedItemId(shownTab);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, shownTab);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        // العودة للمضيفة من تطبيق خارجي تصل بنية فتح جديد بلا تبويب مطلوب: ما دام
        // التبويب غير مذكور نُبقي المعروض كما هو، ولا نرجع الزبون للرئيسية سراً
        if (!intent.hasExtra(EXTRA_TAB)) {
            return;
        }
        closeOverlays();
        binding.bottomNav.setSelectedItemId(intent.getIntExtra(EXTRA_TAB, R.id.tab_home));
    }

    /**
     * فتح لائحة قسم داخل نفس الحاوية، بطبقة تُضاف إلى مكدّس الرجوع وتُغلق به.
     *
     * لا تغيير على الشريط السفلي: القسم ابن الرئيسية في الرسمة، فيبقى الضوء عليها.
     * وكلمة البحث تمرّ معها، فتفتح اللائحة وقد صفّيت عليها من أول ما تظهر.
     */
    public void openSection(@NonNull CustomerSection section, @NonNull String query) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.fragment_container,
                        CustomerSectionFragment.newInstance(section, query))
                .setReorderingAllowed(true)
                .addToBackStack(CustomerSectionFragment.BACK_STACK)
                .commit();
    }

    /**
     * فتح منيو متجر واحد فوق لائحة القسم، بطبقة ثانية في نفس المكدّس.
     *
     * الرجوع منها ينزل للائحة لا للرئيسية، لأن الزبون كان يقرأ أسماء المتاجر قبل
     * أن يدخل هنا، والشريط السفلي لا يتغيّر: المنيو مثل لائحته امتداد للرئيسية.
     */
    public void openStore(@NonNull String storeId) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fragment_enter, R.anim.fragment_exit)
                .replace(R.id.fragment_container, CustomerMenuFragment.newInstance(storeId))
                .setReorderingAllowed(true)
                .addToBackStack(CustomerMenuFragment.BACK_STACK)
                .commit();
    }

    /**
     * نقل الزبون لتبويب بعينه من داخل صفحة: تستخدمه بطاقة الطلب النشط لتذهب لطلباتي.
     * الشريط هو صاحب القرار لا الصفحة، فيسقط طبقات التصفّح أولاً ويضيء على المطلوب.
     */
    public void openTab(int tabId) {
        closeOverlays();
        binding.bottomNav.setSelectedItemId(tabId);
    }

    /**
     * تنفيذ على خيط الواجهة إن كانت الشاشة ما زالت حيّة: قارئ قد يصل بلقطة بعد
     * أن ينقلب الزبون لتبويب آخر أو يُغلق التطبيق.
     */
    public void runOnUi(@NonNull Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    /**
     * إسقاط طبقات التصفّح إن كانت مفتوحة: المنيو أولاً ثم لائحته، لأن الزبون قد يصل
     * المنيو من غير اللائحة يوم تُبنى «طلباتي» وتفتح المتجر مباشرة، فإسقاط طبقة واحدة
     * يترك فوق التبويب صفحة لا يعرف أحد كيف خرج منها.
     */
    private void closeOverlays() {
        FragmentManager layers = getSupportFragmentManager();
        layers.popBackStackImmediate(CustomerMenuFragment.BACK_STACK,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        layers.popBackStackImmediate(CustomerSectionFragment.BACK_STACK,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    /**
     * عرض صفحة التبويب داخل الحاوية: إعادة الضبط على التبويب الحالي تُكرّر الحركة
     * وتعيد قراءة بيانات من سيرفر بلا سبب.
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
        if (tab == R.id.tab_orders) {
            return CustomerSoonFragment.newInstance(R.string.nav_my_orders);
        }
        if (tab == R.id.tab_addresses) {
            return CustomerSoonFragment.newInstance(R.string.nav_addresses);
        }
        if (tab == R.id.tab_account) {
            return CustomerSoonFragment.newInstance(R.string.nav_account);
        }
        return new CustomerHomeFragment();
    }
}
