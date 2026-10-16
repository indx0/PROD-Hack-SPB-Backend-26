#!/usr/bin/env bash
set -euo pipefail

cd /opt/prod-backend
trap 'rm -f /opt/prod-backend/.ci-docker/config.json' EXIT

if [[ $# -ne 1 || -z "$1" ]]; then
  echo "Usage: deploy.sh <image:commit-sha>" >&2
  exit 1
fi

if [[ ! -s .env ]]; then
  echo "Create /opt/prod-backend/.env before the first deployment" >&2
  exit 1
fi

if [[ ! -s .ci-docker/config.json ]]; then
  echo "Registry credentials were not uploaded by the deploy job" >&2
  exit 1
fi

chmod 600 .env .ci-docker/config.json
export DOCKER_CONFIG=/opt/prod-backend/.ci-docker
export APP_IMAGE="$1"

compose() {
  docker compose --env-file .env -f compose.prod.yaml "$@"
}

# Pull first: if Registry is unavailable, the running app is not restarted.
compose config --quiet
compose pull app

if ! compose up -d --wait --wait-timeout 180; then
  compose ps
  compose logs --no-color --tail 100 app postgres
  exit 1
fi

# The last successful image is also available for manual diagnostics.
printf 'APP_IMAGE=%s\n' "$APP_IMAGE" > .image.env
chmod 600 .image.env
compose ps
