package com.taqat.madar.ui.onboarding;

/**
 * بيانات شريحة واحدة من شرائح الجولة التعريفية.
 */
public class OnboardingItem {

    private final int imageRes;
    private final String title;
    private final String description;

    public OnboardingItem(int imageRes, String title, String description) {
        this.imageRes = imageRes;
        this.title = title;
        this.description = description;
    }

    public int getImageRes() {
        return imageRes;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
