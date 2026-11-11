// Предмет из Poly Haven (CC0, gltf 1k) → лёгкий glb для игры: текстуры 512 webp, упрощение сетки, meshopt.
//   node tools/props/pack.mjs вход.gltf web/models/props/имя.glb [доля треугольников 0..1] [размер текстур]
// Библиотеки — /home/user/tools/gltf (GLTF=путь к node_modules/ — другая папка). Список предметов и лицензии — web/models/props/README.txt
const M = process.env.GLTF || '/home/user/tools/gltf/node_modules/';
const { NodeIO } = await import(M + '@gltf-transform/core/dist/index.js');
const ext = await import(M + '@gltf-transform/extensions/dist/index.js');
const fn = await import(M + '@gltf-transform/functions/dist/index.js');
const { MeshoptEncoder, MeshoptDecoder, MeshoptSimplifier } = await import(M + 'meshoptimizer/index.js');
const sharp = (await import(M + 'sharp/dist/index.mjs')).default;
await MeshoptEncoder.ready; await MeshoptDecoder.ready; await MeshoptSimplifier.ready;
const [inp, out, ratio = '1', size = '512'] = process.argv.slice(2);
const io = new NodeIO().registerExtensions(ext.ALL_EXTENSIONS).registerDependencies({ 'meshopt.encoder': MeshoptEncoder, 'meshopt.decoder': MeshoptDecoder });
const doc = await io.read(inp);
const steps = [fn.dedup(), fn.weld()];
if (+ratio < 1) steps.push(fn.simplify({ simplifier: MeshoptSimplifier, ratio: +ratio, error: .002 }));
steps.push(fn.textureCompress({ encoder: sharp, targetFormat: 'webp', resize: [+size, +size], quality: 82 }), fn.prune(), fn.meshopt({ encoder: MeshoptEncoder, level: 'medium' }));
await doc.transform(...steps);
await io.write(out, doc);
const tris = doc.getRoot().listMeshes().reduce((s, m) => s + m.listPrimitives().reduce((a, p) => a + (p.getIndices() ? p.getIndices().getCount() / 3 : 0), 0), 0);
console.log(out.split('/').pop(), Math.round((await import('fs')).statSync(out).size / 1024) + ' КБ', tris + ' треуг.');
