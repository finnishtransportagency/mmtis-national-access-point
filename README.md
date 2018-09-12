# Finnish Transport Agency: National Access Point and digitizing tools

The Finnish National Access Point as defined in MMTIS directive. Contains a service catalogue for transportation service data and APIs as well as tools for digitizing transportation service data. The catalogue portal is partially based on CKAN.

The live NAP service can be found at https://finap.fi. The service instance is built from the code in this repository.

The background of NAP in Finnish legislation is Government Decree 643/2017 that defines essential information on transportation services (https://www.finlex.fi/fi/laki/alkup/2017/20170643). The decree requires transportation service operators to provide open interfaces for information such as routes, timetables, pricing, accessibility, etc.

NAP is the Finnish catalogue service for such essential information. It also includes tools for digitizing basic information for service providers that do not have their own means to do so. The latter functionality is aimed at small companies - it is assumed that larger organizations have their own systems that can export data and APIs. However, also these larger companies use NAP to publish their own interfaces.

## Structure of the repository

Directories:

* `nap` contains the NAP CKAN portal extensions, configurations and scripts
* `ote` contains the OTE digitalization tools application (both frontend and backend)
* `aws` contains configuration and scripts for setting up the cloud environments (except secrets, like keys)
* `database` contains migrations and test data to setup the OTE database


<a href="https://www.browserstack.com" target="_blank" rel="noopener noreferrer"><img src="./Browserstack-logo.svg" width="200"></a>