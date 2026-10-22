# Plan lekcji – aplikacja na Androida

Aplikacja pobiera plan z udostępnionego linku do Excela (SharePoint / OneDrive / Google Sheets),
sama go odświeża, wykrywa zmiany i wysyła powiadomienia. Interfejs po polsku.

## Jak zbudować APK (najprostsza droga: GitHub, bez instalowania czegokolwiek)
1. Załóż puste repozytorium na github.com i wgraj do niego całą zawartość tego folderu
   (razem z ukrytym folderem `.github`).
2. Wejdź w zakładkę **Actions** → **Build APK** → **Run workflow** (uruchomi się też samo po wgraniu).
3. Po ~3–5 min otwórz zakończone uruchomienie i pobierz artefakt **PlanLekcji-apk** (plik `app-debug.apk`).
4. Skopiuj APK na telefon i zainstaluj (zezwól na instalację z nieznanych źródeł).

## Albo w Android Studio
File → Open → wskaż ten folder → poczekaj na synchronizację Gradle → Run ▶ (telefon z USB)
lub Build → Build APK(s).

## Jak to działa
- Link jest wpisany domyślnie, można go zmienić w Ustawieniach.
- Plan jest odczytywany z 5 arkuszy (poniedziałek–piątek): godziny, klasy z liczbą uczniów, sala, budynek,
  przedmiot, nauczyciel, zastępstwa i żółte pola (zajęcia łączone).
- Odświeżanie: co 2 min przy otwartej aplikacji, w tle co 15 min – 6 godz. (WorkManager).
- Zmiany (sala, nauczyciel, przedmiot, budynek, zastępstwo, nowe/usunięte lekcje) trafiają do powiadomień
  i do ekranu „Historia zmian”.
- Sortowanie: godzina / klasa, rosnąco / malejąco. Chipy klas = „moje klasy” (filtr + zakres powiadomień).
