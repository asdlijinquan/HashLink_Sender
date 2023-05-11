package com.example.hashlink_sender;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "my_database";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_NAME = "my_table";
    private static final String COLUMN_NAME = "hash_chain";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_NAME + " BLOB" +
                ");";
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade database here
    }

    public void insertHashChain(byte[][] hashChain) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        for (int i = 0; i < hashChain.length; i++) {
            values.put(COLUMN_NAME, hashChain[i]);
            db.insert(TABLE_NAME, null, values);
        }
        db.close();
    }
}
