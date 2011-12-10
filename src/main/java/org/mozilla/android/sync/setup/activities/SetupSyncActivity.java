/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is Android Sync Client.
 *
 * The Initial Developer of the Original Code is
 * the Mozilla Foundation.
 * Portions created by the Initial Developer are Copyright (C) 2011
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *  Chenxia Liu <liuche@mozilla.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.mozilla.android.sync.setup.activities;


import org.json.simple.JSONObject;
import org.mozilla.android.sync.R;
import org.mozilla.android.sync.setup.Constants;
import org.mozilla.android.sync.setup.jpake.JpakeClient;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class SetupSyncActivity extends Activity {
  private final static String TAG = "SetupSync";

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.setup);
  }

  @Override
  public void onResume() {
    super.onResume();


    // Check whether Sync accounts exist; if so, display Pair text
    AccountManager mAccountManager = AccountManager.get(this);
    Account[] accts = mAccountManager.getAccountsByType(Constants.ACCOUNTTYPE_SYNC);
    Log.d(TAG, "number: " + accts.length);
    if (accts.length > 0) {
      ((TextView) findViewById(R.id.setup_title)).setText(getString(R.string.title_pair));
      ((TextView) findViewById(R.id.setup_subtitle)).setText(getString(R.string.subtitle_pair));
      ((TextView) findViewById(R.id.link_nodevice)).setVisibility(View.INVISIBLE);
    }
    // Start J-PAKE
    JpakeClient jClient = new JpakeClient(this);
    jClient.receiveNoPin();
  }

  /* Click Handlers */
  public void manualClickHandler(View target) {
    Intent accountIntent = new Intent(this, AccountActivity.class);
    accountIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    startActivity(accountIntent);
    overridePendingTransition(0, 0);
  }
  public void cancelClickHandler(View target) {
    finish();
  }

  // Controller methods
  public void displayPin(String pin) {
    // format PIN
    int charPerLine = pin.length()/3;
    String prettyPin = pin.substring(0, charPerLine) + "\n";
    prettyPin += pin.substring(charPerLine, 2*charPerLine) + "\n";
    prettyPin += pin.substring(2*charPerLine, pin.length());
    ((TextView) findViewById(R.id.text_pin)).setText(prettyPin);
  }

  public void displayAbort(String error) {
    // TODO: display abort error or something
  }

  public void onPaired() {
    // TODO Auto-generated method stub

  }

  public void onComplete(JSONObject newData) {
    // TODO Auto-generated method stub

  }

  public void onPairingStart() {
    // TODO Auto-generated method stub

  }
}
