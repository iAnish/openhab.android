/**
 * openHAB, the open Home Automation Bus.
 * Copyright (C) 2010-2012, openHAB.org <admin@openhab.org>
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with Eclipse (or a modified version of that library),
 * containing parts covered by the terms of the Eclipse Public License
 * (EPL), the licensors of this Program grant you additional permission
 * to convey the resulting work.
 */

package org.openhab.habdroid.ui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.openhab.habdroid.R;
import org.openhab.habdroid.model.OpenHABSitemap;
import org.openhab.habdroid.model.OpenHABWidget;
import org.openhab.habdroid.model.OpenHABWidgetDataSource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Toast;

/**
 * This class provides app activity which displays list of openHAB
 * widgets from sitemap page
 * 
 * @author Victor Belov
 *
 */

public class OpenHABWidgetListActivity extends ListActivity {
	// Logging TAG
	private static final String TAG = "OpenHABWidgetListActivity";
	// Datasource, providing list of openHAB widgets
	private OpenHABWidgetDataSource openHABWidgetDataSource;
	// List adapter for list view of openHAB widgets
	private OpenHABWidgetAdapter openHABWidgetAdapter;
	// Url of current sitemap page displayed
	private String displayPageUrl ="";
	// sitemap root url
	private String sitemapRootUrl = "";
	// async http client
	private AsyncHttpClient pageAsyncHttpClient;
	// Sitemap pages stack for digging in and getting back
	private ArrayList<String> pageUrlStack = new ArrayList<String>();
	// openHAB base url
	private String openHABBaseUrl = "http://demo.openhab.org:8080/";
	// List of widgets to display
	private ArrayList<OpenHABWidget> widgetList = new ArrayList<OpenHABWidget>();
	// Username/password for authentication
	private String openHABUsername;
	private String openHABPassword;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO: Make progress indicator active every time we load the page
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.openhabwidgetlist);
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		openHABUsername = settings.getString("default_openhab_username", null);
		openHABPassword = settings.getString("default_openhab_password", null);
		openHABWidgetDataSource = new OpenHABWidgetDataSource();
		openHABWidgetAdapter = new OpenHABWidgetAdapter(OpenHABWidgetListActivity.this,
				R.layout.openhabwidgetlist_genericitem, widgetList);
		getListView().setAdapter(openHABWidgetAdapter);
		openHABWidgetAdapter.setOpenHABUsername(openHABUsername);
		openHABWidgetAdapter.setOpenHABPassword(openHABPassword);
//		this.getActionBar().setDisplayHomeAsUpEnabled(true);
		this.getActionBar().setHomeButtonEnabled(true);
		// Check if we have openHAB page url in saved instance state?
		if (savedInstanceState != null) {
			displayPageUrl = savedInstanceState.getString("displayPageUrl");
			pageUrlStack = savedInstanceState.getStringArrayList("pageUrlStack");
			openHABBaseUrl = savedInstanceState.getString("openHABBaseUrl");
			sitemapRootUrl = savedInstanceState.getString("sitemapRootUrl");
		}
		// If yes, then just show it
		if (displayPageUrl.length() > 0) {
			Log.i(TAG, "displayPageUrl = " + displayPageUrl);
			showPage(displayPageUrl, false);
		// Else check if we got openHAB base url through launch intent?
		} else  {
			openHABBaseUrl = getIntent().getExtras().getString("baseURL");
			if (openHABBaseUrl != null) {
				openHABWidgetAdapter.setOpenHABBaseUrl(openHABBaseUrl);
				selectSitemap(openHABBaseUrl);
			} else {
				Log.i(TAG, "No base URL!");
			}
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
	  // Save UI state changes to the savedInstanceState.
	  // This bundle will be passed to onCreate if the process is
	  // killed and restarted.
	  savedInstanceState.putString("displayPageUrl", displayPageUrl);
	  savedInstanceState.putStringArrayList("pageUrlStack", pageUrlStack);
	  savedInstanceState.putString("openHABBaseUrl", openHABBaseUrl);
	  savedInstanceState.putString("sitemapRootUrl", sitemapRootUrl);
	  super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy() for " + this.displayPageUrl);
		if (pageAsyncHttpClient != null)
			pageAsyncHttpClient.cancelRequests(this, true);
		// release multicast lock for mDNS
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult " + String.valueOf(requestCode) + " " + String.valueOf(resultCode));
		if (resultCode == -1) {
			// Right now only PreferencesActivity returns -1
			// Restart app after preferences
			Log.i(TAG, "Restarting");
			// Get launch intent for application
			Intent restartIntent = getBaseContext().getPackageManager()
		             .getLaunchIntentForPackage( getBaseContext().getPackageName() );
			restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			// Finish current activity
			finish();
			// Start launch activity
			startActivity(restartIntent);
		}
	}

	/**
     * Loads data from sitemap page URL and passes it to processContent
     *
     * @param  pageUrl  an absolute base URL of openHAB sitemap page
     * @param  longPolling  enable long polling when loading page
     * @return      void
     */
	public void showPage(String pageUrl, boolean longPolling) {
		Log.i(TAG, "showPage for " + pageUrl + " longPolling = " + longPolling);
		// Cancel any existing http request to openHAB (typically ongoing long poll)
		if (pageAsyncHttpClient != null) {
			pageAsyncHttpClient.cancelRequests(this, true);
		}
		pageAsyncHttpClient = new AsyncHttpClient();
		// If authentication is needed
		pageAsyncHttpClient.setBasicAuthCredientidals(openHABUsername, openHABPassword);
		// If long-polling is needed
		if (longPolling) {
			// Add corresponding fields to header to make openHAB know we need long-polling
			pageAsyncHttpClient.addHeader("X-Atmosphere-Transport", "long-polling");
			pageAsyncHttpClient.addHeader("Accept", "application/xml");
			pageAsyncHttpClient.setTimeout(30000);
		}
		pageAsyncHttpClient.get(this, pageUrl, new AsyncHttpResponseHandler() {
			@Override
			public void onSuccess(String content) {
				processContent(content);
			}
			@Override
		     public void onFailure(Throwable e) {
				Log.i(TAG, "http request failed");
				if (e.getMessage() != null) {
					Log.e(TAG, e.getMessage());
					if (e.getMessage().equals("Unauthorized")) {
					Toast.makeText(getApplicationContext(), "Authentication failed",
							Toast.LENGTH_LONG).show();
					}
				}
				stopProgressIndicator();
		     }
		});
	}

	/**
     * Parse XML sitemap page and show it
     *
     * @param  content	XML as a text
     * @return      void
     */
	public void processContent(String content) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document;
			// TODO: fix crash with null content
			document = builder.parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
			Node rootNode = document.getFirstChild();
			openHABWidgetDataSource.setSourceNode(rootNode);
			widgetList.clear();
			for (OpenHABWidget w : openHABWidgetDataSource.getWidgets()) {
				widgetList.add(w);
			}
			openHABWidgetAdapter.notifyDataSetChanged();
			setTitle(openHABWidgetDataSource.getTitle());
			setProgressBarIndeterminateVisibility(false);
			getListView().setOnItemClickListener(new OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position,
						long id) {
					Log.i(TAG, "Widget clicked " + String.valueOf(position));
					OpenHABWidget openHABWidget = openHABWidgetAdapter.getItem(position);
					if (openHABWidget.hasLinkedPage()) {
						// Widget have a page linked to it
						// Put current page into the stack and go to linked one
						pageUrlStack.add(0, displayPageUrl);
						displayPageUrl = openHABWidget.getLinkedPage().getLink();
						showPage(openHABWidget.getLinkedPage().getLink(), false);
					}
				}
				
			});
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		showPage(displayPageUrl, true);
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.mainmenu_openhab_preferences:
            Intent myIntent = new Intent(this.getApplicationContext(), OpenHABPreferencesActivity.class);
            startActivityForResult(myIntent, 0);
    		return true;
    	case R.id.mainmenu_openhab_selectsitemap:
			SharedPreferences settings = 
			PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
			Editor preferencesEditor = settings.edit();
			preferencesEditor.putString("default_openhab_sitemap", "");
			preferencesEditor.commit();
    		selectSitemap(openHABBaseUrl);
        case android.R.id.home:
        	displayPageUrl = sitemapRootUrl;
        	// we are navigating to root page, so clear page stack to support regular 'back' behavior for root page
        	pageUrlStack.clear();
            showPage(sitemapRootUrl, false);
            Log.i(TAG, "Home selected - " + sitemapRootUrl);
            return true;
        default:
    		return super.onOptionsItemSelected(item);
    	}
    }

    /**
     * We run all openHAB browsing in a single activity, so we need to
     * intercept 'Back' key to get back to previous sitemap page.
     * If no pages in stack - simulate typical android app behaviour -
     * exit application.
     *
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	Log.i(TAG, "keyCode = " + keyCode);
    	if (keyCode == 4) {
    		Log.i(TAG, "This is 'back' key");
    		if (pageUrlStack.size() > 0) {
    			displayPageUrl = pageUrlStack.get(0);
    			pageUrlStack.remove(0);
    			showPage(displayPageUrl, false);
    		} else {
    			Log.i(TAG, "No more pages left in stack, exiting");
    			finish();
    		}
    		return true;
    	} else {
    		return super.onKeyDown(keyCode, event);
    	}
    }

    /**
     * Get sitemaps from openHAB, if user already configured preffered sitemap
     * just open it. If no preffered sitemap is configured - let user select one.
     *
     * @param  baseUrl  an absolute base URL of openHAB to open
     * @return      void
     */

	private void selectSitemap(String baseURL) {
		Log.i(TAG, "Trying to select sitemap for " + baseURL + "rest/sitemaps");
			Log.i(TAG, "No sitemap configured, asking user to select one");
	    	AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
			// If authentication is needed
	    	asyncHttpClient.setBasicAuthCredientidals(openHABUsername, openHABPassword);
	    	asyncHttpClient.get(baseURL + "rest/sitemaps", new AsyncHttpResponseHandler() {
				@Override
				public void onSuccess(String content) {
//					Log.i(TAG, content);
					final List<String> sitemapNameItems = new ArrayList<String>();
					final List<OpenHABSitemap> sitemapItems = new ArrayList<OpenHABSitemap>();
					DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
					DocumentBuilder builder;
					try {
						builder = factory.newDocumentBuilder();
						Document document;
						document = builder.parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
						NodeList sitemapNodes = document.getElementsByTagName("sitemap");
						if (sitemapNodes.getLength() > 0) {
							for (int i=0; i < sitemapNodes.getLength(); i++) {
								Node sitemapNode = sitemapNodes.item(i);
								OpenHABSitemap openhabSitemap = new OpenHABSitemap(sitemapNode);
								Log.i(TAG, "Sitemap: " + openhabSitemap.getName() + " " + openhabSitemap.getLink()
										+ " " + openhabSitemap.getHomepageLink());
								sitemapNameItems.add(openhabSitemap.getName());
								sitemapItems.add(openhabSitemap);
							}
							SharedPreferences settings = 
									PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
							String selectedSitemap = settings.getString("default_openhab_sitemap", "");
							if (selectedSitemap.length() > 0) {
								Log.i(TAG, "Opening configured sitemap - " + selectedSitemap);
								for (int i=0; i < sitemapNodes.getLength(); i++) {
									if (sitemapItems.get(i).getName().equals(selectedSitemap)) {
										displayPageUrl = sitemapItems.get(i).getHomepageLink();
										sitemapRootUrl = sitemapItems.get(i).getHomepageLink();
										showPage(displayPageUrl, false);
									}
								}
							} else {
								AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(OpenHABWidgetListActivity.this);
								dialogBuilder.setTitle("Select sitemap");
								dialogBuilder.setItems(sitemapNameItems.toArray(new CharSequence[sitemapNameItems.size()]),
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int item) {
												Log.i(TAG, "Selected sitemap " + sitemapNameItems.get(item));
												Log.i(TAG, "Opening " + sitemapItems.get(item).getHomepageLink());
												displayPageUrl = sitemapItems.get(item).getHomepageLink();
												sitemapRootUrl = sitemapItems.get(item).getHomepageLink();
												SharedPreferences settings = 
														PreferenceManager.getDefaultSharedPreferences(OpenHABWidgetListActivity.this);
												Editor preferencesEditor = settings.edit();
												preferencesEditor.putString("default_openhab_sitemap", sitemapItems.get(item).getName());
												preferencesEditor.commit();
												showPage(displayPageUrl, false);
										    	OpenHABWidgetListActivity.this.getListView().setSelection(0);
											}
								}).show();
							}
						} else {
							Toast.makeText(getApplicationContext(), "openHAB returned no sitemaps",
									Toast.LENGTH_LONG).show();
						}
					} catch (ParserConfigurationException e) {
						Log.e(TAG, e.getMessage());
					} catch (UnsupportedEncodingException e) {
						Log.e(TAG, e.getMessage());
					} catch (SAXException e) {
						Log.e(TAG, e.getMessage());
					} catch (IOException e) {
						Log.e(TAG, e.getMessage());
					}
			}
			@Override
		    public void onFailure(Throwable e) {
				Log.e(TAG, e.getMessage());
			}
    	});
	}

	private void stopProgressIndicator() {
		setProgressBarIndeterminateVisibility(false);
	}

}
