// PS1 Bedroom — flicker / z-fighting detector.
//
// Paste into the browser console with the room running (or inject it from a test harness), then:
//   await qaFlicker.run()                 // the room views and every place outside, prints a ranked report
//   await qaFlicker.run({ places: ['beach'] })
//
// How it works: every visible solid mesh is drawn in its own flat colour, and each viewpoint is rendered
// several times with the camera turned by a fraction of a degree. A pixel that belongs to different meshes in
// those renders, while the pixels around it agree (so it's not an object's outline), means two surfaces are
// fighting over the same depth. Pairs are ranked by how many pixels flip. The PS1 vertex
// wobble is included (the same snap code the game uses), since that's what made layered surfaces flicker.
(function () {
  const W = 320, H = 240;
  const rt = new THREE.WebGLRenderTarget(W, H, { minFilter: THREE.NearestFilter, magFilter: THREE.NearestFilter });
  const buf = new Uint8Array(W * H * 4);
  const describe = (m) => {
    const p = new THREE.Vector3(); m.getWorldPosition(p);
    const g = m.geometry && m.geometry.parameters ? Object.values(m.geometry.parameters).slice(0, 3).map((n) => +(+n).toFixed(3)).join('x') : '';
    return `${m.geometry ? m.geometry.type.replace('Geometry', '') : '?'}(${g}) @ ${p.toArray().map((n) => n.toFixed(2)).join(',')}${m.userData.tag ? ' [' + m.userData.tag + ']' : ''}`;
  };
  // The ID materials get the game's own vertex snap (copied from makePS1Material's compiled vertex shader), so
  // flicker caused by the PS1 wobble shows up too. Pass { snap: false } to test bare geometry only.
  let snapChunk = null;
  function findSnapChunk() {
    if (snapChunk !== null) return snapChunk;
    snapChunk = '';
    const probe = makePS1Material({ color: 0xffffff });
    const shader = { vertexShader: THREE.ShaderLib.phong.vertexShader, fragmentShader: THREE.ShaderLib.phong.fragmentShader, uniforms: {} };
    probe.onBeforeCompile(shader);
    const vs = shader.vertexShader;
    const a = vs.indexOf('vec4 mvPosition = vec4( transformed, 1.0 );'), b = vs.indexOf('// Cancel perspective division');
    if (a >= 0 && b > a) snapChunk = vs.slice(a, b);
    return snapChunk;
  }
  function idMaterial(id, side, snap) {
    const m = new THREE.MeshLambertMaterial({ color: 0x000000, emissive: new THREE.Color(((id >> 16) & 255) / 255, ((id >> 8) & 255) / 255, (id & 255) / 255), side, fog: false });
    const chunk = snap ? findSnapChunk() : '';
    if (chunk) {
      m.onBeforeCompile = (shader) => {
        shader.uniforms.uJitterGrid = ps1Uniforms.uJitterGrid;
        shader.vertexShader = 'uniform vec2 uJitterGrid;\nvarying vec3 vMacroPos;\n' + shader.vertexShader.replace('#include <project_vertex>', chunk);
      };
      m.customProgramCacheKey = () => 'qa_snap';
    }
    return m;
  }
  function idPass(meshes, snap) {
    const saved = [];
    meshes.forEach((m, i) => {
      saved.push(m.material);
      m.material = idMaterial(i + 1, Array.isArray(m.material) ? m.material[0].side : m.material.side, snap);
    });
    return () => meshes.forEach((m, i) => { m.material.dispose(); m.material = saved[i]; });
  }
  function grab(cam) {
    renderer.setRenderTarget(rt);
    renderer.setClearColor(0x000000, 1);
    renderer.clear();
    renderer.render(scene, cam);
    renderer.readRenderTargetPixels(rt, 0, 0, W, H, buf);
    renderer.setRenderTarget(null);
    const ids = new Uint32Array(W * H);
    for (let i = 0; i < W * H; i++) ids[i] = (buf[i * 4] << 16) | (buf[i * 4 + 1] << 8) | buf[i * 4 + 2];
    return ids;
  }
  function checkView(pos, target, meshes, pairs, label) {
    const cam = camera.clone();
    cam.aspect = W / H; cam.updateProjectionMatrix();
    // Tiny turns of the camera (no moves): everything shifts evenly on screen, so nothing slides in front of
    // anything else (no false alarms from parallax), but every vertex lands differently on the snap grid,
    // which is exactly the condition that makes layered surfaces flicker.
    const turns = [[0, 0], [0.0011, 0], [0, 0.0011], [-0.0017, 0.0009], [0.0023, -0.0014], [-0.0006, -0.0021], [0.0031, 0.0026]];
    const frames = turns.map(([yaw, pitch]) => {
      cam.position.set(pos[0], pos[1], pos[2]);
      cam.lookAt(target[0], target[1], target[2]);
      cam.rotateY(yaw); cam.rotateX(pitch);
      cam.updateMatrixWorld();
      return grab(cam);
    });
    const base = frames[0];
    let flips = 0;
    for (let y = 3; y < H - 3; y++) for (let x = 3; x < W - 3; x++) {
      const i = y * W + x, c = base[i];
      if (!c) continue;
      // inside a surface in the base frame (not on an outline), with a 2-pixel margin
      let interior = true;
      for (let dy = -3; dy <= 3 && interior; dy++) for (let dx = -3; dx <= 3; dx++) if (base[i + dy * W + dx] !== c) { interior = false; break; }
      if (!interior) continue;
      for (let f = 1; f < frames.length; f++) {
        const o = frames[f][i];
        if (o && o !== c) {
          const a = Math.min(c, o), b = Math.max(c, o), key = a + ':' + b;
          const e = pairs.get(key) || { a, b, px: 0, views: new Set() };
          e.px++; e.views.add(label); pairs.set(key, e);
          flips++;
          break;
        }
      }
    }
    return flips;
  }
  const ROOM_VIEWS = [
    ['default', [0.6, 3.15, 3.1], [0.1, 2.0, -1.2]],
    ['far corner', [4.1, 4.6, 3.2], [-1.2, 0.3, 0.0]],
    ['by the door', [-3.3, 3.2, 3.2], [1.5, 1.2, -1.5]],
    ['at the TV', [-0.6, 1.9, 1.6], [-2.05, 1.3, 0.65]],
    ['TV angled', [-0.4, 1.6, 0.0], [-2.05, 1.36, 0.7]],
    ['dresser', [-1.5, 2.0, 2.6], [-3.3, 1.5, 2.6]],
    ['desk', [0.2, 2.6, 0.6], [-1.3, 2.0, -2.0]],
    ['bed', [2.85, 2.0, 1.6], [2.85, 1.0, -2.5]],
    ['left wall low', [2.0, 0.6, 2.5], [-3.7, 2.5, 0.0]],
    ['ceiling', [0.6, 2.4, 1.2], [0.6, 4.65, -0.4]],
    ['floor items', [0.4, 1.4, 2.4], [-1.2, 0.05, 0.62]]
  ];
  const WINDOW_VIEWS = [
    ['window straight', [0.8, 2.9, -1.0], [0.8, 3.2, -12]],
    ['window left', [2.2, 2.9, -2.4], [-6, 3.6, -12]],
    ['window right', [-0.4, 2.9, -2.4], [8, 3.6, -12]],
    ['window from bed', [2.85, 2.2, 1.4], [0.8, 3.0, -8]],
    ['window from default view', [0.6, 3.15, 3.1], [0.9, 2.9, -6]],
    ['window from the chair', [0.2, 2.1, 0.4], [1.0, 2.9, -6]]
  ];
  async function run(opts = {}) {
    const wasVisible = new Map();
    const solids = () => {
      const list = [];
      scene.traverse((o) => {
        if ((o.isPoints || o.isLine || o.isSprite) && o.visible) { wasVisible.set(o, true); o.visible = false; return; } // rain, stars, wires
        if (!o.isMesh || !o.visible) return;
        let v = true; for (let p = o.parent; p; p = p.parent) if (!p.visible) { v = false; break; }
        if (!v) return;
        const m = Array.isArray(o.material) ? o.material[0] : o.material;
        if (!m || m.transparent || m.depthWrite === false || m.visible === false || m.colorWrite === false) { wasVisible.set(o, o.visible); o.visible = false; return; }
        list.push(o);
      });
      return list;
    };
    const report = [];
    const scan = (label, views) => {
      const meshes = solids();
      const restore = idPass(meshes, opts.snap !== false);
      const pairs = new Map();
      let total = 0;
      views.forEach(([name, pos, tgt]) => { total += checkView(pos, tgt, meshes, pairs, name); });
      restore();
      wasVisible.forEach((v, o) => { o.visible = v; }); wasVisible.clear();
      [...pairs.values()].filter((e) => e.px >= (opts.minPx || 6) && e.b <= meshes.length).sort((x, y) => y.px - x.px).slice(0, opts.top || 12)
        .forEach((e) => report.push({ scene: label, px: e.px, views: [...e.views].join(' / '), a: describe(meshes[e.a - 1]), b: describe(meshes[e.b - 1]) }));
      return total;
    };
    const startEnv = typeof outsideEnv !== 'undefined' ? outsideEnv : 'city';
    if (!opts.places || opts.room) scan('room', ROOM_VIEWS);
    const places = opts.places || Object.keys(OUTSIDE_ENVS);
    for (const p of places) {
      setOutsideEnv(p, true);
      await new Promise((r) => setTimeout(r, 150));
      scan('outside: ' + p, WINDOW_VIEWS);
    }
    setOutsideEnv(startEnv, true);
    console.table(report);
    return report;
  }
  window.qaFlicker = { run, checkView, ROOM_VIEWS, WINDOW_VIEWS };
})();
