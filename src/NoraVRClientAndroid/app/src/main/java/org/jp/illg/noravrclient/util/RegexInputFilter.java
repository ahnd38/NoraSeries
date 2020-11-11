package org.jp.illg.noravrclient.util;

import android.text.InputFilter;
import android.text.Spanned;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

public class RegexInputFilter implements InputFilter {
	
	private final Pattern regexPattern;
	
	public RegexInputFilter(@NonNull final String regexPattern) {
		super();
		
		this.regexPattern = Pattern.compile(regexPattern);
	}
	
	public CharSequence filter(
			CharSequence source, int start, int end, Spanned dest, int dstart, int dend
	) {
		
		if(regexPattern.matcher(source).matches()){
			return source;
		}
		else
			return "";
	}
}
