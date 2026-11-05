targetScope = 'resourceGroup'

@description('Azure region for the Container Apps environment and Log Analytics workspace.')
param location string = resourceGroup().location

@description('Lowercase Container Apps name, 2-23 characters.')
@minLength(2)
@maxLength(23)
param appName string = 'ai-job-agent'

@description('Public container image URI, for example ghcr.io/owner/ai-job-agent:1.0.0.')
param containerImage string

@description('MongoDB connection string. Store it in the local parameters file and never commit that file.')
@secure()
param mongodbUri string

@description('Optional public-board identifiers, separated by commas.')
param greenhouseBoards string = ''

@description('Optional Lever site identifiers, separated by commas.')
param leverSites string = ''

@description('Optional HTTPS RSS/Atom feeds in Company=URL;Company=URL format.')
param rssJobFeeds string = ''

var logAnalyticsName = '${appName}-logs'
var containerAppsEnvironmentName = '${appName}-env'

resource logAnalyticsWorkspace 'Microsoft.OperationalInsights/workspaces@2022-10-01' = {
  name: logAnalyticsName
  location: location
  properties: {
    retentionInDays: 30
    sku: {
      name: 'PerGB2018'
    }
  }
}

resource containerAppsEnvironment 'Microsoft.App/managedEnvironments@2023-05-01' = {
  name: containerAppsEnvironmentName
  location: location
  properties: {
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: logAnalyticsWorkspace.properties.customerId
        sharedKey: logAnalyticsWorkspace.listKeys().primarySharedKey
      }
    }
  }
}

resource containerApp 'Microsoft.App/containerApps@2023-05-01' = {
  name: appName
  location: location
  properties: {
    managedEnvironmentId: containerAppsEnvironment.id
    configuration: {
      activeRevisionsMode: 'Single'
      secrets: [
        {
          name: 'mongodb-uri'
          value: mongodbUri
        }
      ]
      ingress: {
        external: true
        targetPort: 8080
        transport: 'auto'
        allowInsecure: false
      }
    }
    template: {
      containers: [
        {
          name: 'ai-job-agent'
          image: containerImage
          resources: {
            cpu: 1
            memory: '2Gi'
          }
          env: [
            {
              name: 'SPRING_PROFILES_ACTIVE'
              value: 'production'
            }
            {
              name: 'MONGODB_URI'
              secretRef: 'mongodb-uri'
            }
            {
              name: 'AUTOMATION_EMAIL_ENABLED'
              value: 'false'
            }
            {
              name: 'GREENHOUSE_ENABLED'
              value: 'true'
            }
            {
              name: 'GREENHOUSE_BOARDS'
              value: greenhouseBoards
            }
            {
              name: 'LEVER_ENABLED'
              value: 'true'
            }
            {
              name: 'LEVER_SITES'
              value: leverSites
            }
            {
              name: 'RSS_JOB_FEEDS_ENABLED'
              value: 'true'
            }
            {
              name: 'RSS_JOB_FEEDS'
              value: rssJobFeeds
            }
            {
              name: 'TESSERACT_COMMAND'
              value: 'tesseract'
            }
          ]
          probes: [
            {
              type: 'Startup'
              httpGet: {
                path: '/actuator/health/liveness'
                port: 8080
                scheme: 'HTTP'
              }
              periodSeconds: 10
              timeoutSeconds: 5
              failureThreshold: 30
            }
            {
              type: 'Readiness'
              httpGet: {
                path: '/actuator/health/readiness'
                port: 8080
                scheme: 'HTTP'
              }
              periodSeconds: 10
              timeoutSeconds: 5
              failureThreshold: 3
            }
            {
              type: 'Liveness'
              httpGet: {
                path: '/actuator/health/liveness'
                port: 8080
                scheme: 'HTTP'
              }
              periodSeconds: 30
              timeoutSeconds: 5
              failureThreshold: 3
            }
          ]
        }
      ]
      scale: {
        minReplicas: 1
        maxReplicas: 1
      }
    }
  }
}

output appUrl string = 'https://${containerApp.properties.configuration.ingress.fqdn}'
