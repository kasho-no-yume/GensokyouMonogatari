import json
from pathlib import Path
import tempfile
import unittest
import sys
sys.path.insert(0, str(Path(__file__).parent))
from program_ab_report import summarize


class ReportTest(unittest.TestCase):
    def test_ledger_window_filters_samples_and_requires_metadata(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary)
            metadata=dict(scenario='program_48',repeat=1,mode='program',seed=20261017,hardware='test',jdk='21',resolution='1920x1080',viewDistance=8,compression=256,warmupSeconds=30,sampleSeconds=180)
            (directory/'metadata.json').write_text(json.dumps(metadata))
            (directory/'window.json').write_text(json.dumps(dict(windowStartMs=1000,windowStopMs=181000)))
            (directory/'server.jsonl').write_text('\n'.join(json.dumps(row) for row in [dict(type='program_tickcost',wall=1000,nanos=42),dict(type='program_tickcost',wall=0,nanos=9999)]))
            (directory/'frames.csv').write_text('# header\n1000,70\n0,9999\n')
            (directory/'proxy.jsonl').write_text(json.dumps(dict(wall=1,dir='down',bytes=123)))
            result=summarize(directory,True)
            self.assertEqual(42,result['cpuP95Ns'])
            self.assertEqual(70,result['frameP95Ns'])
            self.assertEqual(123,result['connectionWireBytes'])
            self.assertTrue(result['windowComplete'])
            self.assertFalse(result['loadMatchesRequested'])
            (directory/'server.jsonl').write_text(json.dumps(dict(type='program_tickcost',wall=1000,nanos=42,members=48)))
            self.assertTrue(summarize(directory,True)['loadMatchesRequested'])
            metadata['warmupSeconds']=0
            (directory/'metadata.json').write_text(json.dumps(metadata))
            with self.assertRaises(ValueError):
                summarize(directory,True)


if __name__=='__main__':
    unittest.main()
