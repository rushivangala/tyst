#!/usr/bin/env bash
# Runs Gradle and, on failure, turns compiler errors, build failures and failing tests into
# GitHub annotations so they're readable without downloading logs.
set -o pipefail
log="$(mktemp)"
./gradlew "$@" --stacktrace 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  summary=$(grep -E '^e: |What went wrong|^> |FAILED|Exception|error:|Error:|Lint found|INSTALL_|Tests on |No connected' "$log" \
    | grep -vE '^> Task |at org.gradle|Run with --|Get more help|Problems report' | head -60)
  find . \( -path '*/test-results/*' -o -path '*/androidTest-results/*' \) -name '*.xml' 2>/dev/null | while IFS= read -r xml; do
    python3 - "$xml" <<'PY' >> "$log.tests"
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
for case in root.iter('testcase'):
    for f in list(case.findall('failure')) + list(case.findall('error')):
        msg = (f.get('message') or f.text or '').strip().splitlines()
        print(f"{case.get('classname','').split('.')[-1]}.{case.get('name')}: {' | '.join(msg[:8])[:900]}")
PY
  done
  find . -name 'lint-results-*.txt' 2>/dev/null | while IFS= read -r lint; do grep -E ': Error:' "$lint" | head -30 >> "$log.lint"; done
  enc() { sed -e 's/%/%25/g' | awk '{printf "%s%%0A", $0}'; }
  echo "::error title=gradle $*::$(printf '%s\n' "$summary" | enc)"
  [ -s "$log.tests" ] && echo "::error title=failing tests::$(head -40 "$log.tests" | enc)"
  [ -s "$log.lint" ] && echo "::error title=lint errors::$(cat "$log.lint" | enc)"
fi
exit "$status"
