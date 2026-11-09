package com.aulakit.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {

    private static final int COLOR_MAIN = Color.parseColor("#2B5BA8");
    private static final int COLOR_LIGHT = Color.parseColor("#E8EEF8");

    private SharedPreferences prefs;
    private final List<String> students = new ArrayList<>();
    private final List<String> pending = new ArrayList<>();
    private final Random rnd = new Random();

    private final View[] screens = new View[4];
    private final Button[] tabs = new Button[4];

    private ArrayAdapter<String> adapter;
    private TextView drawName;
    private TextView drawInfo;
    private TextView groupsResult;
    private TextView timerText;
    private Button timerBtn;
    private EditText minutesInput;
    private CountDownTimer timer;
    private boolean running = false;
    private ToneGenerator tone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("aulakit", MODE_PRIVATE);
        loadStudents();
        refillPending();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        screens[0] = buildStudents();
        screens[1] = buildDraw();
        screens[2] = buildGroups();
        screens[3] = buildTimer();
        for (View v : screens) {
            content.addView(v, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(COLOR_LIGHT);
        String[] names = {"Alumnos", "Sorteo", "Grupos", "Tiempo"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setOnClickListener(v -> showScreen(idx));
            tabs[i] = b;
            bar.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1f));
        }
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        showScreen(0);
        updateDrawInfo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (tone != null) tone.release();
    }

    // Pantallas

    private void showScreen(int idx) {
        for (int i = 0; i < 4; i++) {
            screens[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            tabs[i].setTextColor(i == idx ? COLOR_MAIN : Color.GRAY);
            tabs[i].setTypeface(null, i == idx ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private LinearLayout baseScreen(String title) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(24), dp(20), dp(12));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(26);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(COLOR_MAIN);
        l.addView(t);
        return l;
    }

    private Button mainButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(COLOR_MAIN);
        return b;
    }

    // Alumnos

    private View buildStudents() {
        LinearLayout l = baseScreen("Alumnos");

        TextView hint = new TextView(this);
        hint.setText("Mantené apretado un nombre para borrarlo");
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, dp(4), 0, dp(12));
        l.addView(hint);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final EditText input = new EditText(this);
        input.setHint("Nombre del alumno");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button add = mainButton("Agregar");
        row.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        l.addView(row);

        add.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) return;
            students.add(name);
            input.setText("");
            studentsChanged();
        });

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, students);
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            String removed = students.remove(position);
            studentsChanged();
            Toast.makeText(this, "Borrado: " + removed, Toast.LENGTH_SHORT).show();
            return true;
        });
        l.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return l;
    }

    // Sorteo

    private View buildDraw() {
        LinearLayout l = baseScreen("Sorteo");

        drawName = new TextView(this);
        drawName.setText("?");
        drawName.setTextSize(44);
        drawName.setGravity(Gravity.CENTER);
        drawName.setTypeface(null, Typeface.BOLD);
        l.addView(drawName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        drawInfo = new TextView(this);
        drawInfo.setGravity(Gravity.CENTER);
        drawInfo.setTextColor(Color.GRAY);
        drawInfo.setPadding(0, 0, 0, dp(16));
        l.addView(drawInfo);

        Button go = mainButton("Sortear");
        go.setTextSize(20);
        go.setOnClickListener(v -> draw());
        l.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        Button reset = new Button(this);
        reset.setText("Reiniciar ronda");
        reset.setAllCaps(false);
        reset.setOnClickListener(v -> {
            refillPending();
            drawName.setText("?");
            updateDrawInfo();
        });
        l.addView(reset);
        return l;
    }

    private void draw() {
        if (students.isEmpty()) {
            Toast.makeText(this, "Primero cargá alumnos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pending.isEmpty()) {
            refillPending();
            Toast.makeText(this, "Ronda nueva", Toast.LENGTH_SHORT).show();
        }
        int i = rnd.nextInt(pending.size());
        drawName.setText(pending.remove(i));
        updateDrawInfo();
    }

    private void updateDrawInfo() {
        if (drawInfo == null) return;
        drawInfo.setText("Quedan " + pending.size() + " de " + students.size() + " en esta ronda");
    }

    // Grupos

    private View buildGroups() {
        LinearLayout l = baseScreen("Grupos");

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        final EditText count = new EditText(this);
        count.setHint("Cantidad de grupos");
        count.setInputType(InputType.TYPE_CLASS_NUMBER);
        count.setText("4");
        row.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button make = mainButton("Armar");
        row.addView(make);
        l.addView(row);

        ScrollView scroll = new ScrollView(this);
        groupsResult = new TextView(this);
        groupsResult.setTextSize(18);
        scroll.addView(groupsResult);
        l.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        make.setOnClickListener(v -> {
            if (students.isEmpty()) {
                Toast.makeText(this, "Primero cargá alumnos", Toast.LENGTH_SHORT).show();
                return;
            }
            int n;
            try {
                n = Integer.parseInt(count.getText().toString().trim());
            } catch (NumberFormatException e) {
                n = 0;
            }
            if (n < 1) {
                Toast.makeText(this, "Poné un número válido", Toast.LENGTH_SHORT).show();
                return;
            }
            if (n > students.size()) n = students.size();
            makeGroups(n);
        });
        return l;
    }

    private void makeGroups(int n) {
        List<String> shuffled = new ArrayList<>(students);
        Collections.shuffle(shuffled, rnd);
        List<List<String>> groups = new ArrayList<>();
        for (int i = 0; i < n; i++) groups.add(new ArrayList<>());
        for (int i = 0; i < shuffled.size(); i++) groups.get(i % n).add(shuffled.get(i));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("Grupo ").append(i + 1).append("\n");
            for (String s : groups.get(i)) sb.append("  • ").append(s).append("\n");
            sb.append("\n");
        }
        groupsResult.setText(sb.toString());
    }

    // Temporizador

    private View buildTimer() {
        LinearLayout l = baseScreen("Temporizador");

        timerText = new TextView(this);
        timerText.setText("05:00");
        timerText.setTextSize(72);
        timerText.setGravity(Gravity.CENTER);
        timerText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        l.addView(timerText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        TextView lbl = new TextView(this);
        lbl.setText("Minutos:");
        lbl.setTextSize(18);
        row.addView(lbl);
        minutesInput = new EditText(this);
        minutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutesInput.setText("5");
        minutesInput.setGravity(Gravity.CENTER);
        row.addView(minutesInput, new LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT));
        l.addView(row);

        timerBtn = mainButton("Iniciar");
        timerBtn.setTextSize(20);
        timerBtn.setOnClickListener(v -> {
            if (running) stopTimer(); else startTimer();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(16);
        l.addView(timerBtn, lp);
        return l;
    }

    private void startTimer() {
        int min;
        try {
            min = Integer.parseInt(minutesInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            min = 0;
        }
        if (min < 1) {
            Toast.makeText(this, "Poné al menos 1 minuto", Toast.LENGTH_SHORT).show();
            return;
        }
        running = true;
        timerBtn.setText("Detener");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        timer = new CountDownTimer(min * 60000L, 250) {
            @Override
            public void onTick(long ms) {
                timerText.setText(formatTime((ms + 999) / 1000));
            }

            @Override
            public void onFinish() {
                timerText.setText("00:00");
                running = false;
                timerBtn.setText("Iniciar");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                beep();
            }
        }.start();
    }

    private void stopTimer() {
        if (timer != null) timer.cancel();
        running = false;
        timerBtn.setText("Iniciar");
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        timerText.setText(formatTime(readMinutes() * 60L));
    }

    private int readMinutes() {
        try {
            return Math.max(0, Integer.parseInt(minutesInput.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatTime(long totalSeconds) {
        return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private void beep() {
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000);
        } catch (RuntimeException ignored) {
        }
        Toast.makeText(this, "Se acabó el tiempo", Toast.LENGTH_LONG).show();
    }

    // Datos

    private void studentsChanged() {
        saveStudents();
        refillPending();
        adapter.notifyDataSetChanged();
        updateDrawInfo();
    }

    private void refillPending() {
        pending.clear();
        pending.addAll(students);
    }

    private void saveStudents() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < students.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(students.get(i));
        }
        prefs.edit().putString("students", sb.toString()).apply();
    }

    private void loadStudents() {
        students.clear();
        String raw = prefs.getString("students", "");
        if (raw.isEmpty()) return;
        for (String s : raw.split("\n")) {
            if (!s.trim().isEmpty()) students.add(s.trim());
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
