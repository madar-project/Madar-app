package com.taqat.madar.ui.merchant.menu;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.core.util.Images;
import com.taqat.madar.data.model.Product;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.ProductRepository;
import com.taqat.madar.data.remote.StorePhotoRepository;
import com.taqat.madar.databinding.ActivityAddProductBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;
import com.taqat.madar.ui.merchant.home.MerchantHomeActivity;

import java.util.Locale;

/**
 * إضافة منتج إلى منيو التاجر.
 *
 * الشاشة تُنتج وثيقة في مجموعة products صاحبها التاجر نفسه، ولا تحتاج اعتماداً:
 * الاعتماد قرار على التاجر يُؤخذ مرة واحدة، فمنتجاته تُنشر مباشرة.
 *
 * صورة المنتج اختيارية — التصميم نفسه ما وضع عليها نجمة — لكن إن اختارها التاجر
 * تُرفع قبل حفظ الوثيقة، لأن حفظ منتج برابط صورة فاشل يترك الزبون أمام مكان فارغ.
 */
public class AddProductActivity extends AppCompatActivity {

    /** أقل عدد أحرف في اسم المنتج: حرفان لا يميّزان طبقاً عن آخر */
    private static final int MIN_NAME_LENGTH = 3;

    /** النطاق الأوسط هو الافتراضي، لأنه أكثر ما تُحضَر به الوجبات */
    private static final int DEFAULT_PREP_TIME_INDEX = 1;

    // حقول التعديل: النموذج واحد للإضافة والتعديل، والمعرّف وحده يفرّق بينهما
    private static final String EXTRA_ID = "product_id";
    private static final String EXTRA_NAME = "product_name";
    private static final String EXTRA_CATEGORY = "product_category";
    private static final String EXTRA_PRICE = "product_price";
    private static final String EXTRA_PREP_TIME = "product_prep_time";
    private static final String EXTRA_DESCRIPTION = "product_description";
    private static final String EXTRA_AVAILABLE = "product_available";
    private static final String EXTRA_PHOTO_URL = "product_photo_url";

    /** يقصد التعديل: null في شاشة الإضافة، ووثيقة المنتج نفسه حين جاء من لائحة المنيو */
    private Product editing;

    /** التاجر أزال صورة منتج قديمة: الحفظ بلا صورة مقصود، لا نسيان */
    private boolean photoDropped;

    private ActivityAddProductBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private final ProductRepository productRepository = new ProductRepository();
    private final StorePhotoRepository photoRepository = new StorePhotoRepository();

    /** نص التصنيف المختار، وهو نفسه القيمة المحفوظة */
    private String selectedCategory = "";

    /** قيمة وقت التحضير بالدقيقتين "15-25"، لا نصه المعروض "+30" */
    private String selectedPrepTime = "";

    private Uri photoUri;
    private boolean submitting;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    showPhoto(uri);
                }
            });

    /** نيّة التعديل: نفس الشاشة، لكن معبّأة بمنتج موجود ومحفوظة فوق وثيقته */
    @NonNull
    public static Intent editIntent(@NonNull Context context, @NonNull Product product) {
        return new Intent(context, AddProductActivity.class)
                .putExtra(EXTRA_ID, product.getId())
                .putExtra(EXTRA_NAME, product.getName())
                .putExtra(EXTRA_CATEGORY, product.getCategory())
                .putExtra(EXTRA_PRICE, product.getPrice())
                .putExtra(EXTRA_PREP_TIME, product.getPrepTime())
                .putExtra(EXTRA_DESCRIPTION, product.getDescription())
                .putExtra(EXTRA_AVAILABLE, product.isAvailable())
                .putExtra(EXTRA_PHOTO_URL, product.getPhotoUrl());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddProductBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            // لا جلسة: التاجر خارج التطبيق أصلاً، فلا معنى لشاشة إضافة منتج
            Toast.makeText(this, R.string.error_account_required, Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, LoginActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return;
        }

        buildCategoryChips();
        buildPrepTimeCards();
        fillForEditing();

        binding.boxPhoto.setOnClickListener(v -> pickPhoto.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
        binding.btnRemovePhoto.setOnClickListener(v -> clearPhoto());
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnCancel.setOnClickListener(v -> finish());

        // التبويب المختار يُضبط قبل تركيب المستمع: setSelectedItemId يستدعي المستمع
        // نفسه، ولو تأخر لكان أول ما تفتح الشاشة يغلقها لأنه يقصد المنيو
        binding.bottomNav.setSelectedItemId(R.id.tab_menu);

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int tab = item.getItemId();
            if (tab == R.id.tab_menu) {
                // المنيو هو الصفحة التي أخرجتنا إلى هنا: إغلاق النموذج يكفي للعودة إليه
                finish();
                return true;
            }
            openHomeTab(tab);
            return false;
        });
    }

    /**
     * تبويب من شريط الواجهة الرئيسية: نعود للمضيفة وهي تعرض الصفحة المطلوبة، لأن
     * الشاشات الفرعية ما تعرف كيف تُبدّل صفحات بعضها بعضًا.
     */
    private void openHomeTab(int tab) {
        startActivity(new Intent(this, MerchantHomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MerchantHomeActivity.EXTRA_TAB, tab));
        finish();
    }

    /**
     * شرائح التصنيف تُبنى من ملف القوائم، لأن النص العربي نفسه هو القيمة المحفوظة:
     * ما يسمّيه التاجر اليوم هو ما يصفّي به الزبون بحثه غداً. والأولى مختارة افتراضياً
     * لأن أغلب المنتجات وجبات رئيسية، فلا يُترك التاجر أمام شاشة بلا اختيار.
     */
    private void buildCategoryChips() {
        String[] categories = getResources().getStringArray(R.array.product_categories);
        for (String category : categories) {
            Chip chip = (Chip) getLayoutInflater()
                    .inflate(R.layout.item_category_chip, binding.chipGroupCategory, false);
            // كل شريحة تحتاج معرفاً، فمن دونها لا يستطيع ChipGroup أن يُبقي واحدة مختارة فقط
            chip.setId(View.generateViewId());
            chip.setText(category);
            chip.setTag(category);
            binding.chipGroupCategory.addView(chip);

            if (selectedCategory.isEmpty()) {
                chip.setChecked(true);
                selectedCategory = category;
            }
        }

        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                selectedCategory = "";
                return;
            }
            Chip chosen = group.findViewById(checkedIds.get(0));
            selectedCategory = chosen == null || chosen.getTag() == null
                    ? "" : chosen.getTag().toString();
        });
    }

    /**
     * بطاقات وقت التحضير الثلاث تُبنى من ملف القوائم أيضاً: النص المعروض "+30"
     * والقيمة المحفوظة "30-60"، لأن التاجر يفكّر بنطاق والزبون يحتاج رقماً.
     */
    private void buildPrepTimeCards() {
        String[] labels = getResources().getStringArray(R.array.product_prep_times);
        String[] values = getResources().getStringArray(R.array.product_prep_time_values);
        int gapPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8,
                getResources().getDisplayMetrics());

        for (int index = 0; index < labels.length; index++) {
            final int position = index;
            View card = getLayoutInflater()
                    .inflate(R.layout.item_product_prep_time, binding.prepTimeGroup, false);
            ((TextView) card.findViewById(R.id.tv_prep_value)).setText(labels[index]);

            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
            if (position > 0) {
                params.setMarginStart(gapPx);
            }
            card.setOnClickListener(v -> selectPrepTime(position));
            binding.prepTimeGroup.addView(card, params);
        }

        int preferred = DEFAULT_PREP_TIME_INDEX < values.length
                ? DEFAULT_PREP_TIME_INDEX : values.length - 1;
        selectPrepTime(preferred);
    }

    private void selectPrepTime(int index) {
        String[] values = getResources().getStringArray(R.array.product_prep_time_values);
        if (index < 0 || index >= values.length) {
            return;
        }
        selectedPrepTime = values[index];

        for (int i = 0; i < binding.prepTimeGroup.getChildCount(); i++) {
            // البطاقة تمرّر حالة الاختيار لأبنائها، فتتغيّر خلفيتها وألوان نصّها معاً
            binding.prepTimeGroup.getChildAt(i).setSelected(i == index);
        }
    }

    /**
     * تعبئة النموذج للتعديل: كل حقل يأخذ قيمته المحفوظة، ومن دون معرّف في النية
     * تبقى الشاشة إضافة عادية. والصورة القديمة تُعرض من رابطها، ولا تُرفع من جديد
     * إلا إذا اختار التاجر غيرها — فإعادة رفع ملف لا تغيير فيه تهدر بيانات الزبون.
     */
    private void fillForEditing() {
        Bundle extras = getIntent().getExtras();
        String id = extras == null ? "" : extras.getString(EXTRA_ID, "");
        if (id.isEmpty()) {
            return;
        }

        String name = extras.getString(EXTRA_NAME, "");
        String category = extras.getString(EXTRA_CATEGORY, "");
        double price = extras.getDouble(EXTRA_PRICE);
        String prepTime = extras.getString(EXTRA_PREP_TIME, "");
        String description = extras.getString(EXTRA_DESCRIPTION, "");
        boolean available = extras.getBoolean(EXTRA_AVAILABLE, true);
        String photoUrl = extras.getString(EXTRA_PHOTO_URL, "");

        editing = new Product("", name, category, price);
        editing.setId(id);
        editing.setPrepTime(prepTime);
        editing.setDescription(description);
        editing.setAvailable(available);
        editing.setPhoto("", photoUrl);

        binding.etName.setText(name);
        binding.etPrice.setText(String.format(Locale.US, "%.2f", price));
        binding.etDescription.setText(description);
        binding.switchAvailable.setChecked(available);

        selectCategory(category);
        selectPrepTimeByValue(prepTime);
        binding.btnSave.setText(R.string.btn_update_product);

        if (!photoUrl.isEmpty()) {
            showStoredPhoto(photoUrl);
        }
    }

    /** تصنيف المنتج المحفوظ يُختار إن كان موجوداً في القائمة المشتركة */
    private void selectCategory(String category) {
        for (int i = 0; i < binding.chipGroupCategory.getChildCount(); i++) {
            Chip chip = (Chip) binding.chipGroupCategory.getChildAt(i);
            if (chip.getTag() != null && chip.getTag().equals(category)) {
                chip.setChecked(true);
                return;
            }
        }
    }

    /** نطاق التحضير يُقرأ من قيمته لا من نصه: "+30" معروض و"30-60" محفوظ */
    private void selectPrepTimeByValue(String value) {
        String[] values = getResources().getStringArray(R.array.product_prep_time_values);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                selectPrepTime(i);
                return;
            }
        }
    }

    /** صورة موجودة في كلوديناري تُعرض كما هي إلى أن يرفع التاجر غيرها */
    private void showStoredPhoto(String url) {
        Glide.with(this).load(url).into(binding.ivPhoto);
        binding.ivPhoto.setImageTintList(null);
        binding.ivPhoto.setVisibility(View.VISIBLE);
        binding.ivPhotoIcon.setVisibility(View.GONE);
        binding.tvPhotoTitle.setText(R.string.add_product_photo_change);
        binding.btnRemovePhoto.setVisibility(View.VISIBLE);
    }

    /**
     * المعاينة تُصغَّر قبل العرض: صورة الكاميرا الأصلية بحجم أربعة ميغابيكسل،
     * وتحميلها في خيط الواجهة يجمّد الشاشة نصف ثانية قبل أن يبدأ أي رفع.
     */
    private void showPhoto(Uri uri) {
        photoUri = uri;
        photoDropped = false;
        Bitmap thumbnail = Images.thumbnail(this, uri);
        if (thumbnail != null) {
            binding.ivPhoto.setImageBitmap(thumbnail);
        }
        binding.ivPhoto.setVisibility(View.VISIBLE);
        binding.ivPhotoIcon.setVisibility(View.GONE);
        binding.tvPhotoTitle.setText(R.string.add_product_photo_change);
        binding.btnRemovePhoto.setVisibility(View.VISIBLE);
    }

    private void clearPhoto() {
        photoUri = null;
        // في التعديل هذا قرار حذف الصورة لا إلغاء اختيار جديد، فالحفظ لازم يمسحها
        photoDropped = editing != null;
        binding.ivPhoto.setImageDrawable(null);
        binding.ivPhoto.setVisibility(View.GONE);
        binding.ivPhotoIcon.setVisibility(View.VISIBLE);
        binding.tvPhotoTitle.setText(R.string.add_product_photo_title);
        binding.btnRemovePhoto.setVisibility(View.GONE);
    }

    /**
     * التحقق حقلًا بعد حقل، والخطأ يظهر تحت صاحبه لا في تنبيه عام. والاسم والسعر
     * هما الحقلان الوحيدان اللازمان: الباقي إما اختياري أو له قيمة افتراضية.
     */
    private void save() {
        if (submitting) {
            return;
        }

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            finish();
            return;
        }

        String name = textOf(binding.etName.getText());
        if (name.length() < MIN_NAME_LENGTH) {
            showError(binding.tilName, binding.etName, R.string.error_product_name);
            return;
        }

        double price = parsePrice(textOf(binding.etPrice.getText()));
        if (price < Product.MIN_PRICE) {
            showError(binding.tilPrice, binding.etPrice, R.string.error_product_price);
            return;
        }

        Product product = new Product(account.getUid(), name, selectedCategory, price);
        product.setPrepTime(selectedPrepTime);
        product.setDescription(textOf(binding.etDescription.getText()));
        product.setAvailable(binding.switchAvailable.isChecked());

        // المعرّف هو الفاصل: بوثيقة معلومة الحفظ يعدّلها، وبلا معرّف ينشر جديدة
        if (editing != null) {
            product.setId(editing.getId());
        }

        setSubmitting(true);
        if (photoUri != null) {
            byte[] jpeg = Images.prepareForUpload(this, photoUri);
            if (jpeg == null) {
                setSubmitting(false);
                Toast.makeText(this, R.string.error_photo_invalid, Toast.LENGTH_LONG).show();
                return;
            }
            uploadPhoto(account.getUid(), product, jpeg);
            return;
        }

        // ما اختار صورة جديدة: نُبقي المحفوظة إلا إذا طلب حذفها صراحة
        if (editing != null && editing.hasPhoto() && !photoDropped) {
            saveProduct(product, editing.getPhotoPath(), editing.getPhotoUrl());
            return;
        }
        saveProduct(product, "", "");
    }

    /**
     * الرفع في خيط خلفي: صورة واحدة تنتظر شبكة، وانتظارها في خيط الواجهة يجمّد الشاشة
     * كما جمّدتها من قبل صور معرض المتجر الست.
     */
    private void uploadPhoto(String uid, Product product, byte[] jpeg) {
        new Thread(() -> {
            StorePhotoRepository.Uploaded uploaded = photoRepository.uploadProduct(uid, jpeg);
            if (uploaded == null) {
                runIfAlive(() -> {
                    setSubmitting(false);
                    Toast.makeText(this, R.string.error_product_photo_upload_failed,
                            Toast.LENGTH_LONG).show();
                });
                return;
            }
            runIfAlive(() -> saveProduct(product, uploaded.publicId, uploaded.url));
        }).start();
    }

    private void saveProduct(Product product, String photoPath, String photoUrl) {
        product.setPhoto(photoPath, photoUrl);

        productRepository.save(product, new ProductRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                runIfAlive(() -> {
                    Toast.makeText(AddProductActivity.this,
                            editing == null ? R.string.msg_product_saved : R.string.msg_product_updated,
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }

            @Override
            public void onError() {
                runIfAlive(() -> {
                    setSubmitting(false);
                    Toast.makeText(AddProductActivity.this,
                            editing == null ? R.string.error_product_save_failed
                                    : R.string.error_product_update_failed,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** السعر يُكتب بفاصل عشري، والنقطة والألفاظ كلها مقبولة ما دامت رقماً موجباً */
    private double parsePrice(String input) {
        if (TextUtils.isEmpty(input)) {
            return 0;
        }
        try {
            return Double.parseDouble(input.replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void showError(TextInputLayout layout, EditText field, int messageRes) {
        layout.setError(getString(messageRes));
        field.requestFocus();
    }

    private void setSubmitting(boolean submitting) {
        this.submitting = submitting;
        binding.btnSave.setEnabled(!submitting);
        if (editing != null) {
            binding.btnSave.setText(submitting
                    ? R.string.btn_product_updating : R.string.btn_update_product);
            return;
        }
        binding.btnSave.setText(submitting
                ? R.string.btn_product_saving : R.string.btn_save_product);
    }

    /**
     * تنفيذ على الواجهة إن كانت الشاشة باقية: الرفع يستغرق ثوانٍ قد يضغط خلالها
     * التاجر «إلغاء وتراجع»، فتحديث شاشة مغلقة لا معنى له.
     */
    private void runIfAlive(@NonNull Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    private String textOf(CharSequence value) {
        return TextUtils.isEmpty(value) ? "" : value.toString().trim();
    }
}
