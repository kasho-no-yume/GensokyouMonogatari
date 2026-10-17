import asyncio
import sys
import time
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from net_jitter_proxy import LagConfig, _relay


class RelayTest(unittest.IsolatedAsyncioTestCase):
    async def test_delay_is_per_arrival_not_serial_per_chunk_and_order_is_preserved(self):
        class Reader:
            def __init__(self):
                self.index = 0

            async def read(self, _):
                self.index += 1
                return bytes([self.index]) if self.index <= 8 else b''

        class Writer:
            def __init__(self):
                self.data = bytearray()

            def write(self, data):
                self.data.extend(data)

            async def drain(self):
                pass

            def close(self):
                pass

        writer = Writer()
        started = time.monotonic()
        await _relay(Reader(), writer, LagConfig(100, 0, False), False)
        self.assertEqual(bytes(range(1, 9)), bytes(writer.data))
        self.assertGreaterEqual(time.monotonic() - started, .09)
        self.assertLess(time.monotonic() - started, .6)


if __name__ == '__main__':
    unittest.main()
