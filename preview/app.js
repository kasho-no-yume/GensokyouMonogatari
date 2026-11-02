import * as THREE from './vendor/three.module.js';
import { PreviewRenderer, liveCounts } from './renderer.js';

const DATA = 'data';
const LANG = '/src/main/resources/assets/gensokyou/lang/zh_cn.json';
const TPS = 20;

const canvas = document.getElementById('view');
const select = document.getElementById('card-select');
const titleEl = document.getElementById('title');
const metaEl = document.getElementById('meta');
const playBtn = document.getElementById('play');
const range = document.getElementById('range');
const tickEl = document.getElementById('tick');
const aliveEl = document.getElementById('alive');
const densityCanvas = document.getElementById('density');
const playhead = document.getElementById('playhead');
const speedEl = document.getElementById('speed');
const loopEl = document.getElementById('loop');
const errorEl = document.getElementById('error');

const renderer = new PreviewRenderer(canvas);
const camera = renderer.camera;
camera.rotation.order = 'YXZ';
// Dev hook: the acceptance check reads draw stats instead of eyeballing a screenshot.
window.__preview = {
  renderer,
  stats: () => ({ tick, card: card && card.card, ...renderer.renderer.info.render, bad: renderer.bad, drawn: renderer.drawn, grows: renderer.grows }),
  getCard: () => card,
};

let card = null;
let index = null;
let lang = {};
let tick = 0;
let playing = true;
let speed = 1;
let last = performance.now();
let density = null;
let densityPeak = 0;
let lastDrawn = -1;

// ---- camera ---------------------------------------------------------------------------------
const cam = { yaw: 0, pitch: 0 };
const keys = new Set();
const limit = new THREE.Vector3();

function applyRotation() {
  camera.rotation.set(cam.pitch, cam.yaw, 0);
}

function aimAt(target) {
  camera.lookAt(limit.set(target[0], target[1], target[2]));
  cam.yaw = camera.rotation.y;
  cam.pitch = camera.rotation.x;
}

function preset(name) {
  if (!card) return;
  const scene = card.scene;
  const boss = scene.boss;
  const stub = scene.stub;
  const mid = [boss[0], (boss[1] + stub[1]) / 2, (boss[2] + stub[2]) / 2];
  switch (name) {
    case 'eyes':
      camera.position.set(stub[0], scene.stubEyes[1], stub[2]);
      aimAt(boss);
      break;
    case 'top':
      camera.position.set(mid[0], Math.max(boss[1], stub[1]) + 26, mid[2] + 0.01);
      aimAt([mid[0], mid[1], mid[2]]);
      break;
    case 'side':
      camera.position.set(mid[0] + 34, mid[1] + 4, mid[2]);
      aimAt(mid);
      break;
    case 'boss':
      camera.position.set(boss[0], boss[1] + 2, boss[2] - 14);
      aimAt([stub[0], stub[1] + 1, stub[2]]);
      break;
    default:
      break;
  }
}

canvas.addEventListener('pointerdown', (event) => {
  canvas.setPointerCapture(event.pointerId);
  canvas.dataset.drag = '1';
});
canvas.addEventListener('pointerup', (event) => {
  canvas.releasePointerCapture(event.pointerId);
  canvas.dataset.drag = '';
});
canvas.addEventListener('pointermove', (event) => {
  if (canvas.dataset.drag !== '1') return;
  cam.yaw -= event.movementX * 0.0026;
  cam.pitch = Math.max(-1.5, Math.min(1.5, cam.pitch - event.movementY * 0.0026));
  applyRotation();
});
canvas.addEventListener('wheel', (event) => {
  event.preventDefault();
  const dir = new THREE.Vector3();
  camera.getWorldDirection(dir);
  camera.position.addScaledVector(dir, -Math.sign(event.deltaY) * 1.5);
}, { passive: false });

addEventListener('keydown', (event) => {
  if (event.target instanceof HTMLSelectElement || event.target instanceof HTMLInputElement) return;
  keys.add(event.code);
  if (event.code === 'Space') { togglePlay(); event.preventDefault(); }
  if (event.code === 'ArrowLeft') { tick = Math.max(0, tick - 1); range.value = String(tick); }
  if (event.code === 'ArrowRight') { tick = Math.min(card ? card.ticks : 0, tick + 1); range.value = String(tick); }
  if (event.key >= '1' && event.key <= '4') preset(['eyes', 'top', 'side', 'boss'][Number(event.key) - 1]);
});
addEventListener('keyup', (event) => keys.delete(event.code));

function moveCamera(dt) {
  const forward = new THREE.Vector3();
  camera.getWorldDirection(forward);
  const right = new THREE.Vector3().crossVectors(forward, camera.up).normalize();
  const step = (keys.has('ShiftLeft') || keys.has('ShiftRight') ? 28 : 9) * dt;
  const move = new THREE.Vector3();
  if (keys.has('KeyW')) move.addScaledVector(forward, step);
  if (keys.has('KeyS')) move.addScaledVector(forward, -step);
  if (keys.has('KeyD')) move.addScaledVector(right, step);
  if (keys.has('KeyA')) move.addScaledVector(right, -step);
  if (keys.has('KeyE')) move.y += step;
  if (keys.has('KeyQ')) move.y -= step;
  if (move.lengthSq() > 0) camera.position.add(move);
}

// ---- playback -------------------------------------------------------------------------------
function togglePlay() {
  if (!card) return;
  if (!playing && tick >= card.ticks) tick = 0;
  playing = !playing;
  playBtn.textContent = playing ? '❚❚' : '▶';
}

function currentCard() {
  if (!index) return null;
  const value = select.value;
  return index.cards.find((entry) => `${entry.owner}:${entry.index}` === value) || null;
}

// 逐 tick 存活数：由成员首末采样做区间计数（见 renderer.js 的 liveCounts），不碰任何运动语义。
function buildDensity(loaded) {
  return liveCounts(loaded);
}

function drawDensity() {
  if (!density) return;
  const dpr = Math.min(devicePixelRatio || 1, 2);
  const w = Math.max(1, Math.round(densityCanvas.clientWidth));
  const h = Math.max(1, Math.round(densityCanvas.clientHeight));
  densityCanvas.width = Math.round(w * dpr);
  densityCanvas.height = Math.round(h * dpr);
  const ctx = densityCanvas.getContext('2d');
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  const peak = Math.max(1, densityPeak);
  ctx.fillStyle = 'rgba(120,150,220,.38)';
  for (let x = 0; x < w; x++) {
    const t = Math.min(density.length - 1, Math.floor((x / w) * card.ticks));
    const bar = Math.max(1, Math.round((density[t] / peak) * (h - 2)));
    ctx.fillRect(x, h - bar, 1, bar);
  }
  ctx.fillStyle = 'rgba(255,134,200,.30)';
  ctx.fillRect(0, 0, w, 1);
}

async function loadCard(entry) {
  const response = await fetch(`${DATA}/${entry.file}`);
  if (!response.ok) throw new Error(`${entry.file}: HTTP ${response.status}`);
  card = await response.json();
  for (const style of card.styles) style._color = null;
  for (const member of card.members) {
    // Pre-split the laser axis so the hot loop does no packing work.
    if (member.laser) member.life = member.life;
  }
  renderer.setCard(card);
  range.max = String(card.ticks);
  range.step = '0.25';
  tick = 0;
  playing = true;
  playBtn.textContent = '❚❚';
  density = buildDensity(card);
  densityPeak = density.reduce((a, b) => (b > a ? b : a), 0);
  lastDrawn = -1;
  drawDensity();
  const name = lang[card.card] || card.card;
  titleEl.textContent = `${name}`;
  const ownerName = lang[`entity.gensokyou.${card.owner}`] || card.owner;
  metaEl.textContent = `${ownerName} · 第 ${card.index} 张 · ${(card.ticks / TPS).toFixed(1)}s · `
    + `${card.members.length} 成员 · stride ${card.stride}`;
  preset('eyes');
}

function frame(now) {
  const dt = Math.min(0.1, (now - last) / 1000);
  last = now;
  if (card) {
    moveCamera(dt);
    if (playing) {
      tick += dt * TPS * speed;
      if (tick >= card.ticks) {
        if (loopEl.checked) tick = 0;
        else { tick = card.ticks; playing = false; playBtn.textContent = '▶'; }
      }
      range.value = String(tick);
    }
    renderer.render(tick);
    tickEl.textContent = `${Math.floor(tick)} / ${card.ticks} · ${(tick / TPS).toFixed(2)}s`;
    if (renderer.drawn !== lastDrawn) {
      lastDrawn = renderer.drawn;
      aliveEl.innerHTML = `存活 <b>${renderer.drawn}</b> · 峰值 ${densityPeak}`;
    }
    const ratio = Math.min(1, Math.max(0, tick / Math.max(1, card.ticks)));
    playhead.style.left = `calc(8px + (100% - 16px) * ${ratio})`;
  }
  requestAnimationFrame(frame);
}

function resize() {
  renderer.resize(innerWidth, innerHeight);
  drawDensity();
}
addEventListener('resize', resize);

range.addEventListener('input', () => {
  tick = Number(range.value);
});
playBtn.addEventListener('click', togglePlay);
speedEl.addEventListener('change', () => { speed = Number(speedEl.value); });
for (const button of document.querySelectorAll('#cams button')) {
  button.addEventListener('click', () => preset(button.dataset.preset));
}

async function main() {
  resize();
  requestAnimationFrame(frame);
  try {
    const [indexResponse, langResponse] = await Promise.all([
      fetch(`${DATA}/index.json`),
      fetch(LANG).catch(() => null),
    ]);
    if (!indexResponse.ok) throw new Error(`data/index.json: HTTP ${indexResponse.status}`);
    index = await indexResponse.json();
    if (langResponse && langResponse.ok) lang = await langResponse.json();

    const owners = new Map();
    for (const entry of index.cards) {
      if (!owners.has(entry.owner)) owners.set(entry.owner, []);
      owners.get(entry.owner).push(entry);
    }
    for (const [owner, cards] of owners) {
      const group = document.createElement('optgroup');
      group.label = lang[`entity.gensokyou.${owner}`] || owner;
      for (const entry of cards.sort((a, b) => a.index - b.index)) {
        const option = document.createElement('option');
        option.value = `${entry.owner}:${entry.index}`;
        option.textContent = `#${entry.index} ${lang[entry.card] || entry.card}`
          + `  (${(entry.ticks / TPS).toFixed(1)}s)`;
        group.appendChild(option);
      }
      select.appendChild(group);
    }
    select.addEventListener('change', () => {
      const entry = currentCard();
      if (entry) loadCard(entry).catch(showError);
    });
    const first = index.cards[0];
    select.value = `${first.owner}:${first.index}`;
    await loadCard(first);
  } catch (error) {
    showError(error);
  }
}

function showError(error) {
  errorEl.style.display = 'block';
  errorEl.innerHTML = `<div style="font-size:14px;margin-bottom:6px">预览数据没找到</div>`
    + `<div style="color:#8b93b8">${error.message}</div>`
    + `<div style="margin-top:8px;color:#8b93b8">先跑一次导出：<br>`
    + `<code>tools\\gradle_task.ps1 exportDanmakuPreview</code></div>`;
  console.error(error);
}

main();
