package com.taqat.madar.core.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * تجهيز الصور قبل رفعها إلى التخزين.
 *
 * صورة الهاتف الواحدة تصل إلى أربعة ميجابايت أو أكثر، ورفعها كما هو يعني إبطاء
 * إرسال الطلب على شبكة بطيئة، واستهلاك رصيد التخزين، وتحميل لوحة التحكم صوراً ثقيلة
 * وهي تعرضها في مربع صغير. لذلك تُصغَّر أطول ضلع إلى {@link #MAX_EDGE} بكسل وتُضغط
 * بجودة {@link #QUALITY}، وهما رقمان يُبقيان صورة الواجهة مقروءةً بعدة أمتار.
 */
public final class Images {

    /** أقصى طول للضلع الأكبر في الصورة بعد التصغير، بالبكسل */
    private static final int MAX_EDGE = 1280;

    /** جودة ضغط JPEG من مئة: تكفي للواجهة وتُبقي الحجم في حدود مئة كيلوبايت */
    private static final int QUALITY = 80;

    /**
     * أقصى طول للضلع الأكبر في نسخة العرض، بالبكسل.
     *
     * المربّعات في الشاشة لا تتجاوز مئة ديب، فترجمة الصورة كاملة كانت تملأ ذاكرة
     * الجهاز ست صوراً وهي تُعرض في حجم أصغر من أن تُفرَّق.
     */
    private static final int THUMBNAIL_EDGE = 320;

    /**
     * قراءة الصورة وتصغيرها واعتدال اتجاهها، أو null تعذّر القراءة.
     *
     * الاتجاه مهم هنا: الكاميرا تحفظ الصورة معتدلة في بيانات وصفية لا في البكسلات نفسها،
     * فلو تجاهلناها وصلت صورة المحل مائلة تسعين درجة.
     */
    @Nullable
    public static byte[] prepareForUpload(@NonNull Context context, @NonNull Uri uri) {
        Bitmap decoded = decodeSampled(context, uri, MAX_EDGE);
        if (decoded == null) {
            return null;
        }

        Bitmap upright = rotateIfNeeded(context, uri, decoded, MAX_EDGE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean compressed = upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, output);
        if (upright != decoded) {
            upright.recycle();
        }
        decoded.recycle();

        return compressed && output.size() > 0 ? output.toByteArray() : null;
    }

    /**
     * نسخة صغيرة للعرض على الشاشة فقط، أو null تعذّر القراءة.
     *
     * لا تُستخدم أبداً في الرفع: هذه صورة مبكّلة تُعرض في مربّع صغير، والرفع يحتاج
     * النسخة التي جهّزتها {@link #prepareForUpload}.
     */
    @Nullable
    public static Bitmap thumbnail(@NonNull Context context, @NonNull Uri uri) {
        Bitmap decoded = decodeSampled(context, uri, THUMBNAIL_EDGE);
        if (decoded == null) {
            return null;
        }
        return rotateIfNeeded(context, uri, decoded, THUMBNAIL_EDGE);
    }

    /**
     * فك الترميز على نصف الأبعاد المطلوبة فقط: قراءة البكسلات كاملةً لصورة كبيرة
     * تستهلك ذاكرة الجهاز قبل أن نصل إلى التصغير أصلاً.
     */
    @Nullable
    private static Bitmap decodeSampled(Context context, Uri uri, int maxEdge) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        if (!readBounds(context, uri, bounds)) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge);
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        } catch (Exception error) {
            return null;
        }
    }

    private static boolean readBounds(Context context, Uri uri, BitmapFactory.Options bounds) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return false;
            }
            BitmapFactory.decodeStream(input, null, bounds);
        } catch (Exception error) {
            return false;
        }
        return bounds.outWidth > 0 && bounds.outHeight > 0;
    }

    /** أكبر مضاعف لرقم اثنين يجعل الصورة الناتجة داخل الحد المطلوب */
    private static int sampleSize(int width, int height, int maxEdge) {
        int sample = 1;
        while (width / sample > maxEdge * 2 || height / sample > maxEdge * 2) {
            sample *= 2;
        }
        return sample;
    }

    /**
     * تعديل اتجاه الصورة ثم تصغيرها إلى الطول المطلوب.
     *
     * التصغير هنا دقيق بعد التصغير التقريبي في decodeSampled، لأن inSampleSize يقفز
     * بأضعاف اثنين فقط فقد تبقى الصورة أكبر من المطلوب بمرتين.
     */
    @Nullable
    private static Bitmap rotateIfNeeded(Context context, Uri uri, Bitmap source, int maxEdge) {
        int degrees = rotationDegrees(context, uri);
        int edge = Math.max(source.getWidth(), source.getHeight());
        float scale = edge > maxEdge ? (float) maxEdge / edge : 1f;

        if (degrees == 0 && scale == 1f) {
            return source;
        }

        Matrix matrix = new Matrix();
        if (scale != 1f) {
            matrix.postScale(scale, scale);
        }
        if (degrees != 0) {
            matrix.postRotate(degrees);
        }

        Bitmap result = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
        return result == source ? source : result;
    }

    /** درجات الدوران من بيانات الصورة الوصفية، وصفر لمن لا يحملها */
    private static int rotationDegrees(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return 0;
            }

            int orientation = new ExifInterface(input)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception error) {
            return 0;
        }
    }

    private Images() {
    }
}
