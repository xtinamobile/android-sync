/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.sync.config.activities;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mozilla.gecko.R;
import org.mozilla.gecko.sync.Logger;
import org.mozilla.gecko.sync.SyncConfiguration;
import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.setup.Constants;
import org.mozilla.gecko.sync.setup.SyncAccounts.SyncAccountParameters;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.widget.ListView;
import android.widget.Toast;

/**
 * Configure which engines to Sync.
 */
public class ConfigureEnginesActivity extends AndroidSyncConfigureActivity
    implements DialogInterface.OnClickListener, DialogInterface.OnMultiChoiceClickListener {
  public final static String LOG_TAG = "ConfigureEnginesAct";
  private ListView selectionsList;

  protected interface AccountConsumer {
    public void run(SyncAccountParameters syncParams, SharedPreferences prefs);
  }

  protected String[]  _options = new String[] { "Bookmarks", "History", "Passwords", "Tabs" };
  protected boolean[] _selections = new boolean[_options.length];
  private final boolean[] _origSelections = new boolean[_options.length];

  @Override
  public void onResume() {
    super.onResume();
    final ConfigureEnginesActivity self = this;
    // Display engine configure UI.
    fetchPrefsAndConsume(new PrefsConsumer() {

      @Override
      public void run(SharedPreferences prefs) {
        // Extract enabled-engine state.
        Set<String> enabledEngineNames = getEnabledEngines(prefs);
        if (enabledEngineNames == null) {
          enabledEngineNames = new HashSet<String>();
        }
        setSelections(enabledEngineNames);

        new AlertDialog.Builder(self)
            .setTitle(R.string.sync_configure_engines_sync_my_title)
            .setMultiChoiceItems(_options, _selections, self)
            .setIcon(R.drawable.icon)
            .setPositiveButton(android.R.string.ok, self)
            .setNegativeButton(android.R.string.cancel, self).show();
      }
    });
  }

  private Set<String> getEnabledEngines(SharedPreferences syncPrefs) {
    Set<String> engines;
    // Check engine SharedPrefs first.
    SharedPreferences engineConfigPrefs = mContext.getSharedPreferences(Constants.PREFS_ENGINE_SELECTION, Utils.SHARED_PREFERENCES_MODE);
    @SuppressWarnings("unchecked")
    // We only add booleans to this SharedPreference.
    Map<String, Boolean> engineMap = (Map<String, Boolean>) engineConfigPrefs.getAll();
    if (!engineMap.isEmpty()) {
      engines = new HashSet<String>();
      for (Entry<String, Boolean> pair : engineMap.entrySet()) {
        if (pair.getValue()) {
          engines.add(pair.getKey());
        }
      }
      return engines;
    }
    Logger.warn(LOG_TAG, "No previous engine prefs");
    // No previously stored engine prefs.
    return SyncConfiguration.getEnabledEngineNames(syncPrefs);
  }

  // Hardcoded engines.
  public void setSelections(Set<String> enabled) {
    _selections[0] = enabled.contains("bookmarks");
    // Omit Forms, because there is no way to enable/disable Forms in desktop UI.
    _selections[1] = enabled.contains("history");
    _selections[2] = enabled.contains("passwords");
    _selections[3] = enabled.contains("tabs");

    // Cache original selections for comparing changes.
    System.arraycopy(_selections, 0, _origSelections, 0, _selections.length);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    if (which == DialogInterface.BUTTON_POSITIVE && saveEngines()) {
      setResult(RESULT_OK);
      Toast.makeText(this, R.string.sync_notification_savedprefs, Toast.LENGTH_SHORT).show();
    } else {
      setResult(RESULT_CANCELED);
    }
    finish();
  }

  @Override
  public void onClick(DialogInterface dialog, int which, boolean isChecked) {
    // Display multi-selection clicks in UI.
    _selections[which] = isChecked;
    if (selectionsList == null) {
      selectionsList = ((AlertDialog) dialog).getListView();
    }
    selectionsList.setItemChecked(which, isChecked);
  }

  /**
   * Persists enabled engines to SharedPreferences if changed.
   * @return true if changed, false otherwise.
   */
  private boolean saveEngines() {
    if (_selections.equals(_origSelections)) {
      return false;
    }
    // Persist to SharedPreferences.
    SharedPreferences prefs = mContext.getSharedPreferences(Constants.PREFS_ENGINE_SELECTION, Utils.SHARED_PREFERENCES_MODE);
    Editor enginePrefsEditor = prefs.edit();
    for (int i = 0; i < _selections.length; i++) {
      if (_selections[i] != _origSelections[i]) {
        enginePrefsEditor.putBoolean(_options[i].toLowerCase(), _selections[i]);
      }
    }
    enginePrefsEditor.commit();
    return true;
  }
}