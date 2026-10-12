"""Run the full isolated synthetic scenario against localhost or a configured HTTPS proxy."""
import os
import pathlib
import runpy
import sys
import uuid
if '--prefix' not in sys.argv:
    sys.argv.extend(['--prefix', 'smoke-' + uuid.uuid4().hex[:10]])
if '--complete' not in sys.argv:
    sys.argv.append('--complete')
runpy.run_path(str(pathlib.Path(__file__).with_name('seed-demo.py')), run_name='__main__')
