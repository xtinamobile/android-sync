/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test.integration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mozilla.android.sync.test.helpers.MockGlobalSessionCallback;
import org.mozilla.gecko.background.fxa.FxAccountUtils;
import org.mozilla.gecko.background.fxa.SkewHandler;
import org.mozilla.gecko.background.testhelpers.MockPrefsGlobalSession;
import org.mozilla.gecko.background.testhelpers.MockSharedPreferences;
import org.mozilla.gecko.background.testhelpers.WaitHelper;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.MockMyIDTokenFactory;
import org.mozilla.gecko.browserid.RSACryptoImplementation;
import org.mozilla.gecko.fxa.FxAccountConstants;
import org.mozilla.gecko.sync.CollectionKeys;
import org.mozilla.gecko.sync.CryptoRecord;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.GlobalSession;
import org.mozilla.gecko.sync.InfoCollections;
import org.mozilla.gecko.sync.MetaGlobal;
import org.mozilla.gecko.sync.SyncConfiguration;
import org.mozilla.gecko.sync.crypto.KeyBundle;
import org.mozilla.gecko.sync.delegates.FreshStartDelegate;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.net.HawkAuthHeaderProvider;
import org.mozilla.gecko.sync.repositories.domain.VersionConstants;
import org.mozilla.gecko.tokenserver.TokenServerClient;
import org.mozilla.gecko.tokenserver.TokenServerClientDelegate;
import org.mozilla.gecko.tokenserver.TokenServerException;
import org.mozilla.gecko.tokenserver.TokenServerToken;

import android.content.SharedPreferences;

@Category(IntegrationTestCategory.class)
public class TestFreshStart {
  // TODO: switch this to use a local server, with appropriate setup.
  static final String TEST_USERNAME     = "6gnkjphdltbntwnrgvu46ey6mu7ncjdl";
  static final String TEST_SYNC_KEY     = "fuyx96ea8rkfazvjdfuqumupye"; // Weave.Identity.syncKey

  private CollectionKeys keysToUpload;
  private MockGlobalSessionCallback callback;
  private GlobalSession session;
  private AuthHeaderProvider authHeaderProvider;
  private TokenServerToken token;

  protected TokenServerToken getToken() throws Exception {
    String TEST_MOCKMYID_USERNAME = "testtest";
    String TEST_TOKEN_SERVER_URL = FxAccountConstants.DEFAULT_TOKEN_SERVER_ENDPOINT;
    String TEST_AUDIENCE = FxAccountUtils.getAudienceForURL(TEST_TOKEN_SERVER_URL);
    BrowserIDKeyPair keyPair = RSACryptoImplementation.generateKeyPair(1024);
    final String assertion = new MockMyIDTokenFactory().createMockMyIDAssertion(keyPair, TEST_MOCKMYID_USERNAME, TEST_AUDIENCE);
    final TokenServerClient tokenServerClient = new TokenServerClient(new URI(TEST_TOKEN_SERVER_URL), Executors.newSingleThreadExecutor());
    final TokenServerToken[] tokens = new TokenServerToken[1];
    WaitHelper.getTestWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        tokenServerClient.getTokenFromBrowserIDAssertion(assertion, true, new TokenServerClientDelegate() {
          @Override
          public void handleSuccess(TokenServerToken token) {
            tokens[0] = token;
            WaitHelper.getTestWaiter().performNotify();
          }

          @Override
          public void handleFailure(TokenServerException e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }

          @Override
          public void handleError(Exception e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }
        });
      }
    });
    return tokens[0];
  }

  @Before
  public void setUp() throws Exception {
    if (token == null) {
      token = getToken();
    }
    final SkewHandler tokenServerSkewHandler = SkewHandler.getSkewHandlerFromEndpointString(token.endpoint);
    final long tokenServerSkew = tokenServerSkewHandler.getSkewInSeconds();
    authHeaderProvider = new HawkAuthHeaderProvider(token.id, token.key.getBytes("UTF-8"), false, tokenServerSkew);

    keysToUpload = CollectionKeys.generateCollectionKeys();
    keysToUpload.setKeyBundleForCollection("addons", KeyBundle.withRandomKeys());

    callback = new MockGlobalSessionCallback(null);
    final SharedPreferences prefs = new MockSharedPreferences();
    final SyncConfiguration config = new SyncConfiguration(TEST_USERNAME, authHeaderProvider, prefs);
    config.syncKeyBundle = new KeyBundle(TEST_USERNAME, TEST_SYNC_KEY);
    session = new MockPrefsGlobalSession(config, callback, null, null, null) {
      @Override
      public CollectionKeys generateNewCryptoKeys() {
        return keysToUpload;
      }

      // On fresh start completed, just stop.
      @Override
      public void freshStart() {
        freshStart(this, new FreshStartDelegate() {
          @Override
          public void onFreshStartFailed(Exception e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }

          @Override
          public void onFreshStart() {
            WaitHelper.getTestWaiter().performNotify();
          }
        });
      }
    };
    session.config.clusterURL = new URI(token.endpoint);
  }

  protected void doFreshStart() {
    WaitHelper.getTestWaiter().performWait(WaitHelper.onThreadRunnable(new Runnable() {
      @Override
      public void run() {
        try {
          session.freshStart();
        } catch (Exception e) {
          WaitHelper.getTestWaiter().performNotify(e);
        }
      }
    }));
  }

  @Test
  public void testLiveFreshStart() throws Exception {
    session.config.enabledEngineNames = new HashSet<String>();
    session.config.enabledEngineNames.add("bookmarks");
    session.config.enabledEngineNames.add("clients");
    session.config.enabledEngineNames.add("addons");
    session.config.enabledEngineNames.add("prefs");
    session.config.userSelectedEngines = null;

    doFreshStart();

    // Verify that meta and crypto are the only entries in info/collections.
    ExtendedJSONObject o = TestBasicFetch.realLiveFetch(authHeaderProvider, session.config.infoCollectionsURL()).jsonObject();
    InfoCollections infoCollections = new InfoCollections(o);
    assertNotNull(infoCollections.getTimestamp("meta"));
    assertNotNull(infoCollections.getTimestamp("crypto"));
    assertEquals(2, o.object.entrySet().size());

    // Verify that meta/global looks okay.
    o = TestBasicFetch.realLiveFetch(authHeaderProvider, session.config.metaURL()).jsonObject();
    assertNotNull(o);
    MetaGlobal mg = new MetaGlobal(null, null);
    mg.setFromRecord(CryptoRecord.fromJSONRecord(o));
    assertEquals(Long.valueOf(GlobalSession.STORAGE_VERSION), mg.getStorageVersion());
    List<String> namesList = new ArrayList<String>(mg.getEnabledEngineNames());
    Collections.sort(namesList);
    String[] names = namesList.toArray(new String[namesList.size()]);
    String[] expected = new String[] { "addons", "bookmarks", "clients", "prefs" };
    assertArrayEquals(expected, names);
    assertEquals(VersionConstants.BOOKMARKS_ENGINE_VERSION, mg.getEngines().getObject("bookmarks").getIntegerSafely("version").intValue());
    assertEquals(VersionConstants.CLIENTS_ENGINE_VERSION, mg.getEngines().getObject("clients").getIntegerSafely("version").intValue());
    assertEquals(0, mg.getEngines().getObject("addons").getIntegerSafely("version").intValue());
    assertEquals(0, mg.getEngines().getObject("prefs").getIntegerSafely("version").intValue());

    // Verify that crypto/keys looks okay.
    String jsonCryptoKeys = TestBasicFetch.realLiveFetch(authHeaderProvider, session.config.keysURI().toString()).body();
    CollectionKeys keys = new CollectionKeys();
    keys.setKeyPairsFromWBO(CryptoRecord.fromJSONRecord(jsonCryptoKeys), session.config.syncKeyBundle);
    assertTrue(keys.equals(keysToUpload));
    assertTrue(keys.keyBundleForCollectionIsNotDefault("addons"));
    assertFalse(keys.keyBundleForCollectionIsNotDefault("bookmarks"));
  }
}
