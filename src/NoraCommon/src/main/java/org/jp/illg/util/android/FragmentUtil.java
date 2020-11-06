package org.jp.illg.util.android;

import androidx.fragment.app.Fragment;

public class FragmentUtil {

	public static boolean isAliveFragment(Fragment fragment){
		if(fragment == null){return false;}

		return
				!fragment.isDetached() &&
				fragment.getActivity() != null &&
				fragment.isResumed();
	}
}
