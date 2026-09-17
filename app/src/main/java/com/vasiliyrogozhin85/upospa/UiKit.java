package com.vasiliyrogozhin85.upospa;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

final class UiKit {
    static final int BG = Color.rgb(5, 18, 32);
    static final int SURF = Color.rgb(10, 35, 55);
    static final int SURF2 = Color.rgb(16, 47, 71);
    static final int STROKE = Color.rgb(31, 78, 108);
    static final int HOST = Color.rgb(25, 139, 255);
    static final int CLIENT = Color.rgb(10, 191, 94);
    static final int WARN = Color.rgb(255, 183, 77);
    static final int ERROR = Color.rgb(255, 88, 88);
    static final int TEXT = Color.rgb(242, 248, 255);
    static final int MUTED = Color.rgb(158, 184, 207);

    static void screen(Activity a) {
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) a.getWindow().getDecorView().setSystemUiVisibility(0);
    }

    static int dp(Activity a, int x) {
        return (int) (x * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView tv(Activity a, String s, int size, int color, boolean bold) {
        TextView v = new TextView(a);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setLineSpacing(0, 1.06f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    static TextView header(Activity a, String title, int accent) {
        TextView v = tv(a, title, 25, TEXT, true);
        v.setCompoundDrawablePadding(dp(a, 8));
        return v;
    }

    static TextView section(Activity a, String s) {
        TextView v = tv(a, s, 17, TEXT, true);
        v.setPadding(0, dp(a, 18), 0, dp(a, 7));
        return v;
    }

    static LinearLayout card(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        l.setBackground(bg(a, SURF, 12, STROKE, 1));
        return l;
    }

    static View card(Activity a, View v) {
        LinearLayout l = card(a);
        l.addView(v);
        return l;
    }

    static TextView chip(Activity a, String text, int color) {
        TextView v = tv(a, text, 12, TEXT, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(a,10), dp(a,5), dp(a,10), dp(a,5));
        v.setBackground(bg(a, darken(color), 999, color, 1));
        return v;
    }

    static Button button(Activity a, String s, int color, View.OnClickListener listener) {
        Button b = new Button(a);
        b.setText(s);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(a,14), 0, dp(a,14), 0);
        b.setMinHeight(dp(a,50));
        b.setBackground(bg(a, color, 10, lighten(color), 1));
        b.setOnClickListener(listener);
        b.setLayoutParams(top(a, 7));
        return b;
    }

    static Button darkButton(Activity a, String s, View.OnClickListener listener) {
        return button(a, s, SURF2, listener);
    }

    static LinearLayout.LayoutParams top(Activity a, int n) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(a,n);
        return p;
    }

    static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, -2, w);
    }

    static GradientDrawable bg(Activity a, int fill, int radiusDp, int stroke, int widthDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(a, radiusDp));
        if (widthDp > 0) g.setStroke(dp(a,widthDp), stroke);
        return g;
    }

    static int lighten(int c) {
        return Color.rgb(Math.min(255, Color.red(c)+28),
                Math.min(255, Color.green(c)+28),
                Math.min(255, Color.blue(c)+28));
    }

    static int darken(int c) {
        return Color.rgb(Math.max(0, Color.red(c)/3),
                Math.max(0, Color.green(c)/3),
                Math.max(0, Color.blue(c)/3));
    }

    static String yesNo(boolean b) { return b ? "да" : "нет"; }

    private UiKit() {}
}
