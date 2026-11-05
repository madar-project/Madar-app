package com.taqat.madar.ui.merchant.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.taqat.madar.R;
import com.taqat.madar.data.model.MerchantProfile;
import com.taqat.madar.data.remote.AuthRepository;
import com.taqat.madar.data.remote.MerchantProfileRepository;
import com.taqat.madar.databinding.FragmentMerchantHomeBinding;
import com.taqat.madar.ui.auth.login.LoginActivity;

/**
 * التبويب الأول لواجهة التاجر: رأس متجره وأرقام يومه وطلباته.
 *
 * الأرقام والطلبات المعروضة هنا كلها مصدرها مجموعة الطلبات، وهي لم تُنشأ بعد — أي أن
 * مسار الزبون الذي يضع طلباً لم يُبنَ — فالشاشة تعرض حالة «لا طلبات» الصريحة بدل أرقام
 * مجمّلة، وكل بطاقة طلب ستُضاف في الكود لا في التصميم.
 *
 * ما هو حقيقي هنا: اسم المتجر، شعاره المرفوع، وحالته مفتوح/مغلق المحفوظة في وثيقته.
 * والصفحة مستمعة لوثيقة المتجر لا تقرأ مرة وتسكت: الرفض عند إدارة مدار تنفيذُه حذف،
 * فقد يصير التاجر هنا ويفقد متجره وهو عليها.
 */
public class MerchantHomeFragment extends Fragment {

    private final AuthRepository authRepository = new AuthRepository();
    private final MerchantProfileRepository storeRepository = new MerchantProfileRepository();

    private FragmentMerchantHomeBinding binding;
    private String uid = "";
    private MerchantProfile store;
    /** يمنع السويتش أن يحفظ حالة جديدة وهو يُعبَّأ من البيانات لا من يد التاجر */
    private boolean rendering;
    /** لا حركة بعد الآن: إما خرجنا لإشعار أو وجّهنا الشاشة، فلا نكررها مع كل لقطة */
    private boolean settled;
    private ListenerRegistration storeWatch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMerchantHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser account = authRepository.currentUser();
        if (account == null) {
            openLogin(getString(R.string.error_account_required));
            return;
        }
        uid = account.getUid();

        binding.switchOpen.setOnCheckedChangeListener((button, checked) -> {
            if (rendering) {
                return;
            }
            applyOpenState(checked);
            saveOpenState(checked);
        });

        binding.tvShowAll.setOnClickListener(v -> openSoon());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!uid.isEmpty()) {
            watchStore();
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
     * متابعة وثيقة المتجر: أول لقطة تعبّئ الشاشة، وكل لقطة بعدها تحمل قراراً جديداً
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
                runOnUi(() -> {
                    if (binding != null && !rendering && !settled) {
                        Toast.makeText(requireContext(), R.string.error_load_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void render(MerchantProfile profile) {
        if (settled) {
            // لقطة وصلت بعد أن قررنا الخروج: لا معنى لتعبئة شاشة مُغلقة
            return;
        }
        store = profile;

        // لا بوابة ثانية على حالة المتجر هنا: الاعتماد يُقاس من وثيقة الحساب وحدها،
        // ولو بوّبنا مرتين لرمى كل منا الآخر حلقة لا تنتهي عند أي اختلاف بينهما
        rendering = true;
        binding.tvStoreName.setText(profile.getStoreName());
        binding.switchOpen.setChecked(profile.isAcceptingOrders());
        rendering = false;
        applyOpenState(profile.isAcceptingOrders());

        if (profile.hasLogo()) {
            Glide.with(this)
                    .load(profile.getLogoUrl())
                    .placeholder(R.drawable.ic_store)
                    .into(binding.ivLogo);
            binding.ivLogo.setImageTintList(null);
        }

        // لا طلبات بعد، فكل الأرقام صفر ولا نقطة تنبيه
        binding.tvSales.setText(getString(R.string.home_currency) + getString(R.string.home_zero));
        binding.viewAlertDot.setVisibility(View.GONE);
    }

    /** سطر الحالة تحت اسم المتجر، يتبع السويتش لا حالة الاعتماد */
    private void applyOpenState(boolean open) {
        binding.tvStoreStatus.setText(open
                ? R.string.home_status_open : R.string.home_status_closed);
    }

    /**
     * حفظ حالة المحل فور تحريك السويتش: التاجر يتوقّع أن يفتح متجره ويغلقه بيده،
     * والفشل يُرجع السويتش إلى حالته السابقة بدل أن يبقى على كذبة.
     */
    private void saveOpenState(boolean open) {
        storeRepository.setAcceptingOrders(uid, open,
                new MerchantProfileRepository.WriteCallback() {
                    @Override
                    public void onSuccess() {
                        if (store != null) {
                            store.setAcceptingOrders(open);
                        }
                    }

                    @Override
                    public void onError() {
                        runOnUi(() -> {
                            if (binding == null) {
                                return;
                            }
                            rendering = true;
                            binding.switchOpen.setChecked(!open);
                            rendering = false;
                            applyOpenState(!open);
                            Toast.makeText(requireContext(), R.string.error_status_update_failed,
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    /**
     * متجر حُذفت وثيقته أو فقدنا الصلاحية عليها: الإدارة رفضت الطلب، والرفض عند مدار
     * حذف للحساب كله لا كتابة حالة فيه. فالجلسة هنا صارت ميتة، والمخرج أن نخبره
     * ونسجّل خروجه إلى شاشة الدخول، لا أن نبقى نعرض بيانات لم تعد موجودة.
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
     * تنفيذ على خيط الواجهة إن كانت الصفحة ما زالت متصلة بنشاطها: الاستماع قد يصل
     * بلقطة بعد أن ينقلب التاجر لتبويب آخر أو يُغلق التطبيق.
     */
    private void runOnUi(@NonNull Runnable action) {
        if (!isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(action);
    }

    private void openSoon() {
        Toast.makeText(requireContext(), R.string.msg_screen_coming_soon,
                Toast.LENGTH_SHORT).show();
    }
}
