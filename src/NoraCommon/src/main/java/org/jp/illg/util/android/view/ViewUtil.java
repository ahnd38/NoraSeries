package org.jp.illg.util.android.view;

import android.text.InputFilter;
import android.view.View;
import android.widget.TextView;

import com.annimon.stream.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ViewUtil {

	public static <T extends View> void consumerWhileViewDisabled(T view, Consumer<T> consumer){
		if(view == null || consumer == null)
			throw new IllegalArgumentException("View and consumer must be set.");

		boolean enabled = view.isEnabled();

		view.setEnabled(false);

		consumer.accept(view);

		view.setEnabled(enabled);
	}

	public static boolean addTextViewInputFilter(TextView textView, InputFilter inputFilter){
		if(textView == null || inputFilter == null){return false;}

		InputFilter[] filters = textView.getFilters();
		int newFiltersLength = filters != null ? filters.length + 1 : 1;

		InputFilter[] newFilters = new InputFilter[newFiltersLength];

		if (newFiltersLength > 1)
			System.arraycopy(filters,0,newFilters,0,newFiltersLength - 1);

		newFilters[newFiltersLength - 1] = inputFilter;

		textView.setFilters(newFilters);

		return true;
	}
}
