# PiiWii Timeline Extractor Windows 1.0.1

Application Windows qui récupère Google Maps Timeline sans lire les cookies ou jetons du navigateur.

## Fonctionnement
1. Installe automatiquement Java 17 (Temurin), Android SDK et une image Android 13 Google Play si nécessaire.
2. Crée un AVD local `PiiWiiTimeline`.
3. Applique rootAVD uniquement à cet Android virtuel.
4. Ouvre Google Maps dans l'émulateur.
5. L'utilisateur se connecte lui-même à Google et restaure sa sauvegarde « Vos trajets ».
6. Le programme surveille automatiquement la base `odlh-storage.db` jusqu'à stabilisation.
7. Il produit `Timeline.json`, une copie de la base et un ZIP dans `Documents\PiiWii Timeline`.

Aucun accès root n'est effectué sur le téléphone physique. Aucun mot de passe, cookie ou jeton Google n'est lu par le programme.

## Sources tierces
Le décodage `odlh_export.py` provient de `arkenoi/timeline-export` (licence MIT) et est intégré au build.
`rootAVD` est téléchargé à l'exécution depuis le dépôt `galihlasahido/rootAVD` et reste soumis à sa propre licence.

## Correctif 1.0.1
- remplace `adb wait-for-device` par une surveillance réelle de l'émulateur ;
- journalise la sortie de `emulator.exe` dans `%LOCALAPPDATA%\PiiWiiTimelineExtractor\emulator-startup.log` ;
- vérifie l'accélération Android ;
- essaie automatiquement trois modes de démarrage : accéléré/GPU auto, accéléré/rendu logiciel, puis CPU/rendu logiciel ;
- détecte immédiatement si l'émulateur s'est fermé au lieu d'attendre un timeout aveugle ;
- augmente les délais uniquement pour le mode logiciel lent.
