# devworkspace-telemetry-woopra-plugin

## Prerequisites

This repo depends on packages in the [GitHub maven package registry](https://github.com/features/packages).
A [personal access token](https://github.com/settings/tokens) with `read:packages` access is required to pull down
dependencies from GitHub.


Add a repository entry in `$HOME/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">
   <servers>
      <server>
         <id>che-incubator</id>
         <username>YOUR GITHUB USERNAME</username>
         <password>YOUR PERSONAL ACCESS TOKEN</password>
      </server>
   </servers>

   <profiles>
      <profile>
         <id>github</id>
         <activation>
            <activeByDefault>true</activeByDefault>
         </activation>
         <repositories>
            <repository>
               <id>central</id>
               <url>https://repo1.maven.org/maven2</url>
               <releases><enabled>true</enabled></releases>
               <snapshots><enabled>false</enabled></snapshots>
            </repository>
            <repository>
               <id>che-incubator</id>
               <name>GitHub navikt Apache Maven Packages</name>
               <url>https://maven.pkg.github.com/che-incubator/che-workspace-telemetry-client</url>
            </repository>
         </repositories>
      </profile>
   </profiles>
</settings>
```

## Building the native application and the Docker Image
Here are two ways to build the native application:
### Building with GraalVM
GraalVM version [21.3.1](https://www.graalvm.org/) and `native-image` are required. Refer to [Configuring GraalVM](https://quarkus.io/guides/building-native-image#configuring-graalvm).

```
mvn package -Pnative
```

### Building with a container runtime
```
mvn package -Pnative -Dquarkus.native.container-build=true 
```

### Creating the Docker image
After building the application with either methods above, run:
```
docker build -f src/main/docker/Dockerfile.native -t image-name:tag .
```

## Setting Segment and Woopra credentials
There are four configuration properties used to provide Woopra and Segment credentials. Refer to [MainConfiguration.java](https://github.com/che-incubator/devworkspace-telemetry-woopra-plugin/blob/master/src/main/java/com/redhat/che/workspace/services/telemetry/woopra/MainConfiguration.java
). These properties can be set via environment variables.

| Environment variable         | Description |
| ---------------------------- | ----------- |
| `WOOPRA_DOMAIN`              | The Woopra domain to send events to.       |
| `SEGMENT_WRITE_KEY`          | The write key to send events to Segment and Woopra.|
| `WOOPRA_DOMAIN_ENDPOINT`     | The HTTP endpoint that returns the Woopra domain. The endpoint will be accessed if `WOOPRA_DOMAIN` is not provided. |
| `SEGMENT_WRITE_KEY_ENDPOINT` | The HTTP endpoint that returns the Segment write key. The endpoint will be accessed if `SEGMENT_WRITE_KEY` is not provided. |

## Running Tests
```
mvn verify
```

## Publishing a new version of the plugin `meta.yaml` file

The plugin `meta.yaml` is hosted on a CDN at [static.developers.redhat.com](https://static.developers.redhat.com).  In order to push a new version, you will need the appropriate Akamai credential file, with the following layout:

```
[default]
key = key = <Secret key for the Akamai NetStorage account>
id = <NetStorage account ID>
group = <NetStorage storage group>
host = <NetStorage host>
cpcode = <NetStorage CPCode>
```

Save this file as `akamai-auth.conf`.

In the root of this repository, run:

```shell
docker run -w /root/app -v $(pwd):/root/app -v \
  /path/to/akamai-auth.conf:/root/.akamai-cli/.netstorage/auth \
  akamai/cli netstorage upload \
  --directory che/plugins/eclipse/che-workspace-telemetry-woopra-plugin/0.0.1 \
  meta.yaml
```
## Trademark

"Che" is a trademark of the Eclipse Foundation.
