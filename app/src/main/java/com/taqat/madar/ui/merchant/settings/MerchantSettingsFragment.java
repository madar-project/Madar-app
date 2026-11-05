package com.taqat.madar.ui.merchant.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.BuildConfig;
import com.taqat.madar.R;
import com.taqat.madar.data.local.NotificationPrefs;
import com.taqat.madar.data.model.MerchantProfile;
import com.taqat.madar.data.model.User;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.MerchantProfileRepository;
import com.taqat.madar.data.remote.UserRepository;
import com.taqat.madar.databinding.FragmentMerchantSettingsBinding;
import com.taqat.madar.databinding.SheetNotificationSoundBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;
import com.taqat.madar.ui.merchant.notifications.MerchantNotificationsActivity;

import java.util.Locale;

/**
 * تبويب الإعدادات عند التاجر: حالته، بيانات متجره كما يراها الزبون، وملفه والدعم.
 *
 * الصفحة قراءة لا كتابة، عدا قرارين محليين: نغمة الطلبات محفوظة على الجهاز، وشاشة
 * السياسات نصٌّ ثابت. أما بيانات المتجر فتُعرض كما هي في وثيقته، والتعديل عليها شاشة
 * مستقلة تفتح منها: الاسم والعنوان والرقم والشعار، وحالة الاعتماد ليست بينها.
 *
 * الصفحة مستمعة لوثيقة المتجر لا تقرأ مرة وتسكت: الرفض عند إدارة مدار تنفيذُه حذف،
 * فقد يقعد التاجر على إعدادات متجره وهو يُحذف من تحته.
 */
public class MerchantSettingsFragment extends Fragment {

    private final AuthRepository authRepository = new AuthRepository();
    private final MerchantProfileRepository storeRepository = new MerchantProfileRepository();
    private final UserRepository userRepository = new UserRepository();

    private FragmentMerchantSettingsBinding binding;
    private NotificationPrefs notificationPrefs;
    private String uid = "";
    private ListenerRegistration storeWatch;

    /** لا حركة بعد الآن: إما خرجنا لإشعار أو وجّهنا الشاشة، فلا نكررها مع كل لقطة */
    private boolean settled;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMerchantSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            Toast.makeText(requireContext(), R.string.error_account_required,
                    Toast.LENGTH_LONG).show();
            return;
        }
        uid = account.getUid();
        notificationPrefs = new NotificationPrefs(requireContext());

        binding.tvVersion.setText(getString(R.string.settings_version,
                BuildConfig.VERSION_NAME));

        binding.rowSounds.setOnClickListener(v -> openSoundSheet());
        // الجرس في الرأس يفتح مركز الإشعارات، والنغمة بقيت على سطرِها وحده
        binding.ivNotifications.setOnClickListener(v -> startActivity(
                new Intent(requireContext(), MerchantNotificationsActivity.class)));
        binding.rowSupport.setOnClickListener(v -> openWhatsApp());
        binding.rowPolicies.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MerchantPoliciesActivity.class)));

        binding.btnEditStore.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EditStoreActivity.class)));
        binding.btnLogout.setOnClickListener(v -> signOut());

        renderSoundState();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!uid.isEmpty()) {
            watchStore();
            loadPhone();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (storeWatch != null) {
            storeWatch.remove();
            storeWatch = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * متابعة وثيقة المتجر: أول لقطة تعبّئ الصفحة، وكل لقطة بعدها تحمل قراراً جديداً
     * من الإدارة أو حذفاً لمتجر رُفض.
     */
    private void watchStore() {
        storeWatch = storeRepository.watch(uid, new MerchantProfileRepository.LiveCallback() {
            @Override
            public void onProfile(@NonNull MerchantProfile profile) {
                runOnUi(() -> {
                    if (binding != null) {
                        render(profile);
                    }
                });
            }

            @Override
            public void onRemoved() {
                runOnUi(() -> {
                    if (binding != null) {
                        openLogin(getString(R.string.msg_account_removed));
                    }
                });
            }

            @Override
            public void onUnavailable() {
                // الانقطاع ليس قراراً: نبقي ما على الشاشة ولا نفسّره حذفاً
                runOnUi(() -> toast(R.string.error_load_failed));
            }
        });
    }

    /**
     * رقم الجوال في وثيقة الحساب لا في وثيقة المتجر: الذي يُطلب عند التسجيل هو رقم
     * صاحبه، فبطاقة المتجر تبقى لبيانات النشاط. قراءة واحدة تكفي، فالرقم يتغيّر في
     * مسار آخر لا هنا.
     */
    private void loadPhone() {
        userRepository.loadUser(uid, new UserRepository.UserCallback() {
            @Override
            public void onLoaded(@NonNull User user) {
                runOnUi(() -> {
                    if (binding != null) {
                        binding.tvPhone.setText(displayPhone(user.getPhone()));
                    }
                });
            }

            @Override
            public void onNotFound() {
                runOnUi(() -> {
                    if (binding != null) {
                        binding.tvPhone.setText(R.string.settings_no_phone);
                    }
                });
            }

            @Override
            public void onError() {
                runOnUi(() -> toast(R.string.error_load_failed));
            }
        });
    }

    private void render(@NonNull MerchantProfile profile) {
        if (settled) {
            // لقطة وصلت بعد أن قررنا الخروج: لا معنى لتعبئة صفحة مُغلقة
            return;
        }

        binding.tvStoreName.setText(profile.getStoreName());
        binding.ivVerified.setVisibility(profile.isApproved() ? View.VISIBLE : View.GONE);

        String address = profile.getAddress();
        binding.tvAddress.setText(address.isEmpty()
                ? getString(R.string.settings_no_address) : address);

        boolean open = profile.isAcceptingOrders();
        binding.tvStoreState.setText(open
                ? R.string.settings_status_open : R.string.settings_status_closed);

        if (profile.hasLogo()) {
            Glide.with(this)
                    .load(profile.getLogoUrl())
                    .placeholder(R.drawable.ic_store)
                    .into(binding.ivLogo);
            binding.ivLogo.setImageTintList(null);
        }
    }

    /** حالة سطر النغمة كما هي محفوظة على الجهاز، لا كما نتخيّلها */
    private void renderSoundState() {
        if (binding == null) {
            return;
        }
        boolean on = notificationPrefs.isOrderSoundEnabled();
        binding.tvSoundsState.setText(on
                ? R.string.settings_state_active : R.string.settings_state_stopped);
        binding.tvSoundsState.setTextColor(ContextCompat.getColor(requireContext(),
                on ? R.color.success_green : R.color.text_secondary));
    }

    /**
     * ورقة النغمة: المفتاح يحفظ فور تحريكه ولا ينتظر زر «حفظ»، فقرار واحد لا يحتمل
     * أن يضيع بإغلاق الورقة بالسحب. والسطر الذي تحتها يصرّح أن الإشعارات نفسها
     * لم تُبنَ بعد، فلا يحسب التاجر أن هاتفه سيسكت لمّا تُفعَّل.
     */
    private void openSoundSheet() {
        if (notificationPrefs == null) {
            return;
        }
        SheetNotificationSoundBinding sheet =
                SheetNotificationSoundBinding.inflate(getLayoutInflater());

        sheet.switchSound.setChecked(notificationPrefs.isOrderSoundEnabled());
        sheet.switchSound.setOnCheckedChangeListener((button, checked) ->
                notificationPrefs.setOrderSoundEnabled(checked));

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(sheet.getRoot());
        dialog.setOnShowListener(shown -> {
            View frame = (View) sheet.getRoot().getParent();
            if (frame == null) {
                return;
            }
            frame.setBackgroundResource(R.drawable.bg_sheet_top);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(frame);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        sheet.btnDone.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(ignored -> renderSoundState());
        dialog.show();
    }

    /**
     * الدعم عبر واتساب: رابط wa.me يفتح المحادثة إن كان التطبيق مثبتاً، والرقم في
     * ملف النصوص لأنّه يتغيّر أكثر مما تتغيّر الشاشة.
     */
    private void openWhatsApp() {
        String number = getString(R.string.support_whatsapp_number);
        String message = Uri.encode(getString(R.string.support_whatsapp_message));
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/" + number + "?text=" + message));

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException missing) {
            toast(R.string.settings_support_unavailable);
        }
    }

    /** إنهاء الجلسة والعودة لبداية مسار تسجيل الدخول */
    private void signOut() {
        if (settled) {
            return;
        }
        settled = true;

        authRepository.signOut();

        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        requireActivity().startActivity(intent);
        requireActivity().finish();
    }

    /**
     * متجر حُذفت وثيقته أو فقدنا الصلاحية عليها: الحساب صار ميتاً، والمخرج أن نخبره
     * ونسجّل خروجه إلى شاشة الدخول لا أن نبقى نعرض بيانات لم تعد موجودة.
     */
    private void openLogin(String notice) {
        if (settled) {
            return;
        }
        settled = true;

        Toast.makeText(requireContext(), notice, Toast.LENGTH_LONG).show();
        authRepository.signOut();

        requireActivity().startActivity(new Intent(requireContext(), LoginActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        requireActivity().finish();
    }

    /**
     * الرقم المعروض مقسَّم كما يقرؤه الفلسطيني: ‎+970 59 765 4321. والتخزين يبقى
     * E.164 بلا فواصل، لأن التقسيم للعين لا للعنوان.
     */
    @NonNull
    private String displayPhone(@Nullable String e164) {
        if (e164 == null || e164.isEmpty()) {
            return getString(R.string.settings_no_phone);
        }
        String national = e164.startsWith("+970") ? e164.substring(4) : e164;
        if (national.length() != 9) {
            return e164;
        }
        return String.format(Locale.US, "+970 %s %s %s",
                national.substring(0, 2), national.substring(2, 5), national.substring(5));
    }

    private void toast(int messageRes) {
        if (binding != null) {
            Toast.makeText(requireContext(), messageRes, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * تنفيذ على خيط الواجهة إن كانت الصفحة ما زالت متصلة بنشاطها: الاستماع قد يصل
     * بلقطة بعد أن ينقلب التاجر لتبويب آخر أو يُغلق التطبيق.
     *
     * الاتصال يُعاد فحصه وقت التنفيذ لا قبل الطلب فقط: العودة من تطبيق خارجي قد
     * تصل والصفحة منعزلة قد مضى عليها دور كامل، والكتابة عليها حينها استثناء.
     */
    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (isAdded() && binding != null) {
                action.run();
            }
        });
    }
}
