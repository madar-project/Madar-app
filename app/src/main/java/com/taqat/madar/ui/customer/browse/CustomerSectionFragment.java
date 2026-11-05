package com.taqat.madar.ui.customer.browse;

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
import com.taqat.madar.data.model.MerchantProfile;
import com.taqat.madar.data.model.Offer;
import com.taqat.madar.data.model.Product;
import com.taqat.madar.data.remote.MerchantProfileRepository;
import com.taqat.madar.data.remote.OfferRepository;
import com.taqat.madar.data.remote.ProductRepository;
import com.taqat.madar.databinding.FragmentCustomerSectionBinding;
import com.taqat.madar.databinding.ItemCustomerStoreBinding;
import com.taqat.madar.ui.customer.CustomerHomeActivity;
import com.taqat.madar.ui.customer.CustomerSection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * لائحة متاجر قسم: ما بناه التجار في هذه التصنيفات وعتمدته إدارة مدار.
 *
 * ثلاثة أسئلة تُقرأ مرة واحدة عند الفتح لا مستمعاً: المتاجر، ثم منتجاتها لتُحسب أسرع
 * مدة تحضير، ثم عروضها لتُوضع شارة «عرض». الشاشة قائمة يُدخلها الزبون ويخرج منها،
 * فالبقاء مستمعاً على ثلاث مجموعات كان سيدفع ثمنها في كل مرة يمرّ فيها من الرئيسية.
 *
 * ما لا مصدر له لا يُختلق: شارة التقييم ورسوم التوصيل مبنيتان في البطاقة ومخفيتان،
 * لأن لا حقل لأي منهما في قاعدة البيانات بعد.
 */
public class CustomerSectionFragment extends Fragment {

    /** اسم طبقة القسم في مكدّس الرجوع: به وحده تُسقطها المضيفة عند أي ضغطة على الشريط */
    public static final String BACK_STACK = "customer_section";

    private static final String ARG_SECTION = "section";
    private static final String ARG_QUERY = "query";

    private final MerchantProfileRepository storeRepository = new MerchantProfileRepository();
    private final ProductRepository productRepository = new ProductRepository();
    private final OfferRepository offerRepository = new OfferRepository();

    private FragmentCustomerSectionBinding binding;
    private final StoreAdapter adapter = new StoreAdapter();

    /** كل ما وصل للقسم بعد كلمة البحث، ثم تُصفّى منه الشريحة المختارة عند العرض */
    private final List<StoreCard> cards = new ArrayList<>();

    private CustomerSection section = CustomerSection.RESTAURANTS;
    private String query = "";
    /** "" تعني «الكل»، وهي الشريحة المختارة دائماً قبل أن يلمس الزبون شيئاً */
    private String shownCategory = "";

    /**
     * متجر واحد كما تُعرض بطاقته: بياناته من merchants، ووقت تحضيره من منتجاته،
     * وشارة عرضه من عروضه. الثلاث كلها وصلت بالفعل، فالجمع بينها هنا لا في السيرفر.
     */
    private static final class StoreCard {
        @NonNull
        final MerchantProfile store;
        /** أسرع نطاق تحضير بين أصنافه المتوفرة، فارغ إن لم يكن له صنف متوفر */
        String prepTime = "";
        boolean hasOffer;

        StoreCard(@NonNull MerchantProfile store) {
            this.store = store;
        }
    }

    @NonNull
    public static CustomerSectionFragment newInstance(@NonNull CustomerSection section,
                                                      @NonNull String query) {
        Bundle args = new Bundle();
        args.putString(ARG_SECTION, section.name());
        args.putString(ARG_QUERY, query);

        CustomerSectionFragment page = new CustomerSectionFragment();
        page.setArguments(args);
        return page;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerSectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = requireArguments();
        section = CustomerSection.of(args.getString(ARG_SECTION));
        query = args.getString(ARG_QUERY, "");

        binding.rvStores.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvStores.setAdapter(adapter);

        // عنوان الشاشة هو اسم القسم، ولو جاء الزبون من البحث صار كلمته هو
        int fallback = section.titleRes() == 0 ? R.string.cust_all_stores : section.titleRes();
        binding.tvSectionTitle.setText(query.isEmpty() ? getString(fallback) : query);

        // TODO شاشة إشعارات الزبون لم تُسلَّم رسمتها بعد: الجرس ظاهر كما في الرسمة ولا يفتح شيئاً
        binding.ivNotifications.setOnClickListener(v -> Toast.makeText(requireContext(),
                R.string.msg_screen_coming_soon, Toast.LENGTH_SHORT).show());

        loadStores();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * متاجر القسم المعتمدة. قسم بلا تصنيف محفوظ (التوصيل الخاص والخدمات المهنية) لا
     * يُسأل عنه السيرفر: لائحته فارغة اليوم مهما كان الجواب، والنتيجة معروفة قبل السؤال.
     */
    private void loadStores() {
        cards.clear();
        render();

        List<String> categories = section.categories(requireContext());
        if (categories.isEmpty()) {
            return;
        }

        storeRepository.loadByCategories(categories, new MerchantProfileRepository.ListCallback() {
            @Override
            public void onStores(@NonNull List<MerchantProfile> stores) {
                runOnUi(() -> {
                    if (binding == null) {
                        return;
                    }
                    for (MerchantProfile store : stores) {
                        if (matches(store)) {
                            cards.add(new StoreCard(store));
                        }
                    }
                    buildChips();
                    if (cards.isEmpty()) {
                        render();
                        return;
                    }
                    enrich();
                });
            }

            @Override
            public void onError() {
                runOnUi(() -> {
                    if (binding != null) {
                        Toast.makeText(requireContext(), R.string.error_load_failed,
                                Toast.LENGTH_LONG).show();
                        render();
                    }
                });
            }
        });
    }

    /**
     * منتجات المتاجر وعروضها في قراءتين متوازيتين، والشاشة تُرسم حين تعود الاثنتان.
     *
     * لا انتظار لسبب واحد: العدّاد في الكود لا في السيرفر، فلو تأخرت قراءة العروض
     * بقيت البطاقات بلا شارة إلى أن تصل، وهذا كل ما تغيّره — لا اللائحة كلها.
     */
    private void enrich() {
        List<String> storeIds = new ArrayList<>();
        for (StoreCard card : cards) {
            storeIds.add(card.store.getUid());
        }

        int[] waiting = {2};
        Runnable done = () -> {
            if (--waiting[0] == 0) {
                runOnUi(CustomerSectionFragment.this::render);
            }
        };

        productRepository.loadForStores(storeIds, new ProductRepository.ListCallback() {
            @Override
            public void onProducts(@NonNull List<Product> products) {
                for (Product product : products) {
                    if (!product.isAvailable()) {
                        continue;
                    }
                    StoreCard card = cardOf(product.getStoreId());
                    if (card != null && isFaster(product, card.prepTime)) {
                        card.prepTime = product.getPrepTime();
                    }
                }
                done.run();
            }

            @Override
            public void onError() {
                done.run();
            }
        });

        offerRepository.loadForStores(storeIds, new OfferRepository.ListCallback() {
            @Override
            public void onOffers(@NonNull List<Offer> offers) {
                long now = System.currentTimeMillis();
                for (Offer offer : offers) {
                    StoreCard card = cardOf(offer.getStoreId());
                    if (card != null && offer.isLive(now)) {
                        card.hasOffer = true;
                    }
                }
                done.run();
            }

            @Override
            public void onError() {
                done.run();
            }
        });
    }

    @Nullable
    private StoreCard cardOf(@NonNull String storeId) {
        for (StoreCard card : cards) {
            if (card.store.getUid().equals(storeId)) {
                return card;
            }
        }
        return null;
    }

    /**
     * أي النطاقين أسرع؟ يُقارن أول رقم فيهما لا النص، لأن «+30» تسبق «10-15» في
     * ترتيب الحروف وتُترك أبطأ طبق على البطاقة. والفراغ أبطأ من أي رقم: أول صنف
     * له نطاق يملأ البطاقة من غير مقارنة.
     */
    private static boolean isFaster(@NonNull Product candidate, @NonNull String kept) {
        return kept.isEmpty() || candidate.prepStartMinutes() < Product.minutesOf(kept);
    }

    /** كلمة البحث تُقارن بالاسم والنبذة والتصنيف: الثلاثة نصوص يعرفها التاجر من نموذج متجره */
    private boolean matches(@NonNull MerchantProfile store) {
        if (query.isEmpty()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.getDefault());
        return store.getStoreName().toLowerCase(Locale.getDefault()).contains(needle)
                || store.getDescription().toLowerCase(Locale.getDefault()).contains(needle)
                || store.getCategory().toLowerCase(Locale.getDefault()).contains(needle);
    }

    /**
     * الشرائح من المتاجر التي وصلت لا من قائمة ثابتة: «الكل» ثم كل تصنيف له متجر واحد
     * على الأقل في هذا القسم، فلا شريحة تضغط عليها فتجد فراغاً من صنع الواجهة.
     */
    private void buildChips() {
        binding.chipGroupFilter.removeAllViews();

        Set<String> present = new LinkedHashSet<>();
        for (StoreCard card : cards) {
            if (!card.store.getCategory().isEmpty()) {
                present.add(card.store.getCategory());
            }
        }

        Chip allChip = addChip(getString(R.string.cust_chip_all), "");
        for (String category : present) {
            addChip(category, category);
        }

        // «الكل» مختارة من أول ما تظهر الشرائح كما في الرسمة: ChipGroup لا يختار
        // شيئاً من نفسه حين تُضاف شرائحه بالكود، فيبقى الشريط مضيئاً على واحدة منها
        binding.chipGroupFilter.check(allChip.getId());
    }

    private Chip addChip(@NonNull CharSequence text, @NonNull String category) {
        Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.item_category_chip, binding.chipGroupFilter, false);

        // معرّف مولَّد لكل شريحة: ChipGroup يقرأ اختيارها منه، وتُترك التصفية على وسمها
        chip.setId(View.generateViewId());
        chip.setText(text);
        chip.setTag(category);
        chip.setOnCheckedChangeListener((button, checked) -> {
            if (checked) {
                shownCategory = String.valueOf(button.getTag());
                render();
            }
        });
        binding.chipGroupFilter.addView(chip);
        return chip;
    }

    /** عرض الشريحة المختارة على ما وصل، بلا سؤال جديد للسيرفر */
    private void render() {
        List<StoreCard> shown = new ArrayList<>();
        for (StoreCard card : cards) {
            if (shownCategory.isEmpty() || shownCategory.equals(card.store.getCategory())) {
                shown.add(card);
            }
        }

        boolean empty = shown.isEmpty();
        binding.rvStores.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

        // فراغ البحث غير فراغ القسم: الأولى «لا نتائج» والثانية «لا متاجر هنا بعد»
        binding.tvEmptyTitle.setText(query.isEmpty()
                ? R.string.cust_section_empty_title : R.string.cust_search_empty_title);
        binding.tvEmptyHint.setText(query.isEmpty()
                ? R.string.cust_section_empty_hint : R.string.cust_search_empty_hint);

        adapter.submit(shown);
    }

    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(action);
    }

    /** لائحة البطاقات: مصدرها ما في الذاكرة وحده، فلا قراءة ولا استماع أثناء التمرير */
    private final class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.Holder> {

        private final List<StoreCard> items = new ArrayList<>();

        void submit(@NonNull List<StoreCard> cards) {
            items.clear();
            items.addAll(cards);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCustomerStoreBinding.inflate(
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

            private final ItemCustomerStoreBinding itemBinding;

            Holder(@NonNull ItemCustomerStoreBinding binding) {
                super(binding.getRoot());
                this.itemBinding = binding;
            }

            void bind(@NonNull StoreCard card) {
                MerchantProfile store = card.store;

                itemBinding.tvStoreName.setText(store.getStoreName());
                itemBinding.tvStoreDescription.setText(store.getDescription());
                itemBinding.pillOffer.setVisibility(card.hasOffer ? View.VISIBLE : View.GONE);

                // المتجر المغلق يقولها في مكان الوقت: لا عنصر جديد في الرسمة، والسطر نفسه كافٍ
                if (!store.isAcceptingOrders()) {
                    itemBinding.ivEtaIcon.setVisibility(View.GONE);
                    itemBinding.tvPrepTime.setText(R.string.cust_store_closed);
                } else if (card.prepTime.isEmpty()) {
                    itemBinding.ivEtaIcon.setVisibility(View.GONE);
                    itemBinding.tvPrepTime.setText("");
                } else {
                    itemBinding.ivEtaIcon.setVisibility(View.VISIBLE);
                    itemBinding.tvPrepTime.setText(getString(R.string.cust_card_eta,
                            prepLabel(card.prepTime)));
                }

                showPhoto(store);

                // البطاقة تفتح منيو المتجر: طبقة فوق هذه اللائحة، والرجوع يعود إليها
                itemBinding.getRoot().setOnClickListener(v ->
                        ((CustomerHomeActivity) requireActivity())
                                .openStore(card.store.getUid()));
            }

            /** أول صورة من معرض المتجر، فإن لم يكن له معرض فشعاره، وإن لم يكن فلا صورة */
            private void showPhoto(@NonNull MerchantProfile store) {
                String url = store.getPhotoUrls().isEmpty()
                        ? store.getLogoUrl() : store.getPhotoUrls().get(0);

                if (url.isEmpty()) {
                    itemBinding.ivStorePhoto.setImageResource(R.drawable.ic_store);
                    itemBinding.ivStorePhoto.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    itemBinding.ivStorePhoto.setImageTintList(
                            ContextCompat.getColorStateList(requireContext(), R.color.secondary_navy));
                    return;
                }

                itemBinding.ivStorePhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
                itemBinding.ivStorePhoto.setImageTintList(null);
                Glide.with(CustomerSectionFragment.this)
                        .load(url)
                        .placeholder(R.drawable.ic_store)
                        .into(itemBinding.ivStorePhoto);
            }

            /**
             * وقت التحضير يُحفظ نطاقاً للفرز، ويُعرض بالتسمية نفسها التي يقرأها التاجر
             * في إضافة المنتج: «30-60» في الوثيقة و«+30» على الشاشة.
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
        }
    }
}
