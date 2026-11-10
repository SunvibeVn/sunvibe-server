#!/usr/bin/env bash
# Fills the env file of the one-VM test environment (W35a plan Task S3, D-8; scripts/ops/README.md "Test environment on
# one VM").
#
#   gen-secrets.sh <env-file>           create the file from the example when absent or empty (mode 600), fill every
#                                       <generated> value, and on a terminal ask for the <owner> mail login and key
#   gen-secrets.sh --check <env-file>   print each key with filled or missing; exit 1 while any is missing
#
# Each <generated> value comes from openssl rand in the length and encoding its consumer needs. A filled value is never
# overwritten, so a second run changes nothing. The script prints key names only, never a value.
set -euo pipefail
umask 077

EXAMPLE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../env" && pwd)/test-env.env.example"
readonly EXAMPLE
readonly OWNER_KEYS=(MAIL_USERNAME MAIL_PASSWORD)

usage() {
  sed -n '5,7p' "$0" | sed 's/^# \{0,1\}//' >&2
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
  local file="$1" key="$2" value="$3" tmp
  tmp="$(mktemp "$file.XXXXXX")"
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"
    if [[ "$line" == "$key="* ]]; then
      printf '%s=%s\n' "$key" "$value"
    else
      printf '%s\n' "$line"
    fi
  done < "$file" > "$tmp"
  chmod 600 "$tmp"
  mv -f "$tmp" "$file"
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
  local file="$1" key value other db_password=""
  if [ ! -s "$file" ]; then
    tr -d '\r' < "$EXAMPLE" > "$file"
    echo "created $file from the example"
  fi
  chmod 600 "$file"

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

[ $# -ge 1 ] || usage
if [ "$1" = "--check" ]; then
  [ $# -eq 2 ] || usage
  check "$2"
else
  [ $# -eq 1 ] && [[ "$1" != -* ]] || usage
  fill "$1"
fi
