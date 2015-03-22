[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Centaur/repox?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

### What is Repox
The main motivation of Repox is to speedup sbt dependency resolving. But it can be used as ivy/gradle/ maven/leiningen proxy as well，as a replacement of Nexus/Artifactory.

Currently Repox only do proxying, there is no support for artifact publishing.

To get your hands dirty immediately, go [Getting Start](https://github.com/Centaur/repox/wiki/Getting-Started)

### Why Repox

We have tried Nexus and Artifactory open source version as private proxy, neither has brought improvements for sbt projects.

### Repox Philosophy

* Proxy Everything

   So that we can introduce improvments at every step.

* Succeed fast or Fail fast

     * If multiple upstream repos have the request file, choose the fastest one.
     * If a connection did not get data for a long time, abort and redownload。
     * Remember failed request to make less duplicate.

  **Important Note: This means that repox may have more chance to “download failed”。You just reinvoke the failed command. We believe that more retry brings better experience than waiting forever. **

* Do what a proxy is supposed to do

   If a file has ever been downloaded, response immediately, without asking upstreams.

Read the wiki pages for details.

### Repox Pros

* Lightweight

Just a dozen of source code files for the core functionality, each less then 200 lines. Just a weekend's reading.

* Reactive overall

undertow + akka + async-http-client

* Adjust configurations by web admin

* Practical

We build Repox for our own needs and we are using it everyday.

### Suggested Deployment
1. As Organization Private Proxy

Recommended. So that once a developer has done `sbt update` a project, others' updating feels like using local repo.

1. Run locally

The deployment and running are very simple，it brings good productivity too when running locally。
