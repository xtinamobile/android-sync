/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.gecko.sync.crypto.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import org.junit.Test;
import org.mozilla.apache.commons.codec.binary.Base64;
import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.crypto.CryptoException;
import org.mozilla.gecko.sync.crypto.KeyBundle;

public class TestKeyBundle {
  @Test
  public void testUsernameFromAccount() throws NoSuchAlgorithmException, UnsupportedEncodingException {
    assertEquals("xee7ffonluzpdp66l6xgpyh2v2w6ojkc", Utils.sha1Base32("foobar@baz.com"));
    assertEquals("xee7ffonluzpdp66l6xgpyh2v2w6ojkc", KeyBundle.usernameFromAccount("foobar@baz.com"));
    assertEquals("xee7ffonluzpdp66l6xgpyh2v2w6ojkc", KeyBundle.usernameFromAccount("FooBar@Baz.com"));
    assertEquals("xee7ffonluzpdp66l6xgpyh2v2w6ojkc", KeyBundle.usernameFromAccount("xee7ffonluzpdp66l6xgpyh2v2w6ojkc"));
    assertEquals("foobar",                           KeyBundle.usernameFromAccount("foobar"));
    assertEquals("foobar",                           KeyBundle.usernameFromAccount("FOOBAr"));
  }

  @Test
  public void testCreateKeyBundle() throws UnsupportedEncodingException, CryptoException {
    String username              = "smqvooxj664hmrkrv6bw4r4vkegjhkns";
    String friendlyBase32SyncKey = "gbh7teqqcgyzd65svjgibd7tqy";
    String base64EncryptionKey   = "069EnS3EtDK4y1tZ1AyKX+U7WEsWRp9b" +
                                   "RIKLdW/7aoE=";
    String base64HmacKey         = "LF2YCS1QCgSNCf0BCQvQ06SGH8jqJDi9" +
                                   "dKj0O+b0fwI=";

    KeyBundle keys = new KeyBundle(username, friendlyBase32SyncKey);
    assertArrayEquals(keys.getEncryptionKey(), Base64.decodeBase64(base64EncryptionKey.getBytes("UTF-8")));
    assertArrayEquals(keys.getHMACKey(), Base64.decodeBase64(base64HmacKey.getBytes("UTF-8")));
  }

  /*
   * Basic sanity check to make sure length of keys is correct (32 bytes).
   * Also make sure that the two keys are different.
   */
  @Test
  public void testGenerateRandomKeys() throws CryptoException {
    KeyBundle keys = KeyBundle.withRandomKeys();

    assertEquals(32, keys.getEncryptionKey().length);
    assertEquals(32, keys.getHMACKey().length);

    boolean equal = Arrays.equals(keys.getEncryptionKey(), keys.getHMACKey());
    assertEquals(false, equal);
  }
}
