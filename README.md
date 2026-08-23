# Bobby Gallery — dépôt de mises à jour

Ce dépôt public est utilisé par **Bobby Gallery** pour les mises à jour automatiques.

## Fonctionnement

1. La nouvelle APK doit garder exactement le package `ch.piiwii.bobbygallery2` et la même signature.
2. Le `versionCode` doit augmenter à chaque version.
3. Le fichier doit être nommé exactement `Bobby-Gallery-X.Y.Z.apk`.
4. L'APK est placée dans `incoming/` puis poussée sur `main`.
5. GitHub Actions vérifie le package, la signature, le nom et les versions, sélectionne l'APK au `versionCode` le plus élevé, calcule le SHA-256, génère `update.json`, crée/met à jour la Release `vX.Y.Z`, la marque **Latest** et publie l'APK sous le nom fixe `Bobby-Gallery.apk`.

## URL consultée par l'application

`https://raw.githubusercontent.com/piiwii1/bobby-gallery-updates/main/update.json`

## APK Latest

`https://github.com/piiwii1/bobby-gallery-updates/releases/latest/download/Bobby-Gallery.apk`

## Fichiers volumineux

Les APK Bobby Gallery dépassent actuellement 100 Mo. Le dossier `incoming/*.apk` est donc configuré avec **Git LFS**. Le plus simple pour déposer une APK est d'utiliser GitHub Desktop : cloner ce dépôt, copier l'APK dans `incoming/`, puis Commit + Push.
