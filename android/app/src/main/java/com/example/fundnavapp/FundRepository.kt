package com.example.fundnavapp

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FundRepository(context: Context) {
    private val dbHelper = FundDatabaseHelper(context)

    fun addFund(fund: Fund) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(FundDatabaseHelper.COLUMN_FUND_CODE, fund.fundCode)
            put(FundDatabaseHelper.COLUMN_FUND_NAME, fund.fundName)
            put(FundDatabaseHelper.COLUMN_CURRENT_AMOUNT, fund.currentAmount)
            put(FundDatabaseHelper.COLUMN_CURRENT_HOLDING_PROFIT, fund.currentHoldingProfit)
            put(FundDatabaseHelper.COLUMN_DAILY_CHANGE, fund.dailyChange)
            put(FundDatabaseHelper.COLUMN_DAILY_PROFIT, fund.dailyProfit)
        }
        db.insertWithOnConflict(
            FundDatabaseHelper.TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
        db.close()
    }

    fun deleteFund(fundCode: String) {
        val db = dbHelper.writableDatabase
        db.delete(FundDatabaseHelper.TABLE_NAME, "${FundDatabaseHelper.COLUMN_FUND_CODE} = ?", arrayOf(fundCode))
        db.close()
    }

    fun getAllFunds(): List<Fund> {
        val funds = mutableListOf<Fund>()
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            FundDatabaseHelper.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null
        )

        while (cursor.moveToNext()) {
            val fundCode = cursor.getString(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_FUND_CODE))
            val fundName = cursor.getString(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_FUND_NAME))
            val currentAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_CURRENT_AMOUNT))
            val currentHoldingProfit = cursor.getDouble(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_CURRENT_HOLDING_PROFIT))
            val dailyChange = cursor.getDouble(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_DAILY_CHANGE))
            val dailyProfit = cursor.getDouble(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_DAILY_PROFIT))
            funds.add(Fund(fundCode, fundName, currentAmount, currentHoldingProfit, dailyChange, dailyProfit))
        }
        cursor.close()
        db.close()
        return funds
    }

    fun getFund(fundCode: String): Fund? {
        val db = dbHelper.readableDatabase
        val cursor: Cursor = db.query(
            FundDatabaseHelper.TABLE_NAME,
            null,
            "${FundDatabaseHelper.COLUMN_FUND_CODE} = ?",
            arrayOf(fundCode),
            null,
            null,
            null
        )

        return if (cursor.moveToFirst()) {
            val fundName = cursor.getString(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_FUND_NAME))
            val currentAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_CURRENT_AMOUNT))
            val currentHoldingProfit = cursor.getDouble(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_CURRENT_HOLDING_PROFIT))
            val dailyChange = cursor.getDouble(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_DAILY_CHANGE))
            val dailyProfit = cursor.getDouble(cursor.getColumnIndexOrThrow(FundDatabaseHelper.COLUMN_DAILY_PROFIT))
            cursor.close()
            db.close()
            Fund(fundCode, fundName, currentAmount, currentHoldingProfit, dailyChange, dailyProfit)
        } else {
            cursor.close()
            db.close()
            null
        }
    }

    fun updateFund(fund: Fund) {
        addFund(fund) // 利用insertWithOnConflict的REPLACE特性
    }
}

class FundDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "funds.db"
        const val DATABASE_VERSION = 2
        const val TABLE_NAME = "funds"
        const val COLUMN_FUND_CODE = "fund_code"
        const val COLUMN_FUND_NAME = "fund_name"
        const val COLUMN_CURRENT_AMOUNT = "current_amount"
        const val COLUMN_CURRENT_HOLDING_PROFIT = "current_holding_profit"
        const val COLUMN_DAILY_CHANGE = "daily_change"
        const val COLUMN_DAILY_PROFIT = "daily_profit"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE $TABLE_NAME " +
                "($COLUMN_FUND_CODE TEXT PRIMARY KEY, " +
                "$COLUMN_FUND_NAME TEXT, " +
                "$COLUMN_CURRENT_AMOUNT REAL, " +
                "$COLUMN_CURRENT_HOLDING_PROFIT REAL, " +
                "$COLUMN_DAILY_CHANGE REAL, " +
                "$COLUMN_DAILY_PROFIT REAL)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }
}
