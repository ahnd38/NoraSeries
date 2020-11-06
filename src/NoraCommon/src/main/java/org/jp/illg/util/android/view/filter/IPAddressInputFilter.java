package org.jp.illg.util.android.view.filter;

import android.text.InputFilter;
import android.text.Spanned;

public class IPAddressInputFilter implements InputFilter {

	static IPAddressInputFilter instance;

	public static IPAddressInputFilter getInstance(){return instance;}

	static{
		instance = new IPAddressInputFilter();
	}

	private IPAddressInputFilter(){
		super();
	}

	@Override
	public CharSequence filter(
			CharSequence source, int start, int end,
			Spanned dest, int dstart, int dend
	) {
		if (end > start) {
			String destTxt = dest.toString();
			String resultingTxt = destTxt.substring(0, dstart)
					+ source.subSequence(start, end)
					+ destTxt.substring(dend);
			if (!resultingTxt
					.matches("^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?")) {
				return "";
			} else {
				String[] splits = resultingTxt.split("\\.");
				for (int i = 0; i < splits.length; i++) {
					if (Integer.valueOf(splits[i]) > 255) {
						return "";
					}
				}
			}
		}

		return null;
	}
}
