job('Cypress-e2e-tests') {
    logRotator {
        daysToKeep(3)
    }

    scm {
        git('https://github.com/finnishtransportagency/mmtis-national-access-point.git', '*/master')
    }
    triggers {
        scm('H/15 * * * *')
    }

    wrappers {
        nodejs('nodejs-8.x-cypress')
        toolenv('nodejs-8.x-cypress')
    }

    steps {
        shell('ansible-vault view --vault-password-file=~/.vault_pass.txt aws/ansible/environments/staging/group_vars/all/vault > build.properties')

        envInjectBuilder {
            propertiesContent('')
            propertiesFilePath('build.properties')
        }

        shell('npm i cypress@1.x && $(npm bin)/cypress verify')
        shell('CYPRESS_RECORD_KEY=${vault_cypress_record_key} '+
              'CYPRESS_NAP_LOGIN=${vault_cypress_nap_username} '+
              'CYPRESS_NAP_PASSWORD=${vault_cypress_nap_password} '+
              '$(npm bin)/cypress run --browser chrome --record '+
              '--spec cypress/integration/smoke/ote_spec.js')
    }
}
