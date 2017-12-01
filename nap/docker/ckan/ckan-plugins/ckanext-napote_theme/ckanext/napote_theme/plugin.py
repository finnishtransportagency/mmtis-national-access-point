# encoding: utf-8

import pprint
import mimetypes
from logging import getLogger
from pkg_resources import resource_stream
import csv
import sys

import ckan.authz as authz
import ckan.plugins as plugins
import ckan.plugins.toolkit as tk
from ckan.lib.plugins import DefaultTranslation
from routes.mapper import SubMapper
from ckan.lib import authenticator
from ckan.model import User

from ckan.lib import i18n
from ckan.plugins import PluginImplementations
from ckan.plugins.interfaces import ITranslation
from paste.deploy.converters import aslist

csv.field_size_limit(sys.maxsize)

log = getLogger(__name__)

### FIXME: MONKEY PATCH CKAN AUTHENTICATION START ###

def authenticate_monkey_patch(self, environ, identity):
    """Using our custom authentication method with Repoze auth
    This allows us to use email address in login """
    if not ('login' in identity and 'password' in identity):
        return None

    login = identity['login']

    # Try login with email
    users = User.by_email(login)

    # If we get an user list as a result, use the first result. Otherwise, try to
    # get user by username.
    if users:
        # Use the first result only. We'll have to assume that that we have one email per username
        # in the ckan database. By default, CKAN allows using the same email for multiple users.
        user = users[0]
    else:
        user = User.by_name(login)

    if user is None:
        log.debug('Login failed - username %r not found', login)
    elif not user.is_active():
        log.debug('Login as %r failed - user isn\'t active', login)
    elif not user.validate_password(identity['password']):
        log.debug('Login as %r failed - password not valid', login)
    else:
        return user.name

    return None

authenticator.UsernamePasswordAuthenticator.authenticate = authenticate_monkey_patch

### MONKEY PATCH CKAN AUTHENTICATION END ###

### MONKEY PATCH CKAN I18N LANGUAGE START ###

supported_languages = ['fi', 'sv', 'en']

# Take language from "finap_lang" cookie which both CKAN and OTE
# can use. Language selection sets cookie and refreshes the page.
def language_from_cookie(request):
    lang = "fi"
    try:
        lang = request.cookies['finap_lang']
    except:
        pass

    return lang

def handle_request(request, tmpl_context):
    ''' Set the language for the request '''

    config = i18n.config

    lang = language_from_cookie(request)
    i18n.set_lang(lang)

    ## CODE AFTER THIS LINE IS TAKEN AS IS FROM ckan.lib.i18n
    for plugin in PluginImplementations(ITranslation):
        if lang in plugin.i18n_locales():
            i18n._add_extra_translations(plugin.i18n_directory(), lang,
                                    plugin.i18n_domain())

    extra_directory = config.get('ckan.i18n.extra_directory')
    extra_domain = config.get('ckan.i18n.extra_gettext_domain')
    extra_locales = aslist(config.get('ckan.i18n.extra_locales'))
    if extra_directory and extra_domain and extra_locales:
        if lang in extra_locales:
            _add_extra_translations(extra_directory, lang, extra_domain)

    tmpl_context.language = lang
    return lang

i18n.handle_request = handle_request

### MONKEY PATCH CKAN I18N LANGAUGE END ###

def dataset_purge_custom_auth(context, data_dict):
    # Defer authorization for package_pruge to package_update
    # This authorization is similar to editing package fields.

    return authz.is_authorized('package_update', context, data_dict)


def log_debug(*args):
    log.info(*args)


def tags_to_select_options(tags=None):
    if tags is None:
        tags = []
    return [{'name': tag, 'value': tag} for tag in tags]


def get_in(data, *keys):
    try:
        for k in keys:
            data = data[k]
        return data
    except (IndexError, KeyError) as e:
        return None


# TODO: This is an example, how to translate our dataset fields. ckan multilingual needs to be added into ckan.plugins for this to work.
def update_term_translations():
    return tk.get_action('term_translation_update_many')({'ignore_auth': True}, {
        'data': [
            {
                'term': u'passenger-transportation',
                'term_translation': u'Henkilöidenkuljetuspalvelut',
                'lang_code': 'fi'
            },
            {
                'term': 'terminal',
                'term_translation': u'Asemat, satamat ja muut terminaalit',
                'lang_code': 'fi'
            },
            {
                'term': 'rentals',
                'term_translation': u'Ajoneuvojen vuokrauspalvelut ja kaupalliset yhteiskäyttöpalvelut',
                'lang_code': 'fi'
            },
            {
                'term': 'parking',
                'term_translation': u'Yleiset kaupalliset pysäköintipalvelut',
                'lang_code': 'fi'
            },
            {
                'term': 'brogerake',
                'term_translation': u'Välityspalvelut',
                'lang_code': 'fi'
            }
        ]})


class LanguageMiddleware(object):
    def __init__(self, app):
        self.app = app

    def __call__(self, environ, start_response):
        print 'hei täällä kieli middleware'
        return self.app(environ, start_response)


class NapoteThemePlugin(plugins.SingletonPlugin, DefaultTranslation, tk.DefaultDatasetForm):
    plugins.implements(plugins.IAuthFunctions)
    plugins.implements(plugins.IPluginObserver, inherit=True)
    plugins.implements(plugins.IConfigurer)
    plugins.implements(plugins.IRoutes, inherit=True)

    # http://docs.ckan.org/en/latest/extensions/translating-extensions.html
    plugins.implements(plugins.ITranslation)
    plugins.implements(plugins.IDatasetForm)
    plugins.implements(plugins.ITemplateHelpers)
    plugins.implements(plugins.IFacets, inherit=True)
    plugins.implements(plugins.IResourceView, inherit=True)
    plugins.implements(plugins.IMiddleware)


    def get_auth_functions(self):
        return {'dataset_purge': dataset_purge_custom_auth}

    def get_helpers(self):
        return {
            'tags_to_select_options': tags_to_select_options,
            'log_debug': log_debug}

    def after_load(self, service):
        # update_term_translations()
        return service

    def update_config(self, config):
        # CKAN uses the default Python library mimetypes to detect the media type of afile.
        # If some particular format is not included in the ones guessed by the mimetypes library,
        # a default application/octet-stream value will be returned.

        # Add support for svg files in templates.
        mimetypes.add_type('image/svg+xml', '.svg')

        # Add this plugin's templates dir to CKAN's extra_template_paths, so
        # that CKAN will use this plugin's custom templates.
        # 'templates' is the path to the templates dir, relative to this
        # plugin.py file.
        tk.add_template_directory(config, 'templates')

        # Register this plugin's fanstatic directory with CKAN.
        # Here, 'fanstatic' is the path to the fanstatic directory
        # (relative to this plugin.py file), and 'napote_theme' is the name
        # that we'll use to refer to this fanstatic directory from CKAN
        # templates.
        tk.add_resource('fanstatic', 'napote_theme')

        # Public directory for static images
        tk.add_public_directory(config, 'public')

    def before_map(self, map):
        map.redirect('/dataset/new', '/error/')
        map.redirect('/dataset/edit/{id:.*}', '/error/')
        map.redirect('/dataset/groups/{id:.*}', '/error/')
        map.redirect('/dataset/delete/{id:.*}', '/error/')
        map.redirect('/dataset/new_resource/{id:.*}', '/error/')
        map.redirect('/dataset/{id:.*}/resource/{resource_id:.*}/new_view', '/error/')
        map.redirect('/dataset/{id:.*}/resource_edit/{resource_id:.*}', '/error/')
        map.redirect('/dataset/{id:.*}/resource/{resource_id:.*}/edit_view/{view_id:.*}', '/error/')
        map.redirect('/dataset/{id:.*}/resource_delete/{resource_id:.*}', '/error/')
        map.redirect('/group/new', '/error/')
        map.redirect('/group/member_new/{id:.*}', '/error/')
        map.redirect('/group/edit/{id:.*}', '/error/')
        map.redirect('/organization/bulk_process/{id:.*}/', '/error/')

        # Hook user password reset route to our custom user controller

        map.connect('/user/reset',
                    controller='ckanext.napote_theme.controller:CustomUserController',
                    action='request_reset')

        map.connect('/user/register',
                    controller='ckanext.napote_theme.controller:CustomUserController',
                    action='register')

        map.connect('/organization/new',
                    controller='ckanext.napote_theme.organization_controller:CustomOrganizationController',
                    action='new')

        map.connect('/user/login',
                            controller='ckanext.napote_theme.controller:CustomUserController',
                            action='login')

        map.connect('/user/_logout',
                            controller='ckanext.napote_theme.controller:CustomUserController',
                            action='logout')

        return map

    def after_map(self, map):
        with SubMapper(map, controller='package') as m:
            m.connect('search', '/ote/index.html#/services', action='search',
                      highlight_actions='index search')
        return map

    def dataset_facets(self, facets_dict, package_type):
        facets_dict.clear()

        # facets_dict['organization'] = tk._('Organizations')
        facets_dict['extras_transport_service_type'] = tk._('Transport Service Type')
        # facets_dict['extras_operation_area'] = tk._('Operation Area')
        # facets_dict['tags'] = tk._('Tags')
        # facets_dict['res_format'] = tk._('Formats')
        # facets_dict['license_id'] = tk._('Licenses')

        return facets_dict

    def organization_facets(self, facets_dict, organization_type, package_type):
        facets_dict.clear()

        facets_dict['extras_transport_service_type'] = tk._('Transport Service Type')
        # facets_dict['extras_operation_area'] = tk._('Operation Area')
        # facets_dict['tags'] = tk._('Tags')
        # facets_dict['res_format'] = tk._('Formats')
        # facets_dict['license_id'] = tk._('Licenses')

        return facets_dict

    def _modify_package_schema(self, schema):
        # add custom fields
        schema.update({
            'transport_service_type': [tk.get_validator('ignore_missing'),
                                       tk.get_converter('convert_to_extras')]
        })

        schema.update({
            'operation_area': [tk.get_validator('ignore_missing'),
                               tk.get_converter('convert_to_extras')]
        })

        return schema

    def show_package_schema(self):
        schema = super(NapoteThemePlugin, self).show_package_schema()

        # Prevent listing vocabulary tags mixed in with normal tags
        schema['tags']['__extras'].append(tk.get_converter('free_tags_only'))

        schema.update({
            'transport_service_type': [tk.get_converter('convert_from_extras'),
                                       tk.get_validator('ignore_missing')],
        })

        schema.update({
            'operation_area': [tk.get_converter('convert_from_extras'),
                               tk.get_validator('ignore_missing')]
        })

        return schema

    def create_package_schema(self):
        schema = super(NapoteThemePlugin, self).create_package_schema()
        schema = self._modify_package_schema(schema)

        return schema

    def update_package_schema(self):
        schema = super(NapoteThemePlugin, self).update_package_schema()
        schema = self._modify_package_schema(schema)

        return schema

    def is_fallback(self):
        return True

    def package_types(self):
        return []

    # Methods for IResourceView

    def info(self):
        return {'name': 'transport_service_view',
                'title': tk._('Transport Service View'),
                'iframed': False}

    def can_view(self, data_dict):
        return data_dict['resource']['format'] == 'GeoJSON'

    def setup_template_variables(self, context, data_dict):
        # log_debug('setup_template_variables, ctx:\n %s, data:\n %s', pprint.pformat(context), pprint.pformat(data_dict))
        url = get_in(data_dict, 'resource', 'url') or get_in(data_dict, 'package', 'resources', 0, 'url')
        return {'transport_service_url': url}

    def view_template(self, context, data_dict):
        return "transport_service_view.html"

    def form_template(self, context, data_dict):
        return "transport_service_view.html"

    # Methods for IMiddleware

    def make_middleware(self, app, config):
        log_debug("make_middleware: app = %s, config = %s", pprint.pformat(app), pprint.pformat(config))
        return LanguageMiddleware(app)

    def make_error_log_middleware(self, app, config):
        return app
