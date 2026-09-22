// Run once through an authenticated administrator session after the reviewed MR is merged.
import jenkins.model.Jenkins
import hudson.plugins.git.GitSCM
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.UserRemoteConfig
import hudson.triggers.SCMTrigger
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition

def j = Jenkins.get()
def validation = j.getItem('gongguhan-ci-validation')
if (validation == null || validation.getLastBuild()?.isBuilding() ||
    validation.getLastBuild()?.getResult()?.toString() != 'SUCCESS') {
    throw new IllegalStateException('Complete a successful validation build before enabling deployment')
}
def job = j.getItem('gongguhan-DevelopV2') ?: j.createProject(WorkflowJob, 'gongguhan-DevelopV2')
def scm = new GitSCM([new UserRemoteConfig(
    'https://lab.ssafy.com/s15-fintech-finance-sub1/S15P21B205.git', null, null, 'gongguhan-gitlab-read')],
    [new BranchSpec('*/DevelopV2')], false, [], null, null, [])
job.setDefinition(new CpsScmFlowDefinition(scm, 'Jenkinsfile'))
job.setDescription('DevelopV2 only: tests → immutable images → backup → deploy → health check. Private SSH access.')
job.addTrigger(new SCMTrigger('H/2 * * * *'))
job.setDisabled(false)
job.save()
job.getTriggers().values().each { it.start(job, true) }
validation.setDisabled(true)
validation.save()
println('Enabled DevelopV2 polling; verify the first SCM-triggered build and deployment result.')
