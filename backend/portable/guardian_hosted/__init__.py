from pathlib import Path
import sys
# Share vetted validators and object-store/provider interfaces with the AWS backend.
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'src'))
