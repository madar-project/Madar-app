package com.taqat.madar.core.util;

/**
 * أدوات أرقام الهواتف للسوق الفلسطيني (غزة/الضفة).
 *
 * محلياً يُكتب الرقم: 059 XXX XXXX  (10 أرقام يبدأ بصفر)
 * دولياً  يُكتب الرقم: ‎+970 59 XXX XXXX  (9 أرقام بعد كود الدولة، بدون الصفر)
 *
 * التطبيق يخزّن ويرسل الرقم دائماً بالصيغة الدولية E.164 لأن Firebase لا يقبل غيرها.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** كود الدولة الافتراضي: فلسطين +970 */
    public static final String PALESTINE_COUNTRY_CODE = "+970";

    /** الصيغة الدولية الصحيحة: +970 ثم رقم يبدأ بـ 5 ومجموع الأرقام 9 */
    private static final String E164_PATTERN = "\\+9705\\d{8}";

    /**
     * تحويل أي إدخال من المستخدم إلى الصيغة الدولية:
     * 0591234567 / 00970591234567 / 970591234567 / +970591234567  →  ‎+970591234567
     */
    public static String toE164(String input) {
        if (input == null) {
            return "";
        }

        // نحذف كل ما ليس رقماً (مسافات, شرطات, أقواس) ونحتفظ بعلامة + في البداية إن وجدت
        String trimmed = input.trim();
        String digits = trimmed.replaceAll("[^0-9]", "");

        if (digits.startsWith("00970")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("9705")) {
            return "+" + digits;
        }
        if (digits.startsWith("05")) {
            return PALESTINE_COUNTRY_CODE + digits.substring(1);
        }
        if (digits.startsWith("5")) {
            return PALESTINE_COUNTRY_CODE + digits;
        }
        return "+" + digits;
    }

    /** هل الرقم رقم جوال فلسطيني صالح؟ */
    public static boolean isValidPalestineMobile(String input) {
        return toE164(input).matches(E164_PATTERN);
    }

    /** عرض الرقم محلياً للمستخدم: ‎+970591234567 → 0591234567 */
    public static String toLocalDisplay(String e164) {
        if (e164 != null && e164.startsWith(PALESTINE_COUNTRY_CODE)) {
            return "0" + e164.substring(PALESTINE_COUNTRY_CODE.length());
        }
        return e164 == null ? "" : e164;
    }
}
