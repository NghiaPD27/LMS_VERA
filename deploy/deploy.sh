#!/usr/bin/env bash
set -euo pipefail

APP_NAME="lms-vera-backend"
BLUE_COLOR="blue"
GREEN_COLOR="green"
BLUE_PORT="8081"
GREEN_PORT="8082"
CONTAINER_PORT="8080"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-90}"
HEALTH_PATH="${HEALTH_PATH:-/actuator/health}"
NGINX_UPSTREAM_PATH="${NGINX_UPSTREAM_PATH:-/etc/nginx/conf.d/lms-vera-backend-upstream.conf}"

IMAGE="${1:-}"
if [[ -z "$IMAGE" ]]; then
  echo "Usage: deploy.sh <docker-image>"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
STATE_FILE="$APP_ROOT/current_color"
TEMPLATE_FILE="$SCRIPT_DIR/nginx-lms-vera-upstream.conf.template"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE"
  exit 1
fi

if [[ ! -f "$TEMPLATE_FILE" ]]; then
  echo "Missing Nginx upstream template: $TEMPLATE_FILE"
  exit 1
fi

if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
  SUDO=""
else
  SUDO="sudo"
fi

container_name() {
  echo "${APP_NAME}-$1"
}

port_for_color() {
  case "$1" in
    "$BLUE_COLOR") echo "$BLUE_PORT" ;;
    "$GREEN_COLOR") echo "$GREEN_PORT" ;;
    *) echo "Unknown color: $1" >&2; exit 1 ;;
  esac
}

opposite_color() {
  case "$1" in
    "$BLUE_COLOR") echo "$GREEN_COLOR" ;;
    "$GREEN_COLOR") echo "$BLUE_COLOR" ;;
    *) echo "$BLUE_COLOR" ;;
  esac
}

container_exists() {
  docker ps -a --format '{{.Names}}' | grep -Fxq "$(container_name "$1")"
}

container_health() {
  local color="$1"
  local name
  name="$(container_name "$color")"
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$name" 2>/dev/null || true
}

is_container_healthy() {
  local color="$1"
  [[ "$(container_health "$color")" == "healthy" ]]
}

http_health_ok() {
  local port="$1"
  if command -v curl >/dev/null 2>&1; then
    curl -fsS "http://127.0.0.1:${port}${HEALTH_PATH}" >/dev/null 2>&1
  elif command -v wget >/dev/null 2>&1; then
    wget -qO- "http://127.0.0.1:${port}${HEALTH_PATH}" >/dev/null 2>&1
  else
    return 1
  fi
}

wait_for_healthy() {
  local color="$1"
  local port
  local deadline
  local status
  port="$(port_for_color "$color")"
  deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))

  while (( SECONDS < deadline )); do
    status="$(container_health "$color")"
    if [[ "$status" == "healthy" ]] || http_health_ok "$port"; then
      echo "Container $(container_name "$color") is healthy on port $port"
      return 0
    fi
    if [[ "$status" == "exited" || "$status" == "dead" ]]; then
      echo "Container $(container_name "$color") stopped before becoming healthy"
      return 1
    fi
    sleep 3
  done

  echo "Timed out waiting for $(container_name "$color") to become healthy"
  return 1
}

detect_active_color() {
  if [[ -f "$STATE_FILE" ]]; then
    local saved_color
    saved_color="$(tr -d '[:space:]' < "$STATE_FILE")"
    if [[ "$saved_color" == "$BLUE_COLOR" || "$saved_color" == "$GREEN_COLOR" ]]; then
      if container_exists "$saved_color" && is_container_healthy "$saved_color"; then
        echo "$saved_color"
        return 0
      fi
    fi
  fi

  if container_exists "$BLUE_COLOR" && is_container_healthy "$BLUE_COLOR"; then
    echo "$BLUE_COLOR"
  elif container_exists "$GREEN_COLOR" && is_container_healthy "$GREEN_COLOR"; then
    echo "$GREEN_COLOR"
  else
    echo ""
  fi
}

render_nginx_upstream() {
  local port="$1"
  local output_file="$2"
  sed "s/{{UPSTREAM_PORT}}/${port}/g" "$TEMPLATE_FILE" > "$output_file"
}

cleanup_new_container() {
  local color="$1"
  if container_exists "$color"; then
    docker rm -f "$(container_name "$color")" >/dev/null 2>&1 || true
  fi
}

env_file_value() {
  local key="$1"
  awk -v key="$key" '
    $0 ~ "^[[:space:]]*" key "[[:space:]]*=" {
      value = $0
      sub(/^[^=]*=/, "", value)
      sub(/\r$/, "", value)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      gsub(/^"|"$/, "", value)
      print value
      exit
    }
  ' "$ENV_FILE"
}

DOCKER_NETWORK="${DOCKER_NETWORK:-$(env_file_value DOCKER_NETWORK)}"
DOCKER_NETWORK="${DOCKER_NETWORK:-$(env_file_value APP_DOCKER_NETWORK)}"

if [[ -n "$DOCKER_NETWORK" ]]; then
  if ! docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1; then
    echo "Docker network not found: $DOCKER_NETWORK"
    echo "Create it on the VPS first, then attach PostgreSQL to that network."
    exit 1
  fi
fi

echo "Pulling image: $IMAGE"
docker pull "$IMAGE"

ACTIVE_COLOR="$(detect_active_color)"
if [[ -z "$ACTIVE_COLOR" ]]; then
  NEW_COLOR="$BLUE_COLOR"
  OLD_COLOR=""
  echo "No healthy active container detected. First deploy will use blue."
else
  NEW_COLOR="$(opposite_color "$ACTIVE_COLOR")"
  OLD_COLOR="$ACTIVE_COLOR"
  echo "Active color: $ACTIVE_COLOR. Deploying new color: $NEW_COLOR."
fi

NEW_CONTAINER="$(container_name "$NEW_COLOR")"
NEW_PORT="$(port_for_color "$NEW_COLOR")"

cleanup_new_container "$NEW_COLOR"

echo "Starting $NEW_CONTAINER on 127.0.0.1:$NEW_PORT"
DOCKER_RUN_ARGS=(
  docker run -d
  --name "$NEW_CONTAINER"
  --restart unless-stopped
  --env-file "$ENV_FILE"
  -p "127.0.0.1:${NEW_PORT}:${CONTAINER_PORT}"
)

if [[ -n "$DOCKER_NETWORK" ]]; then
  echo "Attaching $NEW_CONTAINER to Docker network: $DOCKER_NETWORK"
  DOCKER_RUN_ARGS+=(--network "$DOCKER_NETWORK")
fi

DOCKER_RUN_ARGS+=("$IMAGE")
"${DOCKER_RUN_ARGS[@]}" >/dev/null

if ! wait_for_healthy "$NEW_COLOR"; then
  echo "New container failed health check. Keeping old color: ${OLD_COLOR:-none}."
  docker logs --tail 120 "$NEW_CONTAINER" || true
  cleanup_new_container "$NEW_COLOR"
  exit 1
fi

TMP_UPSTREAM="$(mktemp)"
BACKUP_UPSTREAM="$(mktemp)"
HAD_EXISTING_UPSTREAM="false"

if [[ -f "$NGINX_UPSTREAM_PATH" ]]; then
  $SUDO cp "$NGINX_UPSTREAM_PATH" "$BACKUP_UPSTREAM"
  HAD_EXISTING_UPSTREAM="true"
fi

render_nginx_upstream "$NEW_PORT" "$TMP_UPSTREAM"
$SUDO mkdir -p "$(dirname "$NGINX_UPSTREAM_PATH")"
$SUDO cp "$TMP_UPSTREAM" "$NGINX_UPSTREAM_PATH"

if ! $SUDO nginx -t; then
  echo "Nginx config test failed. Rolling back upstream config."
  if [[ "$HAD_EXISTING_UPSTREAM" == "true" ]]; then
    $SUDO cp "$BACKUP_UPSTREAM" "$NGINX_UPSTREAM_PATH"
  else
    $SUDO rm -f "$NGINX_UPSTREAM_PATH"
  fi
  cleanup_new_container "$NEW_COLOR"
  rm -f "$TMP_UPSTREAM" "$BACKUP_UPSTREAM"
  exit 1
fi

if ! $SUDO systemctl reload nginx; then
  echo "Nginx reload failed. Rolling back upstream config."
  if [[ "$HAD_EXISTING_UPSTREAM" == "true" ]]; then
    $SUDO cp "$BACKUP_UPSTREAM" "$NGINX_UPSTREAM_PATH"
  else
    $SUDO rm -f "$NGINX_UPSTREAM_PATH"
  fi
  cleanup_new_container "$NEW_COLOR"
  rm -f "$TMP_UPSTREAM" "$BACKUP_UPSTREAM"
  exit 1
fi

echo "$NEW_COLOR" > "$STATE_FILE"

if [[ -n "$OLD_COLOR" ]] && container_exists "$OLD_COLOR"; then
  echo "Stopping old container: $(container_name "$OLD_COLOR")"
  docker rm -f "$(container_name "$OLD_COLOR")" >/dev/null 2>&1 || true
fi

rm -f "$TMP_UPSTREAM" "$BACKUP_UPSTREAM"
echo "Deploy complete. Active color: $NEW_COLOR. Upstream port: $NEW_PORT."
