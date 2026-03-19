#!/bin/bash

# =============================================================================
# Virtual Card Platform — Seed Script
# Creates 50 cards with unique cardholder names via the REST API
# =============================================================================
# Usage:
#   chmod +x create_cards.sh
#   ./create_cards.sh
#
# Optional overrides:
#   BASE_URL=http://localhost:9090 ./create_cards.sh
# =============================================================================

BASE_URL="${BASE_URL:-http://localhost:8080}"
ENDPOINT="$BASE_URL/api/v1/cards"
SUCCESS=0
FAILED=0

echo "============================================"
echo " Creating 50 virtual cards"
echo " Target: $ENDPOINT"
echo "============================================"

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

for i in "${!NAMES[@]}"; do
  NAME="${NAMES[$i]}"
  # Random balance between 100 and 5000
  BALANCE=$(( RANDOM % 4901 + 100 ))
  CARD_NUM=$(( i + 1 ))

  RESPONSE=$(curl --silent --show-error --write-out "\n%{http_code}" \
    --request POST "$ENDPOINT" \
    --header "Content-Type: application/json" \
    --data "{
      \"name\": \"$NAME\",
      \"initBalance\": $BALANCE
    }")

  HTTP_BODY=$(echo "$RESPONSE" | head -n -1)
  HTTP_STATUS=$(echo "$RESPONSE" | tail -n 1)

  if [ "$HTTP_STATUS" -eq 201 ]; then
    CARD_ID=$(echo "$HTTP_BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "[$CARD_NUM/50] Created | $NAME | balance: $BALANCE | id: $CARD_ID"
    SUCCESS=$(( SUCCESS + 1 ))
  else
    echo "[$CARD_NUM/50] FAILED  | $NAME | status: $HTTP_STATUS | response: $HTTP_BODY"
    FAILED=$(( FAILED + 1 ))
  fi

  # Small delay to avoid overwhelming the server
  sleep 0.1
done

echo "============================================"
echo " Done: $SUCCESS created, $FAILED failed"
echo "============================================"