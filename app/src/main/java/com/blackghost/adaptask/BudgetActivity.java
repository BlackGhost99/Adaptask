package com.blackghost.adaptask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Expense tracking is offline-first and belongs to the existing Adaptask APK. */
public final class BudgetActivity extends Activity {
    private static final int EXPORT_CSV = 504;
    private static final int BLUE = Color.rgb(22, 139, 246);
    private static final int INK = Color.rgb(24, 43, 74);
    private static final int RED = Color.rgb(206, 49, 65);
    private static final int GREEN = Color.rgb(18, 137, 94);
    private final boolean french = !Locale.getDefault().getLanguage().equals("en");
    private TaskStore taskStore;
    private BudgetStore budget;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        taskStore = new TaskStore(this);
        budget = new BudgetStore(taskStore);
        render();
    }

    @Override protected void onDestroy() {
        if (taskStore != null) taskStore.close();
        super.onDestroy();
    }

    private String tr(String fr, String en) { return french ? fr : en; }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
    private String money(long n) {
        return NumberFormat.getIntegerInstance(Locale.FRANCE).format(n) + " FCFA";
    }
    private String date(long timestamp) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date(timestamp));
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size, int color, boolean strong) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        if (strong) view.setTypeface(null, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout panel() {
        LinearLayout view = column();
        view.setPadding(dp(15), dp(15), dp(15), dp(15));
        view.setBackground(background(Color.WHITE, 16));
        view.setElevation(dp(2));
        return view;
    }

    private void append(LinearLayout parent, View view, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(bottomDp);
        parent.addView(view, params);
    }

    private Button button(String name, boolean primary) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(name);
        view.setTextSize(14);
        view.setTextColor(primary ? Color.WHITE : BLUE);
        view.setBackground(background(primary ? BLUE : Color.rgb(231, 244, 255), 12));
        view.setPadding(dp(12), dp(5), dp(12), dp(5));
        return view;
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 251, 255));
        setContentView(scroll);
        LinearLayout root = column();
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹", false);
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));
        back.setOnClickListener(v -> finish());
        TextView heading = text(tr("Budget & Dépenses", "Budget & Expenses"), 23, BLUE, true);
        LinearLayout.LayoutParams headerText = new LinearLayout.LayoutParams(0, -2, 1);
        headerText.leftMargin = dp(10);
        header.addView(heading, headerText);
        append(root, header, 15);
        append(root, text(tr("Suivi personnel · disponible hors connexion",
                              "Personal ledger · available offline"), 12, INK, false), 15);

        long capital = budget.capital();
        long spent = budget.totalSpent();
        long balance = capital - spent;

        LinearLayout overview = panel();
        overview.addView(text(tr("Capital initial", "Starting capital"), 13, INK, false));
        append(overview, text(money(capital), 22, INK, true), 10);
        overview.addView(text(tr("Total des dépenses", "Total expenses"), 13, INK, false));
        append(overview, text(money(spent), 22, RED, true), 10);
        overview.addView(text(tr("Reste disponible", "Remaining balance"), 13, INK, false));
        append(overview, text(money(balance), 28, balance < 0 ? RED : GREEN, true), 12);

        if (capital > 0) {
            ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(1000);
            // Use floating-point only for the UI ratio. Monetary arithmetic remains integer.
            progress.setProgress((int) Math.min(1000, (spent / (double) capital) * 1000));
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(balance < 0 ? RED : BLUE));
            append(overview, progress, 8);
        }
        if (balance < 0) append(overview, text(tr("Attention : le budget est dépassé.",
                  "Warning: budget exceeded."), 13, RED, true), 8);
        Button setCapital = button(tr("Modifier le capital", "Edit starting capital"), false);
        append(overview, setCapital, 0);
        setCapital.setOnClickListener(v -> editCapital());
        append(root, overview, 14);

        Button add = button(tr("+ Nouvelle dépense", "+ New expense"), true);
        append(root, add, 9);
        add.setOnClickListener(v -> editExpense(0, "", "", 0, System.currentTimeMillis()));
        Button export = button(tr("Exporter en CSV (Excel)", "Export CSV (Excel)"), false);
        append(root, export, 18);
        export.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_TITLE, "adaptask-depenses.csv");
            startActivityForResult(intent, EXPORT_CSV);
        });

        append(root, text(tr("Liste des dépenses", "Expense history"), 20, INK, true), 12);
        boolean hasRows = false;
        try (Cursor rows = budget.list(false)) {
            while (rows.moveToNext()) {
                hasRows = true;
                long id = rows.getLong(0);
                String title = rows.getString(1);
                String category = rows.getString(2);
                long amount = rows.getLong(3);
                long spentAt = rows.getLong(4);
                LinearLayout item = panel();
                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout info = column();
                append(info, text(title, 16, INK, true), 5);
                info.addView(text((category.isEmpty() ? tr("Divers", "Other") : category)
                        + " · " + date(spentAt), 12, INK, false));
                top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                TextView amountLabel = text("- " + money(amount), 14, RED, true);
                amountLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
                top.addView(amountLabel);
                append(item, top, 12);

                LinearLayout controls = new LinearLayout(this);
                controls.setOrientation(LinearLayout.HORIZONTAL);
                Button edit = button(tr("Modifier", "Edit"), false);
                controls.addView(edit, new LinearLayout.LayoutParams(0, dp(43), 1));
                edit.setOnClickListener(v -> editExpense(id, title, category, amount, spentAt));
                Button delete = button(tr("Supprimer", "Delete"), false);
                LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(0, dp(43), 1);
                deleteParams.leftMargin = dp(8);
                controls.addView(delete, deleteParams);
                delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(tr("Supprimer cette dépense ?", "Delete this expense?"))
                    .setMessage(title + " · " + money(amount))
                    .setNegativeButton(tr("Annuler", "Cancel"), null)
                    .setPositiveButton(tr("Supprimer", "Delete"), (dialog, which) -> {
                        try { budget.delete(id); render(); }
                        catch (Exception error) { showError(error); }
                    }).show());
                item.addView(controls);
                append(root, item, 10);
            }
        }
        if (!hasRows) append(root, text(tr("Aucune dépense enregistrée.",
                "No expenses recorded yet."), 14, INK, false), 10);
    }

    private EditText field(String hint, String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(15);
        input.setPadding(dp(6), dp(10), dp(6), dp(10));
        return input;
    }

    private long parseAmount(EditText input, boolean allowZero) {
        String raw = input.getText().toString().replace(" ", "")
                .replace("\u00a0", "").replace("\u202f", "").trim();
        long value;
        try { value = Long.parseLong(raw); }
        catch (NumberFormatException e) {
            input.setError(tr("Montant entier invalide", "Invalid whole-number amount"));
            return -1;
        }
        if ((allowZero ? value < 0 : value <= 0) || value > BudgetStore.MAX_AMOUNT) {
            input.setError(tr("Montant hors limites", "Amount out of range"));
            return -1;
        }
        return value;
    }

    private void editCapital() {
        LinearLayout layout = column();
        layout.setPadding(dp(20), dp(12), dp(20), 0);
        EditText amount = field(tr("Capital initial (FCFA)", "Starting capital (FCFA)"),
                String.valueOf(budget.capital()));
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(amount);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(tr("Définir le capital", "Set starting capital"))
                .setView(layout)
                .setNegativeButton(tr("Annuler", "Cancel"), null)
                .setPositiveButton(tr("Enregistrer", "Save"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    long value = parseAmount(amount, true);
                    if (value < 0) return;
                    try {
                        budget.setCapital(value);
                        dialog.dismiss();
                        render();
                    } catch (Exception e) { showError(e); }
                }));
        dialog.show();
    }

    private void editExpense(long id, String oldTitle, String oldCategory, long oldAmount,
                             long oldDate) {
        LinearLayout layout = column();
        layout.setPadding(dp(18), dp(8), dp(18), 0);
        EditText title = field(tr("Dépense (ex : transport)", "Expense (e.g. transport)"), oldTitle);
        append(layout, title, 6);
        EditText category = field(tr("Catégorie (facultatif)", "Category (optional)"), oldCategory);
        append(layout, category, 6);
        EditText amount = field(tr("Montant en FCFA", "Amount in FCFA"),
                oldAmount > 0 ? String.valueOf(oldAmount) : "");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        append(layout, amount, 6);

        final long[] selectedDate = {oldDate};
        Button date = button(tr("Date : ", "Date: ") + date(oldDate), false);
        append(layout, date, 6);
        date.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selectedDate[0]);
            new DatePickerDialog(this, (picker, year, month, day) -> {
                cal.set(year, month, day);
                selectedDate[0] = cal.getTimeInMillis();
                date.setText(tr("Date : ", "Date: ") + date(selectedDate[0]));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(id == 0 ? tr("Nouvelle dépense", "New expense")
                        : tr("Modifier la dépense", "Edit expense"))
                .setView(layout)
                .setNegativeButton(tr("Annuler", "Cancel"), null)
                .setPositiveButton(tr("Enregistrer", "Save"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = title.getText().toString().trim();
                    String group = category.getText().toString().trim();
                    if (name.isEmpty() || name.length() > 120) {
                        title.setError(tr("Description obligatoire (max 120 caractères)",
                                "Description required (max 120 characters)"));
                        return;
                    }
                    if (group.length() > 60) {
                        category.setError(tr("60 caractères maximum", "60 characters maximum"));
                        return;
                    }
                    long value = parseAmount(amount, false);
                    if (value < 0) return;
                    try {
                        if (id == 0) budget.add(name, group, value, selectedDate[0]);
                        else budget.update(id, name, group, value, selectedDate[0]);
                        dialog.dismiss();
                        render();
                    } catch (Exception e) { showError(e); }
                }));
        dialog.show();
    }

    private void showError(Exception e) {
        Toast.makeText(this, tr("Opération impossible : ", "Operation failed: ")
                + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private static String csv(String input) {
        String value = input == null ? "" : input;
        // Protect formula injection when the file is opened in Excel or LibreOffice.
        String leading = value.trim();
        if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void exportCsv(Uri uri) throws IOException {
        StringBuilder output = new StringBuilder("\uFEFF");
        output.append("Date;Description;Catégorie;Montant (FCFA);Reste (FCFA)\n");
        long running = budget.capital();
        output.append(";").append(csv(tr("Capital initial", "Starting capital")))
                .append(";;0;").append(running).append('\n');
        try (Cursor rows = budget.list(true)) {
            while (rows.moveToNext()) {
                long amount = rows.getLong(3);
                running -= amount;
                output.append(csv(date(rows.getLong(4)))).append(';')
                        .append(csv(rows.getString(1))).append(';')
                        .append(csv(rows.getString(2))).append(';')
                        .append(amount).append(';').append(running).append('\n');
            }
        }
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
            if (stream == null) throw new IOException("Document inaccessible");
            stream.write(output.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_CSV || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        try {
            exportCsv(data.getData());
            Toast.makeText(this, tr("Export CSV enregistré", "CSV exported"), Toast.LENGTH_LONG).show();
        } catch (Exception e) { showError(e); }
    }
}
