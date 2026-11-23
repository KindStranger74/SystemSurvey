#!/usr/bin/env bash
# Builds jars/SystemSurvey.jar with the game's JDK and deploys the mod to the game's mods folder.
# Usage: ./build.sh [--no-deploy]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
GAME="${GAME:-C:/Games/Starsector}"
CORE="$GAME/starsector-core"
JDK="$GAME/jre/bin"

CP="$GAME/mods/PatchLib/jars/PatchLibAPI.jar;$CORE/starfarer.api.jar;$CORE/starfarer_obf.jar;$CORE/fs.common_obf.jar;$CORE/lwjgl.jar;$CORE/lwjgl_util.jar;$CORE/json.jar;$CORE/log4j-1.2.9.jar"

rm -rf "$HERE/build/classes"
mkdir -p "$HERE/build/classes" "$HERE/jars"
"$JDK/javac.exe" --release 17 -implicit:none -sourcepath "" -encoding UTF-8 -Xlint:-options -cp "$CP" -d "$HERE/build/classes" $(find "$HERE/src" -name '*.java')
"$JDK/jar.exe" cf "$HERE/jars/SystemSurvey.jar" -C "$HERE/build/classes" .
echo "built jars/SystemSurvey.jar"

if [ "${1:-}" != "--no-deploy" ]; then
	DEST="$GAME/mods/SystemSurvey"
	rm -rf "$DEST"
	mkdir -p "$DEST"
	cp -r "$HERE/mod_info.json" "$HERE/data" "$HERE/jars" "$DEST/"
	echo "deployed to $DEST"
fi
