from setuptools import setup, find_packages

setup(
    name="rtdp-workflow-cli",
    version="0.1.0",
    packages=find_packages(),
    include_package_data=True,
    install_requires=[
        "click>=8.0.0",
        "pyyaml>=6.0",
        "jinja2>=3.0.0",
    ],
    entry_points={
        "console_scripts": [
            "rtdp-workflow=rtdp.cli.rtdp_workflow_cli:cli",
        ],
    },
    author="RTDP Team",
    author_email="your.email@example.com",
    description="RTDP Workflow CLI for generating and managing RTDP workflows",
    long_description=open("README.md").read(),
    long_description_content_type="text/markdown",
    url="https://github.com/yourusername/rtdp-workflow-cli",
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: MIT License",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.7",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
    ],
    python_requires=">=3.7",
) 