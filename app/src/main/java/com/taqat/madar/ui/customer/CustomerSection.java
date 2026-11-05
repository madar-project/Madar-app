package com.taqat.madar.ui.customer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import android.content.Context;

import com.taqat.madar.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * أقسام الزبون الأربعة في الرئيسية، وما يفتحها من تصنيفات متاجر.
 *
 * القسم واجهة لا جدول في قاعدة البيانات: المتاجر تُقرأ من merchants على حقل category،
 * والقائمة التي يصفّها كل قسم مكتوبة في arrays.xml بقيم store_categories نفسها. فلو
 * تغيّرت تسمية تصنيف هناك ولم تُغيَّر معه، صار القسم بلا متاجر — وهذا ثمن إبقاء
 * التصنيف نصاً عربياً واحداً يقرأه التاجر والزبون من غير ترجمة بين الطرفين.
 *
 * القسمان الأخيران بلا قائمة تصنيفات: مسار التوصيل الخاص والخدمات المهنية لم يُبنَ،
 * فلا متاجر تحمل تصنيفهما بعد، ولا معنى لسؤال السيرفر عن شيء تُعرف إجابته قبل السؤال.
 */
public enum CustomerSection {

    /** المطاعم والمقاهي معاً، لأن الرسمة تجمع «وجبات سريعة ومقاهي» تحت زر واحد */
    RESTAURANTS(R.string.cust_sec_restaurants, R.string.cust_sec_restaurants_sub,
            R.drawable.ic_restaurant, R.array.section_restaurants_categories),

    MARKETS(R.string.cust_sec_markets, R.string.cust_sec_markets_sub,
            R.drawable.ic_store, R.array.section_markets_categories),

    DELIVERY(R.string.cust_sec_delivery, R.string.cust_sec_delivery_sub,
            R.drawable.ic_truck, 0),

    SERVICES(R.string.cust_sec_services, R.string.cust_sec_services_sub,
            R.drawable.ic_briefcase, 0),

    /**
     * ليس بطاقة في الرئيسية: يفتحه البحث وزر التصفية فيمسح كل تصنيفات المتاجر،
     * وشرائح التصنيف هناك تُبنى من المتاجر التي وصلت لا من قائمة ثابتة.
     */
    ALL(0, 0, 0, R.array.store_categories);

    @StringRes
    private final int titleRes;
    @StringRes
    private final int subtitleRes;
    private final int iconRes;
    private final int categoriesArrayRes;

    CustomerSection(@StringRes int titleRes, @StringRes int subtitleRes, int iconRes,
                    int categoriesArrayRes) {
        this.titleRes = titleRes;
        this.subtitleRes = subtitleRes;
        this.iconRes = iconRes;
        this.categoriesArrayRes = categoriesArrayRes;
    }

    @StringRes
    public int titleRes() {
        return titleRes;
    }

    @StringRes
    public int subtitleRes() {
        return subtitleRes;
    }

    public int iconRes() {
        return iconRes;
    }

    /** التصنيفات التي يُسأل عنها السيرفر، فارغة إن كان القسم بلا مسار بعد */
    @NonNull
    public List<String> categories(@NonNull Context context) {
        if (categoriesArrayRes == 0) {
            return new ArrayList<>();
        }
        return Arrays.asList(context.getResources().getStringArray(categoriesArrayRes));
    }

    /** اسم القسم كما يُمرَّر بين الشاشة ولائحتها، مع رجوع آمن للمطاعم لو جاء نص قديم */
    @NonNull
    public static CustomerSection of(String name) {
        for (CustomerSection section : values()) {
            if (section.name().equals(name)) {
                return section;
            }
        }
        return RESTAURANTS;
    }

    /**
     * قسم هذا التصنيف: تُقرأ منه أيقونة المتجر في صفحته، لأن الأيقونة هوية القسم
     * لا هوية الطبق. ويُرجَع null للتصنيف الفارغ أو لما ليس له قسم بعد، فالتوصيل
     * الخاص والخدمات المهنية بلا متاجر، واختراع أيقونة لهما كاختراع متجر لهما.
     *
     * ALL متروك عمداً: يمسح كل التصنيفات، فلو دخل الحلقة لأجاب عن كل شيء.
     */
    @Nullable
    public static CustomerSection sectionOfCategory(@NonNull Context context,
                                                    @NonNull String category) {
        if (category.isEmpty()) {
            return null;
        }
        for (CustomerSection section : new CustomerSection[]{RESTAURANTS, MARKETS}) {
            if (section.categories(context).contains(category)) {
                return section;
            }
        }
        return null;
    }
}
