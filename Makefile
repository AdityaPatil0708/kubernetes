APPS         ?= node python java
VERSION      ?= v1
KIND_CLUSTER ?= kind
APP          ?= node

.PHONY: build load apply deploy release set-image restart status watch forward clean

## Build all three images at $(VERSION).
build:
	for a in $(APPS); do docker build -t k8s-demo/$$a-app:$(VERSION) apps/$$a-app || exit 1; done

## Side-load images into kind (no registry needed). Skip if your cluster pulls from a registry.
load:
	for a in $(APPS); do kind load docker-image k8s-demo/$$a-app:$(VERSION) --name $(KIND_CLUSTER) || exit 1; done

## Apply every ConfigMap, Deployment and Service.
apply:
	kubectl apply -R -f k8s/

deploy: build load apply status

## Point the running Deployments at $(VERSION) — this is the rolling update.
set-image:
	for a in $(APPS); do kubectl set image deploy/$$a-app $$a-app=k8s-demo/$$a-app:$(VERSION) || exit 1; done

## Build a new version and roll it out: make release VERSION=v2
release: build load set-image status

## Pick up edited ConfigMap values (env is only injected at pod start).
restart:
	kubectl rollout restart -f k8s/ -R

status:
	for a in $(APPS); do kubectl rollout status deploy/$$a-app --timeout=120s || exit 1; done
	kubectl get pods -l 'app in (node-app,python-app,java-app)' -o wide

watch:
	kubectl get pods -l 'app in (node-app,python-app,java-app)' -w

## Reach one app locally: make forward APP=python
forward:
	kubectl port-forward svc/$(APP)-app 8080:80

clean:
	kubectl delete -R -f k8s/ --ignore-not-found
