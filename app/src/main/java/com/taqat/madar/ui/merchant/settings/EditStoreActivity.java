package com.taqat.madar.ui.merchant.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseUser;
import com.taqat.madar.R;
import com.taqat.madar.core.ui.InsetUtils;
import com.taqat.madar.core.util.Images;
import com.taqat.madar.core.util.PhoneNumbers;
import com.taqat.madar.data.model.MerchantProfile;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.MerchantProfileRepository;
import com.taqat.madar.data.remote.StorePhotoRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.databinding.ActivityEditStoreBinding;

import java.util.Locale;

/**
 * شاشة تعديل بيانات المتجر، وتُفتح من تبويب الإعدادات.
 *
 * الثلاثة الحقول هنا هي ما يملك التاجر تغييره بعد الاعتماد: اسم نشاطه وعنوانه ورقم
 * تواصله، وشعاره إن بدّله. أما التصنيف والأوقات وطرق الدفع فقرارها يبقى في نموذج
 * التسجيل، وحالة الاعتماد نفسها مغلقة على الإدارة: القبول والرفض تنفيذها في الوثيقة
 * لا في هذه الشاشة.
 *
 * الرقم ليس في وثيقة المتجر: الذي يُطلب عند التسجيل هو رقم صاحب الحساب، فيُحفظ مع
 * الحساب في users. فتحفظ الشاشة وثيقتين، المتجر أولاً ثم الحساب من بعده، لأن المتجر
 * هو ما يقرأه الزبون.
 */
public class EditStoreActivity extends AppCompatActivity {

    /** نفس حدود نموذج التسجيل، حتى لا يقبل التعديل ما رفضه الإنشاء */
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MIN_ADDRESS_LENGTH = 6;

    private ActivityEditStoreBinding binding;

    private final AuthRepository authRepository = new AuthRepository();
    private final MerchantProfileRepository storeRepository = new MerchantProfileRepository();
    private final UserRepository userRepository = new UserRepository();
    private final StorePhotoRepository photoRepository = new StorePhotoRepository();

    private String uid = "";

    /** نسخة الوثيقة كما قُرئت: نُعدّل عليها ونكتبها كاملة، فالحفظ يكتب الحقول كلها */
    private MerchantProfile store;

    /** الرقم كما قُرئ من الحساب، فإن لم يتغيّر لا نكتبه من جديد */
    private String savedPhone = "";

    private Uri logoUri;
    private boolean submitting;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickLogo =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    showLogo(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditStoreBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        InsetUtils.applySystemBarPadding(binding.getRoot());

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            Toast.makeText(this, R.string.error_account_required, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        uid = account.getUid();

        binding.boxLogo.setOnClickListener(v -> {
            if (!submitting) {
                pickLogo.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            }
        });
        binding.btnSave.setOnClickListener(v -> save());
        binding.btnCancel.setOnClickListener(v -> finish());

        loadStore();
    }

    /**
     * قراءة وثيقة المتجر لتعبئة النموذج. لا استماع هنا كما في الإعدادات: الشاشة
     * قصيرة العمر ومفتوحة على قرار واحد، فلقطة واحدة تكفي ما دامت الوثيقة لن تُقرأ
     * إلا لهذه التعبئة.
     */
    private void loadStore() {
        storeRepository.load(uid, new MerchantProfileRepository.ProfileCallback() {
            @Override
            public void onLoaded(@NonNull MerchantProfile profile) {
                runIfAlive(() -> fill(profile));
            }

            @Override
            public void onNotFound() {
                runIfAlive(EditStoreActivity.this::closeUnloaded);
            }

            @Override
            public void onError() {
                runIfAlive(EditStoreActivity.this::closeUnloaded);
            }
        });
    }

    /** ما لم تُقرأ بياناته لا يُعدَّل: شاشة فارغة بحفظ فارغ أضرّ من رسالة وخروج */
    private void closeUnloaded() {
        Toast.makeText(this, R.string.edit_load_failed, Toast.LENGTH_LONG).show();
        finish();
    }

    private void fill(@NonNull MerchantProfile profile) {
        store = profile;

        binding.etStoreName.setText(profile.getStoreName());
        binding.etAddress.setText(profile.getAddress());
        binding.tvStoreName.setText(profile.getStoreName());
        binding.ivVerified.setVisibility(profile.isApproved() ? View.VISIBLE : View.GONE);
        binding.tvPortalState.setText(profile.isApproved()
                ? R.string.edit_portal_approved : R.string.edit_portal_pending);

        if (profile.hasLogo()) {
            Glide.with(this)
                    .load(profile.getLogoUrl())
                    .placeholder(R.drawable.ic_store)
                    .into(binding.ivLogo);
            binding.ivLogo.setVisibility(View.VISIBLE);
            binding.ivLogoPlaceholder.setVisibility(View.GONE);
        }

        loadPhone();
    }

    /**
     * رقم الحساب يُعرض مقسّماً كما يقرؤه صاحبه، ويُخزَّن بالصيغة الدولية. والتقسيم
     * للعين وحدها: الحفظ يجمعه أرقاماً قبل أن يكتبه.
     */
    private void loadPhone() {
        userRepository.loadUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onLoaded(@NonNull User user) {
                runIfAlive(() -> {
                    savedPhone = user.getPhone() == null ? "" : user.getPhone();
                    binding.etPhone.setText(savedPhone.isEmpty()
                            ? "" : displayPhone(savedPhone));
                });
            }

            @Override
            public void onNotFound() {
                // حساب بغير وثيقة: الخانة تُترك فارغة، والتسجيل يكتب رقمها من جديد
            }

            @Override
            public void onError() {
                runIfAlive(() -> Toast.makeText(EditStoreActivity.this,
                        R.string.error_load_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showLogo(Uri uri) {
        logoUri = uri;
        Glide.with(this)
                .load(uri)
                .placeholder(R.drawable.ic_store)
                .into(binding.ivLogo);
        binding.ivLogo.setVisibility(View.VISIBLE);
        binding.ivLogoPlaceholder.setVisibility(View.GONE);
    }

    /** التحقق حقل بحدّه تحت العين لا في تنبيه عام، لأن الخطأ يعرف مكانه */
    private void save() {
        if (submitting) {
            return;
        }
        if (store == null) {
            Toast.makeText(this, R.string.edit_load_failed, Toast.LENGTH_LONG).show();
            return;
        }

        binding.tilStoreName.setError(null);
        binding.tilPhone.setError(null);
        binding.tilAddress.setError(null);

        String name = textOf(binding.etStoreName);
        String address = textOf(binding.etAddress);
        String e164 = PhoneNumbers.toE164(textOf(binding.etPhone));

        if (name.length() < MIN_NAME_LENGTH) {
            binding.tilStoreName.setError(getString(R.string.edit_name_short));
            binding.etStoreName.requestFocus();
            return;
        }
        if (!PhoneNumbers.isValidPalestineMobile(e164)) {
            binding.tilPhone.setError(getString(R.string.error_phone_invalid));
            binding.etPhone.requestFocus();
            return;
        }
        if (address.length() < MIN_ADDRESS_LENGTH) {
            binding.tilAddress.setError(getString(R.string.edit_address_short));
            binding.etAddress.requestFocus();
            return;
        }

        store.setStoreName(name);
        store.setAddress(address);

        submitting = true;
        binding.btnSave.setEnabled(false);

        if (logoUri != null) {
            uploadLogo(e164);
        } else {
            saveStore(e164);
        }
    }

    /**
     * الشعار يُرفع قبل الكتابة كما في نموذج التسجيل: وثيقة بغير رابط يترك المتجر
     * بلا صورته عند الزبون، والتصغير والرفع كلاهما عمل طويل لا يجوز على خيط الواجهة.
     *
     * الصورة القديمة تبقى في التخزين: حذف الصور قرار سيرفر الإدارة وحده، وهو الذي
     * يملك المفتاح الذي يُحذف به، فيمسح مع الحساب كل صوره.
     */
    private void uploadLogo(String e164) {
        final Uri picked = logoUri;

        new Thread(() -> {
            byte[] jpeg = Images.prepareForUpload(this, picked);
            if (jpeg == null) {
                failSave(R.string.error_photo_invalid);
                return;
            }

            StorePhotoRepository.Uploaded uploaded = photoRepository.upload(uid, jpeg);
            if (uploaded == null) {
                failSave(R.string.error_photo_upload_failed);
                return;
            }

            runIfAlive(() -> {
                store.setLogo(uploaded.publicId, uploaded.url);
                logoUri = null;
                saveStore(e164);
            });
        }).start();
    }

    private void saveStore(String e164) {
        storeRepository.save(store, new MerchantProfileRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                runIfAlive(() -> savePhone(e164));
            }

            @Override
            public void onError() {
                failSave(R.string.error_save_failed);
            }
        });
    }

    /**
     * الرقم على وثيقة الحساب، وبعد أن تمّ حفظ المتجر: كتابة الحساب وحدها بلا متجر
     * تحفظ رقماً على حساب ببيانات قديمة، والكتابة كلها في طريق واحد تُبقي القرارين
     * متجاورين. وما لم يتغيّر الرقم لا كتابة ولا انتظار لخطأ من كتابة بلا معنى.
     */
    private void savePhone(String e164) {
        if (e164.equals(savedPhone)) {
            finishSave();
            return;
        }

        userRepository.updatePhone(uid, e164, new UserRepository.ResultCallback() {
            @Override
            public void onSuccess() {
                runIfAlive(EditStoreActivity.this::finishSave);
            }

            @Override
            public void onError() {
                failSave(R.string.error_save_failed);
            }
        });
    }

    private void finishSave() {
        Toast.makeText(this, R.string.edit_saved, Toast.LENGTH_LONG).show();
        finish();
    }

    /** فشل الحفظ يترك الشاشة كما كانت: الزر يرجع ضاغطاً، والرسول يقول أين المشكلة */
    private void failSave(int messageRes) {
        runIfAlive(() -> {
            submitting = false;
            binding.btnSave.setEnabled(true);
            Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
        });
    }

    @NonNull
    private String textOf(EditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    /**
     * +970597654321 إلى ‎+970 59 765 4321: تقسيم يقرؤه الفلسطيني بلمحة، ومن لم يكن
     * رقمه على كود الدولة يُعرض كما هو بلا تقسيم مُختَرَع.
     */
    @NonNull
    private String displayPhone(@NonNull String e164) {
        String national = e164.startsWith("+970") ? e164.substring(4) : e164;
        if (national.length() != 9) {
            return e164;
        }
        return String.format(Locale.US, "+970 %s %s %s",
                national.substring(0, 2), national.substring(2, 5), national.substring(5));
    }

    /** تنفيذ على خيط الواجهة ما دمنا لم نُنهِ الشاشة: الرفع ينتهي وقد خرج التاجر */
    private void runIfAlive(@NonNull Runnable action) {
        if (isFinishing()) {
            return;
        }
        runOnUiThread(action);
    }
}
