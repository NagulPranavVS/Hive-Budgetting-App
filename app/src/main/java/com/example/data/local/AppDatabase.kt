package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Category
import com.example.data.model.Expense
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Category::class, Expense::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN monthlyBudget REAL DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN isIncome INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN isSavings INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN isSavings INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rupee_tracker_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            populateDefaultCategories(db)
        }

        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
            super.onDestructiveMigration(db)
            populateDefaultCategories(db)
        }

        private fun populateDefaultCategories(db: SupportSQLiteDatabase) {
            val defaultCategories: List<List<Any>> = listOf(
                // name, iconName, colorHex, isIncome, isSavings
                listOf("Food & Dining", "restaurant", "#EF4444", 0, 0),
                listOf("Transport", "directions_car", "#3B82F6", 0, 0),
                listOf("Shopping", "shopping_bag", "#EC4899", 0, 0),
                listOf("Utilities", "power", "#F59E0B", 0, 0),
                listOf("Entertainment", "movie", "#8B5CF6", 0, 0),
                listOf("Health", "medical_services", "#10B981", 0, 0),
                listOf("Education", "school", "#06B6D4", 0, 0),
                listOf("Other Expense", "category", "#6B7280", 0, 0),
                
                // Income categories
                listOf("Salary", "monetization_on", "#10B981", 1, 0),
                listOf("Freelance", "laptop", "#3B82F6", 1, 0),
                listOf("Investments", "trending_up", "#F59E0B", 1, 0),
                listOf("Gifts", "card_giftcard", "#EC4899", 1, 0),
                listOf("Other Income", "account_balance_wallet", "#8B5CF6", 1, 0),

                // Savings categories
                listOf("Investment", "trending_up", "#10B981", 0, 1),
                listOf("FD/RD", "description", "#3B82F6", 0, 1),
                listOf("In-hand", "account_balance_wallet", "#EC4899", 0, 1)
            )

            try {
                for (cat in defaultCategories) {
                    db.execSQL(
                        "INSERT INTO categories (name, iconName, colorHex, isIncome, isSavings, monthlyBudget) VALUES (?, ?, ?, ?, ?, NULL)",
                        arrayOf<Any>(cat[0], cat[1], cat[2], cat[3], cat[4])
                    )
                }
            } catch (e: Exception) {
                Log.e("AppDatabase", "Error inserting default categories during database creation/migration", e)
            }
        }
    }
}
