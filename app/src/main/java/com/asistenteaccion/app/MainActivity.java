package com.asistenteaccion.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int VOICE_REQUEST = 700;
    private static final int AUDIO_PERMISSION = 701;
    private EditText commandInput;
    private TextView serviceStatus;
    private LinearLayout appsContainer;
    private PackageManager packageManager;
    private final List<ResolveInfo> apps = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        packageManager = getPackageManager();
        commandInput = findViewById(R.id.commandInput);
        serviceStatus = findViewById(R.id.serviceStatus);
        appsContainer = findViewById(R.id.appsContainer);
        findViewById(R.id.runButton).setOnClickListener(v -> ejecutarComando(commandInput.getText().toString()));
        findViewById(R.id.voiceButton).setOnClickListener(v -> iniciarVoz());
        findViewById(R.id.accessibilityButton).setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        cargarAplicaciones();
    }

    @Override protected void onResume() { super.onResume(); actualizarEstadoServicio(); }

    private void cargarAplicaciones() {
        Intent intent = new Intent(Intent.ACTION_MAIN); intent.addCategory(Intent.CATEGORY_LAUNCHER);
        apps.clear(); apps.addAll(packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL));
        Collections.sort(apps, Comparator.comparing(a -> a.loadLabel(packageManager).toString().toLowerCase(Locale.ROOT)));
        appsContainer.removeAllViews();
        for (ResolveInfo info : apps) {
            if (info.activityInfo.packageName.equals(getPackageName())) continue;
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(18, 12, 14, 12); row.setBackgroundColor(Color.WHITE);
            ImageView icon = new ImageView(this); icon.setImageDrawable(info.loadIcon(packageManager)); row.addView(icon, new LinearLayout.LayoutParams(52, 52));
            TextView name = new TextView(this); name.setText(info.loadLabel(packageManager)); name.setTextSize(17); name.setTextColor(Color.rgb(30,30,38)); name.setPadding(16, 0, 8, 0); row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(v -> abrir(info));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(12, 4, 12, 4); appsContainer.addView(row, params);
        }
    }

    private void abrir(ResolveInfo info) {
        Intent launch = packageManager.getLaunchIntentForPackage(info.activityInfo.packageName);
        if (launch != null) startActivity(launch); else Toast.makeText(this, "No se puede abrir esa aplicación", Toast.LENGTH_SHORT).show();
    }

    private void iniciarVoz() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"); intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Di un comando");
        try { startActivityForResult(intent, VOICE_REQUEST); } catch (Exception e) { Toast.makeText(this, "El reconocimiento de voz no está disponible", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_REQUEST && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) { commandInput.setText(results.get(0)); ejecutarComando(results.get(0)); }
        }
    }

    private void ejecutarComando(String raw) {
        String command = normalizar(raw);
        if (command.isEmpty()) { Toast.makeText(this, "Escribe o di un comando", Toast.LENGTH_SHORT).show(); return; }
        if (command.equals("volver") || command.contains("volver atras") || command.equals("atras")) { confirmar("volver atrás", () -> ejecutarServicio("volver")); return; }
        if (command.equals("inicio") || command.contains("ir al inicio") || command.contains("pantalla principal")) { confirmar("ir al inicio", () -> ejecutarServicio("inicio")); return; }
        if (command.contains("abrir configuracion") || command.contains("abre configuracion") || command.contains("abrir ajustes")) { confirmar("abrir configuración", () -> startActivity(new Intent(Settings.ACTION_SETTINGS))); return; }
        if (command.startsWith("escribe ") || command.startsWith("escribir ")) {
            String text = raw.substring(raw.indexOf(' ') + 1).trim(); confirmar("escribir: " + text, () -> ejecutarServicioEscribir(text)); return;
        }
        String name = raw.replaceFirst("(?i)^(abr[íi]|abre|abrir|inicia|iniciar)\\s+", "").trim();
        if (!name.equals(raw) || command.startsWith("abr ")) { abrirPorNombre(name); return; }
        abrirPorNombre(raw.trim());
    }

    private void abrirPorNombre(String query) {
        String needle = normalizar(query); ResolveInfo best = null;
        for (ResolveInfo info : apps) { String label = normalizar(info.loadLabel(packageManager).toString()); if (label.equals(needle) || label.contains(needle) || needle.contains(label)) { best = info; break; } }
        if (best != null) abrir(best); else Toast.makeText(this, "No encontré la aplicación: " + query, Toast.LENGTH_LONG).show();
    }

    private void confirmar(String action, Runnable yes) {
        new AlertDialog.Builder(this).setTitle("Confirmar acción").setMessage("¿Quieres " + action + "?").setNegativeButton("Cancelar", null).setPositiveButton("Confirmar", (d, w) -> yes.run()).show();
    }

    private void ejecutarServicio(String action) {
        AccionAccessibilityService service = AccionAccessibilityService.getInstance();
        if (service == null) { Toast.makeText(this, "Activa primero el Servicio de Accesibilidad", Toast.LENGTH_LONG).show(); return; }
        boolean ok = action.equals("volver") ? service.volver() : service.inicio(); if (!ok) Toast.makeText(this, "Android no permitió ejecutar la acción", Toast.LENGTH_SHORT).show();
    }

    private void ejecutarServicioEscribir(String text) {
        AccionAccessibilityService service = AccionAccessibilityService.getInstance();
        if (service == null) { Toast.makeText(this, "Activa primero el Servicio de Accesibilidad", Toast.LENGTH_LONG).show(); return; }
        if (!service.escribir(text)) Toast.makeText(this, "No encontré un campo editable activo", Toast.LENGTH_LONG).show();
    }

    private String normalizar(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT).trim(), Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replaceAll("[¿?!¡.,]", "").replaceAll("\\s+", " ");
    }

    private void actualizarEstadoServicio() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean active = enabled != null && enabled.contains(getPackageName());
        serviceStatus.setText(active ? "Servicio de accesibilidad: activo" : "Servicio de accesibilidad: desactivado");
    }
}
