# KONI — Application Mobile Android

Application Android native pour KONI (Logiciel de Gestion Immobilière).

## Obtenir l'APK automatiquement (recommandé)

### Étape 1 — Créer un dépôt GitHub
1. Allez sur github.com → **New repository**
2. Nom : `KoniApp`
3. Visibilité : **Public** (pour GitHub Actions gratuit)
4. Cliquez **Create repository**

### Étape 2 — Uploader les fichiers
Dans le dépôt créé, uploadez tous ces fichiers en respectant la structure des dossiers.

### Étape 3 — GitHub Actions compile automatiquement
Dès que vous poussez le code, GitHub Actions :
- Compile le projet Android
- Génère l'APK
- Crée une **Release** avec l'APK téléchargeable

### Étape 4 — Télécharger l'APK
- Allez dans l'onglet **Releases** de votre dépôt GitHub
- Téléchargez `KONI-debug.apk`

### Étape 5 — Installer sur Android
1. Transférez l'APK sur votre téléphone (WhatsApp, email, câble USB)
2. **Paramètres → Sécurité → Sources inconnues** → Activer
3. Ouvrez l'APK et installez
4. Lancez **KONI** depuis vos applications

---

## Fonctionnalités de l'app

- Écran de démarrage (splash) animé avec logo KONI
- Chargement de `https://koni-03ud.onrender.com`
- Barre de progression lors du chargement
- Actualisation par glissement (pull-to-refresh)
- Détection de connexion internet avec page hors-ligne
- Navigation arrière avec bouton Android
- Téléchargement des PDF et fichiers Excel
- Compatible Android 5.0+ (API 21+)

---

## Structure du projet

```
KoniApp/
├── .github/workflows/build-apk.yml   ← Compilation automatique
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/koni/app/
│       │   ├── SplashActivity.java
│       │   └── MainActivity.java
│       └── res/
│           ├── layout/
│           │   ├── activity_splash.xml
│           │   └── activity_main.xml
│           ├── drawable/ic_koni_logo.xml
│           ├── values/
│           │   ├── strings.xml
│           │   ├── colors.xml
│           │   └── themes.xml
│           └── mipmap-*/ic_launcher.xml
├── build.gradle
├── settings.gradle
└── gradle/wrapper/gradle-wrapper.properties
```

---

Editeur : KONE KATANGUI | KONI v1.0
