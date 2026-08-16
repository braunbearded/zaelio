package com.zaelio.app;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class I18n {
    public static final String SYSTEM = "system";
    public static final String GERMAN = "de";
    public static final String ENGLISH = "en";
    public static final String SPANISH = "es";

    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> ES = new HashMap<>();

    static {
        put("Abbrechen", "Cancel", "Cancelar");
        put("Aktionen", "Actions", "Acciones");
        put("Akzentfarbe", "Accent color", "Color de acento");
        put("Bestehende Felder brauchen einen Namen.", "Existing fields need a name.", "Los campos existentes necesitan un nombre.");
        put("Beschreibung", "Description", "Descripción");
        put("Build", "Build", "Compilación");
        put("Darstellung", "Appearance", "Apariencia");
        put("Daten übertragen", "Data transfer", "Transferir datos");
        put("Diese Session wirklich löschen?", "Really delete this session?", "¿Eliminar esta sesión?");
        put("Diesen Tracker", "This tracker", "Este tracker");
        put("Dieses Feld", "This field", "Este campo");
        put(" wirklich löschen?", " really delete?", " ¿eliminar?");
        put("Dunkel", "Dark", "Oscuro");
        put("Dauer", "Duration", "Duración");
        put("Deutsch", "German", "Alemán");
        put("Dezimalzahl", "Decimal", "Decimal");
        put("Duplizieren", "Duplicate", "Duplicar");
        put("Einstellungen", "Settings", "Ajustes");
        put("English", "English", "Inglés");
        put("Export fehlgeschlagen: ", "Export failed: ", "Error al exportar: ");
        put("Export gespeichert", "Export saved", "Exportación guardada");
        put("Exportieren", "Export", "Exportar");
        put("Ausgeklappt", "Expanded", "Expandido");
        put("Eingeklappt", "Collapsed", "Plegado");
        put("Feld", "Field", "Campo");
        put("Feld hinzufügen", "Add field", "Añadir campo");
        put("Feld löschen", "Delete field", "Eliminar campo");
        put("Felder", "Fields", "Campos");
        put("Feldgröße", "Field size", "Tamaño del campo");
        put("Feldname", "Field name", "Nombre del campo");
        put("Ganzzahl", "Integer", "Entero");
        put("Gespeicherte Einträge", "Saved entries", "Entradas guardadas");
        put("Gewicht", "Weight", "Peso");
        put("Groß", "Large", "Grande");
        put("Grunddaten", "Basic data", "Datos básicos");
        put("Hell", "Light", "Claro");
        put("Import abgeschlossen (", "Import complete (", "Importación completada (");
        put("Import fehlgeschlagen: ", "Import failed: ", "Error al importar: ");
        put("Importieren", "Import", "Importar");
        put("Importierter Tracker", "Imported tracker", "Tracker importado");
        put("Klein", "Small", "Pequeño");
        put("Kompakt", "Compact", "Compacto");
        put("Kopie", "Copy", "Copia");
        put("Kopieren", "Copy", "Copiar");
        put("Link konnte nicht geöffnet werden", "Could not open link", "No se pudo abrir el enlace");
        put("Löschen", "Delete", "Eliminar");
        put("Menü", "Menu", "Menú");
        put("Nachkommastellen", "Decimal places", "Decimales");
        put("Neue Session", "New session", "Nueva sesión");
        put("Neuen Tracker anlegen", "Create new tracker", "Crear nuevo tracker");
        put("Neuer Tracker", "New tracker", "Nuevo tracker");
        put("Neues Feld", "New field", "Nuevo campo");
        put("Noch keine Felder angelegt.", "No fields yet.", "Aún no hay campos.");
        put("Noch keine Sessions vorhanden", "No sessions yet", "Aún no hay sesiones");
        put("Noch keine Werte eingetragen.", "No values entered yet.", "Aún no hay valores.");
        put("Notiz", "Note", "Nota");
        put("Nur Sessions", "Sessions only", "Solo sesiones");
        put("Nur Tracker", "Trackers only", "Solo trackers");
        put("Offline Tracker ohne Google-Dienste", "Offline tracker without Google services", "Tracker offline sin servicios de Google");
        put("Ohne Label", "No label", "Sin etiqueta");
        put("Pflichtfeld", "Required field", "Campo obligatorio");
        put("Quellcode", "Source code", "Código fuente");
        put("Reset", "Reset", "Reiniciar");
        put("Schriftgröße", "Font size", "Tamaño de fuente");
        put("Schrittweite", "Step size", "Incremento");
        put("Zusatzgewicht", "Additional weight", "Peso adicional");
        put("Sehr groß", "Very large", "Muy grande");
        put("Session", "Session", "Sesión");
        put("Session-Felder beim Öffnen", "Session fields on open", "Campos de sesión al abrir");
        put("Session konnte nicht angelegt werden", "Could not create session", "No se pudo crear la sesión");
        put("Session löschen", "Delete session", "Eliminar sesión");
        put("Session nicht gefunden", "Session not found", "Sesión no encontrada");
        put("Sessions", "Sessions", "Sesiones");
        put("Sprache", "Language", "Idioma");
        put("Spanisch", "Spanish", "Español");
        put("Standard", "Default", "Predeterminado");
        put("Standardwert", "Default value", "Valor predeterminado");
        put("Start", "Start", "Iniciar");
        put("Stop", "Stop", "Detener");
        put("System", "System", "Sistema");
        put("Text", "Text", "Texto");
        put("Textfeld", "Text field", "Campo de texto");
        put("Timer", "Timer", "Temporizador");
        put("Tracker", "Tracker", "Tracker");
        put("Tracker auswählen", "Choose tracker", "Elegir tracker");
        put("Tracker bearbeiten", "Edit tracker", "Editar tracker");
        put("Tracker duplizieren", "Duplicate tracker", "Duplicar tracker");
        put("Tracker enthält keine Felder", "Tracker has no fields", "El tracker no tiene campos");
        put("Tracker konnte nicht gespeichert werden", "Could not save tracker", "No se pudo guardar el tracker");
        put("Tracker löschen", "Delete tracker", "Eliminar tracker");
        put("Tracker nicht gefunden", "Tracker not found", "Tracker no encontrado");
        put("Tracker und Sessions", "Trackers and sessions", "Trackers y sesiones");
        put("Tracker-Name", "Tracker name", "Nombre del tracker");
        put("Typ", "Type", "Tipo");
        put("Über die App", "About", "Acerca de");
        put("Unbenannter Tracker", "Unnamed tracker", "Tracker sin nombre");
        put("Version", "Version", "Versión");
        put("Verschieben", "Move", "Mover");
        put("Vollständiges Backup", "Full backup", "Copia de seguridad completa");
        put("Vorherigen Wert übernehmen", "Use previous value", "Usar valor anterior");
        put("Vorlagen ohne Session-Einträge", "Templates without session entries", "Plantillas sin entradas de sesión");
        put("Wiederholungen", "Reps", "Repeticiones");
        put("Wähle einen Tracker für die neue Session.", "Choose a tracker for the new session.", "Elige un tracker para la nueva sesión.");
        put("Zurück", "Back", "Atrás");
        put("Zum Beenden erneut Zurück drücken", "Press Back again to exit", "Pulsa Atrás otra vez para salir");
        put("Zahlfeld: 12", "Number field: 12", "Campo numérico: 12");
        put("zweite Zeile", "second line", "segunda línea");
        put("dritte Zeile", "third line", "tercera línea");
        put("Blau", "Blue", "Azul");
        put("Teal", "Teal", "Verde azulado");
        put("Grün", "Green", "Verde");
        put("Orange", "Orange", "Naranja");
        put("Rot", "Red", "Rojo");
        put("Violett", "Purple", "Violeta");
        put("Pink", "Pink", "Rosa");
        put("Indigo", "Indigo", "Índigo");
    }

    private static void put(String de, String en, String es) {
        EN.put(de, en);
        ES.put(de, es);
    }

    private I18n() { }

    public static String resolveLanguage(String setting) {
        String language = SYSTEM.equals(setting) ? Locale.getDefault().getLanguage() : setting;
        if (GERMAN.equals(language)) {
            return GERMAN;
        }
        if (SPANISH.equals(language)) {
            return SPANISH;
        }
        return ENGLISH;
    }

    public static String translate(String text, String language) {
        if (text == null || GERMAN.equals(language)) {
            return text;
        }
        Map<String, String> map = SPANISH.equals(language) ? ES : EN;
        String translated = map.get(text);
        return translated == null ? text : translated;
    }
}
