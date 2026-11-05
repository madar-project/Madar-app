package com.taqat.madar.ui.customer.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.taqat.madar.R;
import com.taqat.madar.databinding.FragmentCustomerSoonBinding;

/**
 * صفحة تبويب لم يُسلَّم تصميمه: اسم التبويب وأيقونته وعبارة صريحة أن شاشته بعدُ لم تُرسم.
 *
 * وجودها ضروري لئلا تكون الضغطة على الشريط السفلي ميتة: الشريط أربعة تبويبات كما في
 * الرسمة، وواحد منها فقط مُسلَّم. فالأمانة أن تُقال للزبون كلمة لا أن تُملأ الشاشة
 * ببيانات مختلقة أو تُترك بيضاء بلا سبب.
 */
public class CustomerSoonFragment extends Fragment {

    /** اسم التبويب كما يظهر في الشريط، فيُعرض عنواناً على صفحته */
    private static final String ARG_LABEL = "tab_label";

    private FragmentCustomerSoonBinding binding;

    @NonNull
    public static CustomerSoonFragment newInstance(@StringRes int labelRes) {
        Bundle args = new Bundle();
        args.putInt(ARG_LABEL, labelRes);

        CustomerSoonFragment page = new CustomerSoonFragment();
        page.setArguments(args);
        return page;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCustomerSoonBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int label = requireArguments().getInt(ARG_LABEL, R.string.nav_home);
        binding.tvSoonLabel.setText(label);
        binding.ivSoonIcon.setImageResource(iconOf(label));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** الأيقونة نفسها التي في الشريط السفلي، فتعرف الصفحة مكانها بلا شرح */
    private int iconOf(int label) {
        if (label == R.string.nav_my_orders) {
            return R.drawable.ic_receipt;
        }
        if (label == R.string.nav_addresses) {
            return R.drawable.ic_bookmark;
        }
        if (label == R.string.nav_account) {
            return R.drawable.ic_person;
        }
        return R.drawable.ic_home;
    }
}
