# encoding: utf-8

import mimetypes
from logging import getLogger
from pkg_resources import resource_stream
import csv
import sys
import ckan.plugins as plugins
import ckan.plugins.toolkit as tk
from ckan.lib.plugins import DefaultTranslation

csv.field_size_limit(sys.maxsize)

log = getLogger(__name__)

# TODO: We should get these from the database
municipalitys = (
    u'Helsinki', u'Ii', u'Joensuu', u'Kempele', u'Muhos', u'Oulu', u'Pieksämäki', u'Salo', u'Seinäjoki', u'Vantaa')
transport_services = (u'Terminal', u'Passenger Transportation', u'Rental', u'Parking', u'Brokerage')


def read_csv(file_path):
    file = resource_stream(__name__, file_path)

    # Read the file into a dictionary for each row ({header : value})
    reader = csv.DictReader(file, delimiter=',')
    data = {}

    for row in reader:
        for header, value in row.items():
            unicodeVal = unicode(value, 'utf-8')
            try:
                data[header].append(unicodeVal)
            except KeyError:
                data[header] = [unicodeVal]

    return data

def log_debug(*args):
    log.info(*args)

def tags_to_select_options(tags=None):
    if tags is None:
        tags = []
    return [{'name': tag, 'value': tag} for tag in tags]


class NapoteThemePlugin(plugins.SingletonPlugin, tk.DefaultDatasetForm):
    # http://docs.ckan.org/en/latest/extensions/translating-extensions.html
    # Enable after translations have been generated
    # plugins.implements(plugins.ITranslation)
    plugins.implements(plugins.IConfigurer)
    plugins.implements(plugins.IDatasetForm)
    plugins.implements(plugins.ITemplateHelpers)
    plugins.implements(plugins.IFacets, inherit=True)
    plugins.implements(plugins.IRoutes, inherit=True)

    def get_helpers(self):
        return {
            'tags_to_select_options': tags_to_select_options,
            'log_debug': log_debug}

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

        return map

    def dataset_facets(self, facets_dict, package_type):
        facets_dict.clear()

        facets_dict['organization'] = tk._('Organizations')
        facets_dict['extras_transport_service_type'] = tk._('Transport Service Type')
        facets_dict['extras_operation_area'] = tk._('Operation Area')
        facets_dict['tags'] = tk._('Tags')
        facets_dict['res_format'] = tk._('Formats')
        facets_dict['license_id'] = tk._('Licenses')

        return facets_dict

    def organization_facets(self, facets_dict, organization_type, package_type):
        facets_dict.clear()

        facets_dict['extras_transport_service_type'] = tk._('Transport Service Type')
        facets_dict['extras_operation_area'] = tk._('Operation Area')
        facets_dict['tags'] = tk._('Tags')
        facets_dict['res_format'] = tk._('Formats')
        facets_dict['license_id'] = tk._('Licenses')

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
