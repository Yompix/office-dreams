// Распаковывает GLB игры для Godot: снимает сжатие meshopt и квантование (браузерные форматы, Godot их не читает).
// Запуск: node tools/godot/unpack_glb.mjs <из папки> <в папку>. Нужен npm-пакет glTF-Transform в $GLTF_MODULES
// (по умолчанию /home/user/tools/gltf: npm i @gltf-transform/core @gltf-transform/extensions @gltf-transform/functions meshoptimizer).
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
const MOD = process.env.GLTF_MODULES || '/home/user/tools/gltf';
const req = createRequire(path.join(MOD, 'x.js'));
const { NodeIO } = req('@gltf-transform/core');
const { ALL_EXTENSIONS } = req('@gltf-transform/extensions');
const { dequantize } = req('@gltf-transform/functions');
const { MeshoptDecoder } = await import(pathToFileURL(path.join(MOD, 'node_modules/meshoptimizer/index.js')).href);
await MeshoptDecoder.ready;
const io = new NodeIO().registerExtensions(ALL_EXTENSIONS).registerDependencies({ 'meshopt.decoder': MeshoptDecoder });
const DROP = ['EXT_meshopt_compression', 'KHR_mesh_quantization'];
const [src, dst] = process.argv.slice(2);
for (const f of fs.readdirSync(src).filter(f => f.endsWith('.glb'))) {
  const doc = await io.read(path.join(src, f));
  if (!doc.getRoot().listExtensionsUsed().some(e => DROP.includes(e.extensionName))) continue;
  await doc.transform(dequantize());
  for (const e of doc.getRoot().listExtensionsUsed()) if (DROP.includes(e.extensionName)) e.dispose();
  fs.rmSync(path.join(dst, f), { force: true });   // в dst лежит жёсткая ссылка на оригинал — сначала убрать, иначе запись испортит оригинал
  await io.write(path.join(dst, f), doc);
  console.log('распакован', f);
}
