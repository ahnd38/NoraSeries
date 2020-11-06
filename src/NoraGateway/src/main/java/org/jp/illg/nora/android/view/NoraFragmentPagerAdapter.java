package org.jp.illg.nora.android.view;


import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.greenrobot.eventbus.EventBus;
import org.jp.illg.util.android.view.DynamicFragmentStatePagerAdapter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraFragmentPagerAdapter extends DynamicFragmentStatePagerAdapter {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Context context;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private EventBus eventBus;

	private class PageInfo{
		@Getter
		@Setter
		private Class<? extends Fragment> fragmentClass;

		@Getter
		@Setter
		private String pageTitle;

		private PageInfo(Class<? extends Fragment> fragmentClass, String pageTitle){
			super();

			setFragmentClass(fragmentClass);
			setPageTitle(pageTitle);
		}
	}

	private List<PageInfo> pages;

	public NoraFragmentPagerAdapter(FragmentManager fm, Context context, EventBus eventBus){
		super(fm);

		pages = new ArrayList<>();

		setContext(context);
		setEventBus(eventBus);
	}

	public boolean addPage(Class<? extends Fragment> pageFragmentClass, String pageTitle){
		if(pageFragmentClass == null){return false;}

		if(pageTitle == null){pageTitle = "";}

		pages.add(new PageInfo(pageFragmentClass, pageTitle));

		return true;
	}

	public boolean removePage(int pagePosition){
		if(pages.size() <= pagePosition){return false;}

		removeItem(pagePosition);

		return true;
	}

	@Override
	public Fragment getItem(int position) {
		if(pages.size() <= position){return null;}

		Fragment fragment = null;

		Class<? extends Fragment> fragmentClass = pages.get(position).getFragmentClass();

		Method getInstanceMethod = null;
		try {
			getInstanceMethod =
					fragmentClass.getMethod("getInstance", EventBus.class);
		}catch(NoSuchMethodException ex){
			log.warn("Please implement getInstance(Context, EventBus).", ex);
			return null;
		}

		try {
			fragment =
					(Fragment) getInstanceMethod.invoke(null, getEventBus());
		}catch(Exception ex){
			log.warn("Could not invoke getInstance method.", ex);
			return null;
		}

		return fragment;
	}

	@Override
	public int getCount() {
		return pages.size();
	}

	@Override
	public CharSequence getPageTitle(int position) {
		if(pages.size() <= position){return "";}

		return pages.get(position).getPageTitle();
	}

	@Override
	public void removeItem(int position){
		if(pages.size() > position){pages.remove(position);}
		super.removeItem(position);
	}
}
