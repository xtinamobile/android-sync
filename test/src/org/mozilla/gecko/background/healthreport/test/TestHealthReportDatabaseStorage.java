/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.background.healthreport.test;

import java.io.File;
import java.util.ArrayList;

import org.mozilla.gecko.background.healthreport.HealthReportStorage.Field;
import org.mozilla.gecko.background.healthreport.HealthReportStorage.MeasurementFields;
import org.mozilla.gecko.background.test.helpers.DBHelpers;
import org.mozilla.gecko.background.test.helpers.FakeProfileTestCase;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class TestHealthReportDatabaseStorage extends FakeProfileTestCase {
  @Override
  protected String getCacheSuffix() {
    return File.separator + "health-" + System.currentTimeMillis() + ".profile";
  }

  public static class MockMeasurementFields implements MeasurementFields {
    @Override
    public Iterable<FieldSpec> getFields() {
      ArrayList<FieldSpec> fields = new ArrayList<FieldSpec>();
      fields.add(new FieldSpec("testfield1", Field.TYPE_INTEGER_COUNTER));
      fields.add(new FieldSpec("testfield2", Field.TYPE_INTEGER_COUNTER));
      return fields;
    }
  }

  public void testInitializingProvider() {
    MockHealthReportDatabaseStorage storage = new MockHealthReportDatabaseStorage(context, fakeProfileDirectory);
    storage.beginInitialization();

    // Two providers with the same measurement and field names. Shouldn't conflict.
    storage.ensureMeasurementInitialized("testpA.testm", 1, new MockMeasurementFields());
    storage.ensureMeasurementInitialized("testpB.testm", 2, new MockMeasurementFields());
    storage.finishInitialization();

    // Now make sure our stuff is in the DB.
    SQLiteDatabase db = storage.getDB();
    Cursor c = db.query("measurements", new String[] {"id", "name", "version"}, null, null, null, null, "name");
    assertTrue(c.moveToFirst());
    assertEquals(2, c.getCount());

    Object[][] expected = new Object[][] {
        {null, "testpA.testm", 1},
        {null, "testpB.testm", 2},
    };

    DBHelpers.assertCursorContains(expected, c);
    c.close();
  }

  public void testEnvironmentsAndFields() {
    MockHealthReportDatabaseStorage storage = new MockHealthReportDatabaseStorage(context, fakeProfileDirectory);
    storage.beginInitialization();
    storage.ensureMeasurementInitialized("testpA.testm", 1, new MockMeasurementFields());
    storage.ensureMeasurementInitialized("testpB.testn", 1, new MockMeasurementFields());
    storage.finishInitialization();

    MockDatabaseEnvironment environmentA = storage.getEnvironment();
    environmentA.mockInit("v123");
    final int envA = environmentA.register();
    assertEquals(envA, environmentA.register());

    // getField memoizes.
    assertSame(storage.getField("foo", 2, "bar"),
               storage.getField("foo", 2, "bar"));

    // It throws if you refer to a non-existent field.
    try {
      storage.getField("foo", 2, "bar").getID();
      fail("Should throw.");
    } catch (IllegalStateException ex) {
      // Expected.
    }

    // It returns the field ID for a valid field.
    Field field = storage.getField("testpA.testm", 1, "testfield1");
    assertTrue(field.getID() >= 0);

    // These IDs are stable.
    assertEquals(field.getID(), field.getID());
    int fieldID = field.getID();

    // Store some data for two environments across two days.
    storage.incrementDailyCount(envA, storage.getYesterday(), fieldID, 4);
    storage.incrementDailyCount(envA, storage.getYesterday(), fieldID, 1);
    storage.incrementDailyCount(envA, storage.getToday(), fieldID, 2);

    MockDatabaseEnvironment environmentB = storage.getEnvironment();
    environmentB.mockInit("v234");
    final int envB = environmentB.register();
    assertFalse(envA == envB);

    storage.incrementDailyCount(envB, storage.getToday(), fieldID, 6);
    storage.incrementDailyCount(envB, storage.getToday(), fieldID, 2);

    // Let's make sure everything's there.
    Cursor c = storage.getRawEventsSince(storage.getOneDayAgo());
    try {
      assertTrue(c.moveToFirst());
      assertTrue(assertRowEquals(c, storage.getYesterday(), envA, fieldID, 5));
      assertTrue(assertRowEquals(c, storage.getToday(), envA, fieldID, 2));
      assertFalse(assertRowEquals(c, storage.getToday(), envB, fieldID, 8));
    } finally {
      c.close();
    }
  }

  /**
   * Asserts validity for a storage cursor. Returns whether there is another row to process.
   */
  private static boolean assertRowEquals(Cursor c, int day, int env, int field, int value) {
    assertEquals(day,   c.getInt(0));
    assertEquals(env,   c.getInt(1));
    assertEquals(field, c.getInt(2));
    assertEquals(value, c.getLong(3));
    return c.moveToNext();
  }
}
