#!/usr/bin/env bash
# Etape 3 : reconstruction reelle d'une ISO Live Linux Mint.
set -Eeuo pipefail
BASE="linuxmint-22.3-xfce-64bit.iso"
EXPECTED="45a835b5dddaf40e84d776549e0b19b3fbd49673b6cc6434ebddbfcd217df776"
WORK="$PWD/.piiwii-iso"
OUT="$PWD/dist"
mkdir -p "$WORK" "$OUT"
echo '[1/7] Installer les outils ISO'
sudo apt-get update -qq
sudo apt-get install -y -qq --no-install-recommends xorriso squashfs-tools rsync curl python3
echo '[2/7] Verifier le stockage'
df -h "$WORK"
FREE=$(df -Pk "$WORK" | awk 'NR==2{print $4}')
test "$FREE" -gt 14680064 || { echo "ESPACE INSUFFISANT: $FREE Ko disponibles" >&2; exit 1; }
echo '[3/7] Telecharger la base Mint et verifier son authenticite'
curl -fL --retry 3 --connect-timeout 25 \
  https://pub.linuxmint.io/stable/22.3/sha256sum.txt -o "$WORK/sha256sum.txt"
grep -F "$EXPECTED" "$WORK/sha256sum.txt" | grep -F "$BASE"
curl -fL --retry 4 --retry-delay 8 --continue-at - \
  "https://pub.linuxmint.io/stable/22.3/$BASE" -o "$WORK/$BASE"
echo "$EXPECTED  $WORK/$BASE" | sha256sum -c -
echo '[4/7] Extraire le systeme Live'
mkdir -p "$WORK/iso" "$WORK/rootfs"
xorriso -osirrox on -indev "$WORK/$BASE" -extract / "$WORK/iso" >/dev/null
test -s "$WORK/iso/casper/filesystem.squashfs"
sudo unsquashfs -no-progress -d "$WORK/rootfs" "$WORK/iso/casper/filesystem.squashfs" >/dev/null
test -e "$WORK/rootfs/usr/bin/ubiquity"
echo '[5/7] Ajouter les reglages et la personnalisation dans le systeme'
sudo python3 - "$WORK/rootfs" <<'PY'
from pathlib import Path
import sys
r=Path(sys.argv[1])
def write(name,s,mode=None):
    p=r/name
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(s,encoding="utf-8")
    if mode: p.chmod(mode)
write("usr/share/piiwii-turbo/build-info","PiiWii Turbo Linux 0.9.0 - base de test ISO personnalisee\n")
write("etc/default/locale","LANG=fr_CH.UTF-8\nLANGUAGE=fr_CH:fr\n")
write("etc/timezone","Europe/Zurich\n")
write("etc/default/keyboard","XKBMODEL=pc105\nXKBLAYOUT=ch\nXKBVARIANT=fr\nXKBOPTIONS=\n")
write("etc/skel/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml",'''<?xml version="1.0"?>
<channel name="xsettings" version="1.0">
  <property name="Net" type="empty">
    <property name="ThemeName" type="string" value="Mint-Y-Dark"/>
    <property name="IconThemeName" type="string" value="Mint-Y-Blue"/>
  </property>
</channel>
''')
write("usr/share/applications/karafun-piiwii.desktop",'''[Desktop Entry]
Type=Application
Name=KaraFun Web
Exec=xdg-open https://www.karafun.com/fr-ch/web/
Icon=audio-input-microphone
Terminal=false
Categories=AudioVideo;Audio;
''')
write("etc/skel/.config/xfce4/panel/whiskermenu-1.rc",
 "favorites=firefox.desktop,spotify.desktop,karafun-piiwii.desktop,thunar.desktop,xfce-settings-manager.desktop\n")
print("Profils par defaut, theme sombre, KaraFun, locale et clavier installes")
PY
echo '[6/7] Recompresser le systeme personnalise'
sudo rm -f "$WORK/iso/casper/filesystem.squashfs"
sudo mksquashfs "$WORK/rootfs" "$WORK/iso/casper/filesystem.squashfs" \
  -noappend -comp xz -b 1M -processors 2 -no-progress >/dev/null
sudo unsquashfs -cat "$WORK/iso/casper/filesystem.squashfs" \
  usr/share/piiwii-turbo/build-info | grep 'PiiWii Turbo Linux'
(
 cd "$WORK/iso"
 find . -type f ! -name md5sum.txt ! -path './boot.catalog' ! -path './isolinux/boot.cat' \
  -print0 | sort -z | xargs -0 md5sum > md5sum.txt
)
echo '[7/7] Reconstruire et controler ISO hybride'
ISO="$OUT/PiiWii-Turbo-Linux-0.9.0-TEST.iso"
xorriso -indev "$WORK/$BASE" -outdev "$ISO" -overwrite nondir \
  -map "$WORK/iso/casper/filesystem.squashfs" /casper/filesystem.squashfs \
  -map "$WORK/iso/md5sum.txt" /md5sum.txt \
  -boot_image any replay -volid PIIWII_TURBO_LINUX -commit >/dev/null
test -s "$ISO"
xorriso -indev "$ISO" -report_el_torito plain > "$OUT/boot-report.txt" 2>&1
grep -i 'El Torito' "$OUT/boot-report.txt"
sha256sum "$ISO" > "$OUT/PiiWii-Turbo-Linux-0.9.0-TEST.iso.sha256"
stat -c 'Image ISO creee : %n (%s octets)' "$ISO"
echo 'ATTENTION : image de test, sans tous les composants de la version finale.'
