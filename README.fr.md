<div align="center">

# RideLab

**Transformez votre Android en tableau de bord de cyclisme en intérieur**

Home trainers BLE · Entraînement ERG · Enregistrement en arrière-plan · Analyse locale

![Android](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4)
![Local first](https://img.shields.io/badge/Data-Local%20first-35D0BA)

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md) · [Español](README.es.md) · [Português (Brasil)](README.pt-BR.md) · [Français](README.fr.md)

</div>

RideLab est une application Android native développée avec Kotlin et Jetpack Compose. Connectez un home trainer et un capteur cardiaque, réglez la puissance, suivez un entraînement et consultez vos sorties sur votre téléphone. Aucun compte ni service cloud requis.

## Fonctionnalités

- **Tableau de bord en direct :** puissance, fréquence cardiaque, cadence, durée et courbes des cinq dernières minutes.
- **ERG manuel :** saisie numérique, ajustements de ±5 W, application explicite et objectifs mémorisés par home trainer. Restaurer une préférence n’envoie pas de commande.
- **Entraînement :** séances intégrées, test FTP en rampe et test initial sans FTP connu. L’ERG automatique est actuellement réservé aux ThinkRider X2 MAX reconnus.
- **Arrière-plan :** un service au premier plan conserve les connexions et l’enregistrement Room à environ 1 Hz pendant le changement d’application, le verrouillage ou la rotation.
- **Gestion de sortie :** démarrer, mettre en pause, reprendre, terminer. La pause gèle le temps actif, les échantillons et la séance, en conservant connexions et résistance appliquée. Terminer conserve les connexions jusqu’à leur déconnexion manuelle.
- **Historique et analyse :** regroupement repliable par année/mois, suppression individuelle, courbes, continuité, moyennes/maxima, NP/IF/TSS, meilleures puissances, zones, W/kg et comparaisons historiques.
- **Énergie estimée :** calories calculées à partir de la puissance et équivalences alimentaires ; aucune mesure individuelle du métabolisme.
- **Export :** CSV par sortie, ZIP de tous les CSV et sauvegarde complète restaurable.
- **Fenêtre transparente :** durée, puissance, BPM et cadence au-dessus des autres applications, courbes repliables, déplacement, taille, police et transparence réglables.
- **Apparence :** cinq palettes, modes clair/sombre/système, trois dispositions flottantes et sept langues.

Les valeurs absentes restent absentes ; les vrais zéros sont conservés. FTP et poids sont facultatifs et mémorisés au début de la sortie : les changements ultérieurs ne modifient pas les données historiques. La NP exige une puissance suffisamment continue ; IF/TSS exigent le FTP enregistré. Les meilleures puissances couvrent 5/15/30 secondes et 1/5/20/60 minutes.

## Compatibilité

Les capacités sont déterminées par les services et caractéristiques GATT réels. Mesurer la puissance ne signifie pas pouvoir utiliser l’ERG.

| Appareil / protocole | Fonctionnement | Validation |
| --- | --- | --- |
| ThinkRider X2 MAX | Cycling Power + FE-C over BLE ERG, 50–2000 W et séances automatiques existantes | Principal home trainer validé physiquement |
| Home trainer FTMS | Indoor Bike Data ; ERG manuel avec caractéristiques et plage valides | Tests de protocole/unitaires ; validation matérielle en attente ; séances automatiques désactivées |
| Autres FE-C over BLE | ERG manuel prudent de 50–200 W avec FEC1/FEC2/FEC3 complets | Modèles non vérifiés ; automatisation désactivée |
| Cycling Power seul | Puissance et cadence si les révolutions du pédalier sont disponibles | Pas d’ERG déduit ; rôle distinct de capteur de puissance externe encore incomplet |
| Capteur cardiaque BLE | Heart Rate Service standard, BPM UINT8/UINT16 | Fonctionnement simultané avec X2 MAX validé ; autres modèles à tester |

Le X2 MAX testé utilise Cycling Power et FE-C, sans FTMS. Si les deux commandes sont présentes, FE-C est prioritaire ; aucun objectif n’est envoyé par les deux protocoles. FTMS exige autorisation et réponse de succès : une écriture GATT réussie ne suffit pas. La reconnexion ne renvoie pas automatiquement les objectifs. Aucune compatibilité universelle n’est annoncée. Voir le [guide](TRAINER_COMPATIBILITY.md).

## Prise en main

1. Utilisez Android 12+ (API 31) avec BLE. Installez l’APK s’il est proposé dans [Releases](https://github.com/JigenDaisuke66/RideLab/releases), sinon lancez le code avec Android Studio. Un AAB ne s’installe pas directement comme un APK.
2. Activez Bluetooth et autorisez les appareils à proximité. Sélectionnez séparément le home trainer et le capteur cardiaque.
3. Vérifiez les valeurs, puis renseignez éventuellement FTP et poids. Vous pouvez les laisser vides. Le test FTP initial est disponible sur les appareils pris en charge ; enregistrer son résultat exige une confirmation.
4. Démarrez la sortie et autorisez les notifications. Appliquez explicitement les objectifs ERG manuels ; consultez l’Historique après avoir terminé.

La fenêtre exige l’autorisation d’affichage au-dessus des autres applications. Réglez-la depuis la sortie ou les réglages. Testez d’abord une courte sortie puis le verrouillage : les politiques du téléphone affectent aussi l’arrière-plan. [Procédure](FOREGROUND_SERVICE_VERIFICATION.md).

## Export et restauration

| Fichier | Usage | Restaurable |
| --- | --- | --- |
| `.csv` individuel | Échantillons d’une sortie pour Excel/WPS | Non |
| CSV `.zip` | `summary.csv` et un CSV par sortie terminée | Non |
| `.ridelab` | Sorties terminées/interrompues, échantillons et FTP/poids historiques | Oui |

L’export groupé et la restauration sont en haut de l’Historique ; le CSV individuel se trouve dans les détails. La restauration fusionne les données, conserve l’existant et ignore les doublons identiques. Les sorties actives et réglages ne sont pas sauvegardés. Les cellules CSV vides indiquent une absence. La sauvegarde est compressée et vérifiée, sans chiffrement, et peut contenir les adresses Bluetooth. [Format et limites](HISTORY_BACKUP.md).

## Langues

Chinois simplifié, anglais, japonais, chinois traditionnel (Taïwan), espagnol, portugais du Brésil et français. L’application suit le système par défaut et utilise le chinois simplifié si sa langue n’est pas prise en charge. Les choix manuels sont conservés ; la langue par application d’Android est prise en charge.

## Développement et tests

Utilisez Android Studio compatible avec l’AGP du projet, de préférence son JDK 21 intégré. SDK de compilation 37.1, cible 36, API minimale 31. Voir les [versions](gradle/libs.versions.toml) et le [Wrapper](gradle/wrapper/gradle-wrapper.properties).

```bash
git clone https://github.com/JigenDaisuke66/RideLab.git
cd RideLab
# macOS / Linux
chmod +x gradlew
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Windows PowerShell :

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Ouvrez le projet, synchronisez Gradle et exécutez `app` sur un téléphone réel. Ces commandes ne produisent pas d’APK. Compiler les tests sur appareil ne les exécute pas ; les tests unitaires ne remplacent pas la validation matérielle. Le service possède BLE et l’enregistrement ; Compose présente l’état et transmet les événements. Lire [projet](PROJECT.md), [architecture UI](UI_ARCHITECTURE.md) et [feuille de route](ROADMAP.md).

## Retours et contributions

Dans [Issues](https://github.com/JigenDaisuke66/RideLab/issues), indiquez versions de l’application et d’Android, téléphone, modèle et firmware du home trainer/capteur, reproduction, services/protocole/plage et journaux : `RideLabGatt`, `RideLabWorkout`, `RideLabOverlay`. Retirez au besoin adresses et données personnelles avant partage. Ajoutez les tests pertinents et les traductions UI dans les sept langues.

Synchronisation cloud, comptes, FIT/TCX, PowerMatch et simulation de pente ne sont pas proposés actuellement. Les documents de vérification distinguent implémentation et validation physique.
