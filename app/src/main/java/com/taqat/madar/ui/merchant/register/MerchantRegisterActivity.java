package com.taqat.madar.ui.merchant.register;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.BuildConfig;
import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.core.util.Emails;
import com.taqat.madar.core.util.Images;
import com.taqat.madar.core.util.Passwords;
import com.taqat.madar.core.util.PhoneNumbers;
import com.taqat.madar.data.model.MerchantProfile;
import com.taqat.madar.data.model.Role;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthError;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.MerchantProfileRepository;
import com.taqat.madar.data.remote.StorePhotoRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.databinding.ActivityMerchantRegisterBinding;
import com.taqat.madar.ui.auth.email.EmailVerificationActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * شاشة تسجيل التاجر: الحساب ومتجره في نموذج واحد.
 *
 * التاجر لا يفتح التطبيق قبل أن تعتمد إدارة مدار متجره، فبيانات نشاطه تُكتب مرة واحدة
 * هنا مع الحساب: users تحمل صاحبه، و merchants تحمل المتجر بحالة قيد المراجعة.
 * تأكيد البريد بعد الحفظ مباشرة، لأن الرابط يصل لصاحبه قبل أن يُقرأ ملف المتجر.
 *
 * تعديل البيانات لاحقاً ليس من هذه الشاشة، بل من بروفايل التاجر بعد الاعتماد.
 */
public class MerchantRegisterActivity extends AppCompatActivity {

    /** أقل عدد حروف مقبول للاسم واسم المتجر، ومنهما حرفان بلا فائدة */
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MIN_ADDRESS_LENGTH = 6;

    /** متجر يعمل نهاراً: يفتح التاسعة ويغلق الحادية عشرة مساءً، وهو الافتراضي حتى يغيّره التاجر */
    private static final String DEFAULT_OPENING = "09:00";
    private static final String DEFAULT_CLOSING = "23:00";

    private ActivityMerchantRegisterBinding binding;
    private final AuthRepository authRepository = new AuthRepository();
    private final UserRepository userRepository = new UserRepository();
    private final MerchantProfileRepository storeRepository = new MerchantProfileRepository();
    private final StorePhotoRepository photoRepository = new StorePhotoRepository();

    /** labels الأوقات كما يراها التاجر، و values هي نفسها المحفوظة بصيغة 24 ساعة */
    private String[] hourLabels = new String[0];
    private String[] hourValues = new String[0];

    private String selectedCategory = "";
    private Uri logoUri;
    private final List<Uri> galleryUris = new ArrayList<>();

    /** نص النموذج كما أُخذ عند الإرسال، والوثيقة تُبنى منه وتُستكمل بعد الرفع */
    private MerchantProfile draft;
    private String fullName = "";
    private String email = "";
    private String phoneE164 = "";
    private boolean submitting;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickLogo =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null) {
                            showLogo(uri);
                        }
                    });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickGallery =
            registerForActivityResult(
                    new ActivityResultContracts.PickMultipleVisualMedia(MerchantProfile.MAX_PHOTOS),
                    this::addPhotos);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMerchantRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        hourLabels = getResources().getStringArray(R.array.business_hours);
        hourValues = getResources().getStringArray(R.array.business_hour_values);

        buildCategoryChips();
        setupHours();
        rebuildGallery();

        binding.btnBack.setOnClickListener(v -> finish());
        binding.boxLogo.setOnClickListener(v -> pickImage(pickLogo));
        binding.btnRemoveLogo.setOnClickListener(v -> removeLogo());
        binding.btnSubmit.setOnClickListener(v -> submit());
    }

    /**
     * شرائح التصنيفات تُبنى من ملف القوائم، لأن النص العربي نفسه هو القيمة المحفوظة.
     * وبهذا تبقى القائمة مصدراً واحداً: تُعرض هنا اليوم، وتُعرض في تصفّح الزبون غداً.
     */
    private void buildCategoryChips() {
        String[] categories = getResources().getStringArray(R.array.store_categories);
        for (String category : categories) {
            Chip chip = (Chip) getLayoutInflater()
                    .inflate(R.layout.item_category_chip, binding.chipGroupCategory, false);
            // كل شريحة تحتاج معرفاً، فمن دونها لا يستطيع ChipGroup أن يُبقي واحدة مختارة فقط
            chip.setId(View.generateViewId());
            chip.setText(category);
            chip.setTag(category);
            binding.chipGroupCategory.addView(chip);
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

    private void setupHours() {
        binding.actvOpening.setSimpleItems(hourLabels);
        binding.actvClosing.setSimpleItems(hourLabels);
        binding.actvOpening.setText(hourLabels[indexOfValue(DEFAULT_OPENING, 3)], false);
        binding.actvClosing.setText(
                hourLabels[indexOfValue(DEFAULT_CLOSING, hourLabels.length - 1)], false);

        // أيقونة القائمة مرفوضة مع الخانة الفارغة، فالنقر على الحقل نفسه هو ما يفتح القائمة
        binding.actvOpening.setOnClickListener(v -> binding.actvOpening.showDropDown());
        binding.actvClosing.setOnClickListener(v -> binding.actvClosing.showDropDown());
    }

    private void pickImage(ActivityResultLauncher<PickVisualMediaRequest> launcher) {
        launcher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void showLogo(Uri uri) {
        logoUri = uri;
        drawOrPlaceholder(binding.ivLogo, uri);
        binding.ivLogo.setVisibility(View.VISIBLE);
        binding.ivLogoPlaceholder.setVisibility(View.GONE);
        binding.btnRemoveLogo.setVisibility(View.VISIBLE);
    }

    private void removeLogo() {
        logoUri = null;
        binding.ivLogo.setImageDrawable(null);
        binding.ivLogo.setVisibility(View.GONE);
        binding.ivLogoPlaceholder.setVisibility(View.VISIBLE);
        binding.btnRemoveLogo.setVisibility(View.GONE);
    }

    /** الصور الجديدة تُضاف إلى ما اختاره التاجر حتى تمتلئ الخانات الست */
    private void addPhotos(@Nullable List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        for (Uri uri : uris) {
            if (galleryUris.size() >= MerchantProfile.MAX_PHOTOS) {
                break;
            }
            if (!galleryUris.contains(uri)) {
                galleryUris.add(uri);
            }
        }
        rebuildGallery();
    }

    /**
     * إعادة رسم بلاطات المعرض من الصفر: أبعد من تعديل البلاطات واحداً واحداً،
     * لأن الحذف من الوسط يغيّر مواضع البقية كلها.
     */
    private void rebuildGallery() {
        binding.galleryGrid.removeAllViews();

        for (int position = 0; position < galleryUris.size(); position++) {
            final int index = position;
            View tile = getLayoutInflater()
                    .inflate(R.layout.item_store_photo, binding.galleryGrid, false);
            drawOrPlaceholder(tile.findViewById(R.id.iv_photo), galleryUris.get(index));
            tile.findViewById(R.id.btn_remove_photo).setOnClickListener(v -> {
                galleryUris.remove(index);
                rebuildGallery();
            });
            binding.galleryGrid.addView(tile);
        }

        if (galleryUris.size() < MerchantProfile.MAX_PHOTOS) {
            View addTile = getLayoutInflater()
                    .inflate(R.layout.item_store_photo_add, binding.galleryGrid, false);
            addTile.setOnClickListener(v -> pickImage(pickGallery));
            binding.galleryGrid.addView(addTile);
        }

        binding.tvGalleryCounter.setText(getString(R.string.gallery_counter,
                galleryUris.size(), MerchantProfile.MAX_PHOTOS));
    }

    /** عرض الصورة مصغّرة على قدر المربع، فترجمة صورة الهاتف كاملة في ذاكرة الجهاز تبذير */
    private void drawOrPlaceholder(ImageView target, Uri uri) {
        Bitmap thumbnail = Images.thumbnail(this, uri);
        if (thumbnail != null) {
            target.setImageBitmap(thumbnail);
        } else {
            target.setImageResource(R.drawable.ic_store);
        }
    }

    /** التحقق حقلًا بعد حقل، والخطأ يظهر تحت صاحبه لا في تنبيه عام */
    private void submit() {
        if (submitting) {
            return;
        }

        fullName = textOf(binding.etName.getText());
        String storeName = textOf(binding.etStoreName.getText());
        email = Emails.normalize(textOf(binding.etEmail.getText()));
        String phoneInput = textOf(binding.etPhone.getText());
        String password = textOf(binding.etPassword.getText());
        String confirmation = textOf(binding.etConfirmPassword.getText());
        String address = textOf(binding.etAddress.getText());

        if (fullName.length() < MIN_NAME_LENGTH) {
            showError(binding.tilName, binding.etName, R.string.error_name_short);
            return;
        }
        if (storeName.length() < MIN_NAME_LENGTH) {
            showError(binding.tilStoreName, binding.etStoreName, R.string.error_store_name_short);
            return;
        }
        if (selectedCategory.isEmpty()) {
            Toast.makeText(this, R.string.error_category_required, Toast.LENGTH_LONG).show();
            return;
        }
        if (!Emails.isValid(email)) {
            showError(binding.tilEmail, binding.etEmail, R.string.error_email_invalid);
            return;
        }
        if (!PhoneNumbers.isValidPalestineMobile(phoneInput)) {
            showError(binding.tilPhone, binding.etPhone, R.string.error_phone_invalid);
            return;
        }
        if (!Passwords.isStrongEnough(password)) {
            showError(binding.tilPassword, binding.etPassword, R.string.error_password_weak);
            return;
        }
        if (!Passwords.doMatch(password, confirmation)) {
            showError(binding.tilConfirmPassword, binding.etConfirmPassword,
                    R.string.error_password_mismatch);
            return;
        }
        if (address.length() < MIN_ADDRESS_LENGTH) {
            showError(binding.tilAddress, binding.etAddress, R.string.error_address_short);
            return;
        }
        if (galleryUris.isEmpty()) {
            Toast.makeText(this, R.string.error_photo_required, Toast.LENGTH_LONG).show();
            return;
        }

        List<String> payments = selectedPayments();
        if (payments.isEmpty()) {
            Toast.makeText(this, R.string.error_payment_required, Toast.LENGTH_LONG).show();
            return;
        }

        phoneE164 = PhoneNumbers.toE164(phoneInput);
        draft = new MerchantProfile("", storeName, selectedCategory, address);
        draft.setDescription(textOf(binding.etDescription.getText()));
        draft.setOpeningTime(valueFor(binding.actvOpening.getText()));
        draft.setClosingTime(valueFor(binding.actvClosing.getText()));
        draft.setPaymentMethods(payments);

        setSubmitting(true);
        authRepository.createAccount(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onAuthenticated(FirebaseUser account) {
                if (account == null) {
                    runIfAlive(() -> {
                        setSubmitting(false);
                        Toast.makeText(MerchantRegisterActivity.this,
                                R.string.error_auth_unknown, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                saveAccount(account);
            }

            @Override
            public void onError(AuthError error) {
                runIfAlive(() -> handleAuthError(error));
            }
        });
    }

    /** طرق الدفع كما سمّاها التاجر في الشاشة، فهي نفسها ما يقرأه الزبون في صفحة المتجر */
    private List<String> selectedPayments() {
        List<String> methods = new ArrayList<>();
        if (binding.cbJawwal.isChecked()) {
            methods.add(binding.cbJawwal.getText().toString());
        }
        if (binding.cbPalpay.isChecked()) {
            methods.add(binding.cbPalpay.getText().toString());
        }
        if (binding.cbBop.isChecked()) {
            methods.add(binding.cbBop.getText().toString());
        }
        return methods;
    }

    /** بعض أخطاء Firebase تخص حقلاً بعينه، فيُعرض تحته لا في تنبيه عام */
    private void handleAuthError(AuthError error) {
        setSubmitting(false);

        if (error == AuthError.EMAIL_IN_USE) {
            showError(binding.tilEmail, binding.etEmail, error.messageRes());
            return;
        }
        if (error == AuthError.PASSWORD_WEAK) {
            showError(binding.tilPassword, binding.etPassword, error.messageRes());
            return;
        }

        String message = getString(error.messageRes());
        String detail = authRepository.lastTechnicalMessage();
        if (BuildConfig.DEBUG && !TextUtils.isEmpty(detail)) {
            message = message + " — " + shorten(detail);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /**
     * حفظ صاحب المتجر في users قبل متجره: مفتاح وثيقة المتجر هو معرّف حسابه،
     * فلا معنى لمتجر بلا حساب سبقه.
     */
    private void saveAccount(FirebaseUser account) {
        User profile = new User(account.getUid(), fullName, email, phoneE164, Role.MERCHANT);
        profile.setEmailVerified(account.isEmailVerified());

        userRepository.saveUser(profile, new UserRepository.ResultCallback() {
            @Override
            public void onSuccess() {
                runIfAlive(() -> uploadImages(account.getUid()));
            }

            @Override
            public void onError() {
                runIfAlive(() -> {
                    // الحساب نشأ في المصادقة لكن صاحبه لم يُحفظ، فنغلق الجلسة لتعاد المحاولة
                    authRepository.signOut();
                    setSubmitting(false);
                    Toast.makeText(MerchantRegisterActivity.this,
                            R.string.error_save_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * تجهيز الصور ورفعها قبل حفظ وثيقة المتجر، فالحفظ بلا روابط يترك الطلب عند المراجعة
     * بلا دليل، والمعرّفات وحدها ما يسمح بحذف صورة لاحقاً.
     *
     * التصغير والرفع في خيط خلفي واحد: الأول يقرأ الصور من الذاكرة، والثاني ينتظر الشبكة،
     * وكلاهما يجمّد الشاشة إن تمّ في خيط الواجهة. والصور تُجهَّز كلها قبل أن تُرفع،
     * فلو كانت إحداها لا تُقرأ تعطلت العملية قبل أن نستهلك اتصالاً واحداً.
     */
    private void uploadImages(String uid) {
        final List<byte[]> logoBytes = new ArrayList<>();
        if (logoUri != null) {
            byte[] prepared = Images.prepareForUpload(this, logoUri);
            if (prepared == null) {
                failUpload(R.string.error_photo_invalid);
                return;
            }
            logoBytes.add(prepared);
        }

        final List<byte[]> photoBytes = new ArrayList<>();
        for (Uri photo : galleryUris) {
            byte[] prepared = Images.prepareForUpload(this, photo);
            if (prepared == null) {
                failUpload(R.string.error_photo_invalid);
                return;
            }
            photoBytes.add(prepared);
        }

        new Thread(() -> {
            String logoPath = "";
            String logoUrl = "";
            if (!logoBytes.isEmpty()) {
                StorePhotoRepository.Uploaded uploaded = photoRepository.upload(uid, logoBytes.get(0));
                if (uploaded == null) {
                    failUpload(R.string.error_photo_upload_failed);
                    return;
                }
                logoPath = uploaded.publicId;
                logoUrl = uploaded.url;
            }

            List<String> paths = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            for (byte[] jpeg : photoBytes) {
                StorePhotoRepository.Uploaded uploaded = photoRepository.upload(uid, jpeg);
                if (uploaded == null) {
                    failUpload(R.string.error_photo_upload_failed);
                    return;
                }
                paths.add(uploaded.publicId);
                urls.add(uploaded.url);
            }

            final String markPath = logoPath;
            final String markUrl = logoUrl;
            runIfAlive(() -> saveStore(uid, markPath, markUrl, paths, urls));
        }).start();
    }

    private void saveStore(String uid, String logoPath, String logoUrl,
                           List<String> paths, List<String> urls) {
        draft.setUid(uid);
        draft.setLogo(logoPath, logoUrl);
        draft.setPhotos(paths, urls);

        storeRepository.save(draft, new MerchantProfileRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                runIfAlive(MerchantRegisterActivity.this::finishRegistration);
            }

            @Override
            public void onError() {
                runIfAlive(() -> {
                    setSubmitting(false);
                    Toast.makeText(MerchantRegisterActivity.this,
                            R.string.error_save_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** بعد الحفظ: تأكيد البريد، فالشاشة ترسل الرابط بنفسها وتنتظر الضغط عليه */
    private void finishRegistration() {
        Toast.makeText(this, R.string.msg_merchant_created, Toast.LENGTH_LONG).show();

        Intent next = new Intent(this, EmailVerificationActivity.class);
        next.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(next);
        finish();
    }

    /** موضع الوقت في القائمة: من النص المحفوظ إلى ما يعرض للتاجر */
    private int indexOfValue(String value, int fallback) {
        for (int i = 0; i < hourValues.length; i++) {
            if (hourValues[i].equals(value)) {
                return i;
            }
        }
        return fallback;
    }

    /** النص الظاهر في الخانة إلى القيمة بصيغة 24 ساعة، ومن لم يختر يُترك له الافتراضي */
    private String valueFor(CharSequence label) {
        String text = textOf(label);
        int index = Arrays.asList(hourLabels).indexOf(text);
        return index >= 0 ? hourValues[index] : "";
    }

    private void showError(TextInputLayout layout, EditText field, int messageRes) {
        layout.setError(getString(messageRes));
        field.requestFocus();
    }

    private void failUpload(int messageRes) {
        runIfAlive(() -> {
            setSubmitting(false);
            Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
        });
    }

    /**
     * تنفيذ على الواجهة إن كانت الشاشة باقية: الرفع يستغرق ثوانٍ قد يضغط خلالها
     * التاجر زر الرجوع، فتحديث شاشة مغلقة أو بدء نشاط منها لا معنى له.
     */
    private void runIfAlive(@NonNull Runnable action) {
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    private void setSubmitting(boolean submitting) {
        this.submitting = submitting;
        binding.btnSubmit.setEnabled(!submitting);
        binding.btnSubmit.setText(submitting
                ? R.string.merchant_submitting : R.string.btn_merchant_save);
    }

    /** أول سطر من رسالة Firebase الخام، بطول يبقى مقروءاً في التنبيه */
    private String shorten(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() <= 110 ? single : single.substring(0, 110) + "…";
    }

    private String textOf(CharSequence value) {
        return TextUtils.isEmpty(value) ? "" : value.toString().trim();
    }
}
