# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

# Added by author
import os
import sys
sys.path.insert(0, os.path.abspath("../.."))  # abs path to the root


# -- Project information -----------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#project-information
project = 'sro-rtdp'
copyright = '2024, jlab-epsci'
author = 'jlab-epsci'

# -- General configuration ---------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#general-configuration

# Added by author
extensions = ["sphinx.ext.todo", "sphinx.ext.viewcode", "sphinx.ext.autodoc",]

templates_path = ['_templates']
exclude_patterns = ["build", "Thumb.db", ".DS_Store", "__pycache__"] # updated

# -- Options for HTML output -------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#options-for-html-output

html_theme = "sphinx_rtd_theme" # this requiures a `pip install sphinx-rtd-theme` beforehand
html_static_path = ['_static']
