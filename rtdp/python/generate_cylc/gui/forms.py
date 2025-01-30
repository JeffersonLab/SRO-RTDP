from flask_wtf import FlaskForm
from wtforms import (
    StringField, SelectField, IntegerField, FloatField,
    SubmitField, ValidationError, HiddenField
)
from wtforms.validators import DataRequired, Optional, NumberRange


class WorkflowMetadataForm(FlaskForm):
    """Form for workflow metadata."""
    name = StringField('Workflow Name', validators=[DataRequired()])
    description = StringField('Description')
    submit = SubmitField('Save')


class PlatformConfigForm(FlaskForm):
    """Form for platform configuration."""
    name = StringField('Platform Name', validators=[DataRequired()])
    job_runner = SelectField('Job Runner',
                             choices=[('slurm', 'SLURM')],
                             validators=[DataRequired()])
    submit = SubmitField('Save')


class ResourcesForm(FlaskForm):
    """Form for component resources."""
    partition = StringField('Partition', validators=[DataRequired()])
    cpus_per_task = IntegerField('CPUs per Task',
                                 default=4,
                                 validators=[DataRequired(), NumberRange(min=1)])
    mem = StringField('Memory',
                      default="4G",
                      validators=[DataRequired()])


class NetworkForm(FlaskForm):
    """Form for network configuration."""
    port = IntegerField('Port', validators=[
                        DataRequired(), NumberRange(min=1024, max=65535)])
    bind_address = StringField('Bind Address', default="0.0.0.0")


class EmulatorConfigForm(FlaskForm):
    """Form for emulator configuration."""
    threads = IntegerField('Threads',
                           default=4,
                           validators=[DataRequired(), NumberRange(min=1)])
    latency = IntegerField('Latency (ms)',
                           default=50,
                           validators=[DataRequired(), NumberRange(min=0)])
    mem_footprint = FloatField('Memory Footprint',
                               default=0.05,
                               validators=[DataRequired(), NumberRange(min=0)])
    output_size = FloatField('Output Size',
                             default=0.001,
                             validators=[DataRequired(), NumberRange(min=0)])


class TestDataForm(FlaskForm):
    """Form for test data configuration."""
    size = StringField('Data Size',
                       default="100M",
                       validators=[DataRequired()])


class ComponentForm(FlaskForm):
    """Form for adding/editing components."""
    id = StringField('Component ID', validators=[DataRequired()])
    type = SelectField('Type',
                       choices=[
                           ('receiver', 'Receiver'),
                           ('emulator', 'Emulator'),
                           ('sender', 'Sender')
                       ],
                       validators=[DataRequired()])

    # Resources
    partition = StringField('Partition', validators=[DataRequired()])
    cpus_per_task = IntegerField('CPUs per Task',
                                 default=4,
                                 validators=[DataRequired(), NumberRange(min=1)])
    mem = StringField('Memory',
                      default="4G",
                      validators=[DataRequired()])

    # Network
    port = IntegerField('Port',
                        validators=[Optional(), NumberRange(min=1024, max=65535)])
    bind_address = StringField('Bind Address', default="0.0.0.0")

    # Emulator Configuration
    threads = IntegerField('Threads',
                           default=4,
                           validators=[Optional(), NumberRange(min=1)])
    latency = IntegerField('Latency (ms)',
                           default=50,
                           validators=[Optional(), NumberRange(min=0)])
    mem_footprint = FloatField('Memory Footprint',
                               default=0.05,
                               validators=[Optional(), NumberRange(min=0)])
    output_size = FloatField('Output Size',
                             default=0.001,
                             validators=[Optional(), NumberRange(min=0)])

    # Test Data
    data_size = StringField('Data Size', default="100M")

    submit = SubmitField('Save')

    def validate_id(self, field):
        """Validate component ID format."""
        if not field.data[0].isalpha():
            raise ValidationError("Component ID must start with a letter")
        if not field.data.replace('_', '').isalnum():
            raise ValidationError(
                "Component ID can only contain letters, numbers, and underscores")


class EdgeForm(FlaskForm):
    """Form for adding data flow connections."""
    from_id = HiddenField('From Component')
    to_id = HiddenField('To Component')
    description = StringField(
        'Data Flow Description',
        description='Description of the data being transferred',
        validators=[DataRequired()]
    )
    submit = SubmitField('Add Data Flow')


class ContainerConfigForm(FlaskForm):
    """Form for container configuration."""
    image_path = StringField('Container Image Path',
                             validators=[DataRequired()])
    submit = SubmitField('Save')
