// Preview renderer: draws ONLY what the exported projection says.
//
// Hard rule (openspec add-danmaku-preview-site, requirement 1): nothing in this file may decide
// motion, visibility or damage. No card names, no pattern names, no path maths. Every position,
// alpha, brightness, size and beam length comes from the baked data.
import * as THREE from './vendor/three.module.js';

const ASSET_ROOT = '/src/main/resources/assets';
const DEG = Math.PI / 180;

export function assetUrl(id) {
  const i = id.indexOf(':');
  return `${ASSET_ROOT}/${id.slice(0, i)}/${id.slice(i + 1)}`;
}

const scratch = new Float64Array(11);

/** Linear interpolation between baked samples. Null outside the member's recorded life. */
function sampleAt(member, tick) {
  const s = member.s;
  const n = s.length;
  if (n === 0) return null;
  if (tick < s[0][0] || tick > s[n - 1][0]) return null;
  let lo = 0;
  let hi = n - 1;
  while (lo + 1 < hi) {
    const mid = (lo + hi) >> 1;
    if (s[mid][0] <= tick) lo = mid; else hi = mid;
  }
  const a = s[lo];
  const b = s[hi];
  const span = b[0] - a[0];
  const w = span <= 0 ? 0 : (tick - a[0]) / span;
  scratch[0] = tick;
  for (let i = 1; i <= 6; i++) scratch[i] = a[i] + (b[i] - a[i]) * w;
  scratch[7] = a.length > 7 ? a[7] + (b[7] - a[7]) * w : 0;
  for (let i = 8; i <= 10; i++) scratch[i] = a.length > 7 ? a[i] + (b[i] - a[i]) * w : 0;
  return scratch;
}

/** 逐 tick 存活数：成员的首个与末个采样就是它在画面上的存活区间。 */
export function liveCounts(card) {
  const n = card.ticks + 1;
  const delta = new Int32Array(n + 1);
  for (const member of card.members) {
    const s = member.s;
    if (!s.length) continue;
    const from = Math.max(0, Math.ceil(s[0][0]));
    const to = Math.min(card.ticks, Math.floor(s[s.length - 1][0]));
    if (to < from) continue;
    delta[from] += 1;
    delta[to + 1] -= 1;
  }
  const values = new Int32Array(n);
  let running = 0;
  for (let i = 0; i < n; i++) {
    running += delta[i];
    values[i] = running;
  }
  return values;
}

/** Triangle soup with per-vertex RGBA, rebuilt every frame like the Minecraft immediate renderer. */
class Batch {
  constructor(scene, texture, blending, triangles) {
    this.geometry = new THREE.BufferGeometry();
    this.allocate(triangles);
    this.material = new THREE.MeshBasicMaterial({
      map: texture, transparent: true, vertexColors: true, depthWrite: false,
      side: THREE.DoubleSide, blending, toneMapped: false,
    });
    this.mesh = new THREE.Mesh(this.geometry, this.material);
    this.mesh.frustumCulled = false;
    this.n = 0;
    this.bad = 0;
    this.grows = 0;
    scene.add(this.mesh);
  }

  /** (Re)allocate the vertex buffers, preserving whatever is already in them. */
  allocate(triangles) {
    const maxVerts = Math.max(3, Math.ceil(triangles) * 3);
    if (maxVerts <= this.maxVerts) return;
    const position = new Float32Array(maxVerts * 3);
    const uv = new Float32Array(maxVerts * 2);
    const color = new Float32Array(maxVerts * 4);
    if (this.position) {
      position.set(this.position.subarray(0, this.maxVerts * 3));
      uv.set(this.uv.subarray(0, this.maxVerts * 2));
      color.set(this.color.subarray(0, this.maxVerts * 4));
    }
    this.maxVerts = maxVerts;
    this.position = position;
    this.uv = uv;
    this.color = color;
    this.posAttr = new THREE.BufferAttribute(position, 3).setUsage(THREE.DynamicDrawUsage);
    this.uvAttr = new THREE.BufferAttribute(uv, 2).setUsage(THREE.DynamicDrawUsage);
    this.colorAttr = new THREE.BufferAttribute(color, 4).setUsage(THREE.DynamicDrawUsage);
    this.geometry.setAttribute('position', this.posAttr);
    this.geometry.setAttribute('uv', this.uvAttr);
    this.geometry.setAttribute('color', this.colorAttr);
    this.geometry.setDrawRange(0, 0);
  }

  begin() { this.n = 0; this.bad = 0; }

  vert(x, y, z, u, v, r, g, b, a) {
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) { this.bad++; return; }
    // Grow instead of dropping: a dropped vertex is a bullet that silently stops existing.
    if (this.n >= this.maxVerts) { this.allocate(Math.ceil(this.maxVerts / 3 * 1.5)); this.grows++; }
    const p = this.n * 3;
    const q = this.n * 2;
    const c = this.n * 4;
    this.position[p] = x; this.position[p + 1] = y; this.position[p + 2] = z;
    this.uv[q] = u; this.uv[q + 1] = v;
    this.color[c] = r; this.color[c + 1] = g; this.color[c + 2] = b; this.color[c + 3] = a;
    this.n++;
  }

  end() {
    this.posAttr.needsUpdate = true;
    this.uvAttr.needsUpdate = true;
    this.colorAttr.needsUpdate = true;
    this.geometry.setDrawRange(0, this.n);
  }
}

export class PreviewRenderer {
  constructor(canvas) {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.setClearColor(0x0b0d16, 1);
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(70, 1, 0.05, 500);
    this.batches = new Map();
    this.textures = new Map();
    this.grid = null;
    this.markers = new THREE.Group();
    this.scene.add(this.markers);
    this.right = new THREE.Vector3();
    this.up = new THREE.Vector3();
    this.forward = new THREE.Vector3();
    this.card = null;
    this.drawn = 0;
    this.bad = 0;
    this.grows = 0;
    this.lastTick = 0;
  }

  texture(id, repeat) {
    const key = id + (repeat ? '|r' : '');
    let texture = this.textures.get(key);
    if (texture) return texture;
    texture = new THREE.TextureLoader().load(assetUrl(id));
    texture.colorSpace = THREE.SRGBColorSpace;
    if (repeat) texture.wrapS = texture.wrapT = THREE.RepeatWrapping;
    this.textures.set(key, texture);
    return texture;
  }

  batch(id, additive, triangles) {
    const key = id + '|' + (additive ? 'add' : 'blend');
    let batch = this.batches.get(key);
    if (!batch) {
      batch = new Batch(this.scene, this.texture(id), additive ? THREE.AdditiveBlending : THREE.NormalBlending, triangles);
      this.batches.set(key, batch);
    }
    return batch;
  }

  resize(w, h) {
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
  }

  setCard(card) {
    this.card = card;
    // Size the bullet buffer from the real peak, not the average: the average under-allocates
    // exactly on the dense cards, and an under-sized buffer silently drops bullets.
    const peak = liveCounts(card).reduce((a, b) => (b > a ? b : a), 0);
    const quads = Math.max(1024, Math.ceil(peak * 1.3));
    for (const batch of this.batches.values()) {
      this.scene.remove(batch.mesh);
      batch.geometry.dispose();
      batch.material.dispose();
    }
    this.batches.clear();
    this.bulletCapacity = quads * 2;
    this.batch(card.renderer.bullet, false, this.bulletCapacity);

    if (this.grid) { this.scene.remove(this.grid); this.grid.geometry.dispose(); this.grid.material.dispose(); }
    const ground = card.scene.groundTop;
    this.grid = new THREE.GridHelper(80, 80, 0x2a3350, 0x161c2c);
    this.grid.position.y = ground;
    this.scene.add(this.grid);

    this.markers.clear();
    const boss = card.scene.boss;
    const bossMarker = new THREE.Mesh(
      new THREE.OctahedronGeometry(0.6, 0),
      new THREE.MeshBasicMaterial({ color: 0xff86c8, wireframe: true }),
    );
    bossMarker.position.set(boss[0], boss[1], boss[2]);
    this.markers.add(bossMarker);

    const stub = card.scene.stub;
    const post = new THREE.Mesh(
      new THREE.BoxGeometry(0.6, 1.8, 0.6),
      new THREE.MeshBasicMaterial({ color: 0x6ad3ff, transparent: true, opacity: 0.22 }),
    );
    post.position.set(stub[0], stub[1] + 0.9, stub[2]);
    this.markers.add(post);
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(0.75, 0.9, 40),
      new THREE.MeshBasicMaterial({ color: 0x6ad3ff, transparent: true, opacity: 0.5, side: THREE.DoubleSide }),
    );
    ring.rotation.x = -Math.PI / 2;
    ring.position.set(stub[0], ground + 0.02, stub[2]);
    this.markers.add(ring);

    for (const warning of card.warnings) {
      if (!warning.laser) continue;
      this.batch(card.renderer.laser, true, 2048);
      break;
    }
  }

  /** Camera basis in world space (billboards are drawn like the Minecraft renderer draws them). */
  updateBasis() {
    this.camera.updateMatrixWorld();
    this.right.setFromMatrixColumn(this.camera.matrixWorld, 0).normalize();
    this.up.setFromMatrixColumn(this.camera.matrixWorld, 1).normalize();
    this.forward.setFromMatrixColumn(this.camera.matrixWorld, 2).normalize().negate();
  }

  quad(batch, x, y, z, half, r, g, b, a, angle, uvScale, tag) {
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z) || !Number.isFinite(half) || !Number.isFinite(angle === undefined ? 0 : angle)) {
      this.badLabel = 'quad:' + (tag || '?') + ':' + [x, y, z, half, angle].join(',');
      return;
    }
    const right = this.right;
    const up = this.up;
    let rx = right.x, ry = right.y, rz = right.z;
    let ux = up.x, uy = up.y, uz = up.z;
    if (angle) {
      const c = Math.cos(angle);
      const s = Math.sin(angle);
      const nrx = rx * c + ux * s, nry = ry * c + uy * s, nrz = rz * c + uz * s;
      const nux = -rx * s + ux * c, nuy = -ry * s + uy * c, nuz = -rz * s + uz * c;
      rx = nrx; ry = nry; rz = nrz; ux = nux; uy = nuy; uz = nuz;
    }
    const ax = rx * half, ay = ry * half, az = rz * half;
    const bx = ux * half, by = uy * half, bz = uz * half;
    const v = uvScale || 1;
    batch.vert(x - ax - bx, y - ay - by, z - az - bz, 0, v, r, g, b, a);
    batch.vert(x + ax - bx, y + ay - by, z + az - bz, 1, v, r, g, b, a);
    batch.vert(x + ax + bx, y + ay + by, z + az + bz, 1, 0, r, g, b, a);
    batch.vert(x - ax - bx, y - ay - by, z - az - bz, 0, v, r, g, b, a);
    batch.vert(x + ax + bx, y + ay + by, z + az + bz, 1, 0, r, g, b, a);
    batch.vert(x - ax + bx, y - ay + by, z - az + bz, 0, 0, r, g, b, a);
  }

  star(card, styleIndex, x, y, z, half, angle, r, g, b, a) {
    if (!Number.isFinite(half) || !Number.isFinite(angle)) { this.badLabel = 'star'; return; }
    const style = card.styles[styleIndex];
    const outline = style.outline;
    const batch = this.batch(style.texture, false, this.bulletCapacity);
    const cos = Math.cos(angle);
    const sin = Math.sin(angle);
    const right = this.right;
    const up = this.up;
    const forward = this.forward;
    const h = half * (style.half || 0.25);
    // Local (lx, ly, lz) in the camera-facing frame, tumbled around local X like the game does.
    const put = (lx, ly, lz, u, v) => {
      const ty = ly * cos - lz * sin;
      const tz = ly * sin + lz * cos;
      batch.vert(
        x + right.x * lx + up.x * ty + forward.x * tz,
        y + right.y * lx + up.y * ty + forward.y * tz,
        z + right.z * lx + up.z * ty + forward.z * tz,
        u, v, r, g, b, a,
      );
    };
    const P = [];
    for (let i = 0; i < 10; i++) P.push([outline[i][0] * half, outline[i][1] * half]);
    for (let i = 0; i < 10; i++) {
      const j = (i + 1) % 10;
      const [px, py] = P[i];
      const [qx, qy] = P[j];
      put(0, 0, h, 0.5, 0.5); put(px, py, h, 0, 0); put(qx, qy, h, 1, 0);
      put(qx, qy, h, 1, 0); put(0, 0, -h, 0.5, 0.5); put(qx, qy, -h, 1, 0);
      put(px, py, -h, 0, 0); put(px, py, h, 0, 1); put(qx, qy, h, 1, 1);
      put(qx, qy, -h, 1, 0); put(px, py, -h, 0, 0); put(px, py, h, 0, 1);
    }
  }

  render(tick) {
    const card = this.card;
    if (!card) return;
    this.updateBasis();
    for (const batch of this.batches.values()) batch.begin();
    this.drawn = 0;
    this.lastTick = tick;

    for (const member of card.members) {
      const s = sampleAt(member, tick);
      if (!s) continue;
      const style = card.styles[member.style];
      const tint = style._color || (style._color = new THREE.Color(style.tint));
      const alpha = s[4];
      if (alpha <= 0.002) continue;
      this.drawn++;
      const brightness = s[5];
      const r = tint.r * brightness;
      const g = tint.g * brightness;
      const b = tint.b * brightness;
      const x = s[1], y = s[2], z = s[3];
      const age = Math.max(0, tick - member.born);

      if (member.laser) {
        this.renderLaser(card, member, style, x, y, z, s, age, alpha, r, g, b);
        continue;
      }
      const size = s[6];
      const half = style.radius * style.scale * size;
      if (half <= 0) continue;
      if (style.mesh === 'star') {
        const tumble = style.tumbleAmp > 0 && style.tumblePeriod > 0
          ? style.tumbleAmp * Math.sin(2 * Math.PI * age / style.tumblePeriod) * DEG
          : 0;
        this.star(card, member.style, x, y, z, half, tumble, r, g, b, alpha);
      } else {
        this.quad(this.batch(style.texture, false, this.bulletCapacity), x, y, z, half, r, g, b, alpha, 0, 1, 'bullet');
      }
    }

    this.renderWarnings(card, tick);
    this.renderBillboards(card, tick);
    this.renderGuides(card, tick);

    for (const batch of this.batches.values()) batch.end();
    this.bad = 0;
    this.grows = 0;
    for (const batch of this.batches.values()) {
      this.bad += batch.bad;
      this.grows += batch.grows;
    }
    this.renderer.render(this.scene, this.camera);
  }

  renderLaser(card, member, style, x, y, z, s, age, alpha, r, g, b) {
    const length = s[7];
    const envelope = Math.max(0, Math.min(1, Math.min(age / 3, (member.life - age) / 3)));
    if (envelope <= 0) return;
    const radius = style.radius * s[6] * envelope;
    if (radius <= 0) return;
    const dir = [s[8], s[9], s[10]];
    const h = Math.hypot(dir[0], dir[2]);
    const rx = h < 1e-6 ? 1 : dir[2] / h;
    const rz = h < 1e-6 ? 0 : -dir[0] / h;

    const circle = this.batch(card.renderer.circle, true, 2048);
    this.quad(circle, x, y, z, radius * 2.2, 1 - r, 1 - g, 1 - b,
      200 / 255 * envelope * alpha, age * 1.4 * DEG, 1, 'ring');

    if (length >= 0.001) {
      const beam = this.batch(card.renderer.laser, true, 4096);
      this.ribbon(beam, x, y, z, dir, rx, rz, length, radius * 1.9, r, g, b, 70 / 255 * envelope * alpha);
      this.ribbon(beam, x, y, z, dir, rx, rz, length, radius, r, g, b, 235 / 255 * envelope * alpha);
      this.ribbon(beam, x, y, z, dir, rx, rz, length, radius * 0.45,
        0.25 * r + 0.75, 0.25 * g + 0.75, 0.25 * b + 0.75, envelope * alpha);
    }

    const cap = this.batch(card.renderer.cap, true, 2048);
    const cr = 0.25 * r + 0.75, cg = 0.25 * g + 0.75, cb = 0.25 * b + 0.75;
    const capAlpha = 235 / 255 * envelope * alpha;
    this.quad(cap, x, y, z, radius, cr, cg, cb, capAlpha, 0, 1, 'cap0');
    if (length >= 0.001) {
      this.quad(cap, x + dir[0] * length, y + dir[1] * length, z + dir[2] * length,
        radius, cr, cg, cb, capAlpha, 0, 1, 'cap1');
    }
  }

  ribbon(batch, x, y, z, dir, rx, rz, length, width, r, g, b, a) {
    if (!Number.isFinite(length) || !Number.isFinite(width) || !Number.isFinite(rx) || !Number.isFinite(rz)) {
      this.badLabel = 'ribbon';
      return;
    }
    const ax = rx * width, az = rz * width;
    const tx = x + dir[0] * length, ty = y + dir[1] * length, tz = z + dir[2] * length;
    // uv v repeats along the beam exactly like the game's emitBeam.
    batch.vert(x - ax, y, z - az, 0, 0, r, g, b, a);
    batch.vert(x + ax, y, z + az, 1, 0, r, g, b, a);
    batch.vert(tx + ax, ty, tz + az, 1, length, r, g, b, a);
    batch.vert(x - ax, y, z - az, 0, 0, r, g, b, a);
    batch.vert(tx + ax, ty, tz + az, 1, length, r, g, b, a);
    batch.vert(tx - ax, ty, tz - az, 0, length, r, g, b, a);
  }

  renderWarnings(card, tick) {
    for (const warning of card.warnings) {
      if (tick < warning.from || tick >= warning.until) continue;
      const src = warning.src;
      const dir = warning.dir;
      if (warning.laser) {
        const h = Math.hypot(dir[0], dir[2]);
        const rx = h < 1e-6 ? 1 : dir[2] / h;
        const rz = h < 1e-6 ? 0 : -dir[0] / h;
        const styleRadius = card.styles[warning.style].radius;
        this.ribbon(this.batch(card.renderer.laser, true, 4096), src[0], src[1], src[2], dir, rx, rz,
          warning.beam, styleRadius * 0.22, 1, 0, 0, 100 / 255);
        const circle = this.batch(card.renderer.circle, true, 2048);
        this.quad(circle, src[0], src[1], src[2], styleRadius * 2.2, 1, 0, 0, 140 / 255, tick * 1.4 * DEG, 1, 'warnRing');
      } else {
        const batch = this.batch(card.renderer.warning, true, 2048);
        this.quad(batch, src[0], src[1], src[2], 0.08, 1, 0.25, 0.25, 170 / 255, 0, 1, 'warnDot');
      }
    }
  }

  renderBillboards(card, tick) {
    for (const billboard of card.billboards) {
      if (Math.abs(billboard.t - tick) > 0.75) continue;
      const tint = new THREE.Color(billboard.tint);
      const batch = this.batch(billboard.texture, true, 2048);
      this.quad(batch, billboard.pos[0], billboard.pos[1], billboard.pos[2],
        billboard.size / 2, tint.r, tint.g, tint.b, 200 / 255, billboard.angle, 1, 'billboard');
    }
  }

  renderGuides(card, tick) {
    for (const guide of card.guides) {
      if (Math.abs(guide.t - tick) > 0.75) continue;
      const batch = this.batch(card.renderer.bullet, false, 4096);
      for (const point of guide.p) {
        this.quad(batch, point[0], point[1], point[2], 0.035, 80 / 255, 160 / 255, 255 / 255, 100 / 255, 0, 1, 'guide');
      }
    }
  }
}
