from setuptools import setup, find_packages

setup(
    name="rtdp-components",
    version="0.1.0",
    packages=find_packages(),
    install_requires=[
        "pyzmq",
        "pyyaml",
        "numpy",
    ],
    python_requires=">=3.10",
) 