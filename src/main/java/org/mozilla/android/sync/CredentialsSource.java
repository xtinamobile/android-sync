package org.mozilla.android.sync;

import org.mozilla.android.sync.crypto.KeyBundle;

public interface CredentialsSource {

  public abstract String credentials();
  public abstract CollectionKeys getCollectionKeys();
  public abstract KeyBundle keyForCollection(String collection) throws NoCollectionKeysSetException;
}