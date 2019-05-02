/*
* Fetch git branch from tag
*
* @return branch name
* */
def parseBranchFromTag(String str) {
    def match = ~/(?s)-\d{4}-\d{2}-\d{2}.*$/
    return (str - match).trim()
}

/*
* Get git short commit hash
*
* return commit hash
* */
def gitShortCommit() {
    return sh(returnStdout: true, script: "git rev-parse --short HEAD").trim()
}

/*
* Send notification on slack
*
* @return nil
* */
def notifySlack(String color, message) {
    slackSend channel: "${env.ICM_SLACK_CHANNEL}",
            color: "${color}",
            message: "${message}",
            tokenCredentialId: "${env.ICM_SLACK_CRED_ID}"
}