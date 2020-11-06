package org.jp.illg.util.android.view.filter;

import android.text.InputFilter;
import android.text.Spanned;

import org.jp.illg.dstar.util.CallSignValidator;

public class FullCallsignInputFilter implements InputFilter {

	private static final Object instanceLock = new Object();

	private static FullCallsignInputFilter instance = null;

	private FullCallsignInputFilter(){
		super();
	}

	public static FullCallsignInputFilter getInstance(){
		synchronized (instanceLock){
			if(instance != null)
				return instance;
			else
				return (instance = new FullCallsignInputFilter());
		}
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



			if(!CallSignValidator.isValidUserCallsign(resultingTxt)){return "";}
		}

		return null;
	}
}
