package com.taqat.madar.ui.customer.menu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.taqat.madar.R;
import com.taqat.madar.data.local.CustomerCart;
import com.taqat.madar.data.model.MerchantProfile;
import com.taqat.madar.data.model.Product;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.MerchantProfileRepository;
import com.taqat.madar.data.remote.ProductRepository;
import com.taqat.madar.databinding.FragmentCustomerMenuBinding;
import com.taqat.madar.databinding.ItemCustomerMenuProductBinding;
import com.taqat.madar.ui.customer.CustomerSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * منيو متجر واحد كما يفتحه الزبون من بطاقته: غلاف المتجر، وبلاطات معلوماته،
 * وتصنيفات أصنافه، ولائحة أطباقه، وشريط سلة يظهر حين تكون فيها حاجة.
 *
 * السلة التي يعبؤها هذا المنيو هي سلة الزبون واحدة في كل المحلات، لا سلة لكل متجر:
 * يجمع من هنا ومن الصيدلية ومن غيرهما، ويدفع مرة واحدة في الآخر. والشريط تحت يقول
 * ذلك المجموع كله، فمجموع محل وحده رقمان مختلفان على شاشة واحدة.
 *
 * قراءتان لا مستمعان: ملف المتجر ومنتجاته. الشاشة يُدخلها الزبون ويخرج منها، ولو بقيت
 * مستمعة على products لدفع ثمن كل تمريرة فيها وهو يقرأ طبقاً واحداً. وحين يصل الطلب
 * إلى باب الدفع تُترك المستمعات لشاشة الطلب نفسها.
 *
 * ما لا حقل له لا يُختلق: بلاطا التقييم ورسوم التوصيل مبنيتان ومخفيتان، لأن الأولى
 * تُحسب من تقييمات الزبائن والثانية من المسافة بين الزبون والمتجر، ولا واحدة منهما
 * محفوظة بعد. ومدة التحضير هي الوحيدة التي لها مصدر، فتُقال «مدة التحضير» لا
 * «مدة التوصيل» كما كُتب في الرسمة: الاسم المحفوظ أحسن من الوعد الذي لا مصدر له.
 */
public class CustomerMenuFragment extends Fragment {

    /** اسم طبقة المنيو في مكدّس الرجوع: فوق طبقة القسم، والرجوع ينزل إليها لا للرئيسية */
    public static final String BACK_STACK = "customer_menu";

    private static final String ARG_STORE_ID = "store_id";

    private final MerchantProfileRepository storeRepository = new MerchantProfileRepository();
    private final ProductRepository productRepository = new ProductRepository();
    private final AuthRepository authRepository = new AuthRepository();

    private FragmentCustomerMenuBinding binding;
    private final ProductAdapter adapter = new ProductAdapter();

    private String storeId = "";
    private CustomerCart cart;

    /** ما وصل من منتجات المتجر متوفراً، والشرائح والبطاقات كلها تُقرأ من هذه القائمة */
    private final List<Product> products = new ArrayList<>();

    /** "" تعني «الكل»، وهي الشريحة المختارة قبل أن يلمس الزبون شيئاً */
    private String shownCategory = "";

    @NonNull
    public static CustomerMenuFragment newInstance(@NonNull String storeId) {
        Bundle args = new Bundle();
        args.putString(ARG_STORE_ID, storeId);

        CustomerMenuFragment page = new CustomerMenuFragment();
        page.setArguments(args);
        return page;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        storeId = requireArguments().getString(ARG_STORE_ID, "");

        String accountUid = authRepository.currentUser() == null
                ? "" : authRepository.currentUser().getUid();
        if (storeId.isEmpty() || accountUid.isEmpty()) {
            // بلا متجر أو بلا جلسة: لا سلة ولا قراءة، والشاشة نفسها ما كان لها أن تُفتح
            Toast.makeText(requireContext(), R.string.error_account_required,
                    Toast.LENGTH_LONG).show();
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }
        cart = CustomerCart.forCustomer(requireContext(), accountUid);

        binding.rvProducts.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvProducts.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // TODO شاشة إشعارات الزبون لم تُسلَّم رسمتها بعد: الجرس ظاهر كما في الرسمة ولا يفتح شيئاً
        binding.ivNotifications.setOnClickListener(v -> Toast.makeText(requireContext(),
                R.string.msg_screen_coming_soon, Toast.LENGTH_SHORT).show());

        // TODO شاشة السلة والدفع لم تُسلَّم رسمتها: الشريط يعدّ ويعرض المجموع، والزر لا يفتح شيئاً
        binding.btnCart.setOnClickListener(v -> Toast.makeText(requireContext(),
                R.string.msg_screen_coming_soon, Toast.LENGTH_SHORT).show());

        updateCartBar();
        loadStore();
        loadProducts();
    }

    @Override
    public void onResume() {
        super.onResume();

        // السلة تُقرأ من جديد لا تُحدَّث في المكان: النسخة في الذاكرة لا تعرف ما كتبه
        // مكان آخر على الجهاز نفسه، وشريط المجموع لا يحتمل رقماً قديماً
        String accountUid = authRepository.currentUser() == null
                ? "" : authRepository.currentUser().getUid();
        if (binding != null && !accountUid.isEmpty()) {
            cart = CustomerCart.forCustomer(requireContext(), accountUid);
            updateCartBar();
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * ملف المتجر: الاسم والنبذة والصورة وحالة الفتح وبلاطا العنوان والساعات.
     * متجر حُذف أو لم يُعتمد لا يُترك صفحة بيضاء: تُقال كلمته ويُغلق ويُرجَع للقائمة.
     */
    private void loadStore() {
        storeRepository.load(storeId, new MerchantProfileRepository.ProfileCallback() {
            @Override
            public void onLoaded(@NonNull MerchantProfile profile) {
                runOnUi(() -> {
                    if (binding != null) {
                        renderStore(profile);
                    }
                });
            }

            @Override
            public void onNotFound() {
                runOnUi(CustomerMenuFragment.this::closeWithMessage);
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

    /** منتجات المتجر: تُترك منها المتوفرة وحدها، فطبق نفد من المطبخ ليس خياراً للزبون */
    private void loadProducts() {
        productRepository.loadForStore(storeId, new ProductRepository.ListCallback() {
            @Override
            public void onProducts(@NonNull List<Product> items) {
                runOnUi(() -> {
                    if (binding == null) {
                        return;
                    }
                    products.clear();
                    for (Product item : items) {
                        if (item.isAvailable()) {
                            products.add(item);
                        }
                    }
                    buildChips();
                    renderProducts();
                });
            }

            @Override
            public void onError() {
                runOnUi(() -> {
                    if (binding != null) {
                        Toast.makeText(requireContext(), R.string.error_load_failed,
                                Toast.LENGTH_LONG).show();
                        renderProducts();
                    }
                });
            }
        });
    }

    private void closeWithMessage() {
        if (binding == null) {
            return;
        }
        Toast.makeText(requireContext(), R.string.cust_menu_store_gone, Toast.LENGTH_LONG).show();
        requireActivity().getOnBackPressedDispatcher().onBackPressed();
    }

    /** الغلاف وما عليه: كل كلمة فيه من merchants، والصورة أول معرض المتجر أو شعاره */
    private void renderStore(@NonNull MerchantProfile store) {
        binding.tvStoreName.setText(store.getStoreName());
        binding.tvStoreDescription.setText(store.getDescription());
        binding.tvStoreDescription.setVisibility(
                store.getDescription().isEmpty() ? View.GONE : View.VISIBLE);

        showHeroPhoto(store);

        // أيقونة القسم لا أيقونة الطبق: لا حقل صورة تصنيف في العقد، والقسم محفوظ فعلاً
        CustomerSection section = CustomerSection.sectionOfCategory(requireContext(),
                store.getCategory());
        binding.ivCategoryIcon.setImageResource(
                section == null ? R.drawable.ic_store : section.iconRes());

        renderOpenState(store);
        renderStoreTiles(store);
    }

    /**
     * صورة الغلاف: أول معرض المتجر، فإن لم يكن له معرض فشعاره، وإن لم يكن فلا صورة.
     * وهي نفس الترتيب الذي تُقرأ منه صورة بطاقته في اللائحة، فلا يفتّش الزبون عن
     * وجه المتجر في مكانين فيجدهما مختلفين.
     */
    private void showHeroPhoto(@NonNull MerchantProfile store) {
        String url = store.getPhotoUrls().isEmpty()
                ? store.getLogoUrl() : store.getPhotoUrls().get(0);

        if (url.isEmpty()) {
            binding.ivHeroPhoto.setImageResource(R.drawable.ic_store);
            binding.ivHeroPhoto.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            binding.ivHeroPhoto.setImageTintList(
                    ContextCompat.getColorStateList(requireContext(), R.color.secondary_navy));
            return;
        }

        binding.ivHeroPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        binding.ivHeroPhoto.setImageTintList(null);
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_store)
                .into(binding.ivHeroPhoto);
    }

    /**
     * حالة الفتح ثلاث كلمات: التاجر أوقف الاستقبال، أو المتجر خارج ساعاته، أو هو يعمل.
     * الأولى قرار والثانية ساعة، ولو قِيلتا كلمة واحدة لظنّ الزبون أن المتجر رفضه.
     */
    private void renderOpenState(@NonNull MerchantProfile store) {
        int stateRes;
        int dotColorRes;

        if (!store.isAcceptingOrders()) {
            stateRes = R.string.cust_menu_paused;
            dotColorRes = R.color.danger_red;
        } else if (store.isOpenNow()) {
            stateRes = R.string.cust_menu_open_now;
            dotColorRes = R.color.success_green;
        } else {
            stateRes = R.string.cust_menu_closed_now;
            dotColorRes = R.color.danger_red;
        }

        binding.tvOpenState.setText(stateRes);
        binding.viewStatusDot.setBackgroundTintList(
                ContextCompat.getColorStateList(requireContext(), dotColorRes));
    }

    /**
     * بلاطا العنوان والساعات: يُترَك البلاط فارغاً بلا معنى، فمتجر لم يسجّل عنوانه
     * أو ساعاته خير من بلاط يقول «—» تحت اسم حقل يوعد الزبون بمعلومة.
     *
     * التقييم ورسوم التوصيل لا يُلمسان هنا: لا حقل لهما، وبناؤهما يبدأ من مسار
     * التقييمات وحساب المسافة، لا من هذه الشاشة.
     */
    private void renderStoreTiles(@NonNull MerchantProfile store) {
        binding.tileAddress.setVisibility(
                store.getAddress().isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvAddressValue.setText(store.getAddress());

        boolean hasHours = !store.getClosingTime().isEmpty();
        binding.tileHours.setVisibility(hasHours ? View.VISIBLE : View.GONE);
        binding.tvHoursValue.setText(hasHours ? hourLabel(store.getClosingTime()) : "");
    }

    /** أسرع مدة تحضير بين أصناف المتجر، تُقال في بلاطها كما تُقال في بطاقته بالقائمة */
    private void renderPrepTile() {
        String fastest = "";
        for (Product product : products) {
            if (fastest.isEmpty() || product.prepStartMinutes() < minutesOf(fastest)) {
                fastest = product.getPrepTime();
            }
        }

        boolean hasPrep = !fastest.isEmpty();
        binding.tilePrep.setVisibility(hasPrep ? View.VISIBLE : View.GONE);
        binding.tvPrepValue.setText(hasPrep
                ? getString(R.string.cust_menu_minutes, prepLabel(fastest)) : "");
    }

    /**
     * الشرائح من أصناف هذا المتجر لا من قائمة ثابتة: «الكل» ثم كل تصنيف له صنف واحد
     * متوفر فعلاً، فلا شريحة يضغط عليها الزبون فيجد لائحة فارغة من صنع الواجهة.
     */
    private void buildChips() {
        binding.chipGroupFilter.removeAllViews();

        Set<String> present = new LinkedHashSet<>();
        for (Product product : products) {
            if (!product.getCategory().isEmpty()) {
                present.add(product.getCategory());
            }
        }

        Chip allChip = addChip(getString(R.string.cust_chip_all), "");
        for (String category : present) {
            addChip(category, category);
        }

        // الشرائح تُبنى بالكود، وChipGroup لا يختار من نفسه واحدة: «الكل» تُضاء هنا
        binding.chipGroupFilter.check(allChip.getId());
    }

    private Chip addChip(@NonNull CharSequence text, @NonNull String category) {
        Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.item_category_chip, binding.chipGroupFilter, false);

        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setTag(category);
        chip.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                shownCategory = String.valueOf(button.getTag());
                renderProducts();
            }
        });
        binding.chipGroupFilter.addView(chip);
        return chip;
    }

    /** عرض الشريحة المختارة من القائمة المحفوظة في الذاكرة، بلا سؤال جديد للسيرفر */
    private void renderProducts() {
        if (binding == null) {
            return;
        }

        renderPrepTile();

        List<Product> shown = new ArrayList<>();
        for (Product product : products) {
            if (shownCategory.isEmpty() || shownCategory.equals(product.getCategory())) {
                shown.add(product);
            }
        }

        boolean empty = shown.isEmpty();
        binding.rvProducts.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        adapter.submit(shown);
    }

    /** شريط السلة: يظهر بالواحد ويختفي بالفراغ، وهو يقول سلة الزبون كلها لا هذا المحل */
    private void updateCartBar() {
        int count = cart == null ? 0 : cart.itemCount();
        binding.cardCart.setVisibility(count == 0 ? View.GONE : View.VISIBLE);

        if (count == 0) {
            return;
        }
        binding.tvCartItems.setText(getString(R.string.cust_menu_items, count));
        binding.tvCartTotal.setText(getString(R.string.cust_menu_total,
                money(cart == null ? 0 : cart.total())));
    }

    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(action);
    }

    /** السعر بالشيكل كما يُحفظ في المنتج، ورمز العملة أولاً كما في بقية شاشات مدار */
    private String money(double value) {
        return getString(R.string.currency_symbol) + String.format(Locale.US, "%.2f", value);
    }

    /**
     * نطاق التحضير يُحفظ للفرز («30-60») ويُعرض بالتسمية التي يقرأها التاجر في إضافة
     * المنتج («+30»)، فلا أرقام على شاشة الزبون غير الأرقام التي كتبها التاجر.
     */
    private String prepLabel(@NonNull String value) {
        String[] values = getResources().getStringArray(R.array.product_prep_time_values);
        String[] labels = getResources().getStringArray(R.array.product_prep_times);
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i].equals(value)) {
                return labels[i];
            }
        }
        return value;
    }

    /** «23:00» إلى التسمية التي اختارها التاجر نفسه في نموذج متجره: «11:00 م» */
    private String hourLabel(@NonNull String value) {
        String[] values = getResources().getStringArray(R.array.business_hour_values);
        String[] labels = getResources().getStringArray(R.array.business_hours);
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i].equals(value)) {
                return labels[i];
            }
        }
        return value;
    }

    /** أول رقم في نطاق محفوظ، وقيمة مكتوبة في الكونسول تُترك أبطأ شيء لا تُحسب أسرع */
    private static int minutesOf(@NonNull String range) {
        return range.isEmpty() ? Integer.MAX_VALUE : Product.minutesOf(range);
    }

    /** لائحة الأصناف: مصدرها ما في الذاكرة، والبطاقة تفتح التفاصيل وعدّادها هو الكتابة */
    private final class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.Holder> {

        private final List<Product> items = new ArrayList<>();

        void submit(@NonNull List<Product> products) {
            items.clear();
            items.addAll(products);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCustomerMenuProductBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private final class Holder extends RecyclerView.ViewHolder {

            private final ItemCustomerMenuProductBinding itemBinding;

            Holder(@NonNull ItemCustomerMenuProductBinding binding) {
                super(binding.getRoot());
                this.itemBinding = binding;
            }

            void bind(@NonNull Product product) {
                itemBinding.tvName.setText(product.getName());
                itemBinding.tvDescription.setText(product.getDescription());
                itemBinding.tvDescription.setVisibility(
                        product.getDescription().isEmpty() ? View.GONE : View.VISIBLE);
                itemBinding.tvPrice.setText(money(product.getPrice()));

                showPhoto(product);

                // وحدات هذا الصنف في السلة بكل أوجهه: هي العدد على البطاقة، وإطارها البرتقالي
                int units = cart == null ? 0 : cart.quantity(product.getId());
                itemBinding.cardProduct.setStrokeColor(ContextCompat.getColorStateList(
                        requireContext(), units > 0 ? R.color.primary_orange : R.color.field_stroke));
                itemBinding.cardProduct.setStrokeWidth(units > 0 ? dp(2f) : dp(1f));

                // الصفر حالة بلا ناقص ولا عدد: تبقى حبّة الزائد وحدها كما كانت في الرسمة
                itemBinding.stepper.btnMinus.setVisibility(
                        units == 0 ? View.GONE : View.VISIBLE);
                itemBinding.stepper.tvQty.setVisibility(units == 0 ? View.GONE : View.VISIBLE);
                itemBinding.stepper.tvQty.setText(String.valueOf(units));
                itemBinding.stepper.getRoot().setBackgroundResource(
                        units == 0 ? 0 : R.drawable.bg_stepper_bar);
                // المتجر المغلق لا يُمنع من العرض، لكن الطلب لا يُكمل إلا عند الدفع:
                // هناك تُقال حالة المتجر مرة واحدة، لا هنا في كل مرة يضيف فيها الزبون صنفاً
                itemBinding.cardProduct.setOnClickListener(v -> openDetails(product));
                itemBinding.stepper.btnPlus.setOnClickListener(v -> adjust(product, 1));
                itemBinding.stepper.btnMinus.setOnClickListener(v -> adjust(product, -1));
            }

            /** صورة الطبق إن رفعها التاجر، وإلا أيقونة المطعم بلون المتجر نفسه */
            private void showPhoto(@NonNull Product product) {
                if (!product.hasPhoto()) {
                    itemBinding.ivProductPhoto.setImageResource(R.drawable.ic_restaurant);
                    itemBinding.ivProductPhoto.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    itemBinding.ivProductPhoto.setImageTintList(
                            ContextCompat.getColorStateList(requireContext(), R.color.secondary_navy));
                    return;
                }

                itemBinding.ivProductPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
                itemBinding.ivProductPhoto.setImageTintList(null);
                Glide.with(CustomerMenuFragment.this)
                        .load(product.getPhotoUrl())
                        .placeholder(R.drawable.ic_restaurant)
                        .into(itemBinding.ivProductPhoto);
            }
        }
    }

    /** بكسلات الكثافة لسمك الإطار: الرسم بالسماكة لا بالبكسل، فيبقى واحداً على كل شاشة */
    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * عدّاد البطاقة: وحدة زيادة أو نقصان على وجه الصنف الذي في السلة. البطاقة لا تخترع
     * اختيارات لم تُعرض، فمن أراد خبزاً ومضافات وملاحظة فليفتح التفاصيل بضغطة على بطاقته.
     */
    private void adjust(@NonNull Product product, int delta) {
        if (cart == null) {
            return;
        }
        cart.adjust(product, delta);
        updateCartBar();
        adapter.notifyDataSetChanged();
    }

    /**
     * البطاقة كلها تفتح نافذة تفاصيل الوجبة، وكل ما يُلمس هناك مكتوب في السلة قبل أن
     * تُغلَق النافذة — فالشريط والبطاقة يُقرآن من جديد بعد كل كتابة ولا تُحمَل النافذة
     * بردّها إلى هنا.
     */
    private void openDetails(@NonNull Product product) {
        if (cart == null) {
            return;
        }
        new ProductDetailsSheet(requireContext(), product, cart, () -> {
            updateCartBar();
            adapter.notifyDataSetChanged();
        }).show();
    }
}
