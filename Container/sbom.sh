#!/bin/bash
# SPDX-FileCopyrightText: 2026 interactive instruments GmbH
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Stückliste des Image (SBOM, CycloneDX) mit syft: Pakete des Basis-Image, Java-
# Bibliotheken in Tomcat und Webanwendung. Die nativen Bibliotheken der OKLABI unter
# /opt/lib erkennt syft nicht; sie stehen mit Version und Lizenz im Lizenzbericht.
# Die Stückliste der Webanwendung aus dem Gradle-Bau liegt im Image unter
# /opt/vermdb/bom.json.
#
#   Container/sbom.sh [Image] [Ausgabe]
#
# Vorgabe: vermdb:latest nach Container/build/vermdb-image.cdx.json
set -euo pipefail
cd "$(dirname "$0")"
BILD="${1:-vermdb:latest}"
AUS="${2:-build/vermdb-image.cdx.json}"
SYFT=anchore/syft:v1.57.2@sha256:e97e20b6e6d1699c60296b66ab337941724f9bd06ac279d893eda89601fc4b2a

mkdir -p "$(dirname "$AUS")"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
docker save --platform linux/amd64 -o "$TMP/bild.tar" "$BILD"
docker run --rm -v "$TMP:/w:ro" "$SYFT" docker-archive:/w/bild.tar -q -o cyclonedx-json > "$AUS"
echo "Stückliste: $AUS"
