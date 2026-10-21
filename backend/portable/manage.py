#!/usr/bin/env python3
import os
import sys
from bootstrap import load_config
load_config()
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'guardian_hosted.settings')
from django.core.management import execute_from_command_line
execute_from_command_line(sys.argv)
