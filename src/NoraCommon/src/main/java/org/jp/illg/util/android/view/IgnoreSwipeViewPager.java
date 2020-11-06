package org.jp.illg.util.android.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.viewpager.widget.ViewPager;

public class IgnoreSwipeViewPager extends ViewPager {

	public IgnoreSwipeViewPager(Context context) {
		super(context);
	}

	public IgnoreSwipeViewPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean canScrollHorizontally(int direction) {
		return false;
	}
}
