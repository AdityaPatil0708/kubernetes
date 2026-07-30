# Multi-language Kubernetes demo

Three containerized web services — **Node.js**, **Python**, **Java** — each deployed to Kubernetes
with a `Deployment`, `Service`, and `ConfigMap`. Runs multi-replica, and takes new image versions
via rolling updates with no dropped requests.

## The apps

All three are dependency-free (Node `http`, Python `http.server`, Java `com.sun.net.httpserver`) and
expose the same contract on port 8080:

| Route | Response |
|---|---|
| `GET /` | `{"app":"node-app","version":"1","greeting":"...","pod":"node-app-7d4f-x9k2"}` |
| `GET /healthz` | `200 ok` |

`greeting` and `version` come from the ConfigMap; `pod` is the container hostname. Those two fields
are what make the demos below visible — `pod` proves load balancing across replicas, `version`
proves the rolling update.

## Layout

```
apps/<lang>-app/       one source file + Dockerfile
k8s/<lang>/            configmap.yaml, deployment.yaml, service.yaml
Makefile               build / load / apply / release / status
```

## Prerequisites

Docker and `kubectl`, plus a cluster. Any cluster works; the `make load` target assumes
[kind](https://kind.sigs.k8s.io/) so locally-built images are usable without pushing to a registry.

```sh
kind create cluster            # or minikube start, or your own cluster
kubectl config current-context # confirm you are pointed at it
```

## Deploy

```sh
make deploy        # build → load into kind → apply → wait for rollout
make forward APP=node    # then: curl localhost:8080/
```

Individual steps: `make build`, `make load`, `make apply`, `make status`, `make clean`.
If your cluster pulls from a registry instead of kind, skip `make load`, push the
`k8s-demo/<lang>-app:v1` tags yourself, and set `imagePullPolicy: Always`.

## Demo 1 — load balancing across replicas

Each Deployment runs 3 replicas behind one ClusterIP Service. The `pod` field rotates:

```sh
make forward APP=node &
for i in $(seq 12); do curl -s localhost:8080/ | python3 -c 'import json,sys;print(json.load(sys.stdin)["pod"])'; done
```

## Demo 2 — scaling

```sh
kubectl scale deploy/python-app --replicas=5
make watch          # pods appear, become Ready, join the Service
kubectl scale deploy/python-app --replicas=3
```

## Demo 3 — rolling update with zero downtime

Bump `APP_VERSION` in the ConfigMaps, build a `v2` tag, and roll it out — while hammering the
service to prove nothing 5xx's or refuses a connection.

```sh
# terminal 1 — continuous traffic
make forward APP=node
# terminal 2 — watch for any non-200
while true; do curl -s -o /dev/null -w '%{http_code} ' localhost:8080/; sleep 0.2; done
# terminal 3 — roll it
sed -i '' 's/APP_VERSION: "1"/APP_VERSION: "2"/' k8s/*/configmap.yaml
kubectl apply -R -f k8s/
make release VERSION=v2
```

Terminal 2 stays all `200`. Two settings do that work:

- `maxUnavailable: 0` with `maxSurge: 1` — a new pod comes up *before* an old one goes away, so
  ready replicas never dip below 3.
- the `readinessProbe` on `/healthz` — the Service only sends traffic to a pod once it answers, so
  a still-booting pod (notably the JVM) never receives a request.

Roll back:

```sh
kubectl rollout undo deploy/node-app
kubectl rollout history deploy/node-app
```

### Changing config without changing the image

Env vars from a ConfigMap are injected at pod start, so editing a ConfigMap changes nothing on its
own:

```sh
kubectl apply -R -f k8s/   # ConfigMap updated
make restart               # kubectl rollout restart — pods pick up the new values
```

## Tradeoffs

- **The Java image uses a JDK base (~450 MB), not a JRE.** `CMD ["java", "App.java"]` runs the
  single-file source launcher (JEP 330), which needs a compiler at runtime but removes Maven,
  `javac`, and a multi-stage build entirely. Its probe timings in
  `k8s/java/deployment.yaml` are looser to cover the compile-on-boot delay. Switch to a
  multi-stage `javac` → `eclipse-temurin:25-jre` build if image size or start time starts to matter.
- **The three manifest sets are copies, not templates.** Three apps × three files is small enough
  that Helm or kustomize would add more to read than it removes. Add an overlay when there is a
  second environment to vary.
- **No Ingress, HPA, or namespace.** ClusterIP + `kubectl port-forward` covers local access; HPA
  needs the resource requests tuned against real load first.

## Running an app without Docker

```sh
APP_VERSION=1 GREETING="local" node   apps/node-app/server.js
APP_VERSION=1 GREETING="local" python3 apps/python-app/app.py
APP_VERSION=1 GREETING="local" java    apps/java-app/App.java
```
