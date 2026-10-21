#!/usr/bin/env bash
# After a failed device run, report crashes and Tyst's log lines as a GitHub annotation.
log=$(adb logcat -d -v brief 2>/dev/null | grep -E "AndroidRuntime|FATAL|tyst|Tyst|InputMethodService|InlineSuggestion" | tail -80)
enc() { sed -e 's/%/%25/g' | awk '{printf "%s%%0A", $0}'; }
echo "::error title=device log::$(printf '%s\n' "$log" | enc)"
