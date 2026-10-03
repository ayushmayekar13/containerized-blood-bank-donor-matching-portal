/*
 * Jenkins bootstrap: the JDK and Maven installations the jobs refer to by name,
 * plus the server's own URL.
 *
 * The host's default `java` is 11 and Spring Boot 3 needs 17+, so the JDK is
 * pinned here by absolute path rather than left to whatever is on the agent's
 * PATH. Pinning it in Jenkins as well as in scripts/env.sh means the build uses
 * the same JDK whether it is run by hand or by the pipeline, which removes a
 * whole class of "works locally, fails in CI".
 *
 * Idempotent: an installation with the same name is replaced, not duplicated.
 */
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration
import hudson.model.JDK
import hudson.tasks.Maven

def jenkins = Jenkins.get()

// ---- server URL, used in build links and in the evidence captured per build
def location = JenkinsLocationConfiguration.get()
def url = System.getenv('JENKINS_URL') ?: 'http://localhost:8090/'
if (location.getUrl() != url) {
    println "--> setting Jenkins URL to ${url}"
    location.setUrl(url)
    location.setAdminAddress(System.getenv('JENKINS_ADMIN_EMAIL') ?: 'ayushmayekar1333@gmail.com')
    location.save()
}

// ---- JDK 21
def javaHome = System.getenv('BUILD_JAVA_HOME') ?: '/opt/homebrew/opt/openjdk@21'
def jdkDescriptor = jenkins.getDescriptorByType(hudson.model.JDK.DescriptorImpl)
if (jdkDescriptor.getInstallations().every { it.getName() != 'jdk21' }) {
    println "--> registering JDK installation 'jdk21' at ${javaHome}"
    def existing = jdkDescriptor.getInstallations().toList()
    existing.add(new JDK('jdk21', javaHome))
    jdkDescriptor.setInstallations(existing as JDK[])
    jdkDescriptor.save()
}

// ---- Maven
def mavenHome = System.getenv('BUILD_MAVEN_HOME') ?: '/opt/homebrew/opt/maven/libexec'
def mavenDescriptor = jenkins.getDescriptorByType(hudson.tasks.Maven.DescriptorImpl)
if (mavenDescriptor.getInstallations().every { it.getName() != 'maven3' }) {
    println "--> registering Maven installation 'maven3' at ${mavenHome}"
    def existing = mavenDescriptor.getInstallations().toList()
    existing.add(new Maven.MavenInstallation('maven3', mavenHome, []))
    mavenDescriptor.setInstallations(existing as Maven.MavenInstallation[])
    mavenDescriptor.save()
}

println '--> Jenkins tool bootstrap complete'
