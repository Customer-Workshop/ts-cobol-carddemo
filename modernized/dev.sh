#!/usr/bin/env bash
# Start the full CardDemo modernized stack for local development:
# PostgreSQL (docker) -> migrate + seed -> backend (:3000) -> frontend (:5173).
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f backend/.env ]; then
  cp backend/.env.example backend/.env
  echo "created backend/.env from .env.example"
fi

echo "==> starting postgres"
docker compose up -d postgres
until docker exec carddemo-postgres pg_isready -U carddemo -d carddemo >/dev/null 2>&1; do
  sleep 1
done

echo "==> installing dependencies"
npm install

echo "==> migrate + seed"
npm run db:migrate
npm run db:seed

echo "==> building shared + backend"
npm run build -w @carddemo/shared
npm run build -w @carddemo/backend

echo "==> starting backend on :3000 and frontend on :5173"
set -a; source backend/.env; set +a
node backend/dist/src/main.js &
BACKEND_PID=$!
trap 'kill $BACKEND_PID' EXIT

VITE_USE_MOCKS=false VITE_API_URL=http://localhost:3000 npm run dev -w @carddemo/frontend
