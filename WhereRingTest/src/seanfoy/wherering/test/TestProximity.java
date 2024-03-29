/*
 * Copyright 2010 Sean M. Foy
 * 
 * This file is part of WhereRing.
 *
 *  WhereRing is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  WhereRing is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with WhereRing.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package seanfoy.wherering.test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.location.LocationManager;
import android.media.AudioManager;
import android.test.ServiceTestCase;
import android.util.Log;
import seanfoy.Greenspun;
import seanfoy.wherering.*;
import seanfoy.wherering.Place.RingerMode;

public class TestProximity extends ServiceTestCase<WRService> {
    public void testStartEmpty() {
        final CounterContext ctx = new CounterContext(getContext().getApplicationContext());
        setContext(ctx);
        assertEquals(0, ctx.count(Context.ALARM_SERVICE));
        startService(new Intent(ctx, WRService.class));
        assertTrue(0 < ctx.count(Context.ALARM_SERVICE));
    }

    //TODO: sadly, the test provider class
    // (MockProvider) doesn't support GpsStatus
    // listening. Consider implementing that
    // and submitting a patch.
    // public void testNoGPSAfterStop() {}
    
    public void testEntry() {
        Context ctx = getContext();
        
        Place dlc = LocationHelpers.makePlaceDeLaConcorde();
        dlc.ringerMode = Place.RingerMode.normal;
        Place newbury = LocationHelpers.makeNewbury();
        newbury.ringerMode = Place.RingerMode.vibrate;

        newbury.upsert(db);
        establish(ctx, dlc);
        startAndSettle(ctx);
        LocationHelpers.teleport(ctx, getLM(ctx), newbury);
        waitForRinger(limit, ctx, newbury);
    }
    
    public void testExit() {
        Context ctx = getContext();
        Place dlc = LocationHelpers.makePlaceDeLaConcorde();
        dlc.ringerMode = Place.RingerMode.normal;
        Place newbury = LocationHelpers.makeNewbury();
        newbury.ringerMode = Place.RingerMode.vibrate;
        
        dlc.upsert(db);
        establish(ctx, newbury);
        startAndSettle(ctx);
        
        LocationManager lm = getLM(ctx);
        LocationHelpers.teleport(ctx, lm, dlc);
        waitForRinger(limit, ctx, dlc);
        LocationHelpers.teleport(ctx, lm, newbury);
        waitForRinger(limit, ctx, newbury);
    }
    
    public void testPlaceToPlace() {
        Context ctx = getContext();
        Place dlc = LocationHelpers.makePlaceDeLaConcorde();
        dlc.ringerMode = Place.RingerMode.normal;
        Place newbury = LocationHelpers.makeNewbury();
        newbury.ringerMode = Place.RingerMode.vibrate;
        
        dlc.upsert(db);
        newbury.upsert(db);
        establish(ctx, dlc);
        startAndSettle(ctx);
        waitForRinger(limit, ctx, dlc);
        
        LocationManager lm = getLM(ctx);
        LocationHelpers.teleport(ctx, lm, newbury);
        waitForRinger(limit, ctx, newbury);
    }
    
    public void testPlaceToPlaceRestoration() {
        Context ctx = getContext();
        Place dlc = LocationHelpers.makePlaceDeLaConcorde();
        dlc.ringerMode = Place.RingerMode.normal;
        Place newbury = LocationHelpers.makeNewbury();
        newbury.ringerMode = Place.RingerMode.vibrate;
        Place google = LocationHelpers.makeGoogleplex();
        google.ringerMode = Place.RingerMode.silent;
        
        dlc.upsert(db);
        newbury.upsert(db);
        establish(ctx, google);
        startAndSettle(ctx);
        
        // Look out for nondeterminism in
        // the order of entry/exit events
        // when we transition directly from
        // one place to the next. Assuming
        // the two orderings are equally
        // likely, this test has a 2^-n
        // chance of passing in the presence
        // of a bug.
        final int n = 16;
        for (int i = 0; i < n; ++i) {
            android.util.Log.i("here I am", "i = " + i);
            LocationManager lm = getLM(ctx);
            if (i % 2 == 0) {
                LocationHelpers.teleport(ctx, lm, newbury);
                waitForRinger(limit, ctx, newbury);
                LocationHelpers.teleport(ctx, lm, dlc);
                waitForRinger(limit, ctx, dlc);
            }
            else {
                LocationHelpers.teleport(ctx, lm, dlc);
                waitForRinger(limit, ctx, dlc);
                LocationHelpers.teleport(ctx, lm, newbury);
                waitForRinger(limit, ctx, newbury);                
            }
            LocationHelpers.teleport(ctx, lm, google);
            waitForRinger(limit, ctx, google);
        }
    }
    
    public void testUserOverride() {
        Place googleplex = LocationHelpers.makeGoogleplex();
        googleplex.ringerMode = RingerMode.normal;
        Place newbury = LocationHelpers.makeNewbury();
        newbury.ringerMode = RingerMode.vibrate;
        RingerMode override = RingerMode.silent;
        googleplex.upsert(db);
        Context ctx = getContext();
        establish(ctx, newbury);
        LocationManager lm = getLM(ctx);
        
        startAndSettle(ctx);
        LocationHelpers.teleport(ctx, lm, googleplex);
        long delay = -System.nanoTime();
        waitForRinger(limit, ctx, googleplex);
        delay += System.nanoTime();
        AudioManager am =
            (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(override.ringer_mode);
        LocationHelpers.teleport(ctx, lm, newbury);
        Greenspun.sleep(4 * delay / 1000);
        assertEquals(
                "WhereRing should not restore previous ring states when the user has adjusted the ring state while in a place.",
                override.ringer_mode,
                am.getRingerMode());
        
        // WhereRing should set the ringer on place
        // entry even after user override in a previous
        // place.
        LocationHelpers.teleport(ctx, lm, googleplex);
        waitForRinger(limit, ctx, googleplex);
    }

    private void startAndSettle(Context ctx) {
        Intent wrsi = new Intent(ctx, WRService.class);
        startService(wrsi);
        while (!getService().isSubscribed()) {
            Greenspun.sleep(10);
        }
        String tag = getClass().getName();
        Log.i(
            tag,
            "service started and subscribed for alarms");
        // give the LocationManager time to setup
        // its onLocationChanged subscriptions
        Greenspun.sleep((long)(limit * 2));
        Log.i(
            tag,
            "service subscribed for proximities. Probably.");
    }

    private void waitForRinger(double limit, Context ctx, Place place) {
        AudioManager am = (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
        double deadline = System.currentTimeMillis() + limit;
        while (
                am.getRingerMode() != place.ringerMode.ringer_mode &&
                System.currentTimeMillis() < deadline) {
            Greenspun.sleep(16);
        }
        if (System.currentTimeMillis() > deadline) Log.i("waitForRinger", "deadline passed");
        assertEquals(place.ringerMode.ringer_mode, am.getRingerMode());
    }
    
    private void establish(Context ctx, Place p) {
        AudioManager am =
            (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(p.ringerMode.ringer_mode);
        LocationHelpers.teleport(ctx, getLM(ctx), p);
    }
    
    private LocationManager getLM(Context ctx) {
        return (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        db = new DBAdapter(getContext());
        db.open();
        db.delete(Place.TABLE_NAME, "");
        LocationHelpers.setupTestProvider(getLM(getContext()));
    }
    @Override
    public void tearDown() throws Exception {
        db.close();
        LocationHelpers.teardownTestProvider(getLM(getContext()));
        Context ctx = getContext();
        ctx.stopService(new Intent(ctx, WRService.class));
        WRService s = getService();
        while (s != null && s.isSubscribed()) {
            Greenspun.sleep(10);
        }
        super.tearDown();
    }
    
    double limit = 4096; //ms

    class CounterContext extends ContextWrapper {
        @Override
        public Context getApplicationContext() {
            Context appContext = super.getApplicationContext();
            if (appContext == getBaseContext()) return this;
            return new CounterContext(super.getApplicationContext());
        }

        @Override
        public Object getSystemService(String name) {
            systemServiceCounter.put(
                name,
                1 + seanfoy.Greenspun.setDefault(
                        systemServiceCounter,
                        name,
                        0));
            return super.getSystemService(name);
        }
        public Map<String, Integer> systemServiceCounter =
            new HashMap<String, Integer>();
        public int count(String name) {
            Integer result = systemServiceCounter.get(name);
            if (result == null) return 0;
            return result;
        }
        
        public CounterContext(Context base) {
            super(base);
        }        
    }
    
    public TestProximity() {
        super(WRService.class);
    }
    
    private DBAdapter db;
    
    /**
     * Start the service under test, in the same way as if it was started by
     * {@link android.content.Context#startService Context.startService()}, providing the 
     * arguments it supplied.  If you use this method to start the service, it will automatically
     * be stopped by {@link #tearDown}.
     *  
     * @param intent The Intent as if supplied to {@link android.content.Context#startService}.
     */
    protected void startService(Intent intent) {
        //TODO: report the bug that super.startService
        // calls onStart rather than onStartCommand.
        // onStart is deprecated and onStartCommand
        // is documented to call onStart before returning
        // a stickiness constant from Service.
        // Note: ApplicationContext.startService uses
        // ActivityManagerNative to talk to
        // ActivityManagerService to scheduleServiceArgs
        // on the ActivityThread, which is handled by
        // invoking... onStartCommand.
        assertFalse(mServiceStarted());
        assertFalse(mServiceBound());
        
        if (!mServiceAttached()) {
            setupService();
        }
        assertNotNull(mService());
        
        if (!mServiceCreated()) {
            mService().onCreate();
            set_mServiceCreated(true);
        }
        mService().onStartCommand(intent, serviceFlag, mServiceId());
        
        set_mServiceStarted(true);
    }
    private int serviceFlag = 0;
    private <T> T privateGetter(String fieldname) {
        try {
            Field f = ServiceTestCase.class.getDeclaredField(fieldname);
            f.setAccessible(true);
            return (T)f.get(this);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    private Boolean mServiceStarted() {
        return privateGetter("mServiceStarted");
    }
    private Boolean mServiceBound() {
        return privateGetter("mServiceBound");
    }
    private Boolean mServiceAttached() {
        return privateGetter("mServiceAttached");
    }
    private Service mService() {
        return privateGetter("mService");
    }
    private Boolean mServiceCreated() {
        return privateGetter("mServiceCreated");
    }
    private <T> void privateSetter(String fieldname, T v) {
        try {
            Field f = ServiceTestCase.class.getDeclaredField(fieldname);
            f.setAccessible(true);
            f.set(this, v);
        }
        catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    private void set_mServiceCreated(boolean v) {
        privateSetter("mServiceCreated", v);
    }
    private Integer mServiceId() {
        return privateGetter("mServiceId");
    }
    private void set_mServiceStarted(boolean mServiceStarted) {
        privateSetter("mServiceStarted", mServiceStarted);
    }
}
