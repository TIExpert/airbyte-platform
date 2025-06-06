# featureflag-server

![Version: 0.67.17](https://img.shields.io/badge/Version-0.67.17-informational?style=flat-square) ![Type: application](https://img.shields.io/badge/Type-application-informational?style=flat-square) ![AppVersion: dev](https://img.shields.io/badge/AppVersion-dev-informational?style=flat-square)

Helm chart to deploy the featureflag-server

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://charts.bitnami.com/bitnami | common | 1.x.x |

## Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| affinity | object | `{}` |  |
| containerSecurityContext | object | `{}` |  |
| debug.enabled | bool | `false` |  |
| debug.remoteDebugPort | int | `5005` |  |
| enabled | bool | `false` |  |
| env_vars | object | `{}` |  |
| extraContainers | list | `[]` |  |
| extraEnv | list | `[]` |  |
| extraInitContainers | list | `[]` |  |
| extraLabels | object | `{}` |  |
| extraSelectorLabels | object | `{}` |  |
| extraVolumeMounts | list | `[]` |  |
| extraVolumes | list | `[]` |  |
| global.configMapName | string | `""` |  |
| global.credVolumeOverride | string | `""` |  |
| global.database.secretName | string | `""` |  |
| global.database.secretValue | string | `""` |  |
| global.edition       | string | `"community"`                 |  |
| global.extraContainers | list | `[]` |  |
| global.extraLabels | object | `{}` |  |
| global.extraSelectorLabels | object | `{}` |  |
| global.logs.accessKey.existingSecret | string | `""` |  |
| global.logs.accessKey.existingSecretKey | string | `""` |  |
| global.logs.accessKey.password | string | `"minio"` |  |
| global.logs.gcs.bucket | string | `""` |  |
| global.logs.gcs.credentials | string | `""` |  |
| global.logs.gcs.credentialsJson | string | `""` |  |
| global.logs.minio.enabled | bool | `true` |  |
| global.logs.s3.bucket | string | `"airbyte-dev-logs"` |  |
| global.logs.s3.bucketRegion | string | `""` |  |
| global.logs.s3.enabled | bool | `false` |  |
| global.logs.secretKey.existingSecret | string | `""` |  |
| global.logs.secretKey.existingSecretKey | string | `""` |  |
| global.logs.secretKey.password | string | `"minio123"` |  |
| global.secretName | string | `""` |  |
| global.serviceAccountName | string | `"placeholderServiceAccount"` |  |
| image.pullPolicy | string | `"IfNotPresent"` |  |
| image.repository | string | `"airbyte/workload-api-server"` |  |
| ingress.annotations | object | `{}` |  |
| ingress.className | string | `""` |  |
| ingress.enabled | bool | `false` |  |
| ingress.hosts | list | `[]` |  |
| ingress.tls | list | `[]` |  |
| livenessProbe.enabled | bool | `true` |  |
| livenessProbe.failureThreshold | int | `3` |  |
| livenessProbe.initialDelaySeconds | int | `60` |  |
| livenessProbe.periodSeconds | int | `10` |  |
| livenessProbe.successThreshold | int | `1` |  |
| livenessProbe.timeoutSeconds | int | `1` |  |
| log.level | string | `"INFO"` |  |
| nodeSelector | object | `{}` |  |
| podAnnotations | object | `{}` |  |
| podLabels | object | `{}` |  |
| readinessProbe.enabled | bool | `true` |  |
| readinessProbe.failureThreshold | int | `3` |  |
| readinessProbe.initialDelaySeconds | int | `30` |  |
| readinessProbe.periodSeconds | int | `10` |  |
| readinessProbe.successThreshold | int | `1` |  |
| readinessProbe.timeoutSeconds | int | `1` |  |
| replicaCount | int | `1` |  |
| resources.limits | object | `{}` |  |
| resources.requests | object | `{}` |  |
| secrets | object | `{}` |  |
| service.annotations | object | `{}` |  |
| service.port | int | `8007` |  |
| service.type | string | `"ClusterIP"` |  |
| tolerations | list | `[]` |  |
| workloadApi | object | `{}` |  |

