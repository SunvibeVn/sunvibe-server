#!/usr/bin/env bash
# Fills the env file of the one-VM test environment (W35a plan Task S3, D-8; scripts/ops/README.md "Test environment on
# one VM").
#
#   gen-secrets.sh [--new <KEY>]... <env-file>
#       create the file from the example when absent or empty (mode 600), add every key of the example the file lacks
#       with the example's value, fill every <generated> value, and on a terminal ask for the <owner> mail login and key
#   gen-secrets.sh --check <env-file>   print each key with filled or missing; exit 1 while any is missing
#
# Each <generated> value comes from openssl rand in the length and encoding its consumer needs. A filled value is never
# overwritten, so a second run changes nothing. The script prints key names only, never a value.
# The example is the list of keys: a key removed from the file comes back with the example's value. But a <generated> key
# that an existing file lacks is refused (exit 1, nothing written) unless --new names it: a fresh value would not read the
# data stored with the old one (encrypted ID card numbers, hashes, sessions). Put the old line back, or, for a key the
# example gained, run again with --new <KEY>.
set -euo pipefail
umask 077

EXAMPLE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../env" && pwd)/test-env.env.example"
readonly EXAMPLE
readonly OWNER_KEYS=(MAIL_USERNAME MAIL_PASSWORD)
NEW_KEYS=()
# The copy set_value is writing; an interrupted run removes it rather than leave values in a second file
TMP_FILE=""
trap 'rm -f "${TMP_FILE:-}"' EXIT
trap 'exit 130' INT TERM

usage() {
  sed -n '5,8p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

is_placeholder() {
  [[ -z "$1" || "$1" =~ ^\<[^\>]*\>$ ]]
}

# KEY=VALUE lines of a file, CR removed, comments and blank lines left out
entries() {
  tr -d '\r' < "$1" | grep -E '^[A-Z0-9_]+=' || true
}

value_of() {
  # $1 file, $2 key: the value after the first =, empty when the key is absent (one reader, no pipe to break)
  awk -v prefix="$2=" '{ sub(/\r$/, "") } index($0, prefix) == 1 { print substr($0, length(prefix) + 1); exit }' "$1"
}

# openssl breaks base64 into lines of 64, with CRLF on Windows; the value is one line
base64_key() {
  openssl rand -base64 "$1" | tr -d '\r\n'
}

# A new value for one <generated> key, in the form its consumer reads
generate() {
  case "$1" in
    # JWTProvider: a base64 HMAC-SHA256 key of at least 256 bits
    JWT_SECRET) base64_key 64 ;;
    # application.yml app.crypto: base64, exactly 32 bytes each (the two must differ: see fill)
    ID_CARD_CRYPTO_KEY|ID_CARD_CRYPTO_INDEX_KEY) base64_key 32 ;;
    # application.yml app.auth.otp-hash-key and app.acquisition.hash-key: base64, at least 32 bytes
    AUTH_OTP_HASH_KEY|ACQUISITION_HASH_KEY) base64_key 32 ;;
    # Letters and digits only: nothing for Compose to expand or a JDBC URL to read
    DB_PASSWORD|POSTGRES_PASSWORD) openssl rand -hex 24 | tr -d '\r\n' ;;
    *) echo "gen-secrets.sh: no rule for the generated key $1" >&2; return 1 ;;
  esac
}

# Writes the file again with one key's value replaced; a temporary file in the same folder, renamed over it
set_value() {
  local file="$1" key="$2" value="$3" found=0
  TMP_FILE="$(mktemp "$file.XXXXXX")"
  {
    while IFS= read -r line || [ -n "$line" ]; do
      line="${line%$'\r'}"
      if [[ "$line" == "$key="* ]]; then
        printf '%s=%s\n' "$key" "$value"
        found=1
      else
        printf '%s\n' "$line"
      fi
    done < "$file"
    # A key the file lacks goes at the end
    [ "$found" -eq 1 ] || printf '%s=%s\n' "$key" "$value"
  } > "$TMP_FILE"
  chmod 600 "$TMP_FILE"
  mv -f "$TMP_FILE" "$file"
  TMP_FILE=""
}

check() {
  local file="$1" key line missing=0
  [ -f "$file" ] || { echo "gen-secrets.sh: no file $file" >&2; exit 2; }
  local -A have=()
  while IFS= read -r line; do have["${line%%=*}"]="${line#*=}"; done < <(entries "$file")
  # The keys of the example first, in its order, then any key only the file has
  local -a keys=()
  mapfile -t keys < <( { entries "$EXAMPLE"; entries "$file"; } | cut -d= -f1 | awk '!seen[$0]++')
  for key in "${keys[@]}"; do
    if [[ -v "have[$key]" ]] && ! is_placeholder "${have[$key]}"; then
      echo "$key filled"
    else
      echo "$key missing"
      missing=1
    fi
  done
  return "$missing"
}

fill() {
  local file="$1" key value other db_password="" existed=0
  if [ -s "$file" ]; then
    existed=1
  else
    tr -d '\r' < "$EXAMPLE" > "$file"
    echo "created $file from the example"
  fi
  chmod 600 "$file"

  # A key the file lacks comes in with the example's value (a placeholder is filled below). In an existing file a lacking
  # <generated> key is refused unless named with --new, or unless it is one of the two database password keys and the
  # other one holds the password.
  local -a absent=() refused=()
  mapfile -t absent < <(comm -23 <(entries "$EXAMPLE" | cut -d= -f1 | sort) <(entries "$file" | cut -d= -f1 | sort))
  for key in "${absent[@]}"; do
    [ "$existed" -eq 1 ] && [ "$(value_of "$EXAMPLE" "$key")" = "<generated>" ] || continue
    is_named_new "$key" && continue
    case "$key" in
      DB_PASSWORD) other=POSTGRES_PASSWORD ;;
      POSTGRES_PASSWORD) other=DB_PASSWORD ;;
      *) other="" ;;
    esac
    if [ -n "$other" ] && ! is_placeholder "$(value_of "$file" "$other")"; then continue; fi
    refused+=("$key")
  done
  if [ "${#refused[@]}" -gt 0 ]; then
    echo "gen-secrets.sh: ${refused[*]} missing from the existing env file; nothing was written. A new value would not" \
      "read the data stored with the old one: put the old line back, or, for a key the example gained, run again with" \
      "--new <KEY>." >&2
    exit 1
  fi
  for key in "${NEW_KEYS[@]}"; do
    if [ "$existed" -eq 1 ] && [[ " ${absent[*]} " != *" $key "* ]]; then
      echo "--new $key changes nothing: the file has that key"
    fi
  done
  for key in "${absent[@]}"; do
    set_value "$file" "$key" "$(value_of "$EXAMPLE" "$key")"
    echo "added $key from the example"
  done

  # One database password for the API and the database container; a filled one is copied to the other key
  for key in DB_PASSWORD POSTGRES_PASSWORD; do
    value="$(value_of "$file" "$key")"
    if ! is_placeholder "$value"; then db_password="$value"; fi
  done

  # The keys to fill, read before the file is rewritten
  local -a generated=()
  mapfile -t generated < <(entries "$file" | sed -n 's/^\([A-Z0-9_]*\)=<generated>$/\1/p')
  for key in "${generated[@]}"; do
    case "$key" in
      DB_PASSWORD|POSTGRES_PASSWORD)
        [ -n "$db_password" ] || db_password="$(generate "$key")"
        value="$db_password" ;;
      ID_CARD_CRYPTO_KEY|ID_CARD_CRYPTO_INDEX_KEY)
        if [ "$key" = ID_CARD_CRYPTO_KEY ]; then other=ID_CARD_CRYPTO_INDEX_KEY; else other=ID_CARD_CRYPTO_KEY; fi
        value="$(generate "$key")"
        while [ "$value" = "$(value_of "$file" "$other")" ]; do value="$(generate "$key")"; done ;;
      *)
        value="$(generate "$key")" ;;
    esac
    set_value "$file" "$key" "$value"
    echo "filled $key"
  done

  local -a left=()
  for key in "${OWNER_KEYS[@]}"; do
    if is_placeholder "$(value_of "$file" "$key")"; then left+=("$key"); fi
  done
  [ "${#left[@]}" -gt 0 ] || return 0
  if [ ! -t 0 ]; then
    echo "left ${left[*]} unfilled: no terminal; run this script again in your own SSH terminal to type them"
    return 0
  fi
  for key in "${left[@]}"; do
    while :; do
      read -r -s -p "$key (hidden): " value
      echo
      # Letters, digits and @ . _ + - only: a Brevo SMTP login and key need nothing else, and Compose expands nothing
      if [[ "$value" =~ ^[A-Za-z0-9@._+-]+$ ]]; then break; fi
      echo "not taken: empty, or a character other than letters, digits and @ . _ + -; type it again"
    done
    set_value "$file" "$key" "$value"
    echo "filled $key"
  done
}

is_named_new() {
  local named
  for named in "${NEW_KEYS[@]}"; do [ "$named" != "$1" ] || return 0; done
  return 1
}

mode=fill
target=""
while [ $# -gt 0 ]; do
  case "$1" in
    --check) mode=check; shift ;;
    --new)
      [ $# -ge 2 ] && [[ "$2" =~ ^[A-Z0-9_]+$ ]] || usage
      NEW_KEYS+=("$2"); shift 2 ;;
    -*) usage ;;
    *) [ -z "$target" ] || usage; target="$1"; shift ;;
  esac
done
[ -n "$target" ] || usage
# --new names a <generated> key of the example, nothing else
for named in "${NEW_KEYS[@]}"; do
  [ "$(value_of "$EXAMPLE" "$named")" = "<generated>" ] || usage
done
if [ "$mode" = check ]; then
  [ "${#NEW_KEYS[@]}" -eq 0 ] || usage
  check "$target"
else
  fill "$target"
fi
