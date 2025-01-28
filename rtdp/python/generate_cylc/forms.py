from flask_wtf import FlaskForm
from wtforms import (
    StringField, IntegerField, FloatField, SubmitField
)
from wtforms.validators import DataRequired, NumberRange


class WorkflowConfigForm(FlaskForm):
    # Workflow Settings
    workflow_name = StringField('Workflow Name',
                                default='cpu-emu',
                                validators=[DataRequired()])
    workflow_description = StringField('Description',
                                       default='Cylc-based CPU Emulator Testing Workflow')

    # Platform Settings
    platform_name = StringField('Platform Name',
                                default='jlab_slurm',
                                validators=[DataRequired()])
    cylc_path = StringField('Cylc Path',
                            default='/path/to/cylc-env/bin/',
                            validators=[DataRequired()])
    hosts = StringField('Hosts',
                        default='tsai@ifarm2402',
                        validators=[DataRequired()])
    job_runner = StringField('Job Runner',
                             default='slurm',
                             validators=[DataRequired()])

    # Receiver Settings
    receiver_ntasks = IntegerField('Receiver Tasks',
                                   default=1,
                                   validators=[NumberRange(min=1)])
    receiver_cpus = IntegerField('Receiver CPUs per Task',
                                 default=4,
                                 validators=[NumberRange(min=1)])
    receiver_mem = StringField('Receiver Memory',
                               default='8G',
                               validators=[DataRequired()])
    receiver_partition = StringField('Receiver Partition',
                                     default='ifarm',
                                     validators=[DataRequired()])
    receiver_timeout = StringField('Receiver Timeout',
                                   default='2h',
                                   validators=[DataRequired()])

    # Emulator Settings
    emulator_ntasks = IntegerField('Emulator Tasks',
                                   default=1,
                                   validators=[NumberRange(min=1)])
    emulator_cpus = IntegerField('Emulator CPUs per Task',
                                 default=4,
                                 validators=[NumberRange(min=1)])
    emulator_mem = StringField('Emulator Memory',
                               default='16G',
                               validators=[DataRequired()])
    emulator_partition = StringField('Emulator Partition',
                                     default='ifarm',
                                     validators=[DataRequired()])
    emulator_timeout = StringField('Emulator Timeout',
                                   default='2h',
                                   validators=[DataRequired()])

    # Sender Settings
    sender_ntasks = IntegerField('Sender Tasks',
                                 default=1,
                                 validators=[NumberRange(min=1)])
    sender_cpus = IntegerField('Sender CPUs per Task',
                               default=4,
                               validators=[NumberRange(min=1)])
    sender_mem = StringField('Sender Memory',
                             default='8G',
                             validators=[DataRequired()])
    sender_partition = StringField('Sender Partition',
                                   default='ifarm',
                                   validators=[DataRequired()])
    sender_timeout = StringField('Sender Timeout',
                                 default='2h',
                                 validators=[DataRequired()])

    # Network Settings
    receiver_port = IntegerField('Receiver Port',
                                 default=50080,
                                 validators=[NumberRange(min=1024, max=65535)])
    emulator_port = IntegerField('Emulator Port',
                                 default=50888,
                                 validators=[NumberRange(min=1024, max=65535)])

    # Emulator Configuration
    emulator_threads = IntegerField('Emulator Threads',
                                    default=4,
                                    validators=[NumberRange(min=1)])
    emulator_latency = FloatField('Emulator Latency (GB)',
                                  default=50,
                                  validators=[NumberRange(min=0)])
    emulator_mem_footprint = FloatField('Memory Footprint (GB)',
                                        default=0.05,
                                        validators=[NumberRange(min=0)])
    emulator_output_size = FloatField('Output Size (GB)',
                                      default=0.001,
                                      validators=[NumberRange(min=0)])

    # Test Data Settings
    test_data_size = StringField('Test Data Size',
                                 default='100M',
                                 validators=[DataRequired()])

    # Container Settings
    container_image = StringField('Container Image',
                                  default='cpu-emu.sif',
                                  validators=[DataRequired()])
    docker_source = StringField('Docker Source',
                                default='jlabtsai/rtdp-cpu_emu:latest',
                                validators=[DataRequired()])

    submit = SubmitField('Generate Configuration')
