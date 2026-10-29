#!/usr/bin/env bash
# PiiWii Turbo Linux v1.0.0 TEST: tested, official Mint 22.3 Xfce ISO base.
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
BASE=linuxmint-22.3-xfce-64bit.iso
EXPECTED=45a835b5dddaf40e84d776549e0b19b3fbd49673b6cc6434ebddbfcd217df776
NAME=PiiWii-Turbo-Linux-1.0.0-TEST.iso
WORK="$PWD/.piiwii-v1"
OUT="$PWD/dist"
ROOT="$WORK/rootfs"
LIVE="$WORK/iso"
mkdir -p "$OUT" "$WORK"
cleanup() {
  set +e
  for d in run sys proc dev; do
    if mountpoint -q "$ROOT/$d"; then umount -R "$ROOT/$d"; fi
  done
  if [[ -e "$WORK/resolv.conf.orig" || -L "$WORK/resolv.conf.orig" ]]; then
    rm -f "$ROOT/etc/resolv.conf"
    cp -a "$WORK/resolv.conf.orig" "$ROOT/etc/resolv.conf"
  fi
  if [[ -e "$ROOT/usr/sbin/policy-rc.d" ]]; then rm -f "$ROOT/usr/sbin/policy-rc.d"; fi
  set -e
}
trap cleanup EXIT
echo '[1/8] Installer les outils et verifier l espace disque'
apt-get update -qq
apt-get install -y -qq --no-install-recommends xorriso squashfs-tools curl rsync gnupg ca-certificates
df -h .
free="$(df -Pk "$WORK" | awk 'NR==2 {print $4}')"
if ((free < 16*1024*1024)); then echo "Espace insuffisant: $free Kio" >&2; exit 2; fi
echo '[2/8] Telecharger et verifier l ISO Linux Mint officielle'
curl -fsSL --retry 4 https://pub.linuxmint.io/stable/22.3/sha256sum.txt -o "$WORK/sha256sum.txt"
grep -F "$EXPECTED" "$WORK/sha256sum.txt" | grep -F "$BASE"
curl -fL --retry 5 --retry-delay 8 --continue-at - "https://pub.linuxmint.io/stable/22.3/$BASE" -o "$WORK/$BASE"
printf '%s  %s\n' "$EXPECTED" "$WORK/$BASE" | sha256sum -c -
echo '[3/8] Extraire les fichiers et le systeme Linux'
mkdir -p "$LIVE" "$ROOT"
xorriso -osirrox on -indev "$WORK/$BASE" -extract / "$LIVE" >/dev/null
test -s "$LIVE/casper/filesystem.squashfs"
unsquashfs -no-progress -d "$ROOT" "$LIVE/casper/filesystem.squashfs" >/dev/null
test -x "$ROOT/usr/bin/ubiquity"
test -e "$ROOT/var/lib/dpkg/status"
echo '[4/8] Installer Google Chrome et Spotify dans le vrai systeme Live'
mkdir -p "$ROOT/etc/apt/keyrings" "$ROOT/tmp" "$ROOT/dev" "$ROOT/proc" "$ROOT/sys" "$ROOT/run"
if [[ -e "$ROOT/etc/resolv.conf" || -L "$ROOT/etc/resolv.conf" ]]; then
  cp -a "$ROOT/etc/resolv.conf" "$WORK/resolv.conf.orig"
fi
rm -f "$ROOT/etc/resolv.conf"
cp -L /etc/resolv.conf "$ROOT/etc/resolv.conf"
for d in dev proc sys run; do
  mount --rbind "/$d" "$ROOT/$d"
  mount --make-rslave "$ROOT/$d"
done
curl -fL --retry 4 --retry-delay 5 \
  https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
  -o "$ROOT/tmp/piiwii-chrome.deb"
curl -fsSL --retry 4 https://download.spotify.com/debian/pubkey_5384CE82BA52C83A.asc \
  | gpg --batch --yes --dearmor -o "$ROOT/etc/apt/keyrings/piiwii-spotify.gpg"
chmod 644 "$ROOT/etc/apt/keyrings/piiwii-spotify.gpg"
echo 'deb [arch=amd64 signed-by=/etc/apt/keyrings/piiwii-spotify.gpg] https://repository.spotify.com stable non-free' \
  > "$ROOT/etc/apt/sources.list.d/piiwii-spotify.list"
printf '#!/bin/sh\nexit 101\n' > "$ROOT/usr/sbin/policy-rc.d"
chmod 755 "$ROOT/usr/sbin/policy-rc.d"
chroot "$ROOT" apt-get update
chroot "$ROOT" apt-get install -y --no-install-recommends /tmp/piiwii-chrome.deb spotify-client
chroot "$ROOT" dpkg -s google-chrome-stable | grep -q '^Status: install ok installed$'
chroot "$ROOT" dpkg -s spotify-client | grep -q '^Status: install ok installed$'
echo '[5/8] Retirer Firefox uniquement apres verification de Chrome'
if chroot "$ROOT" dpkg -s firefox 2>/dev/null | grep -q '^Status: install ok installed$'; then
  chroot "$ROOT" apt-get purge -y firefox
fi
if chroot "$ROOT" dpkg -s firefox 2>/dev/null | grep -q '^Status: install ok installed$'; then
  echo 'Firefox reste installe' >&2; exit 3
fi
rm -f "$ROOT/tmp/piiwii-chrome.deb" "$ROOT/usr/sbin/policy-rc.d"
echo '[6/8] Personnaliser l interface et les comptes'
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
r=Path(sys.argv[1])
def put(name, content, executable=False):
    p=r/name
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(content,encoding='utf-8')
    if executable: p.chmod(0o755)
put('usr/share/piiwii-turbo/build-info',
    'PiiWii Turbo Linux 1.0.0 TEST: Chrome + Spotify + SoundRent\n')
put('etc/default/locale','LANG=fr_CH.UTF-8\nLANGUAGE=fr_CH:fr\n')
put('etc/timezone','Europe/Zurich\n')
put('etc/default/keyboard','XKBMODEL="pc105"\nXKBLAYOUT="ch"\nXKBVARIANT="fr"\nXKBOPTIONS=""\n')
put('etc/skel/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml', '''<?xml version="1.0"?>
<channel name="xsettings" version="1.0">
 <property name="Net" type="empty">
  <property name="ThemeName" type="string" value="Mint-Y-Dark"/>
  <property name="IconThemeName" type="string" value="Mint-Y-Blue"/>
 </property>
</channel>
''')
put('etc/skel/.config/xfce4/panel/whiskermenu-1.rc',
    'favorites=google-chrome.desktop,spotify.desktop,thunar.desktop,xfce-settings-manager.desktop,gnome-calculator.desktop\n')
put('etc/skel/.config/mimeapps.list','''[Default Applications]
x-scheme-handler/http=google-chrome.desktop
x-scheme-handler/https=google-chrome.desktop
text/html=google-chrome.desktop
application/xhtml+xml=google-chrome.desktop
''')
put('etc/xdg/xfce4/helpers.rc','WebBrowser=google-chrome\n')
# No misleading KaraFun Web shortcut: no native KaraFun application for Linux.
web=r/'usr/share/applications/karafun-piiwii.desktop'
if web.exists():web.unlink()
put('usr/local/sbin/piiwii-configure-soundrent', '''#!/bin/sh
set -eu
if ! getent passwd soundrent >/dev/null; then
  useradd -m -s /bin/bash -c SoundRent soundrent
  passwd -l soundrent >/dev/null 2>&1 || true
fi
if id -nG soundrent | tr ' ' '\\n' | grep -qx sudo; then
  echo 'SoundRent ne doit pas etre administrateur' >&2; exit 1
fi
install -d -o soundrent -g soundrent /home/soundrent/.config/xfce4/panel
printf '%s\\n' 'favorites=google-chrome.desktop,spotify.desktop' > /home/soundrent/.config/xfce4/panel/whiskermenu-1.rc
chown soundrent:soundrent /home/soundrent/.config/xfce4/panel/whiskermenu-1.rc
''',True)
# Ubiquity runs executable target-config hooks after copying to /target.
put('usr/lib/ubiquity/target-config/95piiwii', '''#!/bin/sh
set -eu
if [ -x /target/usr/local/sbin/piiwii-configure-soundrent ]; then
  chroot /target /usr/local/sbin/piiwii-configure-soundrent
fi
''',True)
# Choose SoundRent's own password safely after installation.
put('usr/local/bin/piiwii-activate-soundrent', '''#!/bin/sh
set -eu
if ! getent passwd soundrent >/dev/null; then
  echo 'SoundRent sera cree pendant l installation sur disque'; exit 1
fi
exec xfce4-terminal --title='Activer SoundRent' --command='sudo passwd soundrent'
''',True)
put('usr/share/applications/piiwii-activate-soundrent.desktop', '''[Desktop Entry]
Type=Application
Name=Activer SoundRent
Comment=Choisir un mot de passe distinct pour le compte SoundRent
Exec=/usr/local/bin/piiwii-activate-soundrent
Icon=system-users
Terminal=false
Categories=Settings;System;
''')
PY
test -x "$ROOT/usr/bin/ubiquity"
test -x "$ROOT/usr/lib/ubiquity/target-config/95piiwii"
test -x "$ROOT/usr/local/sbin/piiwii-configure-soundrent"
chroot "$ROOT" dpkg-query -W google-chrome-stable spotify-client | tee "$OUT/apps-installed.txt"
cleanup
trap - EXIT
echo '[7/8] Mettre a jour les metadonnees et compresser le nouveau Live'
chroot "$ROOT" dpkg-query -W > "$LIVE/casper/filesystem.manifest"
du -sx --block-size=1 "$ROOT" | awk '{print $1}' > "$LIVE/casper/filesystem.size"
rm -f "$LIVE/casper/filesystem.squashfs"
mksquashfs "$ROOT" "$LIVE/casper/filesystem.squashfs" \
  -noappend -comp xz -b 1M -processors 4 -no-progress >/dev/null
unsquashfs -cat "$LIVE/casper/filesystem.squashfs" usr/share/piiwii-turbo/build-info | grep '1.0.0 TEST'
(cd "$LIVE" && find . -type f ! -name md5sum.txt ! -path './boot.catalog' ! -path './isolinux/boot.cat' \
  -print0 | sort -z | xargs -0 md5sum > md5sum.txt)
echo '[8/8] Fabriquer et controler l ISO hybride BIOS/UEFI'
ISO="$OUT/$NAME"
xorriso -indev "$WORK/$BASE" -outdev "$ISO" -overwrite nondir \
  -map "$LIVE/casper/filesystem.squashfs" /casper/filesystem.squashfs \
  -map "$LIVE/casper/filesystem.manifest" /casper/filesystem.manifest \
  -map "$LIVE/casper/filesystem.size" /casper/filesystem.size \
  -map "$LIVE/md5sum.txt" /md5sum.txt \
  -boot_image any replay -volid PIIWII_TURBO_LINUX -commit >/dev/null
test -s "$ISO"
xorriso -indev "$ISO" -report_el_torito plain > "$OUT/boot-report.txt" 2>&1
grep -q -i 'El Torito' "$OUT/boot-report.txt"
sha256sum "$ISO" > "$OUT/$NAME.sha256"
stat -c 'ISO reelle : %n (%s octets)' "$ISO"
echo 'Image TEST — controle du demarrage et installation sur machine virtuelle encore requis.'
