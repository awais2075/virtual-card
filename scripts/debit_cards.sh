#!/bin/bash

# =============================================================================
# Virtual Card Platform — Debit Seed Script
# Fetches all cards, runs successful debits and intentional declines
# =============================================================================
# Usage:
#   chmod +x debit_cards.sh
#   ./debit_cards.sh
#
# Optional overrides:
#   BASE_URL=http://localhost:9090 ./debit_cards.sh
# =============================================================================

BASE_URL="${BASE_URL:-http://localhost:8080}"
CARDS_ENDPOINT="$BASE_URL/api/v1/cards"

SUCCESS=0
DECLINED=0
FAILED=0
CARD_NUM=0

echo "============================================"
echo " Virtual Card Debit Seed Script"
echo " Target: $BASE_URL"
echo "============================================"

# -----------------------------------------------------------------------------
# Step 1 — Fetch all card IDs and balances by creating known cards
#           We use the names from create_cards.sh to look up their IDs
#           by hitting GET /api/v1/cards — but since there is no list endpoint,
#           we store IDs during creation. Instead we re-create a known set
#           and grab IDs from the 409 conflict response or create new ones.
#
#           Simpler approach: accept card IDs as input or read from a file.
#           This script reads from cards.txt if it exists (produced by
#           create_cards.sh), otherwise prompts you to provide IDs.
# -----------------------------------------------------------------------------

# Check if cards.txt exists from a previous run
if [ ! -f "cards.txt" ]; then
  echo ""
  echo " cards.txt not found."
  echo " Re-running card creation to capture IDs..."
  echo ""

  NAMES=(
    "alice johnson"     "bob smith"         "charlie brown"     "diana prince"
    "evan rogers"       "fiona green"       "george harris"     "hannah white"
    "ivan black"        "julia roberts"     "kevin hart"        "laura palmer"
    "michael scott"     "nina simone"       "oscar wilde"       "patricia lane"
    "quentin blake"     "rachel green"      "samuel jackson"    "tina turner"
    "ulysses grant"     "victoria beckett"  "walter white"      "xena warrior"
    "yasmine ali"       "zachary taylor"    "amber rose"        "ben harper"
    "claire danes"      "derek morgan"      "elena fisher"      "frank castle"
    "grace kelly"       "henry ford"        "isla fisher"       "james bond"
    "karen page"        "liam neeson"       "mia wallace"       "noah cross"
    "olivia pope"       "peter parker"      "quinn fabray"      "rick grimes"
    "sarah connor"      "thomas shelby"     "uma thurman"       "victor stone"
    "wendy darling"     "xavier woods"
  )

  > cards.txt

  for NAME in "${NAMES[@]}"; do
    BALANCE=$(( RANDOM % 4901 + 100 ))
    RESPONSE=$(curl --silent --write-out "\n%{http_code}" \
      --request POST "$CARDS_ENDPOINT" \
      --header "Content-Type: application/json" \
      --data "{\"name\": \"$NAME\", \"initBalance\": $BALANCE}")

    HTTP_BODY=$(echo "$RESPONSE" | head -n -1)
    HTTP_STATUS=$(echo "$RESPONSE" | tail -n 1)

    if [ "$HTTP_STATUS" -eq 201 ]; then
      CARD_ID=$(echo "$HTTP_BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
      CARD_BALANCE=$(echo "$HTTP_BODY" | grep -o '"balance":[0-9.]*' | head -1 | cut -d':' -f2)
      echo "$CARD_ID $CARD_BALANCE $NAME" >> cards.txt
      echo " Created: $NAME | balance: $CARD_BALANCE | id: $CARD_ID"
    else
      echo " Skipped (already exists): $NAME"
    fi
    sleep 0.1
  done

  echo ""
  echo " Card IDs saved to cards.txt"
  echo "============================================"
fi

# -----------------------------------------------------------------------------
# Step 2 — Run debit transactions: mix of successful and declined
# -----------------------------------------------------------------------------

echo ""
echo " Running debit transactions..."
echo ""

while IFS=' ' read -r CARD_ID BALANCE NAME_PARTS; do
  CARD_NUM=$(( CARD_NUM + 1 ))
  NAME="$NAME_PARTS"

  # --- Scenario A: Normal successful debit (50% of balance) ---
  DEBIT_AMOUNT=$(echo "$BALANCE * 0.5" | bc | xargs printf "%.2f")
  IDEM_KEY_A="debit-success-$(echo "$CARD_ID" | cut -c1-8)-$CARD_NUM"

  RESPONSE_A=$(curl --silent --write-out "\n%{http_code}" \
    --request POST "$CARDS_ENDPOINT/$CARD_ID/debit" \
    --header "Content-Type: application/json" \
    --data "{
      \"amount\": $DEBIT_AMOUNT,
      \"idempotencyKey\": \"$IDEM_KEY_A\"
    }")

  STATUS_A=$(echo "$RESPONSE_A" | tail -n 1)
  BODY_A=$(echo "$RESPONSE_A" | head -n -1)

  if [ "$STATUS_A" -eq 200 ]; then
    TX_STATUS=$(echo "$BODY_A" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    BAL_AFTER=$(echo "$BODY_A" | grep -o '"balanceAfter":[0-9.]*' | head -1 | cut -d':' -f2)
    echo "[$CARD_NUM] SUCCESS  | $NAME | debit: $DEBIT_AMOUNT | balance after: $BAL_AFTER | tx: $TX_STATUS"
    SUCCESS=$(( SUCCESS + 1 ))
  else
    echo "[$CARD_NUM] ERROR    | $NAME | debit: $DEBIT_AMOUNT | status: $STATUS_A"
    FAILED=$(( FAILED + 1 ))
  fi

  sleep 0.05

  # --- Scenario B: Intentional decline — amount exceeds balance ---
  # Use a very large amount guaranteed to exceed any card balance
  LARGE_AMOUNT="99999.00"
  IDEM_KEY_B="debit-decline-$(echo "$CARD_ID" | cut -c1-8)-$CARD_NUM"

  RESPONSE_B=$(curl --silent --write-out "\n%{http_code}" \
    --request POST "$CARDS_ENDPOINT/$CARD_ID/debit" \
    --header "Content-Type: application/json" \
    --data "{
      \"amount\": $LARGE_AMOUNT,
      \"idempotencyKey\": \"$IDEM_KEY_B\"
    }")

  STATUS_B=$(echo "$RESPONSE_B" | tail -n 1)
  BODY_B=$(echo "$RESPONSE_B" | head -n -1)
  ERROR_MSG=$(echo "$BODY_B" | grep -o '"message":"[^"]*"' | head -1 | cut -d'"' -f4)

  if [ "$STATUS_B" -eq 422 ]; then
    echo "[$CARD_NUM] DECLINED | $NAME | debit: $LARGE_AMOUNT | reason: $ERROR_MSG"
    DECLINED=$(( DECLINED + 1 ))
  else
    echo "[$CARD_NUM] UNEXPECTED | $NAME | status: $STATUS_B | body: $BODY_B"
    FAILED=$(( FAILED + 1 ))
  fi

  sleep 0.05

  # --- Scenario C: Idempotency check — repeat the successful debit with same key ---
  RESPONSE_C=$(curl --silent --write-out "\n%{http_code}" \
    --request POST "$CARDS_ENDPOINT/$CARD_ID/debit" \
    --header "Content-Type: application/json" \
    --data "{
      \"amount\": $DEBIT_AMOUNT,
      \"idempotencyKey\": \"$IDEM_KEY_A\"
    }")

  STATUS_C=$(echo "$RESPONSE_C" | tail -n 1)
  BODY_C=$(echo "$RESPONSE_C" | head -n -1)
  TX_ID_A=$(echo "$BODY_A" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  TX_ID_C=$(echo "$BODY_C" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

  if [ "$STATUS_C" -eq 200 ] && [ "$TX_ID_A" = "$TX_ID_C" ]; then
    echo "[$CARD_NUM] IDEMPOTENT | $NAME | same tx returned: $TX_ID_C"
  else
    echo "[$CARD_NUM] IDEMPOTENCY FAIL | $NAME | status: $STATUS_C"
  fi

  sleep 0.05

done < cards.txt

echo ""
echo "============================================"
echo " Debit run complete"
echo " Successful : $SUCCESS"
echo " Declined   : $DECLINED"
echo " Errors     : $FAILED"
echo "============================================"