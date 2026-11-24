#!/usr/bin/env bash
# Uploads a bundle from scripts/central-bundle.sh to Maven Central's Portal and waits until Central has published it.
# A version Central already has is skipped, so a rerun passes. Needs MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_PASSWORD,
# a Portal user token (RELEASING.md). Usage: scripts/central-publish.sh <bundle.zip> <version>
set -euo pipefail
bundle="$1"; version="$2"
api=https://central.sonatype.com/api/v1/publisher
auth="Authorization: Bearer $(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')"
missing=0
for artifact in $(unzip -Z1 "$bundle" | sed -n 's#^bot/ouro/ui/\([^/]*\)/.*\.pom$#\1#p' | sort -u); do
  if curl -fsS -H "$auth" "$api/published?namespace=bot.ouro.ui&name=$artifact&version=$version" | grep -q '"published":true'; then
    echo "bot.ouro.ui:$artifact:$version is already on Maven Central."
  else
    missing=1
  fi
done
if [ "$missing" = 0 ]; then echo "Every artifact is already published, so nothing is uploaded."; exit 0; fi
id="$(curl -fsS -H "$auth" -F "bundle=@$bundle" "$api/upload?publishingType=AUTOMATIC&name=ouro-ui-android-v$version")"
echo "Deployment $id uploaded; waiting for Central to validate and publish it."
for _ in $(seq 1 160); do
  status="$(curl -fsS -X POST -H "$auth" "$api/status?id=$id")"
  state="$(printf '%s' "$status" | python3 -c 'import json,sys; print(json.load(sys.stdin)["deploymentState"])')"
  echo "$(date -u +%H:%M:%S) $state"
  case "$state" in
    PUBLISHED) echo "Published: https://central.sonatype.com/namespace/bot.ouro.ui"; exit 0 ;;
    FAILED) echo "::error::Central refused deployment $id. Fix what it lists, then rerun this job:"; printf '%s\n' "$status" | python3 -m json.tool; exit 1 ;;
  esac
  sleep 15
done
echo "::error::Deployment $id was not published within 40 minutes. Fix: check https://central.sonatype.com/publishing/deployments, then rerun this job (a published version is skipped)."
exit 1
