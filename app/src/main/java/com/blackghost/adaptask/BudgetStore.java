package com.blackghost.adaptask;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Local-only budget ledger, stored in the same SQLite database as Adaptask tasks.
 * Monetary values are whole FCFA (integers), never floating-point values.
 */
public final class BudgetStore {
    public static final long MAX_AMOUNT = 1_000_000_000_000L;
    private final TaskStore taskStore;

    public BudgetStore(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    public long capital() {
        try (Cursor cursor = taskStore.getReadableDatabase()
                .rawQuery("SELECT capital FROM budget_config WHERE id=1", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public void setCapital(long amount) {
        validateCapital(amount);
        ContentValues values = new ContentValues();
        values.put("capital", amount);
        if (taskStore.getWritableDatabase().update("budget_config", values, "id=1", null) != 1) {
            throw new IllegalStateException("Budget configuration not found");
        }
    }

    public long totalSpent() {
        try (Cursor cursor = taskStore.getReadableDatabase()
                .rawQuery("SELECT COALESCE(SUM(amount),0) FROM expenses", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    public Cursor list(boolean chronological) {
        return taskStore.getReadableDatabase().rawQuery(
            "SELECT id,title,category,amount,spent_at FROM expenses ORDER BY spent_at "
            + (chronological ? "ASC,id ASC" : "DESC,id DESC"), null);
    }

    public long add(String title, String category, long amount, long spentAt) {
        validate(title, category, amount, spentAt);
        ContentValues values = values(title, category, amount, spentAt);
        return taskStore.getWritableDatabase().insertOrThrow("expenses", null, values);
    }

    public void update(long id, String title, String category, long amount, long spentAt) {
        if (id <= 0) throw new IllegalArgumentException("Invalid expense ID");
        validate(title, category, amount, spentAt);
        if (taskStore.getWritableDatabase().update(
                "expenses", values(title, category, amount, spentAt),
                "id=?", new String[]{String.valueOf(id)}) != 1) {
            throw new IllegalArgumentException("Expense not found");
        }
    }

    public void delete(long id) {
        if (id <= 0) throw new IllegalArgumentException("Invalid expense ID");
        if (taskStore.getWritableDatabase().delete(
                "expenses", "id=?", new String[]{String.valueOf(id)}) != 1) {
            throw new IllegalArgumentException("Expense not found");
        }
    }

    public static void validateCapital(long amount) {
        if (amount < 0 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException("Capital must be between 0 and " + MAX_AMOUNT);
        }
    }

    public static void validate(String title, String category, long amount, long spentAt) {
        if (title == null || title.trim().isEmpty() || title.trim().length() > 120)
            throw new IllegalArgumentException("A description (max 120 characters) is required");
        if (category == null || category.trim().length() > 60)
            throw new IllegalArgumentException("Category is too long");
        if (amount <= 0 || amount > MAX_AMOUNT)
            throw new IllegalArgumentException("Expense amount must be positive");
        if (spentAt <= 0) throw new IllegalArgumentException("Invalid date");
    }

    private static ContentValues values(String title, String category, long amount, long spentAt) {
        ContentValues values = new ContentValues();
        values.put("title", title.trim());
        values.put("category", category.trim());
        values.put("amount", amount);
        values.put("spent_at", spentAt);
        return values;
    }
}
