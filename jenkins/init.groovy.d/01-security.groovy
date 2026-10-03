/*
 * Jenkins bootstrap: security realm and the project's administrator account.
 *
 * Runs on every start, before the UI is available. It is idempotent: it only
 * creates the account if the realm does not already hold it, so restarting
 * Jenkins never resets a password that was changed by hand.
 *
 * Why this exists instead of the setup wizard: the wizard requires a human to
 * paste an unlock token and click through five screens, which cannot be
 * scripted, cannot be reproduced on another machine, and cannot be captured as
 * evidence. The whole point of this subject is that environments are described
 * as code - that has to include the CI server itself.
 */
import jenkins.model.Jenkins
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.security.HudsonPrivateSecurityRealm

def jenkins = Jenkins.get()
def username = System.getenv('JENKINS_ADMIN_USER') ?: 'admin'
def password = System.getenv('JENKINS_ADMIN_PASSWORD') ?: 'bloodbank-admin'

if (!(jenkins.getSecurityRealm() instanceof HudsonPrivateSecurityRealm)) {
    println "--> creating private security realm with administrator '${username}'"
    def realm = new HudsonPrivateSecurityRealm(false, false, null)
    realm.createAccount(username, password)
    jenkins.setSecurityRealm(realm)

    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    strategy.setAllowAnonymousRead(false)
    jenkins.setAuthorizationStrategy(strategy)

    jenkins.save()
} else {
    def realm = (HudsonPrivateSecurityRealm) jenkins.getSecurityRealm()
    if (realm.getAllUsers().every { it.getId() != username }) {
        println "--> administrator '${username}' missing from existing realm; creating it"
        realm.createAccount(username, password)
        jenkins.save()
    } else {
        println "--> administrator '${username}' already present; leaving the realm untouched"
    }
}
