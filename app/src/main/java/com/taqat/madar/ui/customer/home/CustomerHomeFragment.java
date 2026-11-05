package com.taqat.madar.ui.customer.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.R;
import com.taqat.madar.data.model.MerchantProfile;
import com.taqat.madar.data.model.Order;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.MerchantProfileRepository;
import com.taqat.madar.data.remote.OrderRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.databinding.CardCustomerSectionBinding;
import com.taqat.madar.databinding.FragmentCustomerHomeBinding;
import com.taqat.madar.ui.customer.CustomerHomeActivity;
import com.taqat.madar.ui.customer.CustomerSection;

import java.util.List;
import java.util.Locale;

/**
 * الرئيسية للزبون: تحية ومنطقة وبحث وأربعة أقسام وشريط عرض وبطاقة طلب نشط.
 *
 * مصدر كل شيء هنا ما بناه التاجر: الأقسام تفتح لائحة متاجر معتمدة من merchants،
 * وبطاقة الطلب تأتي من orders التي يضعها الزبون نفسه. وما لا مصدر له يُترك كما هو
 * بلا تأليف: شريط العرض نص ثابت حتى تُبنى عروض المنصة، وشارة دقائق الوصول مخفية
 * لأن زمن الطريق لا يُعرف قبل مسار الديليفري.
 *
 * المنطقة محفوظة على الجهاز وحدها: لا حقل مدينة في وثيقة المتجر، فاختيارها اليوم
 * ذكرى للزبون لا شرط تصفية، ولو صارت تصفية لاحقاً لاحت إحداثيات وعناوين محفوظة.
 */
public class CustomerHomeFragment extends Fragment {

    /** حفظ الجهاز لمنطقة الزبون: لا علاقة له بالحساب ولا بقاعدة البيانات */
    private static final String PREFS = "customer_home";
    private static final String KEY_AREA = "area";

    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final OrderRepository orderRepository = new OrderRepository();
    private final MerchantProfileRepository storeRepository = new MerchantProfileRepository();

    private FragmentCustomerHomeBinding binding;
    private String uid = "";
    private ListenerRegistration ordersWatch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser account = authRepository.currentUser();
        if (account != null) {
            uid = account.getUid();
            loadCustomerName();
        }

        binding.tvLocation.setText(
                requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_AREA, getString(R.string.cust_location_default)));

        binding.btnChangeLocation.setOnClickListener(v -> openAreaPicker());
        binding.cardLocation.setOnClickListener(v -> openAreaPicker());

        binding.etSearch.setOnEditorActionListener((field, actionId, ignored) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            openSearch(field.getText().toString().trim());
            return true;
        });

        // التصفية ليست شاشة مستقلة: شريط التصنيفات موجود في اللائحة نفسها، فالزر يفتحها
        binding.btnFilter.setOnClickListener(v -> openSearch(""));

        bindSection(binding.cardRestaurants, CustomerSection.RESTAURANTS);
        bindSection(binding.cardMarkets, CustomerSection.MARKETS);
        bindSection(binding.cardServices, CustomerSection.SERVICES);
        bindSection(binding.cardDelivery, CustomerSection.DELIVERY);

        // TODO شاشة إشعارات الزبون لم تُسلَّم رسمتها بعد: الجرس ظاهر كما في الرسمة ولا يفتح شيئاً
        binding.ivNotifications.setOnClickListener(v -> Toast.makeText(requireContext(),
                R.string.msg_screen_coming_soon, Toast.LENGTH_SHORT).show());

        binding.btnTrack.setOnClickListener(v -> openOrdersTab());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!uid.isEmpty()) {
            watchOrders();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (ordersWatch != null) {
            ordersWatch.remove();
            ordersWatch = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** اسم الزبون في التحية: من وثيقة حسابه لا من اسم المتجر ولا من البريد */
    private void loadCustomerName() {
        userRepository.loadUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onLoaded(@NonNull User user) {
                runOnUi(() -> {
                    if (binding != null) {
                        binding.tvCustomerName.setText(user.getFullName());
                    }
                });
            }

            @Override
            public void onNotFound() {
                // لا وثيقة للحساب: التحية تبقى بلا اسم، والدخول نفسه هو من يقرر الخروج
            }

            @Override
            public void onError() {
                // الانقطاع ليس قراراً: يبقى ما على الشاشة ولا تُستبدل التحية برسالة خطأ
            }
        });
    }

    /**
     * بطاقة طلب نشط: تُعرض ما دام للزبون طلب لم يكتمل ولم يرفض، وتختفي حين لا شيء.
     *
     * اسم المتجر ليس في وثيقة الطلب، فيُقرأ من merchants بمعرّف صاحبه — رحلة واحدة
     * على وثيقة، لأن الزبون يرى متجره الذي طلب منه لا معرّفاً غامضاً.
     */
    private void watchOrders() {
        ordersWatch = orderRepository.watchForCustomer(uid, new OrderRepository.ListCallback() {
            @Override
            public void onOrders(@NonNull List<Order> orders) {
                runOnUi(() -> {
                    if (binding != null) {
                        renderActiveOrder(liveOrder(orders));
                    }
                });
            }

            @Override
            public void onError() {
                runOnUi(() -> {
                    if (binding != null) {
                        Toast.makeText(requireContext(), R.string.error_load_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    /** الأحدث أولاً في اللائحة، فأول غير مؤرشف هو طلب الزبون الذي ينتظره الآن */
    @Nullable
    private static Order liveOrder(@NonNull List<Order> orders) {
        for (Order order : orders) {
            if (!order.isArchived()) {
                return order;
            }
        }
        return null;
    }

    private void renderActiveOrder(@Nullable Order order) {
        if (order == null) {
            binding.cardActiveOrder.setVisibility(View.GONE);
            return;
        }

        binding.cardActiveOrder.setVisibility(View.VISIBLE);
        binding.tvTrackTitle.setText(titleOf(order));
        binding.tvTrackTotal.setText(getString(R.string.cust_track_total, money(order.displayTotal())));

        // الاسم يأتي بعد الطلب بقراءة وثيقة المتجر، فيُعرض الرقم وحده إن لم تصل
        if (order.getStoreId().isEmpty()) {
            // وثيقة مكتوبة في الكونسول بلا متجر: لا سؤال عن معرّف فاضي، والرقم وحده يكفيه
            showStoreName("", order.getNumber());
            return;
        }

        storeRepository.load(order.getStoreId(), new MerchantProfileRepository.ProfileCallback() {
            @Override
            public void onLoaded(@NonNull MerchantProfile profile) {
                runOnUi(() -> showStoreName(profile.getStoreName(), order.getNumber()));
            }

            @Override
            public void onNotFound() {
                runOnUi(() -> showStoreName("", order.getNumber()));
            }

            @Override
            public void onError() {
                runOnUi(() -> showStoreName("", order.getNumber()));
            }
        });
    }

    private void showStoreName(@NonNull String storeName, int number) {
        if (binding == null) {
            return;
        }
        binding.tvTrackStore.setText(storeName.isEmpty()
                ? getString(R.string.cust_track_order_only, number)
                : getString(R.string.cust_track_store, storeName, number));
    }

    /** عبارة البطاقة تتبع حالة الطلب، فلا يُقال «قيد التوصيل» لطلب لم يقبله المتجر بعد */
    private int titleOf(@NonNull Order order) {
        if (Order.STATUS_PREPARING.equals(order.getStatus())) {
            return R.string.cust_track_title_preparing;
        }
        if (Order.STATUS_READY.equals(order.getStatus())) {
            return R.string.cust_track_title_ready;
        }
        return R.string.cust_track_title_new;
    }

    /** اختيار المنطقة لا يصفّي شيئاً بعد: لا حقل مدينة في merchants، فهي ذكرى محفوظة */
    private void openAreaPicker() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cust_location_sheet_title)
                .setItems(R.array.customer_areas, (dialog, which) -> {
                    String area = requireContext().getResources()
                            .getStringArray(R.array.customer_areas)[which];
                    requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putString(KEY_AREA, area).apply();

                    // النافذة قد تبقى مفتوحة بعد أن تُهدم الصفحة عند انقلاب الشاشة
                    if (binding != null) {
                        binding.tvLocation.setText(area);
                    }
                })
                .show();
    }

    /**
     * البحث يفتح لائحة كل المتاجر بكلمة صفّي عليها: لا شاشة نتائج مستقلة في الرسمة،
     * واللائحة فيها شريط تصنيفات يفعل ما يفعله زر التصفية، فشاشة واحدة تكفي الاثنين.
     */
    private void openSearch(@NonNull String query) {
        CustomerHomeActivity host = host();
        if (host == null) {
            return;
        }
        host.openSection(CustomerSection.ALL, query);
    }

    private void bindSection(@NonNull CardCustomerSectionBinding card,
                             @NonNull CustomerSection section) {
        card.tvTitle.setText(section.titleRes());
        card.tvSubtitle.setText(section.subtitleRes());
        card.ivIcon.setImageResource(section.iconRes());
        card.getRoot().setOnClickListener(v -> {
            CustomerHomeActivity host = host();
            if (host != null) {
                host.openSection(section, "");
            }
        });
    }

    /** «تتبع» يذهب لطلباتي: لا شاشة تتبع بعد، وطلباتي هي مكانها يوم تُبنى */
    private void openOrdersTab() {
        CustomerHomeActivity host = host();
        if (host != null) {
            host.openTab(R.id.tab_orders);
        }
    }

    @Nullable
    private CustomerHomeActivity host() {
        if (requireActivity() instanceof CustomerHomeActivity) {
            return (CustomerHomeActivity) requireActivity();
        }
        return null;
    }

    /** المبلغ بصيغة واحدة كما في شاشات التاجر: رقم لاتيني ونقطتان عشريتان */
    @NonNull
    private String money(double value) {
        return getString(R.string.currency_symbol) + String.format(Locale.US, "%.2f", value);
    }

    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(action);
    }
}
