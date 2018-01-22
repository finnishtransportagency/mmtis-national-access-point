job('Generate ChangeLog from Github PRs') {
    logRotator {
        daysToKeep(3)
    }

    scm {
        git('https://github.com/finnishtransportagency/mmtis-national-access-point.git', '*/master')
    }
    triggers {
        cron('0 22 * * *')
    }
    steps {
        shell('cd tools/changelog && lein run')
    }
}
