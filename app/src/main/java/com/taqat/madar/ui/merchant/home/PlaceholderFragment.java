package com.taqat.madar.ui.merchant.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.taqat.madar.databinding.FragmentPlaceholderBinding;

/**
 * صفحة مؤجّلة لتبويب لم تُبنَ شاشته بعد: الطلبات والعروض والإعدادات.
 *
 * وجودها ضروري ما دام التبويب ظاهراً في شريط التنقّل: النقر على تبويب بلا صفحة
 * يجب ألا يترك المستخدم واقفاً في الشاشة التي كان فيها بلا تفسير.
 */
public class PlaceholderFragment extends Fragment {

    private static final String ARG_TITLE = "title";

    private FragmentPlaceholderBinding binding;

    /** التبويب يعرض اسمه فوق الصفحة المؤجّلة، فيعرف المستخدم أنه وصل ولم يضلّ الطريق */
    public static PlaceholderFragment newInstance(int titleRes) {
        PlaceholderFragment page = new PlaceholderFragment();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_TITLE, titleRes);
        page.setArguments(arguments);
        return page;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaceholderBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle arguments = getArguments();
        if (arguments != null) {
            binding.tvTitle.setText(arguments.getInt(ARG_TITLE));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
