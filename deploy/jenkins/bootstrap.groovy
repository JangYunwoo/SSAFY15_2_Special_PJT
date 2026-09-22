import jenkins.model.Jenkins
import hudson.security.HudsonPrivateSecurityRealm
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy
import hudson.slaves.DumbSlave
import hudson.slaves.JNLPLauncher
import hudson.slaves.RetentionStrategy
import hudson.model.Node
import hudson.plugins.git.GitSCM
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.UserRemoteConfig
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import groovy.json.JsonSlurper

def j = Jenkins.get()
// Bootstrap once. Reboots must not reset passwords or administrator configuration.
if (new File(j.rootDir, '.gongguhan-bootstrap-complete').exists()) return
def secretDir = new File('/run/gongguhan-secrets')
def realm = new HudsonPrivateSecurityRealm(false)
realm.createAccount('taemin-admin', new File(secretDir,'admin-password').text.trim())
j.setSecurityRealm(realm)
def authorization = new FullControlOnceLoggedInAuthorizationStrategy()
authorization.setAllowAnonymousRead(false)
j.setAuthorizationStrategy(authorization)
j.setNumExecutors(0)
j.setSlaveAgentPort(-1)
def token = new JsonSlurper().parse(new File(secretDir,'gitlab-read.json'))
SystemCredentialsProvider.getInstance().getStore().addCredentials(Domain.global(),
 new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,'gongguhan-gitlab-read',
 'Jenkins project-scoped read_repository token',token.username,token.token))
def node = new DumbSlave('gongguhan-ci-agent','Trusted DevelopV2 builds only',
 '/opt/gongguhan-ci/workspace','1',Node.Mode.EXCLUSIVE,'gongguhan-ci',
 new JNLPLauncher(),new RetentionStrategy.Always(),[])
j.addNode(node)
new File(j.rootDir,'ci-agent-secret').text = node.toComputer().getJnlpMac()
// Only the temporary validation branch is built at bootstrap; no automatic deployment.
def validation = j.createProject(WorkflowJob,'gongguhan-ci-validation')
def scm = new GitSCM([new UserRemoteConfig('https://lab.ssafy.com/s15-fintech-finance-sub1/S15P21B205.git',null,null,'gongguhan-gitlab-read')],
 [new BranchSpec('*/feature/be/S15P21B205-234-jenkins-cicd')],false,[],null,null,[])
validation.setDefinition(new CpsScmFlowDefinition(scm,'Jenkinsfile'))
validation.save()
j.save()
new File(j.rootDir,'.gongguhan-bootstrap-complete').text = '234'
