#!/usr/bin/env bash

set -e


. tools/lib/lib.sh

assert_root

trap 'catch $? $LINENO' EXIT

restore_dir_structure () {
  echo "Reverting changes back"
  mv charts/airbyte/Chart.yaml charts/airbyte/Chart.yaml.test
  mv charts/airbyte/Chart.yaml.old charts/airbyte/Chart.yaml
  mv charts/airbyte/values.yaml charts/airbyte/values.yaml.test
  mv charts/airbyte/values.yaml.old charts/airbyte/values.yaml
}

catch () {
  echo "catching!"
  if [ "$1" != "0" ];
  then
    echo "Error $1 occurred on $2"
    restore_dir_structure
  fi
}


echo "Getting docker internal host ip"
DOCKER_HOST_IP=$(ip -f inet add show docker0 | sed -En -e 's/.*inet ([0-9.]+).*/\1/p')

echo "Patching coredns configmap NodeHosts with new entry for docker host"
kubectl patch configmap/coredns \
  -n kube-system \
  --type merge \
  -p '{"data":{"NodeHosts": "${DOCKER_HOST_IP} host.docker.internal" }}'

if [ -n "$CI" ]; then
echo "Deploying fluentbit"
helm repo add fluent https://fluent.github.io/helm-charts
helm repo update fluent
sed -i "s/PLACEHOLDER/${WORKFLOW_RUN_ID}/" tools/bin/fluent_values.yaml
helm upgrade --install --values tools/bin/fluent_values.yaml --set env[1].name="AWS_ACCESS_KEY_ID" --set env[1].value=$(echo "$AWS_S3_INTEGRATION_TEST_CREDS" | jq -r .aws_access_key_id) \
 --set env[2].name="AWS_SECRET_ACCESS_KEY" --set env[2].value=$(echo "$AWS_S3_INTEGRATION_TEST_CREDS" | jq -r .aws_secret_access_key) \
 --set env[3].name="AWS_S3_BUCKET" --set env[3].value=${AWS_S3_BUCKET} \
 --set env[4].name="SUITE_TYPE" --set env[4].value="helm-logs" \
 fluentbitci fluent/fluent-bit
fi

echo "Replacing default Chart.yaml and values.yaml with a test one"
mv charts/airbyte/Chart.yaml charts/airbyte/Chart.yaml.old
mv charts/airbyte/Chart.yaml.test charts/airbyte/Chart.yaml
mv charts/airbyte/values.yaml charts/airbyte/values.yaml.old
mv charts/airbyte/values.yaml.test charts/airbyte/values.yaml

echo "Starting app..."

echo "Check if kind cluster is running..."
sudo docker ps

echo "Applying dev-integration-test manifests to kubernetes..."
cd charts/airbyte && helm repo add bitnami https://charts.bitnami.com/bitnami && helm dep update && cd -
helm upgrade --install --debug airbyte charts/airbyte

echo "Waiting for server to be ready..."
kubectl wait --for=condition=Available deployment/airbyte-server --timeout=300s || (kubectl describe pods && exit 1)

echo "Scale up workers by 2"
kubectl scale --replicas=2 deployment airbyte-worker

echo "Listing nodes scheduled for pods..."
kubectl describe pods | grep "Name\|Node"


# allocates a lot of time to start kube. takes a while for postgres+temporal to work things out
sleep 120

if [ -n "$CI" ]; then
  server_logs () { kubectl logs deployment.apps/airbyte-server > /tmp/kubernetes_logs/server.txt; }
  worker_logs () { kubectl logs deployment.apps/airbyte-worker > /tmp/kubernetes_logs/worker.txt; }
  db_logs () { kubectl logs deployment.apps/airbyte-db > /tmp/kubernetes_logs/db.txt; }
  temporal_logs () { kubectl logs deployment.apps/airbyte-temporal > /tmp/kubernetes_logs/temporal.txt; }
  describe_pods () { kubectl describe pods > /tmp/kubernetes_logs/describe_pods.txt; }
  describe_nodes () { kubectl describe nodes > /tmp/kubernetes_logs/describe_nodes.txt; }
  write_all_logs () {
    server_logs;
    worker_logs;
    db_logs;
    temporal_logs;
    pod_sweeper_logs;
    describe_nodes;
    describe_pods;
  }
# Uncomment for debugging. Warning, this is verbose.
  # trap "mkdir -p /tmp/kubernetes_logs && write_all_logs" EXIT
fi

if [ -n "$CI" ]; then
  kubectl expose $(kubectl get po -l app.kubernetes.io/name=server -o name) --name exposed-server-svc --type NodePort --overrides '{ "apiVersion": "v1","spec":{"ports": [{"port":8001,"protocol":"TCP","targetPort":8001,"nodePort":8001}]}}'
else
  kubectl port-forward svc/airbyte-airbyte-server-svc 8001:8001 &
fi

echo "Running worker integration tests..."
KUBE=true ./gradlew :oss:airbyte-workers:integrationTest --scan

echo "Running e2e tests via gradle..."
# Note: we skip basic acceptance tests on local Kube.
KUBE=true SKIP_BASIC_ACCEPTANCE_TESTS=true USE_EXTERNAL_DEPLOYMENT=true ./gradlew -Dorg.gradle.caching=false :oss:airbyte-tests:acceptanceTest --scan

catch
