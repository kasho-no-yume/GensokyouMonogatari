# tune-resonance-relay-radius

## Why

2 阶 ±10 的候选发现半径容不下八方归元（footprint 约 ±6）与结界破坏仪式（约 ±8），三核无法同时入半径。

## What Changes

- `power.resonanceBaseRadius` 默认值 10 → 40，各阶半径变为 ±40 / ±80 / ±160 / ±320。
- 配额、速率、结算、账本、链接存续语义全部不变。

## Capabilities

### Modified Capabilities

- `resonance-relay-ritual`: 放宽候选发现半径基准值与阶级表，并明确半径只管发现、不管存续。

## Impact

- 配置默认值改动一处，代码零改动。
- `add-barrier-break-ritual` 的三核布局依赖本次放宽。
