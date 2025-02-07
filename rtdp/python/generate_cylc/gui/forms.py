from flask_wtf import FlaskForm
from wtforms import (
    StringField, SelectField, IntegerField, FloatField,
    SubmitField, ValidationError, HiddenField, BooleanField,
    SelectMultipleField, FormField
)
from wtforms.validators import DataRequired, Optional, NumberRange
from typing import Any


class WorkflowMetadataForm(FlaskForm):
    """Form for workflow metadata."""
    name = StringField('Workflow Name', validators=[DataRequired()])
    description = StringField('Description')


class PlatformConfigForm(FlaskForm):
    """Form for platform configuration."""
    name = StringField('Platform Name', validators=[DataRequired()])
    job_runner = SelectField('Job Runner',
                             choices=[('slurm', 'SLURM')],
                             validators=[DataRequired()])


class ResourcesForm(FlaskForm):
    """Form for component resources."""
    partition = StringField('Partition', validators=[DataRequired()])
    cpus_per_task = IntegerField('CPUs per Task',
                                 default=4,
                                 validators=[DataRequired(), NumberRange(min=1)])
    mem = StringField('Memory',
                      default="4G",
                      validators=[DataRequired()])


class NetworkConfigForm(FlaskForm):
    """Form for network configuration."""
    listen_port = IntegerField(
        'Listen Port',
        validators=[Optional(), NumberRange(min=1024, max=65535)]
    )
    bind_address = StringField('Bind Address', default='0.0.0.0')
    connect_to = SelectMultipleField(
        'Connect To',
        description='Select components to connect to'
    )


class SenderConfigForm(FlaskForm):
    """Form for sender configuration."""
    data_source = StringField(
        'Data Source',
        description='Path or URL to data source'
    )
    data_format = SelectField(
        'Data Format',
        choices=[
            ('raw', 'Raw'),
            ('hdf5', 'HDF5'),
            ('root', 'ROOT')
        ],
        default='raw'
    )
    chunk_size = StringField('Chunk Size', default='1M')
    test_data_size = StringField('Test Data Size', default='100M')
    test_data_pattern = SelectField(
        'Test Data Pattern',
        choices=[
            ('random', 'Random'),
            ('sequential', 'Sequential'),
            ('custom', 'Custom')
        ],
        default='random'
    )


class ReceiverConfigForm(FlaskForm):
    """Form for receiver configuration."""
    output_dir = StringField(
        'Output Directory',
        validators=[DataRequired()]
    )
    data_validation = BooleanField('Enable Data Validation', default=True)
    buffer_size = StringField('Buffer Size', default='64M')
    compression = BooleanField('Enable Compression', default=False)


class EmulatorConfigForm(FlaskForm):
    """Form for emulator configuration."""
    threads = IntegerField(
        'Threads',
        default=4,
        validators=[DataRequired(), NumberRange(min=1)]
    )
    latency = IntegerField(
        'Latency (ms)',
        default=50,
        validators=[DataRequired(), NumberRange(min=0)]
    )
    mem_footprint = FloatField(
        'Memory Footprint',
        default=0.05,
        validators=[DataRequired(), NumberRange(min=0)]
    )
    output_size = FloatField(
        'Output Size',
        default=0.001,
        validators=[DataRequired(), NumberRange(min=0)]
    )
    processing_type = SelectField(
        'Processing Type',
        choices=[
            ('cpu_intensive', 'CPU Intensive'),
            ('memory_intensive', 'Memory Intensive'),
            ('io_intensive', 'I/O Intensive')
        ],
        default='cpu_intensive'
    )


class TestDataForm(FlaskForm):
    """Form for test data configuration."""
    size = StringField('Data Size',
                       default="100M",
                       validators=[DataRequired()])


class LoadBalancerConfigForm(FlaskForm):
    """Form for load balancer configuration."""
    strategy = SelectField(
        'Strategy',
        choices=[
            ('round_robin', 'Round Robin'),
            ('least_loaded', 'Least Loaded'),
            ('consistent_hash', 'Consistent Hash')
        ],
        default='round_robin'
    )
    max_queue_size = StringField('Max Queue Size', default='100M')
    health_check_interval = IntegerField(
        'Health Check Interval (s)',
        default=5,
        validators=[NumberRange(min=1)]
    )
    backpressure_threshold = FloatField(
        'Backpressure Threshold',
        default=0.8,
        validators=[NumberRange(min=0, max=1)]
    )
    rebalance_threshold = FloatField(
        'Rebalance Threshold',
        default=0.2,
        validators=[NumberRange(min=0, max=1)]
    )


class AggregatorConfigForm(FlaskForm):
    """Form for aggregator configuration."""
    strategy = SelectField(
        'Strategy',
        choices=[
            ('ordered', 'Ordered'),
            ('unordered', 'Unordered'),
            ('time_window', 'Time Window')
        ],
        default='ordered'
    )
    buffer_size = StringField('Buffer Size', default='256M')
    max_delay = IntegerField(
        'Max Delay (ms)',
        default=1000,
        validators=[NumberRange(min=0)]
    )
    batch_size = IntegerField(
        'Batch Size',
        default=100,
        validators=[NumberRange(min=1)]
    )
    window_size = StringField('Window Size', default='1s')


class ComponentForm(FlaskForm):
    """Form for adding/editing components."""
    id = StringField('Component ID', validators=[DataRequired()])
    type = SelectField(
        'Type',
        choices=[
            ('receiver', 'Receiver'),
            ('emulator', 'Emulator'),
            ('sender', 'Sender'),
            ('load_balancer', 'Load Balancer'),
            ('aggregator', 'Aggregator')
        ],
        validators=[DataRequired()]
    )

    # Resources
    partition = StringField('Partition', validators=[DataRequired()])
    cpus_per_task = IntegerField(
        'CPUs per Task',
        default=4,
        validators=[DataRequired(), NumberRange(min=1)]
    )
    mem = StringField('Memory', default='4G', validators=[DataRequired()])

    # Network Configuration
    network_form = FormField(NetworkConfigForm)

    # Component-specific configuration
    emulator_config = FormField(EmulatorConfigForm)
    sender_config = FormField(SenderConfigForm)
    receiver_config = FormField(ReceiverConfigForm)
    load_balancer_config = FormField(LoadBalancerConfigForm)
    aggregator_config = FormField(AggregatorConfigForm)

    submit = SubmitField('Save')

    def validate_id(self, field: Any) -> None:
        """Validate component ID format."""
        if not field.data[0].isalpha():
            raise ValidationError('Component ID must start with a letter')
        if not field.data.replace('_', '').isalnum():
            raise ValidationError(
                'Component ID can only contain letters, numbers, and underscores'
            )


class EdgeForm(FlaskForm):
    """Form for adding data flow connections."""
    from_id = HiddenField('From Component')
    to_id = HiddenField('To Component')
    description = StringField(
        'Data Flow Description',
        description='Description of the data being transferred',
        validators=[DataRequired()]
    )
    data_type = StringField(
        'Data Type',
        description='Type of data being transferred'
    )
    buffer_size = StringField(
        'Buffer Size',
        default='1M',
        description='Buffer size for data transfer'
    )
    submit = SubmitField('Add Data Flow')


class ContainerConfigForm(FlaskForm):
    """Form for container configuration."""
    image_path = StringField(
        'Image Path',
        default='cpu-emu.sif',
        validators=[DataRequired()]
    )
