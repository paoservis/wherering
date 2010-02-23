package seanfoy.wherering;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import seanfoy.Greenspun.Func1;
import android.content.ContentValues;
import android.content.Context;
import android.database.*;
import android.database.sqlite.*;

public class DBAdapter {
    public DBAdapter(Context ctx) {
        this.ctx = ctx;
    }
    
    public void open() throws SQLException {
        dbHelper = new DatabaseHelper(ctx);
        db = dbHelper.getWritableDatabase();
    }
    
    public void close() {
        dbHelper.close();
    }
    
    public void upsert(String table, ContentValues V) {
        db.replace(table, null, V);
    }
    
    public void delete(String table, String whereClause) {
        db.delete(table, whereClause, null);
    }
    
    public <T> T withDB(Func1<SQLiteDatabase, T> f) {
        return f.f(db);
    }
    
    public static <T> T withDBAdapter(Context ctx, Func1<DBAdapter, T> f) {
        DBAdapter a = new DBAdapter(ctx);
        try {
            a.open();
            return f.f(a);
        }
        finally {
            a.close();
        }
    }
    
    public <T> T withCursor(String table, String [] columns, String where, Func1<Cursor, T> f) {
        Cursor c =
            db.query(
                table,
                columns,
                where,
                null,
                null,
                null,
                null);
        try {
            c.moveToFirst();
            return f.f(c);
        }
        finally {
            c.close();
        }
    }
    
    private static void trimEnd(StringBuilder sb, String waste) {
        if (sb.length() < waste.length()) return;
        int tailStart = sb.length() - waste.length();
        int tailEnd = sb.length();
        CharSequence tail = sb.subSequence(tailStart, tailEnd);
        if (!tail.equals(waste)) return;
        sb.delete(tailStart, tailEnd);
    }
    
    public static String escapeSQLIdentifier(String id) {
        return id.replace("\"", "\"\"");
    }
    
    private static final SimpleDateFormat ISO8601ShortDateTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SZ");
    public static String escapeSQLLiteral(Object v) {
        if (v == null) throw new IllegalArgumentException();
        if (v instanceof CharSequence) {
            return String.format("'%s'", v.toString().replaceAll("'", "''"));
        }
        else if (v instanceof Number) {
            return v.toString();
        }
        else if (v instanceof Date) {
            return ISO8601ShortDateTime.format((Date)v);
        }
        return escapeSQLLiteral(v.toString());
    }
    
    public static <T extends Map<String, ?>> String makeWhereClause(T k) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> p : k.entrySet()) {
            sb.append(
                String.format(
                    "%s = %s and ",
                    escapeSQLIdentifier(p.getKey()),
                    escapeSQLLiteral(p.getValue())));
        }
        trimEnd(sb, " and ");
        return sb.toString();
    }
    
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context) {
            super(context, DB_NAME, null, DB_VER);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Place.ddl(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Place.teardownDDL(db);
            onCreate(db);
        }
    }
    
    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;
    private static final String DB_NAME = "WhereRing";
    private static final int DB_VER = 1;
    private Context ctx;
}
